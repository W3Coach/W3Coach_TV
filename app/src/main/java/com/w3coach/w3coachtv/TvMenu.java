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
                        case 4: wgManager.showConfigDialog();    break;
                        case 5: openSystemSettings();            break;
                        case 6: confirmReboot();                 break;
                    }
                })
                .show();
    }

    // ── PIN-Schutz ────────────────────────────────────────────────────────────

    private void withPin(Runnable action) {
        new PinDialog(activity, action::run).show();
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
        View view = LayoutInflater.from(activity).inflate(R.layout.dlg_autoupdate, null);
        CheckBox cbEnabled  = view.findViewById(R.id.cb_autoupdate);
        EditText etInterval = view.findViewById(R.id.et_interval);

        cbEnabled.setChecked(prefs.autoUpdate());
        etInterval.setText(String.valueOf(prefs.updateInterval()));

        new AlertDialog.Builder(activity)
                .setTitle(R.string.dlg_autoupdate_title)
                .setView(view)
                .setPositiveButton(R.string.save, (d, w) -> {
                    boolean enabled = cbEnabled.isChecked();
                    int interval = AutoUpdateJob.DEFAULT_INTERVAL_HOURS;
                    try {
                        int v = Integer.parseInt(etInterval.getText().toString().trim());
                        if (v >= AutoUpdateJob.MIN_INTERVAL_HOURS) interval = v;
                    } catch (NumberFormatException ignored) {}
                    prefs.setAutoUpdate(enabled);
                    prefs.setUpdateInterval(interval);
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
