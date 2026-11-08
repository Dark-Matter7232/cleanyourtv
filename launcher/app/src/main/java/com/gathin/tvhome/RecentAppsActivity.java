package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.widget.LinearLayout;
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
        RecentAppsPanel.populate(this, rows, new RecentAppsPanel.Actions() {
            @Override
            public void open(Intent selected) {
                try {
                    startActivity(selected.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
                    finish();
                } catch (RuntimeException unavailable) {
                    Toast.makeText(RecentAppsActivity.this, R.string.recent_apps_unavailable,
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void closeAll() {
                long before = Recents.availableMemory(RecentAppsActivity.this);
                Recents.closeAll(RecentAppsActivity.this);
                finish();
                handler.postDelayed(() -> {
                    long after = Recents.availableMemory(RecentAppsActivity.this);
                    long freed = Math.max(0L, after - before) / (1024L * 1024L);
                    Toast.makeText(RecentAppsActivity.this,
                            getString(R.string.freed_memory, freed), Toast.LENGTH_SHORT).show();
                }, 350L);
            }
        });

        for (int i = 0; i < rows.getChildCount(); i++) {
            View row = rows.getChildAt(i);
            if (row.isFocusable()) {
                row.requestFocus();
                break;
            }
        }
    }

}
