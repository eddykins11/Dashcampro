package io.kasava.uvc;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.serenegiant.usb.USBMonitor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import io.kasava.broadcast.LocalBroadcastMessage;
import io.kasava.data.Constants;
import io.kasava.data.UsbId;
import io.kasava.uvc.usbcameracommon.UVCCameraHandler;
import io.kasava.uvc.widget.CameraViewInterface;

import static io.kasava.broadcast.LocalBroadcastMessage.Type.uvcCameraReady;

public class UvcDevice {

    private static final String TAG = "UvcDevice";

    private final Context mContext;

    public UvcDevice(Context context) {
        mContext = context;
    }

    private int FILE_CHECK_INTERVAL_MS = (10 * 1000);
    private Handler fileCheckHandler = null;

    private String mId = "0";
    private List<UsbId> mUsbIds = new ArrayList<>();
    private USBMonitor mUsbMonitor;
    private USBMonitor.UsbControlBlock mCtrlBlock;
    private UVCCameraHandler mUvcCameraHandler;
    private CameraViewInterface mCameraView;
    private boolean mIgnoreFirst = false;
    private String mUsbDeviceToIgnore = "";
    private File mFile = null;
    private File mFileLast = null;

    private File mFileCheckFile;
    private long mFileCheckSize = 0;

    public void registerUsbListener(Activity activity, String id, CameraViewInterface cameraView, boolean ignoreFirst, int width, int height, List<UsbId> usbIds) {
        mId = id;
        Log.d(TAG, "registerUsbListener(" + mId + ")");
        mUsbIds = usbIds;
        mCameraView = cameraView;
        mCameraView.setAspectRatio(width / (float) height);

        // If it's not the main camera, file size is smaller and updates less often
        if(!id.contains("1")) {
            FILE_CHECK_INTERVAL_MS = (30 * 1000);
        }

        // Create the fileCheck watchdog handler
        if (fileCheckHandler == null) {
            fileCheckHandler = new Handler();
        }

        mIgnoreFirst = ignoreFirst;

        mUsbMonitor = new USBMonitor(mContext, mOnDeviceConnectListener);
        mUvcCameraHandler = UVCCameraHandler.createHandler(activity, mCameraView, 1, width, height, 1);

        mUsbMonitor.register();
    }

    public void unregisterUsbListener() {
        Log.d(TAG, "unregisterUsbListener(" + mId + ")");
        if(mUsbMonitor != null) {
            mUsbMonitor.unregister();
        }
    }

    public boolean isCameraFound() {
        return (mUvcCameraHandler != null && mUvcCameraHandler.isOpened());
    }

    public void setFile(File file) {
        Log.d(TAG, "setFile(" + mId + ")::" + file);
        mFileLast = mFile;
        mFile = file;

        if (mUvcCameraHandler != null && mUvcCameraHandler.isOpened()) {
            if(!mUvcCameraHandler.isRecording()) {
                // Start the 1st recording file
                mUvcCameraHandler.startRecording(mFile);

                fileCheckHandler.postDelayed(fileCheckRunner, FILE_CHECK_INTERVAL_MS);
            } else {
                // Start the recording file, will be restarted in onStopRecording() if mFile != null
                mUvcCameraHandler.stopRecording();
            }
        }
    }

    private final USBMonitor.OnDeviceConnectListener mOnDeviceConnectListener = new USBMonitor.OnDeviceConnectListener() {
        @Override
        public void onAttach(final UsbDevice device) {

            Log.d(TAG, "OnDeviceConnectListener::(" + mId + ")::vendor:" + Integer.toHexString(device.getVendorId()) +
                    " product:" + Integer.toHexString(device.getProductId()));

            if(mUvcCameraHandler != null && !mUvcCameraHandler.isOpened()) {

                boolean validCam = false;

                for(UsbId usbId: mUsbIds) {
                    if(usbId.vendorId == device.getVendorId() && usbId.productId == device.getProductId()) {
                        validCam = true;
                    }
                }

                if (validCam) {
                    if(mIgnoreFirst) {
                        Log.d(TAG, "OnDeviceConnectListener::onAttach(" + mId + ")::ignoring 1st found (" + device.getDeviceName() + ")");
                        mIgnoreFirst = false;
                        mUsbDeviceToIgnore = device.getDeviceName();
                    } else {
                        if (!device.getDeviceName().equals(mUsbDeviceToIgnore)) {
                            Log.d(TAG, "OnDeviceConnectListener::onAttach(" + mId + ")::vendor:" + Integer.toHexString(device.getVendorId()) +
                                    " product:" + Integer.toHexString(device.getProductId()) + " (" + device.getDeviceName() + ")");

                            Intent intentLocal = new Intent(LocalBroadcastMessage.ID);
                            intentLocal.putExtra(LocalBroadcastMessage.ID, LocalBroadcastMessage.Type.cameraFound);
                            intentLocal.putExtra("camNo", mId);
                            LocalBroadcastManager.getInstance(mContext).sendBroadcast(intentLocal);

                            mUsbMonitor.requestPermission(device);
                        }
                    }
                }
            }
        }

        @Override
        public void onConnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock, final boolean createNew) {
            Log.d(TAG, "OnDeviceConnectListener::onConnect(" + mId + ")");

            mCtrlBlock = ctrlBlock;

            mUvcCameraHandler.open(mCtrlBlock);
            mUvcCameraHandler.addCallback(cameraHandlerCallback);
        }

        @Override
        public void onDisconnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock) {
            Log.d(TAG, "OnDeviceConnectListener::onDisconnect(" + mId + ")");

            if (mUvcCameraHandler != null) {
                mFileLast = mFile;
                mUvcCameraHandler.stopRecording();
                mUvcCameraHandler.close();
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
        final SurfaceTexture st = mCameraView.getSurfaceTexture();
        if(st != null) {
            mUvcCameraHandler.startPreview(new Surface(st));
        }
    }

    public void captureStill(String lvFile) {
        if (mUvcCameraHandler != null && mUvcCameraHandler.isOpened()) {
            mUvcCameraHandler.captureStill(lvFile);
        }
    }

    private final UVCCameraHandler.CameraCallback cameraHandlerCallback = new UVCCameraHandler.CameraCallback() {
        @Override
        public void onOpen() {
            Log.d(TAG, "cameraHandlerCallback::onOpen(" + mId + ")");
            startPreview();
        }

        @Override
        public void onClose() {
            Log.d(TAG, "cameraHandlerCallback::onClose(" + mId + ")");
        }

        @Override
        public void onStartPreview() {
            Log.d(TAG, "cameraHandlerCallback::onStartPreview(" + mId + ")");

            Intent intentLocal = new Intent(LocalBroadcastMessage.ID);
            intentLocal.putExtra(LocalBroadcastMessage.ID, uvcCameraReady);
            intentLocal.putExtra("id", mId);
            LocalBroadcastManager.getInstance(mContext).sendBroadcast(intentLocal);
        }

        @Override
        public void onStopPreview() {
            Log.d(TAG, "cameraHandlerCallback::onStopPreview(" + mId + ")");
        }

        @Override
        public void onStartRecording() {
            Log.d(TAG, "cameraHandlerCallback::onStartRecording(" + mId + ")");
        }

        @Override
        public void onStopRecording() {
            Log.d(TAG, "cameraHandlerCallback::onStopRecording(" + mId + ")");

            try {
                Thread.sleep(200);
            } catch (InterruptedException ex) {
                Log.e(TAG, "cameraHandlerCallback::onStopRecording(" + mId + ")::failed to delay");
            } finally {
                if (mFile != null) {
                    mUvcCameraHandler.startRecording(mFile);
                }

                // Rename to .mp4 as file is playable
                if(mFileLast != null) {
                    if(!mFileLast.renameTo(new File(mFileLast.getAbsolutePath().replace(Constants.CAM_EXTENSION, Constants.MP4_EXTENSION)))) {
                        Log.e(TAG, "cameraHandlerCallback::onStopRecording(" + mId + ")::Failed to rename file to .mp4");
                    }
                }
            }
        }

        @Override
        public void onError(Exception ex) {
            Log.d(TAG, "cameraHandlerCallback::onError(" + mId + ")");
        }
    };

    Runnable fileCheckRunner = new Runnable() {
        @Override
        public void run() {
            fileCheckHandler.postDelayed(fileCheckRunner, FILE_CHECK_INTERVAL_MS);

            if(mFile != null) {
                long newFileSize = mFile.length();

                Log.d(TAG, "fileCheckRunner(" + mId + ")::File=" + mFile.getName() + ", Size=" + newFileSize);

                if (mFile == mFileCheckFile && newFileSize == mFileCheckSize) {
                    Log.e(TAG, "fileCheckRunner(" + mId + ")::Oh Fuck");

                    mFile = null; // Stops auto attempt at restarting
                    mUvcCameraHandler.stopRecording();
                    mUvcCameraHandler.stopPreview();
                    mUvcCameraHandler.close();

                    fileCheckHandler.removeCallbacks(fileCheckRunner);

                    new Timer().schedule(new TimerTask() {
                        @Override
                        public void run() {
                            mUvcCameraHandler.open(mCtrlBlock);
                        }
                    }, 500);
                }

                mFileCheckFile = mFile;
                mFileCheckSize = newFileSize;
            }
        }
    };
}
