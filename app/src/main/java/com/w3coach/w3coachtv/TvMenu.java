package com.w3coach.w3coachtv;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;

/**
 * Kontextmenü für den TV-Betrieb (Long-Press OK).
 *
 * Frei zugänglich: Zoom, Auto-Update, Info
 * PIN-geschützt:   System (URLs, Systemeinstellungen, Neustart)
 */
public class TvMenu {

    private static final String[] ZOOM_LABELS = {
        "75%","80%","85%","90%","95%","100%","105%","110%","115%","120%","125%"
    };

    private final MainActivity   activity;
    private final Prefs          prefs;
    private final WireGuardManager wgManager;

    public TvMenu(MainActivity activity) {
        this.activity  = activity;
        this.prefs     = new Prefs(activity);
        this.wgManager = new WireGuardManager(activity);
    }

    // ── Hauptmenü ─────────────────────────────────────────────────────────────

    public void show() {
        String[] items = {
            activity.getString(R.string.menu_zoom),
            activity.getString(R.string.menu_autoupdate),
            WireGuardService.isConnected
                    ? activity.getString(R.string.wg_disconnect)
                    : activity.getString(R.string.wg_connect),
            activity.getString(R.string.menu_about),
            activity.getString(R.string.menu_system),
        };
        // Hinweis: wg_connect/disconnect = "Quicksupport verbinden/trennen"

        new AlertDialog.Builder(activity)
                .setTitle(R.string.app_name)
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0: showZoom();                     break;
                        case 1: showAutoUpdate();               break;
                        case 2: wgManager.toggle();             break;
                        case 3: openAbout();                    break;
                        case 4: withPin(this::showSystemMenu);  break;
                    }
                })
                .show();
    }

    // ── System-Untermenü (PIN-geschützt) ──────────────────────────────────────

    private void showSystemMenu() {
        String[] items = {
            activity.getString(R.string.menu_url1) + "  " + truncate(prefs.url1()),
            activity.getString(R.string.menu_url2) + "  " + truncate(prefs.url2()),
            activity.getString(R.string.menu_url3) + "  " + truncate(prefs.url3()),
            activity.getString(R.string.menu_autoupdate),
            activity.getString(R.string.menu_check_update),
            activity.getString(R.string.wg_configure),
            activity.getString(R.string.menu_settings),
            activity.getString(R.string.menu_reboot),
        };

        new AlertDialog.Builder(activity)
                .setTitle(R.string.menu_system)
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0: editUrl(1);                      break;
                        case 1: editUrl(2);                      break;
                        case 2: editUrl(3);                      break;
                        case 3: showAutoUpdate();                break;
                        case 4: checkUpdateNow();                break;
                        case 5: wgManager.showConfigDialog();    break;
                        case 6: openSystemSettings();            break;
                        case 7: confirmReboot();                 break;
                    }
                })
                .show();
    }

    // ── PIN-Schutz ────────────────────────────────────────────────────────────

    private void withPin(Runnable action) {
        new PinDialog(activity, action::run).show();
    }



    private void checkUpdateNow() {
        ToastHelper.info(activity, activity.getString(R.string.menu_check_update) + "…");
        new Thread(() -> {
            try {
                GithubUpdateChecker.UpdateInfo info = GithubUpdateChecker.checkForUpdate(
                        BuildConfig.UPDATE_URL, BuildConfig.VERSION_CODE);
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    if (info == null) {
                        ToastHelper.success(activity, activity.getString(R.string.update_none));
                        return;
                    }
                    new AlertDialog.Builder(activity)
                            .setTitle(R.string.update_available)
                            .setMessage("Version " + info.tagName)
                            .setPositiveButton(R.string.yes, (d, w) -> installUpdateNow(info))
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                });
            } catch (Exception e) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                        ToastHelper.error(activity, "Update-Check fehlgeschlagen: " + e.getMessage()));
            }
        }).start();
    }

    private void installUpdateNow(GithubUpdateChecker.UpdateInfo info) {
        ToastHelper.info(activity, activity.getString(R.string.update_installing));
        new Thread(() -> {
            try {
                java.io.File apk = new java.io.File(activity.getCacheDir(), "w3coachtv_update.apk");
                GithubUpdateChecker.downloadApk(info.downloadUrl, apk);
                SilentInstaller.install(activity, apk);
                // Manuelles Update: sofort neu starten
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    android.app.admin.DevicePolicyManager dpm =
                            (android.app.admin.DevicePolicyManager)
                            activity.getSystemService(android.content.Context.DEVICE_POLICY_SERVICE);
                    android.content.ComponentName admin =
                            new android.content.ComponentName(activity, KioskAdminReceiver.class);
                    if (dpm != null && dpm.isDeviceOwnerApp(activity.getPackageName())) {
                        dpm.reboot(admin);
                    }
                }, 5000);
            } catch (Exception e) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                        ToastHelper.error(activity, "Update fehlgeschlagen: " + e.getMessage()));
            }
        }).start();
    }


    // ── URL bearbeiten ────────────────────────────────────────────────────────

    private void editUrl(int nr) {
        String current = nr == 1 ? prefs.url1() : nr == 2 ? prefs.url2() : prefs.url3();

        EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(current);
        input.setSelection(current.length());
        input.setSingleLine(true);
        input.setOnEditorActionListener((v, actionId, event) -> {
            InputMethodManager imm = (InputMethodManager)
                    activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            return false;
        });

        new AlertDialog.Builder(activity)
                .setTitle("URL " + nr)
                .setView(input)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String url = input.getText().toString().trim();
                    if (!url.startsWith("http://") && !url.startsWith("https://"))
                        url = "https://" + url;
                    if (nr == 1) { prefs.setUrl1(url); activity.loadUrl(url); }
                    else if (nr == 2) prefs.setUrl2(url);
                    else prefs.setUrl3(url);
                })
                .setNegativeButton(R.string.cancel, null)
                .create()
                .show();
    }

    // ── Zoom ──────────────────────────────────────────────────────────────────

    private void showZoom() {
        Spinner spinner = new Spinner(activity);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                activity, android.R.layout.simple_spinner_item, ZOOM_LABELS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(prefs.zoom());

        new AlertDialog.Builder(activity)
                .setTitle(R.string.dlg_zoom_title)
                .setView(spinner)
                .setPositiveButton(R.string.save, (d, w) -> {
                    int zoom = spinner.getSelectedItemPosition();
                    prefs.setZoom(zoom);
                    activity.applyZoom(zoom);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ── Auto-Update ───────────────────────────────────────────────────────────

    private void showAutoUpdate() {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(activity);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(48, 16, 48, 8);

        CheckBox cbEnabled = new CheckBox(activity);
        cbEnabled.setText(R.string.menu_autoupdate);
        cbEnabled.setChecked(prefs.autoUpdate());
        layout.addView(cbEnabled);

        // Intervall
        android.widget.TextView tvInterval = new android.widget.TextView(activity);
        tvInterval.setText(R.string.dlg_autoupdate_interval);
        tvInterval.setPadding(0, 16, 0, 0);
        layout.addView(tvInterval);
        EditText etInterval = new EditText(activity);
        etInterval.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etInterval.setText(String.valueOf(prefs.updateInterval()));
        etInterval.setSingleLine(true);
        layout.addView(etInterval);

        // Neustart nach Update
        android.widget.TextView tvReboot = new android.widget.TextView(activity);
        tvReboot.setText(R.string.update_reboot_title);
        tvReboot.setPadding(0, 16, 0, 0);
        layout.addView(tvReboot);

        CheckBox cbRebootNow = new CheckBox(activity);
        cbRebootNow.setText(R.string.update_reboot_now);
        cbRebootNow.setChecked(prefs.updateRebootNow());
        layout.addView(cbRebootNow);

        android.widget.TextView tvRebootTime = new android.widget.TextView(activity);
        tvRebootTime.setText(R.string.update_reboot_time);
        tvRebootTime.setPadding(0, 8, 0, 0);
        layout.addView(tvRebootTime);

        EditText etRebootTime = new EditText(activity);
        etRebootTime.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        etRebootTime.setText(prefs.updateRebootTime());
        etRebootTime.setSingleLine(true);
        etRebootTime.setEnabled(!prefs.updateRebootNow());
        layout.addView(etRebootTime);

        cbRebootNow.setOnCheckedChangeListener((btn, checked) ->
                etRebootTime.setEnabled(!checked));

        new AlertDialog.Builder(activity)
                .setTitle(R.string.dlg_autoupdate_title)
                .setView(layout)
                .setPositiveButton(R.string.save, (d, w) -> {
                    boolean enabled = cbEnabled.isChecked();
                    int interval = AutoUpdateJob.DEFAULT_INTERVAL_HOURS;
                    try {
                        int v = Integer.parseInt(etInterval.getText().toString().trim());
                        if (v >= AutoUpdateJob.MIN_INTERVAL_HOURS) interval = v;
                    } catch (NumberFormatException ignored) {}
                    prefs.setAutoUpdate(enabled);
                    prefs.setUpdateInterval(interval);
                    prefs.setUpdateRebootNow(cbRebootNow.isChecked());
                    prefs.setUpdateRebootTime(etRebootTime.getText().toString().trim());
                    AutoUpdateJob.schedule(activity);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ── Reboot ────────────────────────────────────────────────────────────────

    private void confirmReboot() {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.dlg_reboot_title)
                .setMessage(R.string.dlg_reboot_msg)
                .setPositiveButton(R.string.yes, (d, w) -> AccessibilityService.reboot(activity))
                .setNegativeButton(R.string.no, null)
                .show();
    }

    // ── System-Settings ───────────────────────────────────────────────────────

    private void openSystemSettings() {
        activity.startActivity(new Intent(Settings.ACTION_SETTINGS));
    }

    // ── About ─────────────────────────────────────────────────────────────────

    private void openAbout() {
        activity.startActivity(new Intent(activity, AboutActivity.class));
    }

    // ── Hilfsmethoden ─────────────────────────────────────────────────────────

    private static String truncate(String s) {
        if (s == null || s.isEmpty()) return "–";
        return s.length() > 30 ? s.substring(0, 30) + "…" : s;
    }
}
