package io.kasava.utilities;

import android.content.Context;
import android.os.StatFs;
import android.text.format.DateFormat;
import android.util.Log;

import org.apache.commons.io.FilenameUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.kasava.data.Clip;
import io.kasava.data.ClipFile;
import io.kasava.data.Constants;
import io.kasava.data.Model;

public class Storage {

    private static String TAG = "Storage";

    private Utilities utilities;

    private static String mEncyptionPw = "kq9dx41z084jtuw6";
    private static Model.TYPE mModelType;

    public Storage(Context context, Model.TYPE modelType) {
        utilities = new Utilities(context);
        mModelType = modelType;
    }

    /******************** eMMC *********************/

    public String getEmmcRootDir() {
        File dir = new File("/storage/sdcard0/");
        return dir.getAbsolutePath();
    }

    public long getEmmcFreeStorageMb() {
        File emmcPath = new File("/storage/sdcard0/");
        StatFs statEmmc = new StatFs(emmcPath.getPath());
        long emmcFree = (statEmmc.getBlockSizeLong() * statEmmc.getFreeBlocksLong()) / 1048576;

        Log.d(TAG, "Free eMMC memory: " + emmcFree);
        return emmcFree;
    }

    private boolean isEmmcFree() {
        int minimumFreeMB = 300;

        if(mModelType == Model.TYPE.KXB) {
            minimumFreeMB = 600;
        }

        return (getEmmcFreeStorageMb() > 300);
    }

    /******************** SD Card *********************/

    public String getSdRootDir() {
        File dir = new File("/storage/sdcard1/");
        return dir.getAbsolutePath();
    }

    public long getSdTotalStorageMb() {
        long sdTotalMb = 0;
        File sdPath = new File("/storage/sdcard1/");

        if(sdPath.exists()) {
            StatFs statSd = new StatFs(sdPath.getPath());
            sdTotalMb = (statSd.getBlockSizeLong() * statSd.getBlockCountLong()) / 1048576;
        }

        Log.d(TAG, "Total SD memory: " + sdTotalMb);
        return sdTotalMb;

    }

    public long getSdFreeStorageMb() {

        try {
            File sDpath = new File("/storage/sdcard1/");
            if (sDpath.exists()) {
                StatFs statSd = new StatFs(sDpath.getPath());
                long sdFree = (statSd.getBlockSizeLong() * statSd.getFreeBlocksLong()) / 1048576;

                Log.d(TAG, "Free SD memory: " + sdFree);
                return sdFree;
            }
        } catch (IllegalArgumentException ex) {

        }

        return 0;
    }

    public boolean isSdPresent() {
        return (getSdTotalStorageMb() > 1000);
    }

    private boolean isSdFree() {
        return (getSdFreeStorageMb() > 500);
    }

    public boolean sdTestWrite() {
        boolean successWrite = false;
        boolean successFileCheck = true;
        File file = new File(getSdRootDir(), "test.txt");
        final String testPhrase = "Kasava Test";

        try {
            FileWriter fileWriter = new FileWriter(file, true);
            fileWriter.write(testPhrase);
            fileWriter.flush();
            fileWriter.close();
        }
        catch (IOException ex) {
            Log.e(TAG, "sdTestWrite()::could not create test file - " + ex.getMessage());
        }

        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;

            while ((line = br.readLine()) != null) {
                if(line.equals(testPhrase)) {
                    successWrite = true;
                }
            }
            br.close();
        }
        catch (IOException e) {
            //You'll need to add proper error handling here
        }

        if(file.exists()) {
            if(!file.delete()) {
                successWrite = false;
            }
        }

        File[] recordings = new File(getSdRootDir(), Constants.FOLDER_RECORDINGS).listFiles();

        if(recordings == null || (recordings.length < 30 && getSdFreeStorageMb() < 1000)) {
            successFileCheck = false;
        }

        return successWrite && successFileCheck;
    }

    /******************** Dirs *********************/

    public void createKasavaDirs() {
        File dirEmmcRecording = new File(getEmmcRootDir(), Constants.FOLDER_RECORDINGS);
        File dirEmmcEvents = new File(getEmmcRootDir(), Constants.FOLDER_EVENTS);
        File dirEmmcClips = new File(getEmmcRootDir(), Constants.FOLDER_CLIPS);
        File dirSdRecording = new File(getSdRootDir(), Constants.FOLDER_RECORDINGS);
        File dirSdClips = new File(getSdRootDir(), Constants.FOLDER_CLIPS);
        File dirLv = new File(Constants.FOLDER_LIVEVIEW);

        if (!dirEmmcRecording.exists() && !dirEmmcRecording.mkdirs()) {
            Log.e(TAG, "Failed to create eMMC recording folder");
        }

        if (!dirEmmcEvents.exists() && !dirEmmcEvents.mkdirs()) {
            Log.e(TAG, "Failed to create eMMC events folder");
        }

        if (!dirEmmcClips.exists() && !dirEmmcClips.mkdirs()) {
            Log.e(TAG, "Failed to create eMMC clips folder");
        }

        if (isSdPresent() && !dirSdRecording.exists() && !dirSdRecording.mkdirs()) {
            Log.e(TAG, "Failed to create SD recordings folder");
        }

        if (isSdPresent() && !dirSdClips.exists() && !dirSdClips.mkdirs()) {
            Log.e(TAG, "Failed to create SD clips folder");
        }

        if (!dirLv.exists() && !dirLv.mkdirs()) {
            Log.e(TAG, "Failed to create lv folder");
        }
    }

    public File createNewRecordingDir() {
        File dir = new File(getEmmcRootDir(), Constants.FOLDER_RECORDINGS + File.separator + utilities.dateAsUtcDateTimeString(Calendar.getInstance().getTime()));

        if (!dir.exists()){
            if (!dir.mkdirs()) {
                Log.d(TAG, "Failed to create continuous recording directory");
            } else {
                Log.d(TAG, "Created continuous recording directory::" + dir.getPath());
                return dir;
            }
        }

        return null;
    }

    public File createNewClipEmmcDir(Clip clip) {

        String eventOrClipFolder = Constants.FOLDER_CLIPS;
        if(clip.isEvent) {
            eventOrClipFolder = Constants.FOLDER_EVENTS;
        }

        File dir = new File(getEmmcRootDir(), eventOrClipFolder +
                File.separator + clip.startDateTime + "_" + clip.durationS);

        if (!dir.exists()){
            if (!dir.mkdirs()) {
                Log.d(TAG, "Failed to create eMMC clip recording directory");
            } else {
                Log.d(TAG, "Created eMMC clip recording directory::" + dir.getPath());
                return dir;
            }
        } else {
            deleteDirOrFile(dir);

            if (!dir.mkdirs()) {
                Log.d(TAG, "Failed to create eMMC clip recording directory");
            } else {
                Log.d(TAG, "Created eMMC clip recording directory::" + dir.getPath());
                return dir;
            }
        }

        return null;
    }

    /******************** Recording *******************/

    public File getCurrentRecordingFile(File dir, int id) {

        File currentRecordingFile = null;

        if(dir != null) {
            // Get list of recording files in dir
            File[] recFiles = dir.listFiles();
            Arrays.sort(recFiles);

            // Find folder containing required footage
            for (File recFile : recFiles) {
                if (id == 0 && recFile.getName().contains("journey")) {
                    currentRecordingFile = recFile;
                } else if (recFile.getName().contains("cam" + id)) {
                    currentRecordingFile = recFile;
                }
            }
        }

        Log.d(TAG, "getCurrentRecordingFile()::" + currentRecordingFile);

        return currentRecordingFile;
    }


    public Date getOldestRecordDate() {
        File sdPath = new File("/storage/sdcard0/");
        if(isSdPresent()){
            sdPath = new File("/storage/sdcard1/");
        }
        File[] files = sdPath.listFiles();
        Date nowDate = new Date();
        long nowTime = nowDate.getTime();
        long oldestRecord = files[0].lastModified();
        for (int i = 1; i < files.length; ++i) {
            if(nowTime-files[i].lastModified() > oldestRecord){
                oldestRecord = files[i].lastModified();
            }
        }
        Log.d(TAG, "Oldest record: " + oldestRecord);
        Date oldestDate = new Date((long)oldestRecord*1000);

        return oldestDate;
    }

    private String getDate(long time) {
        Calendar cal = Calendar.getInstance(Locale.ENGLISH);
        cal.setTimeInMillis(time * 1000);
        String date = DateFormat.format("dd-MM-yyyy", cal).toString();
        return date;
    }




    /******************** Cleanup *********************/

    public void cleanupEmmc() {
        Log.d(TAG, "cleanupEmmc()");
        deleteDirOrFile(new File(getEmmcRootDir(), "log_dbg"));
        deleteDirOrFile(new File(getEmmcRootDir(), "kasava/journeys"));
        deleteDirOrFile(new File(getEmmcRootDir(), "clips"));
        deleteEventsClips(new File(getEmmcRootDir(), Constants.FOLDER_EVENTS), 10);
        deleteEventsClips(new File(getEmmcRootDir(), Constants.FOLDER_CLIPS), 3);
        deleteRecordings(new File(getEmmcRootDir(), Constants.FOLDER_RECORDINGS), true);
    }

    public boolean cleanupSd() {
        Log.d(TAG, "cleanupSd()");

        boolean success = true;

        // Delete any non Kasava files & folders at SD root level
        File sdRootDir = new File(getSdRootDir());
        File[] sdRootFiles = sdRootDir.listFiles();
        if (sdRootFiles != null) {
            for (File sdRootFile : sdRootFiles) {
                if(!sdRootFile.getName().contains("kasava")) {
                    deleteDirOrFile(sdRootFile);
                }
            }
        }

        // Delete any non Kasava files & folders within the kasava dir
        File sdKasavaDir = new File(getSdRootDir(), "kasava");
        File[] sdKasavaFiles = sdKasavaDir.listFiles();
        if (sdKasavaFiles != null) {
            for (File sdKasavaFile : sdKasavaFiles) {
                if(!sdKasavaFile.getName().contains("clips") && !sdKasavaFile.getName().contains("recordings")) {
                    deleteDirOrFile(sdKasavaFile);
                }
            }
        }

        if(!deleteRecordings(new File(getSdRootDir(), Constants.FOLDER_RECORDINGS), false)) {
            success = false;
        }

        if(!deleteEventsClips(new File(getSdRootDir(), Constants.FOLDER_CLIPS), 50)) {
            success = false;
        }

        return success;
    }


    private boolean deleteRecordings(File recordingDir, boolean emmc) {
        Log.d(TAG, "deleteRecordings()::" + recordingDir.getAbsolutePath());

        File[] recordings;
        boolean success = true;
        boolean isStorageFree;
        int index = 0;

        try {
            recordings = recordingDir.listFiles();

            if(emmc) {
                isStorageFree = isEmmcFree();
            } else {
                isStorageFree = isSdFree();
            }

            while (recordings != null && recordings.length > index && !isStorageFree) {

                // Sort into reverse dateTime order (oldest 1st)
                Arrays.sort(recordings);

                File[] filesInDir = recordings[index].listFiles();

                // Delete if not a dir or dir is empty
                if (!recordings[0].isDirectory() || filesInDir == null || filesInDir.length == 0) {
                    if (!deleteDirOrFile(recordings[index])) {
                        // If a file or folder fails to delete
                        Log.d(TAG, "deleteRecordings()::Failed to delete: " + recordings[0].getName());
                        success = false;
                    }
                } else {
                    // Delete oldest file in dir
                    if (!deleteOldestFileInDir(recordings[index])) {
                        // If a file or folder fails to delete
                        Log.d(TAG, "deleteRecordings()::Failed to delete oldest file in: " + recordings[0].getName());
                        success = false;
                    }
                }

                // Skip to next dir if fails
                if (success = false) {
                    index++;
                    success = true;
                }

                recordings = recordingDir.listFiles();

                if (emmc) {
                    isStorageFree = isEmmcFree();
                } else {
                    isStorageFree = isSdFree();
                }
            }

        } catch (Exception ex) {
            Log.e(TAG, "deleteRecordings()::Failed: " + ex.getMessage());
        }

        return success;
    }

    private boolean deleteEventsClips(File clipsDir, final int MAX_CLIPS) {
        Log.d(TAG, "deleteEventsClips()::" + clipsDir.getAbsolutePath());

        boolean success = true;
        File[] clips;

        try {
            clips = clipsDir.listFiles();

            if(clips != null && clips.length > MAX_CLIPS) {
                // Sort into last modified order
                Arrays.sort(clips, (f1, f2) -> Long.compare(f1.lastModified(), f2.lastModified()));

                for(int i = 0; i<(clips.length - MAX_CLIPS); i++) {
                    Log.d(TAG, "deleteClips()::deleting: " + clips[i].getName());

                    if(!deleteDirOrFile(clips[i])) {
                        // If a file or folder fails to delete
                        Log.d(TAG, "deleteEventsClips()::Failed to delete: " + clips[i].getName());
                        success = false;
                        break;
                    }
                }
            }
        } catch (Exception ex) {
            Log.e(TAG, "deleteEventsClips()::Failed: " + ex.getMessage());
        }

        return success;
    }

    public void deleteAllDecrypted() {
        Log.d(TAG, "deleteAllDecrypted()");

        try {

            File sdRecordingDir = new File(getSdRootDir(), Constants.FOLDER_RECORDINGS);
            File[] recordings = sdRecordingDir.listFiles();

            for (File recording : recordings) {
                if (recording.getName().contains("_decrypt")) {
                    Log.d(TAG, "deleteAllDecrypted()::deleting: " + recording.getName());
                    deleteDirOrFile(recording);
                }
            }
        } catch (Exception ex) {
            Log.e(TAG, "deleteAllDecrypted()::Failed: " + ex.getMessage());
        }
    }

    private boolean deleteOldestFileInDir(File fileOrDirectory) {
        Log.d(TAG, "deleteDirOrFile()::" + fileOrDirectory.getPath());

        Date oldestDateTime = null;
        File fileToDelete = null;

        if (fileOrDirectory.isDirectory()) {
            for (File file : fileOrDirectory.listFiles()) {
                Date fileDateTime = utilities.getDateFromRecordingFile(file);
                if(fileDateTime == null || oldestDateTime == null || fileDateTime.before(oldestDateTime)) {
                    fileToDelete = file;
                    oldestDateTime = fileDateTime;
                }
            }

            if(fileToDelete != null && deleteDirOrFile(fileToDelete)) {
                return true;
            } else {
                return false;
            }
        }
        return false;
    }

    private boolean deleteDirOrFile(File fileOrDirectory) {
        Log.d(TAG, "deleteDirOrFile()::" + fileOrDirectory.getPath());

        if (fileOrDirectory.isDirectory()) {
            for (File child : fileOrDirectory.listFiles()) {
                if(child != null) {
                    deleteDirOrFile(child);
                }
            }
        }
        return fileOrDirectory.delete();
    }


    /******************** SD syncing *********************/

    public void setEncryptionPw(String pw) {
        if(pw.length() >= 8) {
            mEncyptionPw = pw + pw;
            mEncyptionPw.substring(0, 16); //PW must be 16 characters
        }
    }

    public void syncRecordingsToSd(File currentRecordingDir) {
        Log.d(TAG, "syncRecordingsToSd()");

        if (!isSdPresent()) {
            Log.d(TAG, "syncRecordingsToSd()::No SD card");
            return;
        }

        File emmcRecordingDir = new File(getEmmcRootDir(), Constants.FOLDER_RECORDINGS);
        File[] emmcRecordingDirs = emmcRecordingDir.listFiles();

        File sdRecordingDir = new File(getSdRootDir(), Constants.FOLDER_RECORDINGS);
        File[] sdRecordingDirs = sdRecordingDir.listFiles();

        if (emmcRecordingDirs != null) {
            for (File emmcRecording : emmcRecordingDirs) {
                File sdRecordingToSync = null;

                // Create the directory on SD card if required
                boolean dirCreated = false;
                for (File sdRecording : sdRecordingDirs) {
                    if (emmcRecording.getName().equals(sdRecording.getName())) {
                        dirCreated = true;
                        sdRecordingToSync = sdRecording;
                    }
                }

                if (!dirCreated) {
                    sdRecordingToSync = new File(sdRecordingDir.getAbsolutePath() + File.separator + emmcRecording.getName());
                    if (!sdRecordingToSync.mkdirs()) {
                        Log.e(TAG, "Failed to create SD recording folder");
                        continue;
                    }
                }

                // Sync the files from eMMC directory if they are not being used
                File[] emmcRecordingFiles = emmcRecording.listFiles();
                File[] sdRecordingFiles = sdRecordingToSync.listFiles();
                for (File emmcRecordingFile : emmcRecordingFiles) {
                    boolean fileSynced = false;
                    for (File sdRecordingFile : sdRecordingFiles) {
                        if (sdRecordingFile.getName().equals(emmcRecordingFile.getName() + ".kas")) {
                            fileSynced = true;
                        }
                    }

                    if (!fileSynced) {
                        boolean syncFile = true;

                        // Check if this file should be ignored (i.e. it's being recorded to at the moment)
                        List<File> filesToIgnore = new ArrayList<>();
                        filesToIgnore.add(getCurrentRecordingFile(currentRecordingDir, 0));
                        filesToIgnore.add(getCurrentRecordingFile(currentRecordingDir, 1));
                        filesToIgnore.add(getCurrentRecordingFile(currentRecordingDir, 2));
                        filesToIgnore.add(getCurrentRecordingFile(currentRecordingDir, 3));

                        for (File fileToIgnore : filesToIgnore) {
                            if (fileToIgnore != null && emmcRecordingFile.getAbsolutePath().equals(fileToIgnore.getAbsolutePath())) {
                                syncFile = false;
                            }
                        }

                        if (syncFile) {
                            // Free up SD card space before every file sync
                            cleanupSd();

                            File encryptedFile = new File(sdRecordingToSync.getAbsolutePath() + File.separator +
                                    emmcRecordingFile.getName() + Constants.ENCRYTED_EXTENSION + "_tmp");
                            Crypto.encrypt(mEncyptionPw, emmcRecordingFile, encryptedFile);

                            // Remove the _tmp tag was file has synced
                            File sdRecordingFile = new File(encryptedFile.getAbsolutePath().replace("_tmp", ""));
                            encryptedFile.renameTo(sdRecordingFile);
                        }
                    }
                }
            }
        }

        cleanupSd();
        Log.d(TAG, "syncRecordingsToSd()::Sync finished");
    }

    public void syncRecordingsToSd2() {
        Log.d(TAG, "syncRecordingsToSd()");

        if (!isSdPresent()) {
            Log.d(TAG, "syncRecordingsToSd()::No SD card");
            return;
        }

        File emmcRecordingDir = new File(getEmmcRootDir(), Constants.FOLDER_RECORDINGS);
        File[] emmcRecordings = emmcRecordingDir.listFiles();
        Arrays.sort(emmcRecordings, (a,b) -> b.getName().compareTo(a.getName()));

        File sdRecordingDir = new File(getSdRootDir(), Constants.FOLDER_RECORDINGS);
        File[] sdRecordings = sdRecordingDir.listFiles();

        if (emmcRecordings != null && emmcRecordings.length > 1) {
            for(int i = 1; i < emmcRecordings.length; i++) {
                boolean dirIsSynced = false;

                if (sdRecordings != null && sdRecordings.length > 0) {
                    for (File sdRecordingFolder : sdRecordings) {
                        if (emmcRecordings[i].getName().equals(sdRecordingFolder.getName())) {
                            dirIsSynced = true;
                        }
                    }
                }

                if (!dirIsSynced) {
                    Log.d(TAG, "syncRecordingsToSd()::Syncing: " + emmcRecordings[i].getAbsolutePath());

                    File[] emmcRecordingFiles = emmcRecordings[i].listFiles();

                    File sdRecordingFolderTmp = new File(sdRecordingDir.getAbsolutePath() + File.separator + emmcRecordings[i].getName() + "_tmp");
                    if (!sdRecordingFolderTmp.mkdirs()) {
                        Log.e(TAG, "Failed to create SD recording folder");
                    }

                    if(emmcRecordingFiles != null && emmcRecordingFiles.length > 0) {
                        for (File emmcRecordingFile : emmcRecordingFiles) {
                            File encryptedFile = new File(sdRecordingFolderTmp.getAbsolutePath() + File.separator +
                                    emmcRecordingFile.getName() + Constants.ENCRYTED_EXTENSION);
                            Crypto.encrypt(mEncyptionPw, emmcRecordingFile, encryptedFile);
                        }
                    }

                    File sdRecordingFolder = new File(sdRecordingFolderTmp.getAbsolutePath().replace("_tmp", ""));
                    sdRecordingFolderTmp.renameTo(sdRecordingFolder);
                    Log.d(TAG, "syncRecordingsToSd()::Sync finished: " + emmcRecordings[i].getAbsolutePath());

                    cleanupSd();
                }
            }
        }
    }

    public File createNewClipDecyptDir2(File sourceDir) {
        Log.d(TAG, "createNewClipDecyptDir():");

        File decryptedDir = new File(sourceDir.getAbsolutePath() + "_decrypt");

        if (!decryptedDir.exists()) {
            if (!decryptedDir.mkdirs()) {
                Log.e(TAG, "createNewClipDecyptDir()::Failed to create dir");
                return null;
            } else {
                Log.d(TAG, "createNewClipDecyptDir()::Created dir: " + decryptedDir.getPath());

                File[] recordingFiles = sourceDir.listFiles();

                for (File recordingFile : recordingFiles) {
                    File decryptedFile = new File(decryptedDir.getAbsolutePath() + File.separator +
                            FilenameUtils.removeExtension(recordingFile.getName()));
                    Crypto.decrypt(mEncyptionPw, recordingFile, decryptedFile);
                }
            }
        }

        return decryptedDir;
    }

    public List<ClipFile> createNewClipDecyptDir(File sourceDir, List<ClipFile> fileList) {
        Log.d(TAG, "createNewClipDecyptDir():");

        List<ClipFile> clipFileList = new ArrayList<>();
        File decryptedDir = new File(sourceDir.getAbsolutePath() + "_decrypt");

        if (!decryptedDir.exists()) {
            if (!decryptedDir.mkdirs()) {
                Log.e(TAG, "createNewClipDecyptDir()::Failed to create dir");
                return null;
            } else {
                Log.d(TAG, "createNewClipDecyptDir()::Created dir: " + decryptedDir.getPath());

                File[] recordingFiles = sourceDir.listFiles();

                for (File recordingFile : recordingFiles) {
                    for(ClipFile file : fileList) {
                        if(file.file.getAbsolutePath().equals(recordingFile.getAbsolutePath())) {
                            ClipFile newClipFile = new ClipFile();
                            newClipFile.file = new File(decryptedDir.getAbsolutePath() + File.separator +
                                    FilenameUtils.removeExtension(recordingFile.getName()));
                            newClipFile.startDateTime = file.startDateTime;
                            newClipFile.endDateTime = file.endDateTime;

                            Crypto.decrypt(mEncyptionPw, recordingFile, newClipFile.file);

                            clipFileList.add(newClipFile);
                        }
                    }
                }
            }
        }

        return clipFileList;
    }


    public File getRecordingDir(Date date) {
        Log.d(TAG, "getRecordingDir()::" + date);

        File emmcRecDir = new File(getEmmcRootDir(), Constants.FOLDER_RECORDINGS);
        File recordingDir = null;

        // Get list of recording folders on eMMC
        File[] emmcRecFiles = emmcRecDir.listFiles();
        if (emmcRecFiles != null) {
            // Sort into most recent modified order
            Arrays.sort(emmcRecFiles, Collections.reverseOrder());

            // Find folder containing required footage
            for (File emmcRecFile : emmcRecFiles) {
                if (utilities.recordingDirToLocalDateTime(emmcRecFile).compareTo(date) < 0) {
                    recordingDir = emmcRecFile;
                    break;
                }
            }
        }

        // Find dir on SD if not on eMMC
        if(recordingDir == null) {
            File sdRecDir = new File(getSdRootDir(), Constants.FOLDER_RECORDINGS);

            // Get list of recording folders on eMMC
            File[] sdRecFiles = sdRecDir.listFiles();
            if (sdRecFiles != null) {
                // Sort into most recent modified order
                Arrays.sort(sdRecFiles, Collections.reverseOrder());

                // Find folder containing required footage
                for (File sdRecFile :sdRecFiles) {
                    if (utilities.recordingDirToLocalDateTime(sdRecFile).compareTo(date) < 0) {
                        recordingDir = sdRecFile;
                        break;
                    }
                }
            }
        }

        return recordingDir;
    }
}
