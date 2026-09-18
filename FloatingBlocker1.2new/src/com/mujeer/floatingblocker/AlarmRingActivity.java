package com.mujeer.floatingblocker;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The ringing screen itself. Shows over the lock screen, ignores the back
 * button, and offers exactly one way out: scanning one of this Alarm's
 * registered barcodes. No snooze, no swipe-to-dismiss, nothing else -
 * that's the whole point of this feature.
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

        showOverLockScreen();
        setContentView(R.layout.activity_alarm_ring);

        alarmId = getIntent().getStringExtra(AlarmRingReceiver.EXTRA_ALARM_ID);
        occurrenceMillis = getIntent().getLongExtra(AlarmRingReceiver.EXTRA_OCCURRENCE_MILLIS, 0);

        if (alarmId == null || !AlarmRingService.isRingingFor(alarmId, occurrenceMillis)) {
            // Already resolved (dismissed or punished) before this screen even opened.
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

        Button btnScan = (Button) findViewById(R.id.btnScanToDismiss);
        btnScan.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                launchScanner();
            }
        });
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
        Set<String> acceptedValues = new HashSet<String>();
        List<RegisteredBarcode> allBarcodes = new RegisteredBarcodesStorage(this).loadBarcodes();
        Alarm alarm = null;
        for (Alarm a : new AlarmsStorage(this).loadAlarms()) {
            if (a.id.equals(alarmId)) {
                alarm = a;
                break;
            }
        }
        if (alarm != null) {
            for (RegisteredBarcode b : allBarcodes) {
                if (alarm.barcodeIds.contains(b.id)) {
                    acceptedValues.add(b.value);
                }
            }
        }
        Intent intent = new Intent(this, BarcodeScanActivity.class);
        intent.putExtra(BarcodeScanActivity.EXTRA_ACCEPTED_VALUES, acceptedValues.toArray(new String[0]));
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
