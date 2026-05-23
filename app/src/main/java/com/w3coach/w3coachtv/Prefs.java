package com.w3coach.w3coachtv;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * Zentraler Zugriff auf alle SharedPreferences.
 */
public class Prefs {

    public static final String KEY_URL1             = "clientUrl1";
    public static final String KEY_URL2             = "clientUrl2";
    public static final String KEY_URL3             = "clientUrl3";
    public static final String KEY_ZOOM             = "zoom";
    public static final String KEY_AUTO_UPDATE      = "autoUpdate";
    public static final String KEY_UPDATE_INTERVAL  = "updateIntervalHours";
    public static final String KEY_FIRST_RUN        = "firstRun";
    public static final String KEY_WG_ENDPOINT   = "wgEndpoint";
    public static final String KEY_WG_PUBLIC_KEY = "wgPublicKey";
    public static final String KEY_WG_CLIENT_IP  = "wgClientIp";
    public static final String KEY_WG_PRIVATE_KEY  = "wgPrivateKey";
    public static final String KEY_WG_SPLIT_TUNNEL = "wgSplitTunnel";

    public static final int    DEFAULT_ZOOM         = 5;  // 75 + 5*5 = 100%

    private final SharedPreferences prefs;

    public Prefs(Context context) {
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public SharedPreferences prefs() { return prefs; }

    public String  url1()           { return prefs.getString(KEY_URL1, ""); }
    public String  url2()           { return prefs.getString(KEY_URL2, ""); }
    public String  url3()           { return prefs.getString(KEY_URL3, ""); }
    public int     zoom()           { return prefs.getInt(KEY_ZOOM, DEFAULT_ZOOM); }
    public boolean autoUpdate()     { return prefs.getBoolean(KEY_AUTO_UPDATE, false); }
    public int     updateInterval() { return prefs.getInt(KEY_UPDATE_INTERVAL, AutoUpdateJob.DEFAULT_INTERVAL_HOURS); }
    public boolean firstRun()       { return prefs.getBoolean(KEY_FIRST_RUN, true); }
    public String  wgEndpoint()     { return prefs.getString(KEY_WG_ENDPOINT, ""); }
    public String  wgPublicKey()    { return prefs.getString(KEY_WG_PUBLIC_KEY, ""); }
    public String  wgClientIp()     { return prefs.getString(KEY_WG_CLIENT_IP, "10.0.0.3/32"); }
    public String  wgPrivateKey()   { return prefs.getString(KEY_WG_PRIVATE_KEY, ""); }
    public boolean wgSplitTunnel()  { return prefs.getBoolean(KEY_WG_SPLIT_TUNNEL, true); }

    public void setUrl1(String v)          { prefs.edit().putString(KEY_URL1, v).apply(); }
    public void setUrl2(String v)          { prefs.edit().putString(KEY_URL2, v).apply(); }
    public void setUrl3(String v)          { prefs.edit().putString(KEY_URL3, v).apply(); }
    public void setZoom(int v)             { prefs.edit().putInt(KEY_ZOOM, v).apply(); }
    public void setAutoUpdate(boolean v)   { prefs.edit().putBoolean(KEY_AUTO_UPDATE, v).apply(); }
    public void setUpdateInterval(int v)   { prefs.edit().putInt(KEY_UPDATE_INTERVAL, v).apply(); }
    public void setFirstRun(boolean v)     { prefs.edit().putBoolean(KEY_FIRST_RUN, v).apply(); }
    public void setWgEndpoint(String v)    { prefs.edit().putString(KEY_WG_ENDPOINT, v).apply(); }
    public void setWgPublicKey(String v)   { prefs.edit().putString(KEY_WG_PUBLIC_KEY, v).apply(); }
    public void setWgClientIp(String v)    { prefs.edit().putString(KEY_WG_CLIENT_IP, v).apply(); }
    public void setWgPrivateKey(String v)   { prefs.edit().putString(KEY_WG_PRIVATE_KEY, v).apply(); }
    public void setWgSplitTunnel(boolean v) { prefs.edit().putBoolean(KEY_WG_SPLIT_TUNNEL, v).apply(); }
}
