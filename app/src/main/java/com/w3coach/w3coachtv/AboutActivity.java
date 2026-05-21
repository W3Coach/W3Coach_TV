package com.w3coach.w3coachtv;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.text.format.Formatter;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class AboutActivity extends AppCompatActivity {

    private static final String TV_PACKAGE       = "com.teamviewer.quicksupport.market";
    private static final String TV_ADDON_PACKAGE = "com.teamviewer.quicksupport.addon.universal";
    private static final String TV_ASSET         = "teamviewer_qs.apk";
    private static final String TV_ADDON_ASSET   = "teamviewer_addon.apk";

    private static final String PREF_QS_INSTALLED    = "tv_qs_installed";
    private static final String PREF_ADDON_INSTALLED = "tv_addon_installed";

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(0xFF000000);
        layout.setPadding(64, 64, 64, 64);
        setContentView(layout);

        addLabel(layout, getString(R.string.app_name), 32f, 0xFFFFFFFF, true);
        addLabel(layout, getString(R.string.about_version) + ": " + BuildConfig.VERSION_NAME, 18f, 0xFFAAAAAA, false);
        addLabel(layout, getString(R.string.about_device) + ": " + Build.MODEL, 16f, 0xFFAAAAAA, false);
        addLabel(layout, getString(R.string.about_ip) + ": " + getIpAddress(), 16f, 0xFFAAAAAA, false);

        addLabel(layout, "", 16f, 0xFFAAAAAA, false);

        TextView tvLink = new TextView(this);
        tvLink.setText(R.string.quick_support);
        tvLink.setTextSize(18f);
        tvLink.setTextColor(0xFF1976D2);
        tvLink.setGravity(Gravity.CENTER);
        tvLink.setPadding(0, 16, 0, 16);
        tvLink.setFocusable(true);
        tvLink.setFocusableInTouchMode(true);
        tvLink.setClickable(true);
        tvLink.setOnClickListener(v -> handleQuickSupport());
        layout.addView(tvLink);
    }

    // ── QuickSupport Ablauf ───────────────────────────────────────────────────

    private void handleQuickSupport() {
        // Schritt 1: QuickSupport installiert?
        if (!prefs.getBoolean(PREF_QS_INSTALLED, false)) {
            installFromAssets(TV_ASSET, TV_PACKAGE, PREF_QS_INSTALLED, this::handleQuickSupport);
            return;
        }

        // Schritt 2: Addon installiert?
        if (!prefs.getBoolean(PREF_ADDON_INSTALLED, false)) {
            installFromAssets(TV_ADDON_ASSET, TV_ADDON_PACKAGE, PREF_ADDON_INSTALLED, this::handleQuickSupport);
            return;
        }

        // Schritt 3: Addon-Accessibility aktiv?
        if (!isAccessibilityEnabled(TV_ADDON_PACKAGE)) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.quick_support)
                    .setMessage(R.string.quick_support_addon_accessibility_msg)
                    .setPositiveButton(R.string.perm_grant, (d, w) ->
                            startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return;
        }

        // Alles OK → starten
        launchQuickSupport();
    }

    // ── Installation aus Assets ───────────────────────────────────────────────

    private void installFromAssets(String assetName, String packageName,
                                   String prefKey, Runnable onSuccess) {
        AlertDialog progress = new AlertDialog.Builder(this)
                .setTitle(R.string.quick_support)
                .setMessage(R.string.quick_support_installing)
                .setCancelable(false)
                .create();
        progress.show();

        new Thread(() -> {
            File apkFile = new File(getCacheDir(), assetName);
            boolean copied = copyAsset(assetName, apkFile);

            runOnUiThread(() -> {
                progress.dismiss();
                if (!copied) {
                    Toast.makeText(this, R.string.quick_support_download_failed,
                            Toast.LENGTH_LONG).show();
                    return;
                }
                // Silent installieren
                InstallResultReceiver.postInstallCallback = () -> {
                    // Flag setzen
                    prefs.edit().putBoolean(prefKey, true).apply();
                    // Kurz warten dann weitermachen
                    new Handler(Looper.getMainLooper()).postDelayed(onSuccess, 1500);
                };
                try {
                    SilentInstaller.installExternal(this, apkFile);
                } catch (Exception e) {
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private boolean copyAsset(String assetName, File dest) {
        try (InputStream in  = getAssets().open(assetName);
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[65536];
            int len;
            while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ── QuickSupport starten ──────────────────────────────────────────────────

    private void launchQuickSupport() {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setComponent(new android.content.ComponentName(
                    TV_PACKAGE, "com.teamviewer.quicksupport.ui.QSActivity"));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, R.string.quick_support_not_found, Toast.LENGTH_SHORT).show();
        }
    }

    // ── Accessibility Check ───────────────────────────────────────────────────

    private boolean isAccessibilityEnabled(String packageName) {
        String enabled = android.provider.Settings.Secure.getString(
                getContentResolver(),
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabled != null && enabled.contains(packageName);
    }

    // ── Hilfsmethoden ─────────────────────────────────────────────────────────

    private String getIpAddress() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext()
                    .getSystemService(WIFI_SERVICE);
            if (wm != null) {
                int ip = wm.getConnectionInfo().getIpAddress();
                if (ip != 0) return Formatter.formatIpAddress(ip);
            }
        } catch (Exception ignored) {}
        return "–";
    }

    private void addLabel(LinearLayout parent, String text, float sizeSp,
                          int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(sizeSp);
        tv.setTextColor(color);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, 8, 0, 8);
        if (bold) tv.setTypeface(null, android.graphics.Typeface.BOLD);
        parent.addView(tv);
    }
}
