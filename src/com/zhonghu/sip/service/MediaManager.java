package com.zhonghu.sip.service;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.NetworkInfo.DetailedState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;

import com.zhonghu.sip.api.MediaState;
import com.zhonghu.sip.api.SipConfigManager;
import com.zhonghu.sip.api.SipManager;
import com.zhonghu.sip.pjsip.SameThreadException;
import com.zhonghu.sip.service.SipService.SipRunnable;
import com.zhonghu.sip.uti.accessibility.AccessibilityWrapper;
import com.zhonghu.sip.util.audio.AudioFocusWrapper;
import com.zhonghu.sip.util.bluetooth.BluetoothWrapper;
import com.zhonghu.sip.util.bluetooth.BluetoothWrapper.BluetoothChangeListener;
import com.zhonghu.sip.utils.Compatibility;
import com.zhonghu.sip.utils.Ringer;

public class MediaManager implements BluetoothChangeListener {

	final private static String THIS_FILE = "MediaManager";

	private SipService service;
	private AudioManager audioManager;
	private Ringer ringer;

	// Locks
	private WifiLock wifiLock;
	private WakeLock screenLock;

	// Media settings to save / resore
	private boolean isSetAudioMode = false;

	// By default we assume user want bluetooth.
	// If bluetooth is not available connection will never be done and then
	// UI will not show bluetooth is activated
	private boolean userWantBluetooth = false;
	private boolean userWantSpeaker = false;
	private boolean userWantMicrophoneMute = false;

	private boolean restartAudioWhenRoutingChange = true;
	private Intent mediaStateChangedIntent;

	// Bluetooth related
	private BluetoothWrapper bluetoothWrapper;

	private AudioFocusWrapper audioFocusWrapper;
	private AccessibilityWrapper accessibilityManager;

	private SharedPreferences prefs;
	private boolean useSgsWrkAround = false;
	private boolean useWebRTCImpl = false;
	private boolean doFocusAudio = true;

	private boolean startBeforeInit;
	private static int modeSipInCall = AudioManager.MODE_NORMAL;

	@Override
	public void onBluetoothStateChanged(int status) {
		// TODO Auto-generated method stub

	}

	public MediaManager(SipService aService) {
		service = aService;
		audioManager = (AudioManager) service
				.getSystemService(Context.AUDIO_SERVICE);
		prefs = service.getSharedPreferences("audio", Context.MODE_PRIVATE);
		// prefs = PreferenceManager.getDefaultSharedPreferences(service);
		accessibilityManager = AccessibilityWrapper.getInstance();
		accessibilityManager.init(service);

		ringer = new Ringer(service);
		mediaStateChangedIntent = new Intent(
				SipManager.ACTION_SIP_MEDIA_CHANGED);

		// Try to reset if there were a crash in a call could restore previous
		// settings
		restoreAudioState();
	}

	@SuppressWarnings("deprecation")
	private final synchronized void restoreAudioState() {
		if (!prefs.getBoolean("isSavedAudioState", false)) {
			// If we have NEVER set, do not try to reset !
			return;
		}

		ContentResolver ctntResolver = service.getContentResolver();
		Compatibility.setWifiSleepPolicy(
				ctntResolver,
				prefs.getInt("savedWifiPolicy",
						Compatibility.getWifiSleepPolicyDefault()));
		// audioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
		// prefs.getInt("savedVibrateRing",
		// AudioManager.VIBRATE_SETTING_ONLY_SILENT));
		// audioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_NOTIFICATION,
		// prefs.getInt("savedVibradeNotif", AudioManager.VIBRATE_SETTING_OFF));
		// audioManager.setRingerMode(prefs.getInt("savedRingerMode",
		// AudioManager.RINGER_MODE_NORMAL));

		int inCallStream = Compatibility.getInCallStream(userWantBluetooth);
		setStreamVolume(inCallStream, prefs.getInt("savedVolume",
				(int) (audioManager.getStreamMaxVolume(inCallStream) * 0.8)), 0);

		int targetMode = getAudioTargetMode();
		if (service.getPrefs().useRoutingApi()) {
			audioManager.setRouting(targetMode,
					prefs.getInt("savedRoute", AudioManager.ROUTE_SPEAKER),
					AudioManager.ROUTE_ALL);
		} else {
			audioManager.setSpeakerphoneOn(prefs.getBoolean(
					"savedSpeakerPhone", false));
		}
		audioManager.setMode(prefs
				.getInt("savedMode", AudioManager.MODE_NORMAL));

		Editor ed = prefs.edit();
		ed.putBoolean("isSavedAudioState", false);
		ed.commit();
	}

	private int getAudioTargetMode() {
		int targetMode = modeSipInCall;

		if (service.getPrefs().useModeApi()) {
			Log.d(THIS_FILE, "User want speaker now..." + userWantSpeaker);
			if (!service.getPrefs().generateForSetCall()) {
				return userWantSpeaker ? AudioManager.MODE_NORMAL
						: AudioManager.MODE_IN_CALL;
			} else {
				return userWantSpeaker ? AudioManager.MODE_IN_CALL
						: AudioManager.MODE_NORMAL;
			}
		}
		if (userWantBluetooth) {
			targetMode = AudioManager.MODE_NORMAL;
		}

		Log.d(THIS_FILE, "Target mode... : " + targetMode);
		return targetMode;
	}

	private static final String ACTION_AUDIO_VOLUME_UPDATE = "org.openintents.audio.action_volume_update";
	private static final String EXTRA_STREAM_TYPE = "org.openintents.audio.extra_stream_type";
	private static final String EXTRA_VOLUME_INDEX = "org.openintents.audio.extra_volume_index";
	private static final String EXTRA_RINGER_MODE = "org.openintents.audio.extra_ringer_mode";
	private static final int EXTRA_VALUE_UNKNOWN = -9999;

	private void broadcastVolumeWillBeUpdated(int streamType, int index) {
		Intent notificationIntent = new Intent(ACTION_AUDIO_VOLUME_UPDATE);
		notificationIntent.putExtra(EXTRA_STREAM_TYPE, streamType);
		notificationIntent.putExtra(EXTRA_VOLUME_INDEX, index);
		notificationIntent.putExtra(EXTRA_RINGER_MODE, EXTRA_VALUE_UNKNOWN);

		service.sendBroadcast(notificationIntent, null);
	}

	public void startService() {
		if (bluetoothWrapper == null) {
			bluetoothWrapper = BluetoothWrapper.getInstance(service);
			bluetoothWrapper.setBluetoothChangeListener(this);
			bluetoothWrapper.register();
		}
		if (audioFocusWrapper == null) {
			audioFocusWrapper = AudioFocusWrapper.getInstance();
			audioFocusWrapper.init(service, audioManager);
		}
		modeSipInCall = service.getPrefs().getInCallMode();
		useSgsWrkAround = service.getPrefs().getPreferenceBooleanValue(
				SipConfigManager.USE_SGS_CALL_HACK);
		useWebRTCImpl = service.getPrefs().getPreferenceBooleanValue(
				SipConfigManager.USE_WEBRTC_HACK);
		doFocusAudio = service.getPrefs().getPreferenceBooleanValue(
				SipConfigManager.DO_FOCUS_AUDIO);
		userWantBluetooth = service.getPrefs().getPreferenceBooleanValue(
				SipConfigManager.AUTO_CONNECT_BLUETOOTH);
		userWantSpeaker = service.getPrefs().getPreferenceBooleanValue(
				SipConfigManager.AUTO_CONNECT_SPEAKER);
		restartAudioWhenRoutingChange = service.getPrefs()
				.getPreferenceBooleanValue(
						SipConfigManager.RESTART_AUDIO_ON_ROUTING_CHANGES);
		startBeforeInit = service.getPrefs().getPreferenceBooleanValue(
				SipConfigManager.SETUP_AUDIO_BEFORE_INIT);
	}

	public void stopService() {
		Log.i(THIS_FILE, "Remove media manager....");
		if (bluetoothWrapper != null) {
/*			bluetoothWrapper.unregister();
			bluetoothWrapper.setBluetoothChangeListener(null);
*/			bluetoothWrapper = null;
		}
	}

	/**
	 * Stop call announcement.
	 */
	public void stopRingAndUnfocus() {
		stopRing();
		audioFocusWrapper.unFocus();
	}

	/**
	 * Stop all ringing. <br/>
	 * Warning, this will not unfocus audio.
	 */
	synchronized public void stopRing() {
		if (ringer.isRinging()) {
			ringer.stopRing();
		}
	}

	public void resetSettings() {
		userWantBluetooth = service.getPrefs().getPreferenceBooleanValue(
				SipConfigManager.AUTO_CONNECT_BLUETOOTH);
		userWantSpeaker = service.getPrefs().getPreferenceBooleanValue(
				SipConfigManager.AUTO_CONNECT_SPEAKER);
		userWantMicrophoneMute = false;
	}

	/**
	 * @param defaultRate
	 * @return
	 */
	public long getBestSampleRate(long defaultRate) {
		if (Compatibility
				.isCompatible(android.os.Build.VERSION_CODES.JELLY_BEAN_MR1)) {

			String sampleRateString = audioFocusWrapper
					.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
			return (sampleRateString == null ? defaultRate : Integer
					.parseInt(sampleRateString));
		} else {
			return defaultRate;
		}
	}

	public void setStreamVolume(int streamType, int index, int flags) {
		int inCallStream = Compatibility.getInCallStream(userWantBluetooth);
		int max = audioManager.getStreamMaxVolume(inCallStream);
		if (index < 0) {
			index = 0;
		} else if (index > max) {
			index = max;
		}
		broadcastVolumeWillBeUpdated(streamType, index);
		audioManager.setStreamVolume(streamType, index, flags);
		if (streamType == AudioManager.STREAM_RING) {
			// Update ringer
			ringer.updateRingerMode();
		}
		if (streamType == inCallStream) {
			int maxLevel = audioManager.getStreamMaxVolume(inCallStream);
			float modifiedLevel = (audioManager.getStreamVolume(inCallStream) / (float) maxLevel) * 10.0f;
			// Update default stream level
			service.getPrefs().setPreferenceFloatValue(
					SipConfigManager.SND_STREAM_LEVEL, modifiedLevel);
		}

	}

	public void adjustStreamVolume(int streamType, int direction, int flags) {
		broadcastVolumeWillBeUpdated(streamType, EXTRA_VALUE_UNKNOWN);
		Log.d("henry","streamType = " + streamType + "direction = " + direction);
		Log.d("henry","getStreamVolume = " + audioManager.getStreamVolume(streamType) + "direction = " + direction);

		
		audioManager.adjustStreamVolume(streamType, direction, flags);
		if (streamType == AudioManager.STREAM_RING) {
			// Update ringer
			ringer.updateRingerMode();
		}

		int inCallStream = Compatibility.getInCallStream(userWantBluetooth);
		if (streamType == inCallStream) {
			int maxLevel = audioManager.getStreamMaxVolume(inCallStream);	
			float modifiedLevel = (audioManager.getStreamVolume(inCallStream) / (float) maxLevel) * 10.0f;
			// Update default stream level
			service.getPrefs().setPreferenceFloatValue(
					SipConfigManager.SND_STREAM_LEVEL, modifiedLevel);
		}
	}

	public void setSpeakerphoneOn(boolean on) throws SameThreadException {
		if (service != null && restartAudioWhenRoutingChange
				&& !ringer.isRinging()) {
			service.setNoSnd();
			userWantSpeaker = on;
			service.setSnd();
		} else {
			userWantSpeaker = on;
			audioManager.setSpeakerphoneOn(on);
		}
		broadcastMediaChanged();
	}

	public MediaState getMediaState() {
		MediaState mediaState = new MediaState();
		// Micro
		mediaState.setMicrophoneMute(userWantMicrophoneMute);
		mediaState.setCanMicrophoneMute(true); 

		// Speaker
		mediaState.setSpeakerphoneOn(userWantSpeaker);
		mediaState.setCanSpeakerphoneOn(true && !mediaState.isBluetoothScoOn());// Compatibility.isCompatible(5);
		// Bluetooth

		if (bluetoothWrapper != null) {
			mediaState.setBluetoothScoOn(bluetoothWrapper.isBluetoothOn());
			mediaState.setCanBluetoothSco(bluetoothWrapper.canBluetooth());
		} else {
			mediaState.setBluetoothScoOn(false);
			mediaState.setCanBluetoothSco(false);
		}
		return mediaState;
	}

	public boolean doesUserWantBluetooth() {
		return userWantBluetooth;
	}

	public void toggleMute() throws SameThreadException {
		setMicrophoneMute(!userWantMicrophoneMute);
	}

	public int validateAudioClockRate(int clockRate) {
		if (bluetoothWrapper != null && clockRate != 8000) {
			if (userWantBluetooth && bluetoothWrapper.canBluetooth()) {
				return -1;
			}
		}
		return 0;
	}

	public void setAudioInCall(boolean beforeInit) {
		if (!beforeInit || (beforeInit && startBeforeInit)) {
			actualSetAudioInCall();
		}
	}

	public void unsetAudioInCall() {
		actualUnsetAudioInCall();
	}

	/**
	 * Reset the audio mode
	 */
	private synchronized void actualUnsetAudioInCall() {

		if (!prefs.getBoolean("isSavedAudioState", false) || !isSetAudioMode) {
			return;
		}
		Log.d(THIS_FILE, "Unset Audio In call");
		int inCallStream = Compatibility.getInCallStream(userWantBluetooth);
		if (bluetoothWrapper != null) {
			// This fixes the BT activation but... but... seems to introduce a
			// lot of other issues
			// bluetoothWrapper.setBluetoothOn(true);
			Log.d(THIS_FILE, "Unset bt");
			bluetoothWrapper.setBluetoothOn(false);
		}
		audioManager.setMicrophoneMute(false);
		if (doFocusAudio) {
			audioManager.setStreamSolo(inCallStream, false);
			audioFocusWrapper.unFocus();
		}
		restoreAudioState();

		if (wifiLock != null && wifiLock.isHeld()) {
			wifiLock.release();
		}
		if (screenLock != null && screenLock.isHeld()) {
			Log.d(THIS_FILE, "Release screen lock");
			screenLock.release();
		}

		isSetAudioMode = false;
	}

	/**
	 * Set the audio mode as in call
	 */
	@SuppressWarnings("deprecation")
	private synchronized void actualSetAudioInCall() {
		// Ensure not already set
		if (isSetAudioMode) {
			return;
		}
		stopRing();
		saveAudioState();

		// Set the rest of the phone in a better state to not interferate with
		// current call
		// Do that only if we were not already in silent mode
		/*
		 * Not needed anymore with on flight gsm call capture
		 * if(audioManager.getRingerMode() != AudioManager.RINGER_MODE_SILENT) {
		 * audioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
		 * AudioManager.VIBRATE_SETTING_ON);
		 * audioManager.setVibrateSetting(AudioManager
		 * .VIBRATE_TYPE_NOTIFICATION, AudioManager.VIBRATE_SETTING_OFF);
		 * audioManager.setRingerMode(AudioManager.RINGER_MODE_VIBRATE); }
		 */

		// LOCKS

		// Wifi management if necessary
		ContentResolver ctntResolver = service.getContentResolver();
		Compatibility.setWifiSleepPolicy(ctntResolver,
				Compatibility.getWifiSleepPolicyNever());

		// Acquire wifi lock
		WifiManager wman = (WifiManager) service
				.getSystemService(Context.WIFI_SERVICE);
		if (wifiLock == null) {
			wifiLock = wman
					.createWifiLock(
							(Compatibility.isCompatible(9)) ? WifiManager.WIFI_MODE_FULL_HIGH_PERF
									: WifiManager.WIFI_MODE_FULL,
							"com.csipsimple.InCallLock");
			wifiLock.setReferenceCounted(false);
		}
		WifiInfo winfo = wman.getConnectionInfo();
		if (winfo != null) {
			DetailedState dstate = WifiInfo.getDetailedStateOf(winfo
					.getSupplicantState());
			// We assume that if obtaining ip addr, we are almost connected so
			// can keep wifi lock
			if (dstate == DetailedState.OBTAINING_IPADDR
					|| dstate == DetailedState.CONNECTED) {
				if (!wifiLock.isHeld()) {
					wifiLock.acquire();
				}
			}

			// This wake lock purpose is to prevent PSP wifi mode
			if (service.getPrefs().getPreferenceBooleanValue(
					SipConfigManager.KEEP_AWAKE_IN_CALL)) {
				if (screenLock == null) {
					PowerManager pm = (PowerManager) service
							.getSystemService(Context.POWER_SERVICE);
					screenLock = pm.newWakeLock(
							PowerManager.SCREEN_DIM_WAKE_LOCK
									| PowerManager.ON_AFTER_RELEASE,
							"com.csipsimple.onIncomingCall.SCREEN");
					screenLock.setReferenceCounted(false);
				}
				// Ensure single lock
				if (!screenLock.isHeld()) {
					screenLock.acquire();

				}

			}
		}

		if (!useWebRTCImpl) {
			// Audio routing
			int targetMode = getAudioTargetMode();
			Log.d(THIS_FILE, "Set mode audio in call to " + targetMode);

			if (service.getPrefs().generateForSetCall()) {
				boolean needOutOfSilent = (audioManager.getRingerMode() == AudioManager.RINGER_MODE_SILENT);
				if (needOutOfSilent) {
					audioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
				}
				ToneGenerator toneGenerator = new ToneGenerator(
						AudioManager.STREAM_VOICE_CALL, 1);
				toneGenerator
						.startTone(41 /* ToneGenerator.TONE_CDMA_CONFIRM */);
				toneGenerator.stopTone();
				toneGenerator.release();
				// Restore silent mode
				if (needOutOfSilent) {
					audioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
				}
			}

			// Set mode
			if (targetMode != AudioManager.MODE_IN_CALL && useSgsWrkAround) {
				// For galaxy S we need to set in call mode before to reset
				// stack
				audioManager.setMode(AudioManager.MODE_IN_CALL);
			}

			audioManager.setMode(targetMode);

			// Routing
			if (service.getPrefs().useRoutingApi()) {
				audioManager.setRouting(targetMode,
						userWantSpeaker ? AudioManager.ROUTE_SPEAKER
								: AudioManager.ROUTE_EARPIECE,
						AudioManager.ROUTE_ALL);
			} else {
				audioManager.setSpeakerphoneOn(userWantSpeaker ? true : false);
			}

			audioManager.setMicrophoneMute(false);
			if (bluetoothWrapper != null && userWantBluetooth
					&& bluetoothWrapper.canBluetooth()) {
				Log.d(THIS_FILE, "Try to enable bluetooth");
				bluetoothWrapper.setBluetoothOn(true);
			}

		} else {
			// WebRTC implementation for routing
			int apiLevel = Compatibility.getApiLevel();

			// SetAudioMode
			// ***IMPORTANT*** When the API level for honeycomb (H) has been
			// decided,
			// the condition should be changed to include API level 8 to H-1.
			if (android.os.Build.BRAND.equalsIgnoreCase("Samsung")
					&& (8 == apiLevel)) {
				// Set Samsung specific VoIP mode for 2.2 devices
				int mode = 4;
				audioManager.setMode(mode);
				if (audioManager.getMode() != mode) {
					Log.e(THIS_FILE,
							"Could not set audio mode for Samsung device");
				}
			}

			// SetPlayoutSpeaker
			if ((3 == apiLevel) || (4 == apiLevel)) {
				// 1.5 and 1.6 devices
				if (userWantSpeaker) {
					// route audio to back speaker
					audioManager.setMode(AudioManager.MODE_NORMAL);
				} else {
					// route audio to earpiece
					audioManager.setMode(AudioManager.MODE_IN_CALL);
				}
			} else {
				// 2.x devices
				if ((android.os.Build.BRAND.equalsIgnoreCase("samsung"))
						&& ((5 == apiLevel) || (6 == apiLevel) || (7 == apiLevel))) {
					// Samsung 2.0, 2.0.1 and 2.1 devices
					if (userWantSpeaker) {
						// route audio to back speaker
						audioManager.setMode(AudioManager.MODE_IN_CALL);
						audioManager.setSpeakerphoneOn(userWantSpeaker);
					} else {
						// route audio to earpiece
						audioManager.setSpeakerphoneOn(userWantSpeaker);
						audioManager.setMode(AudioManager.MODE_NORMAL);
					}
				} else {
					// Non-Samsung and Samsung 2.2 and up devices
					audioManager.setSpeakerphoneOn(userWantSpeaker);
				}
			}

		}

		// Set stream solo/volume/focus
		int inCallStream = Compatibility.getInCallStream(userWantBluetooth);
		if (doFocusAudio) {
			if (!accessibilityManager.isEnabled()) {
				audioManager.setStreamSolo(inCallStream, true);
			}
			audioFocusWrapper.focus(userWantBluetooth);
		}
		Log.d(THIS_FILE, "Initial volume level : "
				+ service.getPrefs().getInitialVolumeLevel());
		setStreamVolume(inCallStream,
				(int) (audioManager.getStreamMaxVolume(inCallStream)), 0);

		isSetAudioMode = true;
		// System.gc();
	}

	/**
	 * Save current audio mode in order to be able to restore it once done
	 */
	@SuppressWarnings("deprecation")
	private synchronized void saveAudioState() {
		if (prefs.getBoolean("isSavedAudioState", false)) {
			// If we have already set, do not set it again !!!
			return;
		}
		ContentResolver ctntResolver = service.getContentResolver();

		Editor ed = prefs.edit();
		// ed.putInt("savedVibrateRing",
		// audioManager.getVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER));
		// ed.putInt("savedVibradeNotif",
		// audioManager.getVibrateSetting(AudioManager.VIBRATE_TYPE_NOTIFICATION));
		// ed.putInt("savedRingerMode", audioManager.getRingerMode());
		ed.putInt("savedWifiPolicy",
				Compatibility.getWifiSleepPolicy(ctntResolver));

		int inCallStream = Compatibility.getInCallStream(userWantBluetooth);
		ed.putInt("savedVolume", audioManager.getStreamVolume(inCallStream));

		int targetMode = getAudioTargetMode();
		if (service.getPrefs().useRoutingApi()) {
			ed.putInt("savedRoute", audioManager.getRouting(targetMode));
		} else {
			ed.putBoolean("savedSpeakerPhone", audioManager.isSpeakerphoneOn());
		}
		ed.putInt("savedMode", audioManager.getMode());

		ed.putBoolean("isSavedAudioState", true);
		ed.commit();
	}

	public void setMicrophoneMute(boolean on) {
		if (on != userWantMicrophoneMute) {
			userWantMicrophoneMute = on;
			setSoftwareVolume();
			broadcastMediaChanged();
		}
	}

	/**
	 * Change the audio volume amplification according to the fact we are using
	 * bluetooth
	 */
	public void setSoftwareVolume() {

		if (service != null) {
			final boolean useBT = (bluetoothWrapper != null && bluetoothWrapper
					.isBluetoothOn());

			String speaker_key = useBT ? SipConfigManager.SND_BT_SPEAKER_LEVEL
					: SipConfigManager.SND_SPEAKER_LEVEL;
			String mic_key = useBT ? SipConfigManager.SND_BT_MIC_LEVEL
					: SipConfigManager.SND_MIC_LEVEL;

			final float speakVolume = service.getPrefs()
					.getPreferenceFloatValue(speaker_key);
			final float micVolume = userWantMicrophoneMute ? 0 : service
					.getPrefs().getPreferenceFloatValue(mic_key);

			service.getExecutor().execute(new SipRunnable() {
				@Override
				protected void doRun() throws SameThreadException {
					service.confAdjustTxLevel(speakVolume);
					service.confAdjustRxLevel(micVolume);
					// Force the BT mode to normal
					if (useBT) {
						audioManager.setMode(AudioManager.MODE_NORMAL);
					}
				}
			});
		}
	}

	public void broadcastMediaChanged() {
		service.sendBroadcast(mediaStateChangedIntent,
				SipManager.PERMISSION_USE_SIP);
	}

}
