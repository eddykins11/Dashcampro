package io.kasava.broadcast;

/**
 * Created by E.Barker on 07/05/2017.
 */

public class KasavaBroadcastMessage {
    private static final String packageName = "io.kasava.broadcast.";

    public static final String REQUEST_APP_VERSION = packageName + "REQUEST_APP_VERSION";
    public static final String WATCHDOG_APP_VERSION = packageName + "WATCHDOG_APP_VERSION";
    public static final String WATCHDOG_RESET = packageName + "WATCHDOG_RESET";
    public static final String WAKEUP = packageName + "WAKEUP";
    public static final String IGN_OFF = packageName + "IGN_OFF";
    public static final String IGN_ON = packageName + "IGN_ON";
    public static final String UPDATE = packageName + "UPDATE";

    public static final String REQUEST_DRIVER_CHECK = packageName + "REQUEST_DRIVER_CHECK";
    public static final String DRIVER_DISTRACTED = packageName + "DRIVER_DISTRACTED";
}
