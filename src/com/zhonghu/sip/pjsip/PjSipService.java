package com.zhonghu.sip.pjsip;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;

import org.pjsip.pjsua.SWIGTYPE_p_pj_stun_auth_cred;
import org.pjsip.pjsua.csipsimple_config;
import org.pjsip.pjsua.dynamic_factory;
import org.pjsip.pjsua.pj_ice_sess_options;
import org.pjsip.pjsua.pj_pool_t;
import org.pjsip.pjsua.pj_qos_params;
import org.pjsip.pjsua.pj_str_t;
import org.pjsip.pjsua.pj_turn_tp_type;
import org.pjsip.pjsua.pjmedia_srtp_use;
import org.pjsip.pjsua.pjsip_ssl_method;
import org.pjsip.pjsua.pjsip_timer_setting;
import org.pjsip.pjsua.pjsip_tls_setting;
import org.pjsip.pjsua.pjsip_transport_type_e;
import org.pjsip.pjsua.pjsua;
import org.pjsip.pjsua.pjsuaConstants;
import org.pjsip.pjsua.pjsua_acc_info;
import org.pjsip.pjsua.pjsua_call_flag;
import org.pjsip.pjsua.pjsua_call_setting;
import org.pjsip.pjsua.pjsua_call_vid_strm_op;
import org.pjsip.pjsua.pjsua_config;
import org.pjsip.pjsua.pjsua_logging_config;
import org.pjsip.pjsua.pjsua_media_config;
import org.pjsip.pjsua.pjsua_msg_data;
import org.pjsip.pjsua.pjsua_transport_config;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;

import com.zhong.sip.pjsip.player.IPlayerHandler;
import com.zhong.sip.pjsip.player.SimpleWavPlayerHandler;
import com.zhonghu.sip.api.AccountListManager;
import com.zhonghu.sip.api.SipCallSession;
import com.zhonghu.sip.api.SipConfigManager;
import com.zhonghu.sip.api.SipManager;
import com.zhonghu.sip.api.SipManager.PresenceStatus;
import com.zhonghu.sip.api.SipProfile;
import com.zhonghu.sip.api.SipProfileState;
import com.zhonghu.sip.api.SipUri.ParsedSipContactInfos;
import com.zhonghu.sip.pjsip.earlylock.EarlyLockModule;
import com.zhonghu.sip.pjsip.recorder.IRecorderHandler;
import com.zhonghu.sip.pjsip.recorder.SimpleWavRecorderHandler;
import com.zhonghu.sip.pjsip.reghandler.RegHandlerModule;
import com.zhonghu.sip.pjsip.sipclf.SipClfModule;
import com.zhonghu.sip.pref.PreferencesProviderWrapper;
import com.zhonghu.sip.pref.PreferencesWrapper;
import com.zhonghu.sip.service.MediaManager;
import com.zhonghu.sip.service.SipService;
import com.zhonghu.sip.service.SipService.SipRunnable;
import com.zhonghu.sip.service.SipService.ToCall;
import com.zhonghu.sip.utils.ExtraPlugins;
import com.zhonghu.sip.utils.ExtraPlugins.DynCodecInfos;
import com.zhonghu.sip.utils.TimerWrapper;
import com.zhonghu.sip.utils.WizardUtils;

public class PjSipService {
	private static final String TAG = "SIP PjService";
	private static int DTMF_TONE_PAUSE_LENGTH = 300;
	private static int DTMF_TONE_WAIT_LENGTH = 2000;
	public SipService service;

	private boolean created = false;

	private boolean hasSipStack = false;
	private boolean sipStackIsCorrupted = false;
	private Integer localUdpAccPjId, localUdp6AccPjId, localTcpAccPjId,
			localTcp6AccPjId, localTlsAccPjId, localTls6AccPjId;
	public PreferencesProviderWrapper prefsWrapper;

	private Integer hasBeenHoldByGSM = null;
	private Integer hasBeenChangedRingerMode = null;

	public UAStateReceiver userAgentReceiver;
	public ZrtpStateReceiver zrtpReceiver;
	public MediaManager mediaManager;

	private Timer tasksTimer;
	private SparseArray<String> dtmfToAutoSend = new SparseArray<String>(5);
	private SparseArray<TimerTask> dtmfTasks = new SparseArray<TimerTask>(5);
	private SparseArray<PjStreamDialtoneGenerator> dtmfDialtoneGenerators = new SparseArray<PjStreamDialtoneGenerator>(
			5);
	private SparseArray<PjStreamDialtoneGenerator> waittoneGenerators = new SparseArray<PjStreamDialtoneGenerator>(
			5);
	private String mNatDetected = "";

	// Zhonghu:Add for record state

	// -------
	// Locks
	// -------

	public PjSipService() {

	}

	public void setService(SipService aService) {
		service = aService;
		prefsWrapper = service.getPrefs();
	}

	public boolean isCreated() {
		return created;
	}

	public boolean tryToLoadStack() {
		if (hasSipStack) {
			return true;
		}

		// File stackFile = NativeLibManager.getStackLibFile(service);
		if (!sipStackIsCorrupted) {
			try {
				// Try to load the stack
				// System.load(NativeLibManager.getBundledStackLibFile(service,
				// "libcrypto.so").getAbsolutePath());
				// System.load(NativeLibManager.getBundledStackLibFile(service,
				// "libssl.so").getAbsolutePath());
				// System.loadLibrary("crypto");
				// System.loadLibrary("ssl");
				System.loadLibrary(NativeLibManager.STD_LIB_NAME);
				System.loadLibrary(NativeLibManager.STACK_NAME);
				hasSipStack = true;
				return true;
			} catch (UnsatisfiedLinkError e) {
				// If it fails we probably are running on a special hardware
				Log.e(TAG,
						"We have a problem with the current stack.... NOT YET Implemented",
						e);
				hasSipStack = false;
				sipStackIsCorrupted = true;
				service.notifyUserOfMessage("Can't load native library. CPU arch invalid for this build");
				return false;
			} catch (Exception e) {
				Log.e(TAG, "We have a problem with the current stack....", e);
			}
		}
		return false;
	}

	// Start the sip stack according to current settings

	/**
	 * Start the sip stack Thread safing of this method must be ensured by upper
	 * layer Every calls from pjsip that require start/stop/getInfos from the
	 * underlying stack must be done on the same thread
	 */
	public boolean sipStart() throws SameThreadException {

		Log.d(TAG, "sipStart");
		if (!hasSipStack) {
			Log.e(TAG, "We have no sip stack, we can't start");
			return false;
		}

		// Ensure the stack is not already created or is being created
		if (!created) {
			// Pj timer
			TimerWrapper.create(service);
			int status;
			status = pjsua.create();

			Log.i(TAG, "Created " + status);
			// General config
			{
				pj_str_t[] stunServers = null;
				int stunServersCount = 0;
				pjsua_config cfg = new pjsua_config();
				pjsua_logging_config logCfg = new pjsua_logging_config();
				pjsua_media_config mediaCfg = new pjsua_media_config();
				csipsimple_config cssCfg = new csipsimple_config();

				// SERVICE CONFIG

				if (userAgentReceiver == null) {
					Log.d(TAG, "create ua receiver");
					userAgentReceiver = new UAStateReceiver();
					userAgentReceiver.initService(this);
				}
				userAgentReceiver.reconfigure(service);
				if (zrtpReceiver == null) {
					Log.d(TAG, "create zrtp receiver");
					zrtpReceiver = new ZrtpStateReceiver(this);
				}
				if (mediaManager == null) {
					mediaManager = new MediaManager(service);
				}
				mediaManager.startService();

				initModules();

				DTMF_TONE_PAUSE_LENGTH = prefsWrapper
						.getPreferenceIntegerValue(SipConfigManager.DTMF_PAUSE_TIME);
				DTMF_TONE_WAIT_LENGTH = prefsWrapper
						.getPreferenceIntegerValue(SipConfigManager.DTMF_WAIT_TIME);

				pjsua.setCallbackObject(userAgentReceiver);
				pjsua.setZrtpCallbackObject(zrtpReceiver);

				Log.d(TAG, "Attach is done to callback");

				// CSS CONFIG
				pjsua.csipsimple_config_default(cssCfg);
				cssCfg.setUse_compact_form_headers(prefsWrapper
						.getPreferenceBooleanValue(SipConfigManager.USE_COMPACT_FORM) ? pjsua.PJ_TRUE
						: pjsua.PJ_FALSE);
				cssCfg.setUse_compact_form_sdp(prefsWrapper
						.getPreferenceBooleanValue(SipConfigManager.USE_COMPACT_FORM) ? pjsua.PJ_TRUE
						: pjsua.PJ_FALSE);
				cssCfg.setUse_no_update(prefsWrapper
						.getPreferenceBooleanValue(SipConfigManager.FORCE_NO_UPDATE) ? pjsua.PJ_TRUE
						: pjsua.PJ_FALSE);
				cssCfg.setUse_noise_suppressor(prefsWrapper
						.getPreferenceBooleanValue(SipConfigManager.ENABLE_NOISE_SUPPRESSION) ? pjsua.PJ_TRUE
						: pjsua.PJ_FALSE);

				cssCfg.setTcp_keep_alive_interval(prefsWrapper
						.getTcpKeepAliveInterval());
				cssCfg.setTls_keep_alive_interval(prefsWrapper
						.getTlsKeepAliveInterval());

				cssCfg.setDisable_tcp_switch(prefsWrapper
						.getPreferenceBooleanValue(SipConfigManager.DISABLE_TCP_SWITCH) ? pjsuaConstants.PJ_TRUE
						: pjsuaConstants.PJ_FALSE);
				cssCfg.setDisable_rport(prefsWrapper
						.getPreferenceBooleanValue(SipConfigManager.DISABLE_RPORT) ? pjsuaConstants.PJ_TRUE
						: pjsuaConstants.PJ_FALSE);
				cssCfg.setAdd_bandwidth_tias_in_sdp(prefsWrapper
						.getPreferenceBooleanValue(SipConfigManager.ADD_BANDWIDTH_TIAS_IN_SDP) ? pjsuaConstants.PJ_TRUE
						: pjsuaConstants.PJ_FALSE);

				// Transaction timeouts
				int tsx_to = prefsWrapper
						.getPreferenceIntegerValue(SipConfigManager.TSX_T1_TIMEOUT);
				if (tsx_to > 0) {
					cssCfg.setTsx_t1_timeout(tsx_to);
				}
				tsx_to = prefsWrapper
						.getPreferenceIntegerValue(SipConfigManager.TSX_T2_TIMEOUT);
				if (tsx_to > 0) {
					cssCfg.setTsx_t2_timeout(tsx_to);
				}
				tsx_to = prefsWrapper
						.getPreferenceIntegerValue(SipConfigManager.TSX_T4_TIMEOUT);
				if (tsx_to > 0) {
					cssCfg.setTsx_t4_timeout(tsx_to);
				}
				tsx_to = prefsWrapper
						.getPreferenceIntegerValue(SipConfigManager.TSX_TD_TIMEOUT);
				if (tsx_to > 0) {
					cssCfg.setTsx_td_timeout(tsx_to);
				}

				// -- USE_ZRTP 1 is no_zrtp, 2 is create_zrtp
				File zrtpFolder = PreferencesWrapper.getZrtpFolder(service);
				if (zrtpFolder != null) {
					cssCfg.setUse_zrtp((prefsWrapper
							.getPreferenceIntegerValue(SipConfigManager.USE_ZRTP) > 1) ? pjsua.PJ_TRUE
							: pjsua.PJ_FALSE);
					cssCfg.setStorage_folder(pjsua.pj_str_copy(zrtpFolder
							.getAbsolutePath()));
				} else {
					cssCfg.setUse_zrtp(pjsua.PJ_FALSE);
					cssCfg.setStorage_folder(pjsua.pj_str_copy(""));
				}

				Map<String, DynCodecInfos> availableCodecs = ExtraPlugins
						.buidAudioCodec(service);
				dynamic_factory[] cssCodecs = cssCfg.getExtra_aud_codecs();
				int i = 0;
				for (Entry<String, DynCodecInfos> availableCodec : availableCodecs
						.entrySet()) {
					DynCodecInfos dyn = availableCodec.getValue();
					Log.d(TAG, "dyn = " + dyn.toString());
					if (!TextUtils.isEmpty(dyn.libraryPath)) {
						cssCodecs[i].setShared_lib_path(pjsua
								.pj_str_copy(dyn.libraryPath));
						cssCodecs[i++].setInit_factory_name(pjsua
								.pj_str_copy(dyn.factoryInitFunction));
					}
				}
				cssCfg.setExtra_aud_codecs_cnt(i);

				// Audio implementation
				int implementation = prefsWrapper
						.getPreferenceIntegerValue(SipConfigManager.AUDIO_IMPLEMENTATION);
				if (implementation == SipConfigManager.AUDIO_IMPLEMENTATION_OPENSLES) {
					dynamic_factory audImp = cssCfg.getAudio_implementation();
					audImp.setInit_factory_name(pjsua
							.pj_str_copy("pjmedia_opensl_factory"));
					File openslLib = NativeLibManager.getBundledStackLibFile(
							service, "libpj_opensl_dev.so");
					audImp.setShared_lib_path(pjsua.pj_str_copy(openslLib
							.getAbsolutePath()));
					cssCfg.setAudio_implementation(audImp);
					Log.d(TAG, "Use OpenSL-ES implementation");
				}

				// MAIN CONFIG
				pjsua.config_default(cfg);
				cfg.setCb(pjsuaConstants.WRAPPER_CALLBACK_STRUCT);
				cfg.setUser_agent(pjsua.pj_str_copy(prefsWrapper
						.getUserAgent(service)));
				// We need at least one thread
				int threadCount = prefsWrapper
						.getPreferenceIntegerValue(SipConfigManager.THREAD_COUNT);
				if (threadCount <= 0) {
					threadCount = 1;
				}
				cfg.setThread_cnt(threadCount);
				cfg.setUse_srtp(getUseSrtp());
				cfg.setSrtp_secure_signaling(0);
				cfg.setNat_type_in_sdp(0);
				Log.d(TAG, "setNat_type_in_sdp");
				pjsip_timer_setting timerSetting = cfg.getTimer_setting();
				int minSe = prefsWrapper
						.getPreferenceIntegerValue(SipConfigManager.TIMER_MIN_SE);
				int sessExp = prefsWrapper
						.getPreferenceIntegerValue(SipConfigManager.TIMER_SESS_EXPIRES);
				if (minSe <= sessExp && minSe >= 90) {
					timerSetting.setMin_se(minSe);
					timerSetting.setSess_expires(sessExp);
					cfg.setTimer_setting(timerSetting);
				}
				// DNS
				if (prefsWrapper.enableDNSSRV() && !prefsWrapper.useIPv6()) {
					pj_str_t[] nameservers = getNameservers();
					if (nameservers != null) {
						cfg.setNameserver_count(nameservers.length);
						cfg.setNameserver(nameservers);
					} else {
						cfg.setNameserver_count(0);
					}
				}
				Log.d(TAG, "setNameserver_count");

				// STUN
				boolean isStunEnabled = prefsWrapper
						.getPreferenceBooleanValue(SipConfigManager.ENABLE_STUN);
				if (isStunEnabled) {
					String[] servers = prefsWrapper.getPreferenceStringValue(
							SipConfigManager.STUN_SERVER).split(",");
					cfg.setStun_srv_cnt(servers.length);
					stunServers = cfg.getStun_srv();
					for (String server : servers) {
						Log.d(TAG, "add server " + server.trim());
						stunServers[stunServersCount] = pjsua
								.pj_str_copy(server.trim());
						stunServersCount++;
					}
					cfg.setStun_srv(stunServers);
					cfg.setStun_map_use_stun2(boolToPjsuaConstant(prefsWrapper
							.getPreferenceBooleanValue(SipConfigManager.ENABLE_STUN2)));
				}
				Log.d(TAG, "setStun_map_use_stun2");

				// LOGGING CONFIG
				pjsua.logging_config_default(logCfg);
				logCfg.setConsole_level(prefsWrapper.getLogLevel());
				logCfg.setLevel(prefsWrapper.getLogLevel());
				logCfg.setMsg_logging(pjsuaConstants.PJ_TRUE);

				if (prefsWrapper.getPreferenceBooleanValue(
						SipConfigManager.LOG_USE_DIRECT_FILE, false)) {
					File outFile = PreferencesWrapper
							.getLogsFile(service, true);
					if (outFile != null) {
						logCfg.setLog_filename(pjsua.pj_str_copy(outFile
								.getAbsolutePath()));
						logCfg.setLog_file_flags(0x1108 /* PJ_O_APPEND */);
					}
				}
				Log.d(TAG, "setLog_file_flags");

				// MEDIA CONFIG
				pjsua.media_config_default(mediaCfg);

				// For now only this cfg is supported
				mediaCfg.setChannel_count(1);
				mediaCfg.setSnd_auto_close_time(prefsWrapper.getAutoCloseTime());
				// Echo cancellation
				mediaCfg.setEc_tail_len(prefsWrapper.getEchoCancellationTail());
				int echoMode = prefsWrapper
						.getPreferenceIntegerValue(SipConfigManager.ECHO_MODE);
				long clockRate = prefsWrapper.getClockRate(mediaManager);
				if (clockRate > 16000
						&& echoMode == SipConfigManager.ECHO_MODE_WEBRTC_M) {
					// WebRTC mobile does not allow higher that 16kHz for now
					// TODO : warn user about this point
					echoMode = SipConfigManager.ECHO_MODE_SIMPLE;
				}
				mediaCfg.setEc_options(echoMode);
				mediaCfg.setNo_vad(boolToPjsuaConstant(!prefsWrapper
						.getPreferenceBooleanValue(SipConfigManager.ENABLE_VAD)));
				mediaCfg.setQuality(prefsWrapper.getMediaQuality());
				mediaCfg.setClock_rate(clockRate);
				mediaCfg.setAudio_frame_ptime(prefsWrapper
						.getPreferenceIntegerValue(SipConfigManager.SND_PTIME));

				// Disabled ? because only one thread enabled now for battery
				// perfs on normal state
				int mediaThreadCount = prefsWrapper
						.getPreferenceIntegerValue(SipConfigManager.MEDIA_THREAD_COUNT);
				mediaCfg.setThread_cnt(mediaThreadCount);
				boolean hasOwnIoQueue = prefsWrapper
						.getPreferenceBooleanValue(SipConfigManager.HAS_IO_QUEUE);
				if (threadCount <= 0) {
					// Global thread count is 0, so don't use sip one anyway
					hasOwnIoQueue = false;
				}
				mediaCfg.setHas_ioqueue(boolToPjsuaConstant(hasOwnIoQueue));
				Log.d(TAG, "setHas_ioqueue");

				// ICE
				boolean iceEnabled = prefsWrapper
						.getPreferenceBooleanValue(SipConfigManager.ENABLE_ICE);
				mediaCfg.setEnable_ice(boolToPjsuaConstant(iceEnabled));
				if (iceEnabled) {
					pj_ice_sess_options iceOpts = mediaCfg.getIce_opt();
					boolean aggressiveIce = prefsWrapper
							.getPreferenceBooleanValue(SipConfigManager.ICE_AGGRESSIVE);
					iceOpts.setAggressive(boolToPjsuaConstant(aggressiveIce));
				}
				Log.d(TAG, "setAggressive");

				// TURN
				boolean isTurnEnabled = prefsWrapper
						.getPreferenceBooleanValue(SipConfigManager.ENABLE_TURN);
				if (isTurnEnabled) {
					SWIGTYPE_p_pj_stun_auth_cred creds = mediaCfg
							.getTurn_auth_cred();
					mediaCfg.setEnable_turn(boolToPjsuaConstant(isTurnEnabled));
					mediaCfg.setTurn_server(pjsua.pj_str_copy(prefsWrapper
							.getTurnServer()));
					pjsua.set_turn_credentials(
							pjsua.pj_str_copy(prefsWrapper
									.getPreferenceStringValue(SipConfigManager.TURN_USERNAME)),
							pjsua.pj_str_copy(prefsWrapper
									.getPreferenceStringValue(SipConfigManager.TURN_PASSWORD)),
							pjsua.pj_str_copy("*"), creds);
					// Normally this step is useless as manipulating a pointer
					// in C memory at this point, but in case this changes
					// reassign
					mediaCfg.setTurn_auth_cred(creds);
					int turnTransport = prefsWrapper
							.getPreferenceIntegerValue(SipConfigManager.TURN_TRANSPORT);
					if (turnTransport != 0) {
						switch (turnTransport) {
						case 1:
							mediaCfg.setTurn_conn_type(pj_turn_tp_type.PJ_TURN_TP_UDP);
							break;
						case 2:
							mediaCfg.setTurn_conn_type(pj_turn_tp_type.PJ_TURN_TP_TCP);
							break;
						case 3:
							mediaCfg.setTurn_conn_type(pj_turn_tp_type.PJ_TURN_TP_TLS);
							break;
						default:
							break;
						}
					}
					// mediaCfg.setTurn_conn_type(value);
				} else {
					mediaCfg.setEnable_turn(pjsua.PJ_FALSE);
				}
				Log.d(TAG, "setEnable_turn");

				// INITIALIZE
				status = pjsua.csipsimple_init(cfg, logCfg, mediaCfg, cssCfg,
						service);
				if (status != pjsuaConstants.PJ_SUCCESS) {
					String msg = "Fail to init pjsua "
							+ pjStrToString(pjsua.get_error_message(status));
					Log.e(TAG, msg);
					service.notifyUserOfMessage(msg);
					cleanPjsua();
					return false;
				}
			}
			Log.d(TAG, "csipsimple_init");

			// Add transports
			{
				// TODO : allow to configure local accounts.

				// We need a local account for each transport
				// to not have the
				// application lost when direct call to the IP

				// UDP
				if (prefsWrapper.isUDPEnabled()) {
					int udpPort = prefsWrapper.getUDPTransportPort();
					localUdpAccPjId = createLocalTransportAndAccount(
							pjsip_transport_type_e.PJSIP_TRANSPORT_UDP, udpPort);
					if (localUdpAccPjId == null) {
						cleanPjsua();
						return false;
					}
					// UDP v6
					if (prefsWrapper.useIPv6()) {
						localUdp6AccPjId = createLocalTransportAndAccount(
								pjsip_transport_type_e.PJSIP_TRANSPORT_UDP6,
								udpPort == 0 ? udpPort : udpPort + 10);
					}
				}
				Log.d(TAG, "UDP");

				// TCP
				if (prefsWrapper.isTCPEnabled()) {
					int tcpPort = prefsWrapper.getTCPTransportPort();
					localTcpAccPjId = createLocalTransportAndAccount(
							pjsip_transport_type_e.PJSIP_TRANSPORT_TCP, tcpPort);
					if (localTcpAccPjId == null) {
						cleanPjsua();
						return false;
					}

					// TCP v6
					if (prefsWrapper.useIPv6()) {
						localTcp6AccPjId = createLocalTransportAndAccount(
								pjsip_transport_type_e.PJSIP_TRANSPORT_TCP6,
								tcpPort == 0 ? tcpPort : tcpPort + 10);
					}
				}
				Log.d(TAG, "TCP");

				// TLS
				if (prefsWrapper.isTLSEnabled()) {
					int tlsPort = prefsWrapper.getTLSTransportPort();
					localTlsAccPjId = createLocalTransportAndAccount(
							pjsip_transport_type_e.PJSIP_TRANSPORT_TLS, tlsPort);
					if (localTlsAccPjId == null) {
						cleanPjsua();
						return false;
					}

					// TLS v6
					if (prefsWrapper.useIPv6()) {
						localTls6AccPjId = createLocalTransportAndAccount(
								pjsip_transport_type_e.PJSIP_TRANSPORT_TLS6,
								tlsPort == 0 ? tlsPort : tlsPort + 10);
					}
				}
			}
			Log.d(TAG, "TLS");

			// Add pjsip modules
			for (PjsipModule mod : pjsipModules.values()) {
				mod.onBeforeStartPjsip();
			}

			// Initialization is done, now start pjsua
			status = pjsua.start();

			if (status != pjsua.PJ_SUCCESS) {
				String msg = "Fail to start pjsip  "
						+ pjStrToString(pjsua.get_error_message(status));
				Log.e(TAG, msg);
				service.notifyUserOfMessage(msg);
				cleanPjsua();
				return false;
			}

			// Init media codecs
			initCodecs();
			setCodecsPriorities();

			created = true;
			Log.d(TAG, "sip start ok");

			return true;
		}

		return false;
	}

	/**
	 * Stop sip service
	 * 
	 * @return true if stop has been performed
	 */
	public boolean sipStop() throws SameThreadException {
		Log.d(TAG, ">> SIP STOP <<");

		if (getActiveCallInProgress() != null) {
			Log.e(TAG, "We have a call in progress... DO NOT STOP !!!");
			// TODO : queue quit on end call;
			return false;
		}
		if (created) {
			cleanPjsua();
		}
		if (tasksTimer != null) {
			tasksTimer.cancel();
			tasksTimer.purge();
			tasksTimer = null;
		}
		return true;
	}

	/**
	 * Make a call
	 * 
	 * @param callee
	 *            remote contact ot call If not well formated we try to add
	 *            domain name of the default account
	 */
	public int makeCall(String callee, long accountId, Bundle b)
			throws SameThreadException {
		Log.d(TAG, "makeCall callee = " + callee);
		if (!created) {
			return -1;
		}
		final ToCall toCall = sanitizeSipUri(callee, accountId);
		if (toCall != null) {
			Log.d(TAG, "toCall.getCallee() = " + toCall.getCallee());
			pj_str_t uri = pjsua.pj_str_copy(toCall.getCallee());
			// Nothing to do with this values
			byte[] userData = new byte[1];
			int[] callId = new int[1];
			pjsua_call_setting cs = new pjsua_call_setting();
			pjsua_msg_data msgData = new pjsua_msg_data();
			int pjsuaAccId = toCall.getPjsipAccountId();
			// Call settings to add video
			pjsua.call_setting_default(cs);
			cs.setAud_cnt(1);
			cs.setVid_cnt(0);
			if (b != null && b.getBoolean(SipCallSession.OPT_CALL_VIDEO, false)) {
				cs.setVid_cnt(1);
			}
			cs.setFlag(0);

			pj_pool_t pool = pjsua.pool_create("call_tmp", 512, 512);

			// Msg data to add headers
			pjsua.msg_data_init(msgData);
			pjsua.csipsimple_init_acc_msg_data(pool, pjsuaAccId, msgData);
			if (b != null) {
				Bundle extraHeaders = b
						.getBundle(SipCallSession.OPT_CALL_EXTRA_HEADERS);
				if (extraHeaders != null) {
					for (String key : extraHeaders.keySet()) {
						try {
							String value = extraHeaders.getString(key);
							if (!TextUtils.isEmpty(value)) {
								int res = pjsua
										.csipsimple_msg_data_add_string_hdr(
												pool, msgData,
												pjsua.pj_str_copy(key),
												pjsua.pj_str_copy(value));
								if (res == pjsuaConstants.PJ_SUCCESS) {
									Log.e(TAG, "Failed to add Xtra hdr (" + key
											+ " : " + value
											+ ") probably not X- header");
								}
							}
						} catch (Exception e) {
							Log.e(TAG, "Invalid header value for key : " + key);
						}
					}
				}
			}
			int status = pjsua.call_make_call(pjsuaAccId, uri, cs, userData,
					msgData, callId);
			Log.d(TAG, "status = " + status + "  pjsuaConstants.PJ_SUCCESS = "
					+ pjsuaConstants.PJ_SUCCESS);
			if (status == pjsuaConstants.PJ_SUCCESS) {
				dtmfToAutoSend.put(callId[0], toCall.getDtmf());
				Log.d(TAG,
						"DTMF - Store for " + callId[0] + " - "
								+ toCall.getDtmf());
			}
			pjsua.pj_pool_release(pool);
			return status;
		} else {
		}
		return -1;
	}

	/**
	 * Transform a string callee into a valid sip uri in the context of an
	 * account
	 * 
	 * @param callee
	 *            the callee string to call
	 * @return ToCall object representing what to call and using which account
	 */
	private ToCall sanitizeSipUri(String callee, long accountId) {
		// accountId is the id in term of csipsimple database
		// pjsipAccountId is the account id in term of pjsip adding
		int pjsipAccountId = (int) SipProfile.INVALID_ID;

		// Fake a sip profile empty to get it's profile state
		// Real get from db will be done later
		SipProfile account = new SipProfile();
		account.id = accountId;
		SipProfileState profileState = getProfileState(account);
		long finalAccountId = accountId;

		// If this is an invalid account id
		if (accountId == SipProfile.INVALID_ID
				|| !profileState.isAddedToStack()) {
			int defaultPjsipAccount = pjsua.acc_get_default();
			boolean valid = false;
			account = getAccountForPjsipId(defaultPjsipAccount);
			if (account != null) {
				profileState = getProfileState(account);
				valid = profileState.isAddedToStack();
			}
			// If default account is not active
			if (!valid) {
				List<SipProfileState> profileStateList = AccountListManager
						.getInstance().getAllProfileState();

				for (SipProfileState ps : profileStateList) {
					if (ps.isValidForCall()) {
						finalAccountId = ps.getAccountId();
						pjsipAccountId = ps.getPjsuaId();
						break;
					}
				}
			} else {
				// Use the default account
				finalAccountId = profileState.getAccountId();
				pjsipAccountId = profileState.getPjsuaId();
			}
		} else {
			// If the account is valid
			pjsipAccountId = profileState.getPjsuaId();
		}

		if (pjsipAccountId == SipProfile.INVALID_ID) {
			Log.e(TAG, "Unable to find a valid account for this call");
			return null;
		}

		// Check integrity of callee field
		// Get real account information now
		account = service.getAccount((int) finalAccountId);
		ParsedSipContactInfos finalCallee = account.formatCalleeNumber(callee);
		String digitsToAdd = null;
		if (!TextUtils.isEmpty(finalCallee.userName)
				&& (finalCallee.userName.contains(",") || finalCallee.userName
						.contains(";"))) {
			int commaIndex = finalCallee.userName.indexOf(",");
			int semiColumnIndex = finalCallee.userName.indexOf(";");
			if (semiColumnIndex > 0 && semiColumnIndex < commaIndex) {
				commaIndex = semiColumnIndex;
			}
			digitsToAdd = finalCallee.userName.substring(commaIndex);
			finalCallee.userName = finalCallee.userName
					.substring(0, commaIndex);
		}

		Log.d(TAG, "will call " + finalCallee);

		if (pjsua.verify_sip_url(finalCallee.toString(false)) == 0) {
			// In worse worse case, find back the account id for uri.. but
			// probably useless case
			if (pjsipAccountId == SipProfile.INVALID_ID) {
				pjsipAccountId = pjsua.acc_find_for_outgoing(pjsua
						.pj_str_copy(finalCallee.toString(false)));
			}
			return new ToCall(pjsipAccountId, finalCallee.toString(true),
					digitsToAdd);
		}

		return null;
	}

	/**
	 * Answer a call
	 * 
	 * @param callId
	 *            the id of the call to answer to
	 * @param code
	 *            the status code to send in the response
	 * @return
	 */
	public int callAnswer(int callId, int code) throws SameThreadException {
		if (created) {
			pjsua_call_setting cs = new pjsua_call_setting();
			pjsua.call_setting_default(cs);
			cs.setAud_cnt(1);
			cs.setVid_cnt(prefsWrapper
					.getPreferenceBooleanValue(SipConfigManager.USE_VIDEO) ? 1
					: 0);
			cs.setFlag(0);
			return pjsua.call_answer2(callId, cs, code, null, null);
			// return pjsua.call_answer(callId, code, null, null);
		}
		return -1;
	}

	public int callHold(int callId) throws SameThreadException {
		if (created) {
			return pjsua.call_set_hold(callId, null);
		}
		return -1;
	}

	/**
	 * Hangup a call
	 * 
	 * @param callId
	 *            the id of the call to hangup
	 * @param code
	 *            the status code to send in the response
	 * @return
	 */
	public int callHangup(int callId, int code) throws SameThreadException {
		int result;
		Log.d(TAG,"callHangup callId" + callId + "code = " + code);
		if (created) {
			Log.d(TAG,"callId = " + callId + "code = " + code);
			result = pjsua.call_hangup(callId, code, null, null);
		}
		result = -1;

		return result;
	}

	public int updateCallOptions(int callId, Bundle options) {
		// TODO : if more options we should redesign this part.
		if (options.containsKey(SipCallSession.OPT_CALL_VIDEO)) {
			boolean add = options.getBoolean(SipCallSession.OPT_CALL_VIDEO);
			SipCallSession ci = getCallInfo(callId);
			if (add && ci.mediaHasVideo()) {
				// We already have one video running -- refuse to send another
				return -1;
			} else if (!add && !ci.mediaHasVideo()) {
				// We have no current video, no way to remove.
				return -1;
			}
			pjsua_call_vid_strm_op op = add ? pjsua_call_vid_strm_op.PJSUA_CALL_VID_STRM_ADD
					: pjsua_call_vid_strm_op.PJSUA_CALL_VID_STRM_REMOVE;
			if (!add) {
				// TODO : manage remove case
			}
			return pjsua.call_set_vid_strm(callId, op, null);
		}

		return -1;
	}

	private void cleanPjsua() throws SameThreadException {
		Log.d(TAG, "Detroying...");
		// This will destroy all accounts so synchronize with accounts
		// management lock
		// long flags = 1; /*< Lazy disconnect : only RX */
		// Try with TX & RX if network is considered as available
		long flags = 0;
		if (!prefsWrapper.isValidConnectionForOutgoing(false)) {
			// If we are current not valid for outgoing,
			// it means that we don't want the network for SIP now
			// so don't use RX | TX to not consume data at all
			flags = 3;
		}
		pjsua.csipsimple_destroy(flags);
		AccountListManager.getInstance().removeAllProfileState();
		if (userAgentReceiver != null) {
			userAgentReceiver.stopService();
			userAgentReceiver = null;
		}
		if (mediaManager != null) {
			mediaManager.stopService();
			mediaManager = null;
		}

		TimerWrapper.destroy();

		created = false;
	}

	public SipCallSession getActiveCallInProgress() {
		if (created && userAgentReceiver != null) {
			return userAgentReceiver.getActiveCallInProgress();
		}
		return null;
	}

	// Recorder
	private SparseArray<List<IRecorderHandler>> callRecorders = new SparseArray<List<IRecorderHandler>>();

	/**
	 * Start recording of a call.
	 * 
	 * @param callId
	 *            the call id of the call to record
	 * @throws SameThreadException
	 *             virtual exception to be sure we are calling this from correct
	 *             thread
	 */
	public void startRecording(int callId, int way) throws SameThreadException {
		Log.d(TAG, "startRecording callId = " + callId);
		// Make sure we are in a valid state for recording
		if (!canRecord(callId)) {
			return;
		}
		// Sanitize call way : if 0 assume all
		if (way == 0) {
			way = SipManager.BITMASK_ALL;
		}

		try {
			File recFolder = getRecoderFileDic();
			Log.d(TAG, "recFolder = " + recFolder.toString());
			IRecorderHandler recoder = new SimpleWavRecorderHandler(
					getCallInfo(callId), recFolder, way);
			List<IRecorderHandler> recordersList = callRecorders.get(callId,
					new ArrayList<IRecorderHandler>());
			recordersList.add(recoder);
			callRecorders.put(callId, recordersList);
			recoder.startRecording();
			userAgentReceiver.updateRecordingStatus(callId, false, true);
		} catch (IOException e) {
			// service.notifyUserOfMessage(R.string.cant_write_file);
		} catch (RuntimeException e) {
			Log.e(TAG, "Impossible to record ", e);
		}
	}

	private File getRecoderFileDic() {
		File recFolderDefault = PreferencesProviderWrapper
				.getRecordsFolder(service);
		String foldString = prefsWrapper.getPreferenceStringValue(
				SipConfigManager.RECORDER_FILE_PATH,
				recFolderDefault.toString());
		File dir = new File(foldString);
		if (!dir.exists()) {
			dir.mkdirs();
		}
		if (!dir.canWrite()) {
			return recFolderDefault;
		}
		return dir;

	}

	/**
	 * Are we currently recording the call?
	 * 
	 * @param callId
	 *            The call id to test for a recorder presence
	 * @return true if recording this call
	 */
	public boolean isRecording(int callId) throws SameThreadException {
		List<IRecorderHandler> recorders = callRecorders.get(callId, null);
		if (recorders == null) {
			return false;
		}
		return recorders.size() > 0;
	}

	/**
	 * Can we record for this call id ?
	 * 
	 * @param callId
	 *            The call id to record to a file
	 * @return true if seems to be possible to record this call.
	 */
	public boolean canRecord(int callId) {
		if (!created) {
			// Not possible to record if service not here
			return false;
		}
		SipCallSession callInfo = getCallInfo(callId);
		if (callInfo == null) {
			// Not possible to record if no call info for given call id
			return false;
		}
		int ms = callInfo.getMediaStatus();
		if (ms != SipCallSession.MediaState.ACTIVE
				&& ms != SipCallSession.MediaState.REMOTE_HOLD) {
			// We can't record if media state not running on our side
			return false;
		}
		return true;
	}

	/**
	 * Stop recording of a call.
	 * 
	 * @param callId
	 *            the call to stop record for.
	 * @throws SameThreadException
	 *             virtual exception to be sure we are calling this from correct
	 *             thread
	 */
	public void stopRecording(int callId) throws SameThreadException {
		Log.d(TAG, "stopRecording callId = " + callId);

		if (!created) {
			return;
		}
		List<IRecorderHandler> recoders = callRecorders.get(callId, null);
		if (recoders != null) {
			for (IRecorderHandler recoder : recoders) {
				recoder.stopRecording();
				// Broadcast to other apps the a new sip record has been done
				SipCallSession callInfo = getPublicCallInfo(callId);
				Intent it = new Intent(SipManager.ACTION_SIP_CALL_RECORDED);
				it.putExtra(SipManager.EXTRA_CALL_INFO, callInfo);
				recoder.fillBroadcastWithInfo(it);
				service.sendBroadcast(it, SipManager.PERMISSION_USE_SIP);
			}
			// In first case we drop everything
			callRecorders.delete(callId);
			userAgentReceiver.updateRecordingStatus(callId, true, false);
		}
	}

	// Stream players
	// We use a list for future possible extensions. For now api only manages
	// one
	private SparseArray<List<IPlayerHandler>> callPlayers = new SparseArray<List<IPlayerHandler>>();

	/**
	 * Play one wave file in call stream.
	 * 
	 * @param filePath
	 *            The path to the file we'd like to play
	 * @param callId
	 *            The call id we want to play to. Even if we only use
	 *            {@link SipManager#BITMASK_IN} this must correspond to some
	 *            call since it's used to identify internally created player.
	 * @param way
	 *            The way we want to play this file to. Bitmasked value that
	 *            could be compounded of {@link SipManager#BITMASK_IN} (read
	 *            local) and {@link SipManager#BITMASK_OUT} (read to remote
	 *            party of the call)
	 * @throws SameThreadException
	 *             virtual exception to be sure we are calling this from correct
	 *             thread
	 */
	public void playWaveFile(String filePath, int callId, int way)
			throws SameThreadException {
		if (!created) {
			return;
		}
		// Stop any current player
		stopPlaying(callId);
		if (TextUtils.isEmpty(filePath)) {
			// Nothing to do if we have not file path
			return;
		}
		if (way == 0) {
			way = SipManager.BITMASK_ALL;
		}

		// We create a new player conf port.
		try {
			IPlayerHandler player = new SimpleWavPlayerHandler(
					getCallInfo(callId), filePath, way);
			List<IPlayerHandler> playersList = callPlayers.get(callId,
					new ArrayList<IPlayerHandler>());
			playersList.add(player);
			callPlayers.put(callId, playersList);

			player.startPlaying();
		} catch (IOException e) {
			// TODO : add a can't read file txt
			// service.notifyUserOfMessage(R.string.cant_write_file);
		} catch (RuntimeException e) {
			Log.e(TAG, "Impossible to play file", e);
		}
	}

	public void startWaittoneGenerator(int callId) {
		if (waittoneGenerators.get(callId) == null) {
			waittoneGenerators.put(callId, new PjStreamDialtoneGenerator(
					callId, false));
		}
		waittoneGenerators.get(callId).startPjMediaWaitingTone();
	}

	public void stopWaittoneGenerator(int callId) {
		if (waittoneGenerators.get(callId) != null) {
			waittoneGenerators.get(callId).stopDialtoneGenerator();
			waittoneGenerators.put(callId, null);
		}
	}

	/**
	 * Stop eventual player for a given call.
	 * 
	 * @param callId
	 *            the call id corresponding to player previously created with
	 *            {@link #playWaveFile(String, int, int)}
	 * @throws SameThreadException
	 *             virtual exception to be sure we are calling this from correct
	 *             thread
	 */
	public void stopPlaying(int callId) throws SameThreadException {
		List<IPlayerHandler> players = callPlayers.get(callId, null);
		if (players != null) {
			for (IPlayerHandler player : players) {
				player.stopPlaying();
			}
			callPlayers.delete(callId);
		}
	}

	public int validateAudioClockRate(int aClockRate) {
		if (mediaManager != null) {
			return mediaManager.validateAudioClockRate(aClockRate);
		}
		return -1;
	}

	public void setAudioInCall(int beforeInit) {
		if (mediaManager != null) {
			mediaManager.setAudioInCall(beforeInit == pjsuaConstants.PJ_TRUE);
		}
	}

	public void unsetAudioInCall() {

		if (mediaManager != null) {
			mediaManager.unsetAudioInCall();
		}
	}

	public SipCallSession getPublicCallInfo(int callId) {
		SipCallSession internalCallSession = getCallInfo(callId);
		if (internalCallSession == null) {
			return null;
		}
		return new SipCallSession(internalCallSession);
	}

	public SipCallSession getCallInfo(int callId) {
		if (created/* && !creating */&& userAgentReceiver != null) {
			SipCallSession callInfo = userAgentReceiver.getCallInfo(callId);
			return callInfo;
		}
		return null;
	}

	public void stopDialtoneGenerator(int callId) {
		if (dtmfDialtoneGenerators.get(callId) != null) {
			dtmfDialtoneGenerators.get(callId).stopDialtoneGenerator();
			dtmfDialtoneGenerators.put(callId, null);
		}
		if (dtmfToAutoSend.get(callId) != null) {
			dtmfToAutoSend.put(callId, null);
		}
		if (dtmfTasks.get(callId) != null) {
			dtmfTasks.get(callId).cancel();
			dtmfTasks.put(callId, null);
		}
	}

	private Map<String, PjsipModule> pjsipModules = new HashMap<String, PjsipModule>();

	private void initModules() {
		// TODO : this should be more modular and done from outside
		PjsipModule rModule = new RegHandlerModule();
		pjsipModules.put(RegHandlerModule.class.getCanonicalName(), rModule);

		rModule = new SipClfModule();
		pjsipModules.put(SipClfModule.class.getCanonicalName(), rModule);

		rModule = new EarlyLockModule();
		pjsipModules.put(EarlyLockModule.class.getCanonicalName(), rModule);

		for (PjsipModule mod : pjsipModules.values()) {
			mod.setContext(service);
		}
	}

	public interface PjsipModule {
		/**
		 * Set the android context for the module. Could be usefull to get
		 * preferences for examples.
		 * 
		 * @param ctxt
		 *            android context
		 */
		void setContext(Context ctxt);

		/**
		 * Here pjsip endpoint should have this module added.
		 */
		void onBeforeStartPjsip();

		/**
		 * This is fired just after account was added to pjsip and before will
		 * be registered. Modules does not necessarily implement something here.
		 * 
		 * @param pjId
		 *            the pjsip id of the added account.
		 * @param acc
		 *            the profile account.
		 */
		void onBeforeAccountStartRegistration(int pjId, SipProfile acc);
	}

	private static ArrayList<String> codecs = new ArrayList<String>();
	private static boolean codecs_initialized = false;

	/**
	 * Retrieve codecs from pjsip stack and store it inside preference storage
	 * so that it can be retrieved in the interface view
	 * 
	 * @throws SameThreadException
	 */
	private void initCodecs() throws SameThreadException {

		synchronized (codecs) {
			if (!codecs_initialized) {
				int nbrCodecs, i;
				// Audio codecs
				nbrCodecs = pjsua.codecs_get_nbr();
				for (i = 0; i < nbrCodecs; i++) {
					String codecId = pjStrToString(pjsua.codecs_get_id(i));
					codecs.add(codecId);
					// Log.d(TAG, "Added codec " + codecId);
				}
				// Set it in prefs if not already set correctly
				// prefsWrapper.setCodecList(codecs);
				codecs_initialized = true;
				// We are now always capable of tls and srtp !
				prefsWrapper.setLibCapability(
						PreferencesProviderWrapper.LIB_CAP_TLS, true);
				prefsWrapper.setLibCapability(
						PreferencesProviderWrapper.LIB_CAP_SRTP, true);
			}
		}

	}

	/**
	 * Reset the list of codecs stored
	 */
	public static void resetCodecs() {
		synchronized (codecs) {
			if (codecs_initialized) {
				codecs.clear();
				codecs_initialized = false;
			}
		}
	}

	/**
	 * Set the codec priority in pjsip stack layer based on preference store
	 * 
	 * @throws SameThreadException
	 */
	private void setCodecsPriorities() throws SameThreadException {
		ConnectivityManager cm = ((ConnectivityManager) service
				.getSystemService(Context.CONNECTIVITY_SERVICE));

		synchronized (codecs) {
			if (codecs_initialized) {
				NetworkInfo ni = cm.getActiveNetworkInfo();
				if (ni != null) {

					StringBuilder audioSb = new StringBuilder();
					audioSb.append("Audio codecs : ");
					String currentBandType = prefsWrapper
							.getPreferenceStringValue(
									SipConfigManager.getBandTypeKey(
											ni.getType(), ni.getSubtype()),
									SipConfigManager.CODEC_WB);
					Log.d(TAG, "currentBandType  = " + currentBandType);
					synchronized (codecs) {
						for (String codec : codecs) {

							short aPrio = prefsWrapper.getCodecPriority(codec,
									currentBandType, "0");

							buffCodecLog(audioSb, codec, aPrio);
							pj_str_t codecStr = pjsua.pj_str_copy(codec);
							if (aPrio >= 0) {
								pjsua.codec_set_priority(codecStr, aPrio);
							}
							String codecKey = SipConfigManager.getCodecKey(
									codec,
									SipConfigManager.FRAMES_PER_PACKET_SUFFIX);
							Integer frmPerPacket = SipConfigManager
									.getPreferenceIntegerValue(service,
											codecKey);
							if (frmPerPacket != null && frmPerPacket > 0) {
								Log.v(TAG, "Set codec " + codec + " fpp : "
										+ frmPerPacket);
								pjsua.codec_set_frames_per_packet(codecStr,
										frmPerPacket);
							}
						}

					}
				}

			}
		}
	}

	/**
	 * Append log for the codec in String builder
	 * 
	 * @param sb
	 *            the buffer to be appended with the codec info
	 * @param codec
	 *            the codec name
	 * @param prio
	 *            the priority of the codec
	 */
	private void buffCodecLog(StringBuilder sb, String codec, short prio) {
		if (prio > 0) {
			sb.append(codec);
			sb.append(" (");
			sb.append(prio);
			sb.append(") - ");
		}
	}

	public void setNoSnd() throws SameThreadException {
		if (!created) {
			return;
		}
		pjsua.set_no_snd_dev();
	}

	public void setSnd() throws SameThreadException {
		if (!created) {
			return;
		}
		pjsua.set_snd_dev(0, 0);
	}

	private pjmedia_srtp_use getUseSrtp() {
		try {
			int use_srtp = Integer.parseInt(prefsWrapper
					.getPreferenceStringValue(SipConfigManager.USE_SRTP));
			if (use_srtp >= 0) {
				return pjmedia_srtp_use.swigToEnum(use_srtp);
			}
		} catch (NumberFormatException e) {
			Log.e(TAG, "Transport port not well formated");
		}
		return pjmedia_srtp_use.PJMEDIA_SRTP_DISABLED;
	}

	// Config subwrapper
	private pj_str_t[] getNameservers() {
		pj_str_t[] nameservers = null;

		if (prefsWrapper.enableDNSSRV()) {
			String prefsDNS = prefsWrapper
					.getPreferenceStringValue(SipConfigManager.OVERRIDE_NAMESERVER);
			if (TextUtils.isEmpty(prefsDNS)) {
				String ipv6Escape = "[ \\[\\]]";
				String ipv4Matcher = "^\\d+(\\.\\d+){3}$";
				String ipv6Matcher = "^[0-9a-f]+(:[0-9a-f]*)+:[0-9a-f]+$";
				List<String> dnsServers;
				List<String> dnsServersAll = new ArrayList<String>();
				List<String> dnsServersIpv4 = new ArrayList<String>();
				for (int i = 1; i <= 2; i++) {
					String dnsName = prefsWrapper.getSystemProp("net.dns" + i);
					if (!TextUtils.isEmpty(dnsName)) {
						dnsName = dnsName.replaceAll(ipv6Escape, "");
						if (!TextUtils.isEmpty(dnsName)
								&& !dnsServersAll.contains(dnsName)) {
							if (dnsName.matches(ipv4Matcher)
									|| dnsName.matches(ipv6Matcher)) {
								dnsServersAll.add(dnsName);
							}
							if (dnsName.matches(ipv4Matcher)) {
								dnsServersIpv4.add(dnsName);
							}
						}
					}
				}

				if (dnsServersIpv4.size() > 0) {
					// Prefer pure ipv4 list since pjsua doesn't manage ipv6
					// resolution yet
					dnsServers = dnsServersIpv4;
				} else {
					dnsServers = dnsServersAll;
				}

				if (dnsServers.size() == 0) {
					// This is the ultimate fallback... we should never be there
					// !
					nameservers = new pj_str_t[] { pjsua
							.pj_str_copy("127.0.0.1") };
				} else if (dnsServers.size() == 1) {
					nameservers = new pj_str_t[] { pjsua.pj_str_copy(dnsServers
							.get(0)) };
				} else {
					nameservers = new pj_str_t[] {
							pjsua.pj_str_copy(dnsServers.get(0)),
							pjsua.pj_str_copy(dnsServers.get(1)) };
				}
			} else {
				nameservers = new pj_str_t[] { pjsua.pj_str_copy(prefsDNS) };
			}
		}
		return nameservers;
	}

	public static String pjStrToString(pj_str_t pjStr) {
		try {
			if (pjStr != null) {
				// If there's utf-8 ptr length is possibly lower than slen
				int len = pjStr.getSlen();
				if (len > 0 && pjStr.getPtr() != null) {
					// Be robust to smaller length detected
					if (pjStr.getPtr().length() < len) {
						len = pjStr.getPtr().length();
					}

					if (len > 0) {
						return pjStr.getPtr().substring(0, len);
					}
				}
			}
		} catch (StringIndexOutOfBoundsException e) {
			Log.e(TAG, "Impossible to retrieve string from pjsip ", e);
		}
		return "";
	}

	/**
	 * Get the signal level
	 * 
	 * @param port
	 *            The pjsip port to get signal from
	 * @return an encoded long with rx level on higher byte and tx level on
	 *         lower byte
	 */
	public long getRxTxLevel(int port) {
		long[] rx_level = new long[1];
		long[] tx_level = new long[1];
		pjsua.conf_get_signal_level(port, tx_level, rx_level);
		return (rx_level[0] << 8 | tx_level[0]);
	}

	private Integer createLocalTransportAndAccount(pjsip_transport_type_e type,
			int port) throws SameThreadException {
		Integer transportId = createTransport(type, port);
		return createLocalAccount(transportId);
	}

	private Integer createLocalAccount(Integer transportId)
			throws SameThreadException {
		if (transportId == null) {
			return null;
		}
		int[] p_acc_id = new int[1];
		pjsua.acc_add_local(transportId, pjsua.PJ_FALSE, p_acc_id);
		return p_acc_id[0];
	}

	private int getOnlineForStatus(PresenceStatus presence) {
		return presence == PresenceStatus.ONLINE ? 1 : 0;
	}

	public static long getAccountIdForPjsipId(Context ctxt, int pjId) {
		long accId = SipProfile.INVALID_ID;

		List<SipProfileState> profileStateList = AccountListManager
				.getInstance().getAllProfileState();

		for (SipProfileState profileState : profileStateList) {
			int pjsuaId = profileState.getPjsuaId();
			Log.d(TAG, "Found pjsua " + pjsuaId + " searching " + pjId);
			if (pjsuaId == pjId) {
				accId = profileState.getAccountId();
				break;
			}

		}
		return accId;
	}

	public SipProfile getAccountForPjsipId(int pjId) {
		long accId = getAccountIdForPjsipId(service, pjId);
		if (accId == SipProfile.INVALID_ID) {
			return null;
		} else {
			return service.getAccount(accId);
		}
	}

	public boolean setAccountRegistration(SipProfile account, int renew,
			boolean forceReAdd) throws SameThreadException {

		int status = -1;

		Log.e(TAG, "setAccountRegistration start");
		Log.d(TAG, "account = " + account.toString() + "renew = " + renew
				+ "forceReAdd = " + forceReAdd);
		if (!created || account == null) {
			Log.d(TAG, "PJSIP is not started here, nothing can be done");
			return false;
		}
		if (account.id == SipProfile.INVALID_ID) {
			Log.w(TAG, "Trying to set registration on a deleted account");
			return false;
		}

		SipProfileState profileState = getProfileState(account);

		Log.d(TAG, "profileState = " + profileState);

		// invalid
		if (profileState.getWizard().equalsIgnoreCase(
				WizardUtils.LOCAL_WIZARD_TAG)) {
			if (renew == 0) {
				return false;
			}
		}
		// }
		// In case of already added, we have to act finely
		// If it's local we can just consider that we have to re-add account
		// since it will actually just touch the account with a modify
		if (profileState != null
				&& profileState.isAddedToStack()
				&& !profileState.getWizard().equalsIgnoreCase(
						WizardUtils.LOCAL_WIZARD_TAG)) {
			// The account is already there in accounts list
			AccountListManager.getInstance().removeProfileState(account.id);
			Log.d(TAG,
					"Account already added to stack, remove and re-load or delete");
			if (renew == 1) {
				if (forceReAdd) {
					status = pjsua.acc_del(profileState.getPjsuaId());
					addAccount(account);
				} else {
					pjsua.acc_set_online_status(profileState.getPjsuaId(),
							getOnlineForStatus(service.getPresence()));
					status = pjsua.acc_set_registration(
							profileState.getPjsuaId(), renew);
				}
			} else {
				// if(status == pjsuaConstants.PJ_SUCCESS && renew == 0) {
				Log.d(TAG, "Delete account !!");
				status = pjsua.acc_del(profileState.getPjsuaId());
				AccountListManager.getInstance().removeProfile(account.id);
			}
		} else {
			if (renew == 1) {
				addAccount(account);
			} else {
				Log.w(TAG, "Ask to unregister an unexisting account !!"
						+ account.id);
			}
		}
		// PJ_SUCCESS = 0
		return status == 0;

	}

	public boolean addAccount(SipProfile profile) throws SameThreadException {
		Log.d(TAG, "addAccount  profile = " + profile.toString() );

		int status = pjsuaConstants.PJ_FALSE;
		if (!created) {
			Log.e(TAG, "PJSIP is not started here, nothing can be done");
			return status == pjsuaConstants.PJ_SUCCESS;
		}
		PjSipAccount account = new PjSipAccount(profile);
		account.applyExtraParams(service);

		// Force the use of a transport
		/*
		 * switch (account.transport) { case SipProfile.TRANSPORT_UDP: if
		 * (udpTranportId != null) {
		 * //account.cfg.setTransport_id(udpTranportId); } break; case
		 * SipProfile.TRANSPORT_TCP: if (tcpTranportId != null) { //
		 * account.cfg.setTransport_id(tcpTranportId); } break; case
		 * SipProfile.TRANSPORT_TLS: if (tlsTransportId != null) { //
		 * account.cfg.setTransport_id(tlsTransportId); } break; default: break;
		 * }
		 */
		SipProfileState currentAccountStatus = getProfileState(profile);
		account.cfg.setRegister_on_acc_add(pjsuaConstants.PJ_FALSE);
		Log.d(TAG, "currentAccountStatus.isAddedToStack() = "
				+ currentAccountStatus.isAddedToStack());

		if (currentAccountStatus != null
				&& currentAccountStatus.isAddedToStack()) {
			pjsua.csipsimple_set_acc_user_data(
					currentAccountStatus.getPjsuaId(), account.css_cfg);
			status = pjsua.acc_modify(currentAccountStatus.getPjsuaId(),
					account.cfg);
			beforeAccountRegistration(currentAccountStatus.getPjsuaId(),
					profile);
			// ContentValues cv = new ContentValues();
			// cv.put(SipProfileState.ADDED_STATUS, status);
			// service.getContentResolver().update(
			// ContentUris.withAppendedId(
			// SipProfile.ACCOUNT_STATUS_ID_URI_BASE, profile.id),
			// cv, null, null);

			if (!account.wizard.equalsIgnoreCase(WizardUtils.LOCAL_WIZARD_TAG)) {
				// Re register
				if (status == pjsuaConstants.PJ_SUCCESS) {
					status = pjsua.acc_set_registration(
							currentAccountStatus.getPjsuaId(), 1);
					if (status == pjsuaConstants.PJ_SUCCESS) {
						pjsua.acc_set_online_status(
								currentAccountStatus.getPjsuaId(), 1);
					}
				}
			}
		} else {
			int[] accId = new int[1];

			Log.d(TAG, "WizardUtils.LOCAL_WIZARD_TAG  account.transport = "
					+ account.wizard);

			if (account.wizard.equalsIgnoreCase(WizardUtils.LOCAL_WIZARD_TAG)) {
				Log.d(TAG, "WizardUtils.LOCAL_WIZARD_TAG  account.transport = "
						+ account.transport);
				// We already have local account by default
				// For now consider we are talking about UDP one
				// In the future local account should be set per transport
				switch (account.transport) {
				case SipProfile.TRANSPORT_UDP:
					accId[0] = prefsWrapper.useIPv6() ? localUdp6AccPjId
							: localUdpAccPjId;
					break;
				case SipProfile.TRANSPORT_TCP:
					accId[0] = prefsWrapper.useIPv6() ? localTcp6AccPjId
							: localTcpAccPjId;
					break;
				case SipProfile.TRANSPORT_TLS:
					accId[0] = prefsWrapper.useIPv6() ? localTls6AccPjId
							: localTlsAccPjId;
					break;
				default:
					// By default use UDP
					accId[0] = localUdpAccPjId;
					break;
				}

				pjsua.csipsimple_set_acc_user_data(accId[0], account.css_cfg);
				// TODO : use video cfg here
				// nCfg.setVid_in_auto_show(pjsuaConstants.PJ_TRUE);
				// nCfg.setVid_out_auto_transmit(pjsuaConstants.PJ_TRUE);
				// status = pjsua.acc_modify(accId[0], nCfg);
			} else {
				// Cause of standard account different from local account :)
				status = pjsua.acc_add(account.cfg, pjsuaConstants.PJ_FALSE,
						accId);
				pjsua.csipsimple_set_acc_user_data(accId[0], account.css_cfg);
				beforeAccountRegistration(accId[0], profile);
				pjsua.acc_set_registration(accId[0], 1);
			}

			if (status == pjsuaConstants.PJ_SUCCESS) {
				SipProfileState ps = new SipProfileState(profile);
				ps.setAddedStatus(status);
				ps.setPjsuaId(accId[0]);
				AccountListManager.getInstance().addNewProfileState(ps);
				pjsua.acc_set_online_status(accId[0], 1);
			}
		}
		return status == pjsuaConstants.PJ_SUCCESS;
	}

	void beforeAccountRegistration(int pjId, SipProfile profile) {
		for (PjsipModule mod : pjsipModules.values()) {
			mod.onBeforeAccountStartRegistration(pjId, profile);
		}
	}

	/**
	 * Utility to create a transport
	 * 
	 * @return d or -1 if failed
	 */
	private Integer createTransport(pjsip_transport_type_e type, int port)
			throws SameThreadException {
		pjsua_transport_config cfg = new pjsua_transport_config();
		int[] tId = new int[1];
		int status;
		pjsua.transport_config_default(cfg);
		cfg.setPort(port);

		if (type.equals(pjsip_transport_type_e.PJSIP_TRANSPORT_TLS)) {
			pjsip_tls_setting tlsSetting = cfg.getTls_setting();

			/*
			 * TODO : THIS IS OBSOLETE -- remove from UI String serverName =
			 * prefsWrapper
			 * .getPreferenceStringValue(SipConfigManager.TLS_SERVER_NAME); if
			 * (!TextUtils.isEmpty(serverName)) {
			 * tlsSetting.setServer_name(pjsua.pj_str_copy(serverName)); }
			 */
			String caListFile = prefsWrapper
					.getPreferenceStringValue(SipConfigManager.CA_LIST_FILE);
			if (!TextUtils.isEmpty(caListFile)) {
				tlsSetting.setCa_list_file(pjsua.pj_str_copy(caListFile));
			}

			String certFile = prefsWrapper
					.getPreferenceStringValue(SipConfigManager.CERT_FILE);
			if (!TextUtils.isEmpty(certFile)) {
				tlsSetting.setCert_file(pjsua.pj_str_copy(certFile));
			}

			String privKey = prefsWrapper
					.getPreferenceStringValue(SipConfigManager.PRIVKEY_FILE);

			if (!TextUtils.isEmpty(privKey)) {
				tlsSetting.setPrivkey_file(pjsua.pj_str_copy(privKey));
			}

			String tlsPwd = prefsWrapper
					.getPreferenceStringValue(SipConfigManager.TLS_PASSWORD);
			if (!TextUtils.isEmpty(tlsPwd)) {
				tlsSetting.setPassword(pjsua.pj_str_copy(tlsPwd));
			}

			boolean checkClient = prefsWrapper
					.getPreferenceBooleanValue(SipConfigManager.TLS_VERIFY_CLIENT);
			tlsSetting.setVerify_client(checkClient ? 1 : 0);

			tlsSetting.setMethod(pjsip_ssl_method.swigToEnum(prefsWrapper
					.getTLSMethod()));
			tlsSetting.setProto(0);
			boolean checkServer = prefsWrapper
					.getPreferenceBooleanValue(SipConfigManager.TLS_VERIFY_SERVER);
			tlsSetting.setVerify_server(checkServer ? 1 : 0);

			cfg.setTls_setting(tlsSetting);
		}

		if (prefsWrapper.getPreferenceBooleanValue(SipConfigManager.ENABLE_QOS)) {
			Log.d(TAG, "Activate qos for this transport");
			pj_qos_params qosParam = cfg.getQos_params();
			qosParam.setDscp_val((short) prefsWrapper
					.getPreferenceIntegerValue(SipConfigManager.DSCP_VAL));
			qosParam.setFlags((short) 1); // DSCP
			cfg.setQos_params(qosParam);
		}

		status = pjsua.transport_create(type, cfg, tId);
		if (status != pjsuaConstants.PJ_SUCCESS) {
			String errorMsg = pjStrToString(pjsua.get_error_message(status));
			String msg = "Fail to create " + errorMsg + " (" + status + ")";
			Log.e(TAG, msg);
			if (status == 120098) { /* Already binded */
				msg = "Already binded"/*
									 * service.getString(R.string.
									 * another_application_use_sip_port)
									 */;
			}
			service.notifyUserOfMessage(msg);
			return null;
		}
		return tId[0];
	}

	public void confAdjustTxLevel(int port, float value)
			throws SameThreadException {
		if (created && userAgentReceiver != null) {
			pjsua.conf_adjust_tx_level(port, value);
		}
	}

	public void confAdjustRxLevel(int port, float value)
			throws SameThreadException {
		if (created && userAgentReceiver != null) {
			pjsua.conf_adjust_rx_level(port, value);
		}
	}

	public void setEchoCancellation(boolean on) throws SameThreadException {
		if (created && userAgentReceiver != null) {
			Log.d(TAG, "set echo cancelation " + on);
			pjsua.set_ec(
					on ? prefsWrapper.getEchoCancellationTail() : 0,
					prefsWrapper
							.getPreferenceIntegerValue(SipConfigManager.ECHO_MODE));
		}
	}

	public void adjustStreamVolume(int stream, int direction, int flags) {
		if (mediaManager != null) {
			mediaManager.adjustStreamVolume(stream, direction,
					AudioManager.FLAG_SHOW_UI);
		}
	}

	public void setStreamVolume(int streamType, int index) {
		if (mediaManager != null) {
			mediaManager.setStreamVolume(streamType, index, 0);
		}

	}

	public void silenceRinger() {
		if (mediaManager != null) {
			mediaManager.stopRingAndUnfocus();
		}
	}

	/**
	 * Get the dynamic state of the profile
	 * 
	 * @param account
	 *            the sip profile from database. Important field is id.
	 * @return the dynamic sip profile state
	 */
	/**
	 * Get the dynamic state of the profile
	 * 
	 * @param account
	 *            the sip profile from database. Important field is id.
	 * @return the dynamic sip profile state
	 */
	public SipProfileState getProfileState(SipProfile account) {
		if (!created || account == null) {
			return null;
		}
		if (account.id == SipProfile.INVALID_ID) {
			return null;
		}
		SipProfileState accountInfo = new SipProfileState(account);
		SipProfileState state = AccountListManager.getInstance()
				.getProfileState(account.id);
		if (state != null) {
			accountInfo = state;
		}
		return accountInfo;
	}

	protected void setDetectedNatType(String natName, int status) {
		// Maybe we will need to treat status to eliminate some set (depending
		// of unknown string fine for 3rd part dev)
		mNatDetected = natName;
	}

	private static int boolToPjsuaConstant(boolean v) {
		return v ? pjsuaConstants.PJ_TRUE : pjsuaConstants.PJ_FALSE;
	}

	/**
	 * Synchronize content provider backend from pjsip stack
	 * 
	 * @param pjsuaId
	 *            the pjsua id of the account to synchronize
	 * @throws SameThreadException
	 */
	public void updateProfileStateFromService(int pjsuaId)
			throws SameThreadException {

		if (!created) {
			return;
		}
		long accId = getAccountIdForPjsipId(service, pjsuaId);
		Log.d(TAG, "Update profile from service for " + pjsuaId + " aka in db "
				+ accId);
		if (accId != SipProfile.INVALID_ID) {
			int success = pjsuaConstants.PJ_FALSE;
			pjsua_acc_info pjAccountInfo;
			pjAccountInfo = new pjsua_acc_info();
			success = pjsua.acc_get_info(pjsuaId, pjAccountInfo);
			if (success == pjsuaConstants.PJ_SUCCESS && pjAccountInfo != null) {
				ContentValues cv = new ContentValues();
				try {
					// Should be fine : status code are coherent with RFC
					// status codes
					cv.put(SipProfileState.STATUS_CODE, pjAccountInfo
							.getStatus().swigValue());
				} catch (IllegalArgumentException e) {
					cv.put(SipProfileState.STATUS_CODE,
							SipCallSession.StatusCode.INTERNAL_SERVER_ERROR);
				}
				cv.put(SipProfileState.STATUS_TEXT,
						pjStrToString(pjAccountInfo.getStatus_text()));
				cv.put(SipProfileState.EXPIRES, pjAccountInfo.getExpires());
				AccountListManager.getInstance().updateProfileState(accId, cv);
				Log.d(TAG, "Profile state UP : " + cv);
			}
		} else {
			Log.e(TAG, "Trying to update not added account " + pjsuaId);
		}

	}

	public void onGSMStateChanged(int state, String incomingNumber)
			throws SameThreadException {

		// need add
	}

	/**
	 * Send a dtmf signal to a call
	 * 
	 * @param callId
	 *            the call to send the signal
	 * @param keyCode
	 *            the keyCode to send (android style)
	 * @return
	 */
	public int sendDtmf(int callId, int keyCode) throws SameThreadException {
		if (!created) {
			return -1;
		}
		String keyPressed = "";
		// Since some device (xoom...) are apparently buggy with key character
		// map loading...
		// we have to do crappy thing here
		if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
			keyPressed = Integer.toString(keyCode - KeyEvent.KEYCODE_0);
		} else if (keyCode == KeyEvent.KEYCODE_POUND) {
			keyPressed = "#";
		} else if (keyCode == KeyEvent.KEYCODE_STAR) {
			keyPressed = "*";
		} else {
			// Fallback... should never be there if using visible dialpad, but
			// possible using keyboard
			KeyCharacterMap km = KeyCharacterMap.load(KeyCharacterMap.NUMERIC);
			keyPressed = Integer.toString(km.getNumber(keyCode));
		}
		return sendDtmf(callId, keyPressed);
	}

	private int sendDtmf(final int callId, String keyPressed)
			throws SameThreadException {
		if (TextUtils.isEmpty(keyPressed)) {
			return pjsua.PJ_SUCCESS;
		}

		if (pjsua.call_is_active(callId) != pjsuaConstants.PJ_TRUE) {
			return -1;
		}
		if (pjsua.call_has_media(callId) != pjsuaConstants.PJ_TRUE) {
			return -1;
		}

		String dtmfToDial = keyPressed;
		String remainingDtmf = "";
		int pauseBeforeRemaining = 0;
		boolean foundSeparator = false;
		if (keyPressed.contains(",") || keyPressed.contains(";")) {
			dtmfToDial = "";
			for (int i = 0; i < keyPressed.length(); i++) {
				char c = keyPressed.charAt(i);
				if (!foundSeparator) {
					if (c == ',' || c == ';') {
						pauseBeforeRemaining += (c == ',') ? DTMF_TONE_PAUSE_LENGTH
								: DTMF_TONE_WAIT_LENGTH;
						foundSeparator = true;
					} else {
						dtmfToDial += c;
					}
				} else {
					if ((c == ',' || c == ';')
							&& TextUtils.isEmpty(remainingDtmf)) {
						pauseBeforeRemaining += (c == ',') ? DTMF_TONE_PAUSE_LENGTH
								: DTMF_TONE_WAIT_LENGTH;
					} else {
						remainingDtmf += c;
					}
				}
			}

		}

		int res = 0;
		if (!TextUtils.isEmpty(dtmfToDial)) {
			pj_str_t pjKeyPressed = pjsua.pj_str_copy(dtmfToDial);
			res = -1;
			if (prefsWrapper.useSipInfoDtmf()) {
				res = pjsua.send_dtmf_info(callId, pjKeyPressed);
				Log.d(TAG, "Has been sent DTMF INFO : " + res);
			} else {
				if (!prefsWrapper.forceDtmfInBand()) {
					// Generate using RTP
					res = pjsua.call_dial_dtmf(callId, pjKeyPressed);
					Log.d(TAG, "Has been sent in RTP DTMF : " + res);
				}

				if (res != pjsua.PJ_SUCCESS && !prefsWrapper.forceDtmfRTP()) {
					// Generate using analogic inband
					if (dtmfDialtoneGenerators.get(callId) == null) {
						dtmfDialtoneGenerators.put(callId,
								new PjStreamDialtoneGenerator(callId));
					}
					res = dtmfDialtoneGenerators.get(callId)
							.sendPjMediaDialTone(dtmfToDial);
					Log.d(TAG, "Has been sent DTMF analogic : " + res);
				}
			}
		}

		// Finally, push remaining DTMF in the future
		if (!TextUtils.isEmpty(remainingDtmf)) {
			dtmfToAutoSend.put(callId, remainingDtmf);

			if (tasksTimer == null) {
				tasksTimer = new Timer("com.csipsimple.PjSipServiceTasks");
			}
			TimerTask tt = new TimerTask() {
				@Override
				public void run() {
					service.getExecutor().execute(new SipRunnable() {
						@Override
						protected void doRun() throws SameThreadException {
							Log.d(TAG, "Running pending DTMF send");
							sendPendingDtmf(callId);
						}
					});
				}
			};
			dtmfTasks.put(callId, tt);
			Log.d(TAG, "Schedule DTMF " + remainingDtmf + " in "
					+ pauseBeforeRemaining);
			tasksTimer.schedule(tt, pauseBeforeRemaining);
		} else {
			if (dtmfToAutoSend.get(callId) != null) {
				dtmfToAutoSend.put(callId, null);
			}
			if (dtmfTasks.get(callId) != null) {
				dtmfTasks.put(callId, null);
			}
		}

		return res;
	}

	public void sendPendingDtmf(int callId) throws SameThreadException {
		if (dtmfToAutoSend.get(callId) != null) {
			Log.d(TAG, "DTMF - Send pending dtmf " + dtmfToAutoSend.get(callId)
					+ " for " + callId);
			sendDtmf(callId, dtmfToAutoSend.get(callId));
		}
	}

	public int callReinvite(int callId, boolean unhold)
			throws SameThreadException {
		if (created) {
			return pjsua.call_reinvite(callId,
					unhold ? pjsua_call_flag.PJSUA_CALL_UNHOLD.swigValue() : 0,
					null);

		}
		return -1;
	}

	public int callXfer(int callId, String callee) throws SameThreadException {
		if (created) {
			return pjsua.call_xfer(callId, pjsua.pj_str_copy(callee), null);
		}
		return -1;
	}

	public int callXferReplace(int callId, int otherCallId, int options)
			throws SameThreadException {
		if (created) {
			return pjsua.call_xfer_replaces(callId, otherCallId, options, null);
		}
		return -1;
	}

	public SipCallSession[] getCalls() {
		if (created && userAgentReceiver != null) {
			SipCallSession[] callsInfo = userAgentReceiver.getCalls();
			return callsInfo;
		}
		return new SipCallSession[0];
	}

	/**
	 * Mute microphone
	 * 
	 * @param on
	 *            true if microphone has to be muted
	 * @throws SameThreadException
	 */
	public void setMicrophoneMute(boolean on) throws SameThreadException {
		if (created && mediaManager != null) {
			mediaManager.setMicrophoneMute(on);
		}
	}

	/**
	 * Change speaker phone mode
	 * 
	 * @param on
	 *            true if the speaker mode has to be on.
	 * @throws SameThreadException
	 */
	public void setSpeakerphoneOn(boolean on) throws SameThreadException {
		if (created && mediaManager != null) {
			mediaManager.setSpeakerphoneOn(on);
		}
	}
}
