package io.kasava.utilities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.hardware.display.DisplayManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Display;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import io.kasava.data.Model;

public class Cellular {

    private final static String TAG = "Apn";

    private Context mContext;

    public Cellular(Context context) {
        mContext = context;
    }

    private static final String ACTION_STARVIEW_DATA_MODE_SET = "com.ime.service.intent.ACTION_DATA_MODE_SET";
    private static final String ACTION_STARVIEW_AIRPLANE_MODE_SET = "com.ime.service.intent.ACTION_AIRPLANE_MODE_SET";
    private static final String ACTION_PHISUNG_AIRPLANE_MODE_ON = "com.car.modem_off";
    private static final String ACTION_PHISUNG_AIRPLANE_MODE_OFF = "com.car.modem_on";

    private static final Uri APN_TABLE_URI = Uri.parse("content://telephony/carriers");

    public void start(Model.MODEL model) {
        Log.d(TAG, "start");

        getSimNo();

        // Make sure airplane mode is off
        setAirplaneModeOn(model, false);
        //resetModem(model);

        //getApns();
        set3iot();

        // Enable data roaming if not set
        if (!isDataRoamingEnabled()) {
            setDataRoamingMode(1);
        }
        if (!isRoamingRemindersDisabled()) {
            setRoamingRemindersMode(2);
        }

        new Thread() {
            public void run() {
                if (model.equals(Model.MODEL.KDC402) || model.equals(Model.MODEL.KDC402a)) {
                    setDataMode(true);
                    resetModem(model);
                }
            }
        }.start();
    }

    @SuppressLint("HardwareIds")
    public String getSimNo() {
        TelephonyManager telMgr = (TelephonyManager) mContext.getSystemService(mContext.TELEPHONY_SERVICE);
        String simNo = null;

        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            simNo = telMgr.getSimSerialNumber();
            if(simNo != null) {
                simNo = simNo.replace("f", "");
            }
        }

        Log.d(TAG, "getSimNo()::" + simNo);

        return simNo;
    }

    public boolean isNetworkActive() {
        boolean networkActive = false;
        ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo[] netInfo = cm.getAllNetworkInfo();
        for (NetworkInfo ni : netInfo) {
            if (ni.getTypeName().equalsIgnoreCase("MOBILE") && ni.isConnected()) {
                networkActive = true;
            }
        }

        return networkActive;
    }

    public void setDataMode(boolean eanble) {
        Intent intent = new Intent(ACTION_STARVIEW_DATA_MODE_SET);
        intent.putExtra("on", eanble);
        mContext.sendBroadcast(intent);
    }

    public void resetModem(Model.MODEL model) {
        setAirplaneModeOn(model, true);

        try {
            Thread.sleep(6 * 1000);
        } catch (Exception ex) {
            Log.e(TAG, "resetModem()::Failed to delay modem reset:" + ex.getMessage());
        }

        DisplayManager dm = (DisplayManager) mContext.getSystemService(Context.DISPLAY_SERVICE);
        //for (Display display : dm.getDisplays()) {
        //    if (display.getState() != Display.STATE_OFF) {
                setAirplaneModeOn(model, false);
        //    }
        //}
    }

    public void setAirplaneModeOn(Model.MODEL model, boolean enabled) {
        Log.d(TAG, "setAirplaneModeOn()::" + enabled);

        // Android default method
        Settings.System.putInt(mContext.getContentResolver(), Settings.System.AIRPLANE_MODE_ON, 1);
        Intent intentAndroid = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intentAndroid.putExtra("state", enabled);
        mContext.sendBroadcast(intentAndroid);

        // Custom camera methods
        if(model == Model.MODEL.KDC402 || model == Model.MODEL.KDC402a) {
            Intent intentC11 = new Intent(ACTION_STARVIEW_AIRPLANE_MODE_SET);
            intentC11.putExtra("on", enabled);
            mContext.sendBroadcast(intentC11);
        } else if (enabled) {
            Intent intentPhisung = new Intent(ACTION_PHISUNG_AIRPLANE_MODE_ON);
            mContext.sendBroadcastAsUser(intentPhisung, android.os.Process.myUserHandle());
        } else {
            Intent intentPhisung = new Intent(ACTION_PHISUNG_AIRPLANE_MODE_OFF);
            mContext.sendBroadcastAsUser(intentPhisung, android.os.Process.myUserHandle());
        }
    }

    public boolean isDataRoamingEnabled() {
        try {
            return Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.DATA_ROAMING) == 1;
        }
        catch (Settings.SettingNotFoundException e) {
            return false;
        }
    }

    public void setDataRoamingMode(int mode) {
        Log.d(TAG, "Set data roaming: " + mode);
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.DATA_ROAMING, mode);
    }

    public boolean isRoamingRemindersDisabled() {
        try {
            return Settings.System.getInt(mContext.getContentResolver(), "roaming_reminder_mode_setting") == 2;
        }
        catch (Settings.SettingNotFoundException e) {
            return false;
        }
    }

    public void setRoamingRemindersMode(int mode) {
        Log.d(TAG, "Set roaming reminders: " + mode);
        Settings.System.putInt(mContext.getContentResolver(), "roaming_reminder_mode_setting", mode);
    }

    public void getApns() {
        Log.d(TAG, "getApns()");

        try {
            Cursor c = mContext.getContentResolver().query(APN_TABLE_URI, null, null, null, null);
            //Cursor c = mContext.getContentResolver().query(PREFERRED_APN_URI, null, null, null, null);

            if (c != null) {
                printAllData(c); //Print the entire result set

                if (c.moveToFirst()) {
                    c.close();
                }
            }

        } catch (Exception ex) {
            Log.d(TAG, ex.getMessage());
        }
    }

    private String printAllData(Cursor c) {
        if (c == null) return null;
        String s = "";
        int record_cnt = c.getCount();
        Log.d(TAG, "Total # of records: " + record_cnt);

        if (c.moveToFirst()) {
            String[] columnNames = c.getColumnNames();
            Log.d(TAG, getAllColumnNames(columnNames));
            s += getAllColumnNames(columnNames);
            do {
                String row = "";
                for (String columnIndex : columnNames) {
                    int i = c.getColumnIndex(columnIndex);
                    row += c.getString(i) + ":\t";
                }
                row += "\n";
                Log.d(TAG, row);
                s += row;
            } while (c.moveToNext());
            Log.d(TAG, "End of Records");
        }
        return s;
    }

    private String getAllColumnNames(String[] columnNames) {
        String s = "Column Names:\n";
        for (String t : columnNames) {
            s += t + ":\t";
        }
        return s + "\n";
    }

    public void set3iot() {
        ContentResolver resolver = mContext.getContentResolver();
        ContentValues values = new ContentValues();
        values.put("name", "3iot");
        values.put("numeric", "27202");
        values.put("mcc", "272");
        values.put("mnc", "02");
        values.put("apn", "3iot.com");
        values.put("user", "");
        values.put("server", "");
        values.put("password", "");
        values.put("proxy", "");
        values.put("port", "");
        values.put("mmsproxy", "");
        values.put("mmsport", "");
        values.put("mmsc", "");
        values.put("authtype", "-1");
        values.put("type", "default");
        values.put("current", "1");
        values.put("sourcetype", "0");
        values.put("csdnum", "");
        values.put("protocol", "IP");
        values.put("roaming_protocol", "IP");
        values.put("carrier_enabled", "1");
        values.put("bearer", "0");
        values.put("spn", "");
        values.put("imsi", "");
        values.put("pnn", "");
        values.put("ppp", "");
        values.put("mvno_type", "");
        values.put("mvno_match_data", "");
        values.put("sub_id", "3");
        values.put("profile_id", "0");
        values.put("modem_cognitive", "0");
        values.put("max_conns", "0");
        values.put("wait_time", "0");
        values.put("max_conns_time", "0");
        values.put("mtu", "0");

        try {
            resolver.update(APN_TABLE_URI, values, "_id=379", null);
        } catch (Exception ex) {
            Log.d(TAG, ex.getMessage());
        }
    }

    public void setDigicel() {
        ContentResolver resolver = mContext.getContentResolver();
        ContentValues values = new ContentValues();
        values.put("name", "Digicel");
        values.put("numeric", "338050");
        values.put("mcc", "338");
        values.put("mnc", "050");
        values.put("apn", "web.digiceljamaica.com");
        values.put("user", "");
        values.put("server", "");
        values.put("password", "");
        values.put("proxy", "");
        values.put("port", "");
        values.put("mmsproxy", "");
        values.put("mmsport", "");
        values.put("mmsc", "");
        values.put("authtype", "-1");
        values.put("type", "default");
        values.put("current", "1");
        values.put("sourcetype", "0");
        values.put("csdnum", "");
        values.put("protocol", "IP");
        values.put("roaming_protocol", "IP");
        values.put("carrier_enabled", "1");
        values.put("bearer", "0");
        values.put("spn", "");
        values.put("imsi", "");
        values.put("pnn", "");
        values.put("ppp", "");
        values.put("mvno_type", "");
        values.put("mvno_match_data", "");
        values.put("sub_id", "3");
        values.put("profile_id", "0");
        values.put("modem_cognitive", "0");
        values.put("max_conns", "0");
        values.put("wait_time", "0");
        values.put("max_conns_time", "0");
        values.put("mtu", "0");

        try {
            //resolver.update(APN_TABLE_URI, values, "_id=379", null);
        } catch (Exception ex) {
            Log.d(TAG, ex.getMessage());
        }
    }

    public void setFlow() {
        ContentResolver resolver = mContext.getContentResolver();
        ContentValues values = new ContentValues();
        values.put("name", "Flow");
        values.put("numeric", "338180");
        values.put("mcc", "338");
        values.put("mnc", "180");
        values.put("apn", "web.digiceljamaica.com");
        values.put("user", "");
        values.put("server", "");
        values.put("password", "");
        values.put("proxy", "");
        values.put("port", "");
        values.put("mmsproxy", "");
        values.put("mmsport", "");
        values.put("mmsc", "");
        values.put("authtype", "-1");
        values.put("type", "default");
        values.put("current", "1");
        values.put("sourcetype", "0");
        values.put("csdnum", "");
        values.put("protocol", "IP");
        values.put("roaming_protocol", "IP");
        values.put("carrier_enabled", "1");
        values.put("bearer", "0");
        values.put("spn", "");
        values.put("imsi", "");
        values.put("pnn", "");
        values.put("ppp", "");
        values.put("mvno_type", "");
        values.put("mvno_match_data", "");
        values.put("sub_id", "3");
        values.put("profile_id", "0");
        values.put("modem_cognitive", "0");
        values.put("max_conns", "0");
        values.put("wait_time", "0");
        values.put("max_conns_time", "0");
        values.put("mtu", "0");

        try {
            //resolver.update(APN_TABLE_URI, values, "_id=379", null);
        } catch (Exception ex) {
            Log.d(TAG, ex.getMessage());
        }
    }
}
