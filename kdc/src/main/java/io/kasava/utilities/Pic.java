package io.kasava.utilities;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.ftdi.j2xx.D2xxManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android_serialport_api.SerialPort;
import io.kasava.broadcast.LocalBroadcastMessage;
import io.kasava.data.CanMsg;
import io.kasava.data.Status;

public class Pic {
    private static String TAG = "Pic";

    private Context mContext;

    public Pic(Context context) {
        mContext = context;
    }

    private SerialPort sPort;
    private InputStream mInputStream;
    private OutputStream mOutputStream;
    private ReadThread mReadThread;

    private boolean mUartFound = false;

    private Timer timerHealthCheck;

    /* Serial port handling */
    public void start() {
        try {
            Log.d(TAG, "Setting up serial to junction box...");

            sPort = new SerialPort(new File("/dev/ttyS2"), 9600, 0);

            /* get stream */
            mInputStream = sPort.getInputStream();
            mOutputStream = sPort.getOutputStream();

            startRead();

            Log.d(TAG, "Junction box serial setup complete");
        } catch (SecurityException ex) {
            Log.d(TAG, "SecurityException: " + ex.getMessage());
        } catch (IOException ex) {
            Log.d(TAG, "IOException: " + ex.getMessage());
        }
    }


    public void uartOut(String data) {

        byte[] bytes = data.getBytes();
        Log.d(TAG, "Sending uart data: " + data);

        try {
            mOutputStream.write(bytes);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean uartFound() {
        return mUartFound;
    }


    public void startRead() {
        // Create a receiving thread
        mReadThread = new ReadThread();
        mReadThread.start();
    }


    public void stopRead() {
        mReadThread = null;
    }


    private class ReadThread extends Thread {

        byte[] msgRx = new byte[15];

        @Override
        public void run() {
            super.run();

            while (!isInterrupted()) {
                try {
                    if (mInputStream == null) return;

                    mInputStream.read(msgRx);

                    String[] strMsgRx = new String(msgRx).split("\r");

                    if (strMsgRx.length < 2) {
                        Log.d(TAG, "Uart received not valid");
                    } else {
                        mUartFound = true;

                        //Log.d(TAG, "Uart received:" + strMsgRx[0]);

                        processPicMsg(strMsgRx[0]);

                        //String[] strMsgRxSplit = strMsgRx[0].split(":");
                    }

                } catch (Exception ex) {
                    Log.d(TAG, "Error receiving UART: " + ex.getMessage());
                    ex.printStackTrace();
                    return;
                }
            }
        }
    }


    private void processPicMsg(String msg) {
        Log.d(TAG, "processPicMsg()::" + msg);

        Intent intentLocal = new Intent(LocalBroadcastMessage.ID);
        //intentLocal.putExtra(LocalBroadcastMessage.ID, LocalBroadcastMessage.Type.canRaw);
        //intentLocal.putExtra("msg", msg);
        //LocalBroadcastManager.getInstance(mContext).sendBroadcast(intentLocal);

        if(msg.startsWith("$BT1")) {
            if(msg.contains("$BT1:0")) {
                intentLocal.putExtra(LocalBroadcastMessage.ID, LocalBroadcastMessage.Type.alertButton);
                //LocalBroadcastManager.getInstance(mContext).sendBroadcast(intentLocal);
            } else {

            }

        } else if(msg.startsWith("$BT2")) {
            if(msg.contains("$BT2:1")) {
                if(timerHealthCheck != null) {
                    timerHealthCheck.cancel();
                    timerHealthCheck = null;
                }

                timerHealthCheck = new Timer();
                timerHealthCheck.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        Intent intentHealth = new Intent(LocalBroadcastMessage.ID);
                        intentHealth.putExtra(LocalBroadcastMessage.ID, LocalBroadcastMessage.Type.healthCheckManual);
                        LocalBroadcastManager.getInstance(mContext).sendBroadcast(intentHealth);
                    }
                }, 1000);

            } else {
                if(timerHealthCheck != null) {
                    timerHealthCheck.cancel();
                    timerHealthCheck = null;
                }
            }

        } else if(msg.startsWith("$ADC")) {
            int battReading = Integer.decode("0x" + msg.substring(5, 9));
            Log.d(TAG, "processPicMsg::ADC: " + battReading);

            intentLocal = new Intent(LocalBroadcastMessage.ID);
            intentLocal.putExtra(LocalBroadcastMessage.ID, LocalBroadcastMessage.Type.backupBatt);
            intentLocal.putExtra("value", (int)battReading);
            LocalBroadcastManager.getInstance(mContext).sendBroadcast(intentLocal);

        } else if(msg.startsWith("$CAN")) {
            CanMsg canMsg = Can.stringToCanMsg(msg);

            if (canMsg != null && canMsg.type != null) {
                intentLocal = new Intent(LocalBroadcastMessage.ID);
                intentLocal.putExtra(LocalBroadcastMessage.ID, LocalBroadcastMessage.Type.canData);
                intentLocal.putExtra("type", canMsg.type.toString());
                intentLocal.putExtra("value", canMsg.value);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(intentLocal);
            }
        } else {
            Log.d(TAG, "processFtdiMsg::Unknown msg: " + msg);
        }
    }







    private List<String> mPicHex = new ArrayList<>();

    public boolean getPicHexFile(String hexUrl) {
        boolean success = true;

        Log.d(TAG, "getPicHexFile()::" + hexUrl);

        try {
            mPicHex.clear();
            String tmp;

            // Create a URL for the desired page
            URL url = new URL(hexUrl);

            // Read all the text returned by the server
            BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
            while ((tmp = br.readLine()) != null) {
                mPicHex.add(tmp);
            }
            br.close();

        } catch (MalformedURLException ex) {
            success = false;
            mPicHex.clear();
            Log.e(TAG, "getPicHexFile()::failed: " + ex.getMessage());
        } catch (IOException ex) {
            success = false;
            mPicHex.clear();
            Log.e(TAG, "getPicHexFile()::failed: " + ex.getMessage());
        }

        return success;
    }

    public byte[] getReset() {
        byte[] cmdBytes = new byte[10];
        cmdBytes[0] = (byte) 0x00;
        cmdBytes[1] = (byte) 0x09;
        cmdBytes[2] = (byte) 0x00;
        cmdBytes[3] = (byte) 0x00;
        cmdBytes[4] = (byte) 0x00;
        cmdBytes[5] = (byte) 0x00;
        cmdBytes[6] = (byte) 0x00;
        cmdBytes[7] = (byte) 0x00;
        cmdBytes[8] = (byte) 0x00;
        cmdBytes[9] = (byte) 0x00;

        return cmdBytes;
    }


    public byte[] getConfig() {
        byte[] cmdBytes = new byte[10];
        cmdBytes[0] = (byte) 0x00;
        cmdBytes[1] = (byte) 0x06;
        cmdBytes[2] = (byte) 0x0E;
        cmdBytes[3] = (byte) 0x00;
        cmdBytes[4] = (byte) 0x00;
        cmdBytes[5] = (byte) 0x00;
        cmdBytes[6] = (byte) 0x00;
        cmdBytes[7] = (byte) 0x00;
        cmdBytes[8] = (byte) 0x30;
        cmdBytes[9] = (byte) 0x00;

        return cmdBytes;
    }

    public byte[] setConfig() {
        byte[] cmdBytes = new byte[24];
        cmdBytes[0] = (byte) 0x00;
        cmdBytes[1] = (byte) 0x07;
        cmdBytes[2] = (byte) 0x0E;
        cmdBytes[3] = (byte) 0x00;
        cmdBytes[4] = (byte) 0x55;
        cmdBytes[5] = (byte) 0xAA;
        cmdBytes[6] = (byte) 0x00;
        cmdBytes[7] = (byte) 0x00;
        cmdBytes[8] = (byte) 0x30;
        cmdBytes[9] = (byte) 0x00;

        cmdBytes[10] = (byte) 0xEC;
        cmdBytes[11] = (byte) 0xFF;

        cmdBytes[12] = (byte) 0xF7;
        cmdBytes[13] = (byte) 0xFF;

        cmdBytes[14] = (byte) 0xED;
        cmdBytes[15] = (byte) 0xFF;

        cmdBytes[16] = (byte) 0xF5;
        cmdBytes[17] = (byte) 0xDF;

        cmdBytes[18] = (byte) 0xFF;
        cmdBytes[19] = (byte) 0xFF;

        cmdBytes[20] = (byte) 0xFF;
        cmdBytes[21] = (byte) 0xFF;

        cmdBytes[22] = (byte) 0xFF;
        cmdBytes[23] = (byte) 0xFF;

        return cmdBytes;
    }

    /*
    public int getAddressAfter(int lastAddress) {
        int nextAddress = 0;
        int picHexLineIndex = 3;

        while (nextAddress == 0 && picHexLineIndex < mPicHex.size()) {
            byte[] addressBytes = new BigInteger(mPicHex.get(picHexLineIndex).substring(3, 7), 16).toByteArray();

            if (addressBytes.length == 2) {
                int address = (addressBytes[0] << 8) + addressBytes[1];

                if(address >= (lastAddress + 64)) {
                    nextAddress = address;
                }
            }

            picHexLineIndex++;
        }

        return nextAddress;
    }*/

    public byte[] getEraseForAddress(int nextAddress) {
        byte[] cmdBytes = new byte[10];
        cmdBytes[0] = (byte) 0x00;
        cmdBytes[1] = (byte) 0x03;
        cmdBytes[2] = (byte) 0xEC;
        cmdBytes[3] = (byte) 0x00;
        cmdBytes[4] = (byte) 0x55;
        cmdBytes[5] = (byte) 0xAA;
        cmdBytes[6] = (byte) (nextAddress & 0xFF);
        cmdBytes[7] = (byte) (nextAddress >> 8 & 0xFF);
        cmdBytes[8] = (byte) 0x00;
        cmdBytes[9] = (byte) 0x00;

        return cmdBytes;
    }

    public boolean isWriteCodeForAddress(int addressToCheck) {
        int picHexLineIndex = 3;
        boolean addressCodeFound = false;

        while (!addressCodeFound && picHexLineIndex < mPicHex.size()) {
            byte[] addressBytes = new BigInteger(mPicHex.get(picHexLineIndex).substring(3, 7), 16).toByteArray();

            if (addressBytes.length == 2) {
                int address = (addressBytes[0] << 8) + addressBytes[1];

                if (address >= addressToCheck && address < addressToCheck + 0x40) {
                    addressCodeFound = true;
                }
            }

            picHexLineIndex++;
        }

        return addressCodeFound;
    }

    public byte[] get64BytesForAddress(int nextAddress) {
        int picHexLineIndex = 3;

        byte[] cmdBytes = new byte[74];
        Arrays.fill(cmdBytes, (byte) 0xFF);
        cmdBytes[0] = (byte) 0x00;
        cmdBytes[1] = (byte) 0x02;
        cmdBytes[2] = (byte) 0x40;
        cmdBytes[3] = (byte) 0x00;
        cmdBytes[4] = (byte) 0x55;
        cmdBytes[5] = (byte) 0xAA;
        cmdBytes[6] = (byte) (nextAddress & 0xFF);
        cmdBytes[7] = (byte) (nextAddress >> 8 & 0xFF);
        cmdBytes[8] = (byte) 0x00;
        cmdBytes[9] = (byte) 0x00;

        while (picHexLineIndex < mPicHex.size()) {
            byte[] length = new BigInteger(mPicHex.get(picHexLineIndex).substring(1, 3), 16).toByteArray();

            Log.d(TAG, "get64BytesForAddress()::" + length[0]);

            byte[] addressBytes = new BigInteger(mPicHex.get(picHexLineIndex).substring(3, 7), 16).toByteArray();

            if (addressBytes.length == 2) {
                int address = (addressBytes[0] << 8) + addressBytes[1];

                if (address >= nextAddress && address < nextAddress + 0x40) {
                    for (int i = 0; i < length[0]; i += 2) {
                        Log.d(TAG, "get64BytesForAddress()::subStr: " + mPicHex.get(picHexLineIndex).substring(9 + i * 2, 13 + i * 2));
                        byte[] byteBuf = new BigInteger(mPicHex.get(picHexLineIndex).substring(9 + i * 2, 13 + i * 2), 16).toByteArray();

                        if (byteBuf.length == 3) {
                            cmdBytes[i + address - nextAddress + 10] = byteBuf[1];
                            cmdBytes[i + address - nextAddress + 11] = byteBuf[2];
                        } else if (byteBuf.length == 2) {
                            cmdBytes[i + address - nextAddress + 10] = byteBuf[0];
                            cmdBytes[i + address - nextAddress + 11] = byteBuf[1];
                        } else {
                            cmdBytes[i + address - nextAddress + 10] = 0x00;
                            cmdBytes[i + address - nextAddress + 11] = byteBuf[0];
                        }
                    }
                }
            }
            picHexLineIndex++;
        }

        return cmdBytes;
    }
}
