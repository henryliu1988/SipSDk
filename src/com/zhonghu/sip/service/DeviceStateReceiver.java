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

package com.zhonghu.sip.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.util.Log;

import com.zhonghu.sip.pref.PreferencesProviderWrapper;

public class DeviceStateReceiver extends BroadcastReceiver {

	// private static final String ACTION_DATA_STATE_CHANGED =
	// "android.intent.action.ANY_DATA_STATE";
	private static final String TAG = "Device State";

	@Override
	public void onReceive(Context context, Intent intent) {
		Log.d("henry", "DeviceStateReceiver intent = " + intent);
		PreferencesProviderWrapper prefWrapper = new PreferencesProviderWrapper(
				context);
		String intentAction = intent.getAction();

		if (/* intentAction.equals(ACTION_DATA_STATE_CHANGED) || */
		intentAction.equals(ConnectivityManager.CONNECTIVITY_ACTION)
				|| intentAction.equals(Intent.ACTION_BOOT_COMPLETED)) {

			if (prefWrapper.isValidConnectionForIncoming()
					&& !prefWrapper
							.getPreferenceBooleanValue(PreferencesProviderWrapper.HAS_BEEN_QUIT)) {
				Log.d(TAG, "Try to start service if not already started");
				Intent sip_service_intent = new Intent(context,
						SipService.class);
				context.startService(sip_service_intent);
			}
		}
	}

}
