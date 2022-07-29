package io.kasava.utilities;

import android.content.Context;
import android.content.Intent;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;

import java.util.Timer;
import java.util.TimerTask;

import io.kasava.broadcast.LocalBroadcastMessage;
import io.kasava.dashcampro.KdcService;
import io.kasava.data.Model;

public class Motion {

    private static final String TAG = "Motion";

    private Context mContext;
    public Motion(Context context) {
        mContext = context;
    }

    private enum Gdir {
        POSITIVE,
        NEGATIVE,
        BOTH
    }

    private enum EventTrigger {
        SE_SHOCK,
        SE_ACCELERATION,
        SE_BRAKE,
        SE_TURN,
        SE_MANDOWN,
        SE_ZIGZAG
    }

    private KdcService.MOTION_STATE mMotionState;

    public void setMotionState(KdcService.MOTION_STATE motionState) {
        mMotionState = motionState;
    }

    public KdcService.MOTION_STATE getMotionState() {
        return mMotionState;
    }


    private static Model.TYPE mModelType;
    private static Model.MODEL mModel;

    private static final Float PITCH_ADJ_MAX = 0.4f;
    private static Float mPitch = -155.0f; // General
    private static CircularBuffer<Integer> mXbuf = new CircularBuffer<>();
    private static CircularBuffer<Integer> mYbuf = new CircularBuffer<>();
    private static CircularBuffer<Integer> mZbuf = new CircularBuffer<>();
    private static CircularBuffer<Integer> mXavgBuf = new CircularBuffer<>();
    private static CircularBuffer<Integer> mYavgBuf = new CircularBuffer<>();
    private static CircularBuffer<Integer> mZavgBuf = new CircularBuffer<>();
    private static boolean mMotionParametersSet = false;
    private static int mShockMicrog;
    private static int mShockHz;
    private static int mAccelMicrog;
    private static int mAccelHz;
    private static int mBrakeMicrog;
    private static int mBrakeHz;
    private static int mTurnMicrog;
    private static int mTurnHz;
    private static int mBikeShockMicrog = 5500;
    private static int mMandownMicrog;
    private static int mMandownHz;
    private static int mZigzagTriggerState = 0;

    // Events
    private static final int MOTION_TURN_TIMEOUT_MS = 2500;
    private Timer mEventTimer;
    private EventTrigger mEventTrigger = null;

    private Timer mZigzagTimer;

    private void sendLocalBroadcastMessage(String extra) {
        Log.d(TAG, "Broadcasting message");
        Intent intent = new Intent(LocalBroadcastMessage.ID);
        intent.putExtra(LocalBroadcastMessage.ID, LocalBroadcastMessage.Type.event);
        // Add extra data if required
        if(extra!=null){
            intent.putExtra(LocalBroadcastMessage.EXTRA, extra);
        }
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
    }

    public void setMotionParameters(Model.TYPE modelType, Model.MODEL model, int sensShock, int sensAccel, int sensBrake, int sensTurn, int sensMandown) {
        Log.d(TAG, "Motion sensitivity: " + sensShock + ", " + sensAccel + ", " + sensBrake + ", " + sensTurn + ", " + sensMandown);

        mModelType = modelType;
        mModel = model;

        switch (sensShock) {
            case 0:
                mShockMicrog = 5000;
                mShockHz = 20;
                break;
            case 1:
                mShockMicrog = 800;
                mShockHz = 4;
                break;
            case 2:
                mShockMicrog = 750;
                mShockHz = 3;
                break;
            case 4:
                mShockMicrog = 650;
                mShockHz = 3;
                break;
            case 5:
                mShockMicrog = 600;
                mShockHz = 2;
                break;
            default: // Default is 3
                mShockMicrog = 700;
                mShockHz = 3;
                break;
        }

        switch (sensAccel) {
            case 0:
                mAccelMicrog = 5000;
                mAccelHz = 20;
                break;
            case 1:
                mAccelMicrog = 310;
                mAccelHz = 8;
                break;
            case 2:
                mAccelMicrog = 280;
                mAccelHz = 7;
                break;
            case 4:
                mAccelMicrog = 220;
                mAccelHz = 6;
                break;
            case 5:
                mAccelMicrog = 200;
                mAccelHz = 5;
                break;
            default: // Default is 3
                mAccelMicrog = 250;
                mAccelHz = 7;
                break;
        }

        switch (sensBrake) {
            case 0:
                mBrakeMicrog = 5000;
                mBrakeHz = 20;
                break;
            case 1:
                mBrakeMicrog = 350;
                mBrakeHz = 8;
                break;
            case 2:
                mBrakeMicrog = 320;
                mBrakeHz = 7;
                break;
            case 4:
                mBrakeMicrog = 270;
                mBrakeHz = 6;
                break;
            case 5:
                mBrakeMicrog = 240;
                mBrakeHz = 5;
                break;
            default: // Default is 3
                mBrakeMicrog = 300;
                mBrakeHz = 7;
                break;
        }

        switch (sensTurn) {
            case 0:
                mTurnMicrog = 5000;
                mTurnHz = 20;
                break;
            case 1:
                mTurnMicrog = 600;
                mTurnHz = 22;
                break;
            case 2:
                mTurnMicrog = 570;
                mTurnHz = 20;
                break;
            case 4:
                mTurnMicrog = 490;
                mTurnHz = 16;
                break;
            case 5:
                mTurnMicrog = 450;
                mTurnHz = 15;
                break;
            default: // Default is 3
                mTurnMicrog = 530;
                mTurnHz = 18;
                break;
        }

        switch (sensMandown) {
            case 0:
                mMandownMicrog = 5000;
                mMandownHz = 35;
                break;
            case 1:
                mMandownMicrog = 850;
                mMandownHz = 35;
                break;
            case 2:
                mMandownMicrog = 800;
                mMandownHz = 35;
                break;
            case 4:
                mMandownMicrog = 700;
                mMandownHz = 35;
                break;
            case 5:
                mMandownMicrog = 650;
                mMandownHz = 35;
                break;
            default: // Default is 3
                mMandownMicrog = 600;
                mMandownHz = 25;
                break;
        }

        if(model == Model.MODEL.KXB5) {
            mBikeShockMicrog = 4000;
        }

        mMotionParametersSet = true;
        Log.d(TAG, "setMotionParameters()::Motion parameters set");
    }

    public Float getMotionRotation(int y, int z, float speed) {

        if((mModelType == Model.TYPE.KDC || mModel == Model.MODEL.KXB3 || mModel == Model.MODEL.KXB4 || mModel == Model.MODEL.KXB5) && speed < 0.5f) {
            float pitchAcc = (float) Math.toDegrees(Math.atan2(y, z));
            Float newPitch = (0.9f * mPitch) + (0.1f * pitchAcc);

            if(mPitch > 150 && pitchAcc < -150) {
                mPitch += PITCH_ADJ_MAX;
            } else if(mPitch < -150 && pitchAcc > 150) {
                mPitch -= PITCH_ADJ_MAX;
            } else if(newPitch > mPitch) {
                mPitch += PITCH_ADJ_MAX;
            } else {
                mPitch -= PITCH_ADJ_MAX;
            }

            if(mPitch > 180) {
                mPitch = -180.0f;
            } else if(mPitch < -180) {
                mPitch = 180.0f;
            }
        }

        //Log.d(TAG, "getMotionRotation()::" + mPitch);

        return mPitch;
    }

    public int getTrueY(int y, int z, float pitch) {

        // Remove gravity from Y & Z readings
        y = y - (int)(1000 * (Math.sin(Math.toRadians(pitch))));
        z = z - (int)(1000 * (Math.cos(Math.toRadians(pitch))));

        double percYY = y * ((-0.5 * Math.cos(Math.toRadians(2 * pitch))) + 0.5);
        double percYZ = z * ((0.5 * Math.cos(Math.toRadians(2 * pitch))) + 0.5);
        y = (int)(percYY + percYZ);

        return y;
    }

    public int getTrueZ(int y, int z, float pitch) {

        // Remove gravity from Y & Z readings
        y = y - (int)(1000 * (Math.sin(Math.toRadians(pitch))));
        z = z - (int)(1000 * (Math.cos(Math.toRadians(pitch))));

        double percZY = y * ((0.5 * Math.cos(Math.toRadians(2 * pitch))) + 0.5);
        double percZZ = z * ((-0.5 * Math.cos(Math.toRadians(2 * pitch))) + 0.5);
        z = (int)(-percZY + percZZ);

        return z;
    }

    public void addToMotionEventBuffers(int x, int y, int z) {
        //Log.d(TAG, "addToMotionEventBuffers()::X=" + x + ", Y=" + y + ", Z=" + z );

        // Insert accelerometer values into circular buffer
        mXbuf.insert(x);
        mYbuf.insert(y);
        mZbuf.insert(z);

        if (!mMotionParametersSet) {
            Log.d(TAG, "addToMotionEventBuffers()::Parameters not set");
            return;
        }

        if(mModelType == Model.TYPE.KDC) {
            // Check for harsh turning
            if (checkForEvent(mXbuf, mTurnHz, mTurnMicrog, Gdir.BOTH)) {
                Log.d(TAG, "checkForEvent()::Turn");
                checkEventStatus(EventTrigger.SE_TURN);
            }
            // Check for shock from X, Y & Z
            else if ((checkForEvent(mXbuf, mShockHz, mShockMicrog, Gdir.BOTH)) ||
                    (checkForEvent(mYbuf, mShockHz, mShockMicrog, Gdir.BOTH)) ||
                    (checkForEvent(mZbuf, mShockHz, mShockMicrog, Gdir.BOTH))) {

                Log.d(TAG, "checkForEvent()::Shock");
                checkEventStatus(EventTrigger.SE_SHOCK);
            }

            /*
            // Check for harsh acceleration
            if (checkForEvent(mZbuf, mAccelHz, mAccelMicrog, Gdir.NEGATIVE)) {
                Log.d(TAG, "checkForEvent()::Acceleration");
                checkEventStatus(EventTrigger.SE_ACCELERATION);
            }

            // Check for harsh braking
            if (checkForEvent(mZbuf, mBrakeHz, mBrakeMicrog, Gdir.POSITIVE)) {
                Log.d(TAG, "checkForEvent()::Brake");
                checkEventStatus(EventTrigger.SE_BRAKE);
            }*/
        } else if(mModel == Model.MODEL.KXB3 || mModel == Model.MODEL.KXB4 || mModel == Model.MODEL.KXB5) {
            if ((checkForEvent(mXbuf, 2, mBikeShockMicrog, Gdir.BOTH)) ||
            (checkForEvent(mYbuf, 2, mBikeShockMicrog, Gdir.BOTH)) ||
            (checkForEvent(mZbuf, 2, mBikeShockMicrog, Gdir.BOTH))) {
                Log.d(TAG, "checkForEvent()::Shock");
                checkEventStatus(EventTrigger.SE_SHOCK);
            } else if ((mModel == Model.MODEL.KXB3 || mModel == Model.MODEL.KXB4) && checkForEvent(mXbuf, mMandownHz, mMandownMicrog, Gdir.BOTH)) {
                Log.d(TAG, "checkForEvent()::Mandown");
                checkEventStatus(EventTrigger.SE_MANDOWN);
            }else if (mModel == Model.MODEL.KXB5 && checkForEvent(mZbuf, mMandownHz, mMandownMicrog, Gdir.BOTH)) {
                Log.d(TAG, "checkForEvent()::Mandown");
                checkEventStatus(EventTrigger.SE_MANDOWN);
            } else if (checkForEvent(mXbuf, mTurnHz-6, mTurnMicrog-30, Gdir.BOTH)) {
                if(mZigzagTriggerState == 0) {
                    Log.d(TAG, "checkForEvent()::Zig-zag(1)");
                    if(mXbuf.read(mXbuf.size()-1) < 0) {
                        mZigzagTriggerState = 1;
                    } else {
                        mZigzagTriggerState = 2;
                    }

                    if (mZigzagTimer != null) {
                        mZigzagTimer.cancel();
                        mZigzagTimer = null;
                    }
                    mZigzagTimer = new Timer();
                    mZigzagTimer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            mZigzagTriggerState = 0;
                        }
                    }, 4 * 1000);

                } else if((mZigzagTriggerState == 1 && mXbuf.read(mXbuf.size()-1) > 0) ||
                        (mZigzagTriggerState == 2 && mXbuf.read(mXbuf.size()-1) < 0)) {
                    Log.d(TAG, "checkForEvent()::Zig-zag(2)");
                    mZigzagTriggerState = 3;

                    checkEventStatus(EventTrigger.SE_ZIGZAG);

                    if (mZigzagTimer != null) {
                        mZigzagTimer.cancel();
                        mZigzagTimer = null;
                    }
                    mZigzagTimer = new Timer();
                    mZigzagTimer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            mZigzagTriggerState = 0;
                        }
                    }, 4 * 1000);
                }
            }
        }
    }

    private boolean checkForEvent(CircularBuffer<Integer> cb, int samplesToCheck, float threshold, Gdir direction) {

        int numberVals = 0;

        if (cb.size() <= samplesToCheck) {
            return false; //don't have enough samples to check
        }

        for (int i = 0; i < samplesToCheck; i++) {

            int sr = cb.read(i);

            if (direction.equals(Gdir.BOTH)) {
                if (Math.abs(sr) >= threshold) {
                    numberVals++;
                }
            } else if (direction.equals(Gdir.POSITIVE)) {
                if (sr >= threshold) {
                    numberVals++;
                }
            } else if (direction.equals(Gdir.NEGATIVE)) {
                if (sr <= -threshold) {
                    numberVals++;
                }
            }
        }

        return numberVals >= samplesToCheck;
    }

    private void checkEventStatus(EventTrigger evt) {

        if ((mModel != Model.MODEL.KXB3 && mModel != Model.MODEL.KXB4 && mModel != Model.MODEL.KXB5 && evt == EventTrigger.SE_SHOCK && mEventTrigger != EventTrigger.SE_SHOCK) ||
                (evt == EventTrigger.SE_MANDOWN && mEventTrigger != EventTrigger.SE_MANDOWN)) {

            mEventTrigger = evt;

            if (mEventTimer != null) {
                mEventTimer.cancel();
                mEventTimer = null;
            }
            mMotionState = KdcService.MOTION_STATE.READY_TO_CLEAR;
            Log.d(TAG, "checkEventStatus()::sending " + mEventTrigger.toString() + " event to listener");
            sendLocalBroadcastMessage(mEventTrigger.toString());
            mEventTrigger = null;

        } else if ((evt == EventTrigger.SE_SHOCK && mEventTrigger != EventTrigger.SE_SHOCK) ||
                (evt == EventTrigger.SE_TURN && mEventTrigger != EventTrigger.SE_TURN) ||
                (evt == EventTrigger.SE_ZIGZAG && mEventTrigger != EventTrigger.SE_ZIGZAG)) {

            mEventTrigger = evt;

            if (mEventTimer != null) {
                mEventTimer.cancel();
                mEventTimer = null;
            }
            mEventTimer = new Timer();
            mEventTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    mMotionState = KdcService.MOTION_STATE.READY_TO_CLEAR;
                    Log.d(TAG, "checkEventStatus()::sending " + mEventTrigger.toString() + " event to listener");
                    sendLocalBroadcastMessage(mEventTrigger.toString());

                    mEventTrigger = null;
                }
            }, MOTION_TURN_TIMEOUT_MS);
        }
    }
}