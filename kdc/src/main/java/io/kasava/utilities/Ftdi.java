package io.kasava.utilities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.ftdi.j2xx.D2xxManager;
import com.ftdi.j2xx.FT_Device;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import io.kasava.broadcast.LocalBroadcastMessage;
import io.kasava.dashcampro.KdcService;
import io.kasava.data.CanMsg;
import io.kasava.data.FtdiMsg;

import static io.kasava.broadcast.LocalBroadcastMessage.Type.ftdiQueueEmpty;

public class Ftdi {
    private static String TAG = "Ftdi";

    private Context mContext;
    private Pic mPic;

    private D2xxManager mFtdid2xxContext;
    private FT_Device mFtDevice = null;
    private D2xxManager.DriverParameters mD2xxDrvParameter;

    private boolean mFtdiOpened = false;
    private int mWriteSuccessCount = 0;
    private int mWriteFailCount = 0;
    private List<FtdiMsg> mWriteQueue = new ArrayList<>();
    private boolean mWriteInProgess = false;

    private FtdiReadThread mFtdiReadThread;
    private boolean mFtdiReadThreadActive = false;
    private static int rxPos = 0;
    private boolean mReplyPending = false;
    private Timer timerReplyPending;

    private Timer timerHealthCheck;

    private boolean mPicUpdateInProgress = false;
    private PicUpdateReadThread mPicUpdateReadThread;

    public Ftdi(Context context, D2xxManager ftdid2xxContext) {
        mContext = context;
        mFtdid2xxContext = ftdid2xxContext;
        mPic = new Pic(mContext);

        IntentFilter filter = new IntentFilter();
        filter.addAction("android.hardware.usb.action.USB_DEVICE_ATTACHED");
        mContext.getApplicationContext().registerReceiver(this.mUsbPlugEvents, filter);

        mPic.start();
    }

    private final BroadcastReceiver mUsbPlugEvents = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            UsbDevice dev;
            if ("android.hardware.usb.action.USB_DEVICE_ATTACHED".equals(action)) {
                dev = intent.getParcelableExtra("device");

                if(dev.getVendorId() == 1027 && dev.getProductId() == 24597) {
                    mFtdid2xxContext.addUsbDevice(dev);

                    openDevice(dev);

                    Log.d(TAG, "mUsbPlugEvents()::" + dev);
                }
            }
        }
    };

    private void openDevice(UsbDevice dev) {
        Log.d(TAG, "openDevice()");

        if (mFtDevice != null && mFtDevice.isOpen()) {
            Log.d(TAG, "openDevice()::aleady open");
            return;
        }

        // openBySerial
        mFtDevice = mFtdid2xxContext.openByUsbDevice(mContext, dev);
        if (mFtDevice != null && mFtDevice.isOpen()) {
            Log.d(TAG, "Open by index: Pass");
            mFtDevice.setBaudRate(9600);
            mFtdiOpened = true;

            if (mPicUpdateInProgress) {
                if (mPicUpdateReadThread == null) {
                    mPicUpdateReadThread = new PicUpdateReadThread();
                    mPicUpdateReadThread.start();
                }
            } else {
                if (mFtdiReadThread == null) {
                    mFtdiReadThreadActive = true;
                    mFtdiReadThread = new FtdiReadThread();
                    mFtdiReadThread.start();
                }
            }
        } else {
            Log.d(TAG, "Open by index: Fail");
        }
    }

    public void openDevice2() {
        Log.d(TAG, "openDevice()");

        if(mFtDevice != null && mFtDevice.isOpen()) {
            Log.d(TAG, "openDevice()::aleady open");
            return;
        }

        int devCount = mFtdid2xxContext.createDeviceInfoList(mContext);

        Log.d(TAG, "FTDI device count: " + devCount);

        //mFtdid2xxContext.addUsbDevice()

        if (devCount > 0) {
            D2xxManager.FtDeviceInfoListNode node = mFtdid2xxContext.getDeviceInfoListDetail(0);
            Log.d(TAG, "FTDI mode: " + node);

            // openByIndex
            mFtDevice = mFtdid2xxContext.openByIndex(mContext, 0, mD2xxDrvParameter);
            if (mFtDevice != null && mFtDevice.isOpen()) {
                Log.d(TAG, "Open by index: Pass");
                mFtDevice.setBaudRate(9600);

                if(mPicUpdateInProgress) {
                    if(mPicUpdateReadThread == null) {
                        mPicUpdateReadThread = new PicUpdateReadThread();
                        mPicUpdateReadThread.start();
                    }
                } else {
                    if(mFtdiReadThread == null) {
                        mFtdiReadThreadActive = true;
                        mFtdiReadThread = new FtdiReadThread();
                        mFtdiReadThread.start();
                    }
                }
            } else {
                Log.d(TAG, "Open by index: Fail");
            }
        }
    }

    public void closeDevice() {
        Log.d(TAG, "closeDevice()");

        mFtdiReadThreadActive = false;
        mFtdiReadThread = null;

        if (mFtDevice != null) {
            mFtDevice.close();
            mFtDevice = null;
        }
    }

    public void addToWriteQueue(FtdiMsg msg) {
        Log.d(TAG, "addToWriteQueue()::" + msg.msg);

        Log.d(TAG, "writeToDeviceViaUart()::" + msg.msg);
        mPic.uartOut("$" + msg.msg + "\r");

        if(!mPic.uartFound()) {
            mWriteQueue.add(msg);
            Thread thread = new Thread(this::writeToDevice);
            thread.start();
        }
    }

    public int getQueueSize() {
        return mWriteQueue.size();
    }

    public boolean isReplyPending() {
        return mReplyPending;
    }

    public void writeToDevice() {
        // Don't write any commands while pic is updating
        if(mPicUpdateInProgress) {
            return;
        }

        if(mWriteQueue.isEmpty()) {
            Intent intentLocal = new Intent(LocalBroadcastMessage.ID);
            intentLocal.putExtra(LocalBroadcastMessage.ID, ftdiQueueEmpty);
            LocalBroadcastManager.getInstance(mContext).sendBroadcast(intentLocal);
        }

        if(mWriteInProgess || mWriteQueue.isEmpty()) {
            mWriteSuccessCount = 0;
            return;
        }

        mWriteInProgess = true;

        if(mFtDevice == null || !mFtDevice.isOpen()) {
            //openDevice();
        }

        FtdiMsg ftdiMsg = mWriteQueue.get(0);

        byte[] txBytes = ("$" + ftdiMsg.msg + "\r").getBytes();

        if (mFtDevice != null && mFtDevice.isOpen()) {
            Log.d(TAG, "writeToDevice()::" + ftdiMsg.msg);
            mFtDevice.write(txBytes, txBytes.length);

            if(ftdiMsg.getsReply) {
                mReplyPending = true;
                if (timerReplyPending != null) {
                    timerReplyPending.cancel();
                }

                timerReplyPending = new Timer();
                timerReplyPending.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        mReplyPending = false;
                    }
                }, 2 * 1000);
            }

            mWriteSuccessCount++;

            if(mWriteSuccessCount >= ftdiMsg.attempts) {
                mWriteQueue.remove(0);
                mWriteSuccessCount = 0;
                mWriteFailCount = 0;
            }
        } else {
            Log.e(TAG, "writeToDevice()::failed: " + ftdiMsg.msg);

            mWriteFailCount++;

            if(mWriteFailCount < 5) {
                Log.d(TAG, "writeToDevice()::try again...");

                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ex) {
                    Log.e(TAG, "writeToDevice()::failed to delay");
                }
                //closeDevice();

            } else {
                mWriteQueue.remove(0);
                mWriteFailCount = 0;
            }
        }

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                mWriteInProgess = false;
                writeToDevice();
            }
        }, 200);
    }

    public void setSleepEnabled(boolean enabled) {
        if(enabled) {
            addToWriteQueue(new FtdiMsg("SEN:1", 2, false));
        } else {
            addToWriteQueue(new FtdiMsg("SEN:0", 2, false));
        }
    }

    public void setStandbyHours(int hours) {
        addToWriteQueue(new FtdiMsg("SLP:" + String.format("%02d", hours), 2, false));
    }


    /********** Reading ************/

    private class FtdiReadThread extends Thread {

        byte[] rxByte = new byte[1];
        byte[] rxMsg = new byte[100];

        @Override
        public void run() {
            //super.run();

            while (mFtdiReadThreadActive) {

                try {
                    if(mFtdiOpened && !mFtDevice.isOpen()) {
                        Intent intentLocal = new Intent(LocalBroadcastMessage.ID);
                        intentLocal.putExtra(LocalBroadcastMessage.ID, LocalBroadcastMessage.Type.shutdown);
                        LocalBroadcastManager.getInstance(mContext).sendBroadcast(intentLocal);
                        Log.e(TAG, "FTDI lost connection!");
                        mFtdiOpened = false;
                    }
                    //Log.d(TAG ,mFtDevice + ", " + mFtDevice.isOpen() + ", " + mFtDevice.getQueueStatus());
                    if(mFtDevice != null && mFtDevice.isOpen() && mFtDevice.getQueueStatus() > 0) {
                        mFtDevice.read(rxByte, 1);

                        //Log.d(TAG, "byte(" + rxPos + "):" + new String(rxByte));

                        if (rxByte[0] == 0x0D) { //return character read

                            byte[] rxBytesTrimmed = trim(rxMsg);
                            String rxString = new String(rxBytesTrimmed);

                            Thread thread = new Thread(() -> {
                                processFtdiMsg(rxString);
                            });
                            thread.start();

                            rxPos = 0;
                        } else {
                            rxMsg[rxPos] = rxByte[0];
                            rxPos++;

                            // Prevent overflow if serial port has buffered data
                            if(rxPos > 90) {
                                rxPos = 0;
                            }
                        }
                    } else {

                    }

                } catch (Exception ex) {
                    Log.d(TAG, "Error receiving FTDI UART: " + ex.getMessage());
                    ex.printStackTrace();
                    return;
                }
            }
        }
    }

    private void processFtdiMsg(String msg) {
        Log.d(TAG, "processFtdiMsg()::" + msg);

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
            //Log.d(TAG, "processFtdiMsg::ADC: " + battReading);

            float battPer = battReading - 900;
            battPer *= 0.65;

            //Log.d(TAG, "processFtdiMsg::ADC: " + battPer);

            if(battPer > 120) { battPer = 101; }
            else if(battPer > 100) { battPer = 100; }
            else if(battPer < 1) { battPer = 1; }

            Log.d(TAG, "processFtdiMsg::ADC batt: " + battPer + "%");

            intentLocal = new Intent(LocalBroadcastMessage.ID);
            intentLocal.putExtra(LocalBroadcastMessage.ID, LocalBroadcastMessage.Type.backupBatt);
            intentLocal.putExtra("value", (int)battPer);
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

    static byte[] trim(byte[] bytes) {
        // Find $ start
        int i = 0;
        while (i < rxPos && bytes[i] != 0x24) {
            i++;
        }

        int j = rxPos - 1;
        while (j >= 0 && bytes[j] == 0) {
            --j;
        }

        return Arrays.copyOfRange(bytes, i, j+1);
    }


    /********** Pic Upgrading ************/

    private int nextAddress = 0;
    private int rxPicPos = 0;

    public void startPicUpdating(String hexUrl) {
        if(mPicUpdateInProgress) {
            return;
        }

        mPicUpdateInProgress = true;

        if(mFtDevice == null || !mFtDevice.isOpen()) {
            //openDevice();
        }

        mPic.getPicHexFile(hexUrl);

        nextAddress = 0x1000;

        //writeBytesToDevice(mPic.getConfig());
        writeBytesToDevice(mPic.setConfig());
        //writeBytesToDevice(mPic.getEraseForAddress(nextAddress));
        //writeBytesToDevice(mPic.get64BytesForAddress(nextAddress));
    }

    private void writeBytesToDevice(byte[] bytesToWrite) {
        // Only write commands while pic is updating
        if(!mPicUpdateInProgress) {
            return;
        }

        if (mFtDevice != null && mFtDevice.isOpen()) {
            Log.d(TAG, "writeFlashPageToDevice()::" + bytesToWrite.length + ": " + bytesToHex(bytesToWrite));
            rxPicPos = 0;
            mFtDevice.write(bytesToWrite, bytesToWrite.length);

        } else {
            Log.e(TAG, "writeFlashPageToDevice()::failed");
            mPicUpdateInProgress = false;
        }
    }

    private class PicUpdateReadThread extends Thread {

        byte[] rxByte = new byte[1];
        byte[] rxMsg = new byte[25];

        @Override
        public void run() {
            //super.run();
            while (!isInterrupted() && mPicUpdateInProgress) {
                try {
                    if(mFtDevice != null && mFtDevice.isOpen() && mFtDevice.getQueueStatus() > 0) {
                        mFtDevice.read(rxByte, 1);

                        //Log.d(TAG, "byte(" + rxPicPos + "):" + bytesToHex(rxByte) );

                        rxMsg[rxPicPos] = rxByte[0];
                        rxPicPos++;

                        if(rxPicPos > 10) { //10  23
                            rxPicPos = 0;
                            Log.d(TAG, "bytes:" + bytesToHex(rxMsg));

                            if(rxMsg[1] == 0x03) { // Erasing
                                if(rxMsg[10] == 0x01) { // Success
                                    nextAddress += 0x80;

                                    if (nextAddress < 0x7FFF) {
                                        writeBytesToDevice(mPic.getEraseForAddress(nextAddress));
                                    } else {
                                        // Ready to write new program
                                        nextAddress = 0x1000;
                                        writeBytesToDevice(mPic.get64BytesForAddress(nextAddress));
                                    }
                                } else {
                                    // Try again
                                    writeBytesToDevice(mPic.getEraseForAddress(nextAddress));
                                }
                            } else if(rxMsg[1] == 0x02) { // Writing
                                if(rxMsg[10] == 0x01) { // Success
                                    nextAddress += 0x80;

                                    while(nextAddress < 0x7FFF && !mPic.isWriteCodeForAddress(nextAddress)) {
                                        nextAddress += 0x80;
                                    }

                                    if (nextAddress < 0x7FFF) {
                                        writeBytesToDevice(mPic.get64BytesForAddress(nextAddress));
                                    } else {
                                        // Ready to reset PIC
                                        mPicUpdateInProgress = false;
                                    }
                                } else {
                                    // Try again
                                    writeBytesToDevice(mPic.getEraseForAddress(nextAddress));
                                }
                            } else {
                                mPicUpdateInProgress = false;
                            }
                        }
                    }
                } catch (Exception ex) {
                    Log.d(TAG, "Error receiving FTDI UART: " + ex.getMessage());
                    //ex.printStackTrace();
                    return;
                }
            }
        }
    }

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }
}