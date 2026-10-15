package com.mujeer.floatingblocker;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;

/**
 * Remembers the device's actual, original default launcher (package +
 * activity), captured exactly once - right before this app first registers
 * itself as the forced CATEGORY_HOME answer (see
 * BlockEnforcer.applyHomeGatekeeperProtection / HomeGatekeeperActivity).
 * After that registration, asking Android "what currently resolves
 * CATEGORY_HOME" would just answer with our own gatekeeper, so this is the
 * only remaining record of who to actually hand off to whenever nothing
 * should be restricted.
 */
public class RealLauncherStorage {

    private static final String KEY_PACKAGE = "package";
    private static final String KEY_CLASS = "class";

    private final SharedPreferences prefs;

    public RealLauncherStorage(Context context) {
        prefs = DeviceProtectedPrefs.get(context, "floating_blocker_real_launcher_prefs");
    }

    public boolean hasCapturedRealLauncher() {
        return prefs.contains(KEY_PACKAGE);
    }

    public void saveRealLauncher(ComponentName component) {
        prefs.edit()
                .putString(KEY_PACKAGE, component.getPackageName())
                .putString(KEY_CLASS, component.getClassName())
                .apply();
    }

    /** Null if never captured. Callers must still confirm it actually resolves before relying on it. */
    public ComponentName loadRealLauncher() {
        String pkg = prefs.getString(KEY_PACKAGE, null);
        String cls = prefs.getString(KEY_CLASS, null);
        if (pkg == null || cls == null) {
            return null;
        }
        return new ComponentName(pkg, cls);
    }
}
