package com.mujeer.floatingblocker;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The ringing screen itself. Shows over the lock screen, ignores the back
 * button, and offers exactly one way out: scanning both barcodes of one of
 * this Alarm's pairs, within PAIR_SCAN_WINDOW_MILLIS of each other. No
 * snooze, no swipe-to-dismiss, nothing else - that's the whole point of
 * this feature.
 *
 * launchMode="singleInstance" (see the manifest) means a second alarm
 * trigger while this screen is already showing reuses this SAME instance
 * via onNewIntent rather than creating a new one - onCreate() and
 * onNewIntent() share loadAlarmState() so both paths pick up the new
 * occurrence's data correctly instead of the screen silently going stale
 * (which is exactly the kind of gap that would only ever show up when
 * something in the app was already open/running, not when the phone was
 * otherwise idle).
 */
public class AlarmRingActivity extends Activity {

    private static final int REQUEST_SCAN_DISMISS = 701;

    private String alarmId;
    private long occurrenceMillis;

    private final Runnable onServiceStopped = new Runnable() {
        @Override
        public void run() {
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            showOverLockScreen();
            setContentView(R.layout.activity_alarm_ring);

            Button btnScan = (Button) findViewById(R.id.btnScanToDismiss);
            btnScan.setOnClickListener(new android.view.View.OnClickListener() {
                @Override
                public void onClick(android.view.View v) {
                    launchScanner();
                }
            });

            loadAlarmState(getIntent());
        } catch (Exception e) {
            // Never let a problem here take down the whole app - worst
            // case this screen just closes instead of showing, same as
            // the deliberate "already resolved" early-finish below.
            Log.e("AlarmRingActivity", "onCreate failed", e);
            finish();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        try {
            loadAlarmState(intent);
        } catch (Exception e) {
            Log.e("AlarmRingActivity", "onNewIntent failed", e);
            finish();
        }
    }

    /** Shared by onCreate and onNewIntent - singleInstance means a second Alarm firing while this screen is already up reuses this same instance. */
    private void loadAlarmState(Intent intent) {
        alarmId = intent.getStringExtra(AlarmRingReceiver.EXTRA_ALARM_ID);
        occurrenceMillis = intent.getLongExtra(AlarmRingReceiver.EXTRA_OCCURRENCE_MILLIS, 0);

        if (alarmId == null || !AlarmRingService.isRingingFor(alarmId, occurrenceMillis)) {
            // Already resolved (dismissed or punished) before this screen even opened/updated.
            finish();
            return;
        }

        String alarmName = "";
        for (Alarm a : new AlarmsStorage(this).loadAlarms()) {
            if (a.id.equals(alarmId)) {
                alarmName = a.name;
                break;
            }
        }
        ((TextView) findViewById(R.id.txtAlarmRingingName)).setText(alarmName);
    }

    private void showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                            | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                            | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                            | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }
    }

    private void launchScanner() {
        Alarm alarm = null;
        for (Alarm a : new AlarmsStorage(this).loadAlarms()) {
            if (a.id.equals(alarmId)) {
                alarm = a;
                break;
            }
        }
        if (alarm == null || alarm.barcodePairs.isEmpty()) {
            // Alarm was deleted, or somehow has no pairs, while ringing - nothing to scan against.
            return;
        }

        java.util.Map<String, String> barcodeValueById = new java.util.HashMap<String, String>();
        for (RegisteredBarcode b : new RegisteredBarcodesStorage(this).loadBarcodes()) {
            barcodeValueById.put(b.id, b.value);
        }

        List<String> valuesA = new ArrayList<String>();
        List<String> valuesB = new ArrayList<String>();
        for (BarcodePair pair : alarm.barcodePairs) {
            String valueA = barcodeValueById.get(pair.barcodeIdA);
            String valueB = barcodeValueById.get(pair.barcodeIdB);
            if (valueA != null && valueB != null) {
                valuesA.add(valueA);
                valuesB.add(valueB);
            }
        }
        if (valuesA.isEmpty()) {
            // Every barcode in every pair was removed from Registered Barcodes since this Alarm was set up - nothing to scan against.
            return;
        }

        Intent intent = new Intent(this, BarcodeScanActivity.class);
        intent.putExtra(BarcodeScanActivity.EXTRA_PAIR_VALUES_A, valuesA.toArray(new String[0]));
        intent.putExtra(BarcodeScanActivity.EXTRA_PAIR_VALUES_B, valuesB.toArray(new String[0]));
        startActivityForResult(intent, REQUEST_SCAN_DISMISS);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SCAN_DISMISS && resultCode == RESULT_OK) {
            AlarmRingService.notifyDismissed(this, alarmId, occurrenceMillis);
            finish();
        }
        // RESULT_CANCELED (camera denied/backed out) just returns to this still-ringing screen - no dismissal.
    }

    @Override
    protected void onResume() {
        super.onResume();
        AlarmRingService.setStopListener(onServiceStopped);
        if (alarmId != null && !AlarmRingService.isRingingFor(alarmId, occurrenceMillis)) {
            finish();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        AlarmRingService.clearStopListener();
    }

    @Override
    public void onBackPressed() {
        // Deliberately does nothing - barcode scan is the only way out.
    }
}
