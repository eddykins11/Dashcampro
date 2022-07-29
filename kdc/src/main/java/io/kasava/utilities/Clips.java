package io.kasava.utilities;

import android.content.Context;
import android.util.Log;

import com.bugsnag.android.Bugsnag;

import org.mp4parser.Container;
import org.mp4parser.muxer.Movie;
import org.mp4parser.muxer.Track;
import org.mp4parser.muxer.builder.DefaultMp4Builder;
import org.mp4parser.muxer.container.mp4.MovieCreator;
import org.mp4parser.muxer.tracks.AppendTrack;
import org.mp4parser.muxer.tracks.ClippedTrack;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;

import io.kasava.data.Clip;
import io.kasava.data.ClipFile;
import io.kasava.data.Constants;

public class Clips {

    private static String TAG = "Clips";

    private Storage storage;
    private Utilities utilities;

    private Context mContext;
    public Clips(Context context) {
        mContext = context;

        utilities = new Utilities(mContext);
        utilities.setModel();
        storage = new Storage(mContext, utilities.getModelType());
    }

    public enum CLIP_REQUEST {
        ERROR,
        WAIT,
        NO_FOOTAGE,
        PROCEED
    }

    /******************** Custom Clips *********************/

    public CLIP_REQUEST requestToCreateClipFiles(final Clip clip, File currentRecordingDir) {
        Log.d(TAG, "requestToCreateClipFiles()::" + clip.startDateTime + ", " + clip.durationS + "s, currentDir: " + currentRecordingDir);

        Date startDateTime = utilities.utcDateTimeStringToDate(clip.startDateTime);

        Bugsnag.leaveBreadcrumb("startDateTimeStr=" + clip.startDateTime);
        Bugsnag.leaveBreadcrumb("startDateTime=" + startDateTime);

        if(startDateTime == null) {
            //return CLIP_REQUEST.ERROR;
        }

        // Get the end dateTime
        Calendar cal = Calendar.getInstance();
        cal.setTime(startDateTime);
        cal.add(Calendar.SECOND, clip.durationS);
        Date endDateTime = cal.getTime();

        // Get the input dir
        File inputDir = getRecordingDir(startDateTime);

        Bugsnag.clearBreadcrumbs();

        // If we have no recordings we have to delete the clip request from queue
        if (inputDir == null) {
            Log.e(TAG, "requestToCreateClipFiles()::Clip can't be created for requested time");
            return CLIP_REQUEST.NO_FOOTAGE;
        }

        // If there's not enough free storage wait until there is
        if (storage.getEmmcFreeStorageMb() < 250) {
            Log.e(TAG, "requestToCreateClipFiles()::Internal storage is too low so let's wait");
            return CLIP_REQUEST.WAIT;
        }

        // If the required recording is from current recording files then we wait until it's finished
        if (currentRecordingDir != null && inputDir.getAbsolutePath().equals(currentRecordingDir.getAbsolutePath())) {
            File currentCam1File = storage.getCurrentRecordingFile(currentRecordingDir, 1);
            File currentCam2File = storage.getCurrentRecordingFile(currentRecordingDir, 2);
            File currentCam3File = storage.getCurrentRecordingFile(currentRecordingDir, 3);

            if ((currentCam1File != null && endDateTime.after(utilities.getDateFromRecordingFile(currentCam1File))) ||
                    (currentCam2File != null && endDateTime.after(utilities.getDateFromRecordingFile(currentCam2File))) ||
                    (currentCam3File != null && endDateTime.after(utilities.getDateFromRecordingFile(currentCam3File)))) {
                Log.e(TAG, "requestToCreateClipFiles()::Clip is in current recording so let's wait");
                return CLIP_REQUEST.WAIT;
            }
        }

        Log.d(TAG, "requestToCreateClipFiles()::Can proceed to make clip");

        return CLIP_REQUEST.PROCEED;
    }

    public File createClipFiles(final Clip clip) {
        Log.d(TAG, "createClipFiles()::" + clip.startDateTime + ", " + clip.durationS + "s");

        Date startDateTime = utilities.utcDateTimeStringToDate(clip.startDateTime);

        // Delete any old decrypted files that shouldn't exist
        storage.deleteAllDecrypted();

        // Get the input dir
        File inputDir = getRecordingDir(startDateTime);

        // Get the endDateTime of this recording
        Date journeyEndDateTime = utilities.getRecordingEndDateTimeFromJourney(inputDir);

        // Get the endDateTime requested
        Calendar cal = Calendar.getInstance();
        cal.setTime(startDateTime);
        cal.add(Calendar.SECOND, clip.durationS);
        Date endDateTime = cal.getTime();

        // Adjust endDateTime if request exceeds recording available
        if(journeyEndDateTime != null && journeyEndDateTime.before(endDateTime)) {
            endDateTime = journeyEndDateTime;
            clip.durationS = (int)(endDateTime.getTime() - startDateTime.getTime()) / 1000;
            if(clip.durationS < 0) { clip.durationS = 0; }
            Log.d(TAG, "createClipFiles()::Adjusted:" + clip.startDateTime + ", " + clip.durationS + "s");
        }

        // Set the output dir
        File outputDir = storage.createNewClipEmmcDir(clip);
        if(outputDir == null) {
            Log.e(TAG, "createClipFiles()::Clip already created");
            return null;
        }

        Log.d(TAG, "Input dir: " + inputDir.getAbsolutePath());

        // Find the files needed to create requested clip
        List<ClipFile> clipFileList = getRecordingFileList(inputDir, startDateTime, endDateTime);

        // If input dir is on SD then we need to decrypt
        if(inputDir.getAbsolutePath().contains("sdcard1")) {
            Log.d(TAG, "createClipFiles()::Decrypting: " + inputDir.getAbsolutePath());
            clipFileList = storage.createNewClipDecyptDir(inputDir, clipFileList);
            Log.d(TAG, "createClipFiles()::Decrypted: " + inputDir.getAbsolutePath());
        }

        // Create the journey file
        createClipJourney(clipFileList, startDateTime, endDateTime, outputDir);

        // Just upload the empty journey file so portal knows there is no recording
        if(clip.durationS < 1) {
            Log.e(TAG, "createClipFiles()::Clip already created");
            return outputDir;
        }

        // Create video files
        createClipVideo(clipFileList, 1, startDateTime, endDateTime, outputDir);
        createClipVideo(clipFileList, 2, startDateTime, endDateTime, outputDir);
        createClipVideo(clipFileList, 3, startDateTime, endDateTime, outputDir);

        // Delete any decrypted folders
        //storage.deleteAllDecrypted();

        return outputDir;
    }


    /******************** Journey *********************/

    private void createClipJourney(final List<ClipFile> clipFileList, final Date startDateTime, final Date endDateTime, File outputDir) {
        Log.d(TAG, "createClipJourney()");

        try {
            Log.d(TAG, "createClipFiles()::Creating clip journey file...");

            StringBuilder journeyLogs = new StringBuilder();

            for(ClipFile clipFile : clipFileList) {
                if(clipFile.file.getName().contains("journey")) {
                    journeyLogs.append(getJourneyLogs(clipFile.file, startDateTime, endDateTime));
                }
            }

            File journeyF = new File(outputDir, Constants.JOURNEY_FILENAME + Constants.CSV_EXTENSION);
            FileWriter journeyFw = new FileWriter(journeyF, true);

            journeyFw.write(Constants.JOURNEY_HEADER + System.lineSeparator());
            journeyFw.write(journeyLogs.toString());

            journeyFw.flush();
            journeyFw.close();

        } catch (IOException ex) {
            Log.e(TAG, "createClipJourney()::File writer error: " + ex.getMessage());
        }
    }

    private String getJourneyLogs(final File journeyFile, final Date startDateTime, final Date endDateTime) {
        Log.d(TAG, "getJourneyLogs()::" + journeyFile.getAbsolutePath());

        StringBuilder journeyLogs = new StringBuilder();

        try {
            FileInputStream inputStream = new FileInputStream(journeyFile);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String journeyReadLine = reader.readLine();

            while (journeyReadLine != null) {
                if (isJourneyLogValid(journeyReadLine, startDateTime, endDateTime)) {
                    journeyLogs.append(journeyReadLine).append(System.lineSeparator());
                }

                journeyReadLine = reader.readLine();
            }

        } catch (FileNotFoundException ex) {
            Log.e(TAG, "getJourneyLogs()::File not found: " + ex.getMessage());
        } catch (IOException ex) {
            Log.e(TAG, "getJourneyLogs()::Error reading file: " + ex.getMessage());
        }

        return journeyLogs.toString();
    }

    private boolean isJourneyLogValid(final String log, final Date startDateTime, final Date endDateTime) {

        if(log.startsWith("Date")) {
            return false;
        }

        try {
            String[] logParts = log.split(":");

            SimpleDateFormat sdf = new SimpleDateFormat(Constants.DATETIME_FORMAT_LONG);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date lineDateTime = sdf.parse(logParts[0]);

            if(lineDateTime.after(startDateTime) && lineDateTime.before(endDateTime)) {
                return true;
            }
        }  catch (ParseException ex) {
            Log.e(TAG, "isLineTimestampValid()::Could not parse date from folder: " + ex.getMessage());
        }

        return false;
    }


    /******************** Camera Files *********************/

    private File getRecordingDir(Date startDateTime) {
        Log.d(TAG, "getRecordingDir()::" + startDateTime);

        File emmcRecDir = new File(storage.getEmmcRootDir(), Constants.FOLDER_RECORDINGS);
        File recordingDir = null;
        boolean useEmmc = false;

        // Get list of recording folders on eMMC
        File[] emmcRecFiles = emmcRecDir.listFiles();
        if (emmcRecFiles != null) {
            // Sort into most recent modified order
            Arrays.sort(emmcRecFiles, Collections.reverseOrder());

            // Find folder containing required footage
            for (File emmcRecFile : emmcRecFiles) {
                if (utilities.recordingDirToLocalDateTime(emmcRecFile).compareTo(startDateTime) < 0) {
                    recordingDir = emmcRecFile;
                    useEmmc = true;
                    break;
                }
            }
        }

        // If recording is found on eMMC check it contains the full recording
        if(useEmmc) {
            Log.d(TAG, "getRecordingDir()::useEmmc: " + recordingDir);

            boolean foundJourneyStart = false;
            boolean foundCam1Start = false;
            boolean foundCam2 = false;
            boolean foundCam2Start = false;
            boolean foundCam3 = false;
            boolean foundCam3Start = false;

            // Get list of recording files in dir
            File[] recFiles = recordingDir.listFiles();
            Arrays.sort(recFiles);

            // Find required footage files
            for (File recFile : recFiles) {
                Date fileStartDateTime = utilities.getDateFromRecordingFile(recFile);
                Date fileEndDateTime = utilities.getEndDateTimeOfFile(recFile);

                if(recFile.getName().contains("cam2"))  { foundCam2 = true; }
                else if(recFile.getName().contains("cam3"))  { foundCam3 = true; }

                if (fileStartDateTime != null && fileEndDateTime != null && (startDateTime.after(fileStartDateTime) && startDateTime.before(fileEndDateTime))) { // Finds the start file
                    if(recFile.getName().contains("journey"))  { foundJourneyStart = true; }
                    else if(recFile.getName().contains("cam1"))  { foundCam1Start = true; }
                    else if(recFile.getName().contains("cam2"))  { foundCam2Start = true; }
                    else if(recFile.getName().contains("cam3"))  { foundCam3Start = true; }
                }
            }

            // Check we have all the files available on eMMC
            if(!foundJourneyStart) { useEmmc = false; }
            else if(!foundCam1Start) { useEmmc = false; }
            else if(foundCam2 && !foundCam2Start) { useEmmc = false; }
            else if(foundCam3 && !foundCam3Start) { useEmmc = false; }
        }

        // Find dir on SD if not on eMMC
        if(storage.isSdPresent() && !useEmmc) {
            File sdRecDir = new File(storage.getSdRootDir(), Constants.FOLDER_RECORDINGS);

            // Get list of recording folders on eMMC
            File[] sdRecFiles = sdRecDir.listFiles();
            if (sdRecFiles != null) {
                // Sort into most recent modified order
                Arrays.sort(sdRecFiles, Collections.reverseOrder());

                // Find folder containing required footage
                for (File sdRecFile :sdRecFiles) {
                    if (utilities.recordingDirToLocalDateTime(sdRecFile).compareTo(startDateTime) < 0) {
                        recordingDir = sdRecFile;
                        break;
                    }
                }
            }
        }

        return recordingDir;
    }


    private List<ClipFile> getRecordingFileList(File dir, Date startDateTime, Date endDateTime) {
        Log.d(TAG, "getRecordingFileList()::start:" + startDateTime + ", end:" + endDateTime);
        List<ClipFile> clipFiles = new ArrayList<>();

        // Get list of recording files in dir
        File[] recFiles = dir.listFiles();
        Arrays.sort(recFiles);

        // Find required  containing required footage
        for (File recFile : recFiles) {
            Date fileStartDateTime = utilities.getDateFromRecordingFile(recFile);
            Date fileEndDateTime = utilities.getEndDateTimeOfFile(recFile);

            Log.d(TAG, "getRecordingFileList()::recFile=" + recFile.getName() + ", fileStartDateTime=" + fileStartDateTime + ", fileEndDateTime=" + fileEndDateTime);

            if ((startDateTime.after(fileStartDateTime) && startDateTime.before(fileEndDateTime)) || // Finds the start file
                    (fileStartDateTime.after(startDateTime) && fileEndDateTime.before(endDateTime)) || // Find the middle file(s)
                    (endDateTime.after(fileStartDateTime) && (endDateTime.before(fileEndDateTime) || endDateTime.equals(fileEndDateTime))) ) { // Find the end file
                ClipFile clipFile = new ClipFile();
                clipFile.file = recFile;
                clipFile.startDateTime = fileStartDateTime;
                clipFile.endDateTime = fileEndDateTime;
                clipFiles.add(clipFile);
            }
        }

        for (ClipFile clipFile : clipFiles) {
            Log.d(TAG, "Clip file to use: " + clipFile.file.getName());
        }

        return clipFiles;
    }


    /******************** Cameras *********************/

    public void createClipVideo(final List<ClipFile> clipFileList, final int camId, final Date startDateTime, final Date endDateTime, File outputDir) {
        Log.d(TAG, "createClipVideo()::" + camId);
        List<ClipFile> camFileList = new ArrayList<>();

        boolean isMp4 = true;

        // Find required camera files
        for (ClipFile clipFile : clipFileList) {
            if (clipFile.file.getName().contains("cam" + camId)) {
                camFileList.add(clipFile);

                if(clipFile.file.getName().contains(".vid")) {
                    isMp4 = false;
                }
            }
        }

        if(!camFileList.isEmpty()) {
            if (!isMp4) {
                trimAndMergeVidFiles(camFileList, startDateTime, endDateTime, outputDir);
            } else {
                if(!trimAndMergeMp4Files(camFileList, startDateTime, endDateTime, outputDir)) {
                    Log.e(TAG, "createClipVideo()::Failed to make mp4 file, falling back to .vid creation...");
                    trimAndMergeVidFiles(camFileList, startDateTime, endDateTime, outputDir);
                }
            }
        }
    }

    private void trimAndMergeVidFiles(final List<ClipFile> camFileList, final Date startDateTime, final Date endDateTime, File outputDir) {
        Log.d(TAG, "trimAndMergeVidFiles()");

        try {
            final int MAX_READ_BYTES = 5000000;
            byte[] byteBuffer = new byte[MAX_READ_BYTES];
            String camFileNameParts[] = camFileList.get(0).file.getName().split("_");
            File outputFile = new File(outputDir, camFileNameParts[0] + "_" + camFileNameParts[1] + "_" + camFileNameParts[2] + Constants.CAM_EXTENSION);
            BufferedOutputStream buffOutStream = new BufferedOutputStream(new FileOutputStream(outputFile));

            for (ClipFile camFile : camFileList) {
                long fileSize = camFile.file.length();
                long durationS = (camFile.endDateTime.getTime() - camFile.startDateTime.getTime()) / 1000;

                long startTrimS = (startDateTime.getTime() - camFile.startDateTime.getTime()) / 1000;
                if(startTrimS < 0) { startTrimS = 0; }
                long startTrimBytes = (fileSize / durationS) * startTrimS;

                long endTrimS = (camFile.endDateTime.getTime() - endDateTime.getTime()) / 1000;
                if(endTrimS < 0) { endTrimS = 0; }
                long endTrimBytes = (fileSize / durationS) * endTrimS;

                long bytesToRead = fileSize - startTrimBytes - endTrimBytes;
                if(bytesToRead < 0) { bytesToRead = 0; }

                Log.d(TAG, "trimAndMergeVidFiles()::fileSize: " + fileSize + ", duration: " + durationS +
                        "s, startTrimBytes: " + startTrimBytes + ", endTrimBytes: " + endTrimBytes + ", bytesToRead: " + bytesToRead);

                InputStream inStream = new BufferedInputStream(new FileInputStream(camFile.file));
                inStream.skip(startTrimBytes);

                while (bytesToRead > 0) {
                    long readWriteBuffer = bytesToRead;

                    if (readWriteBuffer > MAX_READ_BYTES) {
                        readWriteBuffer = MAX_READ_BYTES;
                    }

                    inStream.read(byteBuffer, 0, (int) readWriteBuffer);
                    buffOutStream.write(byteBuffer, 0, (int) readWriteBuffer);

                    bytesToRead -= readWriteBuffer;
                }
            }

            // Close the output stream
            buffOutStream.flush();
            buffOutStream.close();

        } catch (IOException ex) {
            Log.e(TAG, "trimAndMergeVidFiles()::Failed: " + ex.getMessage());
        }
    }

    private boolean trimAndMergeMp4Files(final List<ClipFile> camFileList, final Date startDateTime, final Date endDateTime, File outputDir) {
        Log.d(TAG, "trimAndMergeMp4Files()");

        boolean success = true;

        String camFileNameParts[] = camFileList.get(0).file.getName().split("_");
        File outputFileUntrimmed = new File(outputDir, camFileNameParts[0] + "_" + camFileNameParts[1] + "_" + camFileNameParts[2] + "_untrimmed" + Constants.MP4_EXTENSION);
        File outputFile = new File(outputDir, camFileNameParts[0] + "_" + camFileNameParts[1] + "_" + camFileNameParts[2] + Constants.MP4_EXTENSION);

        if(camFileList.size() > 1) {
            mergeMp4Videos(camFileList, outputFileUntrimmed);
        } else {
            utilities.copy(camFileList.get(0).file, outputFileUntrimmed);
        }

        long startTrimS = (startDateTime.getTime() - camFileList.get(0).startDateTime.getTime()) / 1000;
        long endTrimS = startTrimS + ((endDateTime.getTime() - startDateTime.getTime()) / 1000);

        if(!trimMp4Video(outputFileUntrimmed, startTrimS, endTrimS, outputFile)) {
            success = false;
        }

        outputFileUntrimmed.delete();

        return success;
    }


    /******************** Video editing *********************/

    private void mergeMp4Videos(final List<ClipFile> camFileList, File output) {
        Log.d(TAG, "mergeMp4Videos()");

        try {
            List<Movie> inMovies = new ArrayList<>();

            for (ClipFile camFile : camFileList) {
                Log.d(TAG, "mergeMp4Videos()::merge file: " + camFile.file.getAbsolutePath());
                inMovies.add(MovieCreator.build(camFile.file.getAbsolutePath()));
            }

            List<Track> videoTracks = new LinkedList<>();
            for (Movie m : inMovies) {
                for (Track t : m.getTracks()) {
                    if (t.getHandler().equals("vide")) {
                        videoTracks.add(t);
                    }
                }
            }

            Movie result = new Movie();

            if (!videoTracks.isEmpty()) {
                result.addTrack(new AppendTrack(videoTracks.toArray(new Track[videoTracks.size()])));
            }

            Container out = new DefaultMp4Builder().build(result);

            Log.d(TAG, "mergeMp4Videos()::writing merged file to: " + output.getAbsolutePath());
            FileChannel fc = new RandomAccessFile(String.format(output.getAbsolutePath()), "rw").getChannel();
            out.writeContainer(fc);
            fc.close();
        } catch (IOException ex) {
            Log.e(TAG, "mergeMp4Videos()::" + ex.getMessage());
        }
    }

    private boolean trimMp4Video(File input, double startTime, double endTime, File output) {
        Log.d(TAG, "trimMp4Video()");

        boolean success = true;

        try {
            Movie movie = MovieCreator.build(input.getAbsolutePath());
            List<Track> tracks = movie.getTracks();
            movie.setTracks(new LinkedList<>());
            Track track = tracks.get(0);

            // Here we try to find a track that has sync samples. Since we can only start decoding
            // at such a sample we SHOULD make sure that the start of the new fragment is exactly
            // such a frame
            if (track.getSyncSamples() != null && track.getSyncSamples().length > 0) {
                startTime = correctTimeToSyncSample(track, startTime, false);
                endTime = correctTimeToSyncSample(track, endTime, true);
            }

            long currentSample = 0;
            double currentTime = 0;
            double lastTime = -1;
            long startSample1 = -1;
            long endSample1 = -1;

            for (int i = 0; i < track.getSampleDurations().length; i++) {
                long delta = track.getSampleDurations()[i];

                if (currentTime > lastTime && currentTime <= startTime) {
                    // current sample is still before the new starttime
                    startSample1 = currentSample;
                }
                if (currentTime > lastTime && currentTime <= endTime) {
                    // current sample is after the new start time and still before the new endtime
                    endSample1 = currentSample;
                }

                lastTime = currentTime;
                currentTime += (double) delta / (double) track.getTrackMetaData().getTimescale();
                currentSample++;
            }

            movie.addTrack(new AppendTrack(new ClippedTrack(track, startSample1, endSample1)));

            Container out = new DefaultMp4Builder().build(movie);
            FileOutputStream fos = new FileOutputStream(String.format(output.getAbsolutePath(), startTime, endTime));
            FileChannel fc = fos.getChannel();
            out.writeContainer(fc);

            fc.close();
            fos.close();
        } catch (IOException ex) {
            Log.e(TAG, "trimVideo()::" + ex.getMessage());
            success = false;
        } catch (ArrayIndexOutOfBoundsException ex) {
            Log.e(TAG, "trimVideo()::" + ex.getMessage());
            success = false;
        }

        return success;
    }

    private static double correctTimeToSyncSample(Track track, double cutHere, boolean next) {
        double[] timeOfSyncSamples = new double[track.getSyncSamples().length];
        long currentSample = 0;
        double currentTime = 0;
        for (int i = 0; i < track.getSampleDurations().length; i++) {
            long delta = track.getSampleDurations()[i];

            if (Arrays.binarySearch(track.getSyncSamples(), currentSample + 1) >= 0) {
                // samples always start with 1 but we start with zero therefore +1
                timeOfSyncSamples[Arrays.binarySearch(track.getSyncSamples(), currentSample + 1)] = currentTime;
            }
            currentTime += (double) delta / (double) track.getTrackMetaData().getTimescale();
            currentSample++;

        }
        double previous = 0;
        for (double timeOfSyncSample : timeOfSyncSamples) {
            if (timeOfSyncSample > cutHere) {
                if (next) {
                    return timeOfSyncSample;
                } else {
                    return previous;
                }
            }
            previous = timeOfSyncSample;
        }
        return timeOfSyncSamples[timeOfSyncSamples.length - 1];
    }
}
