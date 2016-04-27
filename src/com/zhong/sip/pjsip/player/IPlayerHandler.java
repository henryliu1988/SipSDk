
package com.zhong.sip.pjsip.player;

import com.zhonghu.sip.pjsip.SameThreadException;

public interface IPlayerHandler {
    
    public void startPlaying() throws SameThreadException;

    public void stopPlaying() throws SameThreadException;
    
    
}
