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

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Creating a Holiday Break is NOT gated here - whether it's actually
 * allowed depends on the start date/time the user is about to pick
 * (Lock Schedule locked "now" doesn't matter if the chosen start is on a
 * later app-day - see HolidayBreakEditActivity.isOnLaterAppDay), which
 * isn't known until they're on that screen choosing it. So the Add
 * button here always navigates there; HolidayBreakEditActivity is the
 * only place that actually decides and enforces this. Cancelling one
 * early is always allowed any time, since that restores restriction
 * sooner rather than removing it.
 */
public class HolidayBreaksListActivity extends Activity {

    private HolidayBreaksStorage holidayBreaksStorage;
    private BlocksStorage blocksStorage;
    private LinearLayout breaksContainer;
    private Button btnAddHolidayBreak;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_holiday_breaks_list);
        setTitle(R.string.holiday_breaks_screen_title);

        holidayBreaksStorage = new HolidayBreaksStorage(this);
        blocksStorage = new BlocksStorage(this);

        breaksContainer = (LinearLayout) findViewById(R.id.breaksContainer);
        btnAddHolidayBreak = (Button) findViewById(R.id.btnAddHolidayBreak);

        btnAddHolidayBreak.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(HolidayBreaksListActivity.this, HolidayBreakEditActivity.class));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderBreaks();
    }

    private void renderBreaks() {
        breaksContainer.removeAllViews();
        List<HolidayBreak> breaks = holidayBreaksStorage.loadBreaks();
        Collections.sort(breaks, new Comparator<HolidayBreak>() {
            @Override
            public int compare(HolidayBreak a, HolidayBreak b) {
                return Long.valueOf(a.startMillis).compareTo(b.startMillis);
            }
        });
        List<Block> allBlocks = blocksStorage.loadBlocks();

        for (final HolidayBreak h : breaks) {
            View row = LayoutInflater.from(this).inflate(R.layout.list_item_holiday_break, breaksContainer, false);
            TextView txtRange = (TextView) row.findViewById(R.id.txtBreakRange);
            TextView txtBlocks = (TextView) row.findViewById(R.id.txtBreakBlocks);
            Button btnCancel = (Button) row.findViewById(R.id.btnCancelBreak);

            txtRange.setText(h.name);
            txtBlocks.setText(h.format() + "\n" + affectedBlockNames(h, allBlocks));

            btnCancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new AlertDialog.Builder(HolidayBreaksListActivity.this)
                            .setMessage(R.string.confirm_cancel_break_message)
                            .setPositiveButton(R.string.cancel_break_button, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    List<HolidayBreak> current = holidayBreaksStorage.loadBreaks();
                                    for (int i = 0; i < current.size(); i++) {
                                        if (current.get(i).id.equals(h.id)) {
                                            current.remove(i);
                                            break;
                                        }
                                    }
                                    holidayBreaksStorage.saveBreaks(current);
                                    BlockEnforcer.reapplyAndReschedule(HolidayBreaksListActivity.this);
                                    renderBreaks();
                                }
                            })
                            .setNegativeButton(R.string.cancel_button, null)
                            .show();
                }
            });

            breaksContainer.addView(row);
        }
    }

    private String affectedBlockNames(HolidayBreak h, List<Block> allBlocks) {
        StringBuilder sb = new StringBuilder();
        for (Block b : allBlocks) {
            if (h.affectedBlockIds.contains(b.id)) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(b.name);
            }
        }
        return sb.length() > 0 ? sb.toString() : "";
    }
}
