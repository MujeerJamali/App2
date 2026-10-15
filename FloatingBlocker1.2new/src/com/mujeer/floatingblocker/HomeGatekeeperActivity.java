package com.mujeer.floatingblocker;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Forced, Device-Owner-locked answer to Android's CATEGORY_HOME resolution
 * (registered via BlockEnforcer.applyHomeGatekeeperProtection). Every boot,
 * every Home press, every moment Android needs "something to show" with
 * nothing else in front, this Activity is the one thing the OS is
 * structurally obligated to invoke - unlike BOOT_COMPLETED receivers or
 * AlarmManager wake-ups, which an aggressive OEM "kill this app, block its
 * autostart" battery mode can suppress entirely without the phone itself
 * breaking. A phone cannot have NO home screen, so this is the one trigger
 * that class of battery mode can't take away without a much bigger,
 * OEM-visible failure (no home screen at all).
 *
 * Does the one check that matters, inline, depending on nothing
 * backgroundable: should Lock Schedule be locked right now? If so, hands
 * straight to KioskSessionActivity. If not, forwards to the device's real
 * launcher (captured once before this app ever took over HOME - see
 * RealLauncherStorage) so normal unlocked use is otherwise unaffected -
 * this Activity itself is never meant to be seen.
 */
public class HomeGatekeeperActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Belt-and-suspenders: this Activity is the one trigger point
        // guaranteed to run regardless of whether BOOT_COMPLETED/
        // AlarmManager ever got the chance to, so re-running full
        // enforcement right here means suspension state and the Kiosk
        // lock-task allowlist are never stale by the time the check below
        // runs, even after a gap where nothing else fired at all.
        try {
            BlockEnforcer.reapplyAndReschedule(this);
        } catch (Exception e) {
            // Best effort - the checks below still use whatever state is
            // already in place even if this particular refresh fails.
        }

        if (KioskAllowList.computeCurrentAllowList(this).kioskLayerActive) {
            startActivity(new Intent(this, KioskSessionActivity.class));
            finish();
            return;
        }

        ComponentName realLauncher = new RealLauncherStorage(this).loadRealLauncher();
        if (realLauncher != null && !realLauncher.getPackageName().equals(getPackageName())) {
            try {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_HOME);
                intent.setComponent(realLauncher);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
                return;
            } catch (Exception e) {
                // The stored launcher no longer resolves (uninstalled, disabled, or
                // changed outside this flow) - fall through to the last-resort app
                // list below instead of leaving the user stranded on a blank screen.
            }
        }

        showFallbackAppList();
    }

    /**
     * Last-resort safety net: should only ever be reached if
     * RealLauncherStorage never captured anything, or what it captured no
     * longer resolves. A plain, unrestricted list of every installed app -
     * not pinned, not gated - so the user always has a way off this screen
     * regardless of what went wrong with launcher detection.
     */
    private void showFallbackAppList() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = dpToPx(16);
        container.setPadding(padding, padding, padding, padding);
        scroll.addView(container);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText(R.string.home_gatekeeper_fallback_title);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setGravity(Gravity.CENTER);
        container.addView(title);

        PackageManager pm = getPackageManager();
        List<String> packages = new ArrayList<String>(KioskAllowList.getAllInstalledLaunchablePackages(this));
        Collections.sort(packages, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return labelForPackage(a).compareToIgnoreCase(labelForPackage(b));
            }
        });

        for (final String pkg : packages) {
            Button button = new Button(this);
            button.setText(labelForPackage(pkg));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.topMargin = dpToPx(8);
            button.setLayoutParams(params);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent launchIntent = getPackageManager().getLaunchIntentForPackage(pkg);
                    if (launchIntent != null) {
                        startActivity(launchIntent);
                    }
                }
            });
            container.addView(button);
        }
    }

    private String labelForPackage(String pkg) {
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(pkg, 0);
            return getPackageManager().getApplicationLabel(info).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
