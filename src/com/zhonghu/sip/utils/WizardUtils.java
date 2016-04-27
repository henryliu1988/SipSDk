package com.zhonghu.sip.utils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import android.util.Log;

public class WizardUtils {
	public static class WizardInfo {
		public String label;
		public String id;
		public int icon;
		public int priority = 99;
		public Locale[] countries;
		public boolean isGeneric = false;
		public boolean isWorld = false;
		public Class<?> classObject;

		public WizardInfo(String aId, String aLabel, int aIcon, int aPriority,
				Locale[] aCountries, boolean aIsGeneric, boolean aIsWorld,
				Class<?> aClassObject) {
			id = aId;
			label = aLabel;
			icon = aIcon;
			priority = aPriority;
			countries = aCountries;
			isGeneric = aIsGeneric;
			isWorld = aIsWorld;
			classObject = aClassObject;
		}
	};

	private static boolean initDone = false;

	public static final String LABEL = "LABEL";
	public static final String ICON = "ICON";
	public static final String ID = "ID";
	public static final String LANG_DISPLAY = "DISPLAY";
	public static final String PRIORITY = "PRIORITY";
	public static final String PRIORITY_INT = "PRIORITY_INT";

	public static final String EXPERT_WIZARD_TAG = "EXPERT";
	public static final String BASIC_WIZARD_TAG = "BASIC";
	public static final String ADVANCED_WIZARD_TAG = "ADVANCED";
	public static final String LOCAL_WIZARD_TAG = "LOCAL";

	private static HashMap<String, WizardInfo> WIZARDS_DICT;

	private static class WizardPrioComparator implements
			Comparator<Map<String, Object>> {

		@Override
		public int compare(Map<String, Object> infos1,
				Map<String, Object> infos2) {
			if (infos1 != null && infos2 != null) {
				if ((Boolean) infos1.get(PRIORITY_INT)) {
					Integer w1 = (Integer) infos1.get(PRIORITY);
					Integer w2 = (Integer) infos2.get(PRIORITY);
					// Log.d(THIS_FILE, "Compare : "+w1+ " vs "+w2);
					if (w1 > w2) {
						return -1;
					}
					if (w1 < w2) {
						return 1;
					}
				} else {
					String name1 = (String) infos1.get(LABEL);
					String name2 = (String) infos2.get(LABEL);
					return name1.compareToIgnoreCase(name2);
				}
			}
			return 0;
		}
	}

	private static Locale locale(String isoCode) {
		String[] codes = isoCode.split("_");
		if (codes.length == 2) {
			return new Locale(codes[0].toLowerCase(), codes[1].toUpperCase());
		} else if (codes.length == 1) {
			return new Locale(codes[0].toLowerCase());
		}
		Log.e("WizardUtils", "Invalid locale " + isoCode);
		return null;
	}

}
