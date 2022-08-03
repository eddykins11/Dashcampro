package io.kasava.dashcampro;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.util.Log;

import com.bugsnag.android.Bugsnag;
import com.ftdi.j2xx.D2xxManager;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

import io.kasava.broadcast.KasavaBroadcastMessage;
import io.kasava.broadcast.KasavaBroadcastMessenger;
import io.kasava.broadcast.KasavaBroadcastMessenger.KasavaBroadcastListener;
import io.kasava.broadcast.LocalBroadcastMessage;
import io.kasava.data.Blob;
import io.kasava.data.Constants;
import io.kasava.data.Clip;
import io.kasava.data.Event;
import io.kasava.data.FtdiMsg;
import io.kasava.data.HealthCheckData;
import io.kasava.data.Model;
import io.kasava.data.RecorderState;
import io.kasava.data.Status;
import io.kasava.data.Subscription;
import io.kasava.utilities.Azure;
import io.kasava.utilities.Bluetooth;
import io.kasava.utilities.Can;
import io.kasava.utilities.Cellular;
import io.kasava.utilities.Clips;
import io.kasava.utilities.Events;
import io.kasava.utilities.Ftdi;
import io.kasava.utilities.HealthCheck;
import io.kasava.utilities.Logs;
import io.kasava.utilities.Motion;
import io.kasava.utilities.Storage;
import io.kasava.utilities.Utilities;
import io.kasava.utilities.Wifi;
import me.pushy.sdk.Pushy;

import static io.kasava.broadcast.KasavaBroadcastMessage.REQUEST_DRIVER_CHECK;
import static io.kasava.data.Model.MODEL.KDC406;
import static io.kasava.data.Model.MODEL.KXB1;
import static io.kasava.data.Model.MODEL.KXB2;
import static io.kasava.data.Model.MODEL.KXB3;
import static io.kasava.data.Model.MODEL.KXB4;
import static io.kasava.data.Model.MODEL.KXB5;

/**
 * Created by E.Barker on 06/01/2019.
 */

/*TODO
    5 day shutdown
    12 & 15fps for all
    SD card format
    Geofencing
*/

public class KdcService extends Service implements KasavaBroadcastListener, LocationListener, SensorEventListener {

	private static final String TAG = "KdcService";

    private Context mContext;

    // Service binding
    public final IBinder binder = new LocalBinder();

    class LocalBinder extends Binder {
        KdcService getService() {
            return KdcService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // Constants
    private static final int WATCHDOG_INTERVAL_MS = (10 * 1000);
    private static final int IGNITION_INTERVAL_MS = (4 * 1000);
    private static final int CLEANUP_INTERVAL_MS = (5 * 60 * 1000);
    private static final int STATUS_PARKED_INTERVAL_MS = (30 * 60 * 1000);
    private static final int STATUS_DRIVING_INTERVAL_MS = (30 * 1000);
    private static int mStatusIntervalMs = STATUS_PARKED_INTERVAL_MS;
    private static final int JOURNEY_LOG_INTERVAL_MS = (1000);

    // Power management
    private static PowerManager.WakeLock mWakeLock;
    private static boolean mScreenOn = true;
    private int mScreenOffCount = 0;
    private static Date mScreenOffCheck;

    // Instances
    private Azure azure;
    private Bluetooth bluetooth;
    private Cellular cellular;
    private Clips clips;
    private Events events;
    private Ftdi ftdi;
    private HealthCheck healthCheck;
    private static Logs logs;
    private Motion motion;
    private static Storage storage;
    private Utilities utilities;
    private Wifi wifi;

    // Device details
    private static Model.TYPE mModelType;
    private static Model.MODEL mModel;
    private static Subscription mSubscription;

    // Broadcast
    private KasavaBroadcastMessenger kasavaMessenger = null;

    // Handlers
    private static Handler watchdogHandler = null;
    private static Handler ignitionHandler = null;
    private static Handler cleanupHandler = null;
    private static Handler statusHandler = null;
    private static Handler journeyLogHandler = null;

    // Date & Time
    private static Date mCamStartDate = null;

    // Ignition
    private static int IGN_OFF_DELAY_4S = 4;
    private static int mIgnOffCount = 0;
    private static Date mIgnLastOff = null;

    private static int mParkedStatus = 0;

    private static boolean mIsCharging = false;
    private static int mBackupBatt = -1;

    // Recording checker
    private static int mCameraCount = -1;
    private static Date mRecFailDate;
    private static int mIgnRecCheck = 0;

    // Cellular check
    private static int mCellularOnTimeCount = 0;

    // Location
    private static LocationManager mLocationManager;
    private static Location mLocation;
    private static List<Location> mLocationBuffer = new ArrayList<>();
    private static boolean mSpeedEventActive = false;
    private static int mAccelRate = 28;
    private static int mBrakeRate = 32;

    // Motion
    private static SensorManager mSensorManager = null;
    private static final int SENSOR_TO_MG = 98;
    private static int mMaxX = 0, mMaxY = 0, mMaxZ = 0;
    private static int mLastMaxX = 0, mLastMaxY = 0, mLastMaxZ = 0;
    private static float mPitch = 0;
    private static Timer timerAccelBrake;

    // Event
    public enum EVENT_TYPE {
        CAM_FAIL,
        SD_UNMOUNTED,
        ACCELERATION,
        BRAKE,
        SHOCK,
        TURN,
        SPEEDING,
        ALERT,
        MANDOWN,
        ZIGZAG,
        EATING,
        MOBILE
    }

    private boolean mEventAccel = false;
    private boolean mEventBrake = false;
    private boolean mEventTurn = false;
    private boolean mEventShock = false;
    private boolean mEventZigzag = false;
    private boolean mEventMandown = false;

    public enum MOTION_STATE {
        DISABLED,
        ENABLED,
        READY_TO_CLEAR
    }
    //private static EVENT_STATE mEventState;

    // Clips
    private static List<Clip> mClipQueue = new ArrayList<>();
    private static boolean mClipInProgress = false;

    // LiveView
    private static boolean mLiveViewRunning = false;

    // SD
    private static Timer timerSdUnmounted;
    private static Timer timerSdReboot;

    // Syncing
    private static boolean mSyncingRecordings = false;

    private static RecorderState.STATE mRecorderState = RecorderState.STATE.NOT_RECORDING;

    // USB & FTDI
    private static boolean usbStateChangeReady = true;
    private static Timer timerUsbStateChange;
    private static boolean requiredUsbHostEnabled = false;
    public static D2xxManager mFtD2xx = null;
    //private static Timer timerUsbOff;

    // Immobiliser
    private static boolean mImmobEnabled = false;

    // CAN
    private static double mCanFuelTank = 0;
    private static double mCanEvBattery = 0;

    // SelfTest
    private static boolean mSelfTestInProgress = false;
    private int mSelfTestCount = 0;

    // HealthCheck
    private static Timer mTimerHealthCheck;
    private boolean mCam1Found = false;
    private boolean mCam2Found = false;
    private boolean mCam3Found = false;

    @Override
	public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate()");

        String modelName;

        this.mContext = getBaseContext();

        try {
            mFtD2xx = D2xxManager.getInstance(this);
        } catch (D2xxManager.D2xxException ex) {
            ex.printStackTrace();
        }

        // Set the wake lock to prevent complete sleep
        PowerManager pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            //mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "kasava:kdc:wakelock");
            //mWakeLock.acquire();
        }

        // Register receiver that handles screen on & off logic
        IntentFilter filterScreen = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filterScreen.addAction(Intent.ACTION_SCREEN_OFF);
        BroadcastReceiver mReceiverScreen = new ScreenReceiver();
        registerReceiver(mReceiverScreen, filterScreen);

        utilities = new Utilities(mContext);
        utilities.setModel();
        mModelType = utilities.getModelType();
        mModel = utilities.getModel();
        modelName = utilities.getModelName();

        azure = new Azure(mContext);
        cellular = new Cellular(mContext);
        clips = new Clips(mContext);
        events = new Events(mContext);
        //ftdi = new Ftdi(mContext, mFtD2xx);
        healthCheck = new HealthCheck(mContext);
        logs = new Logs();
        motion = new Motion(mContext);
        storage = new Storage(mContext, mModelType);

        // Make sure watchdog is running
        if (!utilities.isAppRunning("io.kasava.watchdog")) {
            utilities.startWatchdogApp();
        }

        utilities.ledBlueEnabled(false);

        // Set UK time zone & set date
        AlarmManager am = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        am.setTimeZone("Europe/London");
        checkAndSetDate();

        // Get device IMEI
        String deviceId = utilities.getDeviceId();
        Log.d(TAG, "DeviceId: " + deviceId);

        // Get last saved account Info
        mSubscription = loadSavedSubscription();

        // Set Wi-Fi SSID & password
        wifi = new Wifi(modelName + "_" + deviceId.substring(deviceId.length() - 4), "abc45678", mContext);

        // Enable Bluetooth
        bluetooth = new Bluetooth(mContext);
        //bluetooth.start();

        // Configure Bugsnag
        Bugsnag.init(this);
        Bugsnag.setUser(deviceId, "", modelName);

        // Mute audio
        utilities.audioMute();

        // Register for broadcast messages
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver,
                new IntentFilter(LocalBroadcastMessage.ID));

        // Create broadcast messenger
        kasavaMessenger = new KasavaBroadcastMessenger(this);
        kasavaMessenger.addListener(this);

        // Request version of watchdog
        kasavaMessenger.sendMessage(KasavaBroadcastMessage.REQUEST_APP_VERSION, null);

        // Start the watchdog handler
        if (watchdogHandler == null) {
            watchdogHandler = new Handler();
        }
        watchdogHandler.postDelayed(watchdogRunner, 1);

        // Start the ignition handler
        if (ignitionHandler == null) {
            ignitionHandler = new Handler();
        }
        ignitionHandler.postDelayed(ignitionRunner, IGNITION_INTERVAL_MS);

        // Start the cleanup handler
        if (cleanupHandler == null) {
            cleanupHandler = new Handler();
        }
        cleanupHandler.postDelayed(cleanupRunner, 1);

        // Start the status handler
        if (statusHandler == null) {
            statusHandler = new Handler();
        }
        statusHandler.postDelayed(statusRunner, 10 * 1000);

        // Create the journey log handler
        if (journeyLogHandler == null) {
            journeyLogHandler = new Handler();
        }

        // Create folders
        storage.createKasavaDirs();

        // Load the last recorder fail time
        mRecFailDate = utilities.loadRecordingFailDate();

        // Turn off USB camera power
        usbHostEnabled(false, true);

        // Start cellular
        cellular.start(mModel);

        // Start Wi-Fi
        if(deviceId.equals("352538108791982")) {
            wifi.stop();
            wifi.requestWIFIConnection("HUAWEI-2.4G-RwWj", "fX4NQQVy");
        } else {
            wifi.start();
        }

        // Start Azure connection
        azure.start(deviceId, "https://kasava-mapp-kdc.azurewebsites.net", mSubscription);
        //azure.start(deviceId, "https://kasava-mapp-kdc-dev.azurewebsites.net", mSubscription);

        // Set subscription values
        syncSubscription();

        // Start location services
        mLocation = utilities.loadLastLocation();
        utilities.saveLastLocation(null);
        locationStart();

        // Start motion
        motionStart();

        switch (mModel) {
            case KDC402:
            case KDC402a:
            case KDC403:
            case KDC403a:
            case KDC404:
            case KDC404a:
            case KDC405:
            case KDC406:

                break;

            case KXB3:
            case KXB3a:
            case KXB4:
            case KXB5:
                mEventBrake = true;
                mEventShock = true;
                mEventMandown = true;
                mEventZigzag = true;
        }

        // Set screen timeout in mins
        if(mModel == Model.MODEL.KXB3 || mModel == Model.MODEL.KXB4 || mModel == Model.MODEL.KXB5) {
            utilities.setScreenTimeout(5);
        } else {
            utilities.setScreenTimeout(2);
        }

        if(mModel == Model.MODEL.KXB4 || mModel == Model.MODEL.KXB5) {
            IGN_OFF_DELAY_4S = 15;
        }


        mIgnLastOff = Calendar.getInstance().getTime();
        //TODO load mIgnLastOff

        // Set sleep mode for PIC to shutdown power
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                ftdi = new Ftdi(mContext, mFtD2xx);

                switch (mModel) {
                    case KDC406:
                    case KXB1:
                    case KXB2:
                        ftdi.setSleepEnabled(false);
                        break;

                    case KXB3:
                    case KXB3a:
                    case KXB4:
                    case KXB5:
                        ftdi.setSleepEnabled(true);
                        ftdi.setStandbyHours(4);
                        break;
                }

                setRecorderState(RecorderState.STATE.NOT_RECORDING);
            }
        }, 1000);

        try {
            //mClipQueue = utilities.loadListClips();

            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    processClipQueue();
                }
            }, 10 * 1000);
        } catch (Exception ex) {
            Log.e(TAG, "Failed to load clip queue");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.d(TAG, "onDestroy");

		// Release the wake lock
        if(mWakeLock != null) {
            mWakeLock.release();
        }

        locationStop();
        motionStop();

        ftdi.closeDevice();
	}

    private class ScreenReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                Log.d(TAG, "ScreenReceiver()::OFF");
                mScreenOn = false;
                mScreenOffCount = 0;
                mScreenOffCheck = null;

                // Turn off USB camera power
                requiredUsbHostEnabled = false;
                usbHostEnabled(false, true);

                //cellular.setAirplaneModeOn(mModel, true);


                locationStop();
                motionStop();

                /*
                if (ignitionRunner != null) {
                    ignitionHandler.removeCallbacks(ignitionRunner);
                    ignitionHandler = null;
                }

                if (statusRunner != null) {
                    statusHandler.removeCallbacks(statusRunner);
                    statusHandler = null;
                }

                if (cleanupRunner != null) {dev
                    cleanupHandler.removeCallbacks(cleanupRunner);
                    cleanupHandler = null;
                }*/

            } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                Log.d(TAG, "ScreenReceiver()::ON");
                mScreenOn = true;

                locationStart();
                motionStart();

                // Turn on USB camera power
                requiredUsbHostEnabled = true;
                usbHostEnabled(true, true);

                //cellular.resetModem(mModel);
            }
        }
    }

    private void checkAndSetDate() {
        Log.d(TAG, "checkAndSetDate()::" + Calendar.getInstance().getTime());

        Thread thread = new Thread(() -> {

            if(mCamStartDate == null) {
                if (utilities.setCurrentNetworkTime() > 0) {
                    mCamStartDate = Calendar.getInstance().getTime();
                } else {
                    try {
                        Thread.sleep(5 * 1000);
                    } catch (InterruptedException ex) {
                        Log.e(TAG, "checkAndSetDate()::InterruptedException: " + ex.getMessage());
                    } finally {
                        checkAndSetDate();
                    }
                }
            }
        });
        thread.start();
    }


    /******************** Usb *********************/

    public void requestUsbHostEnabled(final boolean hostEnabled) {

        if(ftdi != null && (ftdi.getQueueSize() > 0 || ftdi.isReplyPending()) && !hostEnabled) {
            Log.e(TAG, "requestUsbHostEnabled()::can't set host off while ftdi messages are pending");
            return;
        }

        Log.d(TAG, "requestUsbHostEnabled()::" + hostEnabled);

        requiredUsbHostEnabled = hostEnabled;

        if(usbStateChangeReady && requiredUsbHostEnabled != utilities.isUsbHostEnabled()) {
            usbHostEnabled(requiredUsbHostEnabled, false);
        }
    }

    public void usbHostEnabled(boolean hostEnabled, boolean forceOff) {
        Log.d(TAG, "usbHostEnabled()::" + hostEnabled);
        usbStateChangeReady = false;

        if (timerUsbStateChange != null) {
            timerUsbStateChange.cancel();
        }

        timerUsbStateChange = new Timer();
        timerUsbStateChange.schedule(new TimerTask() {
            @Override
            public void run() {
                usbStateChangeReady = true;
                requestUsbHostEnabled(requiredUsbHostEnabled);
            }
        }, 3 * 1000);

        // No need to toggle USB for 405
        if(mModel == Model.MODEL.KDC405) {
            return;
        }

        // No need to turn off USB host for KDC406
        if(!forceOff && !hostEnabled && (mModel == KDC406 || mModel == KXB1 || mModel == KXB2 || mModel == KXB3 || mModel == KXB4 || mModel == KXB5)) {
            //return;
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    requestUsbHostEnabled(true);
                }
            }, 5 * 1000);
        }

        if(!hostEnabled && ftdi != null){
            ftdi.closeDevice();
        }

        utilities.usbHostEnabled(hostEnabled);
    }

    private void ftdiAddToWriteQueue(String msg, int attempts, boolean getsReply) {
        if(ftdi == null) {
            Log.d(TAG, "ftdiAddToWriteQueue()::null");
            return;
        }

        if(mModel == KDC406 || mModelType == Model.TYPE.KXB) {
            if(!utilities.isUsbHostEnabled()) {
                requestUsbHostEnabled(true);
            }

            ftdi.addToWriteQueue(new FtdiMsg(msg, attempts, getsReply));
        }
    }


    /******************** Subscription *********************/

    private Subscription loadSavedSubscription() {
        Log.d(TAG, "loadSavedSubscription()");
        return utilities.loadSubscription();
    }

    private void syncSubscription() {
        Log.d(TAG, "syncSubscription()");

        int sensAccel = 3;
        int sensBrake = 3;
        int sensTurn = 3;
        int sensShock = 3;

        mSubscription = azure.getSubscriptionFromAzure();
        utilities.saveSubscription(mSubscription);

        if (mSubscription != null) {
            sensShock = mSubscription.sensShock;
            sensAccel = mSubscription.sensAccel;
            sensBrake = mSubscription.sensBrake;
            sensTurn = mSubscription.sensTurn;

            if (mModel == KXB1 || mModel == KXB2 || mModel == KXB3 || mModel == Model.MODEL.KXB3a || mModel == KXB5) {
                immobStateCheck(true);
            }
        }

        // Update motion settings
        motion.setMotionParameters(mModelType, mModel, sensShock, sensAccel, sensBrake, sensTurn, 3);

        // Update harsh acceleration & braking thresholds
        switch (sensAccel) {
            case 0:
                mAccelRate = 100;
                break;
            case 1:
                mAccelRate = 35;
                break;
            case 2:
                mAccelRate = 31;
                break;
            case 4:
                mAccelRate = 24;
                break;
            case 5:
                mAccelRate = 20;
                break;
            default: // Default is 3
                mAccelRate = 28;
                break;
        }

        switch (sensBrake) {
            case 0:
                mBrakeRate = 100;
                break;
            case 1:
                mBrakeRate = 43;
                break;
            case 2:
                mBrakeRate = 39;
                break;
            case 4:
                mBrakeRate = 31;
                break;
            case 5:
                mBrakeRate = 27;
                break;
            default: // Default is 3
                mBrakeRate = 35;
                break;
        }
    }

    public Subscription getSubscription() { return mSubscription; }


    /******************** Recording *********************/

    public void setCameraCount(int cameraCount) {
        Log.d(TAG, "setCameraCount()::" + cameraCount);

        mCameraCount = cameraCount;
    }

    public void setRecorderState(RecorderState.STATE state) {

        if (mCamStartDate == null && state != RecorderState.STATE.NOT_RECORDING) {
            Log.e(TAG, "Can't start recording until date & time is set");
            return;
        }

        mRecorderState = state;

        switch (mRecorderState) {
            case NOT_RECORDING:
                motion.setMotionState(MOTION_STATE.DISABLED);

                // Cancel the health check timer
                if (mTimerHealthCheck != null) {
                    mTimerHealthCheck.cancel();
                }
                mCam1Found = false;
                mCam2Found = false;
                mCam3Found = false;

                utilities.ledRedEnabled(false);
                ftdiAddToWriteQueue("RED:0", 2, false);
                utilities.ledGreenEnabled(true);
                ftdiAddToWriteQueue("GRN:1", 2, false);

                logs.stopRecordingLogs();

                kasavaMessenger.sendMessage(KasavaBroadcastMessage.IGN_OFF, null);

                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        //mReadyToEnableUsbHost = false;
                        requestUsbHostEnabled(false);

                        syncRecordings();
                        processClipQueue();
                    }
                }, 2000);

                break;

            case PREPARING:
                kasavaMessenger.sendMessage(KasavaBroadcastMessage.IGN_ON, null);

                utilities.ledRedEnabled(true);
                utilities.ledGreenEnabled(true);

                // Turn on USB camera power
                requestUsbHostEnabled(true);

                // Stop LiveViewActivity if it's running when ignition is switched on
                utilities.sendLocalBroadcastMessage(LocalBroadcastMessage.Type.ignitionOn, null);

                // Disable events (until camera has recorded for at least 10 seconds)
                motion.setMotionState(MOTION_STATE.DISABLED);

                journeyStart();

                int recorderDelay = wakeUpActivity(mScreenOn);

                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        recorderActivity();
                    }
                }, recorderDelay);

                break;

            case RECORDING:
                utilities.ledRedEnabled(true);
                ftdiAddToWriteQueue("RED:1", 2, false);
                utilities.ledGreenEnabled(false);
                ftdiAddToWriteQueue("GRN:0", 2, false);

                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        // Enable 2nd USB device
                        ftdiAddToWriteQueue("USB", 2, false);
                    }
                }, 5000);

                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        // Enable events
                        Log.d(TAG, "Ready to clear events by motion sensor");
                        motion.setMotionState(MOTION_STATE.READY_TO_CLEAR);
                    }
                }, 10 * 1000);

                if (mTimerHealthCheck != null) {
                    mTimerHealthCheck.cancel();
                }

                Log.d(TAG, "Starting healthCheck timer...");
                mTimerHealthCheck = new Timer();
                mTimerHealthCheck.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        HealthCheckData healthCheckData = healthCheck.runHealthCheck(mCam1Found, mCam2Found, mCam3Found,
                                azure.getToken(), mLocation);

                        if (healthCheckData != null && healthCheckData.dateTime != null) {
                            azureAddToHealthCheckQueue(healthCheckData);
                        }
                    }
                }, 5 * 2 * 1000);

                break;
        }
    }

    public void setRecordingLogs(File dir) {
        logs.stopRecordingLogs();
        logs.createNewRecordingLogs(dir, utilities.dateAsUtcDateTimeString(Calendar.getInstance().getTime()));
    }


    public void syncRecordings() {
        Log.d(TAG, "syncRecordings()");
        // Sync eMMC recordings to SD card
        Thread thread = new Thread(() -> {
            if(!mSyncingRecordings) {
                mSyncingRecordings = true;
                storage.syncRecordingsToSd(logs.getCurrentRecordingDir());
                mSyncingRecordings = false;
            }
        });
        thread.start();
    }

    /******************** Recording Check **************/

    public void resetRecordingCheck() {
        mIgnRecCheck = 0;
    }

    private void handleRecordingFailure() {
        Log.d(TAG, "handleRecordingFailure()");

        boolean rebootNeeded = false;
        mIgnRecCheck = 0;

        Date now = Calendar.getInstance().getTime();

        if(mRecFailDate != null) {
            Log.d(TAG, "handleRecordingFailure()::" + (now.getTime() - mRecFailDate.getTime()));
        }

        if(mRecFailDate != null && (now.getTime() - mRecFailDate.getTime()) < (60 * 1000)) {
            rebootNeeded = true;

            eventTrigger(EVENT_TYPE.CAM_FAIL);
        }

        mRecFailDate = now;
        utilities.saveRecordingFailDate(mRecFailDate);

        // Don't lose any journey information so try a safe recorder stop
        journeyStop();
        utilities.sendLocalBroadcastMessage(LocalBroadcastMessage.Type.ignitionOff, null);

        try {
            if(rebootNeeded) {
                // Allow the camera an extra 3 seconds to send the notification
                Thread.sleep(3 * 1000);
            } else {
                // Give time to save last recorder fail time
                Thread.sleep(500);
            }
        } catch (InterruptedException ex) {
            Log.e(TAG, "Error delaying restart: " + ex.getMessage());
        } finally {
            if(rebootNeeded) {
                Log.d(TAG, "handleRecordingFailure()::rebooting");
                utilities.reboot(mLocation);
            } else {
                Log.d(TAG, "handleRecordingFailure()::quiting");
                System.exit(0);
            }
        }
    }

    /******************** Journey *********************/

    private void journeyStart() {
        Log.d(TAG, "journeyStart()");

        // Restart the status handler
        mStatusIntervalMs = STATUS_DRIVING_INTERVAL_MS;
        if (statusHandler != null) {
            statusHandler.removeCallbacks(statusRunner);
            statusHandler.postDelayed(statusRunner, 5 * 1000);
        }

        // Start the journey log handler
        if (journeyLogHandler != null) {
            journeyLogHandler.removeCallbacks(journeyLogRunner);
            journeyLogHandler.postDelayed(journeyLogRunner, JOURNEY_LOG_INTERVAL_MS);
        }

        motionResetMax();
    }

    private void journeyStop() {
        Log.d(TAG, "journeyStop()");

        // Stop the journey log handler
        if (journeyLogHandler != null) {
            journeyLogHandler.removeCallbacks(journeyLogRunner);
        }

        mStatusIntervalMs = STATUS_PARKED_INTERVAL_MS;

        if(mModel == KXB2 && mSubscription != null && mSubscription.accountId.startsWith("273d62c5-0030")) {
            Log.d(TAG, "journeyStop()::Setting parked status to 1 hour");
            mStatusIntervalMs = STATUS_PARKED_INTERVAL_MS * 2;
        }
    }


    public void addToJourneyLogQueue() {
        File journeyLog = logs.getCurrentRecordingJourneyLog();

        if(journeyLog != null) {
            azure.addToJourneyLogQueue(journeyLog);
        }
    }

    /******************** Handlers *********************/

    Runnable watchdogRunner = new Runnable() {
        @Override
        public void run() {
            watchdogHandler.postDelayed(watchdogRunner, WATCHDOG_INTERVAL_MS);

            // Watchdog App checker
            if(!utilities.isAppRunning("io.kasava.watchdog")) {
                // Start watchdog if it's not running
                utilities.startWatchdogApp();
            } else {
                // Reset the watchdog timer
                kasavaMessenger.sendMessage(KasavaBroadcastMessage.WATCHDOG_RESET, null);
            }

            // Daily reboot checker
            if (utilities.isDailyRebootDue(mRecorderState, mCamStartDate)) {
                Log.d(TAG, "2am reboot");
                utilities.reboot(mLocation);
            }

            // Check if we should restart the modem to try re-establish cellular connection
            mCellularOnTimeCount++;
            if(mCellularOnTimeCount > 30) {
                mCellularOnTimeCount = 0;

                if (azure.getOldestStatusInQueue() != null) {

                    int timeOutMs = 5 * 60 * 1000;

                    if(mRecorderState == RecorderState.STATE.NOT_RECORDING) {
                        timeOutMs = 35 * 60 * 1000;
                    }

                    if (Calendar.getInstance().getTime().getTime()
                            - utilities.azureDateToDate(azure.getOldestStatusInQueue().dateTime).getTime() > timeOutMs) {

                        Thread thread = new Thread(() -> {

                            cellular.setDataMode(true);
                            cellular.resetModem(mModel);

                            // Restart WiFi hotspot
                            wifi.start();

                            azure.processAllQueues();
                        });
                        thread.start();
                    }
                }
            }

            // If immobiliser is set but we haven't updated status in over 1 hour, turn off immobiliser
            if(mSubscription != null && mSubscription.immobState == 1 && mImmobEnabled && azure.getOldestStatusInQueue() != null) {
                if(Calendar.getInstance().getTime().getTime()
                        - utilities.azureDateToDate(azure.getOldestStatusInQueue().dateTime).getTime() > (4 * 60 * 60 * 1000)) {
                    Log.d(TAG, "immobiliser timeout reached");

                    mSubscription.immobState = 0;
                    utilities.saveSubscription(mSubscription);
                }
            }

            immobStateCheck(false);

            if (mRecorderState == RecorderState.STATE.RECORDING) {
                // Make sure Red LED is on
                ftdiAddToWriteQueue("RED:1", 1, false);
            }
        }
    };

    Runnable ignitionRunner = new Runnable() {
        @Override
        public void run() {
            ignitionHandler.postDelayed(ignitionRunner, IGNITION_INTERVAL_MS);

            if (mSelfTestInProgress) {
                switch (mSelfTestCount) {
                    case 0:
                    case 2:
                        ftdiAddToWriteQueue("IMB:1", 1, false);
                        mSelfTestCount++;
                        break;
                    case 1:
                    case 3:
                        ftdiAddToWriteQueue("IMB:0", 1, false);
                        mSelfTestCount++;
                        break;

                    default:
                        break;
                }
            }

            /*
            // Check screen state
            if (!mScreenOn) {
                mScreenOffCount++;

                Date now = Calendar.getInstance().getTime();

                if (mScreenOffCount > 50 ||
                        (mScreenOffCheck != null && now.getTime() - mScreenOffCheck.getTime() > 5000)) {
                    Log.d(TAG, "Turning screen on with WakeActivity");

                    wakeUpActivity(mScreenOn);
                    mScreenOffCount = 0;

                    if (statusHandler != null) {
                        statusHandler.removeCallbacks(statusRunner);
                        statusHandler.postDelayed(statusRunner, 10 * 1000);
                    }
                }

                mScreenOffCheck = now;
            }*/

            int ignCount = 0;


                boolean motionDetected = false;
                int diff = 50;

                if (mMaxX != 0 && mMaxY != 0 && mMaxZ != 0) {
                    if (mMaxX < (mLastMaxX - diff) || mMaxX > (mLastMaxX + diff) ||
                            mMaxY < mLastMaxY - diff || mMaxY > mLastMaxY + diff ||
                            mMaxZ < mLastMaxZ - diff || mMaxZ > mLastMaxZ + diff) {

                        motionDetected = true;
                    }

                    if (motionDetected && mLastMaxX != 0) {
                        ignCount = 5;
                    }

                    mLastMaxX = mMaxX;
                    mLastMaxY = mMaxY;
                    mLastMaxZ = mMaxZ;

            }
                for (int i = 0; i < 5; i++) {
                    if (!mSelfTestInProgress && utilities.getIgnitionStatus()) {
                        ignCount++;
                    }
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }


            boolean ignition = false;

            if (ignCount > 2) {
                ignition = true;
            } else if (ignCount > 0 && !mScreenOn) {
                wakeUpActivity(mScreenOn);
            }

            Log.d(TAG, "ignitionRunner::ST=" + mSelfTestInProgress + ", Ign=" + ignition);

            mIsCharging = ignition;

            if (ignition) {
                mIgnOffCount = 0;
            } else if (mRecorderState != RecorderState.STATE.NOT_RECORDING) {
                mIgnOffCount++;
            }

            if (ignition && mRecorderState == RecorderState.STATE.NOT_RECORDING) {
                // Check again in case is a wakeup from PIC
                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        if (utilities.getIgnitionStatus()) {
                            setRecorderState(RecorderState.STATE.PREPARING);
                        }
                    }
                }, 600);
            } else if (mIgnOffCount > IGN_OFF_DELAY_4S && mRecorderState != RecorderState.STATE.NOT_RECORDING) {
                Log.d(TAG, "ignitionRunner::OFF");
                journeyStop();
                utilities.sendLocalBroadcastMessage(LocalBroadcastMessage.Type.ignitionOff, null);

                mIgnLastOff = Calendar.getInstance().getTime();
            }

            // Check we are recording OK
            if (mModelType == Model.TYPE.KDC && ignition) {
                mIgnRecCheck++;
            } else {
                mIgnRecCheck = 0;
            }
            if (ignition && mIgnRecCheck > 8) {
                Log.d(TAG, "Not recording while ignition is on... let's restart");
                handleRecordingFailure();
            }


            /*
            // Check the last time ign was switched off
            if(mIgnLastOff != null && (Calendar.getInstance().getTime().getTime() - mIgnLastOff.getTime()) > (2 * 60 * 1000)) {// (5 * 24 * 60 * 60 * 1000)) {
                Log.d(TAG, "Powering off camera");

                mIgnLastOff = null;
                //TODO save mIgnLastOff

                Intent i = new Intent("android.intent.action.ACTION_REQUEST_SHUTDOWN");
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                i.putExtra("android.intent.extra.KEY_CONFIRM", true);
                startActivity(i);
            }*/

            if (mRecorderState == RecorderState.STATE.RECORDING) {
                // Make sure Red LED is on
                //ftdiAddToWriteQueue("RED:1", 1, false);
                //ftdiAddToWriteQueue("GRN:0");
            }
        }
    };

    Runnable cleanupRunner = new Runnable() {
        @Override
        public void run() {
            storage.cleanupEmmc();
            if(storage.isSdPresent()) {
                if(!storage.cleanupSd()) {
                    Log.d(TAG, "cleanupSd()::Failed, try reboot in 3 mins to recover SD");

                    if (timerSdReboot != null) {
                        timerSdReboot.cancel();
                    }

                    timerSdReboot = new Timer();
                    timerSdReboot.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            //utilities.reboot(mLocation);
                        }
                    }, 3 * 60 * 1000);

                }
            }

            cleanupHandler.postDelayed(cleanupRunner, CLEANUP_INTERVAL_MS);
        }
    };

    Runnable statusRunner = new Runnable() {
        @Override
        public void run() {
            statusHandler.postDelayed(statusRunner, mStatusIntervalMs);

            boolean isParked = false;

            if(mStatusIntervalMs >= STATUS_PARKED_INTERVAL_MS) {
                isParked = true;
            } else {
                mParkedStatus = 0;
            }

            if(mModel == KDC406 && isParked && mParkedStatus > 4 && mSubscription != null && mSubscription.accountId.startsWith("273d62c5-0030")) {
                return;
            }

            if(isParked) {
                mParkedStatus++;
            }

            if(isParked && !mScreenOn) {
                Log.d(TAG, "Turning screen on with WakeActivity");
                wakeUpActivity(mScreenOn);
            }

            // Get EV battery message
            if(mModel == KXB1 && mRecorderState != RecorderState.STATE.NOT_RECORDING && !mImmobEnabled) {
                ftdiAddToWriteQueue(Can.getCanRxStr(), 1, false);
                ftdiAddToWriteQueue(Can.getCanTxStrFuelTank(), 1, true);
                ftdiAddToWriteQueue(Can.getCanRxStr(), 1, false);
                ftdiAddToWriteQueue(Can.getCanTxStrEvBattery(), 1, true);
            }

            Thread thread = new Thread(() -> {
                if(!utilities.isUsbHostEnabled()) {
                    requestUsbHostEnabled(true);
                }
                ftdi.startPicUpdating("http://www.kasava.io/kasava/updates/kxb/pic/KXB_Upgrade_v1.1.hex");
            });
            //thread.start();

            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    Location location = new Location("");

                    if(mLocation != null) {
                        location = mLocation;
                    } else {
                        location.setLatitude(0);
                        location.setLongitude(0);
                        location.setSpeed(0);
                        location.setBearing(0);
                        location.setAltitude(0);
                        location.setAccuracy(0);
                    }

                    Log.d(TAG, "statusRunner::" + location.getLatitude() + ", " + location.getLongitude());

                    Status status = new Status();
                    status.dateTime = utilities.dateToAzureDate(Calendar.getInstance().getTime());
                    status.parked = mRecorderState == RecorderState.STATE.NOT_RECORDING;
                    status.latitude = location.getLatitude();
                    status.oldestFootageDateTime = utilities.dateToAzureDate(storage.getOldestRecordDate());
                    status.longitude = location.getLongitude();
                    status.speed = location.getSpeed();
                    status.heading = location.getBearing();
                    status.altitude = location.getAltitude();
                    status.accuracy = location.getAccuracy();
                    status.sdCard = storage.isSdPresent();
                    status.sdCardSize = (int) storage.getSdTotalStorageMb();
                    status.cameraCount = mCameraCount;
                    status.immob = mImmobEnabled;
                    status.isCharging = mIsCharging;
                    status.backupBatt = mBackupBatt;
                    status.fuelTank = mCanFuelTank;
                    status.evBattery = mCanEvBattery;

                    Log.d(TAG, "Fuel: " + mCanFuelTank + ", EV:" + mCanEvBattery);

                    azure.addToStatusQueue(status);
                }
            }, 3 * 1000);
        }
    };

    Runnable journeyLogRunner = new Runnable() {
        @Override
        public void run() {
            journeyLogHandler.postDelayed(journeyLogRunner, JOURNEY_LOG_INTERVAL_MS);

            final String nowAsUtcDateTimeStr = utilities.nowAsUtcDateTimeString();

            Location location = new Location("");

            if (mLocation != null) {
                location = mLocation;
            }

            final String journeyLogLine = String.format(getResources().getString(R.string.journey_format),
                    nowAsUtcDateTimeStr, utilities.getLocationDateTime(location.getTime()),
                    utilities.getLatitudeDegreesFrom(location.getLatitude()), utilities.getLongitudeDegreesFrom(location.getLongitude()),
                    location.getSpeed(), location.getBearing(), location.getAltitude(), location.getAccuracy(), mMaxX, mMaxY, mMaxZ, mPitch, mCanFuelTank, mCanEvBattery, mBackupBatt);

            // Reset event detection if required and if motion is steady
            if(motion.getMotionState() == MOTION_STATE.READY_TO_CLEAR) {
                if((mModelType == Model.TYPE.KDC && Math.abs(mMaxX) > 1 && Math.abs(mMaxX) < 300 &&
                    Math.abs(mMaxY) > 1 && Math.abs(mMaxY) < 200 && Math.abs(mMaxZ) > 1 && Math.abs(mMaxZ) < 200) ||
                        ((mModel == Model.MODEL.KXB3 || mModel == Model.MODEL.KXB4) && Math.abs(mMaxX) > 1 && Math.abs(mMaxX) < 250) ||
                        (mModel == KXB5 && Math.abs(mMaxZ) > 1 && Math.abs(mMaxZ) < 250) ) {

                    Log.d(TAG, "journeyLogRunner::Enabled events from motion sensor");
                    motion.setMotionState(MOTION_STATE.ENABLED);
                }
            }



            motionResetMax();

            Log.d(TAG, "journeyLogRunner::" + journeyLogLine);
            logs.writeRecordingJourneyLog(journeyLogLine);
        }
    };


    /******************** Location *********************/

    private void locationStart() {
        Log.d(TAG, "locationStart()");
        try {
            mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if(mLocationManager != null) {
                mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 100, 0, this, Looper.getMainLooper());
                Log.d(TAG, "locationStart()::requested location updates");
            }
        } catch (SecurityException ex) {
            Log.e(TAG, "locationStart()::failed to register for location: " + ex.getMessage());
        }
    }

    private void locationStop() {
        Log.d(TAG, "locationStop()");
        if (mLocationManager != null) {
            mLocationManager.removeUpdates(this);
        }
    }

    public void onLocationChanged(Location location) {

        if (location == null) {
            Log.e(TAG, "Location::onLocationChanged()::location is null");
            return;
        }

        // Convert to Kpm
        location.setSpeed(location.getSpeed() * 3.6f);

        // Set date & time from GPS if not set already
        if (mCamStartDate == null) {
            boolean useOffset = true;
            if(mModel.equals(Model.MODEL.KDC403) || mModel.equals(Model.MODEL.KDC403a) ||
                    mModel.equals(Model.MODEL.KDC405) || mModel.equals(KDC406) ||
                    mModel.equals(KXB3) || mModel.equals(KXB4) || mModel.equals(KXB5)) {
                useOffset = false;
            }
            utilities.setAndroidDate(location.getTime(), useOffset);
            mCamStartDate = Calendar.getInstance().getTime();
        }

        // Check for speeding, acceleration & braking
        if (mRecorderState == RecorderState.STATE.RECORDING) {

            // Check for speeding
            int mSpeedLimit = 131;

            if (location.getSpeed() > mSpeedLimit && !mSpeedEventActive) {
                mSpeedEventActive = true;
                // Start speeding event

            } else if (location.getSpeed() < (mSpeedLimit - 5)) {
                mSpeedEventActive = false;
            }

            // Check for harsh acceleration & braking
            if(mLocation != null && location.getSpeed() != mLocation.getSpeed()) {
                mLocationBuffer.add(location);

                if(mLocationBuffer.size() > 20) {
                    mLocationBuffer.remove(0);
                }

                if(mLocationBuffer.size() > 5) {
                    float accelerationRate = mLocationBuffer.get(mLocationBuffer.size() - 1).getSpeed()
                            - mLocationBuffer.get(mLocationBuffer.size() - 3).getSpeed();

                    if (accelerationRate > mAccelRate) {// && mAccelBrakeMotion == EVENT_TYPE.ACCELERATION) {
                        boolean invalidAlert = false;
                        for (Location location1 : mLocationBuffer) {
                            if (location1.getSpeed() < 2) {
                                invalidAlert = true;
                                break;
                            }
                        }
                        if (mModelType == Model.TYPE.KDC && mEventAccel && !invalidAlert) {
                            // Start acceleration event
                            eventTrigger(EVENT_TYPE.ACCELERATION);
                        }
                    } else if (mEventBrake && accelerationRate < -mBrakeRate) {// && mAccelBrakeMotion == EVENT_TYPE.BRAKE) {
                        // Start braking event
                        eventTrigger(EVENT_TYPE.BRAKE);
                    }
                }
            }
        }

        // Restart status runner if this is the 1st location
        if(mLocation == null) {
            statusHandler.removeCallbacks(statusRunner);
            statusHandler.postDelayed(statusRunner, 500);

            utilities.ledBlueEnabled(true);
        }

        mLocation = location;
    }

    public void onStatusChanged(String provider, int status, Bundle extras) {
        //Log.d(TAG, "Location::onStatusChanged()::provider=" + provider + " status=" + status);
    }

    @Override
    public void onProviderEnabled(String provider) {
        Log.d(TAG, "Location::onProviderEnabled()::" + provider);
    }

    @Override
    public void onProviderDisabled(String provider) {
        Log.d(TAG, "Location::onProviderDisabled()::" + provider);
    }

    public Location getLocation() {
        return mLocation;
    }


    /******************** Motion *********************/

    private void motionStart() {
        try {
            mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

            if(mSensorManager != null) {
                if (mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
                    mSensorManager.registerListener(this,
                            mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), 30000);
                }
            }
        } catch (SecurityException ex) {
            Log.e(TAG, "Failed to register Sensor");
        }
    }

    private void motionStop() {
        if (mSensorManager != null) {
            mSensorManager.unregisterListener(this);
        }
    }

    private void motionResetMax() {
        mMaxX = 0;
        mMaxY = 0;
        mMaxZ = 0;
    }

    private void motionUpdateAverages() {

    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            int x, y, z;

            if(mModel.equals(Model.MODEL.KDC402) || mModel.equals(Model.MODEL.KDC402a)) {
                x = -(int) (event.values[0] * SENSOR_TO_MG);
                y = -(int) (event.values[1] * SENSOR_TO_MG);
                z = (int) (event.values[2] * SENSOR_TO_MG);
            } else if (mModel.equals(Model.MODEL.KDC403) || mModel.equals(Model.MODEL.KDC403a)) {
                x = -(int) (event.values[0] * SENSOR_TO_MG);
                y = (int) (event.values[2] * SENSOR_TO_MG);
                z = (int) (event.values[1] * SENSOR_TO_MG);
            } else if (mModel.equals(Model.MODEL.KDC405) || mModel.equals(KDC406)) {
                x = -(int) (event.values[1] * SENSOR_TO_MG);
                y = (int) (event.values[0] * SENSOR_TO_MG);
                z = (int) (event.values[2] * SENSOR_TO_MG);
            } else if (mModel.equals(KXB3) || mModel.equals(KXB4) || mModel.equals(KXB5)) {
                x = (int) (event.values[2] * SENSOR_TO_MG);
                y = (int) (event.values[1] * SENSOR_TO_MG);
                z = -(int) (event.values[0] * SENSOR_TO_MG);
            } else {
                x = -(int) (event.values[0] * SENSOR_TO_MG);
                y = (int) (event.values[1] * SENSOR_TO_MG);
                z = -(int) (event.values[2] * SENSOR_TO_MG);
            }
            //Log.d(TAG, x + " " + y + " " + z);

            if (mModel == KXB5 || mRecorderState != RecorderState.STATE.NOT_RECORDING) {
                if(mLocation != null) {
                    mPitch = motion.getMotionRotation(y, z, mLocation.getSpeed());
                } else {
                    mPitch = motion.getMotionRotation(y, z, 0);
                }

                int trueY = 0;
                int trueZ = 0;

                if(mModelType == Model.TYPE.KDC) {
                    trueY = motion.getTrueY(y, z, mPitch);
                    trueZ = motion.getTrueZ(y, z, mPitch);
                } else if(mModelType == Model.TYPE.KXB) {
                    trueY = y;
                    trueZ = z;
                }

                // Check for motion events
                if(motion.getMotionState() == MOTION_STATE.ENABLED) {
                    motion.addToMotionEventBuffers(x, trueY, trueZ);
                }

                if(Math.abs(x) > Math.abs(mMaxX)) { mMaxX = x; }
                if(Math.abs(trueY) > Math.abs(mMaxY)) { mMaxY = trueY; }
                if(Math.abs(trueZ) > Math.abs(mMaxZ)) { mMaxZ = trueZ; }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.d(TAG, "onAccuracyChanged()::" + "name=" + sensor.getName() + " accuracy=" + accuracy);
    }


    /******************** LiveView *********************/

    public void azureUploadLiveView(final byte[] data) {
        Thread thread = new Thread(() -> azure.uploadLiveView(data));
        thread.start();
    }


    public void checkDriverDistraction(String filePath) {
        kasavaMessenger.sendMessage(REQUEST_DRIVER_CHECK, filePath);
    }

    /******************** Health Check *********************/

    public void azureAddToHealthCheckQueue(final HealthCheckData healthCheckData) {
        Thread thread = new Thread(() -> azure.addToHealthCheckQueue(healthCheckData));
        thread.start();
    }

    /******************** Events & Clips *********************/

    public void eventTrigger(final EVENT_TYPE eventType) {

        final Date now = Calendar.getInstance().getTime();

        if(eventType == EVENT_TYPE.ALERT) {
            utilities.playAudio(R.raw.alertbuttonactivated, 1900, 100);
        }

        // Delay the event creation to give time to analyse location data
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                // Create the event
                Event event = events.eventTrigger(eventType, now, mLocationBuffer);

                if(event != null && event.dateTime != null) {
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(utilities.utcDateTimeStringToDate(event.dateTime));
                    cal.add(Calendar.SECOND, -5);
                    if (eventType.equals(KdcService.EVENT_TYPE.ALERT)) {
                        // Go back an extra 15 seconds for alert button presses
                        cal.add(Calendar.SECOND, -15);
                    } else if (eventType.equals(EVENT_TYPE.ZIGZAG)) {
                        // Go back an extra 5 seconds for zigzags
                        cal.add(Calendar.SECOND, -5);
                    }
                    event.clipDateTime = utilities.dateAsUtcDateTimeString(cal.getTime());

                    if (event.type >= 10) {
                        Clip clip = new Clip();
                        clip.startDateTime = event.clipDateTime;
                        clip.durationS = 10;
                        if (eventType.equals(KdcService.EVENT_TYPE.ALERT)) {
                            // Create 20 second clips for alert button presses
                            clip.durationS = 30;
                        }
                        clip.isEvent = true;
                        if(event.type == 10 || event.type == 15 || event.type == 20) {
                            clip.upload = true;
                        }

                        clip.upload = true;

                        Log.d(TAG, "eventTrigger()::adding to clip queue");
                        addToClipQueue(clip);
                    }

                    // Add event to azure queue
                    azure.addToEventQueue(event);
                }
            }
        }, 10 * 1000);
    }

    private void addToClipQueue(Clip clip) {
        mClipQueue.add(clip);
        utilities.saveListClips(mClipQueue);
        processClipQueue();
    }

    public void processClipQueue() {
        Thread thread = new Thread(() -> {
            Log.d(TAG, "processClipQueue()::queue size: " + mClipQueue.size());

            if (!mClipInProgress && !mClipQueue.isEmpty()) {
                mClipInProgress = true;

                boolean runQueueAgain = true;

                Clips.CLIP_REQUEST clipRequest = clips.requestToCreateClipFiles(mClipQueue.get(0), logs.getCurrentRecordingDir());

                switch (clipRequest) {
                    case ERROR:
                        mClipQueue.remove(0);
                        Event clipEvent = new Event();
                        clipEvent.dateTime = utilities.dateAsUtcDateTimeString(Calendar.getInstance().getTime());
                        clipEvent.type = 5;
                        clipEvent.notes = mClipQueue.get(0).startDateTime;

                        break;

                    case NO_FOOTAGE:
                        // TODO create empty clip folder & upload
                        mClipQueue.remove(0);
                        break;

                    case WAIT:
                        utilities.sendLocalBroadcastMessage(LocalBroadcastMessage.Type.requestRecordingChangeDir, null);
                        runQueueAgain = false;
                        break;

                    case PROCEED:
                        if (mClipQueue.get(0).attempt < 3) {
                            mClipQueue.get(0).attempt++;
                            utilities.saveListClips(mClipQueue);

                            createAndUploadClip(mClipQueue.get(0));
                        }
                        mClipQueue.remove(0);
                        utilities.saveListClips(mClipQueue);
                        break;
                }

                mClipInProgress = false;

                if(runQueueAgain) {
                    // Process next item in queue
                    processClipQueue();
                }
            }
        });
        thread.start();
    }

    private void createAndUploadClip(final Clip clip) {

        File dirToUpload = clips.createClipFiles(clip);

        if(dirToUpload != null && clip.upload) {
            File[] clipFiles = dirToUpload.listFiles();

            for(File clipFile: clipFiles) {
                if(clipFile.getName().contains(Constants.LOG_EXTENSION)) {
                    // Upload log files without splitting
                    Blob blob = new Blob();
                    blob.fileName = clipFile.getParentFile().getName() + "/" + clipFile.getName();
                    blob.file = clipFile;
                    blob.isEvent = clip.isEvent;
                    azure.addToBlobQueue(blob);
                } else {
                    splitAndUploadClipFile(clipFile, clip.isEvent);
                }
            }
        }
    }

    private void splitAndUploadClipFile(File fileToUpload, boolean isEvent) {

        final int BUFFER_SIZE = 2500000;

        try {
            InputStream inStream = new BufferedInputStream(new FileInputStream(fileToUpload));
            byte[] byteBuffer = new byte[BUFFER_SIZE];
            long bytesToRead = fileToUpload.length();
            int fileParts = filePartsCalc(bytesToRead, BUFFER_SIZE);

            for (int i = 1; i <= fileParts; i++) {
                String fileNameExtension = "#" + i + "-" + fileParts + Constants.VIDEO_PART_EXTENSION;
                String outputPath = fileToUpload.getAbsolutePath() + fileNameExtension;
                BufferedOutputStream buffOutStream = new BufferedOutputStream(new FileOutputStream(outputPath));

                long bytesToReadWrite = bytesToRead;
                if (bytesToReadWrite > BUFFER_SIZE) {
                    bytesToReadWrite = BUFFER_SIZE;
                }
                inStream.read(byteBuffer, 0, (int) bytesToReadWrite);
                buffOutStream.write(byteBuffer, 0, (int) bytesToReadWrite);

                buffOutStream.flush();
                buffOutStream.close();

                Blob blob = new Blob();
                blob.fileName =  fileToUpload.getParentFile().getName() + "/" + fileToUpload.getName() + fileNameExtension;
                blob.file = new File(outputPath);
                blob.isEvent = isEvent;
                azure.addToBlobQueue(blob);

                bytesToRead -= bytesToReadWrite;
            }

            fileToUpload.delete();
        } catch (IOException ex) {

        }
    }

    private int filePartsCalc(long fileLength, int bufferSizer) {

        int times = 0;

        while(fileLength > 0){
            fileLength = fileLength - bufferSizer;
            times++;
        }

        return times;
    }

    /******************** Immobiliser *********************/

    private void immobStateCheck(boolean forceChange) {
        int immobState = 0;
        boolean immobEnabled = false;

        if(mSubscription != null) {
            immobState = mSubscription.immobState;
        }

        if(immobState == 1) {
            immobEnabled = true;
        } else if(immobState == 2 && mCamStartDate != null) { // Scheduled
            Calendar calendar = Calendar.getInstance();
            int day = calendar.get(Calendar.DAY_OF_WEEK);
            int hour = calendar.get(Calendar.HOUR_OF_DAY);
            int hourEn = 0;

            if (mSubscription != null) {

                switch (day) {
                    case Calendar.MONDAY:
                        hourEn = mSubscription.immobMon >> hour;
                        break;
                    case Calendar.TUESDAY:
                        hourEn = mSubscription.immobTue >> hour;
                        break;

                    case Calendar.WEDNESDAY:
                        hourEn = mSubscription.immobWed >> hour;
                        break;

                    case Calendar.THURSDAY:
                        hourEn = mSubscription.immobThu >> hour;
                        break;

                    case Calendar.FRIDAY:
                        hourEn = mSubscription.immobFri >> hour;
                        break;

                    case Calendar.SATURDAY:
                        hourEn = mSubscription.immobSat >> hour;
                        break;

                    case Calendar.SUNDAY:
                        hourEn = mSubscription.immobSun >> hour;
                        break;
                }
            }

            if ((hourEn & 0x1) == 1) {
                immobEnabled = true;
            }
        }

        // Update the immobilser state only if not recording (i.e. ignition is off)
        if((forceChange && !immobEnabled) || (immobEnabled != mImmobEnabled && (mRecorderState == RecorderState.STATE.NOT_RECORDING || !immobEnabled))) {
            mImmobEnabled = immobEnabled;

            if(mImmobEnabled) {
                ftdiAddToWriteQueue("IMB:1", 2, false);
            } else {
                ftdiAddToWriteQueue("IMB:0", 2, false);
            }

            // Restart the status handler
            if (statusHandler != null) {
                statusHandler.removeCallbacks(statusRunner);
                statusHandler.postDelayed(statusRunner, 500);
            }
        }
    }


    /******************** Activities *********************/

    private int wakeUpActivity(boolean screenOn) {
        if (!screenOn) {
            // Start the wake activity if screen is off
            Intent wakeIntent = new Intent(mContext, WakeActivity.class);
            wakeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            wakeIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            mContext.startActivity(wakeIntent);

            return (3 * 1000);
        } else {
            return 1000;
        }
    }

    private void selfTestActivity() {
        if(mModel == KXB1 || mModel == KXB2 || mModel == KXB3 || mModel == KXB4 || mModel == KXB5) {
            ftdiAddToWriteQueue("RED:1", 2, false);
        }
        ftdiAddToWriteQueue("USB", 2, false);

        // Start the selfTest activity
        Intent intent = new Intent(this, SelfTestActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        this.startActivity(intent);
    }

    private void recorderActivity() {
        // Start the recorder activity
        Intent recorderIntent = new Intent(mContext, RecorderActivity.class);
        recorderIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        recorderIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        mContext.startActivity(recorderIntent);
    }

    private void liveViewActivity(int durationS) {
        ftdiAddToWriteQueue("USB", 2, false);

        // Start the liveView activity
        Intent liveViewIntent = new Intent(mContext, LvActivity.class);
        liveViewIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        liveViewIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        liveViewIntent.putExtra("durationS", durationS);
        mContext.startActivity(liveViewIntent);
    }

    public void setLiveViewRunningState(boolean state) {
        mLiveViewRunning = state;
    }

    /********* Self Test *********/

    public void setSelfTestInProgress(boolean selfTestInProgress) {
        mSelfTestCount = 0;
        mSelfTestInProgress = selfTestInProgress;
    }


    /********* Local Broadcast Receiver *********/

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            String extra = "";
            LocalBroadcastMessage.Type messageType = (LocalBroadcastMessage.Type) intent.getSerializableExtra(LocalBroadcastMessage.ID);
            if(intent.hasExtra(LocalBroadcastMessage.EXTRA)){
                extra = intent.getStringExtra(LocalBroadcastMessage.EXTRA);
            }

            switch(messageType) {

                case loginOk:
                    // Register for push notifications
                    new RegisterForPushNotificationsAsync().execute();
                    Pushy.listen(mContext);
                    break;

                case updateSubscription:
                    Thread thread = new Thread(() -> azure.updateSubscription(true));
                    thread.start();
                    break;

                case syncSubscription:
                    syncSubscription();
                    break;

                case wakeUp:
                    if(mRecorderState == RecorderState.STATE.NOT_RECORDING) {
                        utilities.setScreenTimeout(10);
                        wakeUpActivity(false);
                    }
                    break;

                case healthCheckManual:
                    int healthCheckDelay = wakeUpActivity(mScreenOn);

                    new Timer().schedule(new TimerTask() {
                        @Override
                        public void run() {
                            if(!mSelfTestInProgress && mRecorderState == RecorderState.STATE.NOT_RECORDING){
                                mSelfTestInProgress = true;
                                selfTestActivity();
                            }
                        }
                    }, healthCheckDelay);
                    break;

                case reboot:
                    Log.d(TAG, "mMessageReceiver::reboot");
                    mCamStartDate = null; // Stops recording restart
                    utilities.sendLocalBroadcastMessage(LocalBroadcastMessage.Type.ignitionOff, null);
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ex) {
                        Log.e(TAG, "Error delaying reboot: " + ex.getMessage());
                    } finally {
                        utilities.reboot(mLocation);
                    }
                    break;

                case shutdown:
                    /*if(mModel == KDC406 && mRecorderState == RecorderState.STATE.RECORDING) {
                        Log.d(TAG, "mMessageReceiver::shutdown");
                        mCamStartDate = null; // Stops recording restart
                        utilities.sendLocalBroadcastMessage(LocalBroadcastMessage.Type.ignitionOff, null);
                        cellular.setAirplaneModeOn(mModel, true);
                    }*/
                    break;

                case terminalCmd:
                    utilities.setScreenTimeout(10);
                    wakeUpActivity(mScreenOn);

                    String cmd = intent.getStringExtra("cmd");
                    azure.addToCmdQueue(utilities.terminalCommand(cmd));
                    break;

                case updateRequest:
                    String updateUri = intent.getStringExtra("uri");
                    kasavaMessenger.sendMessage(KasavaBroadcastMessage.UPDATE, updateUri);

                    // Force camera to wake up for the upgrade
                    utilities.setScreenTimeout(30);
                    wakeUpActivity(mScreenOn);
                    break;

                case backupBatt:
                    int battPer = intent.getIntExtra("value", -1);

                    if(mIsCharging){
                        battPer = (int)((battPer - 1300) / 2.2);
                    } else  {
                        battPer = (int)((battPer - 1230) / 2.45);
                    }

                    // Check not exceeding limits
                    if(battPer < 1) {
                        battPer = 1;
                    } else if(battPer > 100) {
                        battPer = 100;
                    }

                    if(mBackupBatt == -1) {
                        mBackupBatt = battPer;
                    } else if(mIsCharging && battPer > mBackupBatt) {
                        mBackupBatt++;
                    } else if(!mIsCharging && battPer < mBackupBatt) {
                        int battDrop = mBackupBatt - battPer;
                        if(battDrop > 6) {
                            battDrop = 6;
                        }
                        mBackupBatt -= battDrop;
                    }

                    // Recheck not exceeding limits
                    if(mBackupBatt < 1) {
                        mBackupBatt = 1;
                    } else if(mBackupBatt > 100) {
                        mBackupBatt = 100;
                    }

                    Log.d(TAG, "New battery perc: " + mBackupBatt);

                    break;

                case cameraFound:
                    String camNo = intent.getStringExtra("camNo");

                    switch (camNo) {
                        case "1":
                            mCam1Found = true;
                            break;
                        case "2":
                            mCam2Found = true;
                            break;
                        case "3":
                            mCam3Found = true;
                            break;
                    }

                    break;

                case ftdiQueueEmpty:
                    if(mRecorderState == RecorderState.STATE.NOT_RECORDING) {
                        //requestUsbHostEnabled(false);
                    }
                    break;

                case ftdiCanRxMsg:
                    String rxMsg = intent.getStringExtra("msg");
                    ftdiAddToWriteQueue("CRX:" + rxMsg, 1, false);
                    break;

                case ftdiCanTxMsg:
                    //String txMsg = intent.getStringExtra("msg");
                    //ftdiAddToWriteQueue(Can.getCanRxStr1(), 1, false);
                    //ftdiAddToWriteQueue(Can.getCanTxStr1(), 1, true);
                    //ftdiAddToWriteQueue(Can.getCanRxStr2(), 1, false);
                    //ftdiAddToWriteQueue(Can.getCanTxStr2(), 1, true);
                    break;

                case alertButton:
                    if(mRecorderState == RecorderState.STATE.RECORDING) {
                        eventTrigger(KdcService.EVENT_TYPE.ALERT);
                    }
                    break;

                case liveViewStart:
                    // Start the liveView activity if not recording
                    if(!mLiveViewRunning && mRecorderState == RecorderState.STATE.NOT_RECORDING && !mSelfTestInProgress) {
                        // Turn on USB camera power
                        requestUsbHostEnabled(true);

                        String durationS = intent.getStringExtra("durationS");
                        int liveViewDelay = wakeUpActivity(mScreenOn);

                        new Timer().schedule(new TimerTask() {
                            @Override
                            public void run() {
                                if(mRecorderState == RecorderState.STATE.NOT_RECORDING) {
                                    liveViewActivity(Integer.parseInt(durationS));
                                }
                            }
                        }, liveViewDelay);
                    }

                    break;

                case event:
                    switch (extra) {
                        case "SD_UNMOUNTED":
                            // Start timer notify SD removal
                            if (timerSdUnmounted != null) {
                                timerSdUnmounted.cancel();
                            }

                            timerSdUnmounted = new Timer();
                            timerSdUnmounted.schedule(new TimerTask() {
                                @Override
                                public void run() {
                                    if(!storage.isSdPresent()) {
                                        eventTrigger(EVENT_TYPE.SD_UNMOUNTED);
                                    }
                                }
                            }, 10 * 1000);

                            break;
                        case "SD_MOUNTED":
                            Log.d(TAG, "mMessageReceiver::cancelling SD event");
                            if (timerSdUnmounted != null) {
                                timerSdUnmounted.cancel();
                            }
                            break;
                        case "SE_SHOCK":
                            eventTrigger(EVENT_TYPE.SHOCK);
                            break;
                        case "SE_TURN":
                            if(mLocationBuffer.size() > 1 && mLocationBuffer.get(mLocationBuffer.size()-1).getSpeed() > 10) {
                                eventTrigger(EVENT_TYPE.TURN);
                            }
                            break;
                        case "SE_MANDOWN":
                            eventTrigger(EVENT_TYPE.MANDOWN);
                            break;

                        case "SE_ZIGZAG":
                            eventTrigger(EVENT_TYPE.ZIGZAG);
                            break;
                    }
                    break;

                case processQueue:
                    azure.processQueue(intent.getStringExtra("queue"));
                    break;

                case clearQueue:
                    if(intent.getStringExtra("queue").equals("Clips")) {
                        mClipQueue.clear();
                        utilities.saveListClips(mClipQueue);
                    } else {
                        azure.clearQueue(intent.getStringExtra("queue"));
                    }
                    break;

                case removeFromQueue:
                    azure.removeFromQueue(intent.getStringExtra("queue"), Integer.parseInt(intent.getStringExtra("quantity")));
                    break;

                case customVideoRequest:
                    // Force camera to wake up for the video uploads
                    utilities.setScreenTimeout(20);
                    wakeUpActivity(mScreenOn);

                    String startDateTime = intent.getStringExtra("startDateTime");
                    int durationS = Integer.parseInt(intent.getStringExtra("durationS"));
                    Log.d(TAG, "customVideoRequest()::" + startDateTime + " " + durationS);

                    try {
                        String[] startDateTimeParts = startDateTime.split("-");
                        if (startDateTimeParts.length == 6) {

                            Clip clip = new Clip();

                            @SuppressLint("SimpleDateFormat")
                            SimpleDateFormat sdf = new SimpleDateFormat(Constants.DATETIME_FORMAT_LONG);
                            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
/*
                            clip.startDateTime = sdf.parse(startDateTimeParts[0] + "-" + startDateTimeParts[1] +
                                    "-" + startDateTimeParts[2] + "-" + startDateTimeParts[3] + "-" + startDateTimeParts[4] +
                                    "-" + startDateTimeParts[5] + "-00");
*/
                            clip.startDateTime = startDateTime + "-00";
                            clip.durationS = durationS;
                            clip.upload = true;
                            clip.attempt = 0;

                            addToClipQueue(clip);
                        }
                    } catch(Exception ex) {
                        Log.e(TAG, "customVideoRequest()::" + "Error creating clip");
                    }
                    break;

                case eventVideoRequest:
                    String dateTime = intent.getStringExtra("dateTime");
                    Log.d(TAG, "eventVideoRequest()::" + dateTime);

                    File dirToUpload = null;

                    File[] eventDirs = new File(storage.getEmmcRootDir(), Constants.FOLDER_EVENTS).listFiles();

                    for(File eventDir: eventDirs) {
                        if(eventDir.getName().contains(dateTime)) {
                            dirToUpload = eventDir;
                        }
                    }

                    if(dirToUpload != null) {
                        File[] eventFiles = dirToUpload.listFiles();

                        for(File eventFile: eventFiles) {
                            if(eventFile.getName().contains(Constants.LOG_EXTENSION)) {
                                // Upload log files without splitting
                                Blob blob = new Blob();
                                blob.fileName = eventFile.getParentFile().getName() + "/" + eventFile.getName();
                                blob.file = eventFile;
                                blob.isEvent = true;
                                azure.addToBlobQueue(blob);
                            } else {
                                splitAndUploadClipFile(eventFile, true);
                            }
                        }
                    }

                    break;

                case fileRequest:
                    utilities.setScreenTimeout(20);
                    wakeUpActivity(mScreenOn);

                    String fileStr = intent.getStringExtra("file");
                    File fileToUpload = new File(fileStr);

                    if(fileToUpload.exists()) {
                        Blob newBlob = new Blob();

                        newBlob.file = fileToUpload;
                        newBlob.fileName = "requested/" + fileToUpload.getName();
                        newBlob.isEvent = false;

                        azure.addToBlobQueue(newBlob);
                    }

                    break;

                case canData:
                    if(intent.getStringExtra("type").equals("FUEL_TANK_PERC")) {
                        mCanFuelTank = intent.getDoubleExtra("value", 0);
                    }
                    else if(intent.getStringExtra("type").equals("EV_BATTERY_PERC")) {
                        mCanEvBattery = intent.getDoubleExtra("value", 0);
                    }
                    break;

                case canRaw:
                    azure.addToCmdQueue(intent.getStringExtra("msg"));
                    break;

                default:
                    break;
            }
        }
    };


    /********* Broadcast Receiver *********/

    public void onMessageReceived(final String type, final String message) {

        Log.d(TAG, "Type: " + type + " Message: " + message);

        switch (type) {
            case KasavaBroadcastMessage.WATCHDOG_APP_VERSION:
                Log.d(TAG, "Watchdog version = " + message);
                azure.setWdVersion(message);
                break;

            case KasavaBroadcastMessage.WAKEUP:
                Log.d(TAG, "WakeUp from local broadcast");
                wakeUpActivity(mScreenOn);
                break;

            case KasavaBroadcastMessage.DRIVER_DISTRACTED:
                switch (message) {
                    case "mobile":
                        eventTrigger(EVENT_TYPE.MOBILE);
                        Log.d(TAG, "Distracted driver: mobile");
                        break;

                    case "eating":
                        eventTrigger(EVENT_TYPE.EATING);
                        Log.d(TAG, "Distracted driver: eating");
                        break;
                }
        }
    }


    /******************** Pushy *********************/

    private class RegisterForPushNotificationsAsync extends AsyncTask<Void, Void, Exception> {
        protected Exception doInBackground(Void... params) {
            try {
                // Assign a unique token to this device
                String deviceToken = Pushy.register(getApplicationContext());

                Log.d(TAG, "PushTag = " + deviceToken);
                azure.setPushTag(deviceToken);

            } catch (Exception ex) {
                return ex;
            }

            // Success
            return null;
        }
    }
}