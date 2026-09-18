package com.mujeer.floatingblocker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import java.util.Set;

/**
 * Appears in the system Share sheet from any browser. Extracts just the
 * domain from whatever was shared (never a specific page/path) and asks
 * for confirmation before adding it to the blacklist. Adding is always
 * allowed, same as everywhere else in the app.
 */
public class ShareWebsiteActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String sharedText = null;
        Intent intent = getIntent();
        if (intent != null && Intent.ACTION_SEND.equals(intent.getAction())) {
            sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
        }

        final String domain = DomainExtractor.extract(sharedText);
        if (domain == null || domain.isEmpty()) {
            Toast.makeText(this, R.string.msg_invalid_website, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_block_website_title)
                .setMessage(getString(R.string.confirm_block_website_message, domain))
                .setPositiveButton(R.string.confirm_block_website_yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        BlockedWebsitesStorage storage = new BlockedWebsitesStorage(ShareWebsiteActivity.this);
                        Set<String> domains = storage.loadDomains();
                        domains.add(domain);
                        storage.saveDomains(domains);
                        Toast.makeText(ShareWebsiteActivity.this, R.string.msg_website_added, Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .setNegativeButton(R.string.cancel_button, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        finish();
                    }
                })
                .show();
    }
}
