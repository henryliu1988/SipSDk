package com.zhonghu.sip.api;

public interface CallStateCallBack {

	/**
	 * ͨ��������ͨ��״̬�����仯
	 * @param stateCode ͨ��״̬��
	 *  @see SipCallSession.StatusCode
	 */
	public void onCallStateChanged(int stateCode);
	
	/**
	 * ��������������һ��������������sip���������ɹ�������APP�ɸ��ݴλص�����ת����
	 */
	public void onCallLaunched();

}
