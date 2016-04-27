package com.zhonghu.sip.pjsip;

import org.pjsip.pjsua.ZrtpCallback;

public class ZrtpStateReceiver extends ZrtpCallback{
    private static final String THIS_FILE = "ZrtpStateReceiver";
    private PjSipService pjService;
    
    ZrtpStateReceiver(PjSipService service) {
        pjService = service;
    }

}
