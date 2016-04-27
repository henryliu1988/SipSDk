package com.zhonghu.sip.api;

import java.util.List;
import java.util.UUID;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import com.zhonghu.sip.api.SipCallSession.InvState;
import com.zhonghu.sip.api.SipCallSession.StatusCode;
import com.zhonghu.sip.wizard.Wizard;
import com.zhonghu.sip.wizard.WizardIface;

class SipController {

	private Context context;
	private AccoutStatusCallBack mAccoutStatusCallBack;
	private CallStateCallBack mCallStateCallBack;

	private static SipController sipController;
	private static final String TAG = "SIP SipController";

	private boolean isServiceRunning = false;
	private boolean isSipInit = false;
	private SipCallSession[] callsInfo = null;
	private MediaState mediaState;

	protected SipController() {
		Log.d(TAG, "SipController construct");
	}

	public static SipController getInstance() {
		if (sipController == null) {
			sipController = new SipController();
		}
		return sipController;
	}

	protected void addUser(String domain, String userName, String passWord) {
		SipProfile acount = buidAccout(domain, userName, passWord);
		AccountListManager.getInstance().addNewProfile(acount);
	}

	private SipProfile buidAccout(String domain, String userName,
			String passWord) {
		WizardIface wizard = new Wizard(domain, userName, passWord);
		SipProfile acount = new SipProfile();
		acount = wizard.buildAccount(acount);
		applyNewAccountDefault(acount);
		return acount;
	}

	protected void init(Context context) {
		Log.d(TAG, "isServiceRunning =" + isServiceRunning);
		this.context = context;
		startSipService();
		bindSipService();
		initCallInfo();
		initMediaState();
		isSipInit = true;
	}

	protected boolean isServiceConnected() {
		return service != null;
	}

	protected void logOut() {
		try {
			service.removeAllAccounts();
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	protected void reAddAllAccounts() {
		try {
			service.reAddAllAccounts();
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	protected void quit() {
		stopSipService();
	}

	private void initCallInfo() {
		callsInfo = new SipCallSession[1];
	}

	void initMediaState() {
		mediaState = new MediaState();
	}

	protected void login(String domain, String userName, String passWord) {
		AccountListManager.getInstance().removeAllProfiles();
		SipProfile acount = buidAccout(domain, userName, passWord);
		long id = AccountListManager.getInstance().addNewProfile(acount);
		if (id != SipProfile.INVALID_ID && service != null) {
			try {
				service.addAllAccounts();
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	protected void makeCall(String callNum) {
		makeCallWithOption(callNum);
	}

	protected void acceptCall(int callId) {
		try {
			getSipService().answer(callId, SipCallSession.StatusCode.OK);
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	protected void sendDtmf(int callId, int keyCode) {
		try {
			getSipService().sendDtmf(callId, keyCode);
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	protected int getCurrentCallId() {
		SipCallSession callInfo = getActiveCallInfo();
		if (callInfo == null
				|| callInfo.getCallId() == SipCallSession.INVALID_CALL_ID) {
			Log.e(TAG, "Try to do an action on an invalid call !!!");
			return -1;
		}
		return callInfo.getCallId();
	}

	protected int getAccountStatuCodeByAccId(long accId) {
		SipProfileState state = AccountListManager.getInstance()
				.getProfileState(accId);
		if (state == null) {
			return -1;
		}
		return state.getStatusCode();
	}

	protected int getAccountStatuCodeByUserName(String userName) {
		long id = AccountListManager.getInstance().getProfileAccIdByUserName(
				userName);
		return getAccountStatuCodeByAccId(id);
	}

	protected void hangupCall(int callId, int status) {
		try {
			getSipService().hangup(callId, status);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	protected boolean isSipInit() {
		return isSipInit && context != null && service != null;
	}

	protected void rejectCall(int callid) {
		try {
			getSipService().hangup(callid, StatusCode.BUSY_HERE);
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	protected void adjustVolume(SipCallSession callInfo, int direction,
			int flags) {
		if (service != null) {
			try {
				service.adjustVolume(callInfo, direction, flags);
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	protected void setVolume(SipCallSession callInfo, int value) {
		if (service != null) {
			try {
				service.setVolume(callInfo, value);
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}

	protected void setSpeakerphoneOn(boolean on) {
		if (service != null) {
			Log.d(TAG, "Manually switch to speaker");
			try {
				service.setSpeakerphoneOn((on) ? true : false);
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}

	protected void setMute(boolean on) {
		if (service != null) {
			try {
				service.setMicrophoneMute(on);
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	protected void setEchoCancellation(boolean on) {
		if (service != null) {
			try {
				service.setEchoCancellation(on);
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}

	protected void setIncomingIntent(PendingIntent pendingIntent) {
	}

	protected void setAccoutCallBack(AccoutStatusCallBack callBack) {
		this.mAccoutStatusCallBack = callBack;
	}

	protected void setCallStateCallBack(CallStateCallBack callBack) {
		this.mCallStateCallBack = callBack;
	}

	private void bindSipService() {
		if (service == null) {
			Intent serviceIntent = new Intent(context,com.zhonghu.sip.service.SipService.class);
			context.bindService(serviceIntent, connection,
					Context.BIND_AUTO_CREATE);
		}
	}

	private void stopSipService() {
		Thread t = new Thread("StopSip") {
			public void run() {
				Log.d(TAG, "StopSipService");
				Intent serviceIntent = new Intent(context,com.zhonghu.sip.service.SipService.class);
				context.stopService(serviceIntent);
			};
		};
		t.start();
	}

	protected static long getCurrentAccoutId() {
		List<SipProfile> profileList = AccountListManager.getInstance()
				.getAllSipProfile();

		if (profileList.size() > 0) {
			return profileList.get(0).getId();
		}
		return SipProfile.INVALID_ID;
	}

	private static ISipService service;
	private ServiceConnection connection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName arg0, IBinder binder) {
			service = ISipService.Stub.asInterface(binder);
			checkForUnLoginData();
			try {
				service.registerCallback(mCallback);
			} catch (RemoteException e) {
				Log.e(TAG, "", e);
			} finally {
				isServiceRunning = true;
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			try {
				service.unregisterCallback(mCallback);
			} catch (RemoteException e) {
				e.printStackTrace();
			} finally {
				isServiceRunning = false;
				service = null;
			}
		}
	};

	private void checkForUnLoginData() {
		if (AccountListManager.getInstance().isProfileNoStatus()) {
			try {
				service.addAllAccounts();
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	/**
	 * Apply default settings for a new account to check very basic coherence of
	 * settings and auto-modify settings missing
	 * 
	 * @param account
	 */
	private void applyNewAccountDefault(SipProfile account) {
		if (account.use_rfc5626) {
			if (TextUtils.isEmpty(account.rfc5626_instance_id)) {
				String autoInstanceId = (UUID.randomUUID()).toString();
				account.rfc5626_instance_id = "<urn:uuid:" + autoInstanceId
						+ ">";
			}
		}
	}

	/**
	 * Broadcast the fact that account config has changed
	 * 
	 * @param accountId
	 */
	protected void broadcastAccountChange(long accountId) {
		Intent publishIntent = new Intent(SipManager.ACTION_SIP_ACCOUNT_CHANGED);
		publishIntent.putExtra(SipProfile.FIELD_ID, accountId);
		context.sendBroadcast(publishIntent);
	}

	/**
	 * Broadcast the fact that account config has been deleted
	 * 
	 * @param accountId
	 */
	protected void broadcastAccountDelete(long accountId) {
		Intent publishIntent = new Intent(SipManager.ACTION_SIP_ACCOUNT_DELETED);
		publishIntent.putExtra(SipProfile.FIELD_ID, accountId);
		context.sendBroadcast(publishIntent);
	}

	/**
	 * Broadcast the fact that registration / adding status changed
	 * 
	 * @param accountId
	 *            the id of the account
	 */
	protected void broadcastRegistrationChange(long accountId) {
		Intent publishIntent = new Intent(
				SipManager.ACTION_SIP_REGISTRATION_CHANGED);
		publishIntent.putExtra(SipProfile.FIELD_ID, accountId);
		context.sendBroadcast(publishIntent, SipManager.PERMISSION_USE_SIP);
	}

	private ISipService getSipService() {
		return service;
	}

	private void startSipService() {
		Log.d(TAG, "startSipService");
		Intent serviceIntent = new Intent(context,com.zhonghu.sip.service.SipService.class);
		context.startService(serviceIntent);
	}

	private void makeCallWithOption(String callee) {
		try {
			long accId = getCurrentAccoutId();
			if (accId != SipProfile.INVALID_ID) {
				getSipService().makeCall(callee, accId);
			} else {
				Log.e(TAG, "none account find");
			}
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	protected void setRecordFileDir(String dir) {
		try {
			service.setConfigParam(SipConfigManager.RECORDER_FILE_PATH, dir);
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	protected void startRecord(int callId) {
		try {
			service.startRecording(callId, SipManager.BITMASK_ALL);
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	protected void stopRecord(int callId) {
		try {
			service.stopRecording(callId);
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	protected boolean isRecording(int callId) {
		try {
			return service.isRecording(callId);
		} catch (RemoteException e) {
			e.printStackTrace();
			return false;
		}

	}

	protected String getRecordFileDir() {
		try {
			return service.getConfigParam(SipConfigManager.RECORDER_FILE_PATH);
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}

	protected void onAccoutStatusChanged(int resultCode) {
		Log.d(TAG, "resultCode = " + resultCode);
		if (mAccoutStatusCallBack != null) {
			mAccoutStatusCallBack.onAccoutStatusChanged(resultCode);
		}

	}

	private ICallback.Stub mCallback = new ICallback.Stub() {

		@Override
		public void onSipMediaChanged() throws RemoteException {
			// TODO Auto-generated method stub
			callsInfo = getSipService().getCalls();
			mediaState = getSipService().getCurrentMediaState();
		}

		@Override
		public void onSipCallChanged(int stateCode) throws RemoteException {
			// TODO Auto-generated method stub
			callsInfo = getSipService().getCalls();
			for (SipCallSession info : callsInfo) {
				Log.d("henry", "info = " + info.toString());
			}
			mediaState = getSipService().getCurrentMediaState();

			if (mCallStateCallBack != null) {
				mCallStateCallBack.onCallStateChanged(stateCode);
			}
		}

		@Override
		public void onCallLaunched() throws RemoteException {
			// TODO Auto-generated method stub
			Log.d(TAG, "onCallLaunched");
			callsInfo = getSipService().getCalls();
			mediaState = getSipService().getCurrentMediaState();
			if (mCallStateCallBack != null) {
				mCallStateCallBack.onCallLaunched();
			}
		}

		@Override
		public void onInCommingCall() throws RemoteException {
			// TODO Auto-generated method stub

		}

		@Override
		public void onCallInfoListChanged() throws RemoteException {
			// TODO Auto-generated method stub
			callsInfo = getSipService().getCalls();
		}
	};

	/**
	 * Get the call that is active on the view
	 * 
	 * @param excludeHold
	 *            if true we do not return cals hold locally
	 * @return
	 */
	protected SipCallSession getActiveCallInfo() {
		SipCallSession currentCallInfo = null;
		try {
			callsInfo = getSipService().getCalls();
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		if (callsInfo == null) {
			return null;
		}
		for (SipCallSession callInfo : callsInfo) {
			currentCallInfo = getPrioritaryCall(callInfo, currentCallInfo);
		}

		return currentCallInfo;
	}

	protected SipCallSession[] getCalls() {
		try {
			callsInfo = getSipService().getCalls();
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return callsInfo;
	}

	protected SipCallSession getCallInfoById(int id) {
		SipCallSession currentCallInfo = null;

		for (SipCallSession callInfo : callsInfo) {
			if (callInfo.getCallId() == id) {
				currentCallInfo = callInfo;
				return currentCallInfo;
			}
		}
		return getActiveCallInfo();
	}

	protected boolean isSpeakerOn() {
		if (mediaState == null) {
			return false;
		}
		return mediaState.isSpeakerphoneOn();
	}

	protected boolean isMuteOn() {
		if (mediaState == null) {
			return false;
		}
		return mediaState.isMicrophoneMute();

	}

	/**
	 * Get the call with the higher priority comparing two calls
	 * 
	 * @param call1
	 *            First call object to compare
	 * @param call2
	 *            Second call object to compare
	 * @return The call object with highest priority
	 */
	private SipCallSession getPrioritaryCall(SipCallSession call1,
			SipCallSession call2) {
		// We prefer the not null
		if (call1 == null) {
			return call2;
		} else if (call2 == null) {
			return call1;
		}

		// // We prefer the one not terminated
		// if (call1.isAfterEnded()) {
		// return call2;
		// } else if (call2.isAfterEnded()) {
		// return call1;
		// }
		// // We prefer the one not held
		// if (call1.isLocalHeld()) {
		// return call2;
		// } else if (call2.isLocalHeld()) {
		// return call1;
		// }
		// We prefer the older call
		// to keep consistancy on what will be replied if new call arrives
		return (call1.getConnectStart() > call2.getConnectStart()) ? call1
				: call2;
	}

	/**
	 * Get the call with the higher priority comparing two calls
	 * 
	 * @param call1
	 *            First call object to compare
	 * @param call2
	 *            Second call object to compare
	 * @return The call object with highest priority
	 */
	protected SipCallSession getPrioritaryStateCall(SipCallSession call1,
			SipCallSession call2) {
		// We prefer the not null
		if (call1 == null) {
			return call2;
		} else if (call2 == null) {
			return call1;
		}
		// We prefer the one not terminated
		if (call1.isAfterEnded()) {
			return call2;
		} else if (call2.isAfterEnded()) {
			return call1;
		}
		// We prefer the one not held
		if (call1.isLocalHeld()) {
			return call2;
		} else if (call2.isLocalHeld()) {
			return call1;
		}
		// We prefer the older call
		// to keep consistancy on what will be replied if new call arrives
		return (call1.getCallStart() > call2.getCallStart()) ? call2 : call1;
	}

	protected int getCallDuration(int callId)
	{	int duration = -1;

		SipCallSession callSession = getCallInfoById(callId);
		if(callId != -1)
		{
			if(callSession.callState == InvState.CONFIRMED){
				long start = callSession.getConnectStart();
				long current = SystemClock.elapsedRealtime();
				duration = (int)(current-start);
			}
		}
		return duration;
		
	}
}
