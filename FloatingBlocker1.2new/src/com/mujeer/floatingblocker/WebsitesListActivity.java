package com.mujeer.floatingblocker;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Adding a domain is always allowed (only adds restriction). Removing one
 * is only allowed outside a locked Lock Schedule period - same pattern as
 * everything else in this app.
 */
public class WebsitesListActivity extends Activity {

    private BlockedWebsitesStorage websitesStorage;
    private LockScheduleStorage lockScheduleStorage;
    private TextView txtLockedMessage;
    private LinearLayout websitesContainer;
    private EditText editDomain;
    private Button btnAddDomain;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_websites_list);
        setTitle(R.string.websites_screen_title);

        websitesStorage = new BlockedWebsitesStorage(this);
        lockScheduleStorage = new LockScheduleStorage(this);

        txtLockedMessage = (TextView) findViewById(R.id.txtLockedMessage);
        websitesContainer = (LinearLayout) findViewById(R.id.websitesContainer);
        editDomain = (EditText) findViewById(R.id.editDomain);
        btnAddDomain = (Button) findViewById(R.id.btnAddDomain);

        btnAddDomain.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onAddDomainClicked();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean editingAllowed = lockScheduleStorage.isEditingAllowed();
        txtLockedMessage.setVisibility(editingAllowed ? View.GONE : View.VISIBLE);
        renderDomains(editingAllowed);
    }

    private void onAddDomainClicked() {
        String typed = editDomain.getText().toString();
        String domain = DomainExtractor.extract(typed);
        if (domain == null || domain.isEmpty()) {
            Toast.makeText(this, R.string.msg_invalid_website, Toast.LENGTH_SHORT).show();
            return;
        }
        Set<String> domains = websitesStorage.loadDomains();
        domains.add(domain);
        websitesStorage.saveDomains(domains);
        BlockEnforcer.reapplyAndReschedule(this);
        editDomain.setText("");
        Toast.makeText(this, R.string.msg_website_added, Toast.LENGTH_SHORT).show();
        renderDomains(lockScheduleStorage.isEditingAllowed());
    }

    private void renderDomains(final boolean editingAllowed) {
        websitesContainer.removeAllViews();
        List<String> domains = new ArrayList<String>(websitesStorage.loadDomains());
        Collections.sort(domains);

        for (final String domain : domains) {
            View row = LayoutInflater.from(this).inflate(R.layout.list_item_website, websitesContainer, false);
            TextView txtDomain = (TextView) row.findViewById(R.id.txtDomain);
            Button btnRemove = (Button) row.findViewById(R.id.btnRemoveDomain);

            txtDomain.setText(domain);
            btnRemove.setEnabled(editingAllowed);
            btnRemove.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!lockScheduleStorage.isEditingAllowed()) {
                        Toast.makeText(WebsitesListActivity.this, R.string.msg_websites_locked, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Set<String> current = websitesStorage.loadDomains();
                    current.remove(domain);
                    websitesStorage.saveDomains(current);
                    BlockEnforcer.reapplyAndReschedule(WebsitesListActivity.this);
                    renderDomains(editingAllowed);
                }
            });
            websitesContainer.addView(row);
        }
    }
}
