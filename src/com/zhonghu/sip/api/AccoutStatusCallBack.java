package com.zhonghu.sip.api;

public interface AccoutStatusCallBack {

	/**
	 * 账号状态 回调，账号状态发生变化时，回调发生
	 * @param code 
	 *   @see 
	 */
	public void onAccoutStatusChanged(int code);
}
