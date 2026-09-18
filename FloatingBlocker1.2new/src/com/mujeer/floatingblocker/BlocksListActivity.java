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

public class BlocksListActivity extends Activity {

    public static final String EXTRA_BLOCK_ID = "block_id";

    private BlocksStorage blocksStorage;
    private LockScheduleStorage lockScheduleStorage;
    private LinearLayout blocksContainer;
    private TextView txtLockedMessage;
    private Button btnAddBlock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blocks_list);
        setTitle(R.string.blocks_screen_title);

        blocksStorage = new BlocksStorage(this);
        lockScheduleStorage = new LockScheduleStorage(this);

        blocksContainer = (LinearLayout) findViewById(R.id.blocksContainer);
        txtLockedMessage = (TextView) findViewById(R.id.txtLockedMessage);
        btnAddBlock = (Button) findViewById(R.id.btnAddBlock);

        btnAddBlock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(BlocksListActivity.this, BlockEditActivity.class));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean allowed = lockScheduleStorage.isEditingAllowed();
        txtLockedMessage.setVisibility(allowed ? View.GONE : View.VISIBLE);
        // Creating a brand new Block only ever adds restriction, so it's always allowed.
        btnAddBlock.setEnabled(true);
        renderBlocks(allowed);
    }

    private void renderBlocks(final boolean editingAllowed) {
        blocksContainer.removeAllViews();
        List<Block> blocks = blocksStorage.loadBlocks();
        for (final Block b : blocks) {
            View row = LayoutInflater.from(this).inflate(R.layout.list_item_block, blocksContainer, false);
            TextView txtName = (TextView) row.findViewById(R.id.txtBlockName);
            TextView txtSubtitle = (TextView) row.findViewById(R.id.txtBlockSubtitle);
            Button btnDelete = (Button) row.findViewById(R.id.btnDeleteBlock);

            txtName.setText(b.name);
            txtSubtitle.setText(getString(R.string.block_subtitle_format, b.ranges.size(), b.blockedPackages.size()));
            btnDelete.setEnabled(editingAllowed);

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(BlocksListActivity.this, BlockEditActivity.class);
                    intent.putExtra(EXTRA_BLOCK_ID, b.id);
                    startActivity(intent);
                }
            });

            btnDelete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!lockScheduleStorage.isEditingAllowed()) {
                        Toast.makeText(BlocksListActivity.this, R.string.msg_blocks_locked, Toast.LENGTH_LONG).show();
                        return;
                    }
                    new AlertDialog.Builder(BlocksListActivity.this)
                            .setMessage(b.name)
                            .setPositiveButton(R.string.delete_block_button, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    List<Block> current = blocksStorage.loadBlocks();
                                    int idx = indexOfBlock(current, b.id);
                                    if (idx >= 0) {
                                        current.remove(idx);
                                        blocksStorage.saveBlocks(current);
                                        BlockEnforcer.reapplyAndReschedule(BlocksListActivity.this);
                                    }
                                    renderBlocks(editingAllowed);
                                }
                            })
                            .setNegativeButton(R.string.cancel_button, null)
                            .show();
                }
            });

            blocksContainer.addView(row);
        }
    }

    private int indexOfBlock(List<Block> blocks, String id) {
        for (int i = 0; i < blocks.size(); i++) {
            if (blocks.get(i).id.equals(id)) return i;
        }
        return -1;
    }
}
