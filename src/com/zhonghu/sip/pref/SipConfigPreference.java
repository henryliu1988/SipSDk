package com.zhonghu.sip.pref;

import java.util.HashMap;

import android.util.Log;

import com.zhonghu.sip.api.SipConfigManager;
import com.zhonghu.sip.utils.CustomDistribution;

public class SipConfigPreference {

	private static SipConfigPreference mConfig = new SipConfigPreference();

	public static SipConfigPreference getInstance() {
		return mConfig;
	}

	private final static HashMap<String, String> string_pref = new HashMap<String, String>() {

		private static final long serialVersionUID = 1L;
		{

			put(SipConfigManager.USER_AGENT, CustomDistribution.getUserAgent());
			put(SipConfigManager.LOG_LEVEL, "1");

			put(SipConfigManager.USE_SRTP, "0");
			put(SipConfigManager.USE_ZRTP, "1"); /* 1 is no zrtp */
			put(SipConfigManager.UDP_TRANSPORT_PORT, "0");
			put(SipConfigManager.TCP_TRANSPORT_PORT, "0");
			put(SipConfigManager.TLS_TRANSPORT_PORT, "0");
			put(SipConfigManager.KEEP_ALIVE_INTERVAL_WIFI, "80");
			put(SipConfigManager.KEEP_ALIVE_INTERVAL_MOBILE, "40");
			put(SipConfigManager.TCP_KEEP_ALIVE_INTERVAL_WIFI, "180");
			put(SipConfigManager.TCP_KEEP_ALIVE_INTERVAL_MOBILE, "120");
			put(SipConfigManager.TLS_KEEP_ALIVE_INTERVAL_WIFI, "180");
			put(SipConfigManager.TLS_KEEP_ALIVE_INTERVAL_MOBILE, "120");
			put(SipConfigManager.RTP_PORT, "4000");
			put(SipConfigManager.OVERRIDE_NAMESERVER, "");
			put(SipConfigManager.TIMER_MIN_SE, "90");
			put(SipConfigManager.TIMER_SESS_EXPIRES, "1800");
			put(SipConfigManager.TSX_T1_TIMEOUT, "-1");
			put(SipConfigManager.TSX_T2_TIMEOUT, "-1");
			put(SipConfigManager.TSX_T4_TIMEOUT, "-1");
			put(SipConfigManager.TSX_TD_TIMEOUT, "-1");

			put(SipConfigManager.SND_AUTO_CLOSE_TIME, "1");
			put(SipConfigManager.ECHO_CANCELLATION_TAIL, "200");
			put(SipConfigManager.ECHO_MODE, "3"); /* WEBRTC */
			put(SipConfigManager.SND_MEDIA_QUALITY, "4");
			put(SipConfigManager.SND_CLOCK_RATE, "16000");
			put(SipConfigManager.SND_PTIME, "20");
			put(SipConfigManager.SIP_AUDIO_MODE, "0");
			put(SipConfigManager.MICRO_SOURCE, "7");
			put(SipConfigManager.THREAD_COUNT, "0");
			put(SipConfigManager.MEDIA_THREAD_COUNT, "2");
			put(SipConfigManager.HEADSET_ACTION, "0");
			put(SipConfigManager.AUDIO_IMPLEMENTATION, "0");
			put(SipConfigManager.H264_PROFILE, "66");
			put(SipConfigManager.H264_LEVEL, "0");
			put(SipConfigManager.H264_BITRATE, "0");
			put(SipConfigManager.VIDEO_CAPTURE_SIZE, "");

			put(SipConfigManager.STUN_SERVER, "stun.counterpath.com");
			put(SipConfigManager.TURN_SERVER, "");
			put(SipConfigManager.TURN_USERNAME, "");
			put(SipConfigManager.TURN_PASSWORD, "");
			put(SipConfigManager.TURN_TRANSPORT, "0");
			put(SipConfigManager.TLS_SERVER_NAME, "");
			put(SipConfigManager.CA_LIST_FILE, "");
			put(SipConfigManager.CERT_FILE, "");
			put(SipConfigManager.PRIVKEY_FILE, "");
			put(SipConfigManager.TLS_PASSWORD, "");
			put(SipConfigManager.TLS_METHOD, "0");
			put(SipConfigManager.NETWORK_ROUTES_POLLING, "0");

			put(SipConfigManager.DSCP_VAL, "24");
			put(SipConfigManager.DSCP_RTP_VAL, "46");
			put(SipConfigManager.DTMF_MODE, "0");
			put(SipConfigManager.DTMF_PAUSE_TIME, "300");
			put(SipConfigManager.DTMF_WAIT_TIME, "2000");

			put(SipConfigManager.GSM_INTEGRATION_TYPE,
					Integer.toString(SipConfigManager.GENERIC_TYPE_PREVENT));
			put(SipConfigManager.DIAL_PRESS_TONE_MODE,
					Integer.toString(SipConfigManager.GENERIC_TYPE_AUTO));
			put(SipConfigManager.DIAL_PRESS_VIBRATE_MODE,
					Integer.toString(SipConfigManager.GENERIC_TYPE_AUTO));
			put(SipConfigManager.DTMF_PRESS_TONE_MODE,
					Integer.toString(SipConfigManager.GENERIC_TYPE_PREVENT));
			put(SipConfigManager.UNLOCKER_TYPE,
					Integer.toString(SipConfigManager.GENERIC_TYPE_AUTO));

			put(SipConfigManager.DEFAULT_CALLER_ID, "");
			put(SipConfigManager.THEME, "");
			put(SipConfigManager.CALL_UI_PACKAGE, "");
			put(SipConfigManager.RINGTONE, "");
			put(SipConfigManager.RECORDER_FILE_PATH,"");
			
			put("band_for_3g","nb");
			put("band_for_wifi","wb");
//			put("codec_speex_16000_wb","0");
//			put("codec_speex_16000_fpp","0");
//			put("codec_speex_8000_wb","0");
//			put("codec_speex_32000_wb","0");
//			put("codec_gsm_8000_wb","0");
			put("codec_pcma_8000_wb","1");
//			put("codec_g722_16000_wb","0");
//			put("codec_amr_8000_wb","0");
//			put("codec_amr-wb_16000_wb","0");
//			put("codec_isac_16000_wb","0");
//			put("codec_silk_8000_wb","0");
//			put("codec_silk_12000_wb","0");
//			put("codec_silk_16000_wb","0");
//			put("codec_silk_24000_wb","0");
			put("codec_pcmu_8000_wb","1");
			put("codec_g729_8000_wb","2");
			put("codec_ilbc_8000_wb","1");

//			put("codec_speex_16000_nb","0");
//			put("codec_speex_8000_nb","0");
//			put("codec_speex_32000_nb","0");
//			put("codec_gsm_8000_nb","0");
			put("codec_pcma_8000_nb","1");
//			put("codec_g722_16000_nb","0");
//			put("codec_amr_8000_nb","0");
//			put("codec_amr-wb_16000_nb","0");
//			put("codec_isac_16000_nb","0");
//			put("codec_silk_8000_nb","0");
//			put("codec_silk_12000_nb","0");
//			put("codec_silk_16000_nb","0");
//			put("codec_silk_24000_nb","0");
			put("codec_pcmu_8000_nb","1");
			put("codec_g729_8000_nb","2");
			put("codec_ilbc_8000_nb","1");

		}
	};
	private final static HashMap<String, Integer> int_pref = new HashMap<String, Integer>() {
		private static final long serialVersionUID = 1L;
		{
			put(SipConfigManager.LOG_LEVEL, 1);
			put(SipConfigManager.USE_SRTP, 0);
			put(SipConfigManager.USE_ZRTP, 1); /* 1 is no zrtp */
			put(SipConfigManager.UDP_TRANSPORT_PORT, 0);
			put(SipConfigManager.TCP_TRANSPORT_PORT,0);
			put(SipConfigManager.TLS_TRANSPORT_PORT,0);
			put(SipConfigManager.KEEP_ALIVE_INTERVAL_WIFI, 80);
			put(SipConfigManager.KEEP_ALIVE_INTERVAL_MOBILE, 40);
			put(SipConfigManager.TCP_KEEP_ALIVE_INTERVAL_WIFI, 180);
			put(SipConfigManager.TCP_KEEP_ALIVE_INTERVAL_MOBILE, 120);
			put(SipConfigManager.TLS_KEEP_ALIVE_INTERVAL_WIFI, 180);
			put(SipConfigManager.TLS_KEEP_ALIVE_INTERVAL_MOBILE, 120);
			put(SipConfigManager.RTP_PORT, 4000);
			put(SipConfigManager.TIMER_MIN_SE, 90);
			put(SipConfigManager.TIMER_SESS_EXPIRES, 1800);
			put(SipConfigManager.TSX_T1_TIMEOUT, -1);
			put(SipConfigManager.TSX_T2_TIMEOUT, -1);
			put(SipConfigManager.TSX_T4_TIMEOUT, -1);
			put(SipConfigManager.TSX_TD_TIMEOUT, -1);

			put(SipConfigManager.SND_AUTO_CLOSE_TIME, 1);
			put(SipConfigManager.ECHO_CANCELLATION_TAIL, 200);
			put(SipConfigManager.ECHO_MODE, 3); /* WEBRTC */
			put(SipConfigManager.SND_MEDIA_QUALITY, 4);
			put(SipConfigManager.SND_CLOCK_RATE, 16000);
			put(SipConfigManager.SND_PTIME, 20);
			put(SipConfigManager.SIP_AUDIO_MODE, 3);
			put(SipConfigManager.MICRO_SOURCE, 1);
			put(SipConfigManager.THREAD_COUNT,0);
			put(SipConfigManager.MEDIA_THREAD_COUNT, 1);
			put(SipConfigManager.HEADSET_ACTION, 0);
			put(SipConfigManager.AUDIO_IMPLEMENTATION, 1);
			put(SipConfigManager.H264_PROFILE, 66);
			put(SipConfigManager.H264_LEVEL, 0);
			put(SipConfigManager.H264_BITRATE, 0);
			put(SipConfigManager.TURN_TRANSPORT, 0);
			put(SipConfigManager.TLS_METHOD, 0);
			put(SipConfigManager.NETWORK_ROUTES_POLLING, 0);

			put(SipConfigManager.DSCP_VAL, 24);
			put(SipConfigManager.DSCP_RTP_VAL, 46);
			put(SipConfigManager.DTMF_MODE, 0);
			put(SipConfigManager.DTMF_PAUSE_TIME, 300);
			put(SipConfigManager.DTMF_WAIT_TIME, 2000);

			put(SipConfigManager.GSM_INTEGRATION_TYPE,
					SipConfigManager.GENERIC_TYPE_PREVENT);
			put(SipConfigManager.DIAL_PRESS_TONE_MODE,
					SipConfigManager.GENERIC_TYPE_AUTO);
			put(SipConfigManager.DIAL_PRESS_VIBRATE_MODE,
					SipConfigManager.GENERIC_TYPE_AUTO);
			put(SipConfigManager.DTMF_PRESS_TONE_MODE,
					SipConfigManager.GENERIC_TYPE_PREVENT);
			put(SipConfigManager.UNLOCKER_TYPE,
					SipConfigManager.GENERIC_TYPE_AUTO);

		}

	};
	private final static HashMap<String, Boolean> boolean_pref = new HashMap<String, Boolean>() {

		private static final long serialVersionUID = 1L;
		{
			// Network
			put(SipConfigManager.LOCK_WIFI, true);
			put(SipConfigManager.LOCK_WIFI_PERFS, false);
			put(SipConfigManager.ENABLE_TCP, true);
			put(SipConfigManager.ENABLE_UDP, true);
			put(SipConfigManager.ENABLE_TLS, false);
			put(SipConfigManager.USE_IPV6, false);
			put(SipConfigManager.ENABLE_DNS_SRV, false);
			put(SipConfigManager.ENABLE_ICE, false);
			put(SipConfigManager.ICE_AGGRESSIVE, true);
			put(SipConfigManager.ENABLE_TURN, false);
			put(SipConfigManager.ENABLE_STUN, false);
			put(SipConfigManager.ENABLE_STUN2, false);
			put(SipConfigManager.ENABLE_QOS, false);
			put(SipConfigManager.USE_COMPACT_FORM, false);
			put(SipConfigManager.USE_WIFI_IN, true);
			put(SipConfigManager.USE_WIFI_OUT, true);
			put(SipConfigManager.USE_OTHER_IN, true);
			put(SipConfigManager.USE_OTHER_OUT, true);
			put(SipConfigManager.USE_3G_IN, true);
			put(SipConfigManager.USE_3G_OUT, true);
			put(SipConfigManager.USE_GPRS_IN, true);
			put(SipConfigManager.USE_GPRS_OUT, true);
			put(SipConfigManager.USE_EDGE_IN, false);
			put(SipConfigManager.USE_EDGE_OUT, false);
			put(SipConfigManager.USE_ANYWAY_IN, false);
			put(SipConfigManager.USE_ANYWAY_OUT, false);
			put(SipConfigManager.USE_ROAMING_IN, true);
			put(SipConfigManager.USE_ROAMING_OUT, true);
			put(SipConfigManager.FORCE_NO_UPDATE, true);
			put(SipConfigManager.DISABLE_TCP_SWITCH, true);
			put(SipConfigManager.DISABLE_RPORT, false);
			put(SipConfigManager.ADD_BANDWIDTH_TIAS_IN_SDP, false);

			// Media
			put(SipConfigManager.ECHO_CANCELLATION, true);
			put(SipConfigManager.ENABLE_VAD, false);
			put(SipConfigManager.ENABLE_NOISE_SUPPRESSION, false);
			put(SipConfigManager.USE_SOFT_VOLUME, false);
			put(SipConfigManager.USE_ROUTING_API, false);
			put(SipConfigManager.USE_MODE_API, false);
			put(SipConfigManager.HAS_IO_QUEUE, true);
			put(SipConfigManager.SET_AUDIO_GENERATE_TONE, false);
			put(SipConfigManager.USE_SGS_CALL_HACK, false);
			put(SipConfigManager.USE_WEBRTC_HACK, false);
			put(SipConfigManager.DO_FOCUS_AUDIO, true);
			put(SipConfigManager.INTEGRATE_WITH_NATIVE_MUSIC, true);
			put(SipConfigManager.AUTO_CONNECT_BLUETOOTH, false);
			put(SipConfigManager.AUTO_CONNECT_SPEAKER, false);
			put(SipConfigManager.AUTO_DETECT_SPEAKER, false);
			put(SipConfigManager.CODECS_PER_BANDWIDTH, true);
			put(SipConfigManager.RESTART_AUDIO_ON_ROUTING_CHANGES, true);
			put(SipConfigManager.SETUP_AUDIO_BEFORE_INIT, false);

			// UI
			put(SipConfigManager.KEEP_AWAKE_IN_CALL, false);
			put(SipConfigManager.INVERT_PROXIMITY_SENSOR, false);
			put(SipConfigManager.ICON_IN_STATUS_BAR, true);
			put(SipConfigManager.USE_PARTIAL_WAKE_LOCK, false);
			put(SipConfigManager.ICON_IN_STATUS_BAR_NBR, false);
			put(SipConfigManager.INTEGRATE_WITH_CALLLOGS, true);
			put(SipConfigManager.INTEGRATE_WITH_DIALER, true);
			put(SipConfigManager.INTEGRATE_TEL_PRIVILEGED, false);
			put(SipConfigManager.LOG_USE_DIRECT_FILE, false);
			put(SipConfigManager.START_WITH_TEXT_DIALER, false);
			put(SipConfigManager.REWRITE_RULES_DIALER, false);

			// Calls
			put(SipConfigManager.AUTO_RECORD_CALLS, false);
			put(SipConfigManager.SUPPORT_MULTIPLE_CALLS, false);
			put(SipConfigManager.USE_VIDEO, false);
			put(SipConfigManager.PLAY_WAITTONE_ON_HOLD, false);

			// Secure
			put(SipConfigManager.TLS_VERIFY_SERVER, false);
			put(SipConfigManager.TLS_VERIFY_CLIENT, false);

		}
	};
	private final static HashMap<String, Float> float_pref = new HashMap<String, Float>() {

		private static final long serialVersionUID = 1L;
		{
			put(SipConfigManager.SND_MIC_LEVEL, (float) 1.0);
			put(SipConfigManager.SND_SPEAKER_LEVEL, (float) 1.0);
			put(SipConfigManager.SND_BT_MIC_LEVEL, (float) 1.0);
			put(SipConfigManager.SND_BT_SPEAKER_LEVEL, (float) 1.0);
			put(SipConfigManager.SND_STREAM_LEVEL, (float) 8.0);
		}
	};

	public void setBooleanConfig(String key, boolean value) {
		boolean_pref.put(key, value);
	}

	public boolean getBooleanConfig(String key) {
		if (!boolean_pref.containsKey(key)) {
			return false;
		}
		return boolean_pref.get(key);
	}


	public int getIntegerConfig(String key) {
		if (!int_pref.containsKey(key)) {
			return 0;
		}
		return int_pref.get(key);
	}

	public void setIntegerConfig(String key, int value) {
		int_pref.put(key, value);
	}

	public String getStringConfig(String key) {
		if (!string_pref.containsKey(key)) {
			return null;
		}
		return string_pref.get(key);
	}

	public void setStringConfig(String key, String value) {
		string_pref.put(key, value);
	}

	public float getFloatConfig(String key) {
		if (!float_pref.containsKey(key)) {
			return 0.0F;
		}
		return float_pref.get(key);
	}

	public void setFloatConfig(String key, float value) {
		float_pref.put(key, value);
	}

}
