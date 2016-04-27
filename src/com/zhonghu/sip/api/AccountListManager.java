package com.zhonghu.sip.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.ContentValues;
import android.text.TextUtils;

public class AccountListManager {

	private static AccountListManager mAccountListManager;

	private static Map<Long, SipProfile> profiles = new HashMap<Long, SipProfile>();
	private static Map<Long, SipProfileState> profileStatus = new HashMap<Long, SipProfileState>();

	private static long sCounter = -1;

	private AccountListManager() {
		init();
	}

	public static AccountListManager getInstance() {
		if (mAccountListManager == null) {
			mAccountListManager = new AccountListManager();
		}
		return mAccountListManager;
	}

	private void init() {
	}

	public long addNewProfile(SipProfile profile) {
		String userName = profile.getUsername();
		if (TextUtils.isEmpty(userName)) {
			return SipProfile.INVALID_ID;
		}
		long addId = getProfileAccIdByUserName(userName);
		if (!profiles.containsKey(addId)) {
			sCounter++;
			profile.id = sCounter;
			profiles.put(sCounter, profile);
			return sCounter;
		}
		return SipProfile.INVALID_ID;
	}

	public boolean removeProfile(long id) {
		if (profiles.containsKey(id)) {
			profiles.remove(id);
			SipController.getInstance().broadcastAccountDelete(id);
			return true;
		}
		return false;
	}

	public boolean removeAllProfiles() {
		profiles.clear();
		return true;
	}

	public void updateProfile(long id, ContentValues cv) {
		if (!profiles.containsKey(id)) {
			return;
		}
		profiles.get(id).updateProfileFromValues(cv);
		SipController.getInstance().broadcastAccountChange(id);
	}

	public SipProfile getSipProfile(long id) {
		if (profiles.containsKey(id)) {
			return profiles.get(id);
		}
		return null;
	}

	public List<SipProfile> getAllSipProfile() {
		List<SipProfile> list = new ArrayList<SipProfile>();
		for (Map.Entry<Long, SipProfile> entry : profiles.entrySet()) {
			list.add(entry.getValue());
		}
		return list;
	}

	public long getProfileAccIdByUserName(String userName) {
		if (TextUtils.isEmpty(userName)) {
			return SipProfile.INVALID_ID;
		}
		for (Map.Entry<Long, SipProfile> entry : profiles.entrySet()) {
			SipProfile profile = entry.getValue();
			if (profile != null && !TextUtils.isEmpty(profile.getUsername())
					&& profile.getUsername().equals(userName)) {
				return profile.id;
			}
		}
		return SipProfile.INVALID_ID;

	}

	public long addNewProfileState(SipProfileState state) {
		long accId = state.getAccountId();
		if (!profileStatus.containsKey(accId) && accId != SipProfile.INVALID_ID) {
			profileStatus.put(accId, state);
			return accId;
		}
		return SipProfile.INVALID_ID;
	}

	public boolean updateProfileState(long accId, ContentValues cv) {
		if (!profileStatus.containsKey(accId)) {
			return false;
		}
		profileStatus.get(accId).updateFromContentValue(cv);
		if (cv.containsKey(SipProfileState.STATUS_CODE)) {
			int statusCode = profileStatus.get(accId).getStatusCode();
			SipController.getInstance().onAccoutStatusChanged(statusCode);
		}
		SipController.getInstance().broadcastRegistrationChange(accId);
		return true;
	}

	public boolean removeProfileState(long accId) {
		if (profileStatus.containsKey(accId)) {
			profileStatus.remove(accId);
			SipController.getInstance().broadcastRegistrationChange(accId);
			return true;
		}
		return false;
	}

	public SipProfileState getProfileState(long accId) {
		if (profileStatus.containsKey(accId)) {
			return profileStatus.get(accId);
		}
		return null;
	}

	public List<SipProfileState> getAllProfileState() {
		List<SipProfileState> list = new ArrayList<SipProfileState>();
		for (Map.Entry<Long, SipProfileState> entry : profileStatus.entrySet()) {
			list.add(entry.getValue());
		}
		return list;
	}

	public boolean removeAllProfileState() {
		profileStatus.clear();
		return true;
	}

	public boolean isProfileNoStatus() {
		if (profiles == null || profiles.size() < 1) {
			return false;
		}
		for (Map.Entry<Long, SipProfile> entry : profiles.entrySet()) {
			SipProfile profile = entry.getValue();
			long id = profile.id;
			if (!profileStatus.containsKey(id)) {
				return true;
			}
		}
		return false;
	}
}
