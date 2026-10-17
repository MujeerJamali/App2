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
 * Same editing rules as AlarmEditActivity: adding a trigger or an
 * affected Block only ever adds restriction, so it's always allowed.
 * Removing one, or deleting the whole Confirmation, is only allowed
 * outside a locked Lock Schedule period.
 */
public class ConfirmationEditActivity extends Activity {

    private ConfirmationsStorage confirmationsStorage;
    private BlocksStorage blocksStorage;
    private LockScheduleStorage lockScheduleStorage;

    private EditText editConfirmationName;
    private TextView txtLockedMessage;
    private LinearLayout triggersContainer;
    private Button btnPickTriggerTime;
    private Button btnAddTrigger;
    private Button btnSelectAffectedBlocks;
    private EditText editPunishmentMinutes;
    private Button btnSaveConfirmation;
    private Button btnDeleteConfirmation;

    private String confirmationId; // null if creating a new Confirmation
    private final List<AlarmTrigger> triggers = new ArrayList<AlarmTrigger>();
    private final Set<String> selectedBlockIds = new LinkedHashSet<String>();
    private int pickedMinuteOfDay = -1;
    private boolean editingAllowed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_confirmation_edit);
        setTitle(R.string.confirmation_edit_screen_title);

        confirmationsStorage = new ConfirmationsStorage(this);
        blocksStorage = new BlocksStorage(this);
        lockScheduleStorage = new LockScheduleStorage(this);

        editConfirmationName = (EditText) findViewById(R.id.editConfirmationName);
        txtLockedMessage = (TextView) findViewById(R.id.txtLockedMessage);
        triggersContainer = (LinearLayout) findViewById(R.id.triggersContainer);
        btnPickTriggerTime = (Button) findViewById(R.id.btnPickTriggerTime);
        btnAddTrigger = (Button) findViewById(R.id.btnAddTrigger);
        btnSelectAffectedBlocks = (Button) findViewById(R.id.btnSelectAffectedBlocks);
        editPunishmentMinutes = (EditText) findViewById(R.id.editPunishmentMinutes);
        btnSaveConfirmation = (Button) findViewById(R.id.btnSaveConfirmation);
        btnDeleteConfirmation = (Button) findViewById(R.id.btnDeleteConfirmation);

        confirmationId = getIntent().getStringExtra(ConfirmationsListActivity.EXTRA_CONFIRMATION_ID);
        if (confirmationId == null) {
            editPunishmentMinutes.setText(String.valueOf(Confirmation.DEFAULT_PUNISHMENT_MINUTES));
        }
        loadExistingConfirmationIfAny();

        editingAllowed = lockScheduleStorage.isEditingAllowed();
        txtLockedMessage.setVisibility(editingAllowed ? View.GONE : View.VISIBLE);
        btnDeleteConfirmation.setEnabled(editingAllowed);
        if (confirmationId == null) {
            btnDeleteConfirmation.setVisibility(View.GONE);
        }

        btnPickTriggerTime.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { pickTriggerTime(); }
        });
        btnAddTrigger.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { onAddTriggerClicked(); }
        });
        btnSelectAffectedBlocks.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { onSelectAffectedBlocksClicked(); }
        });
        btnSaveConfirmation.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { onSaveClicked(); }
        });
        btnDeleteConfirmation.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { onDeleteClicked(); }
        });

        renderTriggers();
    }

    private void loadExistingConfirmationIfAny() {
        if (confirmationId == null) {
            return;
        }
        for (Confirmation c : confirmationsStorage.loadConfirmations()) {
            if (c.id.equals(confirmationId)) {
                editConfirmationName.setText(c.name);
                triggers.addAll(c.triggers);
                selectedBlockIds.addAll(c.affectedBlockIds);
                editPunishmentMinutes.setText(String.valueOf(c.punishmentMinutes));
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
                Toast.makeText(ConfirmationEditActivity.this, R.string.msg_time_picked, Toast.LENGTH_SHORT).show();
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
                            Toast.makeText(ConfirmationEditActivity.this, R.string.msg_add_at_least_one_range, Toast.LENGTH_SHORT).show();
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
                        Toast.makeText(ConfirmationEditActivity.this, R.string.msg_confirmations_locked, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    triggers.remove(t);
                    renderTriggers();
                }
            });
            triggersContainer.addView(row);
        }
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
        String name = editConfirmationName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.msg_enter_confirmation_name, Toast.LENGTH_SHORT).show();
            return;
        }
        if (triggers.isEmpty()) {
            Toast.makeText(this, R.string.msg_add_at_least_one_trigger, Toast.LENGTH_LONG).show();
            return;
        }

        List<Confirmation> confirmations = confirmationsStorage.loadConfirmations();
        Confirmation target = null;
        for (Confirmation c : confirmations) {
            if (c.id.equals(confirmationId)) {
                target = c;
                break;
            }
        }
        boolean isNewConfirmation = (target == null);
        if (isNewConfirmation) {
            // Creating a brand new Confirmation is always allowed, regardless of lock state.
            target = new Confirmation();
            target.id = UUID.randomUUID().toString();
            confirmations.add(target);
        }
        int punishmentMinutes;
        try {
            punishmentMinutes = Integer.parseInt(editPunishmentMinutes.getText().toString().trim());
        } catch (NumberFormatException e) {
            punishmentMinutes = 0;
        }
        if (punishmentMinutes <= 0) {
            punishmentMinutes = Confirmation.DEFAULT_PUNISHMENT_MINUTES;
        }

        target.name = name;
        target.triggers.clear();
        target.triggers.addAll(triggers);
        target.affectedBlockIds.clear();
        target.affectedBlockIds.addAll(selectedBlockIds);
        target.punishmentMinutes = punishmentMinutes;

        confirmationsStorage.saveConfirmations(confirmations);
        ConfirmationScheduler.rescheduleAll(this);

        if (isNewConfirmation) {
            // Newly created Confirmations are automatically covered by
            // every existing Holiday Break too, same as new Alarms/Blocks.
            HolidayBreaksStorage holidayBreaksStorage = new HolidayBreaksStorage(this);
            List<HolidayBreak> existingBreaks = holidayBreaksStorage.loadBreaks();
            for (HolidayBreak h : existingBreaks) {
                h.affectedConfirmationIds.add(target.id);
            }
            holidayBreaksStorage.saveBreaks(existingBreaks);
        }

        Toast.makeText(this, R.string.msg_confirmation_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void onDeleteClicked() {
        if (confirmationId == null) {
            return;
        }
        if (!lockScheduleStorage.isEditingAllowed()) {
            Toast.makeText(this, R.string.msg_confirmations_locked, Toast.LENGTH_LONG).show();
            return;
        }
        List<Confirmation> confirmations = confirmationsStorage.loadConfirmations();
        for (int i = 0; i < confirmations.size(); i++) {
            if (confirmations.get(i).id.equals(confirmationId)) {
                confirmations.remove(i);
                break;
            }
        }
        confirmationsStorage.saveConfirmations(confirmations);
        ConfirmationScheduler.rescheduleAll(this);
        finish();
    }
}
