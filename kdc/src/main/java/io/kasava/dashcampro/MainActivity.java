package io.kasava.dashcampro;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

import java.util.Timer;
import java.util.TimerTask;

import io.kasava.data.Model;
import io.kasava.data.RecorderState;
import io.kasava.data.Subscription;

public class MainActivity extends Activity {

    public static final String TAG = "MainActivity";

    private KdcService mKdcService;
    private static boolean mSelfTestActive = false;
    private static Timer timerSelfTest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate()");

        Log.d(TAG, "Version name: " + BuildConfig.VERSION_NAME);
        Log.d(TAG, "Version code: " + BuildConfig.VERSION_CODE);

        setContentView(R.layout.activity_main);

        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        //Intent serviceIntent = new Intent(this, KdcService.class);
        //startService(serviceIntent);
        initKdcService();
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume()");
        mSelfTestActive = false;
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause()");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");
    }

    /******************** Key Events *********************/

    private void selfTestActivity() {
        // Start the selfTest activity
        Intent intent = new Intent(this, SelfTestActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        this.startActivity(intent);
    }

    /******************** Key Events *********************/

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent keyEvent) {
        Log.d(TAG, "onKeyDown()::code=" + keyCode + ", event=" + keyEvent);

        if(!mSelfTestActive &  keyCode == KeyEvent.KEYCODE_F11) {
            mSelfTestActive = true;

            if (timerSelfTest != null) {
                timerSelfTest.cancel();
            }

            timerSelfTest = new Timer();
            timerSelfTest.schedule(new TimerTask() {
                @Override
                public void run() {
                    selfTestActivity();
                }
            }, 2000);
        }

        return super.onKeyDown(keyCode, keyEvent);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent keyEvent) {
        Log.d(TAG, "onKeyUp()::code=" + keyCode + ", event=" + keyEvent);

        if(mSelfTestActive && keyCode == KeyEvent.KEYCODE_F11) {
            mSelfTestActive = false;

            if (timerSelfTest != null) {
                timerSelfTest.cancel();
            }
        }

        if(keyCode == KeyEvent.KEYCODE_F4) {
            mKdcService.setRecorderState(RecorderState.STATE.PREPARING);
        }

        return super.onKeyUp(keyCode, keyEvent);
    }

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



        }

        public void onServiceDisconnected(ComponentName classname) {
            mKdcService = null;
        }
    };
}