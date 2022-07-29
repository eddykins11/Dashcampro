package io.kasava.data;

public class Constants {

    public static final String FOLDER_RECORDINGS = "kasava/recordings";
    public static final String FOLDER_EVENTS = "kasava/events";
    public static final String FOLDER_CLIPS = "kasava/clips";
    public static final String FOLDER_LIVEVIEW = "/storage/sdcard0/kasava/lv";

    public static final String CAM_EXTENSION = ".vid";
    public static final String MP4_EXTENSION = ".mp4";
    public static final String VIDEO_PART_EXTENSION = ".prt";
    public static final String LOG_EXTENSION = ".log";
    public static final String CSV_EXTENSION = ".csv";
    public static final String ENCRYTED_EXTENSION = ".kas";

    public static final String JOURNEY_FILENAME = "journey";
    public static final String EVENT_FILENAME = "events" + LOG_EXTENSION;

    public static final String LV1_FILEPATH = FOLDER_LIVEVIEW + "/lv1.jpg";
    public static final String LV2_FILEPATH = FOLDER_LIVEVIEW + "/lv2.jpg";
    public static final String LV3_FILEPATH = FOLDER_LIVEVIEW + "/lv3.jpg";

    // Legacy
    public static final String FOLDER_RECORDINGS_LEGACY = "continuous";
    public static final String FOLDER_CLIPS_LEGACY = "clips";

    // DateTime formats
    public static final String DATETIME_FORMAT_LONG = "yyyy-MM-dd-HH-mm-ss-SS";
    public static final String DATETIME_FORMAT_AZURE = "yyyy-MM-dd'T'HH:mm:ss.SS'Z'";
    public static final String DATE_FORMAT = "yyyy-MM-dd";
    public static final String TIME_FORMAT = "HH:mm:ss.SS";

    // IDR frame bytes
    public static final byte[] IDR_402_720 = new byte[]{(byte) 0x65, (byte) 0x88, (byte) 0x80, (byte) 0x00};
    public static final byte[] IDR_402_480 = new byte[]{(byte) 0x65, (byte) 0x88, (byte) 0x80};
    //public static final byte[] IDR_403_1080 = new byte[]{(byte) 0x65, (byte) 0x88, (byte) 0x80, (byte) 0x00};
    //public static final byte[] IDR_403_720 = new byte[]{(byte) 0x65, (byte) 0x88, (byte) 0x80, (byte) 0x00};
    public static final byte[] IDR_403a_720 = new byte[]{(byte) 0x65, (byte) 0x00, (byte) 0x25, (byte) 0x88};
    public static final byte[] IDR_403_480 = new byte[]{(byte) 0x65, (byte) 0x88, (byte) 0x80};
    public static final byte[] IDR_404_1080 = new byte[]{(byte) 0x65, (byte) 0x88, (byte) 0x80, (byte) 0x00};
    public static final byte[] IDR_404_720 = new byte[]{(byte) 0x65, (byte) 0x88, (byte) 0x80, (byte) 0x00};
    public static final byte[] IDR_404a_720 = new byte[]{(byte) 0x65, (byte) 0x88, (byte) 0x80, (byte) 0x00};
    public static final byte[] IDR_404_480 = new byte[]{(byte) 0x65, (byte) 0x88, (byte) 0x80};
    public static final byte[] IDR_405_1080 = new byte[]{(byte) 0x65, (byte) 0x88, (byte) 0x80, (byte) 0x00};
    public static final byte[] IDR_405_720 = new byte[]{(byte) 0x65, (byte) 0x88, (byte) 0x80, (byte) 0x00};
    public static final byte[] IDR_405_480 = new byte[]{(byte) 0x65, (byte) 0x88, (byte) 0x80, (byte) 0x00};

    public static final String JOURNEY_HEADER = "DateTime:LocDateTime:Lat:Lng:Speed:Head:Alt:Acu:aX:aY:aZ:Pitch";
}
