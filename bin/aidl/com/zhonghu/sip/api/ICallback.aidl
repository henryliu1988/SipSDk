package com.zhonghu.sip.api;

import com.zhonghu.sip.api.SipCallSession;
interface ICallback {
	void onSipCallChanged(int stateCode);
	void onSipMediaChanged();
	void onCallLaunched();
	void onInCommingCall();
		void onCallInfoListChanged();
	
}