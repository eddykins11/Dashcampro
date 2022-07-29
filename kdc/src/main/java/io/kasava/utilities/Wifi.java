package io.kasava.utilities;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.text.TextUtils;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Created by E.Barker on 20/02/2017.
 */

public class Wifi {
    private static String TAG = "Wifi";

    private final WifiManager mWifiManager;
    private Context mContext;

    private String mSsid;
    private String mNetworkKey;

    private static final String WPA = "WPA";
    private static final String WEP = "WEP";
    private static final String OPEN = "Open";

    public Wifi(String ssid, String networkKey, Context context) {
        mContext = context;

        mSsid = ssid;
        mNetworkKey = networkKey;

        mWifiManager = (WifiManager) mContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    }

    public void start() {
        Log.d(TAG, "start()");

        try {
            Method getConfigMethod = mWifiManager.getClass().getMethod("getWifiApConfiguration");
            WifiConfiguration wifiConfig = (WifiConfiguration) getConfigMethod.invoke(mWifiManager);

            if(!wifiConfig.SSID.equals(mSsid)) {
                // Turn off WiFi so we can rename hotspot
                stop();
            }
        } catch (Exception ex) {
            Log.e(TAG, "start()::Failed to get current WiFi name: " + ex.getMessage());
        }

        WifiConfiguration config = new WifiConfiguration();
        config.SSID = mSsid;
        config.preSharedKey = mNetworkKey;
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);

        setWifiApEnabled(config, true); //always enable regardless of the current state
    }

    public void stop() {
        Log.d(TAG, "stop()");

        setWifiApEnabled(null, false); //always disable regardless of the current state
    }

    private void setWifiApEnabled(WifiConfiguration wifiConfig, boolean enabled) {
        try {
            if (enabled) { // disable WiFi in any case
                mWifiManager.setWifiEnabled(false);
            }

            Method method = mWifiManager.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);

            method.invoke(mWifiManager, wifiConfig, enabled);
        }
        catch (Exception ex) {
            Log.e(this.getClass().toString(), "", ex);
        }
    }

    public int requestWIFIConnection(String networkSSID, String networkPass) {
        try {
            WifiManager wifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);

            if (!wifiManager.isWifiEnabled()) {
                wifiManager.setWifiEnabled(true);
                Log.d(TAG, "Wifi Turned On");
            }

            //Check ssid exists
            if (scanWifi(wifiManager, networkSSID)) {
                if (getCurrentSSID(wifiManager) != null && getCurrentSSID(wifiManager).equals("\"" + networkSSID + "\"")) {
                    Log.d(TAG, "Already Connected With " + networkSSID);
                    return 0;
                }
                //Security type detection
                String SECURE_TYPE = checkSecurity(wifiManager, networkSSID);
                if (SECURE_TYPE == null) {
                    Log.d(TAG, "Unable to find Security type for " + networkSSID);
                    return 0;
                }
                if (SECURE_TYPE.equals(WPA)) {
                    WPA(networkSSID, networkPass, wifiManager);
                } else if (SECURE_TYPE.equals(WEP)) {
                    WEP(networkSSID, networkPass);
                } else {
                    OPEN(wifiManager, networkSSID);
                }
                return 0;

            }
        } catch (Exception e) {
            Log.d(TAG, "Error Connecting WIFI " + e);
        }
        return 0;
    }

    private void WPA(String networkSSID, String networkPass, WifiManager wifiManager) {
        WifiConfiguration wc = new WifiConfiguration();
        wc.SSID = "\"" + networkSSID + "\"";
        wc.preSharedKey = "\"" + networkPass + "\"";
        wc.status = WifiConfiguration.Status.ENABLED;
        wc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
        wc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
        wc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        wc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
        wc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
        wc.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
        int id = wifiManager.addNetwork(wc);
        wifiManager.disconnect();
        wifiManager.enableNetwork(id, true);
        wifiManager.reconnect();
    }

    private void WEP(String networkSSID, String networkPass) {
    }

    private void OPEN(WifiManager wifiManager, String networkSSID) {
        WifiConfiguration wc = new WifiConfiguration();
        wc.SSID = "\"" + networkSSID + "\"";
        wc.hiddenSSID = true;
        wc.priority = 0xBADBAD;
        wc.status = WifiConfiguration.Status.ENABLED;
        wc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        int id = wifiManager.addNetwork(wc);
        wifiManager.disconnect();
        wifiManager.enableNetwork(id, true);
        wifiManager.reconnect();
    }

    private boolean scanWifi(WifiManager wifiManager, String networkSSID) {
        Log.e(TAG, "scanWifi starts");
        List<ScanResult> scanList = wifiManager.getScanResults();
        for (ScanResult i : scanList) {
            if (i.SSID != null) {
                Log.e(TAG, "SSID: " + i.SSID);
            }

            if (i.SSID != null && i.SSID.equals(networkSSID)) {
                Log.e(TAG, "Found SSID: " + i.SSID);
                return true;
            }
        }
        Log.d(TAG, "SSID " + networkSSID + " Not Found");
        return false;
    }

    private String getCurrentSSID(WifiManager wifiManager) {
        String ssid = null;
        ConnectivityManager connManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (networkInfo.isConnected()) {
            final WifiInfo connectionInfo = wifiManager.getConnectionInfo();
            if (connectionInfo != null && !TextUtils.isEmpty(connectionInfo.getSSID())) {
                ssid = connectionInfo.getSSID();
            }
        }
        return ssid;
    }

    private String checkSecurity(WifiManager wifiManager, String ssid) {
        List<ScanResult> networkList = wifiManager.getScanResults();
        for (ScanResult network : networkList) {
            if (network.SSID.equals(ssid)) {
                String Capabilities = network.capabilities;
                if (Capabilities.contains("WPA")) {
                    return WPA;
                } else if (Capabilities.contains("WEP")) {
                    return WEP;
                } else {
                    return OPEN;
                }

            }
        }
        return null;
    }
}