package com.mujeer.floatingblocker;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Editing rules, same pattern as everywhere else in this app: adding a
 * trigger, a barcode, or an affected Block only ever adds restriction, so
 * it's always allowed. Removing a trigger/barcode/block, or deleting the
 * whole Alarm, is only allowed outside a locked Lock Schedule period.
 */
public class AlarmEditActivity extends Activity {

    private AlarmsStorage alarmsStorage;
    private RegisteredBarcodesStorage barcodesStorage;
    private BlocksStorage blocksStorage;
    private LockScheduleStorage lockScheduleStorage;

    private EditText editAlarmName;
    private TextView txtLockedMessage;
    private LinearLayout triggersContainer;
    private Button btnPickTriggerTime;
    private Button btnAddTrigger;
    private Button btnSelectBarcodes;
    private Button btnSelectAffectedBlocks;
    private Button btnSaveAlarm;
    private Button btnDeleteAlarm;

    private String alarmId; // null if creating a new Alarm
    private final List<AlarmTrigger> triggers = new ArrayList<AlarmTrigger>();
    private final Set<String> selectedBarcodeIds = new LinkedHashSet<String>();
    private final Set<String> selectedBlockIds = new LinkedHashSet<String>();
    private int pickedMinuteOfDay = -1;
    private boolean editingAllowed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alarm_edit);
        setTitle(R.string.alarm_edit_screen_title);

        alarmsStorage = new AlarmsStorage(this);
        barcodesStorage = new RegisteredBarcodesStorage(this);
        blocksStorage = new BlocksStorage(this);
        lockScheduleStorage = new LockScheduleStorage(this);

        editAlarmName = (EditText) findViewById(R.id.editAlarmName);
        txtLockedMessage = (TextView) findViewById(R.id.txtLockedMessage);
        triggersContainer = (LinearLayout) findViewById(R.id.triggersContainer);
        btnPickTriggerTime = (Button) findViewById(R.id.btnPickTriggerTime);
        btnAddTrigger = (Button) findViewById(R.id.btnAddTrigger);
        btnSelectBarcodes = (Button) findViewById(R.id.btnSelectBarcodes);
        btnSelectAffectedBlocks = (Button) findViewById(R.id.btnSelectAffectedBlocks);
        btnSaveAlarm = (Button) findViewById(R.id.btnSaveAlarm);
        btnDeleteAlarm = (Button) findViewById(R.id.btnDeleteAlarm);

        alarmId = getIntent().getStringExtra(AlarmsListActivity.EXTRA_ALARM_ID);
        loadExistingAlarmIfAny();

        editingAllowed = lockScheduleStorage.isEditingAllowed();
        txtLockedMessage.setVisibility(editingAllowed ? View.GONE : View.VISIBLE);
        btnDeleteAlarm.setEnabled(editingAllowed);
        if (alarmId == null) {
            btnDeleteAlarm.setVisibility(View.GONE);
        }

        btnPickTriggerTime.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { pickTriggerTime(); }
        });
        btnAddTrigger.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { onAddTriggerClicked(); }
        });
        btnSelectBarcodes.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { onSelectBarcodesClicked(); }
        });
        btnSelectAffectedBlocks.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { onSelectAffectedBlocksClicked(); }
        });
        btnSaveAlarm.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { onSaveClicked(); }
        });
        btnDeleteAlarm.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { onDeleteClicked(); }
        });

        renderTriggers();
    }

    private void loadExistingAlarmIfAny() {
        if (alarmId == null) {
            return;
        }
        for (Alarm a : alarmsStorage.loadAlarms()) {
            if (a.id.equals(alarmId)) {
                editAlarmName.setText(a.name);
                triggers.addAll(a.triggers);
                selectedBarcodeIds.addAll(a.barcodeIds);
                selectedBlockIds.addAll(a.affectedBlockIds);
                return;
            }
        }
    }

    private void pickTriggerTime() {
        java.util.Calendar now = java.util.Calendar.getInstance();
        new TimePickerDialog(this, new TimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(android.widget.TimePicker view, int hourOfDay, int minute) {
                pickedMinuteOfDay = hourOfDay * 60 + minute;
                Toast.makeText(AlarmEditActivity.this, R.string.msg_time_picked, Toast.LENGTH_SHORT).show();
            }
        }, now.get(java.util.Calendar.HOUR_OF_DAY), now.get(java.util.Calendar.MINUTE), false).show();
    }

    private void onAddTriggerClicked() {
        if (pickedMinuteOfDay < 0) {
            Toast.makeText(this, R.string.msg_pick_time_first, Toast.LENGTH_SHORT).show();
            return;
        }
        final String[] dayLabels = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
        final boolean[] checked = new boolean[7];
        new AlertDialog.Builder(this)
                .setTitle(R.string.pick_days_title)
                .setMultiChoiceItems(dayLabels, checked, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        checked[which] = isChecked;
                    }
                })
                .setPositiveButton(R.string.ok_button, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Set<Integer> days = new HashSet<Integer>();
                        for (int i = 0; i < checked.length; i++) {
                            if (checked[i]) days.add(i + 1);
                        }
                        if (days.isEmpty()) {
                            Toast.makeText(AlarmEditActivity.this, R.string.msg_add_at_least_one_range, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        // Adding a trigger is always allowed, regardless of lock state.
                        triggers.add(new AlarmTrigger(pickedMinuteOfDay, days));
                        pickedMinuteOfDay = -1;
                        renderTriggers();
                    }
                })
                .setNegativeButton(R.string.cancel_button, null)
                .show();
    }

    private void renderTriggers() {
        triggersContainer.removeAllViews();
        for (final AlarmTrigger t : triggers) {
            View row = LayoutInflater.from(this).inflate(R.layout.list_item_time_range, triggersContainer, false);
            TextView txtRange = (TextView) row.findViewById(R.id.txtRange);
            Button btnRemove = (Button) row.findViewById(R.id.btnRemoveRange);
            txtRange.setText(t.format());
            btnRemove.setEnabled(editingAllowed);
            btnRemove.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!lockScheduleStorage.isEditingAllowed()) {
                        Toast.makeText(AlarmEditActivity.this, R.string.msg_alarms_locked, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    triggers.remove(t);
                    renderTriggers();
                }
            });
            triggersContainer.addView(row);
        }
    }

    private void onSelectBarcodesClicked() {
        final List<RegisteredBarcode> all = barcodesStorage.loadBarcodes();
        if (all.isEmpty()) {
            Toast.makeText(this, R.string.msg_no_barcodes_to_select, Toast.LENGTH_LONG).show();
            return;
        }
        final String[] labels = new String[all.size()];
        final String[] ids = new String[all.size()];
        final boolean[] checked = new boolean[all.size()];
        for (int i = 0; i < all.size(); i++) {
            labels[i] = all.get(i).label;
            ids[i] = all.get(i).id;
            checked[i] = selectedBarcodeIds.contains(ids[i]);
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.select_barcodes_button)
                .setMultiChoiceItems(labels, checked, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        checked[which] = isChecked;
                    }
                })
                .setPositiveButton(R.string.ok_button, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        selectedBarcodeIds.clear();
                        for (int i = 0; i < checked.length; i++) {
                            if (checked[i]) selectedBarcodeIds.add(ids[i]);
                        }
                    }
                })
                .setNegativeButton(R.string.cancel_button, null)
                .show();
    }

    private void onSelectAffectedBlocksClicked() {
        final List<Block> allBlocks = blocksStorage.loadBlocks();
        if (allBlocks.isEmpty()) {
            Toast.makeText(this, R.string.msg_no_blocks_to_select, Toast.LENGTH_LONG).show();
            return;
        }
        final String[] labels = new String[allBlocks.size()];
        final String[] blockIds = new String[allBlocks.size()];
        final boolean[] checked = new boolean[allBlocks.size()];
        for (int i = 0; i < allBlocks.size(); i++) {
            labels[i] = allBlocks.get(i).name;
            blockIds[i] = allBlocks.get(i).id;
            checked[i] = selectedBlockIds.contains(blockIds[i]);
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.select_blocks_button)
                .setMultiChoiceItems(labels, checked, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        checked[which] = isChecked;
                    }
                })
                .setPositiveButton(R.string.ok_button, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        selectedBlockIds.clear();
                        for (int i = 0; i < checked.length; i++) {
                            if (checked[i]) selectedBlockIds.add(blockIds[i]);
                        }
                    }
                })
                .setNegativeButton(R.string.cancel_button, null)
                .show();
    }

    private void onSaveClicked() {
        String name = editAlarmName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.msg_enter_alarm_name, Toast.LENGTH_SHORT).show();
            return;
        }
        if (triggers.isEmpty()) {
            Toast.makeText(this, R.string.msg_add_at_least_one_trigger, Toast.LENGTH_LONG).show();
            return;
        }
        if (selectedBarcodeIds.isEmpty()) {
            Toast.makeText(this, R.string.msg_select_at_least_one_barcode, Toast.LENGTH_LONG).show();
            return;
        }

        List<Alarm> alarms = alarmsStorage.loadAlarms();
        Alarm target = null;
        for (Alarm a : alarms) {
            if (a.id.equals(alarmId)) {
                target = a;
                break;
            }
        }
        if (target == null) {
            // Creating a brand new Alarm is always allowed, regardless of lock state.
            target = new Alarm();
            target.id = UUID.randomUUID().toString();
            alarms.add(target);
        }
        target.name = name;
        target.triggers.clear();
        target.triggers.addAll(triggers);
        target.barcodeIds.clear();
        target.barcodeIds.addAll(selectedBarcodeIds);
        target.affectedBlockIds.clear();
        target.affectedBlockIds.addAll(selectedBlockIds);

        alarmsStorage.saveAlarms(alarms);
        AlarmScheduler.rescheduleAll(this);

        Toast.makeText(this, R.string.msg_alarm_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void onDeleteClicked() {
        if (alarmId == null) {
            return;
        }
        if (!lockScheduleStorage.isEditingAllowed()) {
            Toast.makeText(this, R.string.msg_alarms_locked, Toast.LENGTH_LONG).show();
            return;
        }
        List<Alarm> alarms = alarmsStorage.loadAlarms();
        for (int i = 0; i < alarms.size(); i++) {
            if (alarms.get(i).id.equals(alarmId)) {
                alarms.remove(i);
                break;
            }
        }
        alarmsStorage.saveAlarms(alarms);
        AlarmScheduler.rescheduleAll(this);
        finish();
    }
}
