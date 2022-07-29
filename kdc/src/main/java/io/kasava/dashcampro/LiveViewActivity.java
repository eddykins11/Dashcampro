package io.kasava.dashcampro;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.usb.UsbDevice;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;

import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.USBMonitor.OnDeviceConnectListener;
import com.serenegiant.usb.USBMonitor.UsbControlBlock;

import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import io.kasava.broadcast.LocalBroadcastMessage;
import io.kasava.data.Constants;
import io.kasava.utilities.Utilities;
import io.kasava.uvc.usbcameracommon.UVCCameraHandler;
import io.kasava.uvc.widget.CameraViewInterface;

public class LiveViewActivity extends AppCompatActivity {

    private static final String TAG = "LiveViewActivity";

    private KdcService mKdcService;

    private Utilities utilities = null;

    private String mModelName;

    // Cam1
    private Camera mCam1;
    private SurfaceView mCam1Preview;
    private SurfaceHolder mCam1PreviewHolder;
    private MediaRecorder mCam1MediaRecorder;

    // Cam2 - In-built
    private Camera mCam2;
    private SurfaceView mCam2Preview;
    private SurfaceHolder mCam2PreviewHolder;
    private MediaRecorder mCam2MediaRecorder;

    // Cam2 - USB
    private USBMonitor mUsbMonitorCam2;
    private UVCCameraHandler mUvcHandlerCam2;
    private CameraViewInterface mCameraViewCam2;

    // Cam3
    private USBMonitor mUsbMonitorCam3;
    private UVCCameraHandler mUvcHandlerCam3;
    private CameraViewInterface mCameraViewCam3;

    // USB cameras
    private static final boolean USE_SURFACE_ENCODER = false;
    private static final int USB_WIDTH = 640;
    private static final int USB_HEIGHT = 480;
    private static final int USB_MODE = 1;

    // Live View
    private boolean mLiveViewInProgress = false;
    private static Timer timerLiveView;
    private static int mLiveViewTimeOutMs = (60 * 1000);
    private byte[] mCam1LvData;
    private byte[] mCam2LvData;

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

        initKdcService();

        utilities = new Utilities(this);
        utilities.setModel();
        mModelName = utilities.getModelName();

        mLiveViewTimeOutMs = (int) getIntent().getSerializableExtra("durationS") * 1000;

        // Register for local broadcast messages
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver,
                new IntentFilter(LocalBroadcastMessage.ID));
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(TAG, "onStart()");

        mLiveViewInProgress = false;

        // Prepare cam1 surface
        prepareCam1Surface();

        if(mModelName.equals("KDC405")) {
            // Prepare cam2 surface
            prepareCam2Surface();
        }

        // Register for USB cameras
        registerUsbListener();

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                prepareCam1();
                if(mModelName.equals("KDC405")) {
                    prepareCam2();
                }
            }
        }, 1500);

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                liveViewStart();
            }
        }, 5000);
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

        if(mKdcService != null) {
            mKdcService.setLiveViewRunningState(false);
        }
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        Log.d(TAG, "onDestroy()");

        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);

        if (mUvcHandlerCam2 != null) {
            mUvcHandlerCam2.release();
            mUvcHandlerCam2 = null;
        }
        if (mUsbMonitorCam2 != null) {
            mUsbMonitorCam2.destroy();
            mUsbMonitorCam2 = null;
        }

        utilities.usbHostEnabled(false);

        try {
            unbindService(mKdcServiceConnection);
        } catch (Exception ex){
            Log.e(TAG, "Failed to unbind KdcService::" + ex.getMessage());
        }
    }


    /******************** Cam1 *********************/

    private void prepareCam1() {
        Log.d(TAG, "prepareCam1()");

        CamcorderProfile profile;

        try {
            mCam1 = Camera.open();
            Camera.Parameters parameters = mCam1.getParameters();

            Log.d(TAG, "prepareCam1()::Resolution=720p");
            profile = CamcorderProfile.get(CamcorderProfile.QUALITY_720P);

            parameters.setPreviewSize(profile.videoFrameWidth, profile.videoFrameHeight);
            mCam1.setParameters(parameters);

            mCam1.reconnect();
            mCam1.setPreviewDisplay(mCam1PreviewHolder);
            mCam1.startPreview();
        } catch (IOException ex) {
            Log.e(TAG, "prepareCam1()::Surface texture is unavailable or unsuitable: " + ex.getMessage());
            return;
        } catch (Exception ex) {
            Log.e(TAG, "prepareCam1()::Error connecting to camera: " + ex.getMessage());
            return;
        }

        try {
            Thread.sleep(500);

            mCam1.unlock();

            mCam1MediaRecorder = new MediaRecorder();
            mCam1MediaRecorder.setCamera(mCam1);
            mCam1MediaRecorder.setPreviewDisplay(mCam1PreviewHolder.getSurface());

            mCam1MediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            mCam1MediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mCam1MediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mCam1MediaRecorder.setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight);
            mCam1MediaRecorder.setVideoFrameRate(12);
            mCam1MediaRecorder.setVideoEncodingBitRate(2500000);

            mCam1MediaRecorder.setOutputFile("storage/sdcard0/tmp1.mp4");

            mCam1MediaRecorder.prepare();
            mCam1MediaRecorder.start();

        } catch (Exception ex) {
            Log.e(TAG, "prepareCam1()::failed: " + ex.getMessage());
        }
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
        } catch (IOException ex) {
            Log.e(TAG, "prepareCam2()::Surface texture is unavailable or unsuitable: " + ex.getMessage());
            return false;
        } catch (Exception ex) {
            Log.e(TAG, "prepareCam2()::Error connecting to camera: " + ex.getMessage());
            return false;
        }

        try {
            Thread.sleep(500);

            mCam2.unlock();

            mCam2MediaRecorder = new MediaRecorder();
            mCam2MediaRecorder.setCamera(mCam2);
            mCam2MediaRecorder.setPreviewDisplay(mCam2PreviewHolder.getSurface());
            mCam2MediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            mCam2MediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mCam2MediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mCam2MediaRecorder.setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight);

            mCam2MediaRecorder.setOutputFile("storage/sdcard0/tmp2.mp4");

            mCam2MediaRecorder.prepare();
            mCam2MediaRecorder.start();

        } catch (Exception ex) {
            Log.e(TAG, "prepareCam2()::failed: " + ex.getMessage());
            return false;
        }

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
        mUvcHandlerCam2 = UVCCameraHandler.createHandler(this, mCameraViewCam2,
                USE_SURFACE_ENCODER ? 0 : 1, USB_WIDTH, USB_HEIGHT, USB_MODE);

        mUsbMonitorCam2.register();
    }

    private final OnDeviceConnectListener mOnDeviceConnectListener = new OnDeviceConnectListener() {
        @Override
        public void onAttach(final UsbDevice device) {
            Log.d(TAG, "OnDeviceConnectListener::onAttach()::vendor:" + device.getVendorId() + " product:" + device.getProductId());
            if((device.getVendorId() == 3141 && device.getProductId() == 25771) ||
                (device.getVendorId() == 1443 && device.getProductId() == 37424) ||
                    (device.getVendorId() == 6935 && device.getProductId() == 528)) {
                mUsbMonitorCam2.requestPermission(device);
            }
        }

        @Override
        public void onConnect(final UsbDevice device, final UsbControlBlock ctrlBlock, final boolean createNew) {
            Log.d(TAG, "OnDeviceConnectListener::onConnect()");
            mUvcHandlerCam2.open(ctrlBlock);
            startPreview();
        }

        @Override
        public void onDisconnect(final UsbDevice device, final UsbControlBlock ctrlBlock) {
            Log.d(TAG, "OnDeviceConnectListener::onDisconnect()");
            if (mUvcHandlerCam2 != null) {
                mUvcHandlerCam2.close();
            }
        }
        @Override
        public void onDettach(final UsbDevice device) {
        }

        @Override
        public void onCancel(final UsbDevice device) {
        }
    };

    private void startPreview() {
        final SurfaceTexture st = mCameraViewCam2.getSurfaceTexture();
        mUvcHandlerCam2.startPreview(new Surface(st));
    }

    /******************** Live View ********************/

    private void liveViewStart() {
        Log.d(TAG, "liveViewStart()");

        // Enable uploading of next live view image after capture
        mLiveViewInProgress = true;

        if (timerLiveView != null) {
            timerLiveView.cancel();
            timerLiveView = null;
        }

        // Start liveViewStop() timer
        timerLiveView = new Timer();
        timerLiveView.schedule(new TimerTask() {
            @Override
            public void run() {
                liveViewStop();
            }
        }, mLiveViewTimeOutMs);

        // Capture the first images
        liveViewCapture();
    }

    private void liveViewStop() {
        Log.d(TAG, "liveViewStop()");

        mLiveViewInProgress = false;

        stopCam2();
        stopCam1();

        finish();
    }

    private void liveViewCapture() {
        Log.d(TAG, "liveViewCapture()");

        // Capture the images from USB cameras
        if (mUvcHandlerCam2 != null && mUvcHandlerCam2.isOpened()) {
            mUvcHandlerCam2.captureStill(Constants.LV2_FILEPATH);
        }
        if (mUvcHandlerCam3 != null && mUvcHandlerCam3.isOpened()) {
            mUvcHandlerCam3.captureStill(Constants.LV3_FILEPATH);
        }

        // Capture the image from front camera
        if (mCam1 != null) {
            try {
                Log.d(TAG, "Capture liveView from cam1...");
                mCam1.takePicture(null, null, mPictureCallbackCam1);
            } catch (Exception ex) {
                Log.e(TAG, "liveViewCapture()::Error taking picture from cam1: " + ex.getMessage());
            }
        }

        // Capture the image from in-built camera
        if (mCam2 != null) {
            try {
                Log.d(TAG, "Capture liveView from cam2...");
                mCam2.takePicture(null, null, mPictureCallbackCam2);
            } catch (Exception ex) {
                Log.e(TAG, "liveViewCapture()::Error taking picture from cam2: " + ex.getMessage());
            }
        }
    }

    Camera.PictureCallback mPictureCallbackCam1 = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] cam1Data, Camera camera) {
            Log.d(TAG, "Captured liveView from cam1: " + cam1Data.length);

            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                Log.e(TAG, "mPictureCallbackCam1::failed to delay");
            }

            mCam1LvData = null;
            mCam1LvData = cam1Data;

            // Merge the liveLiew images
            byte[] lvData;
            if(mModelName.equals("KDC405") && mCam2LvData != null) {
                lvData = utilities.mergeLiveViewImagesToBytes(mCam1LvData, mCam2LvData);
            } else {
                lvData = utilities.mergeLiveViewImagesToBytes(mCam1LvData, null);
            }

            // Upload to Azure
            mKdcService.azureUploadLiveView(lvData);
            mCam1LvData = null;
        }
    };

    Camera.PictureCallback mPictureCallbackCam2 = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] cam2Data, Camera camera) {
            Log.d(TAG, "Captured liveView from cam2: " + cam2Data.length);

            mCam2LvData = cam2Data;
        }
    };

    /********* Local Broadcast Receiver *********/

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            LocalBroadcastMessage.Type messageType = (LocalBroadcastMessage.Type) intent.getSerializableExtra(LocalBroadcastMessage.ID);

            switch(messageType){
                case ignitionOn:
                    Log.d(TAG, "Ignition on so stop the liveview");
                    liveViewStop();
                    break;

                case liveViewUploaded:
                    Log.d(TAG, "LiveView uploaded");
                    if(mLiveViewInProgress) {
                        liveViewCapture();
                    }
                    break;

                default:
                    break;
            }
        }
    };

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
            mKdcService.setLiveViewRunningState(true);
        }

        public void onServiceDisconnected(ComponentName classname) {
            mKdcService = null;
        }
    };
}