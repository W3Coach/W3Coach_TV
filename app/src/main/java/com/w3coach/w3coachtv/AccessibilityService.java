package com.w3coach.w3coachtv;

import android.view.accessibility.AccessibilityEvent;

public class AccessibilityService extends android.accessibilityservice.AccessibilityService {

    public static AccessibilityService instance;

    @Override
    public void onServiceConnected() {
        instance = this;
    }

    public static void reboot(android.content.Context context) {
        android.app.admin.DevicePolicyManager dpm =
                (android.app.admin.DevicePolicyManager)
                context.getSystemService(android.content.Context.DEVICE_POLICY_SERVICE);
        android.content.ComponentName admin =
                new android.content.ComponentName(context, KioskAdminReceiver.class);
        if (dpm != null && dpm.isDeviceOwnerApp(context.getPackageName())) {
            dpm.reboot(admin);
            return;
        }
        if (instance != null) {
            instance.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
        }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}
    @Override public void onDestroy() { instance = null; super.onDestroy(); }
}
