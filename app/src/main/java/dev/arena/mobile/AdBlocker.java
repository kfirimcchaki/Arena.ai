package dev.arena.mobile;

import android.content.Context;
import android.net.Uri;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

/**
 * Lightweight, self-contained tracker/ad blocker.
 *
 * Works like a classic hosts-file blocker:
 *  - "blocklist.txt" in assets holds host rules (one per line, "#" comments).
 *    A request is blocked when its host equals a rule or ends with "." + rule.
 *  - A tiny cosmetic script hides common ad containers on allowed pages
 *    (best-effort; the heavy lifting is the request blocking).
 */
public final class AdBlocker {

    private static final Set<String> RULES = new HashSet<>();
    private static String cosmeticJs = null;

    private AdBlocker() {
    }

    public static synchronized void init(Context context) {
        if (!RULES.isEmpty()) return;
        try (InputStream in = context.getAssets().open("blocklist.txt");
             BufferedReader r = new BufferedReader(new InputStreamReader(in))) {
            String line;
            while ((line = r.readLine()) != null) {
                String t = line.trim().toLowerCase();
                if (t.isEmpty() || t.startsWith("#")) continue;
                if (t.startsWith("||")) t = t.substring(2);
                RULES.add(t);
            }
        } catch (IOException ignored) {
        }
    }

    public static boolean isBlocked(String url) {
        if (url == null || RULES.isEmpty()) return false;
        String host = Uri.parse(url).getHost();
        if (host == null) return false;
        host = host.toLowerCase();
        while (host.startsWith("www.")) host = host.substring(4);
        if (RULES.contains(host)) return true;
        int idx;
        while ((idx = host.indexOf('.')) >= 0) {
            host = host.substring(idx + 1);
            if (RULES.contains(host)) return true;
        }
        return false;
    }

    /** Returns the cosmetic script (asset cosmetic.js), cached after first read. */
    public static synchronized String cosmeticJs(Context context) {
        if (cosmeticJs == null) {
            cosmeticJs = readAsset(context, "cosmetic.js");
            if (cosmeticJs == null) cosmeticJs = "";
        }
        return cosmeticJs;
    }

    static String readAsset(Context context, String name) {
        try (InputStream in = context.getAssets().open(name)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (IOException e) {
            return null;
        }
    }
}
