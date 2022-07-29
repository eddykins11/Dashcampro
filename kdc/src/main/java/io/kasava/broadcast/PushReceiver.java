package io.kasava.broadcast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;

import static io.kasava.broadcast.LocalBroadcastMessage.Type.clearQueue;
import static io.kasava.broadcast.LocalBroadcastMessage.Type.customVideoRequest;
import static io.kasava.broadcast.LocalBroadcastMessage.Type.eventVideoRequest;
import static io.kasava.broadcast.LocalBroadcastMessage.Type.fileRequest;
import static io.kasava.broadcast.LocalBroadcastMessage.Type.ftdiCanRxMsg;
import static io.kasava.broadcast.LocalBroadcastMessage.Type.ftdiCanTxMsg;
import static io.kasava.broadcast.LocalBroadcastMessage.Type.immobiliserOff;
import static io.kasava.broadcast.LocalBroadcastMessage.Type.immobiliserOn;
import static io.kasava.broadcast.LocalBroadcastMessage.Type.liveViewStart;
import static io.kasava.broadcast.LocalBroadcastMessage.Type.reboot;
import static io.kasava.broadcast.LocalBroadcastMessage.Type.removeFromQueue;
import static io.kasava.broadcast.LocalBroadcastMessage.Type.terminalCmd;
import static io.kasava.broadcast.LocalBroadcastMessage.Type.updateSubscription;
import static io.kasava.broadcast.LocalBroadcastMessage.Type.updateRequest;

public class PushReceiver extends BroadcastReceiver {

    private static final String TAG = "PushReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String notificationText;

        if (intent.getStringExtra("message") != null) {
            notificationText = intent.getStringExtra("message");
            Log.d(TAG, notificationText);

            Intent intentLocal = new Intent(LocalBroadcastMessage.ID);

            if (notificationText.equals("Reboot")) {
                intentLocal.putExtra(LocalBroadcastMessage.ID, reboot);
                LocalBroadcastManager.getInstance(context).sendBroadcast(intentLocal);

            } else if (notificationText.equals("Sync")) {
                intentLocal.putExtra(LocalBroadcastMessage.ID, updateSubscription);
                LocalBroadcastManager.getInstance(context).sendBroadcast(intentLocal);

            } else if (notificationText.startsWith("Cmd")) {
                String[] cmdMsg = notificationText.split(":");
                if (cmdMsg.length == 2) {
                    Log.d(TAG, "Got terminal command request: " + cmdMsg[1]);

                    intentLocal.putExtra(LocalBroadcastMessage.ID, terminalCmd);
                    intentLocal.putExtra("cmd", cmdMsg[1]);
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intentLocal);
                }
                LocalBroadcastManager.getInstance(context).sendBroadcast(intentLocal);

            } else if (notificationText.startsWith("ClearQueue:")) {
                String[] msg = notificationText.split(":");

                if (msg.length == 2) {
                    Log.d(TAG, "Got ClearQueue request from push: " + msg[1]);
                    intentLocal.putExtra(LocalBroadcastMessage.ID, clearQueue);
                    intentLocal.putExtra("queue", msg[1]);
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intentLocal);
                }
            } else if (notificationText.equals("RemoveFromQueue")) {
                String[] msg = notificationText.split(":");

                if (msg.length == 3) {
                    intentLocal.putExtra(LocalBroadcastMessage.ID, removeFromQueue);
                    intentLocal.putExtra("queue", msg[1]);
                    intentLocal.putExtra("quantity", msg[2]);
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intentLocal);
                }
            } else if (notificationText.startsWith("Update:")) {
                String[] updateMsg = notificationText.split(":");

                if (updateMsg.length > 1) {
                    String updateStr = updateMsg[1];

                    if(updateMsg.length == 3) {
                        updateStr += ":" + updateMsg[2];
                    }

                    Log.d(TAG, "Got update request from push: " + updateStr);

                    intentLocal.putExtra(LocalBroadcastMessage.ID, updateRequest);
                    intentLocal.putExtra("uri", updateStr);
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intentLocal);
                }
            } else if (notificationText.startsWith("LiveView")) {
                Log.d(TAG, "Got liveView request from push");

                String[] lvMsg = notificationText.split(":");

                String durationS = "60";
                if (lvMsg.length == 2) {
                    durationS = lvMsg[1];
                }

                intentLocal.putExtra(LocalBroadcastMessage.ID, liveViewStart);
                intentLocal.putExtra("durationS", durationS);
                LocalBroadcastManager.getInstance(context).sendBroadcast(intentLocal);

            } else if (notificationText.startsWith("CustomVideoRequest:")) {
                String[] videoMsg = notificationText.split(":");

                if (videoMsg.length == 3) {
                    Log.d(TAG, "Got custom video request from push: " + videoMsg[1] + ", " + videoMsg[2]);

                    intentLocal.putExtra(LocalBroadcastMessage.ID, customVideoRequest);
                    intentLocal.putExtra("startDateTime", videoMsg[1]);
                    intentLocal.putExtra("durationS", videoMsg[2]);
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intentLocal);
                }
            } else if (notificationText.startsWith("EventVideoRequest:")) {
                String[] videoMsg = notificationText.split(":");

                if (videoMsg.length == 2) {
                    Log.d(TAG, "Got event video request from push: " + videoMsg[1]);

                    intentLocal.putExtra(LocalBroadcastMessage.ID, eventVideoRequest);
                    intentLocal.putExtra("dateTime", videoMsg[1]);
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intentLocal);
                }
            } else if (notificationText.startsWith("FileRequest:")) {
                String[] fileMsg = notificationText.split(":");

                if (fileMsg.length == 2) {
                    Log.d(TAG, "Got file request from push: " + fileMsg[1]);

                    intentLocal.putExtra(LocalBroadcastMessage.ID, fileRequest);
                    intentLocal.putExtra("file", fileMsg[1]);
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intentLocal);
                }
            } else if (notificationText.startsWith("Immobiliser:")) {
                String[] immobMsg = notificationText.split(":");

                if (immobMsg.length == 2) {
                    Log.d(TAG, "Got immobiliser request from push: " + immobMsg[1]);

                    if(immobMsg[1].equals("ON")) {
                        intentLocal.putExtra(LocalBroadcastMessage.ID, immobiliserOn);
                    } else {
                        intentLocal.putExtra(LocalBroadcastMessage.ID, immobiliserOff);
                    }

                    LocalBroadcastManager.getInstance(context).sendBroadcast(intentLocal);
                }
            } else if (notificationText.startsWith("CRX:")) {
                String[] canMsg = notificationText.split(":");

                if (canMsg.length == 2) {
                    intentLocal.putExtra(LocalBroadcastMessage.ID, ftdiCanRxMsg);
                    intentLocal.putExtra("msg", canMsg[1]);
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intentLocal);
                }
            } else if (notificationText.startsWith("CTX:")) {
                String[] canMsg = notificationText.split(":");

                if (canMsg.length == 2) {
                    intentLocal.putExtra(LocalBroadcastMessage.ID, ftdiCanTxMsg);
                    intentLocal.putExtra("msg", canMsg[1]);
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intentLocal);
                }
            } else {
                Log.e(TAG, "Unknown push command: " + notificationText);
            }
        }
    }
}