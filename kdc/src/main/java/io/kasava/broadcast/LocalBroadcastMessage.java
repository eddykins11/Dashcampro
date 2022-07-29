package io.kasava.broadcast;

public class LocalBroadcastMessage {

	public final static String ID = "LOCAL_BROADCAST_MSG";
	public final static String EXTRA = "LOCAL_BROADCAST_EXTRA";

	public enum Type {
		alertButton,
		backupBatt,
		canData,
		canRaw,
		clearQueue,
		customVideoRequest,
		eventVideoRequest,
		event,
		fileRequest,
		ftdiQueueEmpty,
		ftdiCanRxMsg,
		ftdiCanTxMsg,
		healthCheckManual,
		ignitionOff,
		ignitionOn,
		liveViewStart,
		liveViewUploaded,
		loginOk,
		processQueue,
		reboot,
		removeFromQueue,
		requestRecordingChangeDir,
		terminalCmd,
		updateSubscription,
		uvcCameraReady,
		shutdown,
		syncSubscription,
		updateRequest,
		wakeUp,
		immobiliserOff,
		immobiliserOn,
		cameraFound
	}
}
