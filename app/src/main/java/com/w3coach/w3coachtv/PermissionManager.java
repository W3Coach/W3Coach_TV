package com.w3coach.w3coachtv;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

/**
 * Führt den Nutzer beim Erststart durch die erforderlichen Berechtigungen.
 * Reihenfolge: Overlay → Install Unknown Apps → Accessibility Service
 *
 * Nach Rückkehr von den System-Settings wird checkAndRequest() erneut
 * aufgerufen – direkt aus MainActivity.onResume().
 */
public class PermissionManager {

    public interface Callback { void onAllGranted(); }

    private final MainActivity activity;
    private final Prefs         prefs;
    private final Callback      callback;

    private AlertDialog currentDialog;

    public PermissionManager(MainActivity activity, Callback callback) {
        this.activity = activity;
        this.prefs    = new Prefs(activity);
        this.callback = callback;
    }

    /** Wird aus onCreate und onResume aufgerufen. */
    public void checkAndRequest() {
        // Dialog schließen falls noch offen (z.B. nach Rückkehr von Settings)
        if (currentDialog != null && currentDialog.isShowing()) {
            currentDialog.dismiss();
            currentDialog = null;
        }

        if (!prefs.firstRun()) { callback.onAllGranted(); return; }

        if (!hasOverlay())          { requestOverlay();       return; }
        if (!hasInstallPackages())  { requestInstallPackages(); return; }
        if (!hasAccessibility())    { requestAccessibility();  return; }

        prefs.setFirstRun(false);
        callback.onAllGranted();
    }

    // ── Overlay ───────────────────────────────────────────────────────────────

    private boolean hasOverlay() {
        return Settings.canDrawOverlays(activity);
    }

    private void requestOverlay() {
        showDialog(
            activity.getString(R.string.perm_overlay),
            () -> activity.startActivity(new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + activity.getPackageName())))
        );
    }

    // ── Install Unknown Apps ──────────────────────────────────────────────────

    private boolean hasInstallPackages() {
        return activity.getPackageManager().canRequestPackageInstalls();
    }

    private void requestInstallPackages() {
        showDialog(
            activity.getString(R.string.perm_install),
            () -> activity.startActivity(new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName())))
        );
    }

    // ── Accessibility Service ─────────────────────────────────────────────────

    private boolean hasAccessibility() {
        String setting = Settings.Secure.getString(
                activity.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        String component = activity.getPackageName()
                + "/" + activity.getPackageName() + ".AccessibilityService";
        return setting != null && setting.contains(component);
    }

    private void requestAccessibility() {
        showDialog(
            activity.getString(R.string.perm_accessibility),
            () -> activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        );
    }

    // ── Dialog ────────────────────────────────────────────────────────────────

    private void showDialog(String message, Runnable openSettings) {
        currentDialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.perm_title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(R.string.perm_grant, (d, w) -> {
                    // Settings öffnen – nach Rückkehr prüft onResume neu
                    openSettings.run();
                })
                .create();
        currentDialog.show();
    }
}
