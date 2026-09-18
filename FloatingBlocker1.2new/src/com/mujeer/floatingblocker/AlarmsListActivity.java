package com.mujeer.floatingblocker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class AlarmsListActivity extends Activity {

    public static final String EXTRA_ALARM_ID = "alarm_id";

    private AlarmsStorage alarmsStorage;
    private LockScheduleStorage lockScheduleStorage;
    private LinearLayout alarmsContainer;
    private TextView txtLockedMessage;
    private Button btnAddAlarm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alarms_list);
        setTitle(R.string.alarms_screen_title);

        alarmsStorage = new AlarmsStorage(this);
        lockScheduleStorage = new LockScheduleStorage(this);

        alarmsContainer = (LinearLayout) findViewById(R.id.alarmsContainer);
        txtLockedMessage = (TextView) findViewById(R.id.txtLockedMessage);
        btnAddAlarm = (Button) findViewById(R.id.btnAddAlarm);

        btnAddAlarm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(AlarmsListActivity.this, AlarmEditActivity.class));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean allowed = lockScheduleStorage.isEditingAllowed();
        txtLockedMessage.setVisibility(allowed ? View.GONE : View.VISIBLE);
        renderAlarms(allowed);
    }

    private void renderAlarms(final boolean editingAllowed) {
        alarmsContainer.removeAllViews();
        List<Alarm> alarms = alarmsStorage.loadAlarms();
        for (final Alarm a : alarms) {
            View row = LayoutInflater.from(this).inflate(R.layout.list_item_alarm, alarmsContainer, false);
            TextView txtName = (TextView) row.findViewById(R.id.txtAlarmName);
            TextView txtSubtitle = (TextView) row.findViewById(R.id.txtAlarmSubtitle);
            Button btnDelete = (Button) row.findViewById(R.id.btnDeleteAlarm);

            txtName.setText(a.name);
            txtSubtitle.setText(getString(R.string.alarm_subtitle_format, a.triggers.size(), a.affectedBlockIds.size()));
            btnDelete.setEnabled(editingAllowed);

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(AlarmsListActivity.this, AlarmEditActivity.class);
                    intent.putExtra(EXTRA_ALARM_ID, a.id);
                    startActivity(intent);
                }
            });

            btnDelete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!lockScheduleStorage.isEditingAllowed()) {
                        Toast.makeText(AlarmsListActivity.this, R.string.msg_alarms_locked, Toast.LENGTH_LONG).show();
                        return;
                    }
                    new AlertDialog.Builder(AlarmsListActivity.this)
                            .setMessage(a.name)
                            .setPositiveButton(R.string.delete_alarm_button, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    List<Alarm> current = alarmsStorage.loadAlarms();
                                    for (int i = 0; i < current.size(); i++) {
                                        if (current.get(i).id.equals(a.id)) {
                                            current.remove(i);
                                            break;
                                        }
                                    }
                                    alarmsStorage.saveAlarms(current);
                                    AlarmScheduler.rescheduleAll(AlarmsListActivity.this);
                                    renderAlarms(editingAllowed);
                                }
                            })
                            .setNegativeButton(R.string.cancel_button, null)
                            .show();
                }
            });

            alarmsContainer.addView(row);
        }
    }
}
