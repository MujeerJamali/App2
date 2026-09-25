package com.mujeer.floatingblocker;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;

/**
 * The live "am I far from home" check behind the location-based override:
 * Lock Schedule's editing-protection, Block enforcement, and Alarms all
 * treat "far from home" the same as "nothing is restricted right now" (see
 * LockScheduleStorage.isEditingAllowed, BlockEnforcer, AlarmPunisher). No
 * location history is kept - only the current position is ever considered,
 * so resolving a past Alarm occurrence after the fact uses today's current
 * location as a best-effort stand-in, not whatever the location actually
 * was back when that occurrence happened.
 *
 * Uses plain framework LocationManager, not Play Services - this project
 * has no Gradle/Play Services dependency to build against. A cached
 * last-known fix is used if it's fresh enough; otherwise (or if there's
 * none at all) this treats location as "not found" for right now, which -
 * per the feature's own fail-safe design - means restrictions stay in
 * effect exactly as if this feature didn't exist, never the other way
 * around. A fresh single-shot request is kicked off in the background
 * either way, to warm the cache for the next check.
 */
public class HomeLocationChecker {

    private static final float HOME_RADIUS_METERS = 20_000f; // 20km
    private static final long STALE_THRESHOLD_MILLIS = 15 * 60 * 1000L; // 15 minutes

    public static boolean isFarFromHome(Context context) {
        HomeLocationStorage storage = new HomeLocationStorage(context);
        if (!storage.isEnabled() || !storage.hasLocation()) {
            return false;
        }

        Location current = getFreshEnoughLastKnownLocation(context);
        requestSingleUpdateToWarmCache(context);

        if (current == null) {
            return false; // location not found - fail-safe: treat as home, restrictions stay in effect
        }

        float[] distance = new float[1];
        Location.distanceBetween(storage.getHomeLat(), storage.getHomeLon(),
                current.getLatitude(), current.getLongitude(), distance);
        return distance[0] > HOME_RADIUS_METERS;
    }

    /** For the setup screen capturing "set this as home" - not gated by the staleness rule above, since the user is standing there right now watching it. */
    public static Location getLastKnownLocationForSetup(Context context) {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        return getBestLastKnownLocation(context);
    }

    private static Location getFreshEnoughLastKnownLocation(Context context) {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        Location best = getBestLastKnownLocation(context);
        if (best == null || (System.currentTimeMillis() - best.getTime()) > STALE_THRESHOLD_MILLIS) {
            return null;
        }
        return best;
    }

    private static Location getBestLastKnownLocation(Context context) {
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) {
            return null;
        }
        Location best = null;
        try {
            for (String provider : lm.getAllProviders()) {
                Location loc = lm.getLastKnownLocation(provider);
                if (loc != null && (best == null || loc.getTime() > best.getTime())) {
                    best = loc;
                }
            }
        } catch (SecurityException e) {
            return null;
        }
        return best;
    }

    private static void requestSingleUpdateToWarmCache(Context context) {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) {
            return;
        }
        String provider;
        if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            provider = LocationManager.GPS_PROVIDER;
        } else if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            provider = LocationManager.NETWORK_PROVIDER;
        } else {
            return;
        }
        try {
            lm.requestSingleUpdate(provider, new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    // Nothing to do - the system already records this as the provider's last-known location.
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {
                }

                // No @Override here - AIDE's compiler (ECJ) doesn't reliably
                // recognize these two as valid overrides of LocationListener's
                // default methods on newer android.jar versions, even though
                // the signature is correct and this still properly implements
                // the interface either way.
                public void onProviderEnabled(String provider) {
                }

                public void onProviderDisabled(String provider) {
                }
            }, Looper.getMainLooper());
        } catch (Exception e) {
            // Best effort - if this fails, the next cycle just tries again.
        }
    }
}
