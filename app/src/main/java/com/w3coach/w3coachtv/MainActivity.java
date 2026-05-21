package com.w3coach.w3coachtv;

import android.annotation.SuppressLint;
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

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Vollbild
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        prefs  = new Prefs(this);
        tvMenu = new TvMenu(this);

        // WebView als Content-View
        webView = new WebView(this);
        setContentView(webView);

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
        // Permissions nach Rückkehr von System-Settings neu prüfen
        if (permissionManager != null) permissionManager.checkAndRequest();
    }

    // ── WebView Setup ─────────────────────────────────────────────────────────

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
            dpm.setLockTaskPackages(admin, new String[]{
                    getPackageName(),
                    "com.teamviewer.quicksupport.market"
            });

            // App als preferred Home Activity setzen (überlebt Neustart)
            ComponentName mainActivity = new ComponentName(this, MainActivity.class);
            android.content.IntentFilter homeFilter = new android.content.IntentFilter(Intent.ACTION_MAIN);
            homeFilter.addCategory(Intent.CATEGORY_HOME);
            homeFilter.addCategory(Intent.CATEGORY_DEFAULT);
            dpm.addPersistentPreferredActivity(admin, homeFilter, mainActivity);

            // Setup-Wizard permanent deaktivieren
            disableSetupWizard(dpm, admin);

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

        // Starte mit URL 1 (oder zeige Menü wenn noch keine URL konfiguriert)
        if (!url1.isEmpty()) {
            loadUrl(url1);
        } else {
            tvMenu.show();
        }
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
