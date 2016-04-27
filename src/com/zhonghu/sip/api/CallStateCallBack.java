package com.zhonghu.sip.api;

public interface CallStateCallBack {

	/**
	 * 通话过程中通话状态发生变化
	 * @param stateCode 通话状态码
	 *  @see SipCallSession.StatusCode
	 */
	public void onCallStateChanged(int stateCode);
	
	/**
	 * 呼叫启动，例如一个呼出的请求在sip服务器被成功触发，APP可根据次回调来跳转界面
	 */
	public void onCallLaunched();

}
