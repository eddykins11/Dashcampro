package io.kasava.utilities;

import android.content.Context;
import android.util.Log;

import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.microsoft.windowsazure.mobileservices.MobileServiceClient;
import com.microsoft.windowsazure.mobileservices.authentication.MobileServiceUser;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import io.kasava.broadcast.LocalBroadcastMessage;
import io.kasava.dashcampro.BuildConfig;
import io.kasava.data.AzureResponse;
import io.kasava.data.Blob;
import io.kasava.data.Cmd;
import io.kasava.data.Event;
import io.kasava.data.DeviceInfo;
import io.kasava.data.HealthCheckData;
import io.kasava.data.LoginRequest;
import io.kasava.data.LoginResult;
import io.kasava.data.Status;
import io.kasava.data.Subscription;

public class Azure {
    private static final String TAG = "Azure";

    private static final int MAX_STATUS_QUEUE = 100;
    private static final int MAX_JOURNEY_LOG_QUEUE = 50;
    private static final int MAX_EVENT_QUEUE = 50;
    private static final int MAX_BLOB_QUEUE = 500;
    private static final int MAX_HEALTH_CHECK_QUEUE = 10;
    private static final int MAX_CMD_QUEUE = 10;

    private final Context mContext;
    private final Cellular cellular;
    private final Utilities utilities;

    public Azure(Context context) {
        mContext = context;
        cellular = new Cellular(mContext);
        utilities = new Utilities(mContext);
    }

    private MobileServiceClient mClient;

    private static final String mStorageConnectionString =
            "DefaultEndpointsProtocol=https;" +
                    "AccountName=kasavastorageuk;" +
                    "AccountKey=6Ak+gUE1ivaXvQqpF7iIsvNYq7/YYFpnlDJlzq5j4n3rOXOWGy4OPq6gE6xWiSHtlt66bSttUmbbbL6GDTKxsQ==";

    private String mContainerName = "Unknown";
    private Subscription mSubscription = null;
    private String mDeviceId = null;
    private String mToken = null;
    private String mWdVersion = "Unknown";
    private String mPushTag = null;

    private List<Status> mStatusQueue = new ArrayList<>();
    private boolean mStatusUpdateInProgress = false;
    private int mStatusTryCount = 0;

    private List<File> mJourneyLogQueue = new ArrayList<>();
    private boolean mJourneyLogUpdateInProgress = false;
    private int mJourneyLogTryCount = 0;

    private List<Event> mEventQueue = new ArrayList<>();
    private boolean mEventUpdateInProgress = false;
    private int mEventTryCount = 0;

    private List<Blob> mBlobQueue = new ArrayList<>();
    private boolean mBlobUploadInProgress = false;
    private int mBlobTryCount = 0;

    private List<HealthCheckData> mHealthCheckDataQueue = new ArrayList<>();
    private boolean mHealthCheckUpdateInProgress = false;
    private int mHealthCheckTryCount = 0;

    private List<Cmd> mCmdQueue = new ArrayList<>();
    private boolean mCmdUpdateInProgress = false;
    private int mCmdTryCount = 0;

    private boolean mLiveViewInProgress = false;

    public void start(final String deviceId, final String azureUrl, final Subscription subscription) {
        mDeviceId = deviceId;
        mSubscription = subscription;
        mContainerName = "kdc";

        Thread thread = new Thread(() -> {
            Log.d(TAG, "Azure Started");

            // Load saved lists
            try {
                mStatusQueue = utilities.loadListStatus();
                mJourneyLogQueue = utilities.loadListJourneyLog();
                mEventQueue = utilities.loadListEvent();
                mBlobQueue = utilities.loadListBlob();
            } catch (Exception ex) {
                Log.e(TAG, "start()::failed to load azure lists: " + ex.getMessage());
            }

            configureClient(azureUrl);
            loginAndFetchToken();
        });
        thread.start();
    }

    private void configureClient(final String azureUrl) {
        try {
            mClient = new MobileServiceClient(azureUrl, mContext);
        } catch (MalformedURLException ex) {
            Log.e(TAG, "Error configuring client: " + ex.getMessage());
        }
    }

    private void loginAndFetchToken() {
        try {
            Log.d(TAG, "loginAndFetchToken():Login to Azure services...");

            LoginRequest details = new LoginRequest();
            details.deviceId = mDeviceId;
            details.password = "78yfbj3b8998we8hfui";

            LoginResult loginResult = mClient.invokeApi("Login", details, LoginResult.class).get();

            mToken = loginResult.mobileServiceAuthenticationToken;

            MobileServiceUser user = new MobileServiceUser(mContainerName);
            user.setAuthenticationToken(mToken);
            mClient.setCurrentUser(user);

            Log.d(TAG, "loginAndFetchToken():Logged in OK");

            utilities.sendLocalBroadcastMessage(LocalBroadcastMessage.Type.loginOk, null);

            processAllQueues();

        } catch (Exception ex) {
            Log.d(TAG, "loginAndFetchToken():Failed to login to Azure: " + ex.getMessage());

            // Try login again
            Thread thread = new Thread(() -> {
                try {
                    Thread.sleep(30 * 1000);
                    loginAndFetchToken();
                } catch (Exception ex1) {
                    Log.e(TAG, "Failed to try login again::" + ex1.getMessage());
                }
            });
            thread.start();
        }
    }

    public String getToken() {
        return mToken;
    }

    public void processAllQueues() {
        Thread thread = new Thread(() -> {
            processStatusQueue();
            processJourneyLogQueue();
            processEventQueue();
            processBlobQueue();
            processCmdQueue();
        });
        thread.start();
    }

    public void processQueue(String queue) {
        Log.d(TAG, "processQueue()::" + queue);

        Thread thread = new Thread(() -> {

            switch (queue) {
                case "Status":
                    processStatusQueue();
                    break;

                case "JourneyLog":
                    processJourneyLogQueue();
                    break;

                case "Event":
                    processEventQueue();
                    break;

                case "Blob":
                    processBlobQueue();
                    break;
            }
        });
        thread.start();
    }

    public void clearQueue(String queue) {
        Log.d(TAG, "clearQueue()::" + queue);

        switch (queue) {
            case "Status":
                mStatusQueue.clear();
                utilities.saveListStatus(mStatusQueue);
                break;

            case "JourneyLog":
                mJourneyLogQueue.clear();
                utilities.saveListJourneyLog(mJourneyLogQueue);
                break;

            case "Event":
                mEventQueue.clear();
                utilities.saveListEvent(mEventQueue);
                break;

            case "Blob":
                mBlobQueue.clear();
                utilities.saveListBlob(mBlobQueue);
                break;
        }
    }

    public void removeFromQueue(String queue, int qty) {
        Log.d(TAG, "removeFromQueue()::" + queue);

        switch (queue) {
            case "Status":
                for(int i = 0; i<qty; i++) {
                    if(!mStatusQueue.isEmpty()) {
                        mStatusQueue.remove(0);
                    }
                }
                utilities.saveListStatus(mStatusQueue);
                break;

            case "JourneyLog":
                for(int i = 0; i<qty; i++) {
                    if(!mJourneyLogQueue.isEmpty()) {
                        mJourneyLogQueue.remove(0);
                    }
                }
                utilities.saveListJourneyLog(mJourneyLogQueue);
                break;

            case "Event":
                for(int i = 0; i<qty; i++) {
                    if(!mEventQueue.isEmpty()) {
                        mEventQueue.remove(0);
                    }
                }
                utilities.saveListEvent(mEventQueue);
                break;

            case "Blob":
                for(int i = 0; i<qty; i++) {
                    if(!mBlobQueue.isEmpty()) {
                        mBlobQueue.remove(0);
                    }
                }
                utilities.saveListBlob(mBlobQueue);
                break;
        }
    }


    /******************** Device Info *********************/

    public void setWdVersion(String wdVersion) {
        mWdVersion = wdVersion;
        Thread thread = new Thread(() -> updateSubscription(true));
        thread.start();
    }

    public void setPushTag(String pushTag) {
        boolean forceTmp = false;
        if(mPushTag == null) {
            forceTmp = true;
        }
        mPushTag = pushTag;
        final boolean force = forceTmp;
        Thread thread = new Thread(() -> updateSubscription(force));
        thread.start();
    }

    public void updateSubscription(boolean forceUpdate) {
        if (mToken != null && mWdVersion != null && mPushTag != null && (mSubscription == null || forceUpdate)) {
            Log.d(TAG, "updateSubscription()::forced=" + forceUpdate);
            try {
                DeviceInfo deviceInfo = new DeviceInfo();
                deviceInfo.deviceId = mDeviceId;
                deviceInfo.iccId = cellular.getSimNo();
                deviceInfo.kdcVersion = BuildConfig.VERSION_NAME;
                deviceInfo.wdVersion = mWdVersion;
                //deviceInfo.picVersion = "1.00";
                deviceInfo.pushTag = mPushTag;

                Log.d(TAG, "SIM: " + deviceInfo.iccId);

                mSubscription = null;

                // Get device subscription
                Log.d(TAG, "updateSubscription()");
                mSubscription = mClient.invokeApi("Info", deviceInfo, Subscription.class).get();

                if (mSubscription != null && mSubscription.accountId != null) {
                    Log.d(TAG, "updateSubscription()::OK");

                    utilities.sendLocalBroadcastMessage(LocalBroadcastMessage.Type.syncSubscription, null);

                    processAllQueues();
                } else {
                    Log.d(TAG, "updateSubscription()::Failed");
                }

            } catch (Exception ex) {
                Log.e(TAG, "Failed to update subscription: " + ex.getMessage());
            }
        }
    }

    public Subscription getSubscriptionFromAzure() {
        return mSubscription;
    }


    /******************** Status *********************/

    public Status getOldestStatusInQueue() {
        if(!mStatusQueue.isEmpty()) {
            return mStatusQueue.get(0);
        } else {
            return null;
        }
    }

    public  void addToStatusQueue(Status status) {
        mStatusQueue.add(status);

        while(mStatusQueue.size() > MAX_STATUS_QUEUE) {
            mStatusQueue.remove(0);
        }

        utilities.saveListStatus(mStatusQueue);
        Log.d(TAG, "Saved status list: " + mStatusQueue.size());

        Thread thread = new Thread(this::processStatusQueue);
        thread.start();
    }

    private void processStatusQueue() {
        Log.d(TAG, "processStatusQueue()::queue size: " + mStatusQueue.size());

        // Update subscription if it's not already set
        updateSubscription(false);

       if(mSubscription == null || mSubscription.accountId == null) {
           Log.d(TAG, "processStatusQueue()::can't process until we have accountId");
           return;
      }


        // Remove any invalid statuses
        while(!mStatusUpdateInProgress && !mStatusQueue.isEmpty() && mStatusQueue.get(0).dateTime == null) {
            mStatusQueue.remove(0);
            utilities.saveListStatus(mStatusQueue);
        }

        if (!mStatusUpdateInProgress && mToken != null && !mStatusQueue.isEmpty()) {

            mStatusUpdateInProgress = true;

            try {
                // Update database
                Status status = mStatusQueue.get(0);

                // Set the accountId & deviceId
                status.accountId = mSubscription.accountId;
                status.deviceId = mDeviceId;

                AzureResponse response = mClient.invokeApi("Status", status, AzureResponse.class).get();
                if (response != null) {
                    Log.d(TAG, "Updated status data");

                    // Remove status and save queue
                    mStatusQueue.remove(0);
                    utilities.saveListStatus(mStatusQueue);
                    mStatusTryCount = 0;

                    if(response.result.equals("Wake")) {
                        utilities.sendLocalBroadcastMessage(LocalBroadcastMessage.Type.wakeUp, null);
                    }

                } else {
                    Log.d(TAG, "Failed to update status data");
                    mStatusTryCount++;
                }

            } catch (Exception ex) {
                Log.e(TAG, "Failed to update status data: " + ex.getMessage());
                mStatusTryCount++;
            }

            mStatusUpdateInProgress = false;

            try {
                if (mStatusTryCount > 3) {
                    // Reset try count for next message attempt
                    mStatusTryCount = 0;
                } else {
                    if(mStatusTryCount > 0) {
                        // Delay if there was a previous failed update
                        Thread.sleep(10 * 1000);
                    }
                    processStatusQueue();
                }
            } catch (InterruptedException ex) {
                Log.e(TAG, "Failed to try status update again: " + ex.getMessage());
            }
        }
    }


    /******************** Journey *********************/

    public void addToJourneyLogQueue(File journeyLog) {
        mJourneyLogQueue.add(journeyLog);

        while(mJourneyLogQueue.size() > MAX_JOURNEY_LOG_QUEUE) {
            mJourneyLogQueue.remove(0);
        }

        utilities.saveListJourneyLog(mJourneyLogQueue);
        Log.d(TAG, "Saved journey list: " + mJourneyLogQueue.size());

        Thread thread = new Thread(this::processJourneyLogQueue);
        thread.start();
    }

    private void processJourneyLogQueue() {
        Log.d(TAG, "processJourneyLogQueue()::queue size: " + mJourneyLogQueue.size());

        if(mSubscription == null || mSubscription.accountId == null) {
            Log.d(TAG, "processJourneyLogQueue()::can't process until we have accountId");
            return;
        }

        // Remove any invalid journeys
        while(!mJourneyLogUpdateInProgress && !mJourneyLogQueue.isEmpty() &&
                (mJourneyLogQueue.get(0) == null || !mJourneyLogQueue.get(0).exists())) {

            mJourneyLogQueue.remove(0);
            utilities.saveListJourneyLog(mJourneyLogQueue);
        }

        if (!mJourneyLogUpdateInProgress && !mJourneyLogQueue.isEmpty()) {

            mJourneyLogUpdateInProgress = true;

            if (uploadJourneyLogFileToAzure(mJourneyLogQueue.get(0))) {
                Log.d(TAG, "Updated journey data");

                // Remove journey and save queue
                mJourneyLogQueue.remove(0);
                utilities.saveListJourneyLog(mJourneyLogQueue);
                mJourneyLogTryCount = 0;

            } else {
                Log.e(TAG, "Failed to update journey data");
                mJourneyLogTryCount++;
            }

            try {
                // Delay all journeys uploads to give server time to add to the serviceBus
                Thread.sleep(20 * 1000);

                mJourneyLogUpdateInProgress = false;

                if (mJourneyLogTryCount > 3) {
                    // Reset try count for next message attempt
                    mJourneyLogTryCount = 0;
                } else {
                    processJourneyLogQueue();
                }
            } catch (InterruptedException ex) {
                Log.e(TAG, "Failed to delay journey data: " + ex.getMessage());
            }
        }
    }

    private boolean uploadJourneyLogFileToAzure(final File journeyLog) {

        boolean success = true;

        try {
            // Retrieve storage account from connection-string.
            CloudStorageAccount storageAccount = CloudStorageAccount.parse(mStorageConnectionString);
            Log.d(TAG, "Connecting to storage...");

            // Create the blob client.
            CloudBlobClient blobClient = storageAccount.createCloudBlobClient();
            Log.d(TAG, "Checking remote directory...");

            // Get container name
            String containerName = mContainerName;

            // Retrieve reference to a previously created container.
            CloudBlobContainer container = blobClient.getContainerReference(containerName);

            if(journeyLog != null && journeyLog.exists()) {
                String blobName = mDeviceId.toLowerCase() + "/journeys/" + journeyLog.getName().replace("journey_", "");
                Log.d(TAG, "Uploading " + journeyLog + " (" + blobName + "): " + journeyLog.length() / 1000 + "KB");

                // Create or overwrite the blob with contents from a local file.
                CloudBlockBlob blockBlob = container.getBlockBlobReference(blobName);
                blockBlob.upload(new FileInputStream(journeyLog), journeyLog.length());

                Log.d(TAG, "Journey log file blob upload complete");
            }
        } catch (Exception ex) {
            Log.e(TAG, "Failed to upload blob, error:" + ex.getMessage());
            success = false;
        }

        return success;
    }


    /******************** Event *********************/

    public void addToEventQueue(Event event) {
        event.deviceId = mDeviceId;
        mEventQueue.add(event);

        while(mEventQueue.size() > MAX_EVENT_QUEUE) {
            mEventQueue.remove(0);
        }

        utilities.saveListEvent(mEventQueue);
        Log.d(TAG, "Saved event list: " + mEventQueue.size());

        Thread thread = new Thread(this::processEventQueue);
        thread.start();
    }

    private void processEventQueue() {
        Log.d(TAG, "processEventQueue()::queue size: " + mEventQueue.size());

        if(mSubscription == null || mSubscription.accountId == null) {
            Log.d(TAG, "processEventQueue()::can't process until we have accountId");
            return;
        }

        // Remove any invalid events
        while(!mEventUpdateInProgress && !mEventQueue.isEmpty() && (mEventQueue.get(0) == null || mEventQueue.get(0).dateTime == null)) {
            mEventQueue.remove(0);
            utilities.saveListEvent(mEventQueue);
        }

        if (!mEventUpdateInProgress && mToken != null && !mEventQueue.isEmpty()) {

            mEventUpdateInProgress = true;

            try {
                // Update database
                Event event = mEventQueue.get(0);

                // Set the accountId & deviceId
                event.accountId = mSubscription.accountId;
                event.deviceId = mDeviceId;

                if(event.dateTime.length() == 22) {
                    Log.d(TAG, "processEventQueue()::changed DT to azure format");
                    event.dateTime = utilities.dateToAzureDate(utilities.utcDateTimeStringToDate(event.dateTime));
                }

                if(event.clipDateTime.length() == 22) {
                    Log.d(TAG, "processEventQueue()::changed DT to azure format");
                    event.clipDateTime = utilities.dateToAzureDate(utilities.utcDateTimeStringToDate(event.clipDateTime));
                }

                AzureResponse response = mClient.invokeApi("Event", event, AzureResponse.class).get();
                if (response != null) {
                    Log.d(TAG, "Updated event data");

                    // Remove event and save queue
                    mEventQueue.remove(0);
                    utilities.saveListEvent(mEventQueue);
                    mEventTryCount = 0;

                } else {
                    Log.d(TAG, "Failed to update event data");
                    mEventTryCount++;
                }

            } catch (Exception ex) {
                Log.e(TAG, "Failed to update event data: " + ex.getMessage());
                mEventTryCount++;
            }

            mEventUpdateInProgress = false;

            try {
                if (mEventTryCount > 3) {
                    // Reset try count for next message attempt
                    mEventTryCount = 0;
                } else {
                    if(mEventTryCount > 0) {
                        // Delay if there was a previous failed update
                        Thread.sleep(10 * 1000);
                    }
                    processEventQueue();
                }
            } catch (InterruptedException ex) {
                Log.e(TAG, "Failed to try event update again: " + ex.getMessage());
            }
        }
    }


    /******************** Blobs *********************/

    public void addToBlobQueue(final Blob blob) {
        mBlobQueue.add(blob);

        while(mBlobQueue.size() > MAX_BLOB_QUEUE) {
            mBlobQueue.remove(0);
        }

        utilities.saveListBlob(mBlobQueue);
        Log.d(TAG, "Saved to blob list()::" + blob.file.getPath());

        processBlobQueue();
    }

    private void processBlobQueue() {
        Log.d(TAG, "processBlobQueue(): " + mBlobQueue.size());
        try {
            if (!mBlobUploadInProgress && !mBlobQueue.isEmpty()) {
                Log.d(TAG, "Checking for any blobs requiring upload");

                final Blob blob = mBlobQueue.get(0);

                Thread threadUpload = new Thread(() -> {
                    mBlobUploadInProgress = true;
                    if(uploadBlobToAzure(blob)) {
                        mBlobQueue.remove(0);
                        utilities.saveListBlob(mBlobQueue);
                        mBlobTryCount = 0;
                    } else {
                        mBlobTryCount++;
                    }
                    mBlobUploadInProgress = false;

                    try {
                        if (mBlobTryCount > 3) {
                            // Reset try count for next message attempt
                            mBlobTryCount = 0;
                        } else {
                            if(mBlobTryCount > 0) {
                                Thread.sleep(30 * 1000);
                            } else {
                                Thread.sleep(1000);
                            }
                            processBlobQueue();
                        }
                    } catch (InterruptedException ex) {
                        Log.e(TAG, "Failed to try blob update again: " + ex.getMessage());
                    }
                });
                threadUpload.start();
            } else {
                Log.d(TAG, "processBlobQueue(): empty or in progress");
            }
        } catch (Exception ex) {
            Log.e(TAG, "processBlobQueue failed: " + ex.getMessage());
        }
    }

    private boolean uploadBlobToAzure(final Blob blob) {

        boolean success = true;

        try {
            // Retrieve storage account from connection-string.
            CloudStorageAccount storageAccount = CloudStorageAccount.parse(mStorageConnectionString);
            Log.d(TAG, "Connecting to storage...");

            // Create the blob client.
            CloudBlobClient blobClient = storageAccount.createCloudBlobClient();
            Log.d(TAG, "Checking remote directory...");

            // Retrieve reference to a previously created container.
            CloudBlobContainer container = blobClient.getContainerReference(mContainerName);

            if(blob != null) {
                File blobToUpload = blob.file;
                String blobName = mDeviceId.toLowerCase();
                if (blob.isEvent) {
                    blobName += "/events/" + blob.fileName;
                } else {
                    blobName += "/clips/" + blob.fileName;
                }

                if (blobToUpload != null && blobToUpload.exists()) {
                    Log.d(TAG, "Uploading " + blobToUpload + ": " + blobToUpload.length() / 1000 + "KB");

                    // Create or overwrite the blob with contents from a local file.
                    CloudBlockBlob blockBlob = container.getBlockBlobReference(blobName);
                    blockBlob.upload(new FileInputStream(blobToUpload), blobToUpload.length());

                    Log.d(TAG, "Blob upload complete");
                }
            }
        } catch (Exception ex) {
            Log.e(TAG, "Failed to upload blob, error:" + ex.getMessage());
            success = false;
        }

        return success;
    }


    /******************** Health Check *********************/

    public void addToHealthCheckQueue(HealthCheckData healthCheckData) {
        mHealthCheckDataQueue.add(healthCheckData);

        while(mHealthCheckDataQueue.size() > MAX_HEALTH_CHECK_QUEUE) {
            mHealthCheckDataQueue.remove(0);
        }

        Log.d(TAG, "Saved health check list: " + mHealthCheckDataQueue.size());

        Thread thread = new Thread(this::processHealthCheckQueue);
        thread.start();
    }

    private void processHealthCheckQueue() {
        Log.d(TAG, "processHealthCheckQueue()::queue size: " + mHealthCheckDataQueue.size());

        /*if(mSubscription == null || mSubscription.accountId == null) {
            Log.d(TAG, "processHealthCheckQueue()::can't process until we have accountId");
            return;
        }*/

        // Remove any invalid events
        while(!mHealthCheckUpdateInProgress && !mHealthCheckDataQueue.isEmpty() && mHealthCheckDataQueue.get(0).dateTime == null) {
            mHealthCheckDataQueue.remove(0);
        }

        if (!mHealthCheckUpdateInProgress && mToken != null && !mHealthCheckDataQueue.isEmpty()) {

            mHealthCheckUpdateInProgress = true;

            try {
                // Update database
                HealthCheckData healthCheckData = mHealthCheckDataQueue.get(0);

                // Set the accountId & deviceId
                if(mSubscription.accountId != null){
                     healthCheckData.accountId = mSubscription.accountId;
                }
                else{
                    healthCheckData.accountId = null;
                }
                healthCheckData.deviceId = mDeviceId;

                AzureResponse response = mClient.invokeApi("HealthCheck", healthCheckData, AzureResponse.class).get();
                if (response != null) {
                    Log.d(TAG, "Updated health check data");

                    // Remove cmd from queue
                    mHealthCheckDataQueue.remove(0);
                    mHealthCheckTryCount = 0;
                } else {
                    Log.d(TAG, "Failed to update health check data");
                    mHealthCheckTryCount++;
                }

            } catch (Exception ex) {
                Log.e(TAG, "Failed to update health check data: " + ex.getMessage());
                mHealthCheckTryCount++;
            }

            mHealthCheckUpdateInProgress = false;

            try {
                if (mHealthCheckTryCount > 3) {
                    // Reset try count for next message attempt
                    mHealthCheckTryCount = 0;
                } else {
                    if(mHealthCheckTryCount > 0) {
                        // Delay if there was a previous failed update
                        Thread.sleep(10 * 1000);
                    }
                    processHealthCheckQueue();
                }
            } catch (InterruptedException ex) {
                Log.e(TAG, "Failed to try event health check again: " + ex.getMessage());
            }
        }
    }


    /******************** Terminal Commands *********************/

    public void addToCmdQueue(String cmdStr) {
        Cmd cmd = new Cmd();
        cmd.deviceId = mDeviceId;
        cmd.dateTime = utilities.dateToAzureDate(Calendar.getInstance().getTime());
        cmd.cmd = cmdStr;
        mCmdQueue.add(cmd);

        while(mCmdQueue.size() > MAX_CMD_QUEUE) {
            mCmdQueue.remove(0);
        }

        Thread thread = new Thread(this::processCmdQueue);
        thread.start();
    }

    private void processCmdQueue() {
        Log.d(TAG, "processCmdQueue()::queue size: " + mCmdQueue.size());

        // Remove any invalid events
        while(!mCmdUpdateInProgress && !mCmdQueue.isEmpty() && mCmdQueue.get(0).dateTime == null) {
            mCmdQueue.remove(0);
        }

        if (!mCmdUpdateInProgress && mToken != null && !mCmdQueue.isEmpty()) {

            mCmdUpdateInProgress = true;

            try {
                // Update database
                Cmd cmd = mCmdQueue.get(0);

                AzureResponse response = mClient.invokeApi("Cmd", cmd, AzureResponse.class).get();
                if (response != null) {
                    Log.d(TAG, "Updated cmd data");

                    // Remove cmd from queue
                    mCmdQueue.remove(0);
                    mCmdTryCount = 0;

                } else {
                    Log.d(TAG, "Failed to update cmd data");
                    mCmdTryCount++;
                }

            } catch (Exception ex) {
                Log.e(TAG, "Failed to update cmd data: " + ex.getMessage());
                mCmdTryCount++;
            }

            mCmdUpdateInProgress = false;

            try {
                if (mCmdTryCount > 3) {
                    // Reset try count for next message attempt
                    mCmdTryCount = 0;
                } else {
                    if(mCmdTryCount > 0) {
                        // Delay if there was a previous failed update
                        Thread.sleep(10 * 1000);
                    }
                    processCmdQueue();
                }
            } catch (InterruptedException ex) {
                Log.e(TAG, "Failed to try event cmd again: " + ex.getMessage());
            }
        }
    }

    /******************** LiveView *********************/

    public void uploadLiveView(final byte[] dataToUpload) {

        if(mLiveViewInProgress) {
            return;
        }

        mLiveViewInProgress = true;
        uploadLiveViewToAzure(dataToUpload);
        mLiveViewInProgress = false;
    }

    private void uploadLiveViewToAzure(byte[] dataToUpload) {

        try {
            // Retrieve storage account from connection-string.
            CloudStorageAccount storageAccount = CloudStorageAccount.parse(mStorageConnectionString);
            Log.d(TAG, "uploadLiveViewToAzure()::Connecting to storage...");

            // Create the blob client.
            CloudBlobClient blobClient = storageAccount.createCloudBlobClient();
            Log.d(TAG, "uploadLiveViewToAzure()::Checking remote directory...");

            // Retrieve reference to a previously created container.
            //CloudBlobContainer container = blobClient.getContainerReference(mContainerName);
            CloudBlobContainer container = blobClient.getContainerReference("devices");

            String blobName = mDeviceId.toLowerCase() + "/" + "lv/" + utilities.nowAsUtcDateTimeString() + ".jpg";

            if (dataToUpload != null) {
                Log.d(TAG, "Uploading liveView data: " + blobName);

                // Create or overwrite the blob with contents from a local file.
                CloudBlockBlob blockBlob = container.getBlockBlobReference(blobName);
                ByteArrayInputStream streamToUpload = new ByteArrayInputStream(dataToUpload);
                blockBlob.upload(streamToUpload, dataToUpload.length);

                Log.d(TAG, "LiveView data upload complete");
            }

        } catch (Exception ex) {
            Log.e(TAG, "Failed to upload liveView data, error:" + ex.getMessage());
        }

        utilities.sendLocalBroadcastMessage(LocalBroadcastMessage.Type.liveViewUploaded, null);
    }
}