package com.zhonghu.sip.wizard;

import java.util.List;

import android.content.Intent;

import com.zhonghu.sip.api.SipProfile;
import com.zhonghu.sip.pref.PreferencesWrapper;

public interface WizardIface {



    // Save
    /**
     * Build the account based on preference view contents.
     * 
     * @param account The sip profile already saved in database
     * @return the sip profile to save into databse based on fields contents.
     */
    SipProfile buildAccount(SipProfile account);

    /**
     * Set default global application preferences. This is a hook method to set
     * preference when an account is saved with this profile. It's useful for
     * sip providers that needs global settings hack.
     * 
     * @param prefs The preference wrapper interface.
     */
    void setDefaultParams(PreferencesWrapper prefs);

    boolean canSave();

    /**
     * Does the wizard changes something that requires to restart sip stack? If
     * so once saved, the wizard will also ask for a stack restart to take into
     * account any preference changed with
     * {@link #setDefaultParams(PreferencesWrapper)}
     * 
     * @return true if the wizard would like the sip stack to restart
     */
    boolean needRestart();

    //List<Filter> getDefaultFilters(SipProfile acc);

    // Extras
    void onActivityResult(int requestCode, int resultCode, Intent data);

    void onStart();
    void onStop();
    void setUserName(String userName);
    void setPassWord(String passWords);


}
