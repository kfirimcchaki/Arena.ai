package dev.arena.mobile;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/**
 * Typed wrapper over SharedPreferences holding every user-facing setting.
 * Plain static methods so any component can read/write preferences.
 */
public final class AppSettings {

    private static final String PREFS = "arena_prefs_v1";

    // Keys
    public static final String KEY_HOME = "home_page";
    public static final String KEY_SEARCH = "search_engine";
    public static final String KEY_DESKTOP = "desktop_site";
    public static final String KEY_ADBLOCK = "adblock";
    public static final String KEY_FORCE_DARK = "force_dark";
    public static final String KEY_TEXT_ZOOM = "text_zoom";
    public static final String KEY_AUTO_UPDATE = "auto_update_check";
    public static final String KEY_LAST_UPDATE_CHECK = "last_update_check";
    public static final String KEY_GEO_ALLOWED = "geo_allowed_origins";

    /** Magic value for KEY_HOME meaning the built-in quick-start page. */
    public static final String HOME_QUICKSTART = "arena:start";

    /** Default home: the branded quick-start page (arena.ai is one tap away). */
    public static final String DEFAULT_HOME = HOME_QUICKSTART;
    public static final String DEFAULT_SEARCH = "google";

    private AppSettings() {
    }

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String getString(Context c, String key, String def) {
        return p(c).getString(key, def);
    }

    public static void setString(Context c, String key, String value) {
        p(c).edit().putString(key, value).apply();
    }

    public static boolean getBool(Context c, String key, boolean def) {
        return p(c).getBoolean(key, def);
    }

    public static void setBool(Context c, String key, boolean value) {
        p(c).edit().putBoolean(key, value).apply();
    }

    public static int getInt(Context c, String key, int def) {
        return p(c).getInt(key, def);
    }

    public static void setInt(Context c, String key, int value) {
        p(c).edit().putInt(key, value).apply();
    }

    public static long getLong(Context c, String key, long def) {
        return p(c).getLong(key, def);
    }

    public static void setLong(Context c, String key, long value) {
        p(c).edit().putLong(key, value).apply();
    }

    public static boolean isQuickStart(String h) {
        return h == null || h.trim().isEmpty() || HOME_QUICKSTART.equals(h.trim());
    }

    /** Resolved URL for the home page (handles the magic quick-start value). */
    public static String homeUrl(Context c) {
        String h = getString(c, KEY_HOME, DEFAULT_HOME);
        if (isQuickStart(h)) {
            return "file:///android_asset/www/index.html";
        }
        String t = h.trim();
        if (!t.startsWith("http://") && !t.startsWith("https://")) t = "https://" + t;
        return t;
    }

    /** Human-readable label of the configured start page. */
    public static String homeLabel(Context c) {
        String h = getString(c, KEY_HOME, DEFAULT_HOME);
        if (isQuickStart(h)) return "Arena quick start";
        return h;
    }

    /** Whether a given origin is exempt from ad-blocking (user enabled "ads on this site"). */
    public static boolean isAdsEnabledFor(Context c, String origin) {
        return getStringSet(c, KEY_ADBLOCK + "_off_" + origin.replaceAll("[^a-zA-Z0-9.]", "_"))
                .contains("1");
    }

    public static void setAdsEnabledFor(Context c, String origin, boolean enabled) {
        String key = KEY_ADBLOCK + "_off_" + origin.replaceAll("[^a-zA-Z0-9.]", "_");
        Set<String> s = new HashSet<>();
        if (enabled) s.add("1");
        p(c).edit().putStringSet(key, s).apply();
    }

    public static boolean isGeoAllowed(Context c, String origin) {
        return getStringSet(c, KEY_GEO_ALLOWED).contains(origin);
    }

    public static void setGeoAllowed(Context c, String origin, boolean allowed) {
        String key = KEY_GEO_ALLOWED;
        Set<String> s = new HashSet<>(getStringSet(c, key));
        if (allowed) s.add(origin); else s.remove(origin);
        p(c).edit().putStringSet(key, s).apply();
    }

    public static Set<String> getStringSet(Context c, String key) {
        Set<String> s = p(c).getStringSet(key, null);
        return s == null ? new HashSet<String>() : s;
    }

    /** Snapshot of everything the home page needs, pushed to JS after any change. */
    public static String toJson(Context c) {
        JSONObject o = new JSONObject();
        try {
            o.put("home", homeLabel(c));
            o.put("quickstart", isQuickStart(getString(c, KEY_HOME, DEFAULT_HOME)));
            o.put("search", getString(c, KEY_SEARCH, DEFAULT_SEARCH));
            o.put("desktop", getBool(c, KEY_DESKTOP, false));
            o.put("adblock", getBool(c, KEY_ADBLOCK, true));
            o.put("dark", getBool(c, KEY_FORCE_DARK, false));
            o.put("version", BuildConfig.VERSION_NAME);
            o.put("code", BuildConfig.VERSION_CODE);
        } catch (JSONException ignored) {
        }
        return o.toString();
    }
}
