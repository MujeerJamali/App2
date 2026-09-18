package com.mujeer.floatingblocker;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class HolidayBreakEditActivity extends Activity {

    private HolidayBreaksStorage holidayBreaksStorage;
    private BlocksStorage blocksStorage;
    private LockScheduleStorage lockScheduleStorage;

    private TextView txtLockedMessage;
    private EditText editBreakName;
    private Button btnPickStartDate;
    private Button btnPickStartTime;
    private Button btnPickEndDate;
    private Button btnPickEndTime;
    private Button btnSelectBlocks;
    private Button btnSaveBreak;

    private final Calendar startCal = Calendar.getInstance();
    private final Calendar endCal = Calendar.getInstance();
    private final Set<String> selectedBlockIds = new HashSet<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_holiday_break_edit);
        setTitle(R.string.holiday_break_edit_screen_title);

        holidayBreaksStorage = new HolidayBreaksStorage(this);
        blocksStorage = new BlocksStorage(this);
        lockScheduleStorage = new LockScheduleStorage(this);

        txtLockedMessage = (TextView) findViewById(R.id.txtLockedMessage);
        editBreakName = (EditText) findViewById(R.id.editBreakName);
        btnPickStartDate = (Button) findViewById(R.id.btnPickStartDate);
        btnPickStartTime = (Button) findViewById(R.id.btnPickStartTime);
        btnPickEndDate = (Button) findViewById(R.id.btnPickEndDate);
        btnPickEndTime = (Button) findViewById(R.id.btnPickEndTime);
        btnSelectBlocks = (Button) findViewById(R.id.btnSelectBlocks);
        btnSaveBreak = (Button) findViewById(R.id.btnSaveBreak);

        boolean creationAllowed = lockScheduleStorage.isEditingAllowed();
        txtLockedMessage.setVisibility(creationAllowed ? View.GONE : View.VISIBLE);
        editBreakName.setEnabled(creationAllowed);
        btnPickStartDate.setEnabled(creationAllowed);
        btnPickStartTime.setEnabled(creationAllowed);
        btnPickEndDate.setEnabled(creationAllowed);
        btnPickEndTime.setEnabled(creationAllowed);
        btnSelectBlocks.setEnabled(creationAllowed);
        btnSaveBreak.setEnabled(creationAllowed);

        updateButtonLabels();

        btnPickStartDate.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { pickDate(startCal); }
        });
        btnPickStartTime.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { pickTime(startCal); }
        });
        btnPickEndDate.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { pickDate(endCal); }
        });
        btnPickEndTime.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { pickTime(endCal); }
        });
        btnSelectBlocks.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { onSelectBlocksClicked(); }
        });
        btnSaveBreak.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { onSaveClicked(); }
        });
    }

    private void pickDate(final Calendar target) {
        new DatePickerDialog(this, new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(android.widget.DatePicker view, int year, int month, int dayOfMonth) {
                target.set(Calendar.YEAR, year);
                target.set(Calendar.MONTH, month);
                target.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                updateButtonLabels();
            }
        }, target.get(Calendar.YEAR), target.get(Calendar.MONTH), target.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void pickTime(final Calendar target) {
        new TimePickerDialog(this, new TimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(android.widget.TimePicker view, int hourOfDay, int minute) {
                target.set(Calendar.HOUR_OF_DAY, hourOfDay);
                target.set(Calendar.MINUTE, minute);
                target.set(Calendar.SECOND, 0);
                updateButtonLabels();
            }
        }, target.get(Calendar.HOUR_OF_DAY), target.get(Calendar.MINUTE), false).show();
    }

    private void updateButtonLabels() {
        btnPickStartDate.setText(formatDate(startCal) + "\n" + getString(R.string.pick_date_button));
        btnPickStartTime.setText(formatTime(startCal) + "\n" + getString(R.string.pick_time_button));
        btnPickEndDate.setText(formatDate(endCal) + "\n" + getString(R.string.pick_date_button));
        btnPickEndTime.setText(formatTime(endCal) + "\n" + getString(R.string.pick_time_button));
    }

    private String formatDate(Calendar c) {
        return new java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault()).format(c.getTime());
    }

    private String formatTime(Calendar c) {
        return new java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(c.getTime());
    }

    private void onSelectBlocksClicked() {
        final List<Block> allBlocks = blocksStorage.loadBlocks();
        final String[] labels = new String[allBlocks.size()];
        final String[] blockIds = new String[allBlocks.size()];
        final boolean[] checked = new boolean[allBlocks.size()];
        for (int i = 0; i < allBlocks.size(); i++) {
            labels[i] = allBlocks.get(i).name;
            blockIds[i] = allBlocks.get(i).id;
            checked[i] = selectedBlockIds.contains(blockIds[i]);
        }

        if (allBlocks.isEmpty()) {
            Toast.makeText(this, R.string.msg_no_blocks_to_select, Toast.LENGTH_LONG).show();
            return;
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
                            if (checked[i]) {
                                selectedBlockIds.add(blockIds[i]);
                            }
                        }
                    }
                })
                .setNegativeButton(R.string.cancel_button, null)
                .show();
    }

    private void onSaveClicked() {
        if (!lockScheduleStorage.isEditingAllowed()) {
            Toast.makeText(this, R.string.msg_holiday_breaks_locked, Toast.LENGTH_LONG).show();
            return;
        }
        String name = editBreakName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.msg_enter_break_name, Toast.LENGTH_SHORT).show();
            return;
        }
        if (endCal.getTimeInMillis() <= startCal.getTimeInMillis()) {
            Toast.makeText(this, R.string.msg_break_end_before_start, Toast.LENGTH_LONG).show();
            return;
        }
        if (selectedBlockIds.isEmpty()) {
            Toast.makeText(this, R.string.msg_select_at_least_one_block, Toast.LENGTH_LONG).show();
            return;
        }

        HolidayBreak h = new HolidayBreak();
        h.id = UUID.randomUUID().toString();
        h.name = name;
        h.startMillis = startCal.getTimeInMillis();
        h.endMillis = endCal.getTimeInMillis();
        h.affectedBlockIds.addAll(selectedBlockIds);

        List<HolidayBreak> breaks = holidayBreaksStorage.loadBreaks();
        breaks.add(h);
        holidayBreaksStorage.saveBreaks(breaks);
        BlockEnforcer.reapplyAndReschedule(this);

        Toast.makeText(this, R.string.msg_break_saved, Toast.LENGTH_SHORT).show();
        finish();
    }
}
