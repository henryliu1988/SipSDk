package com.zhonghu.sip.api;

import android.app.PendingIntent;
import android.content.Context;
import android.media.AudioManager;

public class ZhSip {

	private static final String TAG = "SIP ZhSip";

	/**
	 * Sip 初始化类 建议在程序入口的onCreate中调度
	 * 
	 * @param context
	 *            应用的Application的context
	 */
	public static void init(Context context) {
		SipController.getInstance().init(context);
	}

	public static boolean isSipInit() {
		return SipController.getInstance().isSipInit();
	}

	/*	*//**
	 * Sip服务器登陆用户名 ，密码
	 * 
	 * @param userName
	 *            sip用户账号
	 * @param passWord
	 *            sip 用户密码
	 */
	/*
	 * public static void initUser(String userName, String passWord) {
	 * SipController.getInstance().addUser(userName, passWord); }
	 */

	/**
	 * Sip服务器登陆用户名 ，密码
	 * 
	 * @param userName
	 *            sip用户账号
	 * @param passWord
	 *            sip 用户密码
	 */
	public static void initUser(String domain, String userName, String passWord) {
		SipController.getInstance().addUser(domain, userName, passWord);
	}

	/**
	 * 获取Sip服务是否已经连接
	 * 
	 * @return true:未连接 false:已经连接
	 */
	public static boolean isServiceConnected() {
		return SipController.getInstance().isServiceConnected();
	}

	/**
	 * 获取Sip服务是否已经连接
	 * 
	 * @return true:未连接 false:已经连接
	 */
	public static boolean isUserIsConnected(String domain, String userName,
			String passWord) {
		return false;
	}

	/**
	 * 退出sip服务
	 */
	public static void quit() {
		SipController.getInstance().quit();
	}

	/**
	 * 退出sip服务
	 */
	public static void logOut() {
		SipController.getInstance().logOut();
	}

	/*
	 * public static void login(String userName, String passWord) {
	 * SipController.getInstance().login(userName, passWord); }
	 */

	/**
	 * Sip服务器登陆
	 * 
	 * @param domain
	 *            服务器域名
	 * @param userName
	 *            sip用户账号
	 * @param passWord
	 *            sip 用户密码
	 */
	public static void login(String domain, String userName, String passWord) {
		SipController.getInstance().login(domain, userName, passWord);
	}

	/**
	 * 拨打Sip电话
	 * 
	 * @param callNum
	 *            拨打的电话号码 字符串
	 */
	public static void makeCall(String callNum) {
		SipController.getInstance().makeCall(callNum);
	}

	/**
	 * 接听电话
	 * 
	 * @param callid
	 *            拨打电话之后产生的id
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
	 * 挂断电话
	 * 
	 * @param callid
	 *            电话id
	 * 
	 */
	public static void hangupCall(int callid) {
		SipController.getInstance().hangupCall(callid, 0);
	}

	/**
	 * 拒绝电话
	 * 
	 * @param callid
	 *            电话id
	 * 
	 */
	public static void rejectCall(int callid) {
		SipController.getInstance().rejectCall(callid);
	}

	/**
	 * 调节通话听筒音量 通过音量键出发
	 * 
	 * @param callInfo
	 *            SipCallInfo
	 * @see getActiveCallInfo()
	 * @param direction
	 *            触发方式(递增或者递减)
	 * @see AudioManager.ADJUST_RAISE
	 * @see AudioManager.ADJUST_LOWER
	 * @param flags
	 *            Audio Flag 参数 value取值： AudioManager.FLAG_SHOW_UI
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
	 * 调节当前通话 的通话声音
	 * 
	 * @param direction
	 *            触发方式(递增或者递减)
	 */
	public static void adjustCurrentCallVolume(int direction) {
		SipCallSession currentCallInfo = getActiveCallInfo();
		SipController.getInstance().adjustVolume(currentCallInfo, direction,
				AudioManager.FLAG_SHOW_UI);
	}

	/**
	 * 调节当前通话 的通话声音 默认flag为AudioManager.FLAG_SHOW_UI。 主要用于按下音量调节键时
	 * 递增或者递减调节音量，默认flag下调节音量时显示当前音量。
	 * 
	 * @param callInfo
	 *            SipCallInfo
	 * @param direction
	 *            触发方式(递增或者递减)
	 */
	public static void adjustVolume(SipCallSession callInfo, int direction) {
		SipController.getInstance().adjustVolume(callInfo, direction,
				AudioManager.FLAG_SHOW_UI);
	}

	/**
	 * 调节当前通话 的通话声音
	 * 
	 * @param callInfo
	 *            SipCallInfo
	 * @param value
	 *            volum 值
	 */
	public static void setCallVolume(int value) {
		SipCallSession currentCallInfo = getActiveCallInfo();
		SipController.getInstance().setVolume(currentCallInfo, value);

	}

	/**
	 * 是否打开扬声器
	 * 
	 * @param on
	 *            true:打开 false:关闭
	 */
	public static void setSpeakerphoneOn(boolean on) {
		SipController.getInstance().setSpeakerphoneOn(on);
	}

	/**
	 * 获取当前呼叫的Id
	 * 
	 * @return 当前呼叫的Id
	 */
	public static int getCurrentCallId() {
		return SipController.getInstance().getCurrentCallId();
	}

	/**
	 * 获取当前呼叫的 呼叫信息 SipCallSession
	 * 
	 * @return 当前呼叫的SipCallSession
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
	 * 获取 账号状态
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
	 * 扬声器是否打开
	 * 
	 * @return true :打开 false:关闭
	 */
	public static boolean isSpeakerOn() {
		return SipController.getInstance().isSpeakerOn();
	}

	/**
	 * 设置静音
	 * 
	 * @param on
	 *            true:打开 false:关闭
	 */
	public static void setMute(boolean on) {
		SipController.getInstance().setMute(on);
	}

	/**
	 * 是否打开
	 * 
	 * @return true :打开 false:关闭
	 */
	public static boolean isMuteOn() {
		return SipController.getInstance().isMuteOn();
	}

	/**
	 * 设置账号状态 监听callback
	 * 
	 * @param callBack
	 */
	public static void setAccoutStateCallBack(AccoutStatusCallBack callBack) {
		SipController.getInstance().setAccoutCallBack(callBack);
	}

	/**
	 * 设置通话状态监听callback
	 * 
	 * @param callBack
	 */
	public static void setCallStateCallBack(CallStateCallBack callBack) {
		SipController.getInstance().setCallStateCallBack(callBack);
	}

	/**
	 * 设置Intent 自动打开呼叫界面
	 * 
	 * @param pendingIntent
	 */
	public static void setIncomingIntent(PendingIntent pendingIntent) {
		SipController.getInstance().setIncomingIntent(pendingIntent);
	}

	/**
	 * 设置 录音文件路径
	 * 
	 * @param dir
	 *            录音文件的目标存放路径
	 */
	public static void setRecordFileDir(String dir) {
		SipController.getInstance().setRecordFileDir(dir);
	}

	/**
	 * 获取当前录音文件的目标路径
	 * 
	 * @return
	 */
	public static String getRecordFileDir() {
		return SipController.getInstance().getRecordFileDir();
	}

	/**
	 * 开始录音
	 * 
	 * @param callId
	 *            选择录音的通话id
	 */
	public static void startRecord(int callId) {
		SipController.getInstance().startRecord(callId);
	}

	/**
	 * 停止录音
	 * 
	 * @param callId
	 *            录音的通话id
	 */
	public static void stopRecord(int callId) {
		SipController.getInstance().stopRecord(callId);
	}

	/**
	 * 是否在录音中，
	 * 
	 * @param callId
	 *            通话的Id
	 * @return 返回true：正在录音中；返回false：没有在录音状态
	 */
	public static boolean isRecording(int callId) {
		return SipController.getInstance().isRecording(callId);
	}

	/**
	 * 是否消除回音，
	 * 
	 * @param on
	 *            开关
	 */
	public static void setEchoCancellation(boolean on) {
		SipController.getInstance().setEchoCancellation(on);
	}

	/**
	 * 根据key获取对应的value
	 * 
	 * @param key
	 * @see SipConfigManager
	 * @return 对应的value值
	 */
	public static String getParameters(String key) {
		return key;
	}
	
	public static int getCallDuration(int callId)
	{
		return SipController.getInstance().getCallDuration(callId);
	}

}
