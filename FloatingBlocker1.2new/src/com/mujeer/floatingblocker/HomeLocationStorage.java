package com.mujeer.floatingblocker;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Whether the location-based override is turned on, and the "home"
 * coordinates it's measured against. See HomeLocationChecker for the
 * actual live GPS/distance logic - this is just storage.
 */
public class HomeLocationStorage {

    private static final String PREFS_NAME = "floating_blocker_home_location_prefs";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_HAS_LOCATION = "has_location";
    private static final String KEY_LAT_BITS = "lat_bits";
    private static final String KEY_LON_BITS = "lon_bits";
    private static final String KEY_WAS_OVERRIDE_ACTIVE = "was_override_active";

    private final SharedPreferences prefs;

    public HomeLocationStorage(Context context) {
        prefs = DeviceProtectedPrefs.get(context, PREFS_NAME);
    }

    public boolean isEnabled() {
        return prefs.getBoolean(KEY_ENABLED, false);
    }

    public void setEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public boolean hasLocation() {
        return prefs.getBoolean(KEY_HAS_LOCATION, false);
    }

    public double getHomeLat() {
        return Double.longBitsToDouble(prefs.getLong(KEY_LAT_BITS, 0));
    }

    public double getHomeLon() {
        return Double.longBitsToDouble(prefs.getLong(KEY_LON_BITS, 0));
    }

    public void setHomeLocation(double lat, double lon) {
        prefs.edit()
                .putBoolean(KEY_HAS_LOCATION, true)
                .putLong(KEY_LAT_BITS, Double.doubleToRawLongBits(lat))
                .putLong(KEY_LON_BITS, Double.doubleToRawLongBits(lon))
                .apply();
    }

    /**
     * Whether the location override was active as of the last enforcement
     * cycle - lets BlockEnforcer detect the exact moment it turns on
     * (snapshot everything) or off again (restore everything), instead of
     * just re-checking a live yes/no each time with no memory of what
     * changed.
     */
    public boolean wasOverrideActiveLastCheck() {
        return prefs.getBoolean(KEY_WAS_OVERRIDE_ACTIVE, false);
    }

    public void setOverrideActiveLastCheck(boolean active) {
        prefs.edit().putBoolean(KEY_WAS_OVERRIDE_ACTIVE, active).apply();
    }
}
