package io.kasava.broadcast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;

import static io.kasava.broadcast.LocalBroadcastMessage.Type;

public class AndroidReceiver extends BroadcastReceiver {

    private static final String TAG = "AndroidReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {

        if(intent.getAction() != null) {

            Intent intentLocal = new Intent(LocalBroadcastMessage.ID);

            switch (intent.getAction()) {
                case Intent.ACTION_MEDIA_UNMOUNTED:
                    Log.d(TAG, "AndroidReceiver()::SD removed");
                    intentLocal.putExtra(LocalBroadcastMessage.ID, Type.event);
                    intentLocal.putExtra(LocalBroadcastMessage.EXTRA, "SD_UNMOUNTED");
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intentLocal);
                    break;
                case Intent.ACTION_MEDIA_MOUNTED:
                    Log.d(TAG, "AndroidReceiver()::SD detected");
                    intentLocal.putExtra(LocalBroadcastMessage.ID, Type.event);
                    intentLocal.putExtra(LocalBroadcastMessage.EXTRA, "SD_MOUNTED");
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intentLocal);
                    break;
            }
        }
    }
}