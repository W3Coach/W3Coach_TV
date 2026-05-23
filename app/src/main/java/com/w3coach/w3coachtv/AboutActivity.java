package com.w3coach.w3coachtv;

import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(0xFF000000);
        layout.setPadding(64, 64, 64, 64);
        setContentView(layout);

        addLabel(layout, getString(R.string.app_name),                              32f, 0xFFFFFFFF, true);
        addLabel(layout, getString(R.string.about_version) + ": " + BuildConfig.VERSION_NAME, 18f, 0xFFAAAAAA, false);
        addLabel(layout, getString(R.string.about_device)  + ": " + Build.MODEL,   16f, 0xFFAAAAAA, false);
        addLabel(layout, getString(R.string.about_ip)      + ": " + getIpAddress(), 16f, 0xFFAAAAAA, false);

        // VPN-IP anzeigen wenn Tunnel aktiv
        if (WireGuardService.isConnected) {
            Prefs prefs = new Prefs(this);
            String vpnIp = prefs.wgClientIp().replace("/32", "").replace("/24", "");
            addLabel(layout, getString(R.string.about_vpn_ip) + ": " + vpnIp, 16f, 0xFF2ECC71, false);
        }
    }

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
