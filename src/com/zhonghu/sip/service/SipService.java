package com.zhonghu.sip.service;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo.DetailedState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Parcelable;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.zhonghu.sip.api.AccountListManager;
import com.zhonghu.sip.api.ICallback;
import com.zhonghu.sip.api.ISipService;
import com.zhonghu.sip.api.MediaState;
import com.zhonghu.sip.api.SipCallSession;
import com.zhonghu.sip.api.SipConfigManager;
import com.zhonghu.sip.api.SipManager;
import com.zhonghu.sip.api.SipManager.PresenceStatus;
import com.zhonghu.sip.api.SipProfile;
import com.zhonghu.sip.api.SipProfileState;
import com.zhonghu.sip.pjsip.PjSipService;
import com.zhonghu.sip.pjsip.SameThreadException;
import com.zhonghu.sip.pjsip.UAStateReceiver;
import com.zhonghu.sip.pref.PreferencesProviderWrapper;
import com.zhonghu.sip.pref.PreferencesWrapper;
import com.zhonghu.sip.receiver.DynamicReceiver4;
import com.zhonghu.sip.receiver.DynamicReceiver5;
import com.zhonghu.sip.utils.Compatibility;
import com.zhonghu.sip.utils.CustomDistribution;
import com.zhonghu.sip.utils.ExtraPlugins;
import com.zhonghu.sip.utils.ExtraPlugins.DynActivityPlugin;

public class SipService extends Service {

	private static final String TAG = "SIP SipService";
	private PreferencesProviderWrapper prefsWrapper;
	public boolean supportMultipleCalls = false;

	private SipWakeLock sipWakeLock;
	private boolean autoAcceptCurrent = false;

	private WakeLock wakeLock;
	private WifiLock wifiLock;
	private DynamicReceiver4 deviceStateReceiver;
	private ServicePhoneStateReceiver phoneConnectivityReceiver;
	private TelephonyManager telephonyManager;
	// private ConnectivityManager connectivityManager;

	private SipServiceExecutor mExecutor;
	private static PjSipService pjService;
	private static HandlerThread executorThread;

	// public PresenceManager presenceMgr;
	private BroadcastReceiver serviceReceiver;

	private RemoteCallbackList<ICallback> mCallbacks = new RemoteCallbackList<ICallback>();

	private class ServicePhoneStateReceiver extends PhoneStateListener {

		// private boolean ignoreFirstConnectionState = true;
		private boolean ignoreFirstCallState = true;

		@Override
		public void onCallStateChanged(final int state,
				final String incomingNumber) {
			if (!ignoreFirstCallState) {
				Log.d(TAG, "Call state has changed !" + state + " : "
						+ incomingNumber);
				getExecutor().execute(new SipRunnable() {

					@Override
					protected void doRun() throws SameThreadException {
						if (pjService != null) {
							pjService.onGSMStateChanged(state, incomingNumber);
						}
					}
				});
			} else {
				ignoreFirstCallState = false;
			}
			super.onCallStateChanged(state, incomingNumber);
		}
	}

	// Executes immediate tasks in a single executorThread.
	// Hold/release wake lock for running tasks
	public static class SipServiceExecutor extends Handler {
		WeakReference<SipService> handlerService;

		SipServiceExecutor(SipService s) {
			super(createLooper());
			handlerService = new WeakReference<SipService>(s);
		}

		public void execute(Runnable task) {
			SipService s = handlerService.get();
			if (s != null) {
				s.sipWakeLock.acquire(task);
			}
			Message.obtain(this, 0/* don't care */, task).sendToTarget();
		}

		@Override
		public void handleMessage(Message msg) {
			if (msg.obj instanceof Runnable) {
				executeInternal((Runnable) msg.obj);
			} else {
				Log.w(TAG, "can't handle msg: " + msg);
			}
		}

		private void executeInternal(Runnable task) {
			try {
				task.run();
			} catch (Throwable t) {
				Log.e(TAG, "run task: " + task, t);
			} finally {
				SipService s = handlerService.get();
				if (s != null) {
					s.sipWakeLock.release(task);
				}
			}
		}
	}

	// Auto answer feature

	public void setAutoAnswerNext(boolean auto_response) {
		autoAcceptCurrent = auto_response;
	}

	public SipServiceExecutor getExecutor() {
		// create mExecutor lazily
		if (mExecutor == null) {
			mExecutor = new SipServiceExecutor(this);
		}
		return mExecutor;
	}

	private static Looper createLooper() {
		// synchronized (executorThread) {
		if (executorThread == null) {
			Log.d(TAG, "Creating new handler thread");
			// ADT gives a fake warning due to bad parse rule.
			executorThread = new HandlerThread("SipService.Executor");
			executorThread.start();
		}
		// }
		return executorThread.getLooper();
	}

	class StartRunnable extends SipRunnable {
		@Override
		protected void doRun() throws SameThreadException {
			startSipStack();
		}
	}

	class StopRunnable extends SipRunnable {
		@Override
		protected void doRun() throws SameThreadException {
			stopSipStack();
		}
	}

	class SyncStopRunnable extends ReturnRunnable {
		@Override
		protected Object runWithReturn() throws SameThreadException {
			stopSipStack();
			return null;
		}
	}

	class RestartRunnable extends SipRunnable {
		@Override
		protected void doRun() throws SameThreadException {
			if (stopSipStack()) {
				startSipStack();
			} else {
				Log.e(TAG, "Can't stop ... so do not restart ! ");
			}
		}
	}

	class SyncRestartRunnable extends ReturnRunnable {
		@Override
		protected Object runWithReturn() throws SameThreadException {
			if (stopSipStack()) {
				startSipStack();
			} else {
				Log.e(TAG, "Can't stop ... so do not restart ! ");
			}
			return null;
		}
	}

	class DestroyRunnable extends SipRunnable {
		@Override
		protected void doRun() throws SameThreadException {
			if (stopSipStack()) {
				stopSelf();
			}
		}
	}

	class FinalizeDestroyRunnable extends SipRunnable {
		@Override
		protected void doRun() throws SameThreadException {

			mExecutor = null;

			Log.d(TAG, "Destroy sip stack");

			sipWakeLock.reset();

			if (stopSipStack()) {
			} else {
				Log.e(TAG,
						"Somebody has stopped the service while there is an ongoing call !!!");
			}

			Log.i(TAG, "--- SIP SERVICE DESTROYED ---");
		}
	}

	public abstract static class SipRunnable implements Runnable {
		protected abstract void doRun() throws SameThreadException;

		public void run() {
			try {
				doRun();
			} catch (SameThreadException e) {
				Log.e(TAG, "Not done from same thread");
			}
		}
	}

	/**
	 * Register broadcast receivers.
	 */
	private void registerBroadcasts() {
		// Register own broadcast receiver
		if (deviceStateReceiver == null) {
			IntentFilter intentfilter = new IntentFilter();
			intentfilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
			intentfilter.addAction(SipManager.ACTION_SIP_ACCOUNT_CHANGED);
			intentfilter.addAction(SipManager.ACTION_SIP_ACCOUNT_DELETED);
			intentfilter.addAction(SipManager.ACTION_SIP_CAN_BE_STOPPED);
			intentfilter.addAction(SipManager.ACTION_SIP_REQUEST_RESTART);
			intentfilter.addAction(DynamicReceiver4.ACTION_VPN_CONNECTIVITY);
			if (Compatibility.isCompatible(5)) {
				deviceStateReceiver = new DynamicReceiver5(this);
			} else {
				deviceStateReceiver = new DynamicReceiver4(this);
			}
			registerReceiver(deviceStateReceiver, intentfilter);
			deviceStateReceiver.startMonitoring();
		}
		// Telephony
		if (phoneConnectivityReceiver == null) {
			Log.d(TAG, "Listen for phone state ");
			phoneConnectivityReceiver = new ServicePhoneStateReceiver();

			telephonyManager.listen(phoneConnectivityReceiver, /*
																 * PhoneStateListener
																 * .
																 * LISTEN_DATA_CONNECTION_STATE
																 * |
																 */
					PhoneStateListener.LISTEN_CALL_STATE);
		}
		// // Content observer
		// if(statusObserver == null) {
		// statusObserver = new AccountStatusContentObserver(serviceHandler);
		// getContentResolver().registerContentObserver(SipProfile.ACCOUNT_STATUS_URI,
		// true, statusObserver);
		// }

	}

	/**
	 * Remove registration of broadcasts receiver.
	 */
	private void unregisterBroadcasts() {
		if (deviceStateReceiver != null) {
			try {
				Log.d(TAG, "Stop and unregister device receiver");
				deviceStateReceiver.stopMonitoring();
				unregisterReceiver(deviceStateReceiver);
				deviceStateReceiver = null;
			} catch (IllegalArgumentException e) {
				// This is the case if already unregistered itself
				// Python style usage of try ;) : nothing to do here since it
				// could
				// be a standard case
				// And in this case nothing has to be done
				Log.d(TAG, "Has not to unregister telephony receiver");
			}
		}
		if (phoneConnectivityReceiver != null) {
			Log.d(TAG, "Unregister telephony receiver");
			telephonyManager.listen(phoneConnectivityReceiver,
					PhoneStateListener.LISTEN_NONE);
			phoneConnectivityReceiver = null;
		}
		// if(statusObserver != null) {
		// getContentResolver().unregisterContentObserver(statusObserver);
		// statusObserver = null;
		// }

	}

	private void unregisterServiceBroadcasts() {
		if (serviceReceiver != null) {
			unregisterReceiver(serviceReceiver);
			serviceReceiver = null;
		}
	}

	private void registerServiceBroadcasts() {
		if (serviceReceiver == null) {
			IntentFilter intentfilter = new IntentFilter();
			intentfilter.addAction(SipManager.ACTION_DEFER_OUTGOING_UNREGISTER);
			intentfilter.addAction(SipManager.ACTION_OUTGOING_UNREGISTER);
			serviceReceiver = new BroadcastReceiver() {
				@Override
				public void onReceive(Context context, Intent intent) {
					String action = intent.getAction();
					if (action.equals(SipManager.ACTION_OUTGOING_UNREGISTER)) {
						// unregisterForOutgoing((ComponentName) intent
						// .getParcelableExtra(SipManager.EXTRA_OUTGOING_ACTIVITY));
					} else if (action
							.equals(SipManager.ACTION_DEFER_OUTGOING_UNREGISTER)) {
						// deferUnregisterForOutgoing((ComponentName) intent
						// .getParcelableExtra(SipManager.EXTRA_OUTGOING_ACTIVITY));
					}
				}

			};
			registerReceiver(serviceReceiver, intentfilter);
		}
	}

	public static final class ToCall {
		private Integer pjsipAccountId;
		private String callee;
		private String dtmf;

		public ToCall(Integer acc, String uri) {
			pjsipAccountId = acc;
			callee = uri;
		}

		public ToCall(Integer acc, String uri, String dtmfChars) {
			pjsipAccountId = acc;
			callee = uri;
			dtmf = dtmfChars;
		}

		/**
		 * @return the pjsipAccountId
		 */
		public Integer getPjsipAccountId() {
			return pjsipAccountId;
		}

		/**
		 * @return the callee
		 */
		public String getCallee() {
			return callee;
		}

		/**
		 * @return the dtmf sequence to automatically dial for this call
		 */
		public String getDtmf() {
			return dtmf;
		}
	};

	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		String serviceName = intent.getAction();
		Log.d(TAG, "Action is " + serviceName);
		if (serviceName == null
				|| serviceName.equalsIgnoreCase(SipManager.INTENT_SIP_SERVICE)) {
			Log.d(TAG, "Service returned");
			return binder;
		} /*
		 * else if
		 * (serviceName.equalsIgnoreCase(SipManager.INTENT_SIP_CONFIGURATION)) {
		 * Log.d(TAG, "Conf returned"); return binderConfiguration; }
		 */
		Log.d(TAG, "Default service (SipService) returned");
		return binder;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		Log.d(TAG, "SipService onCreate");
		prefsWrapper = new PreferencesProviderWrapper(this);
		telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		// connectivityManager = (ConnectivityManager)
		// getSystemService(Context.CONNECTIVITY_SERVICE);
		// notificationManager = new SipNotifications(this);
		// notificationManager.onServiceCreate();
		sipWakeLock = new SipWakeLock(
				(PowerManager) getSystemService(Context.POWER_SERVICE));

		boolean hasSetup = prefsWrapper.getPreferenceBooleanValue(
				PreferencesProviderWrapper.HAS_ALREADY_SETUP_SERVICE, false);
		Log.d(TAG, "Service has been setup ? " + hasSetup);

		// presenceMgr = new PresenceManager();
		registerServiceBroadcasts();

		if (!hasSetup) {
			Log.e(TAG, "RESET SETTINGS !!!!");
			prefsWrapper.resetAllDefaultValues();
		}

	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.i(TAG, "Destroying SIP Service");
		getExecutor().execute(new FinalizeDestroyRunnable());
		unregisterBroadcasts();
		unregisterServiceBroadcasts();
	}

	@Override
	@Deprecated
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
		Log.d(TAG, "onStart");

		// Check connectivity, else just finish itself
		if (!isConnectivityValid()) {
			// notifyUserOfMessage(R.string.connection_not_valid);
			Log.d(TAG, "Harakiri... we are not needed since no way to use self");
			cleanStop();
			return;
		}

		// Autostart the stack - make sure started and that receivers are there
		// NOTE : the stack may also be autostarted cause of
		// phoneConnectivityReceiver
		if (!loadStack()) {
			return;
		}

		if (intent != null) {
			Parcelable outActivityParcel = intent
					.getParcelableExtra(SipManager.EXTRA_OUTGOING_ACTIVITY);
			if (outActivityParcel != null) {
				ComponentName outActivity = (ComponentName) outActivityParcel;
				registerForOutgoing(outActivity);
			}
		}

		// if(directConnect) {
		Log.d(TAG, "Direct sip start");
		getExecutor().execute(new StartRunnable());
	}

	public void restartSipStack() throws SameThreadException {
		Log.d(TAG, "restartSipStack");
		if (stopSipStack()) {
			startSipStack();
		} else {
			Log.e(TAG, "Can't stop ... so do not restart ! ");
		}
	}

	// private KeepAliveTimer kaAlarm;
	// This is always done in SipExecutor thread
	private void startSipStack() throws SameThreadException {
		// Cache some prefs

		if (!isConnectivityValid()) {
			Log.e(TAG, "No need to start sip");
			return;
		}
		Log.d(TAG, "Start was asked and we should actually start now");
		if (pjService == null) {
			Log.d(TAG, "Start was asked and pjService in not there");
			if (!loadStack()) {
				Log.e(TAG, "Unable to load SIP stack !! ");
				return;
			}
		}
		Log.d(TAG, "Ask pjservice to start itself");

		// presenceMgr.startMonitoring(this);
		if (pjService.sipStart()) {
			// This should be done after in acquire resource
			// But due to
			// http://code.google.com/p/android/issues/detail?id=21635
			// not a good idea
			applyComponentEnablingState(true);
			registerBroadcasts();
			Log.d(TAG, "Add all accounts");
			addAllAccounts();
		}
	}

	/**
	 * Safe stop the sip stack
	 * 
	 * @return true if can be stopped, false if there is a pending call and the
	 *         sip service should not be stopped
	 */
	public boolean stopSipStack() throws SameThreadException {
		Log.d(TAG, "Stop sip stack");
		boolean canStop = true;
		if (pjService != null) {
			canStop &= pjService.sipStop();
			/*
			 * if(canStop) { pjService = null; }
			 */
		}
		if (canStop) {
			// if(presenceMgr != null) {
			// presenceMgr.stopMonitoring();
			// }

			// Due to http://code.google.com/p/android/issues/detail?id=21635
			// exclude 14 and upper from auto disabling on stop.
			if (!Compatibility.isCompatible(14)) {
				applyComponentEnablingState(false);
			}

			unregisterBroadcasts();
			releaseResources();
		}

		return canStop;
	}

	private boolean hasSomeActiveAccount = false;

	/**
	 * Add accounts from database
	 */
	private void addAllAccounts() throws SameThreadException {
		Log.d(TAG, "We are adding all accounts right now....");
		boolean hasSomeSuccess = false;

		List<SipProfile> accountList = AccountListManager.getInstance()
				.getAllSipProfile();

		if (accountList == null || accountList.size() < 1) {
			return;
		}
		for (SipProfile profile : accountList) {
			SipProfileState status = AccountListManager.getInstance()
					.getProfileState(profile.id);
			if (status != null) {
				return;
			}
			if (profile != null && profile.id != SipProfile.INVALID_ID) {
				if (pjService != null && pjService.addAccount(profile)) {
					hasSomeSuccess = true;
				}
			}
		}
		hasSomeActiveAccount = hasSomeSuccess;
		if (hasSomeSuccess) {
			acquireResources();
		} else {
			releaseResources();
		}
	}

	public boolean setAccountRegistration(SipProfile account, int renew,
			boolean forceReAdd) throws SameThreadException {
		boolean status = false;
		if (pjService != null) {
			status = pjService.setAccountRegistration(account, renew,
					forceReAdd);
		}

		return status;
	}

	/**
	 * Remove accounts from database
	 */
	private void unregisterAllAccounts(boolean cancelNotification)
			throws SameThreadException {

		releaseResources();

		Log.d(TAG, "Remove all accounts");

		List<SipProfile> accountList = AccountListManager.getInstance()
				.getAllSipProfile();
		if (accountList == null || accountList.size() < 1) {
			return;
		}
		for (SipProfile profile : accountList) {
			if (profile != null && profile.id != SipProfile.INVALID_ID) {
				setAccountRegistration(profile, 0, false);
			}
		}
	}

	private void reAddAllAccounts() throws SameThreadException {
		Log.d(TAG, "RE REGISTER ALL ACCOUNTS");
		unregisterAllAccounts(true);
		addAllAccounts();
	}

	public SipProfileState getSipProfileState(int accountDbId) {
		final SipProfile acc = getAccount(accountDbId);
		if (pjService != null && acc != null) {
			return pjService.getProfileState(acc);
		}
		return null;
	}

	public void updateRegistrationsState() {
		Log.d(TAG, "Update registration state");
		ArrayList<SipProfileState> activeProfilesState = new ArrayList<SipProfileState>();

		List<SipProfileState> profileStateList = AccountListManager
				.getInstance().getAllProfileState();

		for (SipProfileState ps : profileStateList) {
			if (ps.isValidForCall()) {
				activeProfilesState.add(ps);
			}
		}
		Collections.sort(activeProfilesState, SipProfileState.getComparator());

		// Handle status bar notification
		if (activeProfilesState.size() > 0
				&& prefsWrapper.getPreferenceBooleanValue(
						SipConfigManager.ICON_IN_STATUS_BAR, true)) {
			// Testing memory / CPU leak as per issue 676
			// for(int i=0; i < 10; i++) {
			// Log.d(THIS_FILE, "Notify ...");
			/*
			 * notificationManager .notifyRegisteredAccounts(
			 * activeProfilesState, prefsWrapper
			 * .getPreferenceBooleanValue(SipConfigManager
			 * .ICON_IN_STATUS_BAR_NBR));
			 */// try {
				// Thread.sleep(6000);
				// } catch (InterruptedException e) {
				// e.printStackTrace();
				// }
				// }
		} /*
		 * else { // notificationManager.cancelRegisters(); }
		 */

		if (hasSomeActiveAccount) {
			acquireResources();
		} else {
			releaseResources();
		}
	}

	public SipProfile getAccount(long accountId) {
		// TODO : create cache at this point to not requery each time as far as
		// it's a service query
		return AccountListManager.getInstance().getSipProfile(accountId);
	}

	private boolean loadStack() {
		// Ensure pjService exists
		if (pjService == null) {
			pjService = new PjSipService();
		}
		pjService.setService(this);

		if (pjService.tryToLoadStack()) {
			return true;
		}
		return false;
	}

	private boolean holdResources = false;

	/**
	 * Ask to take the control of the wifi and the partial wake lock if
	 * configured
	 */
	private synchronized void acquireResources() {
		if (holdResources) {
			return;
		}

		// Add a wake lock for CPU if necessary
		if (prefsWrapper
				.getPreferenceBooleanValue(SipConfigManager.USE_PARTIAL_WAKE_LOCK)) {
			PowerManager pman = (PowerManager) getSystemService(Context.POWER_SERVICE);
			if (wakeLock == null) {
				wakeLock = pman.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
						"com.zhonghu.sip.service.SipService");
				wakeLock.setReferenceCounted(false);
			}
			// Extra check if set reference counted is false ???
			if (!wakeLock.isHeld()) {
				wakeLock.acquire();
			}
		}

		// Add a lock for WIFI if necessary
		WifiManager wman = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		if (wifiLock == null) {
			int mode = WifiManager.WIFI_MODE_FULL;
			if (Compatibility.isCompatible(9)
					&& prefsWrapper
							.getPreferenceBooleanValue(SipConfigManager.LOCK_WIFI_PERFS)) {
				mode = 0x3; // WIFI_MODE_FULL_HIGH_PERF
			}
			wifiLock = wman.createWifiLock(mode, "com.csipsimple.SipService");
			wifiLock.setReferenceCounted(false);
		}
		if (prefsWrapper.getPreferenceBooleanValue(SipConfigManager.LOCK_WIFI)
				&& !wifiLock.isHeld()) {
			WifiInfo winfo = wman.getConnectionInfo();
			if (winfo != null) {
				DetailedState dstate = WifiInfo.getDetailedStateOf(winfo
						.getSupplicantState());
				// We assume that if obtaining ip addr, we are almost connected
				// so can keep wifi lock
				if (dstate == DetailedState.OBTAINING_IPADDR
						|| dstate == DetailedState.CONNECTED) {
					if (!wifiLock.isHeld()) {
						wifiLock.acquire();
					}
				}
			}
		}
		holdResources = true;
	}

	private synchronized void releaseResources() {
		if (wakeLock != null && wakeLock.isHeld()) {
			wakeLock.release();
		}
		if (wifiLock != null && wifiLock.isHeld()) {
			wifiLock.release();
		}
		holdResources = false;
	}

	public boolean isConnectivityValid() {
		if (prefsWrapper.getPreferenceBooleanValue(
				PreferencesWrapper.HAS_BEEN_QUIT, false)) {
			return false;
		}
		boolean valid = prefsWrapper.isValidConnectionForIncoming();
		// if (activitiesForOutgoing.size() > 0) {
		// valid |= prefsWrapper.isValidConnectionForOutgoing();
		// }
		valid = true;
		return valid;
	}

	private void applyComponentEnablingState(boolean active) {
	}

	/**
	 * Should a current incoming call be answered. A call to this method will
	 * reset internal state
	 * 
	 * @param remContact
	 *            The remote contact to test
	 * @param acc
	 *            The incoming guessed account
	 * @return the sip code to auto-answer with. If > 0 it means that an auto
	 *         answer must be fired
	 */
	public int shouldAutoAnswer(String remContact, SipProfile acc,
			Bundle extraHdr) {

		Log.d(TAG, "Search if should I auto answer for " + remContact);
		int shouldAutoAnswer = 0;

		if (autoAcceptCurrent) {
			Log.d(TAG, "I should auto answer this one !!! ");
			autoAcceptCurrent = false;
			return 200;
		}

		if (acc != null) {
			Pattern p = Pattern
					.compile(
							"^(?:\")?([^<\"]*)(?:\")?[ ]*(?:<)?sip(?:s)?:([^@]*@[^>]*)(?:>)?",
							Pattern.CASE_INSENSITIVE);
			Matcher m = p.matcher(remContact);
			String number = remContact;
			if (m.matches()) {
				number = m.group(2);
			}
			// shouldAutoAnswer = Filter.isAutoAnswerNumber(this, acc.id,
			// number, extraHdr);

		} else {
			Log.e(TAG, "Oupps... that come from an unknown account...");
			// If some user need to auto hangup if comes from unknown account,
			// just needed to add local account and filter on it.
		}
		return shouldAutoAnswer;
	}

	public int getGSMCallState() {
		return telephonyManager.getCallState();
	}

	private List<ComponentName> activitiesForOutgoing = new ArrayList<ComponentName>();
	private List<ComponentName> deferedUnregisterForOutgoing = new ArrayList<ComponentName>();

	public void registerForOutgoing(ComponentName activityKey) {
		if (!activitiesForOutgoing.contains(activityKey)) {
			activitiesForOutgoing.add(activityKey);
		}
	}

	public void unregisterForOutgoing(ComponentName activityKey) {
		activitiesForOutgoing.remove(activityKey);
		if (!isConnectivityValid()) {
			cleanStop();
		}
	}

	public void deferUnregisterForOutgoing(ComponentName activityKey) {
		if (!deferedUnregisterForOutgoing.contains(activityKey)) {
			deferedUnregisterForOutgoing.add(activityKey);
		}
	}

	public void treatDeferUnregistersForOutgoing() {
		for (ComponentName cmp : deferedUnregisterForOutgoing) {
			activitiesForOutgoing.remove(cmp);
		}
		deferedUnregisterForOutgoing.clear();
		if (!isConnectivityValid()) {
			cleanStop();
		}
	}

	public void cleanStop() {
		getExecutor().execute(new DestroyRunnable());
	}

	/**
	 * Get the currently instanciated prefsWrapper (to be used by
	 * UAStateReceiver)
	 * 
	 * @return the preferenceWrapper instanciated
	 */
	public PreferencesProviderWrapper getPrefs() {
		// Is never null when call so ok, just not check...
		return prefsWrapper;
	}

	/**
	 * Notify user from a message the sip stack wants to transmit. For now it
	 * shows a toaster
	 * 
	 * @param msg
	 *            String message to display
	 */
	public void notifyUserOfMessage(String msg) {
	}

	/**
	 * Notify user from a message the sip stack wants to transmit. For now it
	 * shows a toaster
	 * 
	 * @param resStringId
	 *            The id of the string resource to display
	 */
	public void notifyUserOfMessage(int resStringId) {
	}

	public UAStateReceiver getUAStateReceiver() {
		return pjService.userAgentReceiver;
	}

	// Binders for media manager to sip stack
	/**
	 * Adjust tx software sound level
	 * 
	 * @param speakVolume
	 *            volume 0.0 - 1.0
	 */
	public void confAdjustTxLevel(float speakVolume) throws SameThreadException {
		if (pjService != null) {
			pjService.confAdjustTxLevel(0, speakVolume);
		}
	}

	/**
	 * Adjust rx software sound level
	 * 
	 * @param speakVolume
	 *            volume 0.0 - 1.0
	 */
	public void confAdjustRxLevel(float speakVolume) throws SameThreadException {
		if (pjService != null) {
			pjService.confAdjustRxLevel(0, speakVolume);
		}
	}

	private final ISipService.Stub binder = new ISipService.Stub() {

		@Override
		public int getVersion() throws RemoteException {
			return SipManager.CURRENT_API;
		}

		@Override
		public void sipStart() throws RemoteException {
			SipService.this.enforceCallingOrSelfPermission(
					SipManager.PERMISSION_USE_SIP, null);
			Log.d(TAG, "Start required from third party app/serv");
			getExecutor().execute(new StartRunnable());

		}

		@Override
		public void sipStop() throws RemoteException {
			SipService.this.enforceCallingOrSelfPermission(
					SipManager.PERMISSION_USE_SIP, null);
			getExecutor().execute(new StopRunnable());

		}

		@Override
		public void forceStopService() throws RemoteException {
			SipService.this.enforceCallingOrSelfPermission(
					SipManager.PERMISSION_USE_SIP, null);
			Log.d(TAG, "Try to force service stop");
			cleanStop();
			// stopSelf();

		}

		@Override
		public void askThreadedRestart() throws RemoteException {
			SipService.this.enforceCallingOrSelfPermission(
					SipManager.PERMISSION_USE_SIP, null);
			Log.d(TAG, "Restart required from third part app/serv");
			getExecutor().execute(new RestartRunnable());

		}

		@Override
		public void addAllAccounts() throws RemoteException {
			SipService.this.enforceCallingOrSelfPermission(
					SipManager.PERMISSION_USE_SIP, null);
			getExecutor().execute(new SipRunnable() {
				@Override
				public void doRun() throws SameThreadException {
					SipService.this.addAllAccounts();
				}
			});
		}

		@Override
		public void removeAllAccounts() throws RemoteException {
			SipService.this.enforceCallingOrSelfPermission(
					SipManager.PERMISSION_USE_SIP, null);
			getExecutor().execute(new SipRunnable() {
				@Override
				public void doRun() throws SameThreadException {
					SipService.this.unregisterAllAccounts(true);
					AccountListManager.getInstance().removeAllProfiles();
				}
			});

		}

		@Override
		public void reAddAllAccounts() throws RemoteException {
			SipService.this.enforceCallingOrSelfPermission(
					SipManager.PERMISSION_USE_SIP, null);
			getExecutor().execute(new SipRunnable() {
				@Override
				public void doRun() throws SameThreadException {
					SipService.this.reAddAllAccounts();
				}
			});

		}

		@Override
		public void setAccountRegistration(int accountId, int renew)
				throws RemoteException {

			// SipService.this.enforceCallingOrSelfPermission(
			// SipManager.PERMISSION_USE_SIP, null);
			//
			// final SipProfile acc = getAccount(accountId);
			// if (acc != null) {
			// final int ren = renew;
			// getExecutor().execute(new SipRunnable() {
			// @Override
			// public void doRun() throws SameThreadException {
			// SipService.this.setAccountRegistration(acc, ren, false);
			// }
			// });
			// }
			//
		}

		@Override
		public SipProfileState getSipProfileState(int accountId)
				throws RemoteException {
			SipService.this.enforceCallingOrSelfPermission(
					SipManager.PERMISSION_USE_SIP, null);
			return SipService.this.getSipProfileState(accountId);
		}

		@Override
		public void switchToAutoAnswer() throws RemoteException {
			SipService.this.enforceCallingOrSelfPermission(
					SipManager.PERMISSION_USE_SIP, null);
			Log.d(TAG, "Switch to auto answer");
			setAutoAnswerNext(true);

		}

		@Override
		public void ignoreNextOutgoingCallFor(String number)
				throws RemoteException {

		}

		@Override
		public void makeCall(String callee, final long accountId)
				throws RemoteException {
			makeCallWithOptions(callee, accountId);
		}

		@Override
		public void makeCallWithOptions(final String callee,
				final long accountId) throws RemoteException {
			SipService.this.enforceCallingOrSelfPermission(
					SipManager.PERMISSION_USE_SIP, null);
			// We have to ensure service is properly started and not just binded
			SipService.this.startService(new Intent(SipService.this,
					SipService.class));

			if (pjService == null) {
				Log.e(TAG, "Can't place call if service not started");
				// TODO - we should return a failing status here
				return;
			}

			Log.d("henry", "supportMultipleCalls = " + supportMultipleCalls);
			if (!supportMultipleCalls) {
				// Check if there is no ongoing calls if so drop this request by
				// alerting user
				SipCallSession activeCall = pjService.getActiveCallInProgress();
				Log.d("henry", "activeCall = " + activeCall);
				if (activeCall != null) {
					if (!CustomDistribution.forceNoMultipleCalls()) {
					}
					return;
				}
			}
			getExecutor().execute(new SipRunnable() {
				@Override
				protected void doRun() throws SameThreadException {
					pjService.makeCall(callee, accountId, null);
				}
			});
		}

		@Override
		public int answer(final int callId, final int status)
				throws RemoteException {
			SipService.this.enforceCallingOrSelfPermission(
					SipManager.PERMISSION_USE_SIP, null);
			ReturnRunnable action = new ReturnRunnable() {
				@Override
				protected Object runWithReturn() throws SameThreadException {
					return (Integer) pjService.callAnswer(callId, status);
				}
			};
			getExecutor().execute(action);
			// return (Integer) action.getResult();
			return SipManager.SUCCESS;

		}

		@Override
		public int hangup(final int callId, final int status)
				throws RemoteException {
			SipService.this.enforceCallingOrSelfPermission(
					SipManager.PERMISSION_USE_SIP, null);
			ReturnRunnable action = new ReturnRunnable() {
				@Override
				protected Object runWithReturn() throws SameThreadException {
					return (Integer) pjService.callHangup(callId, status);
				}
			};
			getExecutor().execute(action);
			return SipManager.SUCCESS;

		}

		@Override
		public int sendDtmf(final int callId, final int keyCode)
				throws RemoteException {
			SipService.this.enforceCallingOrSelfPermission(
					SipManager.PERMISSION_USE_SIP, null);

			ReturnRunnable action = new ReturnRunnable() {
				@Override
				protected Object runWithReturn() throws SameThreadException {
					return (Integer) pjService.sendDtmf(callId, keyCode);
				}
			};
			getExecutor().execute(action);
			return (Integer) action.getResult();

		}

		@Override
		public int hold(final int callId) throws RemoteException {
			SipService.this.enforceCallingOrSelfPermission(
					SipManager.PERMISSION_USE_SIP, null);
			Log.d(TAG, "HOLDING");
			ReturnRunnable action = new ReturnRunnable() {
				@Override
				protected Object runWithReturn() throws SameThreadException {
					return (Integer) pjService.callHold(callId);
				}
			};
			getExecutor().execute(action);
			return (Integer) action.getResult();
		}

		@Override
		public int reinvite(final int callId, final boolean unhold)
				throws RemoteException {
			// TODO Auto-generated method stub
			SipService.this.enforceCallingOrSelfPermission(
					SipManager.PERMISSION_USE_SIP, null);
			Log.d(TAG, "REINVITING");
			ReturnRunnable action = new ReturnRunnable() {
				@Override
				protected Object runWithReturn() throws SameThreadException {
					return (Integer) pjService.callReinvite(callId, unhold);
				}
			};
			getExecutor().execute(action);
			return (Integer) action.getResult();

		}

		@Override
		public int xfer(final int callId, final String callee)
				throws RemoteException {
			SipService.this.enforceCallingOrSelfPermission(
					SipManager.PERMISSION_USE_SIP, null);
			Log.d(TAG, "XFER");
			ReturnRunnable action = new ReturnRunnable() {
				@Override
				protected Object runWithReturn() throws SameThreadException {
					return (Integer) pjService.callXfer(callId, callee);
				}
			};
			getExecutor().execute(action);
			return (Integer) action.getResult();

		}

		@Override
		public int xferReplace(final int callId, final int otherCallId,
				final int options) throws RemoteException {
			SipService.this.enforceCallingOrSelfPermission(
					SipManager.PERMISSION_USE_SIP, null);
			Log.d(TAG, "XFER-replace");
			ReturnRunnable action = new ReturnRunnable() {
				@Override
				protected Object runWithReturn() throws SameThreadException {
					return (Integer) pjService.callXferReplace(callId,
							otherCallId, options);
				}
			};
			getExecutor().execute(action);
			return (Integer) action.getResult();

		}

		@Override
		public SipCallSession getCallInfo(int callId) throws RemoteException {
			SipService.this.enforceCallingOrSelfPermission(
					SipManager.PERMISSION_USE_SIP, null);
			return new SipCallSession(pjService.getCallInfo(callId));
		}

		@Override
		public SipCallSession[] getCalls() throws RemoteException {
			SipService.this.enforceCallingOrSelfPermission(
					SipManager.PERMISSION_USE_SIP, null);
			if (pjService != null) {
				SipCallSession[] listOfCallsImpl = pjService.getCalls();
				SipCallSession[] result = new SipCallSession[listOfCallsImpl.length];
				for (int sessIdx = 0; sessIdx < listOfCallsImpl.length; sessIdx++) {
					result[sessIdx] = new SipCallSession(
							listOfCallsImpl[sessIdx]);
				}
				return result;
			}
			return new SipCallSession[0];

		}

		@Override
		public String showCallInfosDialog(int callId) throws RemoteException {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public void setMicrophoneMute(final boolean on) throws RemoteException {
			SipService.this.enforceCallingOrSelfPermission(
					SipManager.PERMISSION_USE_SIP, null);
			getExecutor().execute(new SipRunnable() {
				@Override
				protected void doRun() throws SameThreadException {
					pjService.setMicrophoneMute(on);
				}
			});
		}

		@Override
		public void setSpeakerphoneOn(final boolean on) throws RemoteException {
			SipService.this.enforceCallingOrSelfPermission(
					SipManager.PERMISSION_USE_SIP, null);
			getExecutor().execute(new SipRunnable() {
				@Override
				protected void doRun() throws SameThreadException {
					pjService.setSpeakerphoneOn(on);
				}
			});
		}

		@Override
		public void setBluetoothOn(boolean on) throws RemoteException {
			// TODO Auto-generated method stub

		}

		@Override
		public void confAdjustTxLevel(int port, float value)
				throws RemoteException {

		}

		@Override
		public void confAdjustRxLevel(int port, float value)
				throws RemoteException {
			// TODO Auto-generated method stub

		}

		@Override
		public long confGetRxTxLevel(final int port) throws RemoteException {
			// TODO Auto-generated method stub
			ReturnRunnable action = new ReturnRunnable() {
				@Override
				protected Object runWithReturn() throws SameThreadException {
					return (Long) pjService.getRxTxLevel(port);
				}
			};
			getExecutor().execute(action);
			return (Long) action.getResult();

		}

		@Override
		public void setEchoCancellation(final boolean on)
				throws RemoteException {
			SipService.this.enforceCallingOrSelfPermission(
					SipManager.PERMISSION_USE_SIP, null);
			if (pjService == null) {
				return;
			}
			getExecutor().execute(new SipRunnable() {
				@Override
				protected void doRun() throws SameThreadException {
					pjService.setEchoCancellation(on);
				}
			});

		}

		@Override
		public void adjustVolume(SipCallSession callInfo, int direction,
				int flags) throws RemoteException {
			// TODO Auto-generated method stub
			SipService.this.enforceCallingOrSelfPermission(
					SipManager.PERMISSION_USE_SIP, null);

			if (pjService == null) {
				return;
			}

			boolean ringing = callInfo.isIncoming()
					&& callInfo.isBeforeConfirmed();
			// Mode ringing
			if (ringing) {
				// What is expected here is to silence ringer
				// pjService.adjustStreamVolume(AudioManager.STREAM_RING,
				// direction, AudioManager.FLAG_SHOW_UI);
				pjService.silenceRinger();
			} else {
				// Mode in call
				if (prefsWrapper
						.getPreferenceBooleanValue(SipConfigManager.USE_SOFT_VOLUME)) {
				} else {
					pjService
							.adjustStreamVolume(Compatibility
									.getInCallStream(pjService.mediaManager
											.doesUserWantBluetooth()),
									direction, flags);
				}
			}

		}

		@Override
		public MediaState getCurrentMediaState() throws RemoteException {
			// TODO Auto-generated method stub
			SipService.this.enforceCallingOrSelfPermission(
					SipManager.PERMISSION_USE_SIP, null);
			MediaState ms = new MediaState();
			if (pjService != null && pjService.mediaManager != null) {
				ms = pjService.mediaManager.getMediaState();
			}
			return ms;

		}

		@Override
		public int startLoopbackTest() throws RemoteException {
			// TODO Auto-generated method stub
			return 0;
		}

		@Override
		public int stopLoopbackTest() throws RemoteException {
			// TODO Auto-generated method stub
			return 0;
		}

		@Override
		public void startRecording(final int callId, final int way)
				throws RemoteException {
			// TODO Auto-generated method stub
			SipService.this.enforceCallingOrSelfPermission(
					SipManager.PERMISSION_USE_SIP, null);
			if (pjService == null) {
				return;
			}
			getExecutor().execute(new SipRunnable() {
				@Override
				protected void doRun() throws SameThreadException {
					pjService.startRecording(callId, way);
				}
			});

		}

		@Override
		public void stopRecording(final int callId) throws RemoteException {

			SipService.this.enforceCallingOrSelfPermission(
					SipManager.PERMISSION_USE_SIP, null);
			if (pjService == null) {
				return;
			}
			getExecutor().execute(new SipRunnable() {
				@Override
				protected void doRun() throws SameThreadException {
					pjService.stopRecording(callId);
				}
			});

		}

		@Override
		public boolean isRecording(int callId) throws RemoteException {
			SipService.this.enforceCallingOrSelfPermission(
					SipManager.PERMISSION_USE_SIP, null);
			if (pjService == null) {
				return false;
			}

			SipCallSession info = pjService.getCallInfo(callId);
			if (info != null) {
				return info.isRecording();
			}
			return false;

		}

		@Override
		public boolean canRecord(int callId) throws RemoteException {
			SipService.this.enforceCallingOrSelfPermission(
					SipManager.PERMISSION_USE_SIP, null);
			if (pjService == null) {
				return false;
			}
			return pjService.canRecord(callId);
		}

		@Override
		public void playWaveFile(final String filePath, final int callId,
				final int way) throws RemoteException {
			SipService.this.enforceCallingOrSelfPermission(
					SipManager.PERMISSION_USE_SIP, null);
			if (pjService == null) {
				return;
			}
			getExecutor().execute(new SipRunnable() {
				@Override
				protected void doRun() throws SameThreadException {
					pjService.playWaveFile(filePath, callId, way);
				}
			});
		}

		@Override
		public void sendMessage(String msg, String toNumber, long accountId)
				throws RemoteException {
			// TODO Auto-generated method stub

		}

		@Override
		public void setPresence(int presence, String statusText, long accountId)
				throws RemoteException {
			// TODO Auto-generated method stub

		}

		@Override
		public int getPresence(long accountId) throws RemoteException {
			return 0;
		}

		@Override
		public String getPresenceStatus(long accountId) throws RemoteException {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public void zrtpSASVerified(int callId) throws RemoteException {
			// TODO Auto-generated method stub

		}

		@Override
		public void updateCallOptions(int callId, Bundle options)
				throws RemoteException {
			getExecutor().execute(new SipRunnable() {
				@Override
				protected void doRun() throws SameThreadException {
					// pjService.updateCallOptions(callId, options);
				}
			});

		}

		@Override
		public void zrtpSASRevoke(int callId) throws RemoteException {
			// TODO Auto-generated method stub

		}

		@Override
		public String getLocalNatType() throws RemoteException {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public void registerCallback(ICallback cb) throws RemoteException {
			// TODO Auto-generated method stub
			if (cb != null) {
				Log.d(TAG, "register");
				mCallbacks.register(cb);
			}
		}

		@Override
		public void unregisterCallback(ICallback cb) throws RemoteException {
			// TODO Auto-generated method stub
			if (cb != null) {
				Log.d(TAG, "unregister");
				mCallbacks.unregister(cb);
			}
		}

		@Override
		public void setConfigParam(String key, String value)
				throws RemoteException {
			// TODO Auto-generated method stub

		}

		@Override
		public String getConfigParam(String key) throws RemoteException {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public void setVolume(SipCallSession callInfo, int value)
				throws RemoteException {
			SipService.this.enforceCallingOrSelfPermission(
					SipManager.PERMISSION_USE_SIP, null);
			if (pjService == null) {
				return;
			}
			boolean ringing = callInfo.isIncoming()
					&& callInfo.isBeforeConfirmed();
			// Mode ringing
			if (ringing) {
				pjService.silenceRinger();
			} else {
				// Mode in call
				if (prefsWrapper
						.getPreferenceBooleanValue(SipConfigManager.USE_SOFT_VOLUME)) {
				} else {
					pjService.setStreamVolume(Compatibility
							.getInCallStream(pjService.mediaManager
									.doesUserWantBluetooth()), value);
				}
			}

		}

	};

	public abstract class ReturnRunnable extends SipRunnable {
		private Semaphore runSemaphore;
		private Object resultObject;

		public ReturnRunnable() {
			super();
			runSemaphore = new Semaphore(0);
		}

		public Object getResult() {
			try {
				runSemaphore.acquire();
			} catch (InterruptedException e) {
				Log.e(TAG, "Can't acquire run semaphore... problem...");
			}
			return resultObject;
		}

		protected abstract Object runWithReturn() throws SameThreadException;

		@Override
		public void doRun() throws SameThreadException {
			setResult(runWithReturn());
		}

		private void setResult(Object obj) {
			resultObject = obj;
			runSemaphore.release();
		}
	}

	private static String UI_CALL_PACKAGE = null;

	public static Intent buildCallUiIntent(Context ctxt, SipCallSession callInfo) {
		// Resolve the package to handle call.
		if (UI_CALL_PACKAGE == null) {
			UI_CALL_PACKAGE = ctxt.getPackageName();
			try {
				Map<String, DynActivityPlugin> callsUis = ExtraPlugins
						.getDynActivityPlugins(ctxt,
								SipManager.ACTION_SIP_CALL_UI);
				String preferredPackage = SipConfigManager
						.getPreferenceStringValue(ctxt,
								SipConfigManager.CALL_UI_PACKAGE,
								UI_CALL_PACKAGE);
				String packageName = null;
				boolean foundPref = false;
				for (String activity : callsUis.keySet()) {
					packageName = activity.split("/")[0];
					if (preferredPackage.equalsIgnoreCase(packageName)) {
						UI_CALL_PACKAGE = packageName;
						foundPref = true;
						break;
					}
				}
				if (!foundPref && !TextUtils.isEmpty(packageName)) {
					UI_CALL_PACKAGE = packageName;
				}
			} catch (Exception e) {
				Log.e(TAG, "Error while resolving package", e);
			}
		}
		SipCallSession toSendInfo = new SipCallSession(callInfo);
		Intent intent = new Intent(SipManager.ACTION_SIP_CALL_UI);
		intent.putExtra(SipManager.EXTRA_CALL_INFO, toSendInfo);
		intent.setPackage(UI_CALL_PACKAGE);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
				| Intent.FLAG_ACTIVITY_SINGLE_TOP);
		return intent;
	}

	// Media direct binders
	public void setNoSnd() throws SameThreadException {
		if (pjService != null) {
			pjService.setNoSnd();
		}
	}

	public void setSnd() throws SameThreadException {
		if (pjService != null) {
			pjService.setSnd();
		}
	}

	private PresenceStatus presence = SipManager.PresenceStatus.ONLINE;

	/**
	 * Get current last status for the user
	 * 
	 * @return
	 */
	public PresenceStatus getPresence() {
		return presence;
	}

	public void onCallStateChanged(int state) {
		Message msg = new Message();
		msg.what = CALLBACK_CALL_STATE_CHANGED;
		msg.arg1 = state;
		mHandler.sendMessage(msg);
	}

	public void onRecordStateChanged(SipCallSession callinfo) {
		// mHandler.sendEmptyMessage(CALLBACK_ACCOUT_STATUS);
	}

	public void onCallLaunched(SipCallSession callinfo) {
		mHandler.sendEmptyMessage(CALLBACK_CALL_LAUNCHED);
	}

	public void onCallInComming(SipCallSession callinfo) {
		mHandler.sendEmptyMessage(CALLBACK_CALL_INCOMMING);
	}

	public void onCallInfoListChanged() {
		mHandler.sendEmptyMessage(CALLBACK_CALLINFO_LIST_CHANGED);
	}

	private static final int CALLBACK_CALLINFO_LIST_CHANGED = 1;
	private static final int CALLBACK_CALL_STATE_CHANGED = 2;
	private static final int CALLBACK_CALL_LAUNCHED = 3;
	private static final int CALLBACK_CALL_INCOMMING = 4;

	private Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			handleCallBack(msg);
		}
	};

	private void handleCallBack(Message msg) {
		int n = mCallbacks.beginBroadcast();
		Log.i(TAG, "mCallbacks.beginBroadcast() is " + n);
		switch (msg.what) {
		case CALLBACK_CALL_STATE_CHANGED:
			int state = msg.arg1;
			try {
				for (int i = 0; i < n; i++) {
					mCallbacks.getBroadcastItem(i).onSipCallChanged(state);
				}
			} catch (RemoteException e) {
				Log.e(TAG, "" + e);
			}
			break;
		case CALLBACK_CALL_LAUNCHED:
			try {
				for (int i = 0; i < n; i++) {
					mCallbacks.getBroadcastItem(i).onCallLaunched();
				}
			} catch (RemoteException e) {
				Log.e(TAG, "" + e);
			}
			break;
		case CALLBACK_CALL_INCOMMING:
			try {
				for (int i = 0; i < n; i++) {
					mCallbacks.getBroadcastItem(i).onInCommingCall();
				}
			} catch (RemoteException e) {
				Log.e(TAG, "" + e);
			}
			break;
		case CALLBACK_CALLINFO_LIST_CHANGED:
			try {
				for (int i = 0; i < n; i++) {
					mCallbacks.getBroadcastItem(i).onCallInfoListChanged();
				}
			} catch (RemoteException e) {
				Log.e(TAG, "" + e);
			}
			break;

		}
		mCallbacks.finishBroadcast();
	}
}
