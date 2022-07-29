package io.kasava.utilities;

import android.annotation.SuppressLint;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceHolder;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.Semaphore;

@SuppressLint("NewApi")
public class CameraHAL {

	
	private SurfaceHolder mSurfaceHolder;

	public static int BACK_RECORDE_WIDTH = 960;

	public static int BACK_RECORDE_HEIGHT = 360;

	public final static int DEFAULT_PREVIEW_WIDTH = 1280;
	
	public final static int DEFAULT_PREVIEW_HEIGHT = 720;
	
	int PREVIEW_WIDTH = DEFAULT_PREVIEW_WIDTH;
	
	int PREVIEW_HEIGHT = DEFAULT_PREVIEW_HEIGHT;

	private Camera mCamera = null;

	private int mCameraId = CameraInfo.CAMERA_FACING_BACK;

	private static final int HANDLER_RECOVER_PREVIEW_SIZE = 0x06;

	private static final int MSG_SYNC_CAM_MODE = 112;

	private static final int HANDLER_RECOVER_DEVICE_PREVIEW_SIZE = 0x08;

	/**
	 * 同步设置摄像头参�?
	 */
	private static Semaphore LOCK_SET_CAM_PARAM = new Semaphore(1, true);

	final String KEY_MAIN_PRE_VIDEO_SIZE = "main-pre-video-size";
	final String KEY_UVC_PRE_VIDEO_SIZE = "uvc-pre-video-size";
	// eg:(x,y)
	final String KEY_MAIN_PRE_VIDEO_OFFSET = "main-pre-video-offset";
	final String KEY_MAIN_PRE_VIDEO_POS = "main-pre-video-pos";
	final String KEY_UVC_PRE_VIDEO_OFFSET = "uvc-pre-video-offset";
	final String KEY_UVC_PRE_VIDEO_POS = "uvc-pre-video-pos";
	final String KEY_UVC_PREVIEW_SIZE = "uvc-preview-size";
	final String KEY_MAIN_PREVIEW_SIZE = "main-preview-size";
	final String KEY_MU_PREVIEW_MODE_MIN_SIZE = "mu-preview-mode_min_size";
	final String KEY_MU_PREVIEW_MODE_MIN_POS = "mu-preview-mode_min_pos";
	final String KEY_EXT_VIDEO_IMG_SIZE = "ext-video_img_size";
	final String KEY_EXT_VIDEO_IMG_POS = "ext-video_img_pos";
	final String KEY_UVC_PREVIEW_OFFSET = "uvc-preview-offset";

	public static final int PREVIEW_MODE_MAIN = 0;
	public static final int PREVIEW_MODE_UVC = 1;
	public static final int PREVIEW_MODE_UVC_IN_MAIN = 2;
	public static final int PREVIEW_MODE_MAIN_IN_UVC = 3;
	public static final int PREVIEW_MODE_MAIN_ON_UVC = 4;
	public static final int PREVIEW_MODE_UVC_ON_MAIN = 5;
	public static final int PREVIEW_MODE_MAIN_UVC = 6;
	public static final int PREVIEW_MODE_UVC_MAIN = 7;

	// 0:main, 1:uvc, 2:uvc in main;main(max)+uvc(min), 3:main in
	// uvc;uvc(max)+main(min), 4:main|uvc, 5:uvc|main, 6:main/uvc, 7:uvc/main

	public static int mCameraPreviewMode = PREVIEW_MODE_MAIN;

	private int video_width = 1280;

	private int video_height = 720;
	

	public CameraHAL(SurfaceHolder surfaceHolder) {
		mSurfaceHolder = surfaceHolder;
	}

	public void initMUParameters(Parameters parameters) {

		int style_config = 2;
		
		parameters.set(KEY_MAIN_PREVIEW_SIZE, "1280x720");


		if (style_config == 1) {
			
			parameters.set(KEY_MAIN_PRE_VIDEO_SIZE, "960x540");
			parameters.set(KEY_UVC_PRE_VIDEO_SIZE, "960x540");
			parameters.set(KEY_MAIN_PRE_VIDEO_OFFSET, "0,0");
			parameters.set(KEY_UVC_PRE_VIDEO_OFFSET, "0,0");
			parameters.set(KEY_UVC_PREVIEW_SIZE, "1280x720");
			parameters.set(KEY_MAIN_PRE_VIDEO_POS, "960,0");
			parameters.set(KEY_UVC_PRE_VIDEO_POS, "0,0");
			
		} else if (style_config == 2) {
			
			parameters.set(KEY_MAIN_PRE_VIDEO_SIZE, "1920x1080");
			parameters.set(KEY_UVC_PRE_VIDEO_SIZE, BACK_RECORDE_WIDTH + "x" + BACK_RECORDE_HEIGHT);
			parameters.set(KEY_MAIN_PRE_VIDEO_POS, "0,0");

			parameters.set(KEY_UVC_PRE_VIDEO_POS, (1920 - BACK_RECORDE_WIDTH) + "," + "0");
			parameters.set(KEY_UVC_PRE_VIDEO_OFFSET, "0,90");
			parameters.set(KEY_UVC_PREVIEW_SIZE, "960x540");
			// parameters.set(KEY_EXT_VIDEO_IMG_SIZE, map_img_width + "x" +
			// map_img_height);
			parameters.set(KEY_EXT_VIDEO_IMG_POS, "0,0");
			
		} else if (style_config == 3) {
			
			parameters.set(KEY_MAIN_PRE_VIDEO_SIZE, video_width + "x" + video_height);
			parameters.set(KEY_UVC_PRE_VIDEO_SIZE, video_width + "x" + 540);
			parameters.set(KEY_MAIN_PRE_VIDEO_POS, "0,540");
			parameters.set(KEY_UVC_PRE_VIDEO_POS, "0,0");
			parameters.set(KEY_MAIN_PRE_VIDEO_OFFSET, "0,180");
			parameters.set(KEY_UVC_PRE_VIDEO_OFFSET, "0,0");
			parameters.set(KEY_UVC_PREVIEW_SIZE, "1280x540");
			// parameters.set(KEY_EXT_VIDEO_IMG_SIZE, ext_video_img_width + "x"
			// + ext_video_img_height);
			parameters.set(KEY_EXT_VIDEO_IMG_POS, video_width + ",0");
		}

		parameters.set(KEY_MU_PREVIEW_MODE_MIN_SIZE, "200x200");
		parameters.set(KEY_MU_PREVIEW_MODE_MIN_POS, "410,60");

	}

	public void switchPreviewMode(int mode, String caller) {

		mCameraPreviewMode = mode;
		try {
			if (mCamera != null) {
				if (mCameraPreviewMode == PREVIEW_MODE_UVC) {
					mCamera.setDisplayOrientation(-1);
				} else {
					mCamera.setDisplayOrientation(0);
				}

				Method switchPrivewMode = Camera.class.getDeclaredMethod("switchPreviewMode", int.class);

				switchPrivewMode.invoke(mCamera, mCameraPreviewMode);
			}
			
		} catch (Exception e) {

		}
		
	}

	public void enableUVCCAMPreview(int enable) {

		try {
			
			if (mCamera != null) {
				Method enableUVCCAMPreview = Camera.class.getDeclaredMethod("enableUVCCAMPreview", int.class);
				enableUVCCAMPreview.invoke(mCamera, enable);
			}
			
		} catch (Exception e) {

		}
		
	}

	public void setPictureCam(int cam_id) {
		
		try {
			
			if (mCamera != null) {
				Method setPictureCam = Camera.class.getDeclaredMethod("setPictureCam", int.class);
				setPictureCam.invoke(mCamera, cam_id);
			}
			
		} catch (Exception e) {
			
		}
		
	}

	public void setExtVideoFrame(byte[] yuv_data) {

		try {
			
			if (mCamera != null) {
				
				Method setExtVideoFrame = Camera.class.getDeclaredMethod("setExtVideoFrame", byte[].class);
				setExtVideoFrame.invoke(mCamera, new Object[] { yuv_data });
				
			}
			
		} catch (Exception e) {

		}

	}

	private Size getOptimalPreviewSize(List<Size> sizes, int w, int h) {

		final double ASPECT_TOLERANCE = 0.05;
		double targetRatio = (double) w / h;
		if (sizes == null)
			return null;
		Size optimalSize = null;
		double minDiff = Double.MAX_VALUE;
		int targetHeight = h;
		// Try to find an size match aspect ratio and size
		for (Size size : sizes) {
			double ratio = (double) size.width / size.height;
			if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE)
				continue;
			if (Math.abs(size.height - targetHeight) < minDiff) {
				optimalSize = size;
				minDiff = Math.abs(size.height - targetHeight);
			}
		}
		// Cannot find the one match the aspect ratio, ignore the requirement
		if (optimalSize == null) {
			minDiff = Double.MAX_VALUE;
			for (Size size : sizes) {
				if (Math.abs(size.height - targetHeight) < minDiff) {
					optimalSize = size;
					minDiff = Math.abs(size.height - targetHeight);
				}
			}
		}
		return optimalSize;
	}


	private void setCameraParam(Camera camera) {
		
		Parameters parameters = camera.getParameters();
		initMUParameters(parameters);

		int w = PREVIEW_WIDTH;
		int h = PREVIEW_HEIGHT;

		Size sz = getOptimalPreviewSize(parameters.getSupportedPreviewSizes(), w, h);

		Log.d("getOptimalPreviewSize", "w " + sz.width + "h " + sz.height);

		if (sz != null) {
			double tmpW = (double) sz.width;
			double tmpH = (double) sz.height;

			// mPreviewMainFrameLayout.setAspectRatio(mAspectRatio);
			parameters.setPreviewSize(sz.width, sz.height);
		}

		parameters.setAutoExposureLock(false);
		parameters.setVideoStabilization(false);
		parameters.setWhiteBalance(Parameters.WHITE_BALANCE_AUTO);
		parameters.setSceneMode(Parameters.SCENE_MODE_AUTO);
		parameters.set("vendor_name", "IME"); // 设置产品名字
		parameters.set("antibanding", "auto");
		parameters.set("auto-whitebalance-lock", "false");
		parameters.set("focus-mode", "continuous-picture");

		int pic_w = 1280;
		int pic_h = 720;
		parameters.setPictureSize(pic_w, pic_h);
		camera.setParameters(parameters);

	}

	// now camera
	public synchronized Camera openCamera(int id, SurfaceHolder surfaceHolder) {
		mCameraId = id;
		Camera camera = mCamera;

		if (camera == null) {
			
			try {
				
				camera = Camera.open(mCameraId);
				
				if (camera != null) {

					setCameraParam(camera);
					camera.setDisplayOrientation(0);
					camera.setPreviewDisplay(this.mSurfaceHolder);
					camera.startPreview();
					
				}

			} catch (Exception e) {
				System.exit(-1);
				return null;
			}
		}

		mCamera = camera;

		camera.setErrorCallback(new Camera.ErrorCallback() {
			@Override
			public void onError(int error, Camera camera) {
			}
		});
		return camera;
	}

	public synchronized void closeCamera() {

		synchronized (CameraHAL.class) {
			if (mCamera != null) {
				try {
					mCamera.reconnect();
				} catch (Exception e) {
				}
				// mHandler.removeMessages(HANDLE_ADD_CALL_BACK_BUFFER);
				mCamera.setPreviewCallbackWithBuffer(null);
				mCamera.stopPreview();
				try {
					mCamera.release();
				} catch (Exception e) {
				}
			}
			mCamera = null;
		}
	}

	synchronized void stopCamera() {
		if (mCamera != null)
			mCamera.lock();
	}

	private Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case HANDLER_RECOVER_PREVIEW_SIZE:
				setPreviewSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);
				break;
			case MSG_SYNC_CAM_MODE:
				switchPreviewMode(mCameraPreviewMode, "self");
				mHandler.removeMessages(MSG_SYNC_CAM_MODE);
				mHandler.sendEmptyMessageDelayed(MSG_SYNC_CAM_MODE, 1000);
				break;
			case HANDLER_RECOVER_DEVICE_PREVIEW_SIZE:
				setPreviewSize(DEFAULT_PREVIEW_WIDTH, DEFAULT_PREVIEW_HEIGHT);
				break;
			default:
				break;
			}
		}
	};

	/**
	 * plese call before setImePreviewCallback
	 * 
	 * @param width
	 * @param height
	 */
	public void setPreviewSize(int width, int height) {

		mHandler.removeMessages(HANDLER_RECOVER_DEVICE_PREVIEW_SIZE);
		int waitCameraInit = 0;

		while (mCamera == null) {

			waitCameraInit++;

			if (waitCameraInit > 3) {
				break;
			}

			try {
				Thread.sleep(1000);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		if (mCamera != null) {
			LOCK_SETCAMPARAMS();
			try {
				Parameters params = null;
				try {
					params = mCamera.getParameters();
				} catch (Exception e) {
					params = null;
				}
				if (params != null) {
					Log.i(CameraHAL.class.getSimpleName(),
							"start --->setPreviewSize ..getParameters != null");
					Size sz = getOptimalPreviewSize(
							params.getSupportedPreviewSizes(), width, height);
					if (sz.width != params.getPreviewSize().width
							|| sz.height != params.getPreviewSize().height) {

						boolean reconnectResult = RECONNECT_CAMERA();
						int waitCount = 0;
						while (!reconnectResult) {
							try {
								Thread.sleep(200);
							} catch (Exception e) {

							}
							reconnectResult = RECONNECT_CAMERA();
							waitCount++;
							if (waitCount > 10) {
								break;
							}
						}

						if (reconnectResult) {

							Log.i(CameraHAL.class.getSimpleName(),
									"start --->setPreviewSize ..stopPreview");
							mCamera.setPreviewCallbackWithBuffer(null);
							mCamera.stopPreview();

							Log.i(CameraHAL.class.getSimpleName(),
									"start --->setPreviewSize ..stopPreview finish ");
							params.setPreviewSize(sz.width, sz.height);
							mCamera.setParameters(params);
							Log.i(CameraHAL.class.getSimpleName(),
									"start --->setPreviewSize ..startPreview ");
							mCamera.startPreview();
							Log.i(CameraHAL.class.getSimpleName(),
									"start --->setPreviewSize ..startPreview finish");
						}
					}
				} else {
					Log.i(CameraHAL.class.getSimpleName(),
							"start --->setPreviewSize ..getParameters == null");
				}

			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				UNLOCK_SETCAMPARAMS();
			}
		}
	}

	private boolean RECONNECT_CAMERA() {
		boolean result = false;
		if (mCamera != null) {
			try {
				mCamera.reconnect();
				result = true;
			} catch (Exception e) {
				result = false;
			}
		}
		return result;
	}

	public static boolean TRY_LOCK_SETCAMPARAMS() {
		return LOCK_SET_CAM_PARAM.tryAcquire();
	}

	public static void LOCK_SETCAMPARAMS() {
		try {
			LOCK_SET_CAM_PARAM.acquire();
		} catch (Exception e) {

		}
	}

	public static void UNLOCK_SETCAMPARAMS() {
		LOCK_SET_CAM_PARAM.release();
	}
	
	public Camera getCamera(){
		return mCamera;
	}

}
