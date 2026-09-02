package dev.arena.mobile;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebHistoryItem;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Arena AI — Mobile Port.
 *
 * A full-featured WebView browser tuned for arena.ai and the open web:
 * downloads, uploads from gallery/camera, mic & camera for sites, location,
 * notifications, ad/tracker blocking, desktop mode, forced dark mode,
 * fullscreen video, a share target and in-app APK updates.
 */
public class MainActivity extends Activity {

    static final String EXTRA_LOAD = "dev.arena.mobile.EXTRA_LOAD";
    static final String EXTRA_TEXT = "dev.arena.mobile.EXTRA_TEXT";

    private static final int REQ_FILE_CHOOSER = 41001;
    private static final int REQ_PERMS = 41002;
    private static final int REQ_MANAGE_UNKNOWN = 41003;

    private static final String DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    // ---- UI ----
    private FrameLayout outer;
    private LinearLayout root;
    private WebView web;
    private EditText urlBar;
    private View progress;
    private ImageButton btnBack;
    private ImageButton btnForward;
    private FrameLayout customViewHost;

    // ---- transient state ----
    private final Handler ui = new Handler(Looper.getMainLooper());
    private ValueCallback<Uri[]> filePathCallback;
    private boolean cameraCaptureRequested;
    private Uri cameraOutUri;
    private WebChromeClient.CustomViewCallback customViewCb;
    private final Set<Long> apkDownloadIds = new HashSet<>();

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createChannels();
        AdBlocker.init(this);

        outer = new FrameLayout(this);
        outer.setBackgroundColor(Color.parseColor("#0B0F24"));
        setContentView(outer);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        outer.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        buildToolbar();
        buildWebView();
        buildDock();

        // Fullscreen-video overlay sits above everything else.
        customViewHost = new FrameLayout(this);
        customViewHost.setBackgroundColor(Color.BLACK);
        customViewHost.setVisibility(View.GONE);
        outer.addView(customViewHost, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        handleIntent(getIntent());
        if (web.getUrl() == null) {
            loadHome();
        }
        checkForUpdatesSilently();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            openUrl(intent.getDataString());
        } else if (intent.hasExtra(EXTRA_LOAD)) {
            openUrl(intent.getStringExtra(EXTRA_LOAD));
        } else if (Intent.ACTION_SEND.equals(intent.getAction())
                || intent.hasExtra(EXTRA_TEXT)) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text == null) text = intent.getStringExtra(EXTRA_TEXT);
            if (text == null) text = "";
            text = text.trim();
            if (text.startsWith("http://") || text.startsWith("https://")) {
                openUrl(text);
            } else if (!text.isEmpty()) {
                doSearch(text);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) web.onPause();
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(downloadReceiver);
        } catch (Exception ignored) {
        }
        if (web != null) {
            web.loadUrl("about:blank");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            onHideCustomViewSafe();
            return;
        }
        if (web != null && web.canGoBack()) {
            web.goBack();
            return;
        }
        moveTaskToBack(true);
    }

    // ---------------------------------------------------------------- UI helpers

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    private Drawable icon(String name) {
        int id = getResources().getIdentifier(name, "drawable", getPackageName());
        if (id == 0) return null;
        Drawable d = getDrawable(id);
        if (d != null) d.mutate();
        return d;
    }

    private ImageButton navButton(String iconName) {
        ImageButton b = new ImageButton(this);
        b.setImageDrawable(icon(iconName));
        b.setBackgroundResource(R.drawable.bg_icon_ripple);
        b.setColorFilter(Color.parseColor("#C9CEF2"));
        b.setPadding(dp(10), dp(10), dp(10), dp(10));
        b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(46), dp(40)));
        return b;
    }

    // ---------------------------------------------------------------- chrome UI

    private void buildToolbar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Color.parseColor("#0B0F24"));
        bar.setPadding(dp(2), dp(6), dp(2), dp(6));
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        btnBack = navButton("ic_arrow_back");
        btnBack.setOnClickListener(v -> {
            if (web != null && web.canGoBack()) web.goBack();
            else loadHome();
        });
        bar.addView(btnBack);

        btnForward = navButton("ic_arrow_forward");
        btnForward.setOnClickListener(v -> {
            if (web != null && web.canGoForward()) web.goForward();
        });
        bar.addView(btnForward);

        urlBar = new EditText(this);
        urlBar.setBackgroundResource(R.drawable.bg_urlbar);
        urlBar.setTextColor(Color.WHITE);
        urlBar.setHintTextColor(Color.parseColor("#6B739F"));
        urlBar.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        urlBar.setSingleLine(true);
        urlBar.setPadding(dp(14), 0, dp(14), 0);
        urlBar.setSelectAllOnFocus(true);
        urlBar.setInputType(InputType.TYPE_TEXT_VARIATION_URI
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        urlBar.setImeOptions(EditorInfo.IME_ACTION_GO);
        urlBar.setHint("Search or type a site");
        urlBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN)) {
                String q = urlBar.getText().toString().trim();
                if (!q.isEmpty()) navigateFromBar(q);
                return true;
            }
            return false;
        });
        bar.addView(urlBar, new LinearLayout.LayoutParams(0, dp(38), 1f));

        ImageButton menuBtn = navButton("ic_more_vert");
        menuBtn.setOnClickListener(v -> showMenu());
        bar.addView(menuBtn);

        progress = new View(this);
        progress.setBackgroundColor(Color.parseColor("#7C5CFF"));
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(0, dp(2)));
    }

    private void buildDock() {
        LinearLayout dockOuter = new LinearLayout(this);
        dockOuter.setOrientation(LinearLayout.VERTICAL);
        dockOuter.setBackgroundColor(Color.parseColor("#0D1230"));
        root.addView(dockOuter, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        View hairline = new View(this);
        hairline.setBackgroundColor(Color.parseColor("#232B5B"));
        dockOuter.addView(hairline, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));

        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER);
        dockOuter.addView(dock, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(55)));

        addDockButton(dock, "ic_home", "Home", this::loadHome);
        addDockButton(dock, "ic_auto_awesome", "Chat", () -> openUrl("https://arena.ai"));
        addDockButton(dock, "ic_image", "Images", () -> openUrl("https://arena.ai/image"));
        addDockButton(dock, "ic_videocam", "Video", () -> openUrl("https://arena.ai/video"));
        addDockButton(dock, "ic_leaderboard", "Boards",
                () -> openUrl("https://arena.ai/leaderboard"));
        addDockButton(dock, "ic_more_vert", "More", this::showMenu);
    }

    private void addDockButton(LinearLayout dock, String iconName, String label,
                               Runnable action) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setBackgroundResource(R.drawable.bg_icon_ripple);
        cell.setPadding(0, dp(3), 0, dp(1));

        ImageButton b = new ImageButton(this);
        b.setImageDrawable(icon(iconName));
        b.setBackground(null);
        b.setColorFilter(Color.parseColor("#C9CEF2"));
        b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        b.setOnClickListener(v -> action.run());
        cell.addView(b, new LinearLayout.LayoutParams(dp(34), dp(26)));

        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
        t.setTextColor(Color.parseColor("#8B93C4"));
        t.setGravity(Gravity.CENTER);
        cell.addView(t);

        dock.addView(cell, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));
    }

    // ---------------------------------------------------------------- webview setup

    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    private void buildWebView() {
        web = new WebView(this);
        root.addView(web, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setGeolocationEnabled(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setSaveFormData(true);
        applyViewSettings(false);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return shouldRouteExternally(request.getUrl().toString());
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view,
                                                              WebResourceRequest request) {
                return maybeBlock(request.getUrl().toString(), request.isForMainFrame());
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                syncUrlBar(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                syncUrlBar(url);
                maybeInjectCosmeticBlocking(url);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                if (request.isForMainFrame()) {
                    showErrorPage(request.getUrl().toString(), error.getErrorCode());
                }
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                            WebResourceResponse response) {
                if (request.isForMainFrame()
                        && response != null && response.getStatusCode() >= 400
                        && request.getUrl() != null
                        && !request.getUrl().toString().contains("error.html")) {
                    showErrorPage(request.getUrl().toString(), -response.getStatusCode());
                }
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress >= 100 || web == null) {
                    progress.setVisibility(View.GONE);
                } else {
                    progress.setVisibility(View.VISIBLE);
                    ViewGroup.LayoutParams lp = progress.getLayoutParams();
                    lp.width = Math.max(dp(2), (int) (getWidthPx() * newProgress / 100f));
                    progress.setLayoutParams(lp);
                }
            }

            @Override
            public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb,
                                             FileChooserParams params) {
                return launchFileChooser(cb, params);
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                ui.post(() -> new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Allow “" + prettyHost(request.getOrigin().toString()) + "” to use")
                        .setMessage(describeResources(request.getResources()))
                        .setPositiveButton("Allow", (d, i) -> request.grant(request.getResources()))
                        .setNegativeButton("Deny", (d, i) -> request.deny())
                        .setOnCancelListener(d -> request.deny())
                        .show());
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin,
                                                           GeolocationPermissions.Callback callback) {
                if (AppSettings.isGeoAllowed(MainActivity.this, origin)) {
                    callback.invoke(origin, true, false);
                    return;
                }
                ui.post(() -> new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Allow “" + prettyHost(origin) + "” to access your location?")
                        .setPositiveButton("Allow", (d, i) -> {
                            AppSettings.setGeoAllowed(MainActivity.this, origin, true);
                            callback.invoke(origin, true, false);
                        })
                        .setNegativeButton("Block", (d, i) -> callback.invoke(origin, false, false))
                        .setOnCancelListener(d -> callback.invoke(origin, false, false))
                        .show());
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customViewHost.getVisibility() == View.VISIBLE) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customViewCb = callback;
                customViewHost.setVisibility(View.VISIBLE);
                customViewHost.addView(view);
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                urlBar.clearFocus();
            }

            @Override
            public void onHideCustomView() {
                onHideCustomViewSafe();
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture,
                                          android.os.Message resultMsg) {
                // target=_blank / window.open: try to reuse the main view.
                if (web != null) {
                    WebView.HitTestResult htr = web.getHitTestResult();
                    String extra = htr == null ? null : htr.getExtra();
                    if (extra != null && URLUtil.isNetworkUrl(extra)) {
                        openUrl(extra);
                        return false;
                    }
                }
                if (resultMsg == null || resultMsg.obj == null) return false;
                WebView newWv = new WebView(MainActivity.this);
                newWv.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView w, WebResourceRequest r) {
                        openUrl(r.getUrl().toString());
                        return true;
                    }
                });
                WebView.WebViewTransport t = (WebView.WebViewTransport) resultMsg.obj;
                t.setWebView(newWv);
                resultMsg.sendToTarget();
                return true;
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(message == null ? "" : message)
                        .setPositiveButton("OK", (d, w) -> result.confirm())
                        .setOnCancelListener(d -> result.cancel())
                        .show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(message == null ? "" : message)
                        .setPositiveButton("OK", (d, w) -> result.confirm())
                        .setNegativeButton("Cancel", (d, w) -> result.cancel())
                        .show();
                return true;
            }

            @Override
            public boolean onJsPrompt(WebView view, String url, String message,
                                      String defaultValue, JsPromptResult result) {
                EditText input = new EditText(MainActivity.this);
                input.setText(defaultValue == null ? "" : defaultValue);
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(message == null ? "" : message)
                        .setView(input)
                        .setPositiveButton("OK",
                                (d, w) -> result.confirm(input.getText().toString()))
                        .setNegativeButton("Cancel", (d, w) -> result.cancel())
                        .show();
                return true;
            }
        });

        web.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            if (url != null && url.startsWith("data:")) {
                saveDataUrl(url, mimeType);
            } else {
                enqueueDownload(url, userAgent, contentDisposition, mimeType);
            }
        });

        web.setOnLongClickListener(v -> {
            WebView.HitTestResult htr = web.getHitTestResult();
            if (htr == null) return false;
            int type = htr.getType();
            if (type == WebView.HitTestResult.IMAGE_TYPE
                    || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                String imgUrl = htr.getExtra();
                if (imgUrl == null) return false;
                showImageActions(imgUrl);
                return true;
            }
            return false;
        });

        web.addJavascriptInterface(new NativeBridge(), "ArenaBridge");

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= 21) {
            cm.setAcceptThirdPartyCookies(web, true);
        }

        registerReceiver(downloadReceiver,
                new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    private View customView;

    private void onHideCustomViewSafe() {
        if (customViewHost.getVisibility() != View.VISIBLE) return;
        customViewHost.removeAllViews();
        customViewHost.setVisibility(View.GONE);
        customView = null;
        if (customViewCb != null) {
            try {
                customViewCb.onCustomViewHidden();
            } catch (Exception ignored) {
            }
            customViewCb = null;
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private int getWidthPx() {
        return getResources().getDisplayMetrics().widthPixels;
    }

    private boolean shouldRouteExternally(String url) {
        if (url == null) return false;
        String l = url.toLowerCase(Locale.US);
        if (l.startsWith("http://") || l.startsWith("https://")
                || l.startsWith("file://") || l.startsWith("about:")
                || l.startsWith("data:") || l.startsWith("blob:")
                || l.startsWith("javascript:") || l.startsWith("ws://")
                || l.startsWith("wss://")) {
            return false; // stay in the app
        }
        // tel:, mailto:, intents, custom schemes -> OS
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "No app can handle this link", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    private WebResourceResponse maybeBlock(String url, boolean forMainFrame) {
        if (forMainFrame || url == null) return null;
        if (!AppSettings.getBool(this, AppSettings.KEY_ADBLOCK, true)) return null;
        String host = Uri.parse(url).getHost();
        if (host == null) return null;
        // never block first-party requests
        String pageHost = web == null || web.getUrl() == null
                ? null : Uri.parse(web.getUrl()).getHost();
        if (host.equalsIgnoreCase(pageHost)) return null;
        if (host.endsWith("." + pageHost)) return null;
        if (AppSettings.isAdsEnabledFor(this, host)) return null;
        if (AdBlocker.isBlocked(url)) {
            return new WebResourceResponse("text/html", "utf-8",
                    new ByteArrayInputStream(new byte[0]));
        }
        return null;
    }

    private void maybeInjectCosmeticBlocking(String url) {
        if (url == null) return;
        if (!AppSettings.getBool(this, AppSettings.KEY_ADBLOCK, true)) return;
        if (!(url.startsWith("http://") || url.startsWith("https://"))) return;
        String host = Uri.parse(url).getHost();
        if (host != null && AppSettings.isAdsEnabledFor(this, host)) return;
        if (host != null && AdBlocker.isBlocked(url)) return; // whole page family blocked
        String js = AdBlocker.cosmeticJs(this);
        if (js.isEmpty()) return;
        web.evaluateJavascript(js, null);
    }

    private void showErrorPage(String failedUrl, int code) {
        if (web == null) return;
        String msg = "Couldn't load this page";
        int c = Math.abs(code);
        if (c == 2 || c == 6 || c == 7 || c == 8 || c == 100 || c == 101
                || c == 102 || c == 106 || c == 110) {
            msg = "No connection to the internet";
        }
        String page = "file:///android_asset/www/error.html?u="
                + Uri.encode(failedUrl == null ? "" : failedUrl)
                + "&m=" + Uri.encode(msg) + "&c=" + code;
        web.loadUrl(page);
    }

    private void applyViewSettings(boolean reload) {
        if (web == null) return;
        WebSettings s = web.getSettings();
        boolean desktop = AppSettings.getBool(this, AppSettings.KEY_DESKTOP, false);
        if (desktop) {
            s.setUserAgentString(DESKTOP_UA);
        } else {
            String cur = s.getUserAgentString();
            if (cur == null || cur.contains("Windows NT")) {
                s.setUserAgentString(WebSettings.getDefaultUserAgent(this)
                        + " ArenaMobile/" + BuildConfig.VERSION_NAME);
            }
        }
        boolean dark = AppSettings.getBool(this, AppSettings.KEY_FORCE_DARK, false);
        if (Build.VERSION.SDK_INT >= 29) {
            s.setForceDark(dark ? WebSettings.FORCE_DARK_ON : WebSettings.FORCE_DARK_OFF);
        }
        if (Build.VERSION.SDK_INT >= 33) {
            s.setAlgorithmicDarkeningAllowed(true);
        }
        s.setTextZoom(AppSettings.getInt(this, AppSettings.KEY_TEXT_ZOOM, 100));
        if (reload && web.getUrl() != null
                && (web.getUrl().startsWith("http://")
                || web.getUrl().startsWith("https://"))) {
            web.reload();
        }
    }

    private static String prettyHost(String origin) {
        try {
            Uri u = Uri.parse(origin);
            String h = u.getHost();
            return h == null ? origin : h;
        } catch (Exception e) {
            return origin;
        }
    }

    private String describeResources(String[] resources) {
        StringBuilder sb = new StringBuilder();
        if (resources != null) {
            for (String r : resources) {
                if (sb.length() > 0) sb.append(" & ");
                if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) sb.append("the camera");
                else if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) sb.append("the microphone");
                else if (PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID.equals(r)) sb.append("protected media");
                else if (PermissionRequest.RESOURCE_MIDI_SYSEX.equals(r)) sb.append("MIDI");
                else sb.append(r.replace("android.webkit.resource.", ""));
            }
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- navigation

    private void loadHome() {
        openUrl(AppSettings.homeUrl(this));
    }

    void openUrl(String url) {
        if (url == null || url.isEmpty() || web == null) return;
        String u = url.trim();
        if (!URLUtil.isNetworkUrl(u) && !u.startsWith("file://")) {
            u = "https://" + u;
        }
        web.loadUrl(u);
    }

    private void navigateFromBar(String input) {
        if (looksLikeUrl(input)) {
            openUrl(input.contains("://") ? input : "https://" + input);
        } else {
            doSearch(input);
        }
    }

    private boolean looksLikeUrl(String q) {
        String l = q.toLowerCase(Locale.US);
        if (l.contains("://")) return true;
        if (l.contains(" ") || !l.contains(".")) return false;
        return l.matches("^(localhost|(\\d{1,3}\\.){3}\\d{1,3}|[a-z0-9-]+(\\.[a-z0-9-]+)+)"
                + "(:\\d+)?(/.*)?$");
    }

    void doSearch(String query) {
        String engine = AppSettings.getString(this, AppSettings.KEY_SEARCH, "google");
        String q = Uri.encode(query);
        String url;
        switch (engine) {
            case "duckduckgo":
                url = "https://duckduckgo.com/?q=" + q;
                break;
            case "bing":
                url = "https://www.bing.com/search?q=" + q;
                break;
            case "startpage":
                url = "https://www.startpage.com/sp/search?query=" + q;
                break;
            case "yahoo":
                url = "https://search.yahoo.com/search?p=" + q;
                break;
            case "google":
            default:
                url = "https://www.google.com/search?q=" + q;
                break;
        }
        openUrl(url);
    }

    private void syncUrlBar(String url) {
        if (urlBar == null) return;
        if (url != null && url.startsWith("file:///android_asset/www/index.html")) {
            urlBar.setText("Arena AI — quick start");
            urlBar.setTextColor(Color.parseColor("#8B93C4"));
        } else if (url != null && url.contains("error.html")) {
            urlBar.setText("Page couldn't load");
            urlBar.setTextColor(Color.parseColor("#8B93C4"));
        } else {
            urlBar.setTextColor(Color.WHITE);
            urlBar.setText(url == null ? "" : url);
            urlBar.setSelection(url == null ? 0 : url.length());
        }
        boolean canBack = web != null && web.canGoBack();
        boolean canFwd = web != null && web.canGoForward();
        btnBack.setColorFilter(canBack ? Color.parseColor("#E8EAFF")
                : Color.parseColor("#4A517A"));
        btnForward.setColorFilter(canFwd ? Color.parseColor("#E8EAFF")
                : Color.parseColor("#4A517A"));
    }

    // ---------------------------------------------------------------- file chooser

    private boolean launchFileChooser(final ValueCallback<Uri[]> cb,
                                      final WebChromeClient.FileChooserParams params) {
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
        }
        filePathCallback = cb;

        boolean capture = false;
        try {
            capture = params != null && params.isCaptureEnabled();
        } catch (Exception ignored) {
        }
        if (capture && !hasPermission(Manifest.permission.CAMERA)) {
            cameraCaptureRequested = true;
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_PERMS);
            return true;
        }
        Intent choose = pickerIntentFor(params);
        if (choose == null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
            return false;
        }
        startActivityForResult(choose, REQ_FILE_CHOOSER);
        return true;
    }

    private Intent pickerIntentFor(WebChromeClient.FileChooserParams params) {
        String[] accept = params == null ? null : params.getAcceptTypes();
        boolean capture = false;
        try {
            capture = params != null && params.isCaptureEnabled();
        } catch (Exception ignored) {
        }
        boolean videoOnly = false;
        boolean imageOnly = false;
        boolean generic = accept == null || accept.length == 0;
        if (accept != null) {
            for (String a : accept) {
                if (a == null) continue;
                String t = a.toLowerCase(Locale.US);
                if (t.contains("video")) videoOnly = true;
                if (t.contains("image")) imageOnly = true;
                if (t.contains("audio")) generic = true;
            }
        }
        boolean multi = params != null
                && params.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE;

        if (capture && imageOnly) {
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cameraOutUri = createCameraUri("capture.jpg");
            if (cameraOutUri != null) {
                i.putExtra(MediaStore.EXTRA_OUTPUT, cameraOutUri);
                i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                if (i.resolveActivity(getPackageManager()) != null) return i;
            }
        }
        if (capture && videoOnly) {
            Intent i = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
            cameraOutUri = createCameraUri("capture.mp4");
            if (cameraOutUri != null) {
                i.putExtra(MediaStore.EXTRA_OUTPUT, cameraOutUri);
                i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                if (i.resolveActivity(getPackageManager()) != null) return i;
            }
        }

        Intent base = new Intent(Intent.ACTION_GET_CONTENT);
        base.addCategory(Intent.CATEGORY_OPENABLE);
        String mime = pickMime(accept);
        if (multi || generic) base.setType("*/*");
        else base.setType(mime);
        if (multi) base.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        String title = params == null || params.getTitle() == null
                || params.getTitle().toString().isEmpty()
                ? "Choose a file" : params.getTitle().toString();
        Intent chooser = Intent.createChooser(base, title);

        if (!capture) {
            List<Intent> initial = new ArrayList<>();
            if (imageOnly || generic) {
                Intent cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                cameraOutUri = createCameraUri("capture.jpg");
                if (cameraOutUri != null) {
                    cam.putExtra(MediaStore.EXTRA_OUTPUT, cameraOutUri);
                    cam.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    if (cam.resolveActivity(getPackageManager()) != null) initial.add(cam);
                }
            }
            if (videoOnly || generic) {
                Intent vid = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
                cameraOutUri = createCameraUri("capture.mp4");
                if (cameraOutUri != null) {
                    vid.putExtra(MediaStore.EXTRA_OUTPUT, cameraOutUri);
                    vid.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    if (vid.resolveActivity(getPackageManager()) != null) initial.add(vid);
                }
            }
            if (!initial.isEmpty()) {
                chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS,
                        initial.toArray(new Intent[0]));
            }
        }
        return chooser;
    }

    private String pickMime(String[] accept) {
        if (accept != null) {
            for (String a : accept) {
                if (a != null && !a.isEmpty() && !a.equals("*/*")) return a;
            }
        }
        return "*/*";
    }

    private Uri createCameraUri(String name) {
        try {
            File dir = new File(getCacheDir(), "camera");
            if (!dir.exists() && !dir.mkdirs()) return null;
            File f = new File(dir, System.currentTimeMillis() + "_" + name);
            return androidx.core.content.FileProvider.getUriForFile(
                    this, "dev.arena.mobile.files", f);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean hasPermission(String p) {
        return checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
    }

    private void ensureNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33 && !hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_PERMS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMS && cameraCaptureRequested) {
            cameraCaptureRequested = false;
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted && filePathCallback != null) {
                Intent i = pickerIntentFor(null);
                if (i != null) {
                    startActivityForResult(i, REQ_FILE_CHOOSER);
                    return;
                }
            }
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(null);
                filePathCallback = null;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE_CHOOSER) {
            Uri[] results = null;
            if (resultCode == RESULT_OK) {
                if (data != null && data.getClipData() != null) {
                    ClipData cd = data.getClipData();
                    results = new Uri[cd.getItemCount()];
                    for (int i = 0; i < cd.getItemCount(); i++) {
                        results[i] = cd.getItemAt(i).getUri();
                    }
                } else if (data != null && data.getData() != null) {
                    results = new Uri[]{data.getData()};
                } else if (cameraOutUri != null) {
                    results = new Uri[]{cameraOutUri};
                }
            }
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            }
            cameraOutUri = null;
        } else if (requestCode == REQ_MANAGE_UNKNOWN) {
            if (Build.VERSION.SDK_INT >= 26 && getPackageManager().canRequestPackageInstalls()) {
                File f = new File(getCacheDir(), "update.apk");
                if (f.exists()) installApkFromFile(f);
            }
        }
    }

    // ---------------------------------------------------------------- downloads

    /** Open the system Downloads app/folder (used by menu & start page). */
    void openDownloads() {
        try {
            Intent i = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(
                        "content://com.android.providers.downloads.documents/root/downloads"));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (Exception e2) {
                Toast.makeText(this, "Downloads folder unavailable", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void enqueueDownload(String url, String ua, String contentDisposition,
                                 String mimeType) {
        ensureNotifPermission();
        String name = fileNameFrom(contentDisposition, url, mimeType);
        try {
            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) return;
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.setTitle(name);
            req.setDescription("Downloading…");
            req.setMimeType(mimeType == null ? "application/octet-stream" : mimeType);
            req.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
            if (ua != null) req.addRequestHeader("User-Agent", ua);
            if (web != null && web.getUrl() != null) {
                req.addRequestHeader("Referer", web.getUrl());
            }
            dm.enqueue(req);
            Toast.makeText(this, "Downloading “" + name + "”…", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Download failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String fileNameFrom(String contentDisposition, String url, String mime) {
        String name = null;
        if (contentDisposition != null) {
            int fi = contentDisposition.toLowerCase(Locale.US).indexOf("filename=");
            if (fi >= 0) {
                String rest = contentDisposition.substring(fi + 9).trim()
                        .replaceAll("^[\"']|[\"']$", "");
                int semi = rest.indexOf(';');
                if (semi > 0) rest = rest.substring(0, semi);
                if (!rest.isEmpty()) name = rest.trim();
            }
        }
        if (name == null) name = URLUtil.guessFileName(url, contentDisposition, mime);
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (name.length() > 100) name = name.substring(name.length() - 100);
        if (name.isEmpty()) name = "download-" + System.currentTimeMillis();
        return name;
    }

    private void saveDataUrl(String dataUrl, String mimeType) {
        ensureNotifPermission();
        String mime = mimeType == null ? "application/octet-stream" : mimeType;
        String ext;
        if (mime.contains("png")) ext = ".png";
        else if (mime.contains("webp")) ext = ".webp";
        else if (mime.contains("gif")) ext = ".gif";
        else if (mime.contains("jpeg")) ext = ".jpg";
        else if (mime.contains("pdf")) ext = ".pdf";
        else if (mime.contains("mp4")) ext = ".mp4";
        else if (mime.contains("svg")) ext = ".svg";
        else ext = ".bin";
        final String name = "download-" + System.currentTimeMillis() + ext;
        try {
            String b64 = dataUrl.substring(dataUrl.indexOf(',') + 1);
            byte[] bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
            if (Build.VERSION.SDK_INT >= 29) {
                ContentResolver cr = getContentResolver();
                android.content.ContentValues v = new android.content.ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                v.put(MediaStore.Downloads.MIME_TYPE, mime);
                v.put(MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/Arena AI");
                v.put(MediaStore.Downloads.IS_PENDING, 1);
                Uri item = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (item != null) {
                    try (OutputStream out = cr.openOutputStream(item)) {
                        out.write(bytes);
                    }
                    v.clear();
                    v.put(MediaStore.Downloads.IS_PENDING, 0);
                    cr.update(item, v, null, null);
                    Toast.makeText(this, "Saved to Downloads/Arena AI", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (dir == null) dir = getCacheDir();
            File f = new File(dir, name);
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(bytes);
            }
            Toast.makeText(this, "Saved to " + f.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (id <= 0) return;
            if (apkDownloadIds.remove(id)) {
                DownloadManager dm = (DownloadManager) context
                        .getSystemService(Context.DOWNLOAD_SERVICE);
                if (dm == null) return;
                Uri uri = dm.getUriForDownloadedFile(id);
                if (uri != null) installApkFromUri(uri);
            }
        }
    };

    // ---------------------------------------------------------------- long-press

    private void showImageActions(final String imageUrl) {
        final String[] items = {"Save image", "Share image", "Open image", "Copy address"};
        new AlertDialog.Builder(this)
                .setTitle(imageUrl.length() > 70 ? imageUrl.substring(0, 70) + "…" : imageUrl)
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0: {
                            String ua = web == null || web.getSettings() == null
                                    ? "" : web.getSettings().getUserAgentString();
                            MediaSaver.saveImage(this, imageUrl, ua,
                                    (ok, msg) -> Toast.makeText(this, msg,
                                            Toast.LENGTH_LONG).show());
                            break;
                        }
                        case 1: {
                            Intent i = new Intent(Intent.ACTION_SEND);
                            i.setType("text/plain");
                            i.putExtra(Intent.EXTRA_TEXT, imageUrl);
                            startActivity(Intent.createChooser(i, "Share image"));
                            break;
                        }
                        case 2:
                            openUrl(imageUrl);
                            break;
                        case 3: {
                            ClipboardManager cm = (ClipboardManager)
                                    getSystemService(Context.CLIPBOARD_SERVICE);
                            cm.setPrimaryClip(ClipData.newPlainText("image", imageUrl));
                            Toast.makeText(this, "Address copied", Toast.LENGTH_SHORT).show();
                            break;
                        }
                    }
                })
                .show();
    }

    // ---------------------------------------------------------------- notifications

    private void createChannels() {
        NotificationManager nm = (NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        nm.createNotificationChannel(new NotificationChannel("downloads",
                getString(R.string.notif_channel_downloads),
                NotificationManager.IMPORTANCE_LOW));
        nm.createNotificationChannel(new NotificationChannel("updates",
                getString(R.string.notif_channel_updates),
                NotificationManager.IMPORTANCE_HIGH));
        nm.createNotificationChannel(new NotificationChannel("errors",
                getString(R.string.notif_channel_errors),
                NotificationManager.IMPORTANCE_DEFAULT));
    }

    private void notifyUpdate(final UpdateChecker.UpdateInfo info) {
        ensureNotifPermission();
        NotificationManager nm = (NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 7, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, "updates")
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("Arena AI " + info.versionName + " is available")
                .setContentText("Tap to get the update")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        nm.notify(42, n);
        AppSettings.setLong(this, AppSettings.KEY_LAST_UPDATE_CHECK,
                System.currentTimeMillis());
    }

    private void checkForUpdatesSilently() {
        boolean auto = AppSettings.getBool(this, AppSettings.KEY_AUTO_UPDATE, true);
        if (!auto) return;
        long last = AppSettings.getLong(this, AppSettings.KEY_LAST_UPDATE_CHECK, 0);
        if (System.currentTimeMillis() - last < 24 * 3600 * 1000L) return;
        UpdateChecker.check(this, info -> {
            if (info != null && info.isNewerThan(BuildConfig.VERSION_CODE)) {
                notifyUpdate(info);
            } else {
                AppSettings.setLong(this, AppSettings.KEY_LAST_UPDATE_CHECK,
                        System.currentTimeMillis());
            }
        });
    }

    // ---------------------------------------------------------------- menu

    private void showMenu() {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView sc = new ScrollView(this);
        sc.addView(list);

        list.addView(menuTitle("VIEW"));
        list.addView(menuToggle("ic_desktop", "Desktop site",
                AppSettings.getBool(this, AppSettings.KEY_DESKTOP, false), (row, on) -> {
                    AppSettings.setBool(this, AppSettings.KEY_DESKTOP, on);
                    applyViewSettings(true);
                }));
        list.addView(menuToggle("ic_dark", "Dark web content",
                AppSettings.getBool(this, AppSettings.KEY_FORCE_DARK, false), (row, on) -> {
                    AppSettings.setBool(this, AppSettings.KEY_FORCE_DARK, on);
                    applyViewSettings(true);
                }));

        list.addView(menuDivider());
        list.addView(menuTitle("PRIVACY"));
        list.addView(menuToggle("ic_shield", "Block ads & trackers",
                AppSettings.getBool(this, AppSettings.KEY_ADBLOCK, true), (row, on) -> {
                    AppSettings.setBool(this, AppSettings.KEY_ADBLOCK, on);
                    if (web != null && web.getUrl() != null
                            && URLUtil.isNetworkUrl(web.getUrl())) web.reload();
                }));

        String host = web == null || web.getUrl() == null
                ? null : Uri.parse(web.getUrl()).getHost();
        if (host != null) {
            list.addView(menuToggle("ic_block_off", "Allow ads on “" + host + "”",
                    AppSettings.isAdsEnabledFor(this, host), (row, on) -> {
                        AppSettings.setAdsEnabledFor(this, host, on);
                        if (web != null && web.getUrl() != null
                                && URLUtil.isNetworkUrl(web.getUrl())) web.reload();
                    }));
        }

        list.addView(menuDivider());
        list.addView(menuTitle("PAGE"));
        list.addView(menuRow("ic_find", "Find on page", this::findOnPage));
        list.addView(menuRow("ic_history", "Pages in this session", this::showHistory));
        list.addView(menuRow("ic_share", "Share this page", this::shareCurrent));
        list.addView(menuRow("ic_download", "Save page (HTML)", this::saveCurrentPage));
        list.addView(menuRow("ic_open_downloads", "Open Downloads folder",
                this::openDownloads));

        list.addView(menuDivider());
        list.addView(menuTitle("APP"));
        list.addView(menuRow("ic_settings", "Settings", () ->
                startActivity(new Intent(MainActivity.this, SettingsActivity.class))));
        list.addView(menuRow("ic_refresh", "Check for updates", this::checkForUpdatesManually));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Menu")
                .setView(sc)
                .setNegativeButton("Close", null)
                .create();

        applyDialogRows(list, dialog);
        dialog.show();
    }

    /** Wire rows so tapping anywhere dismisses the dialog, switches don't. */
    private void applyDialogRows(LinearLayout list, final AlertDialog dialog) {
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            if (child.getTag() != null && "switch".equals(child.getTag())) continue;
            child.setOnClickListener(v -> {
                dialog.dismiss();
                Object tag = child.getTag();
                if (tag instanceof Runnable) ((Runnable) tag).run();
            });
        }
    }

    private TextView menuTitle(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tv.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
        tv.setLetterSpacing(0.08f);
        tv.setTextColor(Color.parseColor("#8B93C4"));
        tv.setPadding(dp(20), dp(14), dp(20), dp(2));
        return tv;
    }

    private View menuDivider() {
        View v = new View(this);
        v.setBackgroundColor(Color.parseColor("#1E2550"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lp.topMargin = dp(6);
        lp.bottomMargin = dp(6);
        v.setLayoutParams(lp);
        return v;
    }

    private View menuRow(String icon, String label, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(8), dp(20), dp(8));
        row.setBackgroundResource(R.drawable.bg_icon_ripple);
        row.setTag(action);
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(icon(icon));
        iv.setColorFilter(Color.parseColor("#C9CEF2"));
        row.addView(iv, new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setTextColor(Color.WHITE);
        tv.setPadding(dp(16), 0, 0, 0);
        row.addView(tv);
        return row;
    }

    private View menuToggle(String icon, String label, boolean initial,
                            final android.widget.CompoundButton.OnCheckedChangeListener action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(4), dp(20), dp(4));
        row.setTag("switch");
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(icon(icon));
        iv.setColorFilter(Color.parseColor("#C9CEF2"));
        row.addView(iv, new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setTextColor(Color.WHITE);
        tv.setPadding(dp(16), 0, dp(8), 0);
        row.addView(tv, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final Switch sw = new Switch(this);
        sw.setChecked(initial);
        sw.setOnCheckedChangeListener(action);
        row.addView(sw);
        return row;
    }

    private void findOnPage() {
        final EditText input = new EditText(this);
        input.setHint("Find on page");
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("Find on page")
                .setView(input)
                .setPositiveButton("Find", (d, w) -> {
                    String q = input.getText().toString();
                    if (!q.isEmpty()) {
                        web.findAllAsync(q);
                        web.findNext(true);
                    }
                })
                .setNeutralButton("Next", (d, w) -> web.findNext(true))
                .setNegativeButton("Done", (d, w) -> {
                    web.clearMatches();
                    d.dismiss();
                })
                .create();
        dlg.show();
    }

    private void showHistory() {
        if (web == null) return;
        WebBackForwardList list = web.copyBackForwardList();
        final int size = list.getSize();
        final int current = list.getCurrentIndex();
        String[] items = new String[size];
        for (int i = 0; i < size; i++) {
            WebHistoryItem item = list.getItemAtIndex(i);
            String t = item.getTitle();
            if (t == null || t.isEmpty()) t = item.getUrl();
            String marker = i == current ? "● " : "   ";
            items[i] = marker + t + "\n      " + item.getUrl();
        }
        new AlertDialog.Builder(this)
                .setTitle("Pages in this session")
                .setItems(items, (d, which) -> {
                    int step = which - current;
                    if (step != 0) web.goBackOrForward(step);
                })
                .setNeutralButton("Clear", (d, w) -> {
                    web.clearHistory();
                    Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void shareCurrent() {
        String url = web.getUrl();
        String title = web.getTitle();
        if (url == null) url = AppSettings.homeUrl(this);
        if (title == null || title.isEmpty()) title = url;
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, title);
        i.putExtra(Intent.EXTRA_TEXT, title + "\n" + url);
        startActivity(Intent.createChooser(i, "Share via"));
    }

    private void saveCurrentPage() {
        String url = web.getUrl();
        if (url == null || !URLUtil.isNetworkUrl(url)) return;
        String title = web.getTitle();
        String name = (title == null || title.isEmpty() ? "page" : title)
                .replaceAll("[^a-zA-Z0-9 _-]", "").trim();
        if (name.isEmpty()) name = "page";
        name = name + ".html";
        try {
            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) return;
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.setTitle(name);
            req.setDescription("Saving page HTML…");
            req.setMimeType("text/html");
            req.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
            if (web.getSettings() != null && web.getSettings().getUserAgentString() != null) {
                req.addRequestHeader("User-Agent", web.getSettings().getUserAgentString());
            }
            dm.enqueue(req);
            Toast.makeText(this, "Saving page HTML to Downloads…", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void checkForUpdatesManually() {
        UpdateChecker.check(this, info -> {
            if (info == null) {
                Toast.makeText(this, "No update server response",
                        Toast.LENGTH_SHORT).show();
            } else if (info.isNewerThan(BuildConfig.VERSION_CODE)) {
                new AlertDialog.Builder(this)
                        .setTitle("Update available: Arena AI " + info.versionName)
                        .setMessage(info.notes == null || info.notes.trim().isEmpty()
                                ? "A new version is available. Download & install it?"
                                : info.notes)
                        .setPositiveButton("Download & install", (d, w) ->
                                downloadAndInstallApk(info.apkUrl))
                        .setNegativeButton("Later", null)
                        .show();
            } else {
                Toast.makeText(this, "You're on the latest version", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ---------------------------------------------------------------- update install

    private void downloadAndInstallApk(final String apkUrl) {
        ensureNotifPermission();
        try {
            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) return;
            String name = URLUtil.guessFileName(apkUrl, null,
                    "application/vnd.android.package-archive");
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(apkUrl));
            req.setTitle(name);
            req.setDescription("Downloading Arena AI update…");
            req.setMimeType("application/vnd.android.package-archive");
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
            req.addRequestHeader("User-Agent", "ArenaMobile/" + BuildConfig.VERSION_NAME);
            apkDownloadIds.add(dm.enqueue(req));
            Toast.makeText(this, "Update downloading…", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void installApkFromUri(Uri apkUri) {
        try {
            File out = new File(getCacheDir(), "update.apk");
            try (InputStream in = getContentResolver().openInputStream(apkUri);
                 OutputStream os = new FileOutputStream(out)) {
                if (in == null) return;
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            }
            installApkFromFile(out);
        } catch (Exception e) {
            Toast.makeText(this, "Install failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void installApkFromFile(File apkFile) {
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(this)
                    .setTitle("Allow installing updates?")
                    .setMessage("Arena AI needs permission to install APK updates "
                            + "from its own downloads.")
                    .setPositiveButton("Settings", (d, w) -> {
                        Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + getPackageName()));
                        startActivityForResult(i, REQ_MANAGE_UNKNOWN);
                    })
                    .setNegativeButton("Not now", null)
                    .show();
            return;
        }
        try {
            Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    this, "dev.arena.mobile.files", apkFile);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Install failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ---------------------------------------------------------------- JS bridge

    /** Exposed to web content as window.ArenaBridge (used by the built-in start page). */
    @SuppressLint("unused")
    private class NativeBridge {
        @JavascriptInterface
        public String getPrefs() {
            return AppSettings.toJson(MainActivity.this);
        }

        @JavascriptInterface
        public void setPref(String key, String value) {
            if (key == null || value == null) return;
            switch (key) {
                case "home":
                    AppSettings.setString(MainActivity.this, AppSettings.KEY_HOME, value);
                    break;
                case "search":
                    AppSettings.setString(MainActivity.this, AppSettings.KEY_SEARCH, value);
                    break;
                case "dark":
                    AppSettings.setBool(MainActivity.this, AppSettings.KEY_FORCE_DARK,
                            Boolean.parseBoolean(value));
                    break;
                case "adblock":
                    AppSettings.setBool(MainActivity.this, AppSettings.KEY_ADBLOCK,
                            Boolean.parseBoolean(value));
                    break;
                case "desktop":
                    AppSettings.setBool(MainActivity.this, AppSettings.KEY_DESKTOP,
                            Boolean.parseBoolean(value));
                    break;
                default:
                    return;
            }
            ui.post(() -> {
                applyViewSettings(false);
                String json = AppSettings.toJson(MainActivity.this);
                web.evaluateJavascript(
                        "try{window.dispatchEvent(new CustomEvent('arena-prefs',"
                                + " {detail:" + json + "}))}catch(e){}", null);
            });
        }

        @JavascriptInterface
        public void openUrl(String url) {
            ui.post(() -> openUrl(url));
        }

        @JavascriptInterface
        public void doSearch(String q) {
            ui.post(() -> doSearch(q == null ? "" : q));
        }

        @JavascriptInterface
        public void goHome() {
            ui.post(MainActivity.this::loadHome);
        }

        @JavascriptInterface
        public void share(String text) {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, text == null ? "" : text);
            startActivity(Intent.createChooser(i, "Share via"));
        }

        @JavascriptInterface
        public void openSettings() {
            ui.post(() -> startActivity(new Intent(MainActivity.this, SettingsActivity.class)));
        }

        @JavascriptInterface
        public void openDownloads() {
            ui.post(MainActivity.this::openDownloads);
        }

        @JavascriptInterface
        public void toast(String message) {
            Toast.makeText(MainActivity.this, message == null ? "" : message,
                    Toast.LENGTH_SHORT).show();
        }
    }
}
