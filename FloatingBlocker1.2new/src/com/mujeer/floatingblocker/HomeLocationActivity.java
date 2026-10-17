package com.mujeer.floatingblocker;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Turns the location-based override on/off, and captures/updates the
 * "home" coordinates it's measured against. See HomeLocationChecker for
 * the actual distance logic this feeds.
 *
 * Changing an ALREADY-SET home location is gated by the true Lock
 * Schedule state (isCurrentlyLocked(), not isEditingAllowed()) -
 * deliberately not location-aware itself, unlike almost every other
 * "editing allowed" check in this app. If it used isEditingAllowed()
 * instead, being far from home would unlock the ability to redefine what
 * "home" means, which would let a single trip permanently disable this
 * feature for good (reset home to wherever you currently are while the
 * override has already kicked in, and your real home becomes "far away"
 * forever after). First-time setup (no location saved yet) is always
 * allowed, same as every other "add" in this app.
 */
public class HomeLocationActivity extends Activity {

    private static final int REQUEST_LOCATION_PERMISSION = 601;

    private HomeLocationStorage storage;
    private LockScheduleStorage lockScheduleStorage;
    private TextView txtLockedMessage;
    private TextView txtStatus;
    private Button btnToggleEnabled;
    private Button btnSetHome;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_location);
        setTitle(R.string.home_location_screen_title);

        storage = new HomeLocationStorage(this);
        lockScheduleStorage = new LockScheduleStorage(this);

        txtLockedMessage = (TextView) findViewById(R.id.txtLockedMessage);
        txtStatus = (TextView) findViewById(R.id.txtStatus);
        btnToggleEnabled = (Button) findViewById(R.id.btnToggleEnabled);
        btnSetHome = (Button) findViewById(R.id.btnSetHome);

        btnToggleEnabled.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onToggleClicked();
            }
        });
        btnSetHome.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onSetHomeClicked();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUi();
    }

    private void refreshUi() {
        boolean enabled = storage.isEnabled();
        boolean hasLocation = storage.hasLocation();

        btnToggleEnabled.setText(enabled ? R.string.home_location_disable_button : R.string.home_location_enable_button);
        btnSetHome.setText(hasLocation ? R.string.home_location_update_button : R.string.home_location_set_button);

        if (!hasLocation) {
            txtStatus.setText(R.string.home_location_status_not_set);
        } else {
            String coords = String.format(java.util.Locale.getDefault(), "%.5f, %.5f", storage.getHomeLat(), storage.getHomeLon());
            txtStatus.setText(getString(enabled ? R.string.home_location_status_enabled : R.string.home_location_status_disabled, coords));
        }

        boolean changeLocked = hasLocation && lockScheduleStorage.isCurrentlyLocked();
        txtLockedMessage.setVisibility(changeLocked ? View.VISIBLE : View.GONE);
    }

    private void onToggleClicked() {
        storage.setEnabled(!storage.isEnabled());
        BlockEnforcer.reapplyAndReschedule(this);
        refreshUi();
    }

    private void onSetHomeClicked() {
        boolean changeLocked = storage.hasLocation() && lockScheduleStorage.isCurrentlyLocked();
        if (changeLocked) {
            Toast.makeText(this, R.string.msg_home_location_locked, Toast.LENGTH_LONG).show();
            return;
        }

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);
            return;
        }

        captureCurrentLocation();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                captureCurrentLocation();
            } else {
                Toast.makeText(this, R.string.msg_home_location_permission_needed, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void captureCurrentLocation() {
        Location location = HomeLocationChecker.getLastKnownLocationForSetup(this);
        if (location == null) {
            Toast.makeText(this, R.string.msg_home_location_unavailable, Toast.LENGTH_LONG).show();
            return;
        }
        storage.setHomeLocation(location.getLatitude(), location.getLongitude());
        BlockEnforcer.reapplyAndReschedule(this);
        Toast.makeText(this, R.string.msg_home_location_set, Toast.LENGTH_SHORT).show();
        refreshUi();
    }
}
