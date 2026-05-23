package com.w3coach.w3coachtv;

import android.annotation.SuppressLint;
import java.io.File;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.content.ComponentName;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    // ── Konstanten ────────────────────────────────────────────────────────────
    private static final String SETTINGS_URL    = "about:blank"; // wird aus Prefs überschrieben
    private static final int    LONG_PRESS_MS   = 600;
    private static final int    ZOOM_BASE       = 75;
    private static final int    ZOOM_STEP       = 5;

    // ── Felder ────────────────────────────────────────────────────────────────
    private WebView  webView;
    private Prefs    prefs;
    private TvMenu             tvMenu;
    private PermissionManager  permissionManager;

    private String   url1, url2, url3;
    private int      currentUrl = 1;

    private final Handler longPressHandler  = new Handler();
    private       boolean longPressTriggered = false;

    private final Handler networkRetryHandler = new Handler();
    private static final int NETWORK_RETRY_MS = 3000;
    private android.widget.FrameLayout rootLayout;
    private android.widget.LinearLayout noNetworkView;
    private android.net.ConnectivityManager.NetworkCallback networkCallback;
    private boolean urlLoaded = false;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Bildschirm dauerhaft an
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Vollbild
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        prefs  = new Prefs(this);
        tvMenu = new TvMenu(this);

        // Root Layout: WebView + NoNetwork-Overlay
        rootLayout = new android.widget.FrameLayout(this);
        webView = new WebView(this);
        rootLayout.addView(webView, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));

        // "Kein Netzwerk" Overlay
        noNetworkView = buildNoNetworkView();
        rootLayout.addView(noNetworkView, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));

        setContentView(rootLayout);

        setupWebView();
        setupBackHandler();
        initKioskMode();

        // Permissions beim Erststart
        permissionManager = new PermissionManager(this, this::onPermissionsGranted);
        permissionManager.checkAndRequest();

    }

    @Override
    protected void onResume() {
        super.onResume();
        // Immersive Mode wiederherstellen
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        AutoUpdateJob.schedule(this);
        // Screensaver bei jedem Resume deaktivieren
        disableScreensaver();
        registerNetworkCallback();
        // Permissions nach Rückkehr von System-Settings neu prüfen
        if (permissionManager != null) permissionManager.checkAndRequest();
    }

    // ── WebView Setup ─────────────────────────────────────────────────────────

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // VPN-Berechtigung Ergebnis an WireGuardManager weitergeben
        if (requestCode == 1001) {
            new WireGuardManager(this).onVpnPermissionResult(resultCode);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Nach Laufzeit-Permission erneut prüfen
        if (permissionManager != null) permissionManager.checkAndRequest();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        applyZoom(prefs.zoom());

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                injectDpadSupport(view);
            }
        });
    }

    private void disableScreensaver() {
        try {
            // Ambient-Modus und Daydream deaktivieren
            android.provider.Settings.Secure.putString(getContentResolver(),
                    "screensaver_enabled", "0");
            android.provider.Settings.Secure.putString(getContentResolver(),
                    "screensaver_activate_on_dock", "0");
            android.provider.Settings.Secure.putString(getContentResolver(),
                    "screensaver_activate_on_sleep", "0");
        } catch (Exception e) {
            android.util.Log.w("MainActivity", "Screensaver konnte nicht deaktiviert werden: " + e.getMessage());
        }
    }


    private void disableSetupWizard(DevicePolicyManager dpm, ComponentName admin) {
        String[] setupPackages = {
            "com.google.android.tungsten.setupwraith",
            "com.google.android.partnersetup",
            "com.google.android.chromecast.setupcustomization",
            // Screensaver / Ambient Mode
            "com.google.android.apps.tv.dreamx",
            "com.google.android.backdrop"
        };
        for (String pkg : setupPackages) {
            try {
                dpm.setApplicationHidden(admin, pkg, true);
            } catch (Exception ignored) {}
        }
    }


    private void injectDpadSupport(WebView view) {
        view.evaluateJavascript(
            "(function(){" +
            "  if(window.__dpad)return; window.__dpad=true;" +
            "  document.addEventListener('keydown',function(e){" +
            "    var s=150;" +
            "    if(e.key==='ArrowDown') window.scrollBy({top:s,behavior:'smooth'});" +
            "    else if(e.key==='ArrowUp') window.scrollBy({top:-s,behavior:'smooth'});" +
            "    else if(e.key==='ArrowRight') window.scrollBy({left:s,behavior:'smooth'});" +
            "    else if(e.key==='ArrowLeft') window.scrollBy({left:-s,behavior:'smooth'});" +
            "  });" +
            "})();", null);
    }

    // ── Kiosk Mode ────────────────────────────────────────────────────────────

    private void initKioskMode() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(this, KioskAdminReceiver.class);
        if (dpm != null && dpm.isDeviceOwnerApp(getPackageName())) {
            dpm.setKeyguardDisabledFeatures(admin, DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_ALL);
            dpm.setMaximumTimeToLock(admin, 0); // Bildschirm nie sperren
            dpm.setLockTaskPackages(admin, new String[]{
                    getPackageName(),
                    "com.teamviewer.quicksupport.market",
                    "com.android.bluetooth",
                    "com.android.bluetooth.chromecast",
                    "com.google.android.bluetooth",
                    "com.google.android.bluetooth.kirkwood",
                    "com.android.settings",
                    "com.android.tv.settings",
                    "com.wireguard.android",
                    "com.amaze.filemanager"
            });

            // App als preferred Home Activity setzen (überlebt Neustart)
            // Zuerst alle bestehenden Home-Preferences entfernen
            dpm.clearPackagePersistentPreferredActivities(admin, getPackageName());
            ComponentName mainActivity = new ComponentName(this, MainActivity.class);
            android.content.IntentFilter homeFilter = new android.content.IntentFilter(Intent.ACTION_MAIN);
            homeFilter.addCategory(Intent.CATEGORY_HOME);
            homeFilter.addCategory(Intent.CATEGORY_DEFAULT);
            homeFilter.addCategory("android.intent.category.LEANBACK_LAUNCHER");
            dpm.addPersistentPreferredActivity(admin, homeFilter, mainActivity);

            // Google TV Launcher deaktivieren
            dpm.setApplicationHidden(admin, "com.google.android.apps.tv.launcherx", true);
            dpm.setApplicationHidden(admin, "com.google.android.tvlauncher", true);

            // Setup-Wizard permanent deaktivieren
            disableSetupWizard(dpm, admin);

            // Storage Permission still erteilen (für w3coach_urls.json)
            try {
                dpm.setPermissionGrantState(admin, getPackageName(),
                        "android.permission.READ_MEDIA_DOCUMENTS",
                        DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
                dpm.setPermissionGrantState(admin, getPackageName(),
                        "android.permission.READ_EXTERNAL_STORAGE",
                        DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
            } catch (Exception ignored) {}

            // Screensaver/Daydream deaktivieren
            disableScreensaver();
        }
        try { startLockTask(); } catch (Exception ignored) {}
    }

    // ── Permissions Callback ──────────────────────────────────────────────────

    private void onPermissionsGranted() {
        url1 = prefs.url1();
        url2 = prefs.url2();
        url3 = prefs.url3();

        startWithNetworkCheck();
    }

    private void registerNetworkCallback() {
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return;

        networkCallback = new android.net.ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(android.net.Network network) {
                runOnUiThread(() -> {
                    showNoNetwork(false);
                    networkRetryHandler.removeCallbacksAndMessages(null);
                    // URL laden falls noch nicht geladen
                    if (!urlLoaded) {
                        urlLoaded = true;
                        if (!url1.isEmpty()) loadUrl(url1);
                        else tvMenu.show();
                    }
                });
            }

            @Override
            public void onLost(android.net.Network network) {
                runOnUiThread(() -> showNoNetwork(true));
            }
        };

        android.net.NetworkRequest request = new android.net.NetworkRequest.Builder()
                .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        cm.registerNetworkCallback(request, networkCallback);
    }

    private void unregisterNetworkCallback() {
        if (networkCallback == null) return;
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                    getSystemService(CONNECTIVITY_SERVICE);
            if (cm != null) cm.unregisterNetworkCallback(networkCallback);
        } catch (Exception ignored) {}
        networkCallback = null;
    }


    private void startWithNetworkCheck() {
        if (isNetworkAvailable()) {
            showNoNetwork(false);
            urlLoaded = true;
            if (!url1.isEmpty()) {
                loadUrl(url1);
            } else {
                tvMenu.show();
            }
        } else {
            showNoNetwork(true);
            networkRetryHandler.postDelayed(this::startWithNetworkCheck, NETWORK_RETRY_MS);
        }
    }

    private boolean isNetworkAvailable() {
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        android.net.Network network = cm.getActiveNetwork();
        if (network == null) return false;
        android.net.NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        return caps != null && caps.hasCapability(
                android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private android.app.AlertDialog noNetworkDialog;

    private void showNoNetwork(boolean show) {
        noNetworkView.setVisibility(show ? android.view.View.VISIBLE : android.view.View.GONE);
        if (show) {
            showNoNetworkDialog();
        } else {
            if (noNetworkDialog != null && noNetworkDialog.isShowing()) {
                noNetworkDialog.dismiss();
            }
        }
    }

    private void showNoNetworkDialog() {
        if (noNetworkDialog != null && noNetworkDialog.isShowing()) return;
        noNetworkDialog = new android.app.AlertDialog.Builder(this)
                .setTitle("Kein Netzwerk")
                .setMessage("Keine Internetverbindung. Netzwerkeinstellungen öffnen?")
                .setCancelable(false)
                .setPositiveButton("Ja", (d, w) ->
                        startActivity(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)))
                .setNegativeButton("Später", null)
                .create();
        noNetworkDialog.show();
    }

    private android.widget.LinearLayout buildNoNetworkView() {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setGravity(android.view.Gravity.CENTER);
        layout.setBackgroundColor(0xFF000000);

        android.widget.TextView icon = new android.widget.TextView(this);
        icon.setText("🌐");
        icon.setTextSize(72f);
        icon.setGravity(android.view.Gravity.CENTER);

        android.widget.TextView cross = new android.widget.TextView(this);
        cross.setText("✕");
        cross.setTextSize(48f);
        cross.setTextColor(0xFFDD0000);
        cross.setGravity(android.view.Gravity.CENTER);

        android.widget.TextView msg = new android.widget.TextView(this);
        msg.setText("No network");
        msg.setTextSize(24f);
        msg.setTextColor(0xFFAAAAAA);
        msg.setGravity(android.view.Gravity.CENTER);
        msg.setPadding(0, 24, 0, 0);

        layout.addView(icon);
        layout.addView(cross);
        layout.addView(msg);
        layout.setVisibility(android.view.View.GONE);
        return layout;
    }


    // ── URL Handling ──────────────────────────────────────────────────────────

    public void loadUrl(String url) {
        if (url == null || url.isEmpty()) return;
        if (!url.startsWith("http://") && !url.startsWith("https://"))
            url = "https://" + url;
        webView.loadUrl(url);
    }

    /** Wechselt zyklisch zwischen URL1 → URL2 → URL3 → URL1 */
    public void toggleUrl() {
        url1 = prefs.url1();
        url2 = prefs.url2();
        url3 = prefs.url3();

        if (currentUrl == 1 && !url2.isEmpty()) {
            currentUrl = 2; loadUrl(url2);
        } else if (currentUrl == 2 && !url3.isEmpty()) {
            currentUrl = 3; loadUrl(url3);
        } else {
            currentUrl = 1; loadUrl(url1);
        }
    }

    // ── Zoom ──────────────────────────────────────────────────────────────────

    public void applyZoom(int level) {
        webView.getSettings().setTextZoom(ZOOM_BASE + level * ZOOM_STEP);
    }

    // ── Key Handling ──────────────────────────────────────────────────────────

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();

        // Tab → Fokus weiterschalten (für Bluetooth-Tastatur)
        if (keyCode == KeyEvent.KEYCODE_TAB && event.getAction() == KeyEvent.ACTION_DOWN) {
            android.view.View focused = getCurrentFocus();
            if (focused != null) {
                focused.focusSearch(android.view.View.FOCUS_FORWARD).requestFocus();
                return true;
            }
        }

        // Long-Press OK/Enter → Kontextmenü
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && !event.isLongPress()) {
                longPressTriggered = false;
                longPressHandler.postDelayed(() -> {
                    longPressTriggered = true;
                    tvMenu.show();
                }, LONG_PRESS_MS);
                return true;
            }
            if (event.getAction() == KeyEvent.ACTION_UP) {
                longPressHandler.removeCallbacksAndMessages(null);
                if (!longPressTriggered) {
                    // Kurzer Druck → normaler Klick in WebView
                    if (!webView.hasFocus()) webView.requestFocus();
                    return webView.dispatchKeyEvent(event);
                }
                return true;
            }
        }

        // D-Pad → WebView
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP    ||
            keyCode == KeyEvent.KEYCODE_DPAD_DOWN  ||
            keyCode == KeyEvent.KEYCODE_DPAD_LEFT  ||
            keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (!webView.hasFocus()) webView.requestFocus();
            return webView.dispatchKeyEvent(event);
        }

        return super.dispatchKeyEvent(event);
    }

    private void setupBackHandler() {
        getOnBackPressedDispatcher().addCallback(this,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        toggleUrl();
                    }
                });
    }
}
