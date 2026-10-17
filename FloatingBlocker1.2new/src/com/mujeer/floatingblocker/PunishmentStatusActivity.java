package com.mujeer.floatingblocker;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Read-only view of every Block currently carrying an Alarm-miss
 * punishment widen window (see BlockPunishmentStorage), so it's possible
 * to see exactly what's blocked, why, and until when - instead of only
 * finding out indirectly by noticing an app is still suspended past its
 * normal schedule.
 */
public class PunishmentStatusActivity extends Activity {

    private BlockPunishmentStorage punishmentStorage;
    private BlocksStorage blocksStorage;
    private LinearLayout punishmentsContainer;
    private TextView txtNoPunishments;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_punishments_list);
        setTitle(R.string.punishments_screen_title);

        punishmentStorage = new BlockPunishmentStorage(this);
        blocksStorage = new BlocksStorage(this);

        punishmentsContainer = (LinearLayout) findViewById(R.id.punishmentsContainer);
        txtNoPunishments = (TextView) findViewById(R.id.txtNoPunishments);
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderPunishments();
    }

    private void renderPunishments() {
        punishmentsContainer.removeAllViews();
        long now = System.currentTimeMillis();

        List<BlockPunishmentStorage.Window> windows = punishmentStorage.getAllWindows();
        // Already-expired windows aren't punishing anything any more - not
        // worth showing on a screen about what's currently in effect.
        for (int i = windows.size() - 1; i >= 0; i--) {
            if (windows.get(i).end <= now) {
                windows.remove(i);
            }
        }
        Collections.sort(windows, new Comparator<BlockPunishmentStorage.Window>() {
            @Override
            public int compare(BlockPunishmentStorage.Window a, BlockPunishmentStorage.Window b) {
                return Long.valueOf(a.start).compareTo(b.start);
            }
        });

        txtNoPunishments.setVisibility(windows.isEmpty() ? View.VISIBLE : View.GONE);

        List<Block> allBlocks = blocksStorage.loadBlocks();
        SimpleDateFormat fmt = new SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault());

        for (BlockPunishmentStorage.Window w : windows) {
            View row = LayoutInflater.from(this).inflate(R.layout.list_item_punishment, punishmentsContainer, false);
            TextView txtBlockName = (TextView) row.findViewById(R.id.txtPunishmentBlockName);
            TextView txtRange = (TextView) row.findViewById(R.id.txtPunishmentRange);
            TextView txtStatus = (TextView) row.findViewById(R.id.txtPunishmentStatus);

            txtBlockName.setText(blockName(allBlocks, w.blockId));
            txtRange.setText(fmt.format(new Date(w.start)) + "  to  " + fmt.format(new Date(w.end)));

            boolean activeNow = now >= w.start && now < w.end;
            txtStatus.setText(activeNow
                    ? getString(R.string.punishment_status_active)
                    : getString(R.string.punishment_status_upcoming));

            punishmentsContainer.addView(row);
        }
    }

    private String blockName(List<Block> allBlocks, String blockId) {
        for (Block b : allBlocks) {
            if (b.id.equals(blockId)) {
                return b.name;
            }
        }
        return getString(R.string.punishment_deleted_block);
    }
}
