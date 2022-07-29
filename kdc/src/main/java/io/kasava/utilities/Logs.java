package io.kasava.utilities;

import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Calendar;

import io.kasava.data.Constants;

public class Logs {

    public static String TAG = "Logs";

    // Log files
    private static File recJourneyF = null;
    private static FileWriter recJourneyFw = null;

    public void createNewRecordingLogs(File dir, String dateTimeStr) {
        try {
            recJourneyF = new File(dir, Constants.JOURNEY_FILENAME + "_" + dateTimeStr + "_part" + Constants.LOG_EXTENSION);
            recJourneyFw = new FileWriter(recJourneyF, true);
            writeRecordingJourneyLog(Constants.JOURNEY_HEADER);
            Log.d(TAG, "createNewRecordingLogs()::Journey log " + recJourneyF);
        }
        catch (IOException ex) {
            Log.e(TAG, "createNewRecordingLogs()::could not create Journey log - " + ex.getMessage());
        }
    }

    public File getCurrentRecordingDir() {
        if(recJourneyF != null) {
            return recJourneyF.getParentFile();
        }

        return null;
    }

    public File getCurrentRecordingJourneyLog() {
        if(recJourneyF != null) {
            return recJourneyF;
        }

        return null;
    }

    public void stopRecordingLogs() {

        Log.d(TAG, "stopRecordingLogs()");

        try {
            // Flush and close current log if any
            if (recJourneyFw != null) {
                recJourneyFw.flush();
                recJourneyFw.close();
                recJourneyFw = null;
            }
        }
        catch (IOException ex) {
            Log.e(TAG, "stopRecordingLogs()::could not close log files");
        } finally {
            recJourneyF = null;
        }
    }

    public void writeRecordingJourneyLog(String msg) {
        if (recJourneyFw != null) {
            try {
                recJourneyFw.write(msg + System.lineSeparator());
                recJourneyFw.flush();
            }
            catch (IOException ex) {
                Log.e(TAG, "writeRecordingJourneyLog()::" + ex.getMessage());
            }
        }
    }
}