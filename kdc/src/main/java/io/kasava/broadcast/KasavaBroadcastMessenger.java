package io.kasava.broadcast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by E.Barker on 07/05/2017.
 */

public class KasavaBroadcastMessenger {
    private List<KasavaBroadcastListener> listeners = new ArrayList<>();
    private Context context;

    public KasavaBroadcastMessenger(Context context){
        this.context = context;
        this.registerReceivers();
    }

    public void addListener(KasavaBroadcastListener listener) {
        listeners.add(listener);
    }

    public void sendMessage(String type, String message){
        Intent intent = new Intent(type);
        intent.putExtra("message", message);
        //context.sendBroadcast(intent);
        context.sendBroadcastAsUser(intent, android.os.Process.myUserHandle());
    }

    private void registerReceivers(){
        registerFilter(KasavaBroadcastMessage.WATCHDOG_APP_VERSION);
        registerFilter(KasavaBroadcastMessage.WAKEUP);
        registerFilter(KasavaBroadcastMessage.DRIVER_DISTRACTED);
    }

    private void registerFilter(String name){
        IntentFilter filter = new IntentFilter(name);
        context.registerReceiver(new Receiver(), filter);
    }

    private class Receiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context ctx, Intent intent) {
            String message = "";
            String type = intent.getAction();
            if (intent.getExtras() != null && intent.getExtras().getString("message") != null) {
                message = intent.getExtras().getString("message");
            }
            notifyMessageReceived(type, message);
        }
    }

    private void notifyMessageReceived(String type, String message){
        for(KasavaBroadcastListener listener : listeners){
            listener.onMessageReceived(type,message);
        }
    }

    public interface KasavaBroadcastListener{
        void onMessageReceived(String type, String message);
    }

}
