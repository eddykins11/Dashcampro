package io.kasava.data;

public class HealthCheckData {
    public String accountId;
    public String deviceId;
    public String dateTime;
    public int type; // 1=manual press, 2=manual remote, 3=auto,
    public boolean camera1;
    public boolean camera2;
    public boolean camera3;
    public boolean sim;
    public boolean network;
    public boolean gps;
    public boolean sdPresent;
    public boolean sdWrite;
    public boolean motion;
    public boolean ign;
    public int emmcFreeMB;
    public int sdFreeMB;
}