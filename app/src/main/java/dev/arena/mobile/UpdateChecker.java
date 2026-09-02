package dev.arena.mobile;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Checks the app's GitHub repository for newer APK releases.
 * Keep it dead-simple: GET /repos/{owner}/{repo}/releases/latest, pick the first
 * release asset ending in ".apk", compare semantic versions.
 */
public final class UpdateChecker {

    /** Default repo; override with -PrepoOwner/-PrepoName or buildConfig fields. */
    private static final String OWNER = "kfirimcchaki";
    private static final String REPO = "Arena.ai";

    public interface Listener {
        void onResult(UpdateInfo info);
    }

    public static final class UpdateInfo {
        public final String versionName;
        public final String versionCode;
        public final String apkUrl;
        public final String notes;
        public final String tag;

        UpdateInfo(String tag, String vn, String vc, String url, String notes) {
            this.tag = tag;
            this.versionName = vn;
            this.versionCode = vc;
            this.apkUrl = url;
            this.notes = notes;
        }

        public boolean isNewerThan(int localCode) {
            try {
                return Integer.parseInt(versionCode) > localCode;
            } catch (Exception e) {
                return false;
            }
        }
    }

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "update-checker");
        t.setDaemon(true);
        return t;
    });

    private UpdateChecker() {
    }

    public static void check(Context context, final Listener listener) {
        EXEC.execute(() -> {
            UpdateInfo info = null;
            try {
                info = fetchLatest();
            } catch (Exception ignored) {
            }
            final UpdateInfo f = info;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (listener != null) listener.onResult(f);
            });
        });
    }

    private static UpdateInfo fetchLatest() throws Exception {
        URL u = new URL("https://api.github.com/repos/" + OWNER + "/" + REPO + "/releases/latest");
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(20000);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("User-Agent", "ArenaMobile/" + BuildConfig.VERSION_NAME);
        int code = c.getResponseCode();
        if (code != 200) {
            c.disconnect();
            return null;
        }
        String body = readAll(c.getInputStream());
        c.disconnect();

        JSONObject rel = new JSONObject(body);
        String tag = rel.optString("tag_name", "");
        String notes = rel.optString("body", "");
        JSONArray assets = rel.optJSONArray("assets");
        String apkUrl = null;
        for (int i = 0; assets != null && i < assets.length(); i++) {
            String name = assets.optJSONObject(i).optString("name", "");
            if (name.endsWith(".apk") && !name.contains("-debug")
                    && !name.contains("noCompress")) {
                apkUrl = assets.optJSONObject(i).optString("browser_download_url");
                break;
            }
        }
        if (apkUrl == null) return null;
        // Tags look like "v1.0.0" and assets like "ArenaAI-1.0.0-<vc>.apk"; extract code.
        String vc = "0";
        int dash = apkUrl.lastIndexOf('-');
        int dot = apkUrl.lastIndexOf('.');
        if (dash >= 0 && dot > dash) {
            String maybe = apkUrl.substring(dash + 1, dot);
            if (maybe.matches("\\d+")) vc = maybe;
        }
        String vn = tag.replaceAll("^v", "");
        return new UpdateInfo(tag, vn, vc, apkUrl, notes);
    }

    private static String readAll(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }
}
