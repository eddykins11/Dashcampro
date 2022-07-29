package io.kasava.utilities;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class Bluetooth {
    private static String TAG = "Bluetooth";

    private Context mContext;

    private static BluetoothAdapter mBluetoothAdapter;

    public Bluetooth(Context context) {
        mContext = context;

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public void start() {
        Log.d(TAG, "start()");

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter == null) {
            Log.d(TAG, "Bluetooth not supported!");
        } else {
            if (mBluetoothAdapter.isEnabled()) {
                Log.d(TAG, "Bluetooth enabled");
            } else {
                Log.d(TAG, "Bluetooth disabled");

                mBluetoothAdapter.enable();
            }
        }
    }
}
