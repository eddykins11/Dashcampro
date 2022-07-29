package io.kasava.dashcampro;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import io.kasava.broadcast.LocalBroadcastMessage;
import io.kasava.data.Constants;
import io.kasava.data.Model;
import io.kasava.data.RecorderState;
import io.kasava.data.Subscription;
import io.kasava.data.UsbId;
import io.kasava.utilities.CameraHAL;
import io.kasava.utilities.Storage;
import io.kasava.utilities.Utilities;
import io.kasava.uvc.UvcDevice;

public class LvActivity extends AppCompatActivity {

    private static final String TAG = "LvActivity";

    private enum CAM_MODE {
        MIPI_PENDING,
        MIPI,
        USB_PENDING,
        USB
    }

    private KdcService mKdcService;

    private Utilities utilities = null;

    private Model.MODEL mModel;

    private CAM_MODE mModeCam1 = null;
    private CAM_MODE mModeCam2 = null;
    private CAM_MODE mModeCam3 = null;

    // Cam1 (MIPI)
    private MipiCamera mCamera1;
    private CameraHAL mCameraHAl;

    // Cam2 (MIPI)
    private MipiCamera mCamera2;

    // Uvc Cameras
    private UvcDevice mUvcDevice1;
    private UvcDevice mUvcDevice2;
    private UvcDevice mUvcDevice3;

    // USB cameras
    private static final int USB_WIDTH_HD = 1280;
    private static final int USB_HEIGHT_HD = 720;
    private static final int USB_WIDTH_VGA = 640;
    private static final int USB_HEIGHT_VGA = 480;

    // Audio
    private static boolean mAudioEnabled = false;

    // AlertButton
    private static boolean mAlertActive = false;

    // Live View
    private boolean mLiveViewInProgress = false;
    private static Timer timerLiveView;
    private byte[] mCam1LvData;
    private byte[] mCam2LvData;

    // Handlers
    private static Handler newCam1FileHandler = null;
    private static Handler newCam2FileHandler = null;
    private static Handler newCam3FileHandler = null;
    private static final int CAMERA_MIPI_INTERVAL_MS = (30 * 1000);
    private static final int CAMERA_USB_INTERVAL_MS = (5 * 60 * 1000);
    private static Handler newJourneyFileHandler = null;
    private static final int JOURNEY_INTERVAL_MS = (10 * 60 * 1000);

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
        mModel = utilities.getModel();

        // Register for local broadcast messages
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver,
                new IntentFilter(LocalBroadcastMessage.ID));
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(TAG, "onStart()");

        mLiveViewInProgress = false;

        //Create the handlers
        if (newCam1FileHandler == null) {
            newCam1FileHandler = new Handler();
        }
        if (newCam2FileHandler == null) {
            newCam2FileHandler = new Handler();
        }
        if (newCam3FileHandler == null) {
            newCam3FileHandler = new Handler();
        }
        if (newJourneyFileHandler == null) {
            newJourneyFileHandler = new Handler();
        }

        // Set camera types
        switch (mModel) {
            case KDC402:
            case KDC402a:
            case KDC403:
            case KDC403a:
            case KDC404:
            case KDC404a:
                mModeCam1 = CAM_MODE.MIPI_PENDING;
                mModeCam2 = CAM_MODE.USB_PENDING;
                break;

            case KDC405:
                mModeCam1 = CAM_MODE.MIPI_PENDING;
                mModeCam2 = CAM_MODE.MIPI_PENDING;
                break;

            case KDC406:
                mModeCam1 = CAM_MODE.MIPI_PENDING;
                mModeCam2 = CAM_MODE.USB_PENDING;
                mModeCam3 = CAM_MODE.USB_PENDING;
                break;

            case KXB3:
            case KXB3a:
            case KXB4:
            case KXB5:
                mModeCam1 = CAM_MODE.USB_PENDING;
                mModeCam2 = CAM_MODE.USB_PENDING;
                break;
        }

        if (mModeCam1 == CAM_MODE.MIPI_PENDING) {
            // Prepare camera1 surface
            mCamera1 = new MipiCamera();
            mCamera1.setSurfaceHolder(findViewById(R.id.viewCam1));
            mCamera1.prepareCameraSurface();

        } else if (mModeCam1 == CAM_MODE.USB_PENDING) {
            List<UsbId> usbIds = new ArrayList<>();
            usbIds.add(new UsbId(0x0C45, 0x64AB));
            usbIds.add(new UsbId(0x1B17, 0x0210));
            usbIds.add(new UsbId(0x05A3, 0x9230));

            mUvcDevice1 = new UvcDevice(this);
            mUvcDevice1.registerUsbListener(this, "1", findViewById(R.id.viewCam1Usb), false, USB_WIDTH_HD, USB_HEIGHT_HD, usbIds);
        }

        if (mModeCam2 == CAM_MODE.MIPI_PENDING) {
            // Prepare camera2 surface
            mCamera2 = new MipiCamera();
            mCamera2.setSurfaceHolder(findViewById(R.id.viewCam2));
            mCamera2.prepareCameraSurface();

        } else if (mModeCam2 == CAM_MODE.USB_PENDING) {
            List<UsbId> usbIds = new ArrayList<>();
            usbIds.add(new UsbId(0x05A3, 0x9230));
            //usbIds.add(new UsbId(0x1B3F, 0x8301));      // Rear v3
            usbIds.add(new UsbId(0x0C45, 0x64AB));
            usbIds.add(new UsbId(0x1B17, 0x0210));

            mUvcDevice2 = new UvcDevice(this);

            mUvcDevice2.registerUsbListener(this, "2", findViewById(R.id.viewCam2Usb),
                    mModel == Model.MODEL.KXB3 || mModel == Model.MODEL.KXB3a || mModel == Model.MODEL.KXB4 || mModel == Model.MODEL.KXB5,
                    USB_WIDTH_VGA, USB_HEIGHT_VGA, usbIds);
        }

        if (mModeCam3 == CAM_MODE.USB_PENDING) {
            List<UsbId> usbIds = new ArrayList<>();
            //usbIds.add(new UsbId(0x05A3, 0x9230));
            usbIds.add(new UsbId(0x1B3F, 0x8301));      // Rear v3
            //usbIds.add(new UsbId(0x0C45, 0x64AB));
            usbIds.add(new UsbId(0xABCD, 0xAB26));
            //usbIds.add(new UsbId(0xABCD, 0xAB26));

            mUvcDevice3 = new UvcDevice(this);

            mUvcDevice3.registerUsbListener(this, "3", findViewById(R.id.viewCam3Usb), false, USB_WIDTH_VGA, USB_HEIGHT_VGA, usbIds);
        }

        if (mModel == Model.MODEL.KDC402a) {
            mCameraHAl = new CameraHAL(findViewById(R.id.viewCam1));
        }

        // Prepare the MIPI cameras and start recording
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                if (mModeCam1 == CAM_MODE.MIPI_PENDING) {
                    if (mCamera1.prepareCamera(1, "720", mAudioEnabled)) {
                        mModeCam1 = CAM_MODE.MIPI;
                        if (mModeCam2 == CAM_MODE.MIPI_PENDING) {
                            if(mCamera2.prepareCamera(2, "480", false)) {
                                mModeCam2 = CAM_MODE.MIPI;
                            }
                        }
                    }
                }
            }
        }, 2000);
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

        if(mMessageReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
        }

        // Deregister USB listeners
        if(mUvcDevice1 != null) { mUvcDevice1.unregisterUsbListener(); }
        if(mUvcDevice2 != null) { mUvcDevice2.unregisterUsbListener(); }
        if(mUvcDevice3 != null) { mUvcDevice3.unregisterUsbListener(); }

        if(mKdcService != null) {
            mKdcService.setLiveViewRunningState(false);
        }

        //mKdcService.usbHostEnabled(false);
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        Log.d(TAG, "onDestroy()");

        try {
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

    /******************** MIPI Camera *********************/

    private final class MipiCamera {

        private Camera camera;
        private SurfaceHolder surfaceHolder;
        private MediaRecorder mediaRecorder;

        private void setSurfaceHolder(SurfaceView surfaceView) {
            surfaceHolder = surfaceView.getHolder();
        }

        private void prepareCameraSurface() {
            Log.d(TAG, "prepareCameraSurface()");
            try {
                surfaceHolder.addCallback(cameraSurfaceCallback);
            } catch (Exception ex) {
                Log.e(TAG, "prepareCameraSurface()::Error preparing front camera surface: " + ex.getMessage());
            }
        }

        private final SurfaceHolder.Callback cameraSurfaceCallback = new SurfaceHolder.Callback() {

            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                Log.d(TAG, "cameraSurfaceCallback::surfaceCreated()");
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                Log.d(TAG, "cameraSurfaceCallback::surfaceChanged()");
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                Log.d(TAG, "cameraSurfaceCallbackSurfaceDestroyed()");
            }
        };

        private boolean prepareCamera(int id, String resolution, boolean audioEnabled) {
            Log.d(TAG, "prepareCamera()");

            int videoBitrate;
            CamcorderProfile profile;

            try {
                if(mModel == Model.MODEL.KDC402a && id == 1) {
                    camera = mCameraHAl.openCamera(id-1, surfaceHolder);
                } else {
                    camera = Camera.open(id-1);
                }

                Camera.Parameters parameters = camera.getParameters();

                parameters.setExposureCompensation(-1);

                if(resolution.equals("1080")) {
                    Log.d(TAG, "prepareCamera()::" + id + ", resolution=1080p");
                    profile = CamcorderProfile.get(CamcorderProfile.QUALITY_1080P);
                    videoBitrate = 4000000;
                } else if(resolution.equals("720")) {
                    profile = CamcorderProfile.get(CamcorderProfile.QUALITY_720P);
                    Log.d(TAG, "prepareCamera()::" + id + ", resolution=720p");
                    videoBitrate = 2500000;
                } else {
                    profile = CamcorderProfile.get(CamcorderProfile.QUALITY_480P);
                    Log.d(TAG, "prepareCamera()::" + id + ", resolution=480p");
                    videoBitrate = 1500000;
                }

                parameters.setPreviewSize(profile.videoFrameWidth, profile.videoFrameHeight);
                camera.setParameters(parameters);

                camera.reconnect();
                camera.setPreviewDisplay(surfaceHolder);
                camera.startPreview();
            } catch (IOException ex) {
                Log.e(TAG, "prepareCamera()::Surface texture is unavailable or unsuitable: " + ex.getMessage());
                return false;
            } catch (Exception ex) {
                Log.e(TAG, "prepareCamera()::Error connecting to camera: " + ex.getMessage());
                return false;
            }

            try {
                Thread.sleep(500);

                camera.unlock();

                mediaRecorder = new MediaRecorder();
                mediaRecorder.setCamera(camera);
                mediaRecorder.setPreviewDisplay(surfaceHolder.getSurface());

                if(audioEnabled) {
                    mediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
                }

                mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
                mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

                if(audioEnabled) {
                    mediaRecorder.setAudioChannels(profile.audioChannels);
                    mediaRecorder.setAudioEncoder(profile.audioCodec);
                    mediaRecorder.setAudioEncodingBitRate(profile.audioBitRate);
                    mediaRecorder.setAudioSamplingRate(profile.audioSampleRate);
                }

                mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
                mediaRecorder.setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight);
                mediaRecorder.setVideoEncodingBitRate(videoBitrate);

                mediaRecorder.setOutputFile("storage/sdcard0/tmp1.mp4");

                mediaRecorder.prepare();
                mediaRecorder.start();

            } catch (Exception ex) {
                Log.e(TAG, "prepareCamera()::failed: " + ex.getMessage());
                return false;
            }

            return true;
        }

        private void stopCamera() {
            if (mediaRecorder != null) {
                Log.d(TAG, "stopMediaRecorder");
                try {
                    mediaRecorder.stop();
                } catch (Exception ex) {
                    Log.e(TAG, "stopCamera()::failed to stop: " + ex.getMessage());
                }
                mediaRecorder.reset();
                mediaRecorder.release();
                mediaRecorder = null;
            }

            if (camera != null) {
                try {
                    camera.lock();
                    camera.stopPreview();
                    camera.setPreviewCallback(null);
                } catch (Exception ex) {
                    Log.e(TAG, "Error stopping camera: " + ex.getMessage());
                }
                try {
                    camera.unlock();
                } catch (Exception ex) {
                    Log.e(TAG, "Error unlocking camera: " + ex.getMessage());
                }
                try {
                    camera.release();
                } catch (Exception ex) {
                    Log.e(TAG, "Error releasing camera: " + ex.getMessage());
                }

                camera = null;
            }

            if(mCameraHAl != null) {
                mCameraHAl.closeCamera();
            }
        }

        private void takePicture(Camera.PictureCallback pictureCallback) {
            camera.takePicture(null, null, pictureCallback);
        }
    }


    /******************** Live View ********************/

    private void liveViewStart(int durationS) {
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
        }, durationS * 1000);

        if(mUvcDevice2 != null && mUvcDevice2.isCameraFound()) { mModeCam2 = CAM_MODE.USB; }
        if(mUvcDevice3 != null && mUvcDevice3.isCameraFound()) { mModeCam3 = CAM_MODE.USB; }

        // Capture the first images
        liveViewCapture();
    }

    private void liveViewStop() {
        Log.d(TAG, "liveViewStop()");

        mLiveViewInProgress = false;
    }

    private void liveViewCapture() {
        Log.d(TAG, "liveViewCapture()");

        mCam1LvData = null;
        mCam2LvData = null;

        // Capture the image from camera1
        if (mModeCam1 == CAM_MODE.MIPI && mCamera1 != null) {
            try {
                Log.d(TAG, "liveViewCapture()::cam1(mipi)...");
                mCamera1.takePicture(mPictureCallbackCam1);
            } catch (Exception ex) {
                Log.e(TAG, "liveViewCapture()::cam1(mipi) error taking picture:" + ex.getMessage());
            }
        } else if(mModeCam1 == CAM_MODE.USB) {
            Log.d(TAG, "liveViewCapture()::cam1(usb)...");
            mUvcDevice1.captureStill(Constants.LV1_FILEPATH);
        }

        // Capture the image from camera2
        if (mModeCam2 == CAM_MODE.MIPI && mCamera2 != null) {
            try {
                Log.d(TAG, "liveViewCapture()::cam2(mipi)...");
                mCamera2.takePicture(mPictureCallbackCam2);
            } catch (Exception ex) {
                Log.e(TAG, "liveViewCapture()::cam2(mipi) error taking picture:" + ex.getMessage());
            }
        } else if(mModeCam2 == CAM_MODE.USB) {
            Log.d(TAG, "liveViewCapture()::cam2(usb)...");
            mUvcDevice2.captureStill(Constants.LV2_FILEPATH);
        }

        // Capture the image from camera3
        if(mModeCam3 == CAM_MODE.USB) {
            Log.d(TAG, "liveViewCapture()::cam3(usb)...");
            mUvcDevice3.captureStill(Constants.LV3_FILEPATH);
        }

        int delayMs = 3000;
        if(mModeCam1 == CAM_MODE.USB) {
            // Give more time if cam1 is USB
            delayMs = 2500;
        }

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                // Merge the liveLiew images
                byte[] lvData = utilities.mergeLiveViewImagesToBytes(mCam1LvData, mCam2LvData);

                // Upload to Azure
                mKdcService.azureUploadLiveView(lvData);
            }
        }, delayMs);
    }

    Camera.PictureCallback mPictureCallbackCam1 = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] cam1Data, Camera camera) {
            Log.d(TAG, "Captured liveView from cam1: " + cam1Data.length);
            mCam1LvData = cam1Data;
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

    private final BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            LocalBroadcastMessage.Type messageType = (LocalBroadcastMessage.Type) intent.getSerializableExtra(LocalBroadcastMessage.ID);

            switch(messageType){

                case ignitionOff:
                    Log.d(TAG, "Ignition off");
                    //stopRecording();
                    break;


                case liveViewStart:
                    Log.d(TAG, "Start liveView");
                    String durationS = intent.getStringExtra("durationS");
                    liveViewStart(Integer.parseInt(durationS));
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

    private final ServiceConnection mKdcServiceConnection = new ServiceConnection() {
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