package io.kasava.utilities;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import io.kasava.data.HealthCheckData;


public class HealthCheck {
    private static String TAG = "HealthCheck";

    private Cellular cellular;
    private Storage storage;
    private Utilities utilities;

    private Context mContext;

    private static final int SECONDS_IN_A_DAY = 86400;
    private static final String HEALTHCHECKDATE_KEY = "HEALTHCHECKDATE_1_KEY";
    private static final String HEALTHCHECKDATE_NAME = "HEALTHCHECKDATE_1_NAME";

    private Date mLastHealthCheckDate;

    public HealthCheck(Context context) {
        mContext = context;

        cellular = new Cellular(mContext);
        utilities = new Utilities(mContext);
        storage = new Storage(mContext, utilities.getModelType());

        mLastHealthCheckDate = loadHealthCheckDate();
    }

    public Date loadHealthCheckDate() {
        Log.d(TAG, "loadHealthCheckDate()");

        Date date = null;
        SharedPreferences storeDataPref = mContext.getSharedPreferences(HEALTHCHECKDATE_KEY, Context.MODE_PRIVATE);
        String elemValue = storeDataPref.getString(HEALTHCHECKDATE_NAME, null);
        if (elemValue != null) {
            Type listType = new TypeToken<Date>() {
            }.getType();
            try {
                date = new Gson().fromJson(elemValue, listType);
            } catch (Exception ex) {

            }
        }

        Log.d(TAG, "loadHealthCheckDate()::" + date);

        return date;
    }

    public void saveHealthCheckDate(Date date) {
        Log.d(TAG, "saveHealthCheckDate()");

        String elemValue;
        Gson gson = new Gson();
        try {
            elemValue = gson.toJson(date);
            SharedPreferences storeDataPref = mContext.getSharedPreferences(HEALTHCHECKDATE_KEY, Context.MODE_PRIVATE);
            SharedPreferences.Editor storeDataEditor = storeDataPref.edit();
            storeDataEditor.clear();
            storeDataEditor.putString(HEALTHCHECKDATE_NAME, elemValue).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }

        mLastHealthCheckDate = date;
    }

    public HealthCheckData runHealthCheck(boolean cam1, boolean cam2, boolean cam3, String azureToken, Location location) {
        Log.d(TAG, "runHealthCheck()");

        HealthCheckData healthCheckData = new HealthCheckData();

        Date now = Calendar.getInstance().getTime();
        Calendar cal = Calendar.getInstance();
        cal.setTime(now);
        long diffInSec = 0;

        if(mLastHealthCheckDate != null) {
            long diffInMs = now.getTime() - mLastHealthCheckDate.getTime();
            diffInSec = TimeUnit.MILLISECONDS.toSeconds(diffInMs);
        }

        if(mLastHealthCheckDate == null || diffInSec > SECONDS_IN_A_DAY) {
            Log.d(TAG, "runHealthCheck()::running...");

            healthCheckData.dateTime = utilities.dateToAzureDate(Calendar.getInstance().getTime());
            healthCheckData.type = 3;
            healthCheckData.camera1 = cam1;
            healthCheckData.camera2 = cam2;
            healthCheckData.camera3 = cam3;
            healthCheckData.sim = cellular.getSimNo() != null;
            healthCheckData.network = azureToken != null;
            healthCheckData.gps = location != null;
            healthCheckData.sdPresent = storage.isSdPresent();
            healthCheckData.sdWrite = storage.sdTestWrite();
            healthCheckData.motion = true;
            healthCheckData.ign = true;
            healthCheckData.emmcFreeMB = (int) storage.getEmmcFreeStorageMb();
            healthCheckData.sdFreeMB = (int) storage.getSdFreeStorageMb();

            saveHealthCheckDate(now);
        } else {
            Log.d(TAG, "runHealthCheck()::not due");
        }

        return healthCheckData;
    }
}
