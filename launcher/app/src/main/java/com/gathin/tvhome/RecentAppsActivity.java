package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The Quick Access version of Recents. It owns the glass panel so it can be shown over the app
 * that opened Quick Access, instead of bringing the opaque Home activity to the front first.
 */
public final class RecentAppsActivity extends Activity {
    private final Handler handler = new Handler();
    private float density;

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            int effect = -1;
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    effect = SoundEffectConstants.NAVIGATION_UP;
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    effect = SoundEffectConstants.NAVIGATION_DOWN;
                    break;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    effect = SoundEffectConstants.NAVIGATION_LEFT;
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    effect = SoundEffectConstants.NAVIGATION_RIGHT;
                    break;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    effect = SoundEffectConstants.CLICK;
                    break;
                default:
                    break;
            }
            if (effect != -1) getWindow().getDecorView().playSoundEffect(effect);
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        density = getResources().getDisplayMetrics().density;
        setContentView(R.layout.activity_quick_settings);
        if (getResources().getBoolean(R.bool.rtl_menu_bar)) {
            View panel = findViewById(R.id.panel);
            android.widget.FrameLayout.LayoutParams position =
                    (android.widget.FrameLayout.LayoutParams) panel.getLayoutParams();
            position.gravity = android.view.Gravity.TOP | android.view.Gravity.LEFT;
            int inset = position.getMarginEnd();
            position.setMarginEnd(0);
            position.setMargins(inset, position.topMargin, 0, position.bottomMargin);
            panel.setLayoutParams(position);
        }
        findViewById(R.id.scrim).setOnClickListener(v -> finish());
        // Match MainActivity's Quick Access Recents popup. Its compact 230dp base width is what
        // keeps this overlay visually identical to the Home version; the old 360dp override made
        // the panel look stretched when it was opened over another app.
        View panel = findViewById(R.id.panel);
        android.widget.FrameLayout.LayoutParams panelPosition =
                (android.widget.FrameLayout.LayoutParams) panel.getLayoutParams();
        panelPosition.width = (int) (230 * density);
        panelPosition.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
        panel.setLayoutParams(panelPosition);
        populate(findViewById(R.id.rows));
    }

    @Override
    protected void onPause() {
        super.onPause();
        finish();
        overridePendingTransition(0, 0);
    }

    private void populate(LinearLayout rows) {
        PackageManager pm = getPackageManager();
        for (String pkg : Recents.recent(this, 6)) {
            try {
                ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
                Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
                if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
                if (launch == null || pm.resolveActivity(launch, 0) == null) continue;

                View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
                ImageView icon = row.findViewById(R.id.icon);
                icon.setImageDrawable(info.loadIcon(pm));
                icon.setImageTintList(null);
                ((TextView) row.findViewById(R.id.title)).setText(pm.getApplicationLabel(info));
                row.findViewById(R.id.dot).setVisibility(View.GONE);
                row.findViewById(R.id.status).setVisibility(View.GONE);
                final Intent selected = new Intent(launch);
                row.setOnClickListener(v -> {
                    try {
                        startActivity(selected.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
                        finish();
                    } catch (RuntimeException unavailable) {
                        Toast.makeText(this, R.string.recent_apps_unavailable, Toast.LENGTH_SHORT).show();
                    }
                });
                rows.addView(row);
            } catch (PackageManager.NameNotFoundException | RuntimeException unavailable) {
                // The app may have disappeared or become disabled while the panel was being built.
            }
        }

        boolean hasRecentRows = rows.getChildCount() > 0;
        if (!hasRecentRows) {
            View empty = getLayoutInflater().inflate(R.layout.item_input, rows, false);
            empty.findViewById(R.id.icon).setVisibility(View.GONE);
            ((TextView) empty.findViewById(R.id.title)).setText(
                    Recents.hasUsageAccess(this) ? R.string.recent_apps_empty : R.string.recents_need_access);
            empty.findViewById(R.id.dot).setVisibility(View.GONE);
            empty.findViewById(R.id.status).setVisibility(View.GONE);
            empty.setFocusable(false);
            empty.setClickable(false);
            rows.addView(empty);
        }

        addSeparator(rows);
        View closeAll = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) closeAll.findViewById(R.id.icon)).setImageResource(R.drawable.ic_apps);
        ((TextView) closeAll.findViewById(R.id.title)).setText(R.string.close_all);
        closeAll.findViewById(R.id.dot).setVisibility(View.GONE);
        closeAll.findViewById(R.id.status).setVisibility(View.GONE);
        closeAll.setOnClickListener(v -> {
            long before = Recents.availableMemory(this);
            Recents.closeAll(this);
            finish();
            handler.postDelayed(() -> {
                long after = Recents.availableMemory(this);
                long freed = Math.max(0L, after - before) / (1024L * 1024L);
                Toast.makeText(this, getString(R.string.freed_memory, freed), Toast.LENGTH_SHORT).show();
            }, 350L);
        });
        rows.addView(closeAll);

        for (int i = 0; i < rows.getChildCount(); i++) {
            View row = rows.getChildAt(i);
            if (row.isFocusable()) {
                row.requestFocus();
                break;
            }
        }
    }

    private void addSeparator(LinearLayout rows) {
        View line = new View(this);
        line.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, (int) density));
        int margin = (int) (6 * density);
        int side = (int) (12 * density);
        lp.setMargins(side, margin, side, margin);
        rows.addView(line, lp);
    }
}
