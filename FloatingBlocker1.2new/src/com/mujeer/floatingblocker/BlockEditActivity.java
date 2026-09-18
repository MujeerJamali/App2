package com.mujeer.floatingblocker;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Editing rules (gated by LockScheduleStorage):
 *   ALWAYS allowed, regardless of lock state - these only ever ADD
 *   restriction: adding a time range, adding an app to the block list,
 *   creating a brand new Block (see BlocksListActivity), renaming a Block.
 *
 *   ONLY allowed while NOT currently locked - these REMOVE restriction:
 *   removing a time range, removing an app, deleting the whole Block.
 */
public class BlockEditActivity extends Activity {

    private BlocksStorage blocksStorage;
    private LockScheduleStorage lockScheduleStorage;

    private EditText editBlockName;
    private TextView txtLockedMessage;
    private LinearLayout rangesContainer;
    private LinearLayout appsContainer;
    private Button btnPickStart;
    private Button btnPickEnd;
    private Button btnAddRange;
    private Button btnAddApp;
    private Button btnSave;
    private Button btnDeleteBlock;

    private String blockId; // null if creating a new Block
    private final List<TimeRange> ranges = new ArrayList<TimeRange>();
    private final Set<String> blockedPackages = new LinkedHashSet<String>();
    private int pickedStart = -1;
    private int pickedEnd = -1;
    private boolean editingAllowed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_block_edit);
        setTitle(R.string.block_edit_screen_title);

        blocksStorage = new BlocksStorage(this);
        lockScheduleStorage = new LockScheduleStorage(this);

        editBlockName = (EditText) findViewById(R.id.editBlockName);
        txtLockedMessage = (TextView) findViewById(R.id.txtLockedMessage);
        rangesContainer = (LinearLayout) findViewById(R.id.rangesContainer);
        appsContainer = (LinearLayout) findViewById(R.id.appsContainer);
        btnPickStart = (Button) findViewById(R.id.btnPickStart);
        btnPickEnd = (Button) findViewById(R.id.btnPickEnd);
        btnAddRange = (Button) findViewById(R.id.btnAddRange);
        btnAddApp = (Button) findViewById(R.id.btnAddApp);
        btnSave = (Button) findViewById(R.id.btnSave);
        btnDeleteBlock = (Button) findViewById(R.id.btnDeleteBlock);

        blockId = getIntent().getStringExtra(BlocksListActivity.EXTRA_BLOCK_ID);
        loadExistingBlockIfAny();

        editingAllowed = lockScheduleStorage.isEditingAllowed();
        applyEditLockState();

        btnPickStart.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { pickTime(true); }
        });
        btnPickEnd.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { pickTime(false); }
        });
        btnAddRange.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { onAddRangeClicked(); }
        });
        btnAddApp.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { onAddAppClicked(); }
        });
        btnSave.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { onSaveClicked(); }
        });
        btnDeleteBlock.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { onDeleteBlockClicked(); }
        });

        if (blockId == null) {
            btnDeleteBlock.setVisibility(View.GONE);
        }

        renderRanges();
        renderApps();
    }

    private void loadExistingBlockIfAny() {
        if (blockId == null) {
            return;
        }
        for (Block b : blocksStorage.loadBlocks()) {
            if (b.id.equals(blockId)) {
                editBlockName.setText(b.name);
                ranges.addAll(b.ranges);
                blockedPackages.addAll(b.blockedPackages);
                return;
            }
        }
    }

    private void applyEditLockState() {
        // Only actions that REMOVE restriction are gated. Adding, and the
        // Save button itself (since additions must always be saveable),
        // stay enabled regardless of lock state.
        txtLockedMessage.setVisibility(editingAllowed ? View.GONE : View.VISIBLE);
        btnDeleteBlock.setEnabled(editingAllowed);
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
                            Toast.makeText(BlockEditActivity.this, R.string.msg_add_at_least_one_range, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        // Adding a range is always allowed, regardless of lock state.
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
        for (final TimeRange r : ranges) {
            View row = LayoutInflater.from(this).inflate(R.layout.list_item_time_range, rangesContainer, false);
            TextView txtRange = (TextView) row.findViewById(R.id.txtRange);
            Button btnRemove = (Button) row.findViewById(R.id.btnRemoveRange);
            txtRange.setText(r.format());
            btnRemove.setEnabled(editingAllowed);
            btnRemove.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!lockScheduleStorage.isEditingAllowed()) {
                        Toast.makeText(BlockEditActivity.this, R.string.msg_blocks_locked, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    ranges.remove(r);
                    renderRanges();
                }
            });
            rangesContainer.addView(row);
        }
    }

    private void onAddAppClicked() {
        List<InstalledApp> apps = getInstalledLaunchableApps();
        final String[] labels = new String[apps.size()];
        final String[] packageNames = new String[apps.size()];
        final boolean[] checked = new boolean[apps.size()];
        final Set<String> preExisting = new HashSet<String>(blockedPackages);
        for (int i = 0; i < apps.size(); i++) {
            labels[i] = apps.get(i).label;
            packageNames[i] = apps.get(i).packageName;
            checked[i] = blockedPackages.contains(packageNames[i]);
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.add_app_button)
                .setMultiChoiceItems(labels, checked, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        checked[which] = isChecked;
                    }
                })
                .setPositiveButton(R.string.ok_button, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        boolean editAllowedNow = lockScheduleStorage.isEditingAllowed();
                        boolean anySkipped = false;
                        for (int i = 0; i < checked.length; i++) {
                            String pkg = packageNames[i];
                            if (checked[i]) {
                                blockedPackages.add(pkg); // adding is always allowed
                            } else if (preExisting.contains(pkg)) {
                                if (editAllowedNow) {
                                    blockedPackages.remove(pkg);
                                } else {
                                    anySkipped = true; // removal not allowed right now - keep it
                                }
                            }
                        }
                        if (anySkipped) {
                            Toast.makeText(BlockEditActivity.this, R.string.msg_some_removals_skipped, Toast.LENGTH_LONG).show();
                        }
                        renderApps();
                    }
                })
                .setNegativeButton(R.string.cancel_button, null)
                .show();
    }

    private void renderApps() {
        appsContainer.removeAllViews();
        PackageManager pm = getPackageManager();
        for (final String pkg : blockedPackages) {
            View row = LayoutInflater.from(this).inflate(R.layout.list_item_app, appsContainer, false);
            TextView txtAppName = (TextView) row.findViewById(R.id.txtAppName);
            Button btnRemove = (Button) row.findViewById(R.id.btnRemoveApp);
            txtAppName.setText(labelForPackage(pm, pkg));
            btnRemove.setEnabled(editingAllowed);
            btnRemove.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!lockScheduleStorage.isEditingAllowed()) {
                        Toast.makeText(BlockEditActivity.this, R.string.msg_blocks_locked, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    blockedPackages.remove(pkg);
                    renderApps();
                }
            });
            appsContainer.addView(row);
        }
    }

    private String labelForPackage(PackageManager pm, String pkg) {
        try {
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            return pm.getApplicationLabel(info).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private void onSaveClicked() {
        String name = editBlockName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.msg_enter_block_name, Toast.LENGTH_SHORT).show();
            return;
        }
        List<Block> blocks = blocksStorage.loadBlocks();
        Block target = null;
        for (Block b : blocks) {
            if (b.id.equals(blockId)) {
                target = b;
                break;
            }
        }
        boolean isNewBlock = (target == null);
        if (isNewBlock) {
            // Creating a brand new Block is always allowed, regardless of lock state.
            target = new Block();
            target.id = UUID.randomUUID().toString();
            blocks.add(target);
        }
        target.name = name;
        target.ranges.clear();
        target.ranges.addAll(ranges);
        target.blockedPackages.clear();
        target.blockedPackages.addAll(blockedPackages);

        blocksStorage.saveBlocks(blocks);

        if (isNewBlock) {
            // Newly created Blocks are automatically covered by every
            // existing Holiday Break too, so old breaks don't need to be
            // manually re-edited every time a new Block is added.
            HolidayBreaksStorage holidayBreaksStorage = new HolidayBreaksStorage(this);
            List<HolidayBreak> existingBreaks = holidayBreaksStorage.loadBreaks();
            for (HolidayBreak h : existingBreaks) {
                h.affectedBlockIds.add(target.id);
            }
            holidayBreaksStorage.saveBreaks(existingBreaks);
        }

        BlockEnforcer.reapplyAndReschedule(this);
        Toast.makeText(this, R.string.msg_ranges_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void onDeleteBlockClicked() {
        if (blockId == null) {
            return;
        }
        if (!lockScheduleStorage.isEditingAllowed()) {
            Toast.makeText(this, R.string.msg_blocks_locked, Toast.LENGTH_LONG).show();
            return;
        }
        List<Block> blocks = blocksStorage.loadBlocks();
        for (int i = 0; i < blocks.size(); i++) {
            if (blocks.get(i).id.equals(blockId)) {
                blocks.remove(i);
                break;
            }
        }
        blocksStorage.saveBlocks(blocks);
        BlockEnforcer.reapplyAndReschedule(this);
        finish();
    }

    private static class InstalledApp {
        String label;
        String packageName;
    }

    private List<InstalledApp> getInstalledLaunchableApps() {
        PackageManager pm = getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(mainIntent, 0);

        List<InstalledApp> apps = new ArrayList<InstalledApp>();
        Set<String> seenPackages = new HashSet<String>();
        String ownPackage = getPackageName();
        for (ResolveInfo info : resolveInfos) {
            String pkg = info.activityInfo.packageName;
            if (pkg.equals(ownPackage) || seenPackages.contains(pkg)) {
                continue;
            }
            seenPackages.add(pkg);
            InstalledApp app = new InstalledApp();
            app.packageName = pkg;
            app.label = info.loadLabel(pm).toString();
            apps.add(app);
        }
        Collections.sort(apps, new Comparator<InstalledApp>() {
            @Override
            public int compare(InstalledApp a, InstalledApp b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        return apps;
    }
}
