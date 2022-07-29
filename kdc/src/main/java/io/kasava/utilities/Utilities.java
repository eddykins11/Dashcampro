package io.kasava.utilities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.location.Location;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.telephony.TelephonyManager;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import io.kasava.broadcast.LocalBroadcastMessage;
import io.kasava.data.Blob;
import io.kasava.data.Clip;
import io.kasava.data.Constants;
import io.kasava.data.Event;
import io.kasava.data.Model;
import io.kasava.data.RecorderState;
import io.kasava.data.Status;
import io.kasava.data.Subscription;

import static io.kasava.data.Model.MODEL.KBC02;
import static io.kasava.data.Model.MODEL.KDC402;
import static io.kasava.data.Model.MODEL.KDC402a;
import static io.kasava.data.Model.MODEL.KDC403;
import static io.kasava.data.Model.MODEL.KDC404;
import static io.kasava.data.Model.MODEL.KDC404a;
import static io.kasava.data.Model.MODEL.KDC405;
import static io.kasava.data.Model.MODEL.KDC406;
import static io.kasava.data.Model.MODEL.KTR1;
import static io.kasava.data.Model.MODEL.KXB1;
import static io.kasava.data.Model.MODEL.KXB2;
import static io.kasava.data.Model.MODEL.KXB3;
import static io.kasava.data.Model.MODEL.KXB3a;
import static io.kasava.data.Model.MODEL.KXB4;
import static io.kasava.data.Model.MODEL.KXB5;
import static io.kasava.data.Model.TYPE.KBC;
import static io.kasava.data.Model.TYPE.KDC;
import static io.kasava.data.Model.TYPE.KTR;
import static io.kasava.data.Model.TYPE.KXB;
import static java.lang.Math.abs;

public class Utilities {

    private static String TAG = "Utilities";

    private Context mContext;

    private Model.TYPE mModelType;
    private Model.MODEL mModel;

    private MediaPlayer mPlayer;

    public Utilities(Context context) {
        mContext = context;
        mPlayer = new MediaPlayer();
    }

    // Items which are saved. Numbers are incremented when data object is updated to prevent load crashes
    private static final String SUBSCRIPTION_KEY = "SUBSCRIPTION_1_KEY";
    private static final String SUBSCRIPTION_NAME = "SUBSCRIPTION_1_NAME";
    private static final String RECFAILDATE_KEY = "RECFAILDATE_1_KEY";
    private static final String RECFAILDATE_NAME = "RECFAILDATE_1_NAME";
    private static final String LASTLOCATION_KEY = "LASTLOCATION_1_KEY";
    private static final String LASTLOCATION_NAME = "LASTLOCATION_1_NAME";
    private static final String JOURNEY_LOGS_QUEUE_KEY = "JOURNEY_LOGS_QUEUE_1_KEY";
    private static final String JOURNEY_LOGS_QUEUE_NAME = "JOURNEY_LOGS_QUEUE_1_NAME";
    private static final String STATUS_QUEUE_KEY = "STATUS_QUEUE_1_KEY";
    private static final String STATUS_QUEUE_NAME = "STATUS_QUEUE_1_NAME";
    private static final String EVENT_QUEUE_KEY = "EVENT_QUEUE_1_KEY";
    private static final String EVENT_QUEUE_NAME = "EVENT_QUEUE_1_NAME";
    private static final String BLOB_QUEUE_KEY = "BLOB_QUEUE_1_KEY";
    private static final String BLOB_QUEUE_NAME = "BLOB_QUEUE_1_NAME";
    private static final String CLIP_QUEUE_KEY = "CLIP_QUEUE_2_KEY";
    private static final String CLIP_QUEUE_NAME = "CLIP_QUEUE_2_NAME";


    /******************** Device Info *********************/

    public String getDeviceId() {
        TelephonyManager telMgr = (TelephonyManager) mContext.getSystemService(mContext.TELEPHONY_SERVICE);
        String deviceId = null;
        int checks = 0;

        while (deviceId == null && checks < 10) {
            try {
                Log.d(TAG, "getDeviceId()::Waiting for correct deviceId");
                checks++;
                Thread.sleep(2000);
            } catch (InterruptedException ex) {
                Log.e(TAG, "getDeviceId()::error delaying thread");
            } finally {
                if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                    deviceId = telMgr.getDeviceId();
                }
            }
        }

        if(deviceId == null) {
            if(mModelType == KDC) {
                deviceId = "352538101572108";
            } else {
                deviceId = "352538108254916";
            }
        } else if(deviceId.equals("352538101566076")) {
            deviceId = "352538101563552";
        }

        return deviceId;
    }

    public void setModel() {
        String model = Build.MODEL;

        Log.d(TAG, "model: " + model);

        switch (model) {
            case "ime_p89":
                mModel = KDC402;
                break;
            case "ime_p88":
                mModel = KDC402a;
                break;
            case "FX":
            case "K":
                if(Build.DEVICE.equals("cm9")) {
                    mModel = Model.MODEL.KDC404a;
                } else {
                    mModel = Model.MODEL.KDC403a;
                }
                break;
            case "403":
                mModel = Model.MODEL.KDC403a;
                break;
            case "C1":
            case "404":
                mModel = Model.MODEL.KDC404a;
                break;
            case "cm10":
            case "405":
                mModel = KDC405;
                break;
            case "406":
                mModel = KDC406;
                break;
            case "G4E":
                mModel = KTR1;
                break;
            case "KXB1":
                mModel = KXB1;
                break;
            case "A1":
            case "KXB2":
                mModel = KXB2;
                break;
            case "KXB3":
                if(Build.DEVICE.equals("cm9")) {
                    mModel = KXB3a;
                } else {
                    mModel = KXB3;
                }
                break;
            case "KXB5":
                mModel = KXB5;
                break;
            case "KXB4":
                mModel = KXB4;
                break;
            case "V1":
                mModel = KBC02;
                break;
            default:
                mModel = Model.MODEL.UNKNOWN;
                break;
        }

        // Get model type
        switch (mModel) {
            case KDC402:
            case KDC402a:
            case KDC403:
            case KDC403a:
            case KDC404:
            case KDC404a:
            case KDC405:
            case KDC406:
                mModelType = KDC;
                break;

            case KTR1:
                mModelType = KTR;
                break;

            case KXB1:
            case KXB2:
            case KXB3:
            case KXB3a:
            case KXB4:
            case KXB5:
                mModelType = KXB;
                break;

            case KBC02:
                mModelType = KBC;
                break;
        }

        try {
            Process process = Runtime.getRuntime().exec("getprop sys.kas.fps");
            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));

            StringBuilder result = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                result.append(line);
            }

            if (result.toString().equals("12")) {
                switch (mModel) {
                    case KDC403a:
                        mModel = KDC403;
                        break;
                    case KDC404a:
                        mModel = KDC404;
                        break;
                }
            }
        }
        catch (IOException ex) {
            Log.e(TAG, "setModel()::Failed to get fps: " + ex.getMessage());
        }

        Log.d(TAG, "setModel()::" + mModel);
    }

    public Model.TYPE getModelType() {
        return mModelType;
    }

    public Model.MODEL getModel() {
        return mModel;
    }

    public String getModelName() {
        String model = "Unknown";

        switch(mModel) {
            case KDC402:
            case KDC402a:
                model = "KDC402";
                break;
            case KDC403:
            case KDC403a:
                model = "KDC403";
                break;
            case KDC404:
            case KDC404a:
                model = "KDC404";
                break;
            case KDC405:
                model = "KDC405";
                break;
            case KDC406:
                model = "KDC406";
                break;
            case KTR1:
                model = "KTR1";
                break;
            case KXB1:
                model = "KXB1";
                break;
            case KXB2:
                model = "KXB2";
                break;
            case KXB3:
            case KXB3a:
                model = "KXB3";
                break;
            case KXB4:
                model = "KXB4";
                break;
            case KXB5:
                model = "KXB5";
                break;
            case KBC02:
                model = "KBC02";
                break;
        }
        return model;
    }

    public String getCameraRecordingFileName(int camera, boolean audio) {
        String model = "Unknown";
        String res = "720";
        String dateTime = dateAsUtcDateTimeString(Calendar.getInstance().getTime());

        switch (mModel) {
            case KDC402:
                model = "402";
                break;
            case KDC402a:
                model = "402a";
                break;
            case KDC403:
                model = "403";
                break;
            case KDC403a:
                model = "403a";
                break;
            case KDC404:
                model = "404";
                break;
            case KDC404a:
                model = "404a";
                break;
            case KDC405:
                model = "405";
                break;
            case KDC406:
                model = "406";
                break;
            case KXB1:
                model = "KXB1";
                break;
            case KXB2:
                model = "KXB2";
                break;
            case KXB3:
            case KXB3a:
                model = "KXB3";
                break;
            case KXB4:
                model = "KXB4";
                break;
            case KXB5:
                model = "KXB5";
                break;
            case KBC02:
                model = "KBC02";
                break;
        }

        if(camera > 1) {
            res = "480";
        }

        if(audio) {
            return "cam" + camera + "_" + model + "_" + res + "_audio_" + dateTime + Constants.CAM_EXTENSION;
        } else {
            return "cam" + camera + "_" + model + "_" + res + "_" + dateTime + Constants.CAM_EXTENSION;
        }
    }

    public String getCameraFileName(int camera, boolean audio) {
        String model = "Unknown";
        String res = "720";

        switch (mModel) {
            case KDC402:
                model = "402";
                break;
            case KDC402a:
                model = "402a";
                break;
            case KDC403:
                model = "403";
                break;
            case KDC403a:
                model = "403a";
                break;
            case KDC404:
                model = "404";
                break;
            case KDC404a:
                model = "404a";
                break;
            case KDC405:
                model = "405";
                break;
            case KDC406:
                model = "406";
                break;
            case KXB1:
                model = "KXB1";
                break;
            case KXB2:
                model = "KXB2";
                break;
            case KXB3:
            case KXB3a:
                model = "KXB3";
                break;
            case KXB4:
                model = "KXB4";
                break;
            case KXB5:
                model = "KXB5";
                break;
            case KBC02:
                model = "KBC02";
                break;
        }

        if(camera > 1) {
            res = "480";
        }

        if(audio) {
            return "cam" + camera + "_" + model + "_" + res + "_audio" + Constants.CAM_EXTENSION;
        } else {
            return "cam" + camera + "_" + model + "_" + res + Constants.CAM_EXTENSION;
        }
    }


    /******************** Android *********************/

    public void reboot(Location lastLocation) {
        saveLastLocation(lastLocation);

        PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        pm.reboot(null);
    }

    public boolean isDailyRebootDue(RecorderState.STATE recorderState, Date camStartDate) {
        boolean rebootRequired = false;
        Date now = Calendar.getInstance().getTime();
        Calendar cal = Calendar.getInstance();
        cal.setTime(now);
        if (camStartDate != null) {
            long diffInMs = now.getTime() - camStartDate.getTime();
            long diffInSec = TimeUnit.MILLISECONDS.toSeconds(diffInMs);

            if (recorderState == RecorderState.STATE.NOT_RECORDING && cal.get(Calendar.HOUR_OF_DAY) == 2 && diffInSec > 4000) {
                rebootRequired = true;
            }
        }

        return rebootRequired;
    }

    public long setCurrentNetworkTime() {
        try {
            NTPUDPClient timeClient = new NTPUDPClient();
            InetAddress inetAddress = InetAddress.getByName("uk.pool.ntp.org");
            TimeInfo timeInfo = timeClient.getTime(inetAddress);
            long returnTime = timeInfo.getMessage().getTransmitTimeStamp().getTime();

            TimeZone zone = TimeZone.getTimeZone("Europe/London");
            //long offset = zone.getOffset(new Date().getTime());

            setAndroidDate(returnTime, false);
            return returnTime;

        } catch (IOException ex) {
            Log.e(TAG, "setCurrentNetworkTime()::Failed to get time " + ex.getMessage());
        }

        return 0;
    }

    public void setAndroidDate(long timestamp, boolean useOffset) {
        TimeZone zone = TimeZone.getTimeZone("Europe/London");

        long offset = 0;
        Date now = new Date(timestamp);

        if(useOffset) {
            Log.d(TAG, "Set with offset");
            offset = zone.getOffset(now.getTime());
            Log.d(TAG, "Offset:" + offset);
        }

        now = new Date(timestamp + offset);

        Log.d(TAG, "Set date & time:" + now);
        Calendar cal = Calendar.getInstance();
        cal.setTime(now);
        AlarmManager am = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        am.setTime(cal.getTimeInMillis());
    }

    public void setScreenTimeout(int mins) {
        Log.d(TAG, "setScreenTimeout()::" + mins + "mins");
        Settings.System.putInt(mContext.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, (mins * 60 * 1000));
    }

    public boolean isAppRunning(final String packageName) {
        final ActivityManager activityManager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        final List<ActivityManager.RunningAppProcessInfo> procInfos = activityManager.getRunningAppProcesses();
        if (procInfos != null) {
            for (final ActivityManager.RunningAppProcessInfo processInfo : procInfos) {
                if (processInfo.processName.equals(packageName)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void startWatchdogApp() {
        Log.d(TAG, "startWatchdogApp()");
        Intent intent = new Intent();
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setComponent(new ComponentName("io.kasava.watchdog", "io.kasava.watchdog.DebugActivity"));
        try {
            mContext.startActivity(intent);
            Log.d(TAG, "Launching watchdog app");
        } catch (Exception ex) {
            Log.e(TAG, "Failed to launch watchdog app");
        }
    }

    /******************** Audio *********************/

    public void audioMute() {
        Log.d(TAG, "audioMute()");

        AudioManager audioManager = (AudioManager)mContext.getSystemService(Context.AUDIO_SERVICE);
        audioManager.setStreamVolume(AudioManager.STREAM_RING, 0, 0);
        audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0);
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
        audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, 0, 0);
    }

    public void playAudio(Object audio, int muteDelay, int vol) {
        Log.d(TAG, "playAudio()::" + audio.toString());
        Uri mediaUri = Uri.parse("android.resource://" + mContext.getPackageName() + File.separator + audio);

        try {
            AudioManager audioManager = (AudioManager)mContext.getSystemService(Context.AUDIO_SERVICE);
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0);
            mPlayer.reset();
            mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mPlayer.setDataSource(mContext, mediaUri);
            mPlayer.prepare();
            mPlayer.start();
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                audioMute();
            }
        }, muteDelay);
    }




    /******************** DateTime *********************/

    @SuppressLint("SimpleDateFormat")
    public String dateAsUtcDateTimeString(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat(Constants.DATETIME_FORMAT_LONG);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return (sdf.format(date));
    }

    @SuppressLint("SimpleDateFormat")
    public String nowAsUtcDateTimeString() {
        Date now = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat(Constants.DATETIME_FORMAT_LONG);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return (sdf.format(now));
    }

    @SuppressLint("SimpleDateFormat")
    public Date utcDateTimeStringToDate(String dateStr) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(Constants.DATETIME_FORMAT_LONG);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

            return sdf.parse(dateStr);

        } catch (Exception ex){
            Log.e(TAG,"utcDateTimeStringToDate()::Failed to parse date: " + ex.getMessage());
        }
        return null;
    }

    @SuppressLint("SimpleDateFormat")
    public String dateToAzureDate(Date date) {
        SimpleDateFormat sdfDate = new SimpleDateFormat(Constants.DATE_FORMAT);
        SimpleDateFormat sdfTime = new SimpleDateFormat(Constants.TIME_FORMAT);
        sdfDate.setTimeZone(TimeZone.getTimeZone("UTC"));
        sdfTime.setTimeZone(TimeZone.getTimeZone("UTC"));
        return (sdfDate.format(date) + "T" + sdfTime.format(date) + "Z");
    }

    @SuppressLint("SimpleDateFormat")
    public Date azureDateToDate(String dateStr) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(Constants.DATETIME_FORMAT_AZURE);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

            return sdf.parse(dateStr);

        } catch (Exception ex){
            Log.e(TAG,"azureDateToDate()::Failed to parse date: " + ex.getMessage());
        }
        return null;
    }

    @SuppressLint("SimpleDateFormat")
    public String getLocationDateTime(long unixTime) {
        Date date = new java.util.Date(unixTime);
        SimpleDateFormat sdf = new SimpleDateFormat(Constants.DATETIME_FORMAT_LONG);
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return sdf.format(date);
    }

    @SuppressLint("SimpleDateFormat")
    Date recordingDirToLocalDateTime(File dir) {
        try {
            String[] dirParts = dir.getPath().split(File.separator);

            SimpleDateFormat sdf = new SimpleDateFormat(Constants.DATETIME_FORMAT_LONG);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

            return sdf.parse(dirParts[dirParts.length-1]);

        } catch (Exception ex){
            Log.e(TAG,"Failed to parse directory to date: " + ex.getMessage());
        }
        return null;
    }

    @SuppressLint("SimpleDateFormat")
    Date getDateFromRecordingFile(File dir) {

        Log.d(TAG, "getDateFromRecordingFile()::" + dir.getName());

        Date dateTime = null;
        try {
            // Remove extension
            String fileName = (dir.getPath().replace(".kas", ""));
            fileName = (fileName.replace(".vid", ""));
            fileName = (fileName.replace(".mp4", ""));
            fileName = (fileName.replace(".log", ""));

            String[] fileNameParts = fileName.split("_");

            SimpleDateFormat sdf = new SimpleDateFormat(Constants.DATETIME_FORMAT_LONG);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

            if(fileName.contains("part")) {
                dateTime = sdf.parse(fileNameParts[fileNameParts.length-2]);
            } else {
                dateTime = sdf.parse(fileNameParts[fileNameParts.length-1]);
            }

            return dateTime;

        } catch (Exception ex){
            Log.e(TAG,"Failed to parse file name to date: " + ex.getMessage());
        }
        return null;
    }


    @SuppressLint("SimpleDateFormat")
    public Date getRecordingEndDateTimeFromJourney(File dir) {

        Log.d(TAG, "getRecordingEndDateTimeFromJourney()::" + dir);

        File lastJourneyFile = null;
        boolean deleteJourneyFileAfterDecrypt = false;

        // Get list of journey files in dir
        File[] journeyFiles = dir.listFiles();
        Arrays.sort(journeyFiles);

        // Find last journey file to get endDateTime from
        for (File journeyFile : journeyFiles) {
            if (journeyFile.getName().contains("journey")) {
                lastJourneyFile = journeyFile;
            }
        }

        if(lastJourneyFile.getName().endsWith(Constants.ENCRYTED_EXTENSION)) {
            File decryptedJourneyFile = new File(lastJourneyFile.getParent() + File.separator +
                    FilenameUtils.removeExtension(lastJourneyFile.getName()));
            Crypto.decrypt("kq9dx41z084jtuw6", lastJourneyFile, decryptedJourneyFile);

            lastJourneyFile = decryptedJourneyFile;
            deleteJourneyFileAfterDecrypt = true;
        }

        Log.d(TAG, "getRecordingEndDateTimeFromJourney()::" + lastJourneyFile);
        SimpleDateFormat sdf = new SimpleDateFormat(Constants.DATETIME_FORMAT_LONG);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date endDateTime = null;

        try {
            BufferedReader br = new BufferedReader(new FileReader(lastJourneyFile));
            String logLine = "";
            String tmp;

            // Read through to the end of file
            while ((tmp = br.readLine()) != null) {
                logLine = tmp;
            }

            try {
                String[] lineParts = logLine.split(":");
                endDateTime = sdf.parse(lineParts[0]);
            } catch (ParseException ex) {
            }

        } catch (FileNotFoundException ex) {
            Log.e(TAG, "getRecordingDurationFromJourney()::File missing: " + ex.getMessage());
        } catch (IOException ex) {
            Log.e(TAG, "getRecordingDurationFromJourney()::could not process file: " + ex.getMessage());
        } finally {
            if(deleteJourneyFileAfterDecrypt) {
                lastJourneyFile.delete();
            }
        }

        Log.d(TAG, "getRecordingEndDateTimeFromJourney()::endDateTime = " + endDateTime);

        return endDateTime;
    }

    public Date getEndDateTimeOfFile(File file) {
        File dir = file.getParentFile();
        File lastFile = null;
        String lastFileType = "";
        Date endDateTime = null;

        // Get list of recording files in dir
        File[] recFiles = dir.listFiles();
        Arrays.sort(recFiles, Collections.reverseOrder());

        // Find required containing required footage
        for (File recFile : recFiles) {
            String[] recFileParts = recFile.getName().split("_");

            // If we've matched the file
            if(recFile.getAbsolutePath().equals(file.getAbsolutePath())) {
                // If we haven't already seen the same file type then this must be the last file of
                // this type so endDateTime is the same as journey endDateTime
                if (!recFileParts[0].equals(lastFileType)) {
                    endDateTime = getRecordingEndDateTimeFromJourney(dir);
                }
                // Otherwise reference the last file dateTime as the endDateTime for our file
                else {

                    endDateTime = getDateFromRecordingFile(lastFile);
                }
            }

            lastFile = recFile;
            lastFileType = recFileParts[0];
        }

        return endDateTime;
    }


    public static void copy(File src, File dst) {

        try {
            InputStream in = new FileInputStream(src);
            OutputStream out = new FileOutputStream(dst);
            try {
                // Transfer bytes from in to out
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            } finally {
                in.close();
                out.close();
            }
        } catch (IOException ex) {
        }
    }


    /******************** Location *********************/

    public String getLatitudeDegreesFrom(Double latitude) {
        final int latDegrees = abs(latitude.intValue());
        final Double latMinutes = (abs(latitude) - latDegrees) * 60;

        return String.format((latitude >= 0 ? "" : "-") + "%02d%07.4f", latDegrees, latMinutes);
    }

    public String getLongitudeDegreesFrom(Double longitude) {
        final int longDegrees = abs(longitude.intValue());
        final Double longMinutes = (abs(longitude) - longDegrees) * 60;

        return String.format((longitude >= 0 ? "" : "-") + "%03d%07.4f", longDegrees, longMinutes);
    }


    /******************** Broastcast *********************/

    public void sendLocalBroadcastMessage(LocalBroadcastMessage.Type msgType, String extra) {

        Intent intent = new Intent(LocalBroadcastMessage.ID);
        intent.putExtra(LocalBroadcastMessage.ID, msgType);
        //add extra data if required
        if(extra!=null){
            intent.putExtra(LocalBroadcastMessage.EXTRA, extra);
        }
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
    }


    /******************** Terminal command *******************/

    public String terminalCommand(String cmd) {
        String res = "Failed";

        try {
            Process process = Runtime.getRuntime().exec(cmd);
            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));

            StringBuilder result = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                result.append(line);
            }

            res = result.toString();
            Log.e(TAG, "terminalCommand()::" + res);

        }
        catch (IOException ex) {
            Log.e(TAG, "terminalCommand()::Failed to get status: " + ex.getMessage());
        }

        return res;
    }


    /******************** Device Control *********************/

    public boolean getIgnitionStatus() {
        String ign = "1";

        try {
            Process process = Runtime.getRuntime().exec("cat /sys/devices/virtual/misc/mtgpio/pin");
            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));

            StringBuilder result = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                result.append(line);
            }

            String res = result.toString();

            if(res.length() > 273) {
                switch(mModel) {
                    case KDC402:
                    case KDC404:
                    case KDC404a:
                    case KXB1:
                    case KXB3a:
                        ign = res.substring(272, 273);
                        break;
                    case KDC402a:
                        ign = res.substring(261, 262);
                        break;
                    case KDC403:
                    case KDC403a:
                    case KDC405:
                    case KDC406:
                    case KXB2:
                    case KXB3:
                    case KXB4:
                    case KXB5:
                        ign = res.substring(177, 178);
                        break;
                }
            }
        }
        catch (IOException ex) {
            Log.e(TAG, "getIgnitionStatus()::Failed to get status: " + ex.getMessage());
        }

        return ign.equals("0");
    }

    public boolean isUsbHostEnabled() {

        boolean hostEnabled = false;

         try {
            Process process = Runtime.getRuntime().exec("getprop persist.usb.mode");
            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));

            StringBuilder result = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                result.append(line);
            }

            String res = result.toString();

            if(res.equals("host")) {
                hostEnabled = true;
            }
        }
        catch (IOException ex) {
            Log.e(TAG, "getIgnitionStatus()::Failed to get status: " + ex.getMessage());
        }

        Log.d(TAG, "isUsbHostEnabled()=" + hostEnabled);

         return hostEnabled;
    }

    public void usbHostEnabled(boolean hostEnabled) {
        Log.d(TAG, "usbHostEnabled()=" + hostEnabled);

        // TEMP
        if(mModel == KXB1 || mModel == KXB2 || mModel == KXB3 || mModel == KXB4 || mModel == KXB5 || mModel == KDC406) {
            //return;
        }

        String[] cmd1 = {"sh","-c",""};
        String[] cmd2 = {"sh","-c",""};

        if(hostEnabled) {
            switch(mModel) {
                case KDC402:
                case KDC402a:
                    cmd1[2] = "echo 1 > /sys/devices/platform/mt_usb/cpower";
                    cmd2[2] = "echo 1 > /sys/devices/platform/mt_usb/hostmode";
                    break;
                case KDC403:
                case KDC403a:
                case KDC404:
                case KDC404a:
                case KDC406:
                case KXB1:
                case KXB2:
                case KXB3:
                case KXB3a:
                case KXB4:
                case KXB5:
                    cmd1[2] = "setprop persist.usb.mode host";
                    cmd2[2] = "echo host > /sys/power/usb_switch_control";
                    break;
            }
            try {
                Runtime.getRuntime().exec(cmd1);
                Runtime.getRuntime().exec(cmd2);
            } catch (IOException ex) {
                Log.e(TAG, "usbHostEnabled()::Failed to enable USB host mode: " + ex.getMessage());
            }
        } else {
            switch(mModel) {
                case KDC402:
                case KDC402a:
                    cmd1[2] = "echo 0 > /sys/devices/platform/mt_usb/cpower";
                    cmd2[2] = "echo 0 > /sys/devices/platform/mt_usb/hostmode";
                    break;
                case KDC403:
                case KDC403a:
                case KDC404:
                case KDC404a:
                case KDC406:
                case KXB1:
                case KXB2:
                case KXB3:
                case KXB3a:
                case KXB4:
                case KXB5:
                    cmd1[2] = "setprop persist.usb.mode peripheral";
                    cmd2[2] = "echo device > /sys/power/usb_switch_control";
                    break;
            }
            try {
                Runtime.getRuntime().exec(cmd1);
                Runtime.getRuntime().exec(cmd2);
            } catch (IOException ex) {
                Log.e(TAG, "usbHostEnabled()::Failed to disable USB host mode: " + ex.getMessage());
            }
        }
    }

    public void ledRedEnabled(boolean mode) {

        String modeStrC11;
        String modeStrC1;
        String modeStrT2;

        if (mode) {
            modeStrC11 = "1";
            modeStrC1 = "led0blink";
            modeStrT2 = "led1blink";
        } else {
            modeStrC11 = "0";
            modeStrC1 = "led0off";
            modeStrT2 = "led1off";
        }

        if (mModel == KDC402 || mModel == KDC402a) {
            String[] cmd = {"sh", "-c", "echo " + modeStrC11 + " > /sys/devices/platform/leds-mt65xx/leds/red/brightness"};
            try {
                Runtime.getRuntime().exec(cmd);
            } catch (IOException ex) {
                Log.e(TAG, "ledRedToogle()::Failed to set LED: " + ex.getMessage());
            }
        } else if (mModel == KDC404 || mModel == KDC404a || mModel == KXB1 || mModel == KXB3a) {
            String[] cmd = {"sh", "-c", "echo " + modeStrC1 + " > /sys/power/led_set_control"};
            try {
                Runtime.getRuntime().exec(cmd);
            } catch (IOException ex) {
                Log.e(TAG, "ledRedToogle()::Failed to set LED: " + ex.getMessage());
            }
        } else if (mModel == KDC405 || mModel == KDC406 || mModel == KXB2 || mModel == KXB3 || mModel == KXB4 || mModel == KXB5) {
            String[] cmd = {"sh", "-c", "echo " + modeStrT2 + " > /sys/power/led_set_control"};
            try {
                Runtime.getRuntime().exec(cmd);
            } catch (IOException ex) {
                Log.e(TAG, "ledRedToogle()::Failed to set LED: " + ex.getMessage());
            }
        }
    }

    public void ledGreenEnabled(boolean mode) {

        String modeStrC11;
        String modeStrC1;
        String modeStrT2;

        if (mode) {
            modeStrC11 = "1";
            modeStrC1 = "led1on";
            modeStrT2 = "led0on";
        } else {
            modeStrC11 = "0";
            modeStrC1 = "led1off";
            modeStrT2 = "led0off";
        }

        if (mModel == KDC402 || mModel == KDC402a) {
            String[] cmd = {"sh", "-c", "echo " + modeStrC11 + " > /sys/devices/platform/leds-mt65xx/leds/green/brightness"};
            try {
                Runtime.getRuntime().exec(cmd);
            } catch (IOException ex) {
                Log.e(TAG, "ledGreenToogle()::Failed to set LED: " + ex.getMessage());
            }
        } else if (mModel == KDC404 || mModel == KDC404a || mModel == KXB1 || mModel == KXB3a) {
            String[] cmd = {"sh", "-c", "echo " + modeStrC1 + " > /sys/power/led_set_control"};
            try {
                Runtime.getRuntime().exec(cmd);
            } catch (IOException ex) {
                Log.e(TAG, "ledGreenToogle()::Failed to set LED: " + ex.getMessage());
            }
        } else if (mModel == KDC405 || mModel == KDC406 || mModel == KXB2 || mModel == KXB3 || mModel == KXB4 || mModel == KXB5) {
            String[] cmd = {"sh", "-c", "echo " + modeStrT2 + " > /sys/power/led_set_control"};
            try {
                Runtime.getRuntime().exec(cmd);
            } catch (IOException ex) {
                Log.e(TAG, "ledRedToogle()::Failed to set LED: " + ex.getMessage());
            }
        }
    }

    public void ledBlueEnabled(boolean mode) {
        Log.d(TAG, "ledBlueToggle()::" + mode);

        String modeStrT2;

        if (mode) {
            modeStrT2 = "led2on";
        } else {
            modeStrT2 = "led2off";
        }

        if (mModel == KDC405 || mModel == KDC406 || mModel == KXB2 || mModel == KXB3 || mModel == KXB4 || mModel == KXB5) {
            String[] cmd = {"sh", "-c", "echo " + modeStrT2 + " > /sys/power/led_set_control"};
            try {
                Runtime.getRuntime().exec(cmd);
            } catch (IOException ex) {
                Log.e(TAG, "ledBlueToggle()::Failed to set LED: " + ex.getMessage());
            }
        }
    }


    /******************** LiveView *********************/

    public byte[] mergeLiveViewImagesToBytes(final byte[] cam1Bytes, final byte[] cam2Bytes) {
        Log.d(TAG, "mergeLiveViewImagesToBytes()");

        final int HEIGHT = 480;
        final int CAM1_WIDTH = 853;
        final int CAM_USB_WIDTH = 640;
        int CAM2_WIDTH = 0;
        int CAM3_WIDTH = 0;

        Bitmap cam1Bitmap;
        Bitmap cam2Bitmap;
        Bitmap cam3Bitmap;
        Bitmap cam1BitmapScaled;
        Bitmap cam2BitmapScaled = null;
        Bitmap cam3BitmapScaled = null;

        int mergedWidth = CAM1_WIDTH;

        // Get cam1 data
        if(cam1Bytes != null) {
            cam1Bitmap = BitmapFactory.decodeByteArray(cam1Bytes, 0, cam1Bytes.length);
        } else {
            cam1Bitmap = BitmapFactory.decodeFile(Constants.LV1_FILEPATH);
        }

        if(cam1Bitmap != null) {
            cam1BitmapScaled = Bitmap.createScaledBitmap(cam1Bitmap, CAM1_WIDTH, HEIGHT, false);

            // Get cam2 data
            if (cam2Bytes != null) {
                cam2Bitmap = BitmapFactory.decodeByteArray(cam2Bytes, 0, cam2Bytes.length);
            } else {
                cam2Bitmap = BitmapFactory.decodeFile(Constants.LV2_FILEPATH);
            }
            if (cam2Bitmap != null) {
                CAM2_WIDTH = CAM_USB_WIDTH;
                mergedWidth += CAM2_WIDTH;
                cam2BitmapScaled = Bitmap.createScaledBitmap(cam2Bitmap, CAM2_WIDTH, HEIGHT, false);
            }

            // Get cam3 data
            cam3Bitmap = BitmapFactory.decodeFile(Constants.LV3_FILEPATH);
            if (cam3Bitmap != null) {
                CAM3_WIDTH = CAM_USB_WIDTH;
                mergedWidth += CAM3_WIDTH;
                cam3BitmapScaled = Bitmap.createScaledBitmap(cam3Bitmap, CAM3_WIDTH, HEIGHT, false);
            }

            // Merge the bitmaps
            Bitmap mergedBitmap = Bitmap.createBitmap(mergedWidth, HEIGHT, cam1BitmapScaled.getConfig());
            Canvas canvas = new Canvas(mergedBitmap);
            canvas.drawBitmap(cam1BitmapScaled, 0f, 0f, null);
            if (cam2BitmapScaled != null) {
                canvas.drawBitmap(cam2BitmapScaled, CAM1_WIDTH, 0f, null);

                if (cam3BitmapScaled != null) {
                    canvas.drawBitmap(cam3BitmapScaled, CAM1_WIDTH + CAM2_WIDTH, 0f, null);
                }
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            try {
                mergedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream);
                byte[] outData = outputStream.toByteArray();

                Log.d(TAG, "mergeLiveViewImagesToBytes()::processed image");

                return outData;
            } catch (Exception ex) {
                Log.e(TAG, ex.getMessage());
            }
        }

        return null;
    }


    /******************** Saving *********************/

    public Subscription loadSubscription() {
        Subscription subscription = new Subscription();
        SharedPreferences storeDataPref = mContext.getSharedPreferences(SUBSCRIPTION_KEY, Context.MODE_PRIVATE);
        String elemValue = storeDataPref.getString(SUBSCRIPTION_NAME, null);
        if (elemValue != null) {
            Type listType = new TypeToken<Subscription>() {
            }.getType();
            subscription = new Gson().fromJson(elemValue, listType);
        }

        return subscription;
    }

    public void saveSubscription(Subscription subscription) {
        String elemValue;
        Gson gson = new Gson();
        try {
            elemValue = gson.toJson(subscription);
            SharedPreferences storeDataPref = mContext.getSharedPreferences(SUBSCRIPTION_KEY, Context.MODE_PRIVATE);
            SharedPreferences.Editor storeDataEditor = storeDataPref.edit();
            storeDataEditor.clear();
            storeDataEditor.putString(SUBSCRIPTION_NAME, elemValue).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Date loadRecordingFailDate() {
        Date date = new Date();
        SharedPreferences storeDataPref = mContext.getSharedPreferences(RECFAILDATE_KEY, Context.MODE_PRIVATE);
        String elemValue = storeDataPref.getString(RECFAILDATE_NAME, null);
        if (elemValue != null) {
            Type listType = new TypeToken<Date>() {
            }.getType();
            try {
                date = new Gson().fromJson(elemValue, listType);
            } catch (Exception ex) {

            }
        }

        return date;
    }

    public void saveRecordingFailDate(Date date) {
        String elemValue;
        Gson gson = new Gson();
        try {
            elemValue = gson.toJson(date);
            SharedPreferences storeDataPref = mContext.getSharedPreferences(RECFAILDATE_KEY, Context.MODE_PRIVATE);
            SharedPreferences.Editor storeDataEditor = storeDataPref.edit();
            storeDataEditor.clear();
            storeDataEditor.putString(RECFAILDATE_NAME, elemValue).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Location loadLastLocation() {
        Location location = null;
        SharedPreferences storeDataPref = mContext.getSharedPreferences(LASTLOCATION_KEY, Context.MODE_PRIVATE);
        String elemValue = storeDataPref.getString(LASTLOCATION_NAME, null);
        if (elemValue != null) {
            Type listType = new TypeToken<Location>() {
            }.getType();
            try {
                location = new Gson().fromJson(elemValue, listType);
            } catch (Exception ex) {

            }
        }

        return location;
    }

    public void saveLastLocation(Location location) {
        String elemValue;
        Gson gson = new Gson();
        try {
            elemValue = gson.toJson(location);
            SharedPreferences storeDataPref = mContext.getSharedPreferences(LASTLOCATION_KEY, Context.MODE_PRIVATE);
            SharedPreferences.Editor storeDataEditor = storeDataPref.edit();
            storeDataEditor.clear();
            storeDataEditor.putString(LASTLOCATION_NAME, elemValue).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<Clip> loadListClips() {
        List<Clip> list = new ArrayList<>();
        SharedPreferences storeDataPref = mContext.getSharedPreferences(CLIP_QUEUE_KEY, Context.MODE_PRIVATE);
        String elemValue = storeDataPref.getString(CLIP_QUEUE_NAME, null);
        if (elemValue != null) {
            Type listType = new TypeToken<ArrayList<Clip>>() {
            }.getType();
            list = new Gson().fromJson(elemValue, listType);
        }
        return list;
    }

    public void saveListClips(List<Clip> list) {
        String elemValue;
        Gson gson = new Gson();
        try {
            elemValue = gson.toJson(list);
            SharedPreferences storeDataPref = mContext.getSharedPreferences(CLIP_QUEUE_KEY, Context.MODE_PRIVATE);
            SharedPreferences.Editor storeDataEditor = storeDataPref.edit();
            storeDataEditor.clear();
            storeDataEditor.putString(CLIP_QUEUE_NAME, elemValue).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<Status> loadListStatus() {
        List<Status> list = new ArrayList<>();
        SharedPreferences storeDataPref = mContext.getSharedPreferences(STATUS_QUEUE_KEY, Context.MODE_PRIVATE);
        String elemValue = storeDataPref.getString(STATUS_QUEUE_NAME, null);
        if (elemValue != null) {
            Type listType = new TypeToken<ArrayList<Status>>() {
            }.getType();
            list = new Gson().fromJson(elemValue, listType);
        }
        return list;
    }

    public void saveListStatus(List<Status> list) {
        String elemValue;
        Gson gson = new Gson();
        try {
            elemValue = gson.toJson(list);
            SharedPreferences storeDataPref = mContext.getSharedPreferences(STATUS_QUEUE_KEY, Context.MODE_PRIVATE);
            SharedPreferences.Editor storeDataEditor = storeDataPref.edit();
            storeDataEditor.clear();
            storeDataEditor.putString(STATUS_QUEUE_NAME, elemValue).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<File> loadListJourneyLog() {
        List<File> list = new ArrayList<>();
        SharedPreferences storeDataPref = mContext.getSharedPreferences(JOURNEY_LOGS_QUEUE_KEY, Context.MODE_PRIVATE);
        String elemValue = storeDataPref.getString(JOURNEY_LOGS_QUEUE_NAME, null);
        if (elemValue != null) {
            Type listType = new TypeToken<ArrayList<File>>() {
            }.getType();
            list = new Gson().fromJson(elemValue, listType);
        }
        return list;
    }

    public void saveListJourneyLog(List<File> list) {
        String elemValue;
        Gson gson = new Gson();
        try {
            elemValue = gson.toJson(list);
            SharedPreferences storeDataPref = mContext.getSharedPreferences(JOURNEY_LOGS_QUEUE_KEY, Context.MODE_PRIVATE);
            SharedPreferences.Editor storeDataEditor = storeDataPref.edit();
            storeDataEditor.clear();
            storeDataEditor.putString(JOURNEY_LOGS_QUEUE_NAME, elemValue).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<Event> loadListEvent() {
        List<Event> list = new ArrayList<>();
        SharedPreferences storeDataPref = mContext.getSharedPreferences(EVENT_QUEUE_KEY, Context.MODE_PRIVATE);
        String elemValue = storeDataPref.getString(EVENT_QUEUE_NAME, null);
        if (elemValue != null) {
            Type listType = new TypeToken<ArrayList<Event>>() {
            }.getType();
            list = new Gson().fromJson(elemValue, listType);
        }
        return list;
    }

    public void saveListEvent(List<Event> list) {
        String elemValue;
        Gson gson = new Gson();
        try {
            elemValue = gson.toJson(list);
            SharedPreferences storeDataPref = mContext.getSharedPreferences(EVENT_QUEUE_KEY, Context.MODE_PRIVATE);
            SharedPreferences.Editor storeDataEditor = storeDataPref.edit();
            storeDataEditor.clear();
            storeDataEditor.putString(EVENT_QUEUE_NAME, elemValue).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<Blob> loadListBlob() {
        List<Blob> list = new ArrayList<>();
        SharedPreferences storeDataPref = mContext.getSharedPreferences(BLOB_QUEUE_KEY, Context.MODE_PRIVATE);
        String elemValue = storeDataPref.getString(BLOB_QUEUE_NAME, null);
        if (elemValue != null) {
            Type listType = new TypeToken<ArrayList<Blob>>() {
            }.getType();
            list = new Gson().fromJson(elemValue, listType);
        }
        return list;
    }

    public void saveListBlob(List<Blob> list) {
        String elemValue;
        Gson gson = new Gson();
        try {
            elemValue = gson.toJson(list);
            SharedPreferences storeDataPref = mContext.getSharedPreferences(BLOB_QUEUE_KEY, Context.MODE_PRIVATE);
            SharedPreferences.Editor storeDataEditor = storeDataPref.edit();
            storeDataEditor.clear();
            storeDataEditor.putString(BLOB_QUEUE_NAME, elemValue).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}