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

package com.zhonghu.sip.utils;

import java.io.File;
import java.io.FileFilter;
import java.lang.reflect.Field;
import java.util.regex.Pattern;

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.media.AudioManager;
import android.media.MediaRecorder.AudioSource;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Contacts;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.zhonghu.sip.api.SipConfigManager;
import com.zhonghu.sip.pref.PreferencesWrapper;

@SuppressWarnings("deprecation")
public final class Compatibility {

    private Compatibility() {
    }

    private static final String THIS_FILE = "Compat";

    public static int getApiLevel() {
        return android.os.Build.VERSION.SDK_INT;
    }

    public static boolean isCompatible(int apiLevel) {
        return android.os.Build.VERSION.SDK_INT >= apiLevel;
    }

    /**
     * Get the stream id for in call track. Can differ on some devices. Current
     * device for which it's different :
     * 
     * @return
     */
    public static int getInCallStream(boolean requestBluetooth) {
        /* Archos 5IT */
        if (android.os.Build.BRAND.equalsIgnoreCase("archos")
                && android.os.Build.DEVICE.equalsIgnoreCase("g7a")) {
            // Since archos has no voice call capabilities, voice call stream is
            // not implemented
            // So we have to choose the good stream tag, which is by default
            // falled back to music
            return AudioManager.STREAM_MUSIC;
        }
        if (requestBluetooth) {
            return 6; /* STREAM_BLUETOOTH_SCO -- Thx @Stefan for the contrib */
        }

        // return AudioManager.STREAM_MUSIC;
        return AudioManager.STREAM_VOICE_CALL;
    }

    public static boolean shouldUseRoutingApi() {
        Log.d(THIS_FILE, "Current device " + android.os.Build.BRAND + " - "
                + android.os.Build.DEVICE);

        // HTC evo 4G
        if (android.os.Build.PRODUCT.equalsIgnoreCase("htc_supersonic")) {
            return true;
        }

        // ZTE joe
        if (android.os.Build.DEVICE.equalsIgnoreCase("joe")) {
            return true;
        }

        // Samsung GT-S5830
        if (android.os.Build.DEVICE.toUpperCase().startsWith("GT-S")) {
            return true;
        }

        if (!isCompatible(4)) {
            // If android 1.5, force routing api use
            return true;
        } else {
            return false;
        }
    }

    public static boolean shouldUseModeApi() {

        // ZTE blade et joe
        if (android.os.Build.DEVICE.equalsIgnoreCase("blade")
                || android.os.Build.DEVICE.equalsIgnoreCase("joe")) {
            return true;
        }
        // Samsung GT-S5360 GT-S5830 GT-S6102 ... probably all..
        if (android.os.Build.DEVICE.toUpperCase().startsWith("GT-") ||
                android.os.Build.PRODUCT.toUpperCase().startsWith("GT-") ||
                android.os.Build.DEVICE.toUpperCase().startsWith("YP-")) {
            return true;
        }

        // HTC evo 4G
        if (android.os.Build.PRODUCT.equalsIgnoreCase("htc_supersonic")) {
            return true;
        }
        // LG P500, Optimus V
        if (android.os.Build.DEVICE.toLowerCase().startsWith("thunder")) {
            return true;
        }
        // LG-E720(b)
        if (android.os.Build.MODEL.toUpperCase().startsWith("LG-E720")
                && !Compatibility.isCompatible(9)) {
            return true;
        }
        // LG-G2
        if (android.os.Build.DEVICE.toLowerCase().startsWith("g2")
                && android.os.Build.BRAND.toLowerCase().startsWith("lge")) {
            return true;
        }
        // LG-LS840
        if (android.os.Build.DEVICE.toLowerCase().startsWith("cayman")) {
            return true;
        }

        // Huawei
        if (android.os.Build.DEVICE.equalsIgnoreCase("U8150") ||
                android.os.Build.DEVICE.equalsIgnoreCase("U8110") ||
                android.os.Build.DEVICE.equalsIgnoreCase("U8120") ||
                android.os.Build.DEVICE.equalsIgnoreCase("U8100") ||
                android.os.Build.DEVICE.toUpperCase().startsWith("U8836") ||
                android.os.Build.PRODUCT.equalsIgnoreCase("U8655") ||
                android.os.Build.DEVICE.toUpperCase().startsWith("HWU9700")) {
            return true;
        }

        // Moto defy mini
        if (android.os.Build.MODEL.equalsIgnoreCase("XT320")) {
            return true;
        }

        // Alcatel
        if (android.os.Build.DEVICE.toUpperCase().startsWith("ONE_TOUCH_993D")) {
            return true;
        }

        // N4
        if (android.os.Build.DEVICE.toUpperCase().startsWith("MAKO")) {
            return true;
        }

        return false;
    }

    public static String guessInCallMode() {
        // New api for 2.3.3 is not available on galaxy S II :(
        if (!isCompatible(11) && android.os.Build.DEVICE.toUpperCase().startsWith("GT-I9100")) {
            return Integer.toString(AudioManager.MODE_NORMAL);
        }

        if (android.os.Build.BRAND.equalsIgnoreCase("sdg") || isCompatible(10)) {
            // Note that in APIs this is only available from level 11.
            return "3";
        }
        if (android.os.Build.DEVICE.equalsIgnoreCase("blade")) {
            return Integer.toString(AudioManager.MODE_IN_CALL);
        }

        if (!isCompatible(5)) {
            return Integer.toString(AudioManager.MODE_IN_CALL);
        }

        return Integer.toString(AudioManager.MODE_NORMAL);
    }

    public static String getDefaultMicroSource() {
        // Except for galaxy S II :(
        if (!isCompatible(11) && android.os.Build.DEVICE.toUpperCase().startsWith("GT-I9100")) {
            return Integer.toString(AudioSource.MIC);
        }

        if (isCompatible(10)) {
            // Note that in APIs this is only available from level 11.
            // VOICE_COMMUNICATION
            return Integer.toString(0x7);
        }
        /*
         * Too risky in terms of regressions else if (isCompatible(4)) { //
         * VOICE_CALL return 0x4; }
         */
        /*
         * if(android.os.Build.MODEL.equalsIgnoreCase("X10i")) { // VOICE_CALL
         * return Integer.toString(0x4); }
         */
        /*
         * Not relevant anymore, atrix I tested sounds fine with that
         * if(android.os.Build.DEVICE.equalsIgnoreCase("olympus")) { //Motorola
         * atrix bug // CAMCORDER return Integer.toString(0x5); }
         */

        return Integer.toString(AudioSource.DEFAULT);
    }

    public static String getDefaultFrequency() {
        if (android.os.Build.DEVICE.equalsIgnoreCase("olympus")) {
            // Atrix bug
            return "32000";
        }
        if (android.os.Build.DEVICE.toUpperCase().equals("GT-P1010")) {
            // Galaxy tab see issue 932
            return "32000";
        }

        return isCompatible(4) ? "16000" : "8000";
    }

    public static String getCpuAbi() {
        if (isCompatible(4)) {
            Field field;
            try {
                field = android.os.Build.class.getField("CPU_ABI");
                return field.get(null).toString();
            } catch (Exception e) {
                Log.w(THIS_FILE, "Announce to be android 1.6 but no CPU ABI field", e);
            }

        }
        return "armeabi";
    }

    public final static int getNumCores() {
        // Private Class to display only CPU devices in the directory listing
        class CpuFilter implements FileFilter {
            @Override
            public boolean accept(File pathname) {
                // Check if filename is "cpu", followed by a single digit number
                if (Pattern.matches("cpu[0-9]", pathname.getName())) {
                    return true;
                }
                return false;
            }
        }
        try {
            // Get directory containing CPU info
            File dir = new File("/sys/devices/system/cpu/");
            // Filter to only list the devices we care about
            File[] files = dir.listFiles(new CpuFilter());
            // Return the number of cores (virtual CPU devices)
            return files.length;
        } catch (Exception e) {
            return Runtime.getRuntime().availableProcessors();
        }
    }

    private static boolean needPspWorkaround() {
        // New api for 2.3 does not work on Incredible S
        if (android.os.Build.DEVICE.equalsIgnoreCase("vivo")) {
            return true;
        }

        // New API for android 2.3 should be able to manage this but do only for
        // honeycomb cause seems not correctly supported by all yet
        if (isCompatible(11)) {
            return false;
        }

        // All htc except....
        if (android.os.Build.PRODUCT.toLowerCase().startsWith("htc")
                || android.os.Build.BRAND.toLowerCase().startsWith("htc")
                || android.os.Build.PRODUCT.toLowerCase().equalsIgnoreCase("inc") /*
                                                                                   * For
                                                                                   * Incredible
                                                                                   */
                || android.os.Build.DEVICE.equalsIgnoreCase("passion") /* N1 */) {
            if (android.os.Build.DEVICE.equalsIgnoreCase("hero") /* HTC HERO */
                    || android.os.Build.DEVICE.equalsIgnoreCase("magic") /*
                                                                          * Magic
                                                                          * Aka
                                                                          * Dev
                                                                          * G2
                                                                          */
                    || android.os.Build.DEVICE.equalsIgnoreCase("tatoo") /* Tatoo */
                    || android.os.Build.DEVICE.equalsIgnoreCase("dream") /*
                                                                          * Dream
                                                                          * Aka
                                                                          * Dev
                                                                          * G1
                                                                          */
                    || android.os.Build.DEVICE.equalsIgnoreCase("legend") /* Legend */

            ) {
                return false;
            }

            // Older than 2.3 has no chance to have the new full perf wifi mode
            // working since does not exists
            if (!isCompatible(9)) {
                return true;
            } else {
                // N1 is fine with that
                if (android.os.Build.DEVICE.equalsIgnoreCase("passion")) {
                    return false;
                }
                return true;
            }

        }
        // Dell streak
        if (android.os.Build.BRAND.toLowerCase().startsWith("dell") &&
                android.os.Build.DEVICE.equalsIgnoreCase("streak")) {
            return true;
        }
        // Motorola milestone 1 and 2 & motorola droid & defy not under 2.3
        if ((android.os.Build.DEVICE.toLowerCase().contains("milestone2") ||
                android.os.Build.BOARD.toLowerCase().contains("sholes") ||
                android.os.Build.PRODUCT.toLowerCase().contains("sholes") ||
                android.os.Build.DEVICE.equalsIgnoreCase("olympus") ||
                android.os.Build.DEVICE.toLowerCase().contains("umts_jordan")) && !isCompatible(9)) {
            return true;
        }
        // Moto defy mini
        if (android.os.Build.MODEL.equalsIgnoreCase("XT320")) {
            return true;
        }

        // Alcatel ONE touch
        if (android.os.Build.DEVICE.startsWith("one_touch_990")) {
            return true;
        }

        return false;
    }

    private static boolean needToneWorkaround() {
        if (android.os.Build.PRODUCT.toLowerCase().startsWith("gt-i5800") ||
                android.os.Build.PRODUCT.toLowerCase().startsWith("gt-i5801") ||
                android.os.Build.PRODUCT.toLowerCase().startsWith("gt-i9003")) {
            return true;
        }
        return false;
    }

    private static boolean needSGSWorkaround() {
        if (isCompatible(9)) {
            return false;
        }
        if (android.os.Build.DEVICE.toUpperCase().startsWith("GT-I9000") ||
                android.os.Build.DEVICE.toUpperCase().startsWith("GT-P1000")) {
            return true;
        }
        return false;
    }

    private static boolean needWebRTCImplementation() {
        if (android.os.Build.DEVICE.toLowerCase().contains("droid2")) {
            return true;
        }
        if (android.os.Build.MODEL.toLowerCase().contains("droid bionic")) {
            return true;
        }
        if (android.os.Build.DEVICE.toLowerCase().contains("sunfire")) {
            return true;
        }
        // Huawei Y300
        if (android.os.Build.DEVICE.equalsIgnoreCase("U8833")) {
            return true;
        }
        return false;
    }

    public static boolean shouldSetupAudioBeforeInit() {
        // Setup for GT / GS samsung devices.
        if (android.os.Build.DEVICE.toLowerCase().startsWith("gt-")
                || android.os.Build.PRODUCT.toLowerCase().startsWith("gt-")) {
            return true;
        }
        return false;
    }

    private static boolean shouldFocusAudio() {
        /* HTC One X */
        if (android.os.Build.DEVICE.toLowerCase().startsWith("endeavoru") ||
                android.os.Build.DEVICE.toLowerCase().startsWith("evita")) {
            return false;
        }

        if (android.os.Build.DEVICE.toUpperCase().startsWith("GT-P7510") && isCompatible(15)) {
            return false;
        }
        return true;
    }

    private static int getDefaultAudioImplementation() {
        // Acer A510
        if (android.os.Build.DEVICE.toLowerCase().startsWith("picasso")) {
            return SipConfigManager.AUDIO_IMPLEMENTATION_JAVA;
        }
        if (Compatibility.isCompatible(11)) {
            return SipConfigManager.AUDIO_IMPLEMENTATION_OPENSLES;
        }
        if (android.os.Build.DEVICE.equalsIgnoreCase("ST25i") && Compatibility.isCompatible(10)) {
            return SipConfigManager.AUDIO_IMPLEMENTATION_OPENSLES;
        }
        if (android.os.Build.DEVICE.equalsIgnoreCase("u8510") && Compatibility.isCompatible(10)) {
            return SipConfigManager.AUDIO_IMPLEMENTATION_OPENSLES;
        }
        if (android.os.Build.DEVICE.toLowerCase().startsWith("rk31sdk")) {
            return SipConfigManager.AUDIO_IMPLEMENTATION_JAVA;
        }
        return SipConfigManager.AUDIO_IMPLEMENTATION_JAVA;
    }

    private static void resetCodecsSettings(PreferencesWrapper preferencesWrapper) {
        boolean supportFloating = false;
        boolean isHeavyCpu = false;
        String abi = getCpuAbi();
        if (!TextUtils.isEmpty(abi)) {
            if (abi.equalsIgnoreCase("mips") || abi.equalsIgnoreCase("x86")) {
                supportFloating = true;
            }
            if (abi.equalsIgnoreCase("armeabi-v7a") || abi.equalsIgnoreCase("x86")) {
                isHeavyCpu = true;
            }
        }

        // For Narrowband
        preferencesWrapper.setCodecPriority("PCMU/8000/1", SipConfigManager.CODEC_NB, "60");
        preferencesWrapper.setCodecPriority("PCMA/8000/1", SipConfigManager.CODEC_NB, "50");
        preferencesWrapper.setCodecPriority("speex/8000/1", SipConfigManager.CODEC_NB, "0");
        preferencesWrapper.setCodecPriority("speex/16000/1", SipConfigManager.CODEC_NB, "0");
        preferencesWrapper.setCodecPriority("speex/32000/1", SipConfigManager.CODEC_NB, "0");
        preferencesWrapper.setCodecPriority("GSM/8000/1", SipConfigManager.CODEC_NB, "230");
        preferencesWrapper.setCodecPriority("G722/16000/1", SipConfigManager.CODEC_NB, "0");
        preferencesWrapper.setCodecPriority("G729/8000/1", SipConfigManager.CODEC_NB, "0");
        preferencesWrapper.setCodecPriority("iLBC/8000/1", SipConfigManager.CODEC_NB, "0");
        preferencesWrapper.setCodecPriority("SILK/8000/1", SipConfigManager.CODEC_NB, "239");
        preferencesWrapper.setCodecPriority("SILK/12000/1", SipConfigManager.CODEC_NB, "0");
        preferencesWrapper.setCodecPriority("SILK/16000/1", SipConfigManager.CODEC_NB, "0");
        preferencesWrapper.setCodecPriority("SILK/24000/1", SipConfigManager.CODEC_NB, "0");
        preferencesWrapper.setCodecPriority("CODEC2/8000/1", SipConfigManager.CODEC_NB, "0");
        preferencesWrapper.setCodecPriority("G7221/16000/1", SipConfigManager.CODEC_NB, "0");
        preferencesWrapper.setCodecPriority("G7221/32000/1", SipConfigManager.CODEC_NB, "0");
        preferencesWrapper.setCodecPriority("ISAC/16000/1", SipConfigManager.CODEC_NB, "0");
        preferencesWrapper.setCodecPriority("ISAC/32000/1", SipConfigManager.CODEC_NB, "0");
        preferencesWrapper.setCodecPriority("AMR/8000/1", SipConfigManager.CODEC_NB, "0");
        preferencesWrapper.setCodecPriority("AMR-WB/16000/1", SipConfigManager.CODEC_NB, "0");
        preferencesWrapper.setCodecPriority("opus/8000/1", SipConfigManager.CODEC_NB, "0");
        preferencesWrapper.setCodecPriority("opus/16000/1", SipConfigManager.CODEC_NB, "0");
        preferencesWrapper.setCodecPriority("opus/24000/1", SipConfigManager.CODEC_NB, "0");
        preferencesWrapper.setCodecPriority("opus/48000/1", SipConfigManager.CODEC_NB, "0");
        preferencesWrapper.setCodecPriority("G726-16/8000/1", SipConfigManager.CODEC_NB, "0");
        preferencesWrapper.setCodecPriority("G726-24/8000/1", SipConfigManager.CODEC_NB, "0");
        preferencesWrapper.setCodecPriority("G726-32/8000/1", SipConfigManager.CODEC_NB, "0");
        preferencesWrapper.setCodecPriority("G726-40/8000/1", SipConfigManager.CODEC_NB, "0");
        preferencesWrapper.setCodecPriority("mpeg4-generic/48000/1", SipConfigManager.CODEC_NB, "0");

        // For Wideband
        preferencesWrapper.setCodecPriority("PCMU/8000/1", SipConfigManager.CODEC_WB, "60");
        preferencesWrapper.setCodecPriority("PCMA/8000/1", SipConfigManager.CODEC_WB, "50");
        preferencesWrapper.setCodecPriority("speex/8000/1", SipConfigManager.CODEC_WB, "0");
        preferencesWrapper.setCodecPriority("speex/16000/1", SipConfigManager.CODEC_WB, "0");
        preferencesWrapper.setCodecPriority("speex/32000/1", SipConfigManager.CODEC_WB, "0");
        preferencesWrapper.setCodecPriority("GSM/8000/1", SipConfigManager.CODEC_WB, "0");
        preferencesWrapper.setCodecPriority("G722/16000/1", SipConfigManager.CODEC_WB,
                supportFloating ? "235" : "0");
        preferencesWrapper.setCodecPriority("G729/8000/1", SipConfigManager.CODEC_WB, "0");
        preferencesWrapper.setCodecPriority("iLBC/8000/1", SipConfigManager.CODEC_WB, "0");
        preferencesWrapper.setCodecPriority("SILK/8000/1", SipConfigManager.CODEC_WB, "0");
        preferencesWrapper.setCodecPriority("SILK/12000/1", SipConfigManager.CODEC_WB, "0");
        preferencesWrapper.setCodecPriority("SILK/16000/1", SipConfigManager.CODEC_WB,
                isHeavyCpu ? "0" : "220");
        preferencesWrapper.setCodecPriority("SILK/24000/1", SipConfigManager.CODEC_WB,
                isHeavyCpu ? "220" : "0");
        preferencesWrapper.setCodecPriority("CODEC2/8000/1", SipConfigManager.CODEC_WB, "0");
        preferencesWrapper.setCodecPriority("G7221/16000/1", SipConfigManager.CODEC_WB, "0");
        preferencesWrapper.setCodecPriority("G7221/32000/1", SipConfigManager.CODEC_WB, "0");
        preferencesWrapper.setCodecPriority("ISAC/16000/1", SipConfigManager.CODEC_WB, "0");
        preferencesWrapper.setCodecPriority("ISAC/32000/1", SipConfigManager.CODEC_WB, "0");
        preferencesWrapper.setCodecPriority("AMR/8000/1", SipConfigManager.CODEC_WB, "0");
        preferencesWrapper.setCodecPriority("AMR-WB/16000/1", SipConfigManager.CODEC_WB, "0");
        preferencesWrapper.setCodecPriority("opus/8000/1", SipConfigManager.CODEC_WB, "0");
        preferencesWrapper.setCodecPriority("opus/16000/1", SipConfigManager.CODEC_WB, "0");
        preferencesWrapper.setCodecPriority("opus/24000/1", SipConfigManager.CODEC_WB, "0");
        preferencesWrapper.setCodecPriority("opus/48000/1", SipConfigManager.CODEC_WB, "0");
        preferencesWrapper.setCodecPriority("G726-16/8000/1", SipConfigManager.CODEC_WB, "0");
        preferencesWrapper.setCodecPriority("G726-24/8000/1", SipConfigManager.CODEC_WB, "0");
        preferencesWrapper.setCodecPriority("G726-32/8000/1", SipConfigManager.CODEC_WB, "0");
        preferencesWrapper.setCodecPriority("G726-40/8000/1", SipConfigManager.CODEC_WB, "0");
        preferencesWrapper.setCodecPriority("mpeg4-generic/48000/1", SipConfigManager.CODEC_WB, "0");

        // Bands repartition
        preferencesWrapper.setPreferenceStringValue("band_for_wifi", SipConfigManager.CODEC_WB);
        preferencesWrapper.setPreferenceStringValue("band_for_other", SipConfigManager.CODEC_WB);
        preferencesWrapper.setPreferenceStringValue("band_for_3g", SipConfigManager.CODEC_NB);
        preferencesWrapper.setPreferenceStringValue("band_for_gprs", SipConfigManager.CODEC_NB);
        preferencesWrapper.setPreferenceStringValue("band_for_edge", SipConfigManager.CODEC_NB);

    }

    public static void setFirstRunParameters(PreferencesWrapper preferencesWrapper) {
        preferencesWrapper.startEditing();
        resetCodecsSettings(preferencesWrapper);

        preferencesWrapper.setPreferenceStringValue(SipConfigManager.SND_MEDIA_QUALITY, getCpuAbi()
                .equalsIgnoreCase("armeabi-v7a") ? "4" : "3");
        preferencesWrapper.setPreferenceStringValue(SipConfigManager.SND_AUTO_CLOSE_TIME,
                isCompatible(4) ? "1" : "5");
        preferencesWrapper.setPreferenceStringValue(SipConfigManager.SND_CLOCK_RATE,
                getDefaultFrequency());
        // HTC PSP mode hack
        preferencesWrapper.setPreferenceBooleanValue(SipConfigManager.KEEP_AWAKE_IN_CALL,
                needPspWorkaround());
        preferencesWrapper.setPreferenceStringValue(SipConfigManager.MEDIA_THREAD_COUNT,
                getNumCores() > 1 ? "2" : "1");

        // Proximity sensor inverted
        if (android.os.Build.PRODUCT.equalsIgnoreCase("SPH-M900") /* Sgs moment */) {
            preferencesWrapper.setPreferenceBooleanValue(SipConfigManager.INVERT_PROXIMITY_SENSOR,
                    true);
        }


        // Galaxy S default settings
        if (android.os.Build.DEVICE.toUpperCase().startsWith("GT-I9000") && !isCompatible(9)) {
            preferencesWrapper.setPreferenceFloatValue(SipConfigManager.SND_MIC_LEVEL, (float) 0.4);
            preferencesWrapper.setPreferenceFloatValue(SipConfigManager.SND_SPEAKER_LEVEL,
                    (float) 0.2);
            preferencesWrapper.setPreferenceBooleanValue(SipConfigManager.USE_SOFT_VOLUME, true);
        }
        // HTC evo 4G
        if (android.os.Build.PRODUCT.equalsIgnoreCase("htc_supersonic") && !isCompatible(9)) {
            preferencesWrapper.setPreferenceFloatValue(SipConfigManager.SND_MIC_LEVEL, (float) 0.5);
            preferencesWrapper.setPreferenceFloatValue(SipConfigManager.SND_SPEAKER_LEVEL,
                    (float) 1.5);

        }

        // Api to use for routing
        preferencesWrapper.setPreferenceBooleanValue(SipConfigManager.USE_ROUTING_API,
                shouldUseRoutingApi());
        preferencesWrapper.setPreferenceBooleanValue(SipConfigManager.USE_MODE_API,
                shouldUseModeApi());
        preferencesWrapper.setPreferenceBooleanValue(SipConfigManager.SET_AUDIO_GENERATE_TONE,
                needToneWorkaround());
        preferencesWrapper.setPreferenceBooleanValue(SipConfigManager.USE_SGS_CALL_HACK,
                needSGSWorkaround());
        preferencesWrapper.setPreferenceStringValue(SipConfigManager.SIP_AUDIO_MODE,
                guessInCallMode());
        preferencesWrapper.setPreferenceStringValue(SipConfigManager.MICRO_SOURCE,
                getDefaultMicroSource());
        preferencesWrapper.setPreferenceBooleanValue(SipConfigManager.USE_WEBRTC_HACK,
                needWebRTCImplementation());
        preferencesWrapper.setPreferenceBooleanValue(SipConfigManager.DO_FOCUS_AUDIO,
                shouldFocusAudio());

        boolean usePriviledged = shouldUsePriviledgedIntegration(preferencesWrapper.getContext());
        preferencesWrapper.setPreferenceBooleanValue(SipConfigManager.INTEGRATE_TEL_PRIVILEGED,
                usePriviledged);
        if (usePriviledged) {
            preferencesWrapper.setPreferenceBooleanValue(SipConfigManager.INTEGRATE_WITH_DIALER,
                    !usePriviledged);
        }

        if (android.os.Build.PRODUCT.startsWith("GoGear_Connect")) {
            preferencesWrapper.setPreferenceBooleanValue(SipConfigManager.INTEGRATE_WITH_CALLLOGS,
                    false);
        }

        preferencesWrapper.setPreferenceStringValue(SipConfigManager.AUDIO_IMPLEMENTATION,
                Integer.toString(getDefaultAudioImplementation()));
        preferencesWrapper.setPreferenceBooleanValue(SipConfigManager.SETUP_AUDIO_BEFORE_INIT,
                shouldSetupAudioBeforeInit());

        preferencesWrapper.endEditing();
    }

    public static boolean useFlipAnimation() {
        if (android.os.Build.BRAND.equalsIgnoreCase("archos")
                && android.os.Build.DEVICE.equalsIgnoreCase("g7a")) {
            return false;
        }
        return true;
    }

    /**
     * Check if we can make gsm calls from within the application It will check
     * setting and capability of the device
     * 
     * @param context
     * @return
     */
    public static boolean canMakeGSMCall(Context context) {
        int integType = SipConfigManager.getPreferenceIntegerValue(context,
                SipConfigManager.GSM_INTEGRATION_TYPE, SipConfigManager.GENERIC_TYPE_PREVENT);
        if (integType == SipConfigManager.GENERIC_TYPE_AUTO) {
            return PhoneCapabilityTester.isPhone(context);
        }
        if (integType == SipConfigManager.GENERIC_TYPE_PREVENT) {
            return false;
        }
        return true;
    }

    public static Intent getContactPhoneIntent() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        /*
         * intent.setAction(Intent.ACTION_GET_CONTENT);
         * intent.setType(Contacts.Phones.CONTENT_ITEM_TYPE);
         */
        if (isCompatible(5)) {
            // Don't use constant to allow backward compat simply
            intent.setData(Uri.parse("content://com.android.contacts/contacts"));
        } else {
            // Fallback for android 4
            intent.setData(Contacts.People.CONTENT_URI);
        }

        return intent;

    }

    private static boolean shouldUsePriviledgedIntegration(Context ctxt) {
        return !PhoneCapabilityTester.isPhone(ctxt);
    }

    public static void updateVersion(PreferencesWrapper prefWrapper, int lastSeenVersion,
            int runningVersion) {}

    public static void updateApiVersion(PreferencesWrapper prefWrapper, int lastSeenVersion,
            int runningVersion) {}

    public static int getHomeMenuId() {
        return 0x0102002c;
        // return android.R.id.home;
    }

    public static boolean isInstalledOnSdCard(Context context) {
        // check for API level 8 and higher
        if (Compatibility.isCompatible(8)) {
            PackageManager pm = context.getPackageManager();
            try {
                PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);
                ApplicationInfo ai = pi.applicationInfo;
                return (ai.flags & 0x00040000 /*
                                               * ApplicationInfo.
                                               * FLAG_EXTERNAL_STORAGE
                                               */) == 0x00040000 /*
                                                                  * ApplicationInfo.
                                                                  * FLAG_EXTERNAL_STORAGE
                                                                  */;
            } catch (NameNotFoundException e) {
                // ignore
            }
        }

        // check for API level 7 - check files dir
        try {
            String filesDir = context.getFilesDir().getAbsolutePath();
            if (filesDir.startsWith("/data/")) {
                return false;
            } else if (filesDir.contains(Environment.getExternalStorageDirectory().getPath())) {
                return true;
            }
        } catch (Throwable e) {
            // ignore
        }

        return false;
    }

    /**
     * Get the current wifi sleep policy
     * @param ctntResolver android content resolver
     * @return the current wifi sleep policy
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public static int getWifiSleepPolicy(ContentResolver ctntResolver) {
        if(Compatibility.isCompatible(Build.VERSION_CODES.JELLY_BEAN_MR1)) {
            return Settings.Global.getInt(ctntResolver, Settings.Global.WIFI_SLEEP_POLICY, Settings.Global.WIFI_SLEEP_POLICY_DEFAULT);
        }else {
            return Settings.System.getInt(ctntResolver, Settings.System.WIFI_SLEEP_POLICY, Settings.System.WIFI_SLEEP_POLICY_DEFAULT);
        }
    }

    /**
     * @return default wifi sleep policy
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public static int getWifiSleepPolicyDefault() {
        if(Compatibility.isCompatible(Build.VERSION_CODES.JELLY_BEAN_MR1)) {
            return Settings.Global.WIFI_SLEEP_POLICY_DEFAULT;
        }else {
            return Settings.System.WIFI_SLEEP_POLICY_DEFAULT;
        }
    }

    /**
     * @return wifi policy to never sleep
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public static int getWifiSleepPolicyNever() {
        if(Compatibility.isCompatible(Build.VERSION_CODES.JELLY_BEAN_MR1)) {
            return Settings.Global.WIFI_SLEEP_POLICY_NEVER;
        }else {
            return Settings.System.WIFI_SLEEP_POLICY_NEVER;
        }
    }
    
    /**
     * Set wifi policy to a value
     * @param ctntResolver context content resolver
     * @param policy the policy to set
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public static void setWifiSleepPolicy(ContentResolver ctntResolver, int policy) {
        if(!Compatibility.isCompatible(Build.VERSION_CODES.JELLY_BEAN_MR1)) {
          //  Settings.System.putInt(ctntResolver, Settings.System.WIFI_SLEEP_POLICY, policy);
        }else {
            // We are not granted permission to change that in api 17+
            //Settings.Global.putInt(ctntResolver, Settings.Global.WIFI_SLEEP_POLICY, policy);
        }
    }

    /**
     * Wrapper to set alarm at exact time
     * @see android.app.AlarmManager#setExact(int, long, PendingIntent)
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static void setExactAlarm(AlarmManager alarmManager, int alarmType, long firstTime,
            PendingIntent pendingIntent) {
        if(isCompatible(Build.VERSION_CODES.KITKAT)) {
            alarmManager.setExact(alarmType, firstTime, pendingIntent);
        }else {
            alarmManager.set(alarmType, firstTime, pendingIntent);
        }
    }
}
