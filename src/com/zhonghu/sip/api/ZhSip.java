package com.zhonghu.sip.api;

import android.app.PendingIntent;
import android.content.Context;
import android.media.AudioManager;

public class ZhSip {

	private static final String TAG = "SIP ZhSip";

	/**
	 * Sip ��ʼ���� �����ڳ�����ڵ�onCreate�е���
	 * 
	 * @param context
	 *            Ӧ�õ�Application��context
	 */
	public static void init(Context context) {
		SipController.getInstance().init(context);
	}

	public static boolean isSipInit() {
		return SipController.getInstance().isSipInit();
	}

	/*	*//**
	 * Sip��������½�û��� ������
	 * 
	 * @param userName
	 *            sip�û��˺�
	 * @param passWord
	 *            sip �û�����
	 */
	/*
	 * public static void initUser(String userName, String passWord) {
	 * SipController.getInstance().addUser(userName, passWord); }
	 */

	/**
	 * Sip��������½�û��� ������
	 * 
	 * @param userName
	 *            sip�û��˺�
	 * @param passWord
	 *            sip �û�����
	 */
	public static void initUser(String domain, String userName, String passWord) {
		SipController.getInstance().addUser(domain, userName, passWord);
	}

	/**
	 * ��ȡSip�����Ƿ��Ѿ�����
	 * 
	 * @return true:δ���� false:�Ѿ�����
	 */
	public static boolean isServiceConnected() {
		return SipController.getInstance().isServiceConnected();
	}

	/**
	 * ��ȡSip�����Ƿ��Ѿ�����
	 * 
	 * @return true:δ���� false:�Ѿ�����
	 */
	public static boolean isUserIsConnected(String domain, String userName,
			String passWord) {
		return false;
	}

	/**
	 * �˳�sip����
	 */
	public static void quit() {
		SipController.getInstance().quit();
	}

	/**
	 * �˳�sip����
	 */
	public static void logOut() {
		SipController.getInstance().logOut();
	}

	/*
	 * public static void login(String userName, String passWord) {
	 * SipController.getInstance().login(userName, passWord); }
	 */

	/**
	 * Sip��������½
	 * 
	 * @param domain
	 *            ����������
	 * @param userName
	 *            sip�û��˺�
	 * @param passWord
	 *            sip �û�����
	 */
	public static void login(String domain, String userName, String passWord) {
		SipController.getInstance().login(domain, userName, passWord);
	}

	/**
	 * ����Sip�绰
	 * 
	 * @param callNum
	 *            ����ĵ绰���� �ַ���
	 */
	public static void makeCall(String callNum) {
		SipController.getInstance().makeCall(callNum);
	}

	/**
	 * �����绰
	 * 
	 * @param callid
	 *            ����绰֮�������id
	 * @see getCurrentCallId()
	 * 
	 */
	public static void acceptCall(int callid) {
		SipController.getInstance().acceptCall(callid);
	}

	public static void hangupCall(int callid, int status) {
		SipController.getInstance().hangupCall(callid, status);
	}

	public static void sendDtmf(int callId, int keyCode) {
		SipController.getInstance().sendDtmf(callId, keyCode);
	}

	/**
	 * �Ҷϵ绰
	 * 
	 * @param callid
	 *            �绰id
	 * 
	 */
	public static void hangupCall(int callid) {
		SipController.getInstance().hangupCall(callid, 0);
	}

	/**
	 * �ܾ��绰
	 * 
	 * @param callid
	 *            �绰id
	 * 
	 */
	public static void rejectCall(int callid) {
		SipController.getInstance().rejectCall(callid);
	}

	/**
	 * ����ͨ����Ͳ���� ͨ������������
	 * 
	 * @param callInfo
	 *            SipCallInfo
	 * @see getActiveCallInfo()
	 * @param direction
	 *            ������ʽ(�������ߵݼ�)
	 * @see AudioManager.ADJUST_RAISE
	 * @see AudioManager.ADJUST_LOWER
	 * @param flags
	 *            Audio Flag ���� valueȡֵ�� AudioManager.FLAG_SHOW_UI
	 *            AudioManager.FLAG_ALLOW_RINGER_MODES
	 *            AudioManager.FLAG_PLAY_SOUND
	 *            AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE
	 *            AudioManager.FLAG_VIBRATE
	 */
	public static void adjustVolume(SipCallSession callInfo, int direction,
			int flags) {
		SipController.getInstance().adjustVolume(callInfo, direction, flags);
	}

	/**
	 * ���ڵ�ǰͨ�� ��ͨ������
	 * 
	 * @param direction
	 *            ������ʽ(�������ߵݼ�)
	 */
	public static void adjustCurrentCallVolume(int direction) {
		SipCallSession currentCallInfo = getActiveCallInfo();
		SipController.getInstance().adjustVolume(currentCallInfo, direction,
				AudioManager.FLAG_SHOW_UI);
	}

	/**
	 * ���ڵ�ǰͨ�� ��ͨ������ Ĭ��flagΪAudioManager.FLAG_SHOW_UI�� ��Ҫ���ڰ����������ڼ�ʱ
	 * �������ߵݼ�����������Ĭ��flag�µ�������ʱ��ʾ��ǰ������
	 * 
	 * @param callInfo
	 *            SipCallInfo
	 * @param direction
	 *            ������ʽ(�������ߵݼ�)
	 */
	public static void adjustVolume(SipCallSession callInfo, int direction) {
		SipController.getInstance().adjustVolume(callInfo, direction,
				AudioManager.FLAG_SHOW_UI);
	}

	/**
	 * ���ڵ�ǰͨ�� ��ͨ������
	 * 
	 * @param callInfo
	 *            SipCallInfo
	 * @param value
	 *            volum ֵ
	 */
	public static void setCallVolume(int value) {
		SipCallSession currentCallInfo = getActiveCallInfo();
		SipController.getInstance().setVolume(currentCallInfo, value);

	}

	/**
	 * �Ƿ��������
	 * 
	 * @param on
	 *            true:�� false:�ر�
	 */
	public static void setSpeakerphoneOn(boolean on) {
		SipController.getInstance().setSpeakerphoneOn(on);
	}

	/**
	 * ��ȡ��ǰ���е�Id
	 * 
	 * @return ��ǰ���е�Id
	 */
	public static int getCurrentCallId() {
		return SipController.getInstance().getCurrentCallId();
	}

	/**
	 * ��ȡ��ǰ���е� ������Ϣ SipCallSession
	 * 
	 * @return ��ǰ���е�SipCallSession
	 */
	public static SipCallSession getActiveCallInfo() {
		return SipController.getInstance().getActiveCallInfo();
	}

	public static SipCallSession getCallInfoById(int id) {
		return SipController.getInstance().getCallInfoById(id);

	}

	public static SipCallSession[] getCalls() {
		return SipController.getInstance().getCalls();

	}

	/**
	 * ��ȡ �˺�״̬
	 * 
	 * @param id
	 * @return
	 */
	public static int getAccountStatus(String userName) {
		return SipController.getInstance().getAccountStatuCodeByUserName(
				userName);
	}

	public static boolean isUserOnLine(String userName) {
		int statusCode = SipController.getInstance()
				.getAccountStatuCodeByUserName(userName);
		return statusCode == 200;
	}

	/**
	 * �������Ƿ��
	 * 
	 * @return true :�� false:�ر�
	 */
	public static boolean isSpeakerOn() {
		return SipController.getInstance().isSpeakerOn();
	}

	/**
	 * ���þ���
	 * 
	 * @param on
	 *            true:�� false:�ر�
	 */
	public static void setMute(boolean on) {
		SipController.getInstance().setMute(on);
	}

	/**
	 * �Ƿ��
	 * 
	 * @return true :�� false:�ر�
	 */
	public static boolean isMuteOn() {
		return SipController.getInstance().isMuteOn();
	}

	/**
	 * �����˺�״̬ ����callback
	 * 
	 * @param callBack
	 */
	public static void setAccoutStateCallBack(AccoutStatusCallBack callBack) {
		SipController.getInstance().setAccoutCallBack(callBack);
	}

	/**
	 * ����ͨ��״̬����callback
	 * 
	 * @param callBack
	 */
	public static void setCallStateCallBack(CallStateCallBack callBack) {
		SipController.getInstance().setCallStateCallBack(callBack);
	}

	/**
	 * ����Intent �Զ��򿪺��н���
	 * 
	 * @param pendingIntent
	 */
	public static void setIncomingIntent(PendingIntent pendingIntent) {
		SipController.getInstance().setIncomingIntent(pendingIntent);
	}

	/**
	 * ���� ¼���ļ�·��
	 * 
	 * @param dir
	 *            ¼���ļ���Ŀ����·��
	 */
	public static void setRecordFileDir(String dir) {
		SipController.getInstance().setRecordFileDir(dir);
	}

	/**
	 * ��ȡ��ǰ¼���ļ���Ŀ��·��
	 * 
	 * @return
	 */
	public static String getRecordFileDir() {
		return SipController.getInstance().getRecordFileDir();
	}

	/**
	 * ��ʼ¼��
	 * 
	 * @param callId
	 *            ѡ��¼����ͨ��id
	 */
	public static void startRecord(int callId) {
		SipController.getInstance().startRecord(callId);
	}

	/**
	 * ֹͣ¼��
	 * 
	 * @param callId
	 *            ¼����ͨ��id
	 */
	public static void stopRecord(int callId) {
		SipController.getInstance().stopRecord(callId);
	}

	/**
	 * �Ƿ���¼���У�
	 * 
	 * @param callId
	 *            ͨ����Id
	 * @return ����true������¼���У�����false��û����¼��״̬
	 */
	public static boolean isRecording(int callId) {
		return SipController.getInstance().isRecording(callId);
	}

	/**
	 * �Ƿ�����������
	 * 
	 * @param on
	 *            ����
	 */
	public static void setEchoCancellation(boolean on) {
		SipController.getInstance().setEchoCancellation(on);
	}

	/**
	 * ����key��ȡ��Ӧ��value
	 * 
	 * @param key
	 * @see SipConfigManager
	 * @return ��Ӧ��valueֵ
	 */
	public static String getParameters(String key) {
		return key;
	}
	
	public static int getCallDuration(int callId)
	{
		return SipController.getInstance().getCallDuration(callId);
	}

}
