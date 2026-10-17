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

public class ConfirmationsListActivity extends Activity {

    public static final String EXTRA_CONFIRMATION_ID = "confirmation_id";

    private ConfirmationsStorage confirmationsStorage;
    private LockScheduleStorage lockScheduleStorage;
    private LinearLayout confirmationsContainer;
    private TextView txtLockedMessage;
    private Button btnAddConfirmation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_confirmations_list);
        setTitle(R.string.confirmations_screen_title);

        confirmationsStorage = new ConfirmationsStorage(this);
        lockScheduleStorage = new LockScheduleStorage(this);

        confirmationsContainer = (LinearLayout) findViewById(R.id.confirmationsContainer);
        txtLockedMessage = (TextView) findViewById(R.id.txtLockedMessage);
        btnAddConfirmation = (Button) findViewById(R.id.btnAddConfirmation);

        btnAddConfirmation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ConfirmationsListActivity.this, ConfirmationEditActivity.class));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean allowed = lockScheduleStorage.isEditingAllowed();
        txtLockedMessage.setVisibility(allowed ? View.GONE : View.VISIBLE);
        renderConfirmations(allowed);
    }

    private void renderConfirmations(final boolean editingAllowed) {
        confirmationsContainer.removeAllViews();
        List<Confirmation> confirmations = confirmationsStorage.loadConfirmations();
        for (final Confirmation c : confirmations) {
            View row = LayoutInflater.from(this).inflate(R.layout.list_item_confirmation, confirmationsContainer, false);
            TextView txtName = (TextView) row.findViewById(R.id.txtConfirmationName);
            TextView txtSubtitle = (TextView) row.findViewById(R.id.txtConfirmationSubtitle);
            Button btnDelete = (Button) row.findViewById(R.id.btnDeleteConfirmation);

            txtName.setText(c.name);
            txtSubtitle.setText(getString(R.string.confirmation_subtitle_format, c.triggers.size(), c.affectedBlockIds.size(), c.punishmentMinutes));
            btnDelete.setEnabled(editingAllowed);

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(ConfirmationsListActivity.this, ConfirmationEditActivity.class);
                    intent.putExtra(EXTRA_CONFIRMATION_ID, c.id);
                    startActivity(intent);
                }
            });

            btnDelete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!lockScheduleStorage.isEditingAllowed()) {
                        Toast.makeText(ConfirmationsListActivity.this, R.string.msg_confirmations_locked, Toast.LENGTH_LONG).show();
                        return;
                    }
                    new AlertDialog.Builder(ConfirmationsListActivity.this)
                            .setMessage(c.name)
                            .setPositiveButton(R.string.delete_confirmation_button, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    List<Confirmation> current = confirmationsStorage.loadConfirmations();
                                    for (int i = 0; i < current.size(); i++) {
                                        if (current.get(i).id.equals(c.id)) {
                                            current.remove(i);
                                            break;
                                        }
                                    }
                                    confirmationsStorage.saveConfirmations(current);
                                    ConfirmationScheduler.rescheduleAll(ConfirmationsListActivity.this);
                                    renderConfirmations(editingAllowed);
                                }
                            })
                            .setNegativeButton(R.string.cancel_button, null)
                            .show();
                }
            });

            confirmationsContainer.addView(row);
        }
    }
}
