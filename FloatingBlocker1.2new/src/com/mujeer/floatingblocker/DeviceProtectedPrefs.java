package com.mujeer.floatingblocker;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

/**
 * Helper to always get SharedPreferences backed by DEVICE-PROTECTED storage
 * (available before the user unlocks their phone for the first time after a
 * reboot), instead of the normal credential-encrypted storage (only readable
 * after unlock). This is what lets our enforcement logic actually work
 * immediately at boot, before someone can dive into Settings.
 */
public class DeviceProtectedPrefs {

    public static SharedPreferences get(Context context, String name) {
        Context targetContext = context;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (!context.isDeviceProtectedStorage()) {
                targetContext = context.createDeviceProtectedStorageContext();
            }
        }
        return targetContext.getSharedPreferences(name, Context.MODE_PRIVATE);
    }
}
