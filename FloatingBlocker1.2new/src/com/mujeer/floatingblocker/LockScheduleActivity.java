package com.mujeer.floatingblocker;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LockScheduleActivity extends Activity {

    private LockScheduleStorage storage;
    private TextView txtLockedMessage;
    private LinearLayout rangesContainer;
    private Button btnPickStart;
    private Button btnPickEnd;
    private Button btnAddRange;
    private Button btnSave;

    private final List<TimeRange> ranges = new ArrayList<TimeRange>();
    private int pickedStart = -1;
    private int pickedEnd = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lock_schedule);
        setTitle(R.string.lock_schedule_screen_title);

        storage = new LockScheduleStorage(this);

        txtLockedMessage = (TextView) findViewById(R.id.txtLockedMessage);
        rangesContainer = (LinearLayout) findViewById(R.id.rangesContainer);
        btnPickStart = (Button) findViewById(R.id.btnPickStart);
        btnPickEnd = (Button) findViewById(R.id.btnPickEnd);
        btnAddRange = (Button) findViewById(R.id.btnAddRange);
        btnSave = (Button) findViewById(R.id.btnSave);

        ranges.addAll(storage.loadRanges());

        btnPickStart.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { pickTime(true); }
        });
        btnPickEnd.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { pickTime(false); }
        });
        btnAddRange.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { onAddRangeClicked(); }
        });
        btnSave.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { onSaveClicked(); }
        });

        applyEditLockState();
        renderRanges();
    }

    private boolean isEditingAllowed() {
        return storage.isEditingAllowed();
    }

    private void applyEditLockState() {
        boolean allowed = isEditingAllowed();
        txtLockedMessage.setVisibility(allowed ? View.GONE : View.VISIBLE);
        btnPickStart.setEnabled(allowed);
        btnPickEnd.setEnabled(allowed);
        btnAddRange.setEnabled(allowed);
        btnSave.setEnabled(allowed);
    }

    private void pickTime(final boolean isStart) {
        Calendar now = Calendar.getInstance();
        new TimePickerDialog(this, new TimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(android.widget.TimePicker view, int hourOfDay, int minute) {
                int minutes = hourOfDay * 60 + minute;
                if (isStart) {
                    pickedStart = minutes;
                } else {
                    pickedEnd = minutes;
                }
            }
        }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false).show();
    }

    private void onAddRangeClicked() {
        if (pickedStart < 0 || pickedEnd < 0 || pickedStart == pickedEnd) {
            Toast.makeText(this, R.string.msg_add_at_least_one_range, Toast.LENGTH_SHORT).show();
            return;
        }
        pickDaysThenAdd();
    }

    private void pickDaysThenAdd() {
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
                            if (checked[i]) {
                                days.add(i + 1); // Calendar.SUNDAY == 1 .. SATURDAY == 7
                            }
                        }
                        if (days.isEmpty()) {
                            Toast.makeText(LockScheduleActivity.this, R.string.msg_add_at_least_one_range, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        ranges.add(new TimeRange(pickedStart, pickedEnd, days));
                        pickedStart = -1;
                        pickedEnd = -1;
                        renderRanges();
                    }
                })
                .setNegativeButton(R.string.cancel_button, null)
                .show();
    }

    private void renderRanges() {
        rangesContainer.removeAllViews();
        boolean allowed = isEditingAllowed();
        for (final TimeRange r : ranges) {
            View row = LayoutInflater.from(this).inflate(R.layout.list_item_time_range, rangesContainer, false);
            TextView txtRange = (TextView) row.findViewById(R.id.txtRange);
            Button btnRemove = (Button) row.findViewById(R.id.btnRemoveRange);
            txtRange.setText(r.format());
            btnRemove.setEnabled(allowed);
            btnRemove.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ranges.remove(r);
                    renderRanges();
                }
            });
            rangesContainer.addView(row);
        }
    }

    private void onSaveClicked() {
        if (ranges.isEmpty()) {
            Toast.makeText(this, R.string.msg_add_at_least_one_range, Toast.LENGTH_SHORT).show();
            return;
        }
        storage.saveRanges(ranges);
        Toast.makeText(this, R.string.msg_ranges_saved, Toast.LENGTH_SHORT).show();
        applyEditLockState();
        renderRanges();
    }
}
