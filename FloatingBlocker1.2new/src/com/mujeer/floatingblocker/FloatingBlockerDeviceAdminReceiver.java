package com.mujeer.floatingblocker;

import android.app.admin.DeviceAdminReceiver;

/**
 * Having any active device admin at all is enough to require the user to
 * deactivate it (Settings > Security > Device admin apps) before the app
 * can be uninstalled - that friction is the whole point, no custom policy
 * behavior is needed here.
 */
public class FloatingBlockerDeviceAdminReceiver extends DeviceAdminReceiver {
}
