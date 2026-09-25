package com.mujeer.floatingblocker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.UUID;

/**
 * Registering a new barcode is always allowed (only adds a new dismiss
 * option). Removing one is only allowed outside a locked Lock Schedule
 * period - same pattern as everything else in this app.
 */
public class BarcodesListActivity extends Activity {

    private static final int REQUEST_SCAN_NEW = 601;

    private RegisteredBarcodesStorage barcodesStorage;
    private LockScheduleStorage lockScheduleStorage;
    private TextView txtLockedMessage;
    private LinearLayout barcodesContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_barcodes_list);
        setTitle(R.string.barcodes_screen_title);

        barcodesStorage = new RegisteredBarcodesStorage(this);
        lockScheduleStorage = new LockScheduleStorage(this);

        txtLockedMessage = (TextView) findViewById(R.id.txtLockedMessage);
        barcodesContainer = (LinearLayout) findViewById(R.id.barcodesContainer);
        Button btnScanNewBarcode = (Button) findViewById(R.id.btnScanNewBarcode);

        btnScanNewBarcode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(new Intent(BarcodesListActivity.this, BarcodeScanActivity.class), REQUEST_SCAN_NEW);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean editingAllowed = lockScheduleStorage.isEditingAllowed();
        txtLockedMessage.setVisibility(editingAllowed ? View.GONE : View.VISIBLE);
        renderBarcodes(editingAllowed);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SCAN_NEW && resultCode == RESULT_OK && data != null) {
            String value = data.getStringExtra(BarcodeScanActivity.EXTRA_DECODED_VALUE);
            if (value != null) {
                promptForLabelAndSave(value);
            }
        }
    }

    private void promptForLabelAndSave(final String value) {
        final EditText input = new EditText(this);
        input.setHint(R.string.barcode_label_hint);
        new AlertDialog.Builder(this)
                .setTitle(R.string.barcode_scanned_title)
                .setView(input)
                .setPositiveButton(R.string.confirm_block_website_yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        RegisteredBarcode b = new RegisteredBarcode();
                        b.id = UUID.randomUUID().toString();
                        b.label = input.getText().toString().trim();
                        if (b.label.isEmpty()) {
                            b.label = getString(R.string.barcode_default_label);
                        }
                        b.value = value;
                        List<RegisteredBarcode> all = barcodesStorage.loadBarcodes();
                        all.add(b);
                        barcodesStorage.saveBarcodes(all);
                        Toast.makeText(BarcodesListActivity.this, R.string.msg_barcode_registered, Toast.LENGTH_SHORT).show();
                        renderBarcodes(lockScheduleStorage.isEditingAllowed());
                    }
                })
                .setNegativeButton(R.string.confirm_delete_safety_cancel, null)
                .show();
    }

    private void renderBarcodes(final boolean editingAllowed) {
        barcodesContainer.removeAllViews();
        List<RegisteredBarcode> barcodes = barcodesStorage.loadBarcodes();

        for (final RegisteredBarcode barcode : barcodes) {
            View row = LayoutInflater.from(this).inflate(R.layout.list_item_barcode, barcodesContainer, false);
            TextView txtLabel = (TextView) row.findViewById(R.id.txtBarcodeLabel);
            TextView txtValue = (TextView) row.findViewById(R.id.txtBarcodeValue);
            Button btnRemove = (Button) row.findViewById(R.id.btnRemoveBarcode);

            txtLabel.setText(barcode.label);
            txtValue.setText(barcode.value);
            btnRemove.setEnabled(editingAllowed);
            btnRemove.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!lockScheduleStorage.isEditingAllowed()) {
                        Toast.makeText(BarcodesListActivity.this, R.string.msg_barcodes_locked, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    List<RegisteredBarcode> all = barcodesStorage.loadBarcodes();
                    for (int i = 0; i < all.size(); i++) {
                        if (all.get(i).id.equals(barcode.id)) {
                            all.remove(i);
                            break;
                        }
                    }
                    barcodesStorage.saveBarcodes(all);
                    renderBarcodes(editingAllowed);
                }
            });
            barcodesContainer.addView(row);
        }
    }
}
