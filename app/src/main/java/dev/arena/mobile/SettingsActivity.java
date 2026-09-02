package dev.arena.mobile;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/** Native settings screen for the browser (Appearance / Privacy / Storage / About). */
public class SettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.parseColor("#0B0F24"));
        setContentView(page);

        // Header
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setBackgroundColor(Color.parseColor("#0D1230"));
        head.setPadding(dp(8), dp(10), dp(8), dp(10));
        TextView back = new TextView(this);
        back.setText("‹  Back");
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        back.setTextColor(Color.parseColor("#38E1FF"));
        back.setPadding(dp(12), dp(4), dp(12), dp(4));
        back.setOnClickListener(v -> finish());
        head.addView(back);
        TextView title = new TextView(this);
        title.setText("Settings");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        head.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        TextView spacer = new TextView(this);
        spacer.setText("    ");
        head.addView(spacer);
        page.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        ScrollView sc = new ScrollView(this);
        sc.addView(body);
        page.addView(sc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // ---------------- Search & start
        body.addView(section("SEARCH & START"));
        body.addView(row("Search engine", value(searchEngineName()),
                v -> pickSearchEngine()));
        body.addView(row("Start page", AppSettings.homeLabel(this),
                v -> editStartPage()));
        body.addView(toggle("Block ads & trackers",
                AppSettings.getBool(this, AppSettings.KEY_ADBLOCK, true),
                (s, on) -> AppSettings.setBool(this, AppSettings.KEY_ADBLOCK, on)));

        // ---------------- Display
        body.addView(section("DISPLAY"));
        body.addView(toggle("Desktop site (mobile sites off)",
                AppSettings.getBool(this, AppSettings.KEY_DESKTOP, false),
                (s, on) -> AppSettings.setBool(this, AppSettings.KEY_DESKTOP, on)));
        body.addView(toggle("Force dark web content",
                AppSettings.getBool(this, AppSettings.KEY_FORCE_DARK, false),
                (s, on) -> AppSettings.setBool(this, AppSettings.KEY_FORCE_DARK, on)));
        body.addView(row("Text size",
                AppSettings.getInt(this, AppSettings.KEY_TEXT_ZOOM, 100) + "%",
                v -> pickTextZoom()));

        // ---------------- Data
        body.addView(section("DATA & UPDATES"));
        body.addView(toggle("Check for app updates daily",
                AppSettings.getBool(this, AppSettings.KEY_AUTO_UPDATE, true),
                (s, on) -> AppSettings.setBool(this, AppSettings.KEY_AUTO_UPDATE, on)));
        body.addView(row("Check for updates now", v -> checkNow()));
        body.addView(row("Open Downloads folder", v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
                        "content://com.android.providers.downloads.documents/root/downloads")));
            } catch (Exception e) {
                Toast.makeText(this, "Downloads folder unavailable", Toast.LENGTH_SHORT).show();
            }
        }));
        body.addView(row("Clear browsing data (cookies, cache, storage)", v ->
                confirmClearData()));
        body.addView(row("Request notification permission", v -> {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(
                    Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            } else {
                Toast.makeText(this, "Notification permission already granted",
                        Toast.LENGTH_SHORT).show();
            }
        }));

        // ---------------- About
        body.addView(section("ABOUT"));
        body.addView(row("Version",
                "Arena AI " + BuildConfig.VERSION_NAME + " (build " + BuildConfig.VERSION_CODE + ")",
                v -> aboutDialog()));
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    private TextView section(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setLetterSpacing(0.08f);
        tv.setTextColor(Color.parseColor("#8B93C4"));
        tv.setPadding(dp(20), dp(18), dp(20), dp(2));
        return tv;
    }

    private String value(String v) {
        return v == null ? "" : v;
    }

    private View row(String label, String sub, View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(8), dp(16), dp(8));
        row.setBackgroundResource(R.drawable.bg_icon_ripple);
        row.setOnClickListener(onClick);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t1 = new TextView(this);
        t1.setText(label);
        t1.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t1.setTextColor(Color.WHITE);
        texts.addView(t1);
        if (sub != null && !sub.isEmpty()) {
            TextView t2 = new TextView(this);
            t2.setText(sub);
            t2.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            t2.setTextColor(Color.parseColor("#8B93C4"));
            t2.setMaxLines(2);
            texts.addView(t2);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView chev = new ImageView(this);
        chev.setImageDrawable(getDrawable(R.drawable.ic_chevron_right));
        chev.setColorFilter(Color.parseColor("#5B6390"));
        row.addView(chev, new LinearLayout.LayoutParams(dp(18), dp(18)));
        return row;
    }

    private View toggle(String label, boolean initial,
                        final android.widget.CompoundButton.OnCheckedChangeListener action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(4), dp(16), dp(4));
        TextView t1 = new TextView(this);
        t1.setText(label);
        t1.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t1.setTextColor(Color.WHITE);
        row.addView(t1, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final Switch sw = new Switch(this);
        sw.setChecked(initial);
        sw.setOnCheckedChangeListener(action);
        row.addView(sw);
        return row;
    }

    private String searchEngineName() {
        switch (AppSettings.getString(this, AppSettings.KEY_SEARCH, "google")) {
            case "duckduckgo":
                return "DuckDuckGo";
            case "bing":
                return "Bing";
            case "startpage":
                return "Startpage";
            case "yahoo":
                return "Yahoo";
            case "google":
            default:
                return "Google";
        }
    }

    private void pickSearchEngine() {
        final String[] names = {"Google", "Bing", "DuckDuckGo", "Startpage", "Yahoo"};
        final String[] keys = {"google", "bing", "duckduckgo", "startpage", "yahoo"};
        int cur = 0;
        String current = AppSettings.getString(this, AppSettings.KEY_SEARCH, "google");
        for (int i = 0; i < keys.length; i++) {
            if (keys[i].equals(current)) cur = i;
        }
        new AlertDialog.Builder(this)
                .setTitle("Search engine")
                .setSingleChoiceItems(names, cur, (d, w) -> {
                    AppSettings.setString(this, AppSettings.KEY_SEARCH, keys[w]);
                    d.dismiss();
                    Toast.makeText(this, "Search engine set to " + names[w],
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void editStartPage() {
        final String[] options = {
                "Arena quick start page (recommended)",
                "arena.ai — open straight into the arena",
                "Custom address…"
        };
        new AlertDialog.Builder(this)
                .setTitle("Start page")
                .setItems(options, (d, w) -> {
                    if (w == 0) {
                        AppSettings.setString(this, AppSettings.KEY_HOME,
                                AppSettings.HOME_QUICKSTART);
                        applyHomeChange();
                    } else if (w == 1) {
                        AppSettings.setString(this, AppSettings.KEY_HOME, "https://arena.ai");
                        applyHomeChange();
                    } else {
                        final EditText input = new EditText(this);
                        input.setText(AppSettings.getString(this, AppSettings.KEY_HOME,
                                AppSettings.DEFAULT_HOME));
                        if (AppSettings.isQuickStart(input.getText().toString())) {
                            input.setText("https://");
                        }
                        input.setSingleLine(true);
                        new AlertDialog.Builder(this)
                                .setTitle("Custom start page")
                                .setMessage("Full address, e.g. https://arena.ai/direct")
                                .setView(input)
                                .setPositiveButton("Save", (d2, w2) -> {
                                    String v = input.getText().toString().trim();
                                    if (v.isEmpty() || "https://".equals(v)) {
                                        v = AppSettings.HOME_QUICKSTART;
                                    }
                                    AppSettings.setString(this, AppSettings.KEY_HOME, v);
                                    applyHomeChange();
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void applyHomeChange() {
        Toast.makeText(this, "Start page updated", Toast.LENGTH_SHORT).show();
        finish();
        startActivity(new Intent(this, MainActivity.class)
                .putExtra(MainActivity.EXTRA_LOAD, AppSettings.homeUrl(this)));
    }

    private void pickTextZoom() {
        final String[] opts = {"Small (75%)", "Normal (100%)", "Large (125%)",
                "Extra large (150%)", "Huge (200%)"};
        final int[] vals = {75, 100, 125, 150, 200};
        int cur = 1;
        int now = AppSettings.getInt(this, AppSettings.KEY_TEXT_ZOOM, 100);
        for (int i = 0; i < vals.length; i++) {
            if (vals[i] == now) cur = i;
        }
        new AlertDialog.Builder(this)
                .setTitle("Text size")
                .setSingleChoiceItems(opts, cur, (d, w) -> {
                    AppSettings.setInt(this, AppSettings.KEY_TEXT_ZOOM, vals[w]);
                    d.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmClearData() {
        new AlertDialog.Builder(this)
                .setTitle("Clear browsing data?")
                .setMessage("Cookies, site storage and caches will be removed. "
                        + "You will be signed out of websites.")
                .setPositiveButton("Clear", (d, w) -> {
                    CookieManager.getInstance().removeAllCookies(null);
                    CookieManager.getInstance().flush();
                    WebStorage.getInstance().deleteAllData();
                    Toast.makeText(this, "Browsing data cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void checkNow() {
        Toast.makeText(this, "Checking for updates…", Toast.LENGTH_SHORT).show();
        UpdateChecker.check(this, info -> {
            if (info == null) {
                Toast.makeText(this, "No update server response", Toast.LENGTH_SHORT).show();
            } else if (info.isNewerThan(BuildConfig.VERSION_CODE)) {
                new AlertDialog.Builder(this)
                        .setTitle("Update available: Arena AI " + info.versionName)
                        .setMessage(info.notes == null || info.notes.trim().isEmpty()
                                ? "Download & install now?" : info.notes)
                        .setPositiveButton("Download & install", (d, w) ->
                                updateApk(info.apkUrl))
                        .setNegativeButton("Later", null)
                        .show();
            } else {
                Toast.makeText(this, "You're on the latest version", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateApk(String url) {
        try {
            android.app.DownloadManager dm = (android.app.DownloadManager)
                    getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) return;
            String name = android.webkit.URLUtil.guessFileName(url, null,
                    "application/vnd.android.package-archive");
            android.app.DownloadManager.Request req =
                    new android.app.DownloadManager.Request(Uri.parse(url));
            req.setTitle(name);
            req.setMimeType("application/vnd.android.package-archive");
            req.setNotificationVisibility(
                    android.app.DownloadManager.Request.VISIBILITY_VISIBLE);
            req.setDestinationInExternalPublicDir(
                    android.os.Environment.DIRECTORY_DOWNLOADS, name);
            dm.enqueue(req);
            Toast.makeText(this, "Update downloading… open it from the notification",
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void aboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Arena AI — Mobile Port")
                .setMessage("Unofficial personal client app for arena.ai.\n\n"
                        + "Version " + BuildConfig.VERSION_NAME
                        + " (build " + BuildConfig.VERSION_CODE + ")\n"
                        + "WebView: " + (android.webkit.WebView.getCurrentWebViewPackage() == null
                        ? "system" : android.webkit.WebView.getCurrentWebViewPackage().versionName)
                        + "\n\n• Full browser: downloads, uploads, camera & mic\n"
                        + "• Ad & tracker blocking\n"
                        + "• Desktop site & dark web modes\n"
                        + "• In-app update notifications\n\n"
                        + "arena.ai is a separate service owned by its respective "
                        + "operators; this app is an independent client.")
                .setPositiveButton("OK", null)
                .show();
    }
}
