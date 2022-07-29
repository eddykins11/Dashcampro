package io.kasava.dashcampro;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Camera;
import android.hardware.usb.UsbDevice;
import android.location.Location;
import android.media.AudioManager;
import android.media.CamcorderProfile;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.USBMonitor.OnDeviceConnectListener;
import com.serenegiant.usb.USBMonitor.UsbControlBlock;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import io.kasava.data.HealthCheckData;
import io.kasava.data.RecorderState;
import io.kasava.data.Subscription;
import io.kasava.data.UsbId;
import io.kasava.utilities.CameraHAL;
import io.kasava.utilities.Cellular;
import io.kasava.utilities.Storage;
import io.kasava.utilities.Utilities;
import io.kasava.uvc.usbcameracommon.UVCCameraHandler;
import io.kasava.uvc.widget.CameraViewInterface;

public class SelfTestActivity extends AppCompatActivity {

    private static final String TAG = "SelfTestActivity";

    private KdcService mKdcService;

    private Cellular cellular;
    private Storage storage = null;
    private Utilities utilities = null;

    private enum TestState {
        RUNNING,
        FAILED,
        PASSED
    }

    private String mModelName;

    // Cam1
    private Camera mCam1;
    private CameraHAL mCameraHAl;
    private SurfaceView mCam1Preview;
    private SurfaceHolder mCam1PreviewHolder;
    private MediaRecorder mCam1MediaRecorder;

    // Cam2 (In-built)
    private Camera mCam2;
    private SurfaceView mCam2Preview;
    private SurfaceHolder mCam2PreviewHolder;
    private MediaRecorder mCam2MediaRecorder;

    // Cam2 (USB)
    private USBMonitor mUsbMonitorCam2;
    private UVCCameraHandler mUvcHandlerCam2;
    private CameraViewInterface mCameraViewCam2;

    // USB cameras
    private static final int USB_WIDTH = 640;
    private static final int USB_HEIGHT = 480;
    private static final int USB_MODE = 1;

    // PanicButton
    private static boolean mPanicActive = false;

    // Tests
    /*
    private TestState mTestState = TestState.RUNNING;
    private boolean mTestCam1 = false;
    private int mTestCamUsb = 0;
    private boolean mTestSim = false;
    private boolean mTestData = false;
    private boolean mTestGps = false;
    private boolean mTestSd = false;
    private boolean mTestIgn = false;
    private boolean mIgnitionCheckInProgress = false;
    */

    private TestState mTestState = TestState.RUNNING;
    private boolean mIgnitionCheckInProgress = false;
    private int mTestCamUsb = 0;
    private HealthCheckData mHealthCheckData;

    // Handlers
    private static Handler selfTestHandler = null;
    private static int mSelfTestIntervalMs = (6 * 1000);

    private int mRunnerCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate()");

        setContentView(R.layout.activity_recorder);

        // Stop Android device turning screen off
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        // Set full screen
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        // Maximum screen brightness for KDC403
        setScreenBrightness(1F);

        initKdcService();

        utilities = new Utilities(this);
        utilities.setModel();
        mModelName = utilities.getModelName();
        cellular = new Cellular(this);
        storage = new Storage(this, utilities.getModelType());

        //utilities.usbHostEnabled(true);
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(TAG, "onStart()");

        /*
        mSelfTestIntervalMs = (5 * 1000);
        mTestState = TestState.RUNNING;
        mTestCam1 = false;
        mTestCamUsb = 0;
        mTestSim = false;
        mTestData = false;
        mTestGps = false;
        mTestSd = false;
        mTestIgn = false;*/

        mSelfTestIntervalMs = (5 * 1000);
        mTestState = TestState.RUNNING;
        mTestCamUsb = 0;

        Date now = Calendar.getInstance().getTime();

        mHealthCheckData = new HealthCheckData();
        mHealthCheckData.dateTime = utilities.dateToAzureDate(now);
        mHealthCheckData.type = 1;
        mHealthCheckData.camera1 = false;
        mHealthCheckData.camera2 = false;
        mHealthCheckData.camera3 = false;
        mHealthCheckData.sim = false;
        mHealthCheckData.network = false;
        mHealthCheckData.gps = false;
        mHealthCheckData.sdPresent = false;
        mHealthCheckData.sdWrite = false;
        mHealthCheckData.motion = true;
        mHealthCheckData.ign = false;
        mHealthCheckData.emmcFreeMB = (int)storage.getEmmcFreeStorageMb();
        mHealthCheckData.sdFreeMB = (int)storage.getSdFreeStorageMb();

        mIgnitionCheckInProgress = false;

        //Create the handlers
        if (selfTestHandler == null) {
            selfTestHandler = new Handler();
        }
        selfTestHandler.postDelayed(selfTestRunner, mSelfTestIntervalMs);

        utilities.playAudio(R.raw.healthcheckstarted, 1500, 100);

        // Check SIM is present
        String simNo = cellular.getSimNo();
        if(simNo != null) {
            Log.d(TAG, "SelfTest::SIM Pass, SIM no=" + simNo);
            mHealthCheckData.sim = true;
        }

        // Check if SD is present
        if(storage.isSdPresent()) {
            Log.d(TAG, "SelfTest::SD Pass");
            mHealthCheckData.sdPresent = true;
        }

        // Check if SD is writable
        if(storage.sdTestWrite()) {
            Log.d(TAG, "SelfTest::SD Write Pass");
            mHealthCheckData.sdWrite = true;
        }

        // Prepare cam1 surface
        prepareCam1Surface();

        if(mModelName.equals("KDC405")) {
            // Prepare cam2 surface
            prepareCam2Surface();
        }

        // Register for USB cameras
        registerUsbListener();

        if(mModelName.equals("KDC402a")) {
            mCameraHAl = new CameraHAL(mCam1PreviewHolder);
        }

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                if (prepareCam1()) {
                    if(mModelName.equals("KDC405")) {
                        prepareCam2();
                    }
                }
            }
        }, 2500);
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause()");
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.d(TAG, "onStop()");

        mKdcService.setSelfTestInProgress(false);

        //utilities.usbHostEnabled(false);

        if (selfTestHandler != null) {
            selfTestHandler.removeCallbacks(selfTestRunner);
            selfTestHandler = null;
        }
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        Log.d(TAG, "onDestroy()");

        if (mUvcHandlerCam2 != null) {
            mUvcHandlerCam2.release();
            mUvcHandlerCam2 = null;
        }
        if (mUsbMonitorCam2 != null) {
            mUsbMonitorCam2.destroy();
            mUsbMonitorCam2 = null;
        }

        if(utilities != null) {
            //utilities.usbHostEnabled(false);
        }

        try {
            mKdcService.setRecorderState(RecorderState.STATE.NOT_RECORDING);
            unbindService(mKdcServiceConnection);
        } catch (Exception ex){
            Log.e(TAG, "Failed to unbind KdcService::" + ex.getMessage());
        }

        // Minimum screen brightness for KDC403
        setScreenBrightness(0.1F);
    }

    /******************** Display *********************/

    private void setScreenBrightness(float brightness) {
        Log.d(TAG, "setScreenBrightness()::" + brightness);
        WindowManager.LayoutParams layout = getWindow().getAttributes();
        layout.screenBrightness = brightness;
        getWindow().setAttributes(layout);
    }


    /******************** Cam1 *********************/

    private boolean prepareCam1() {
        Log.d(TAG, "prepareCam1()");

        int fps = 12;
        int videoBitrate;
        CamcorderProfile profile;

        Subscription subscription = mKdcService.getSubscription();

        Log.d(TAG, "Cameras = " + Camera.getNumberOfCameras());

        try {
            if(mModelName.equals("KDC402a")) {
                mCam1 = mCameraHAl.openCamera(0, mCam1PreviewHolder);
            } else {
                mCam1 = Camera.open(0);
            }

            Camera.Parameters parameters = mCam1.getParameters();

            if(subscription != null && subscription.recRes != null && subscription.recRes.equals("1080")) {
                Log.d(TAG, "prepareCam1()::Resolution=1080p");
                profile = CamcorderProfile.get(CamcorderProfile.QUALITY_1080P);
            } else {
                profile = CamcorderProfile.get(CamcorderProfile.QUALITY_720P);
                Log.d(TAG, "prepareCam1()::Resolution=720p, " + profile.videoBitRate);
            }

            parameters.setPreviewSize(profile.videoFrameWidth, profile.videoFrameHeight);
            mCam1.setParameters(parameters);

            mCam1.reconnect();
            mCam1.setPreviewDisplay(mCam1PreviewHolder);
            mCam1.startPreview();

            Log.d(TAG, "SelfTest::Camera1 Pass");
            mHealthCheckData.camera1 = true;

            stopCam1();

        } catch (IOException ex) {
            Log.e(TAG, "prepareCam1()::Surface texture is unavailable or unsuitable: " + ex.getMessage());
            return false;
        } catch (Exception ex) {
            Log.e(TAG, "prepareCam1()::Error connecting to camera: " + ex.getMessage());
            return false;
        }

        return true;
    }

    private void prepareCam1Surface() {
        Log.d(TAG, "prepareCam1Surface()");
        try {
            mCam1Preview = findViewById(R.id.viewCam1);
            mCam1PreviewHolder = mCam1Preview.getHolder();

            mCam1PreviewHolder.addCallback(cam1SurfaceCallback);
        } catch (Exception ex) {
            Log.e(TAG, "prepareCam1Surface()::Error preparing front camera surface: " + ex.getMessage());
        }
    }

    private SurfaceHolder.Callback cam1SurfaceCallback = new SurfaceHolder.Callback() {

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            Log.d(TAG, "cam1SurfaceCallback::surfaceCreated()");
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Log.d(TAG, "cam1SurfaceCallback::surfaceChanged()");
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.d(TAG, "cam1SurfaceCallbackSurfaceDestroyed()");
        }
    };

    private void stopCam1() {
        if (mCam1MediaRecorder != null) {
            Log.d(TAG, "stopMediaRecorder");
            try {
                mCam1MediaRecorder.stop();
            } catch (Exception ex) {
                Log.e(TAG, "stopCam1()::failed to stop: " + ex.getMessage());
            }
            mCam1MediaRecorder.reset();
            mCam1MediaRecorder.release();
            mCam1MediaRecorder = null;
        }

        if (mCam1 != null) {
            try {
                mCam1.lock();
                mCam1.stopPreview();
                mCam1.setPreviewCallback(null);
            } catch (Exception ex) {
                Log.e(TAG, "Error stopping cam1: " + ex.getMessage());
            }
            try {
                mCam1.unlock();
            } catch (Exception ex) {
                Log.e(TAG, "Error unlocking cam1: " + ex.getMessage());
            }
            try {
                mCam1.release();
            } catch (Exception ex) {
                Log.e(TAG, "Error releasing cam1: " + ex.getMessage());
            }

            mCam1 = null;
        }

        if(mCameraHAl != null) {
            mCameraHAl.closeCamera();
        }
    }


    /******************** Camera 2 In-built *********************/

    private boolean prepareCam2() {
        Log.d(TAG, "prepareCam2()");

        CamcorderProfile profile;

        try {
            mCam2 = Camera.open(1);

            Camera.Parameters parameters = mCam2.getParameters();

            profile = CamcorderProfile.get(CamcorderProfile.QUALITY_480P);
            Log.d(TAG, "prepareCam2()::Resolution=480p, " + profile.videoBitRate);

            parameters.setPreviewSize(profile.videoFrameWidth, profile.videoFrameHeight);
            mCam2.setParameters(parameters);

            mCam2.reconnect();
            mCam2.setPreviewDisplay(mCam2PreviewHolder);
            mCam2.startPreview();

            Log.d(TAG, "SelfTest::Camera2 Pass");
            //mTestCam2 = true;

        } catch (IOException ex) {
            Log.e(TAG, "prepareCam2()::Surface texture is unavailable or unsuitable: " + ex.getMessage());
            return false;
        } catch (Exception ex) {
            Log.e(TAG, "prepareCam2()::Error connecting to camera: " + ex.getMessage());
            return false;
        }

        stopCam2();

        return true;
    }

    private void prepareCam2Surface() {
        Log.d(TAG, "prepareCam2Surface()");
        try {
            mCam2Preview = findViewById(R.id.viewCam2);
            mCam2PreviewHolder = mCam2Preview.getHolder();

            mCam2PreviewHolder.addCallback(cam2SurfaceCallback);
        } catch (Exception ex) {
            Log.e(TAG, "prepareCam2Surface()::Error preparing in-built camera 2 surface: " + ex.getMessage());
        }
    }

    private SurfaceHolder.Callback cam2SurfaceCallback = new SurfaceHolder.Callback() {

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            Log.d(TAG, "cam2SurfaceCallback::surfaceCreated()");
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Log.d(TAG, "cam2SurfaceCallback::surfaceChanged()");
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.d(TAG, "cam2SurfaceCallbackSurfaceDestroyed()");
        }
    };

    private void stopCam2() {
        if (mCam2MediaRecorder != null) {
            Log.d(TAG, "stopMediaRecorder2");
            try {
                mCam2MediaRecorder.stop();
            } catch (Exception ex) {
                Log.e(TAG, "stopCam2()::failed to stop: " + ex.getMessage());
            }
            mCam2MediaRecorder.reset();
            mCam2MediaRecorder.release();
            mCam2MediaRecorder = null;
        }

        if (mCam2 != null) {
            try {
                mCam2.lock();
                mCam2.stopPreview();
                mCam2.setPreviewCallback(null);
            } catch (Exception ex) {
                Log.e(TAG, "Error stopping cam2: " + ex.getMessage());
            }
            try {
                mCam2.unlock();
            } catch (Exception ex) {
                Log.e(TAG, "Error unlocking cam2: " + ex.getMessage());
            }
            try {
                mCam2.release();
            } catch (Exception ex) {
                Log.e(TAG, "Error releasing cam2: " + ex.getMessage());
            }

            mCam2 = null;
        }
    }



    /******************** USB Camera *********************/

    private void registerUsbListener() {
        Log.d(TAG, "registerUsbListener()");
        mCameraViewCam2 = findViewById(R.id.viewCam2Usb);
        mCameraViewCam2.setAspectRatio(USB_WIDTH / (float) USB_HEIGHT);

        mUsbMonitorCam2 = new USBMonitor(this, mOnDeviceConnectListener);
        mUvcHandlerCam2 = UVCCameraHandler.createHandler(this, mCameraViewCam2, 1, USB_WIDTH, USB_HEIGHT, USB_MODE);

        mUsbMonitorCam2.register();
    }

    private final OnDeviceConnectListener mOnDeviceConnectListener = new OnDeviceConnectListener() {
        @Override
        public void onAttach(final UsbDevice device) {
            if(device != null) {
                Log.d(TAG, "OnDeviceConnectListener::onAttach()::vendor:" +
                        Integer.toHexString(device.getVendorId()) + " product:" + Integer.toHexString(device.getProductId()));
                if ((device.getVendorId() == 3141 && device.getProductId() == 25771) ||
                        (device.getVendorId() == 1443 && device.getProductId() == 37424) ||
                        (device.getVendorId() == 6935 && device.getProductId() == 528) ||
                        (device.getVendorId() == 0x0C45 && device.getProductId() == 0x64AB) ||
                        (device.getVendorId() == 0x0C45 && device.getProductId() == 0x6361) ||
                        (device.getVendorId() == 0x1b3f && device.getProductId() == 0x8301) ||
                        (device.getVendorId() == 0xABCD && device.getProductId() == 0xAB26) ||
                        (device.getVendorId() == 0xABCD && device.getProductId() == 0xAB30)||
                        (device.getVendorId() == 0x05A3 && device.getProductId() == 0x9230)) {
                    Log.d(TAG, "SelfTest::Camera Usb Found");
                    mTestCamUsb++;
                }
            }
        }

        @Override
        public void onConnect(final UsbDevice device, final UsbControlBlock ctrlBlock, final boolean createNew) {
            Log.d(TAG, "OnDeviceConnectListener::onConnect()");
        }

        @Override
        public void onDisconnect(final UsbDevice device, final UsbControlBlock ctrlBlock) {
            Log.d(TAG, "OnDeviceConnectListener::onDisconnect()");
        }
        @Override
        public void onDettach(final UsbDevice device) {
        }

        @Override
        public void onCancel(final UsbDevice device) {
        }
    };

    /******************** Handlers *********************/

    Runnable selfTestRunner = new Runnable() {
        @Override
        public void run() {

            // USB cameras
            if(mTestCamUsb > 0) {
                mHealthCheckData.camera2 = true;
            }
            if(mTestCamUsb > 1) {
                mHealthCheckData.camera3 = true;
            }

            // SD Card & SIM check
            if(!mModelName.contains("KXB1") && !mModelName.contains("KXB2") && (!mHealthCheckData.sdPresent || !mHealthCheckData.sdWrite || !mHealthCheckData.sim)) {
                mTestState = TestState.FAILED;
            }

            // Mobile data
            if(!mHealthCheckData.network && cellular.isNetworkActive()) {
                Log.d(TAG, "SelfTest::Data Pass");
                mHealthCheckData.network = true;
            }

            // GPS
            Location location = mKdcService.getLocation();
            if(!mHealthCheckData.gps && location != null) {
                Log.d(TAG, "SelfTest::GPS Pass");
                mHealthCheckData.gps = true;
            }


            if (mTestState == TestState.RUNNING) {

                if (mIgnitionCheckInProgress && utilities.getIgnitionStatus()) {
                    mHealthCheckData.ign = true;
                    mTestState = TestState.PASSED;
                }

                // Timeout for base checks
                if (mRunnerCount > 6) {
                    mTestState = TestState.FAILED;
                }

                if (baseTestsPassed()) {
                    mIgnitionCheckInProgress = true;
                    mSelfTestIntervalMs = (10 * 1000);
                    if (mRunnerCount < 5) {
                        mRunnerCount = 5;
                    }
                    utilities.playAudio(R.raw.pleasestartvehicleignition, 2000, 100);
                } else if (mRunnerCount > 4 && !baseTestsPassed()) {
                    mTestState = TestState.FAILED;
                }
            }

            if (mTestState == TestState.FAILED) {

                mKdcService.azureAddToHealthCheckQueue(mHealthCheckData);

                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {

                        if (!mModelName.contains("KXB1") && !mModelName.contains("KXB2") && !mHealthCheckData.sdPresent) {
                            utilities.playAudio(R.raw.nosdcarddetected, 1700, 100);
                        } else if (!mModelName.contains("KXB1") && !mModelName.contains("KXB2") && !mHealthCheckData.sdWrite) {
                            utilities.playAudio(R.raw.sdcarderror, 1700, 100);
                        } else if (!mHealthCheckData.sim) {
                            utilities.playAudio(R.raw.nosimcarddetected, 1700, 100);
                        } else if (!mHealthCheckData.network) {
                            utilities.playAudio(R.raw.nodataconnection, 1700, 100);
                        } else if (!mHealthCheckData.gps) {
                            utilities.playAudio(R.raw.nogpslocation, 1700, 100);
                        } else if (!mHealthCheckData.camera1 && !mModelName.contains("KXB1") && !mModelName.contains("KXB2") && !mModelName.contains("KXB3") && !mModelName.contains("KXB4") && !mModelName.contains("KXB5")) {
                            utilities.playAudio(R.raw.forwardfacingcameraerror, 1700, 100);
                        } else if (!mHealthCheckData.ign) {
                            utilities.playAudio(R.raw.noignitiondetected, 1700, 100);
                        }

                        try {
                            Thread.sleep(3000);
                            utilities.playAudio(R.raw.heathcheckfailed, 1500, 100);
                            Thread.sleep(2000);
                        } catch (InterruptedException ex) {
                        } finally {
                            finish();
                        }
                    }
                }, 3000);
            } else if (mTestState == TestState.RUNNING) {
                mRunnerCount++;
                if (!mIgnitionCheckInProgress) {
                    utilities.playAudio(R.raw.shortbeep, 500, 5);
                }
                selfTestHandler.postDelayed(selfTestRunner, mSelfTestIntervalMs);
            } else if (mTestState == TestState.PASSED) {

                mKdcService.azureAddToHealthCheckQueue(mHealthCheckData);

                utilities.playAudio(R.raw.healthcheckpassed, 1500, 100);

                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {

                        Log.d(TAG, "USB Count: " + mTestCamUsb);

                        switch (mTestCamUsb) {
                            case 0:
                                utilities.playAudio(R.raw.nosecondcameradetected, 1800, 100);
                                break;
                            case 1:
                                utilities.playAudio(R.raw.secondcameradetected, 1800, 100);
                                break;
                            case 2:
                            case 3:
                                utilities.playAudio(R.raw.twoexternalcamerasdetected, 2000, 100);
                                break;
                        }

                        new Timer().schedule(new TimerTask() {
                            @Override
                            public void run() {
                                finish();
                            }
                        }, 2000);
                    }
                }, 3000);
            }
        }
    };

        private boolean baseTestsPassed() {

        if((mHealthCheckData.camera1 || mModelName.contains("KXB1") || mModelName.contains("KXB2") || mModelName.contains("KXB3") || mModelName.contains("KXB4") || mModelName.contains("KXB5")) &&
                (mModelName.contains("KXB1") || mModelName.contains("KXB2") || mHealthCheckData.sdPresent) &&
                (mModelName.contains("KXB1") || mModelName.contains("KXB2") || mHealthCheckData.sdWrite) &&
                        mHealthCheckData.sim && mHealthCheckData.network && mHealthCheckData.gps) {
            return true;
        } else {
            return false;
        }
    }

    /********* Services *********/

    private void initKdcService() {
        Log.d(TAG, "initKdcService()");
        Intent bindIntent = new Intent(this, KdcService.class);
        startService(bindIntent);
        bindService(bindIntent, mKdcServiceConnection, Context.BIND_AUTO_CREATE);
    }

    private ServiceConnection mKdcServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder rawBinder) {
            mKdcService = ((KdcService.LocalBinder) rawBinder).getService();
            Log.d(TAG, "onKdcServiceConnected: " + mKdcService);
            mKdcService.setSelfTestInProgress(true);
        }

        public void onServiceDisconnected(ComponentName classname) {
            mKdcService = null;
        }
    };
}