

package com.zhonghu.sip.utils;

import com.zhonghu.sip.utils.WizardUtils.WizardInfo;


public final class CustomDistribution {
	
	private CustomDistribution() {}
	
	// CSipSimple trunk distribution
	/**
	 * Does this distribution allow to create other accounts
	 * than the one of the distribution
	 * @return Whether other accounts can be created
	 */
	public static boolean distributionWantsOtherAccounts() {
		return true;
	}
	
	/**
	 * Does this distribution allow to list other providers in 
	 * other accounts creation
	 * @return Whether other provider are listed is wizard picker
	 */
	public static boolean distributionWantsOtherProviders() {
		return true;
	}
	
	/**
	 * SIP User agent to send by default in SIP messages (by default device infos are added to User Agent string)
	 * @return the default user agent
	 */
	public static String getUserAgent() {
		return "SipSdk";
	}
	
	/**
	 * The default wizard info for this distrib. If none no custom distribution wizard is shown
	 * @return the default wizard info
	 */
	public static WizardInfo getCustomDistributionWizard() {
		return null; 
	}
	
	/**
	 * Show or not the issue list in help
	 * @return whether link to issue list should be displayed
	 */
	public static boolean showIssueList() {
		return true;
	}
	

	/**
	 * Whether we want to display first fast setting screen to 
	 * allow user to quickly configure the sip client
	 * @return true if the fast setting screen should be displayed
	 */
	public static boolean showFirstSettingScreen() {
		return true;
	}
	
	/**
	 * Do we want to display messaging feature
	 * @return true if the feature is enabled in this distribution
	 */
	public static boolean supportMessaging() {
		return true;
	}
	
	/**
	 * Do we want to display the favorites feature
	 * @return true if the feature is enabled in this distribution
	 */
	public static boolean supportFavorites() {
	    return true;
	}
	
	/**
	 * Do we want to display record call option while in call
	 * If true the record of conversation will be enabled both in 
	 * ongoing call view and in settings as "auto record" feature
	 * @return true if the feature is enabled in this distribution
	 */
    public static boolean supportCallRecord() {
        return true;
    }

	/**
	 * Shall we force the no mulitple call feature to be set to false
	 * @return true if we don't want to support multiple calls at all.
	 */
	public static boolean forceNoMultipleCalls() {
		return false;
	}

	/**
	 * Should the wizard list display a given generic wizard
	 * @param wizardTag the tag of the generic wizard
	 * @return true if you'd like the wizard to be listed
	 */
    public static boolean distributionWantsGeneric(String wizardTag) {
        return true;
    }

    /**
     * Get the SD card folder name.
     * This folder will be used to store call records, configs and logs
     * @return the name of the folder to use
     */
    public static String getSDCardFolder() {
        return "SipSdk";
    }

  
}
