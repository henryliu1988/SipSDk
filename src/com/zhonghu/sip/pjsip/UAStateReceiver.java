package com.zhonghu.sip.pjsip;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.pjsip.pjsua.Callback;
import org.pjsip.pjsua.SWIGTYPE_p_int;
import org.pjsip.pjsua.SWIGTYPE_p_p_pjmedia_port;
import org.pjsip.pjsua.SWIGTYPE_p_pjmedia_sdp_session;
import org.pjsip.pjsua.SWIGTYPE_p_pjmedia_stream;
import org.pjsip.pjsua.SWIGTYPE_p_pjsip_rx_data;
import org.pjsip.pjsua.SWIGTYPE_p_pjsip_status_code;
import org.pjsip.pjsua.SWIGTYPE_p_pjsip_tx_data;
import org.pjsip.pjsua.pj_pool_t;
import org.pjsip.pjsua.pj_str_t;
import org.pjsip.pjsua.pj_stun_nat_detect_result;
import org.pjsip.pjsua.pjsip_event;
import org.pjsip.pjsua.pjsip_redirect_op;
import org.pjsip.pjsua.pjsip_status_code;
import org.pjsip.pjsua.pjsua;
import org.pjsip.pjsua.pjsua_buddy_info;
import org.pjsip.pjsua.pjsua_med_tp_state_info;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

import com.zhonghu.sip.api.SipCallSession;
import com.zhonghu.sip.api.SipCallSession.StatusCode;
import com.zhonghu.sip.api.SipConfigManager;
import com.zhonghu.sip.api.SipManager;
import com.zhonghu.sip.api.SipManager.PresenceStatus;
import com.zhonghu.sip.api.SipProfile;
import com.zhonghu.sip.service.SipCallSessionImpl;
import com.zhonghu.sip.service.SipService.SipRunnable;
import com.zhonghu.sip.utils.Compatibility;
import com.zhonghu.sip.utils.Threading;
import com.zhonghu.sip.utils.TimerWrapper;

public class UAStateReceiver extends Callback {

	private final static String TAG = "SIP UA Receiver";
	private final static String ACTION_PHONE_STATE_CHANGED = "android.intent.action.PHONE_STATE";
	private PjSipService pjService;

	/**
	 * Map callId to known {@link SipCallSession}. This is cache of known
	 * session maintained by the UA state receiver. The UA state receiver is in
	 * charge to maintain calls list integrity for {@link PjSipService}. All
	 * information it gets comes from the stack. Except recording status that
	 * comes from service.
	 */
	private SparseArray<SipCallSessionImpl> callsList = new SparseArray<SipCallSessionImpl>();

	final static long LAUNCH_TRIGGER_DELAY = 2000;

	private long lastLaunchCallHandler = 0;
	private int lastLaunchCallId = -1;
	private int eventLockCount = 0;
	private boolean mIntegrateWithCallLogs;
	private boolean mPlayWaittone;
	private int mPreferedHeadsetAction;
	private boolean mAutoRecordCalls;
	private int mMicroSource;
	private WorkerHandler msgHandler;
	private HandlerThread handlerThread;

	private WakeLock ongoingCallLock;
	private WakeLock eventLock;
	private static final int ON_CALLINFO_UPDATE = 1;
	private static final int ON_CALL_STATE = 2;
	private static final int ON_MEDIA_STATE = 3;

	private static class WorkerHandler extends Handler {
		WeakReference<UAStateReceiver> sr;

		public WorkerHandler(Looper looper, UAStateReceiver stateReceiver) {
			super(looper);
			Log.d(TAG, "Create async worker !!!");
			sr = new WeakReference<UAStateReceiver>(stateReceiver);
		}

		public void handleMessage(Message msg) {
			UAStateReceiver stateReceiver = sr.get();
			if (stateReceiver == null) {
				return;
			}
			stateReceiver.lockCpu();
			switch (msg.what) {
			case ON_CALL_STATE: {
				SipCallSessionImpl callInfo = (SipCallSessionImpl) msg.obj;
				final int callState = callInfo.getCallState();
				switch (callState) {
				case SipCallSession.InvState.INCOMING:
				case SipCallSession.InvState.CALLING:
					// stateReceiver.notificationManager.showNotificationForCall(callInfo);
					stateReceiver.launchCallHandler(callInfo);
					stateReceiver.broadCastAndroidCallState("RINGING",
							callInfo.getRemoteContact());

					break;
				case SipCallSession.InvState.EARLY:
				case SipCallSession.InvState.CONNECTING:
				case SipCallSession.InvState.CONFIRMED:
					stateReceiver.broadCastAndroidCallState("OFFHOOK",
							callInfo.getRemoteContact());
					if (stateReceiver.pjService.mediaManager != null) {
						if (callState == SipCallSession.InvState.CONFIRMED) {
							// Don't unfocus here
							stateReceiver.pjService.mediaManager.stopRing();
						}
					}					
					// Auto send pending dtmf
					if (callState == SipCallSession.InvState.CONFIRMED) {
						// stateReceiver.sendPendingDtmf(callInfo.getCallId());
					}
					// If state is confirmed and not already intialized
					if (callState == SipCallSession.InvState.CONFIRMED
							&& callInfo.getCallStart() == 0) {
						callInfo.setCallStart(System.currentTimeMillis());
					}
					break;
				case SipCallSession.InvState.DISCONNECTED:
					if (stateReceiver.pjService.mediaManager != null
							&& stateReceiver.getRingingCall() == null) {
						stateReceiver.pjService.mediaManager.stopRing();
					}
					stateReceiver.broadCastAndroidCallState("IDLE",
							callInfo.getRemoteContact());

					// If no remaining calls, cancel the notification
					if (stateReceiver.getActiveCallInProgress() == null) {
						// stateReceiver.notificationManager.cancelCalls();
						// We should now ask parent to stop if needed
						if (stateReceiver.pjService != null
								&& stateReceiver.pjService.service != null) {
							stateReceiver.pjService.service
									.treatDeferUnregistersForOutgoing();
						}
					}

					// If the call goes out in error...
					if (callInfo.getLastStatusCode() != 200
							&& callInfo.getLastReasonCode() != 200) {
						// We notify the user with toaster
/*						stateReceiver.pjService.service
								.notifyUserOfMessage(callInfo
										.getLastStatusCode()
										+ " / "
										+ callInfo.getLastStatusComment());
*/					}
					callInfo.applyDisconnect();
					break;
				default:
					break;
				}
				stateReceiver.onCallStateChanged(callInfo);
				break;
			}
			case ON_MEDIA_STATE: {
				SipCallSession mediaCallInfo = (SipCallSession) msg.obj;
				SipCallSessionImpl callInfo = stateReceiver.callsList
						.get(mediaCallInfo.getCallId());
				callInfo.setMediaStatus(mediaCallInfo.getMediaStatus());
				stateReceiver.callsList
						.put(mediaCallInfo.getCallId(), callInfo);
				stateReceiver.onCallStateChanged(callInfo);
				break;
			}
			}
			stateReceiver.unlockCpu();
		}
	}

	/**
	 * Start the call activity for a given Sip Call Session. <br/>
	 * The call activity should take care to get any ongoing calls when started
	 * so the currentCallInfo2 parameter is indication only. <br/>
	 * This method ensure that the start of the activity is not fired too much
	 * in short delay and may just ignore requests if last time someone ask for
	 * a launch is too recent
	 * 
	 * @param currentCallInfo2
	 *            the call info that raise this request to open the call handler
	 *            activity
	 */
	private synchronized void launchCallHandler(SipCallSession currentCallInfo2) {
		long currentElapsedTime = SystemClock.elapsedRealtime();
		// Synchronized ensure we do not get this launched several time
		// We also ensure that a minimum delay has been consumed so that we do
		// not fire this too much times
		// Specially for EARLY - CONNECTING states
		int callId = currentCallInfo2.getCallId();
		Log.d("henry"+TAG,"launchCallHandler");
		if (lastLaunchCallHandler + LAUNCH_TRIGGER_DELAY < currentElapsedTime
				|| callId != lastLaunchCallId) {
			Log.d("henry"+TAG,"onCallLaunched");

			onCallLaunched(currentCallInfo2);
			lastLaunchCallHandler = currentElapsedTime;
			lastLaunchCallId = callId;
		} else {
			Log.d(TAG, "Ignore extra launch handler");
		}
	}

	private void lockCpu() {
		if (eventLock != null) {
			Log.d(TAG, "< LOCK CPU");
			eventLock.acquire();
			eventLockCount++;
		}
	}

	private void unlockCpu() {
		if (eventLock != null && eventLock.isHeld()) {
			eventLock.release();
			eventLockCount--;
			Log.d(TAG, "> UNLOCK CPU " + eventLockCount);
		}
	}

	// -------
	// Public configuration for receiver
	// -------

	public void initService(PjSipService srv) {
		pjService = srv;

		if (handlerThread == null) {
			handlerThread = new HandlerThread("UAStateAsyncWorker");
			handlerThread.start();
		}
		if (msgHandler == null) {
			msgHandler = new WorkerHandler(handlerThread.getLooper(), this);
		}

		if (eventLock == null) {
			PowerManager pman = (PowerManager) pjService.service
					.getSystemService(Context.POWER_SERVICE);
			eventLock = pman.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
					"com.csipsimple.inEventLock");
			eventLock.setReferenceCounted(true);
		}
		if (ongoingCallLock == null) {
			PowerManager pman = (PowerManager) pjService.service
					.getSystemService(Context.POWER_SERVICE);
			ongoingCallLock = pman.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
					"com.zhsip.ongoingCallLock");
			ongoingCallLock.setReferenceCounted(false);
		}
	}

	public void reconfigure(Context ctxt) {
		mIntegrateWithCallLogs = SipConfigManager.getPreferenceBooleanValue(
				ctxt, SipConfigManager.INTEGRATE_WITH_CALLLOGS);
		mPreferedHeadsetAction = SipConfigManager.getPreferenceIntegerValue(
				ctxt, SipConfigManager.HEADSET_ACTION,
				SipConfigManager.HEADSET_ACTION_CLEAR_CALL);
		mAutoRecordCalls = SipConfigManager.getPreferenceBooleanValue(ctxt,
				SipConfigManager.AUTO_RECORD_CALLS);
		mMicroSource = SipConfigManager.getPreferenceIntegerValue(ctxt,
				SipConfigManager.MICRO_SOURCE);
		mPlayWaittone = SipConfigManager.getPreferenceBooleanValue(ctxt,
				SipConfigManager.PLAY_WAITTONE_ON_HOLD, false);
	}

	/*
	 * private class IncomingCallInfos { public SipCallSession callInfo; public
	 * Integer accId; }
	 */
	@Override
	public void on_incoming_call(final int accId, final int callId,
			SWIGTYPE_p_pjsip_rx_data rdata) {
		Log.d(TAG, "on_incoming_call accId = " + accId);

		lockCpu();

		// Check if we have not already an ongoing call
		boolean hasOngoingSipCall = false;
		if (pjService != null && pjService.service != null) {
			SipCallSessionImpl[] calls = getCalls();
			if (calls != null) {
				for (SipCallSessionImpl existingCall : calls) {
					if (!existingCall.isAfterEnded()
							&& existingCall.getCallId() != callId) {
						if (!pjService.service.supportMultipleCalls) {
							Log.e(TAG,
									"Settings to not support two call at the same time !!!");
							// If there is an ongoing call and we do not support
							// multiple calls
							// Send busy here
							pjsua.call_hangup(callId, StatusCode.BUSY_HERE,
									null, null);
							unlockCpu();
							return;
						} else {
							hasOngoingSipCall = true;
						}
					}
				}
			}
		}

		try {
			SipCallSessionImpl callInfo = updateCallInfoFromStack(callId, null);
			Log.d(TAG, "Incoming call << for account " + accId);
			// Extra check if set reference counted is false ???
			if (!ongoingCallLock.isHeld()) {
				ongoingCallLock.acquire();
			}

			final String remContact = callInfo.getRemoteContact();
			callInfo.setIncoming(true);
			// notificationManager.showNotificationForCall(callInfo);

			// Auto answer feature
            SipProfile profile = pjService.getAccountForPjsipId(accId);
            Bundle extraHdr = new Bundle();
            fillRDataHeader("Call-Info", rdata, extraHdr);
			final int shouldAutoAnswer = pjService.service.shouldAutoAnswer(
					remContact, profile, extraHdr);
			onCallInComming(callInfo);
			Log.d(TAG, "Should I anto answer ? " + shouldAutoAnswer);
			if (shouldAutoAnswer >= 200) {
				// Automatically answer incoming calls with 200 or higher final
				// code
				pjService.callAnswer(callId, shouldAutoAnswer);
			} else {
				// Ring and inform remote about ringing with 180/RINGING
				pjService.callAnswer(callId, 180);

				// if (pjService.mediaManager != null) {
				// if (pjService.service.getGSMCallState() ==
				// TelephonyManager.CALL_STATE_IDLE
				// && !hasOngoingSipCall) {
				// pjService.mediaManager.startRing(remContact);
				// } else {
				// pjService.mediaManager.playInCallTone(MediaManager.TONE_CALL_WAITING);
				// }
				// }
				broadCastAndroidCallState("RINGING", remContact);
			}
			if (shouldAutoAnswer < 300) {
				// Or by api
				launchCallHandler(callInfo);
				Log.d(TAG, "Incoming call >>");
			}
		} catch (SameThreadException e) {
			// That's fine we are in a pjsip thread
		} finally {
			unlockCpu();
		}

	}

	/**
	 * Broadcast to android system that we currently have a phone call. This may
	 * be managed by other sip apps that want to keep track of incoming calls
	 * for example.
	 * 
	 * @param state
	 *            The state of the call
	 * @param number
	 *            The corresponding remote number
	 */
	private void broadCastAndroidCallState(String state, String number) {
		// Android normalized event
		if (!Compatibility.isCompatible(19)) {
			// Not allowed to do that from kitkat
			Intent intent = new Intent(ACTION_PHONE_STATE_CHANGED);
			intent.putExtra(TelephonyManager.EXTRA_STATE, state);
			if (number != null) {
				intent.putExtra(TelephonyManager.EXTRA_INCOMING_NUMBER, number);
			}
			pjService.service.sendBroadcast(intent,
					android.Manifest.permission.READ_PHONE_STATE);
		}
	}

	private void fillRDataHeader(String hdrName,
			SWIGTYPE_p_pjsip_rx_data rdata, Bundle out)
			throws SameThreadException {
		String valueHdr = PjSipService.pjStrToString(pjsua.get_rx_data_header(
				pjsua.pj_str_copy(hdrName), rdata));
		if (!TextUtils.isEmpty(valueHdr)) {
			out.putString(hdrName, valueHdr);
		}
	}

	/**
	 * Get list of calls session available.
	 * 
	 * @return List of calls.
	 */
	public SipCallSessionImpl[] getCalls() {
		if (callsList != null) {
			List<SipCallSessionImpl> calls = new ArrayList<SipCallSessionImpl>();

			synchronized (callsList) {
				for (int i = 0; i < callsList.size(); i++) {
					SipCallSessionImpl callInfo = getCallInfo(i);
					if (callInfo != null) {
						calls.add(callInfo);
					}
				}
			}
			return calls.toArray(new SipCallSessionImpl[calls.size()]);
		}
		return new SipCallSessionImpl[0];
	}

	/**
	 * Get call info for a given call id.
	 * 
	 * @param callId
	 *            the id of the call we want infos for
	 * @return the call session infos.
	 */
	public SipCallSessionImpl getCallInfo(Integer callId) {
		SipCallSessionImpl callInfo;
		synchronized (callsList) {
			callInfo = callsList.get(callId, null);
		}
		return callInfo;
	}

	public void stopService() {

		Threading.stopHandlerThread(handlerThread, true);
		handlerThread = null;
		msgHandler = null;

		// Ensure lock is released since this lock is a ref counted one.
		if (eventLock != null) {
			while (eventLock.isHeld()) {
				eventLock.release();
			}
		}
		if (ongoingCallLock != null) {
			if (ongoingCallLock.isHeld()) {
				ongoingCallLock.release();
			}
		}
	}

	/**
	 * Check if any of call infos indicate there is an active call in progress.
	 * 
	 * @see SipCallSession#isActive()
	 */
	public SipCallSession getActiveCallInProgress() {
		// Go through the whole list of calls and find the first active state.
		synchronized (callsList) {
			for (int i = 0; i < callsList.size(); i++) {
				SipCallSession callInfo = getCallInfo(i);
				if (callInfo != null && callInfo.isActive()) {
					return callInfo;
				}
			}
		}
		return null;
	}

	/**
	 * Broadcast the Headset button press event internally if there is any call
	 * in progress. TODO : register and unregister only while in call
	 */
	public boolean handleHeadsetButton() {
		final SipCallSession callInfo = getActiveCallInProgress();
		if (callInfo != null) {
			// Headset button has been pressed by user. If there is an
			// incoming call ringing the button will be used to answer the
			// call. If there is an ongoing call in progress the button will
			// be used to hangup the call or mute the microphone.
			int state = callInfo.getCallState();
			if (callInfo.isIncoming()
					&& (state == SipCallSession.InvState.INCOMING || state == SipCallSession.InvState.EARLY)) {
				if (pjService != null && pjService.service != null) {
					pjService.service.getExecutor().execute(new SipRunnable() {
						@Override
						protected void doRun() throws SameThreadException {

							pjService.callAnswer(callInfo.getCallId(),
									pjsip_status_code.PJSIP_SC_OK.swigValue());
						}
					});
				}
				return true;
			} else if (state == SipCallSession.InvState.INCOMING
					|| state == SipCallSession.InvState.EARLY
					|| state == SipCallSession.InvState.CALLING
					|| state == SipCallSession.InvState.CONFIRMED
					|| state == SipCallSession.InvState.CONNECTING) {
				//
				// In the Android phone app using the media button during
				// a call mutes the microphone instead of terminating the call.
				// We check here if this should be the behavior here or if
				// the call should be cleared.
				//
				if (pjService != null && pjService.service != null) {
					pjService.service.getExecutor().execute(new SipRunnable() {

						@Override
						protected void doRun() throws SameThreadException {
							if (mPreferedHeadsetAction == SipConfigManager.HEADSET_ACTION_CLEAR_CALL) {
								pjService.callHangup(callInfo.getCallId(), 0);
							} else if (mPreferedHeadsetAction == SipConfigManager.HEADSET_ACTION_HOLD) {
								pjService.callHold(callInfo.getCallId());
							} else if (mPreferedHeadsetAction == SipConfigManager.HEADSET_ACTION_MUTE) {
								pjService.mediaManager.toggleMute();
							}
						}
					});
				}
				return true;
			}
		}
		return false;
	}

	@Override
	protected void swigDirectorDisconnect() {
		// TODO Auto-generated method stub
		super.swigDirectorDisconnect();
	}

	@Override
	public void swigReleaseOwnership() {
		// TODO Auto-generated method stub
		super.swigReleaseOwnership();
	}

	@Override
	public void swigTakeOwnership() {
		// TODO Auto-generated method stub
		super.swigTakeOwnership();
	}

	@Override
	public void on_call_state(final int callId, pjsip_event e) {
		pjsua.css_on_call_state(callId, e);
		lockCpu();

		Log.d(TAG, "Call state <<");
		try {
			// Get current infos now on same thread cause fix has been done on
			// pj
			final SipCallSession callInfo = updateCallInfoFromStack(callId, e);
			int callState = callInfo.getCallState();
			Log.d("henry","on_call_state callState = " + callState);
			msgHandler.sendMessage(msgHandler.obtainMessage(ON_CALL_STATE,
					callInfo));
			// If disconnected immediate stop required stuffs
			if (callState == SipCallSession.InvState.DISCONNECTED) {
				if (pjService.mediaManager != null) {
					if (getRingingCall() == null) {
						pjService.mediaManager.stopRingAndUnfocus();
						pjService.mediaManager.resetSettings();
					}
				}
				if (ongoingCallLock != null && ongoingCallLock.isHeld()) {
					ongoingCallLock.release();
				}
				// Call is now ended
				pjService.stopDialtoneGenerator(callId);
				pjService.stopRecording(callId);
				pjService.stopPlaying(callId);
				pjService.stopWaittoneGenerator(callId);
				// removeCallInfoInList(callId);
			} else {
				if (ongoingCallLock != null && !ongoingCallLock.isHeld()) {
					ongoingCallLock.acquire();
				}
			}
			Log.d(TAG, "Call state >>");
		} catch (SameThreadException ex) {
			// We don't care about that we are at least in a pjsua thread
		} finally {
			// Unlock CPU anyway
			unlockCpu();
		}
	}

	private void removeCallInfoInList(int callId) {
		Log.d(TAG, "removeCallInfoInList call infos from the stack + callId = "
				+ callId);
		SipCallSessionImpl callInfo;
		synchronized (callsList) {
			callInfo = callsList.get(callId);
			if (callInfo != null) {
				callsList.remove(callId);
			}
		}
	}

	@Override
	public void on_call_tsx_state(int call_id,
			org.pjsip.pjsua.SWIGTYPE_p_pjsip_transaction tsx, pjsip_event e) {
		lockCpu();

		Log.d(TAG, "Call TSX state <<");
		try {
			updateCallInfoFromStack(call_id, e);
			Log.d(TAG, "Call TSX state >>");
		} catch (SameThreadException ex) {
			// We don't care about that we are at least in a pjsua thread
		} finally {
			// Unlock CPU anyway
			unlockCpu();
		}
	}

	@Override
	public void on_call_media_state(final int callId) {
		pjsua.css_on_call_media_state(callId);

		lockCpu();
		if (pjService.mediaManager != null) {
			// Do not unfocus here since we are probably in call.
			// Unfocus will be done anyway on call disconnect
			pjService.mediaManager.stopRing();
		}

		try {
			final SipCallSession callInfo = updateCallInfoFromStack(callId,
					null);
			/*
			 * Connect ports appropriately when media status is ACTIVE or REMOTE
			 * HOLD, otherwise we should NOT connect the ports.
			 */
			boolean connectToOtherCalls = false;
			int callConfSlot = callInfo.getConfPort();
			int mediaStatus = callInfo.getMediaStatus();
			if (mediaStatus == SipCallSession.MediaState.ACTIVE
					|| mediaStatus == SipCallSession.MediaState.REMOTE_HOLD) {

				connectToOtherCalls = true;
				pjsua.conf_connect(callConfSlot, 0);
				pjsua.conf_connect(0, callConfSlot);

				// Adjust software volume
				if (pjService.mediaManager != null) {
					pjService.mediaManager.setSoftwareVolume();
				}

				// Auto record
				if (mAutoRecordCalls && pjService.canRecord(callId)
						&& !pjService.isRecording(callId)) {
					pjService.startRecording(callId, SipManager.BITMASK_IN
							| SipManager.BITMASK_OUT);
				}
			}

			// Connects/disconnnect to other active calls (for conferencing).
			boolean hasOtherCall = false;
			synchronized (callsList) {
				if (callsList != null) {
					for (int i = 0; i < callsList.size(); i++) {
						SipCallSessionImpl otherCallInfo = getCallInfo(i);
						if (otherCallInfo != null && otherCallInfo != callInfo) {
							int otherMediaStatus = otherCallInfo
									.getMediaStatus();
							if (otherCallInfo.isActive()
									&& otherMediaStatus != SipCallSession.MediaState.NONE) {
								hasOtherCall = true;
								boolean connect = connectToOtherCalls
										&& (otherMediaStatus == SipCallSession.MediaState.ACTIVE || otherMediaStatus == SipCallSession.MediaState.REMOTE_HOLD);
								int otherCallConfSlot = otherCallInfo
										.getConfPort();
								if (connect) {
									pjsua.conf_connect(callConfSlot,
											otherCallConfSlot);
									pjsua.conf_connect(otherCallConfSlot,
											callConfSlot);
								} else {
									pjsua.conf_disconnect(callConfSlot,
											otherCallConfSlot);
									pjsua.conf_disconnect(otherCallConfSlot,
											callConfSlot);
								}
							}
						}
					}
				}
			}

			// Play wait tone
			if (mPlayWaittone) {
				if (mediaStatus == SipCallSession.MediaState.REMOTE_HOLD
						&& !hasOtherCall) {
					pjService.startWaittoneGenerator(callId);
				} else {
					pjService.stopWaittoneGenerator(callId);
				}
			}

			msgHandler.sendMessage(msgHandler.obtainMessage(ON_MEDIA_STATE,
					callInfo));
		} catch (SameThreadException e) {
			// Nothing to do we are in a pj thread here
		}

		unlockCpu();
	}

	@Override
	public void on_call_sdp_created(int call_id,
			SWIGTYPE_p_pjmedia_sdp_session sdp, pj_pool_t pool,
			SWIGTYPE_p_pjmedia_sdp_session rem_sdp) {
		// TODO Auto-generated method stub
		super.on_call_sdp_created(call_id, sdp, pool, rem_sdp);
	}

	@Override
	public void on_stream_created(int call_id, SWIGTYPE_p_pjmedia_stream strm,
			long stream_idx, SWIGTYPE_p_p_pjmedia_port p_port) {
		// TODO Auto-generated method stub
		super.on_stream_created(call_id, strm, stream_idx, p_port);
	}

	@Override
	public void on_stream_destroyed(int call_id,
			SWIGTYPE_p_pjmedia_stream strm, long stream_idx) {
		// TODO Auto-generated method stub
		super.on_stream_destroyed(call_id, strm, stream_idx);
	}

	@Override
	public void on_dtmf_digit(int call_id, int digit) {
		// TODO Auto-generated method stub
		super.on_dtmf_digit(call_id, digit);
	}

	@Override
	public void on_call_transfer_request(int call_id, pj_str_t dst,
			SWIGTYPE_p_pjsip_status_code code) {
		// TODO Auto-generated method stub
		super.on_call_transfer_request(call_id, dst, code);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.pjsip.pjsua.Callback#on_call_transfer_status(int, int,
	 * org.pjsip.pjsua.pj_str_t, int, org.pjsip.pjsua.SWIGTYPE_p_int)
	 */
	@Override
	public void on_call_transfer_status(int callId, int st_code,
			pj_str_t st_text, int final_, SWIGTYPE_p_int p_cont) {
		lockCpu();
		if ((st_code / 100) == 2) {
			pjsua.call_hangup(callId, 0, null, null);
		}
		unlockCpu();
	}

	@Override
	public void on_call_replace_request(int call_id,
			SWIGTYPE_p_pjsip_rx_data rdata, SWIGTYPE_p_int st_code,
			pj_str_t st_text) {
		// TODO Auto-generated method stub
		super.on_call_replace_request(call_id, rdata, st_code, st_text);
	}

	@Override
	public void on_call_replaced(int old_call_id, int new_call_id) {
		// TODO Auto-generated method stub
		super.on_call_replaced(old_call_id, new_call_id);
	}

	@Override
	public void on_reg_state(final int accountId) {
		Log.d(TAG, "accountId = " + accountId);
		lockCpu();
		pjService.service.getExecutor().execute(new SipRunnable() {
			@Override
			public void doRun() throws SameThreadException {
				// Update java infos
				pjService.updateProfileStateFromService(accountId);
			}
		});
		unlockCpu();
	}

	@Override
	public void on_buddy_state(int buddyId) {
		lockCpu();

		pjsua_buddy_info binfo = new pjsua_buddy_info();
		pjsua.buddy_get_info(buddyId, binfo);

		Log.d(TAG,
				"On buddy " + buddyId + " state " + binfo.getMonitor_pres()
						+ " state "
						+ PjSipService.pjStrToString(binfo.getStatus_text()));
		PresenceStatus presStatus = PresenceStatus.UNKNOWN;
		// First get info from basic status
		String presStatusTxt = PjSipService.pjStrToString(binfo
				.getStatus_text());
		boolean isDefaultTxt = presStatusTxt.equalsIgnoreCase("Online")
				|| presStatusTxt.equalsIgnoreCase("Offline");
		switch (binfo.getStatus()) {
		case PJSUA_BUDDY_STATUS_ONLINE:
			presStatus = PresenceStatus.ONLINE;
			break;
		case PJSUA_BUDDY_STATUS_OFFLINE:
			presStatus = PresenceStatus.OFFLINE;
			break;
		case PJSUA_BUDDY_STATUS_UNKNOWN:
		default:
			presStatus = PresenceStatus.UNKNOWN;
			break;
		}
		// Now get infos from RPID
		switch (binfo.getRpid().getActivity()) {
		case PJRPID_ACTIVITY_AWAY:
			presStatus = PresenceStatus.AWAY;
			if (isDefaultTxt) {
				presStatusTxt = "";
			}
			break;
		case PJRPID_ACTIVITY_BUSY:
			presStatus = PresenceStatus.BUSY;
			if (isDefaultTxt) {
				presStatusTxt = "";
			}
			break;
		default:
			break;
		}

		// pjService.service.presenceMgr.changeBuddyState(PjSipService.pjStrToString(binfo.getUri()),
		// binfo.getMonitor_pres(), presStatus, presStatusTxt);
		unlockCpu();
	}

	@Override
	public void on_pager(int callId, pj_str_t from, pj_str_t to,
			pj_str_t contact, pj_str_t mime_type, pj_str_t body) {
	}

	@Override
	public void on_pager2(int call_id, pj_str_t from, pj_str_t to,
			pj_str_t contact, pj_str_t mime_type, pj_str_t body,
			SWIGTYPE_p_pjsip_rx_data rdata) {
		// TODO Auto-generated method stub
		super.on_pager2(call_id, from, to, contact, mime_type, body, rdata);
	}

	@Override
	public void on_pager_status(int callId, pj_str_t to, pj_str_t body,
			pjsip_status_code status, pj_str_t reason) {
	}

	@Override
	public void on_pager_status2(int call_id, pj_str_t to, pj_str_t body,
			pjsip_status_code status, pj_str_t reason,
			SWIGTYPE_p_pjsip_tx_data tdata, SWIGTYPE_p_pjsip_rx_data rdata) {
		// TODO Auto-generated method stub
		super.on_pager_status2(call_id, to, body, status, reason, tdata, rdata);
	}

	@Override
	public void on_typing(int call_id, pj_str_t from, pj_str_t to,
			pj_str_t contact, int is_typing) {
		// TODO Auto-generated method stub
		super.on_typing(call_id, from, to, contact, is_typing);
	}

	@Override
	public void on_nat_detect(pj_stun_nat_detect_result res) {
		Log.d(TAG, "NAT TYPE DETECTED !!!" + res.getNat_type_name());
		if (pjService != null) {
			pjService.setDetectedNatType(res.getNat_type_name(),
					res.getStatus());
		}
	}

	@Override
	public pjsip_redirect_op on_call_redirected(int call_id, pj_str_t target) {
		Log.w(TAG,
				"Ask for redirection, not yet implemented, for now allow all "
						+ PjSipService.pjStrToString(target));
		return pjsip_redirect_op.PJSIP_REDIRECT_ACCEPT;
	}

	@Override
	public void on_mwi_info(int acc_id, pj_str_t mime_type, pj_str_t body) {
		lockCpu();
		// Treat incoming voice mail notification.

		String msg = PjSipService.pjStrToString(body);
		// Log.d(TAG, "We have a message :: " + acc_id + " | " +
		// mime_type.getPtr() + " | " + body.getPtr());

		boolean hasMessage = false;
		boolean hasSomeNumberOfMessage = false;
		int numberOfMessages = 0;
		// String accountNbr = "";

		String lines[] = msg.split("\\r?\\n");
		// Decapsulate the application/simple-message-summary
		// TODO : should we check mime-type?
		// rfc3842
		Pattern messWaitingPattern = Pattern.compile(
				".*Messages-Waiting[ \t]?:[ \t]?(yes|no).*",
				Pattern.CASE_INSENSITIVE);
		// Pattern messAccountPattern =
		// Pattern.compile(".*Message-Account[ \t]?:[ \t]?(.*)",
		// Pattern.CASE_INSENSITIVE);
		Pattern messVoiceNbrPattern = Pattern.compile(
				".*Voice-Message[ \t]?:[ \t]?([0-9]*)/[0-9]*.*",
				Pattern.CASE_INSENSITIVE);
		Pattern messFaxNbrPattern = Pattern.compile(
				".*Fax-Message[ \t]?:[ \t]?([0-9]*)/[0-9]*.*",
				Pattern.CASE_INSENSITIVE);

		for (String line : lines) {
			Matcher m;
			m = messWaitingPattern.matcher(line);
			if (m.matches()) {
				Log.w(TAG, "Matches : " + m.group(1));
				if ("yes".equalsIgnoreCase(m.group(1))) {
					Log.d(TAG, "Hey there is messages !!! ");
					hasMessage = true;

				}
				continue;
			}
			/*
			 * m = messAccountPattern.matcher(line); if(m.matches()) {
			 * accountNbr = m.group(1); Log.d(TAG, "VM acc : " + accountNbr);
			 * continue; }
			 */
			m = messVoiceNbrPattern.matcher(line);
			if (m.matches()) {
				try {
					numberOfMessages = Integer.parseInt(m.group(1));
					hasSomeNumberOfMessage = true;
				} catch (NumberFormatException e) {
					Log.w(TAG, "Not well formated number " + m.group(1));
				}
				Log.d(TAG, "Nbr : " + numberOfMessages);
				continue;
			}
			if (messFaxNbrPattern.matcher(line).matches()) {
				hasSomeNumberOfMessage = true;
			}
		}
		if (hasMessage && (numberOfMessages > 0 || !hasSomeNumberOfMessage)) {
			SipProfile acc = pjService.getAccountForPjsipId(acc_id);
			if (acc != null) {
				Log.d(TAG,
						acc_id + " -> Has found account "
								+ acc.getDefaultDomain() + " " + acc.id
								+ " >> " + acc.getProfileName());
			}
			Log.d(TAG, "We can show the voice messages notification");
			// notificationManager.showNotificationForVoiceMail(acc,
			// numberOfMessages);
		}
		unlockCpu();
	}

	@Override
	public void on_call_media_transport_state(int call_id,
			pjsua_med_tp_state_info info) {
		// TODO Auto-generated method stub
		super.on_call_media_transport_state(call_id, info);
	}

	@Override
	public int on_validate_audio_clock_rate(int clockRate) {
		if (pjService != null) {
			return pjService.validateAudioClockRate(clockRate);
		}
		return -1;
	}

	@Override
	public void on_setup_audio(int beforeInit) {
		Log.d(TAG, "on_setup_audio beforeInit = " + beforeInit);
		if (pjService != null) {
			pjService.setAudioInCall(beforeInit);
		}
	}

	@Override
	public void on_teardown_audio() {
		if (pjService != null) {
			pjService.unsetAudioInCall();
		}
	}

	@Override
	public int on_set_micro_source() {
		return mMicroSource;
	}

	@Override
	public int timer_schedule(int entry, int entryId, int time) {
		return TimerWrapper.schedule(entry, entryId, time);
	}

	@Override
	public int timer_cancel(int entry, int entryId) {
		return TimerWrapper.cancel(entry, entryId);
	}

	/**
	 * Update the call information from pjsip stack by calling pjsip primitives.
	 * 
	 * @param callId
	 *            The id to the call to update
	 * @param e
	 *            the pjsip_even that raised the update request
	 * @return The built sip call session. It's also stored in cache.
	 * @throws SameThreadException
	 *             if we are calling that from outside the pjsip thread. It's a
	 *             virtual exception to make sure not called from bad place.
	 */
	private SipCallSessionImpl updateCallInfoFromStack(Integer callId,
			pjsip_event e) throws SameThreadException {
		SipCallSessionImpl callInfo;
		Log.d(TAG, "Updating call infos from the stack + callId = " + callId);
		synchronized (callsList) {
			callInfo = callsList.get(callId);
			if (callInfo == null) {
				callInfo = new SipCallSessionImpl();
				callInfo.setCallId(callId);
			}
		}
		// We update session infos. callInfo is both in/out and will be updated
		PjSipCalls.updateSessionFromPj(callInfo, e, pjService.service);
		// We update from our current recording state
		callInfo.setIsRecording(pjService.isRecording(callId));
		callInfo.setCanRecord(pjService.canRecord(callId));
		synchronized (callsList) {
			// Re-add to list mainly for case newly added session
			callsList.put(callId, callInfo);
		}
		onCallInfoListChanged();
		return callInfo;
	}

	public SipCallSession getRingingCall() {
		// Go through the whole list of calls and find the first ringing state.
		synchronized (callsList) {
			for (int i = 0; i < callsList.size(); i++) {
				SipCallSession callInfo = getCallInfo(i);
				if (callInfo != null && callInfo.isActive()
						&& callInfo.isBeforeConfirmed()
						&& callInfo.isIncoming()) {
					return callInfo;
				}
			}
		}
		return null;
	}

	/**
	 * Update status of call recording info in call session info
	 * 
	 * @param callId
	 *            The call id to modify
	 * @param canRecord
	 *            if we can now record the call
	 * @param isRecording
	 *            if we are currently recording the call
	 */
	public void updateRecordingStatus(int callId, boolean canRecord,
			boolean isRecording) {
		SipCallSessionImpl callInfo = getCallInfo(callId);
		callInfo.setCanRecord(canRecord);
		callInfo.setIsRecording(isRecording);
		synchronized (callsList) {
			// Re-add it just to be sure
			callsList.put(callId, callInfo);
		}
		onCallInfoListChanged();
		onRecordStateChanged(callInfo);
	}

	private void onCallInfoListChanged() {
		pjService.service.onCallInfoListChanged();
	}
	private void onCallStateChanged(SipCallSession callinfo) {
		int state = callinfo.getCallState();
		if (state == SipCallSession.InvState.DISCONNECTED
				&& getActiveCallInProgress() != null) {
			removeCallInfoInList(callinfo.getCallId());
			return;
		}
		pjService.service.onCallStateChanged(state);
	}

	private void onRecordStateChanged(SipCallSession callinfo) {
		pjService.service.onRecordStateChanged(callinfo);
	}

	private void onCallLaunched(SipCallSession callinfo) {
		pjService.service.onCallLaunched(callinfo);
	}

	private void onCallInComming(SipCallSession callinfo) {
		pjService.service.onCallInComming(callinfo);
	}
}
