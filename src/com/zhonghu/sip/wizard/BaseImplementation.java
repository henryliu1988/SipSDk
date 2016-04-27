/**
 * Copyright (C) 2010-2012 Regis Montoya (aka r3gis - www.r3gis.fr)
 * This file is part of CSipSimple.
 *
 *  CSipSimple is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  If you own a pjsip commercial license you can also redistribute it
 *  and/or modify it under the terms of the GNU Lesser General Public License
 *  as an android library.
 *
 *  CSipSimple is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with CSipSimple.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.zhonghu.sip.wizard;

import java.util.regex.Pattern;

import android.content.Intent;
import android.preference.EditTextPreference;

import com.zhonghu.sip.pref.PreferencesWrapper;

public abstract class BaseImplementation implements WizardIface {
	
	//Utilities functions
	protected boolean isEmpty(EditTextPreference edt){
		if(edt.getText() == null){
			return true;
		}
		if(edt.getText().equals("")){
			return true;
		}
		return false;
	}
	
	protected boolean isMatching(EditTextPreference edt, String regex) {
		if(edt.getText() == null){
			return false;
		}
		return Pattern.matches(regex, edt.getText());
	}

    /**
     * @see EditTextPreference#getText()
     * @param edt
     */
	protected String getText(EditTextPreference edt){
		return edt.getText();
	}
	

    /**
     * @see GenericPrefs#setStringFieldSummary(String)
     * @param fieldName
     */
	
	/**
	 * Set global preferences for this wizard
	 * If some preference that need restart are modified here
	 * Do not forget to return true in need restart
	 */
	public void setDefaultParams(PreferencesWrapper prefs) {
		// By default empty implementation
	}
	
	@Override
	public boolean needRestart() {
		return false;
	}
	

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // By default empty implementation
    }
    
    public void onStart() {}
    public void onStop() {}
    
}
