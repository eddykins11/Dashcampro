package io.kasava.utilities;

import android.util.Log;

import io.kasava.data.CanMsg;

public class Can {

    private static String TAG = "CAN";

    public static String getCanRxStr1() {
        return "CRX:00000520";
    }

    public static String getCanRxStr() {
        return "CRX:000007E8";
    }

    public static String getCanTxStr1() {
        return "CTX:000007DF01080000000000000102";
    }

    public static String getCanTxStrFuelTank() {
        return "CTX:000007DF010800000000002F0102";
    }
    public static String getCanTxStrEvBattery() {
        return "CTX:000007DF010800000000005B0102";
    }

    public static CanMsg stringToCanMsg(String msg) {
        Log.d(TAG, "stringToCanMsg()::" + msg);

        CanMsg canMsg = new CanMsg();

        if(msg.length() == 33 && msg.startsWith("$")) {
            canMsg.type = getCanTypeFromMsg(msg);
            if(canMsg.type != null) {
                canMsg.value = getCanValueFromType(canMsg.type, msg);
            }
        }

        return canMsg;
    }


    private static CanMsg.TYPE getCanTypeFromMsg(String msg) {
        CanMsg.TYPE canType = null;

        int id = Integer.decode("0x" + msg.substring(5, 13));

        if(id == 0x07E8) {
            int typeInt = Integer.decode("0x" + msg.substring(27, 29));

            switch (typeInt) {
                case 0x2F:
                    canType = CanMsg.TYPE.FUEL_TANK_PERC;
                    break;
                case 0x5B:
                    canType = CanMsg.TYPE.EV_BATTERY_PERC;
                    break;
            }
        }

        return canType;
    }

    private static double getCanValueFromType(CanMsg.TYPE type, String msg) {
        double canValue = 0;

        switch (type) {
            case FUEL_TANK_PERC:
                canValue = (double)Integer.decode("0x" + msg.substring(25, 27)) * 100 / 255;
                break;
            case EV_BATTERY_PERC:
                canValue = (double)Integer.decode("0x" + msg.substring(25, 27)) * 100 / 255;
                break;
        }

        return canValue;
    }
}
