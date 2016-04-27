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

import com.zhonghu.sip.api.SipProfile;
import com.zhonghu.sip.api.SipUri;

public abstract class SimpleImplementation extends BaseImplementation {
	// private static final String THIS_FILE = "SimplePrefsWizard";

	protected static String DISPLAY_NAME = "display_name";
	protected String userName = "";
	protected String passWord = "";
	protected String domain = "192.168.2.202:2060";
	protected static String USE_TCP = "use_tcp";

	/**
	 * Basic implementation of the account building based on simple
	 * implementation fields. A specification of this class could extend and add
	 * its own post processing here.
	 * 
	 * {@inheritDoc}
	 */
	public SipProfile buildAccount(SipProfile account) {
		account.display_name = getDefaultName();
		account.acc_id = "<sip:" + SipUri.encodeUser(getUserName()) + "@"
				+ getDomain() + ">";

		String regUri = "sip:" + getDomain();
		account.reg_uri = regUri;
		account.proxies = new String[] { regUri };

		account.realm = "*";
		account.username = getUserName();
		account.data = getPassword();
		account.scheme = SipProfile.CRED_SCHEME_DIGEST;
		account.datatype = SipProfile.CRED_DATA_PLAIN_PASSWD;

		account.reg_timeout = 900;

		if (canTcp()) {
			account.transport = canTcp() ? SipProfile.TRANSPORT_TCP
					: SipProfile.TRANSPORT_UDP;
		} else {
			account.transport = SipProfile.TRANSPORT_UDP;
		}

		return account;
	}

	/**
	 * Get the server domain to use by default for registrar, proxy and user
	 * domain.
	 * 
	 * @return The server name / ip of the sip domain
	 */
	protected abstract String getDomain();

	/**
	 * Get the default display name for this account.
	 * 
	 * @return The display name to use by default for this account
	 */
	protected abstract String getDefaultName();

	/**
	 * Get the server domain to use by default for registrar, proxy and user
	 * domain.
	 * 
	 * @return The server name / ip of the sip domain
	 */
	protected abstract String getUserName();

	/**
	 * Get the server domain to use by default for registrar, proxy and user
	 * domain.
	 * 
	 * @return The server name / ip of the sip domain
	 */
	protected abstract String getPassword();

	/**
	 * Does the sip provider allows TCP connection. And support it correctly. If
	 * so the application will propose a checkbox to use TCP transportation.
	 * This method may be overriden by a implementation.
	 * 
	 * @return True if TCP is available.
	 */
	protected boolean canTcp() {
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean needRestart() {
		return false;
	}


}
