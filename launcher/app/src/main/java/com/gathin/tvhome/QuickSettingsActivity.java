package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The panel the remote's Settings button opens when {@code settings_key_panel} is on (HomeKeyService):
 * a few glass rows under the Settings pill, over Home or whatever app is playing. Translucent and in a
 * task of its own, so the app behind keeps running and Back returns to it. Pressing Settings again
 * closes the panel.
 */
public final class QuickSettingsActivity extends Activity {
    private boolean screenshotWhenStopped;

    /** Keep the panel's navigation sounds in the same native sound-effect channel as Home. */
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
            if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP
                    || event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (wrapFocus(event.getKeyCode())) return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }


    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
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
        LinearLayout rows = findViewById(R.id.rows);
        addRow(rows, R.drawable.ic_settings, R.string.tv_settings, 0, SystemSettings.intent(this, Settings.ACTION_SETTINGS));
        addRow(rows, R.drawable.ic_wifi, R.string.quick_network, 0, SystemSettings.intent(this, Settings.ACTION_WIFI_SETTINGS));
        addRow(rows, R.drawable.ic_bluetooth, R.string.quick_bluetooth, 0,
                SystemSettings.accessoriesIntent(this));
        addRow(rows, R.drawable.ic_sound, R.string.quick_sound, 0, SystemSettings.soundIntent(this));
        addRow(rows, R.drawable.ic_picture, R.string.quick_picture, 0, SystemSettings.displayIntent(this));
        addRow(rows, R.drawable.ic_recent_apps, R.string.recents, 0,
                new Intent(this, RecentAppsActivity.class));
        // Keep utility actions together at the bottom, just above launcher-specific settings.
        addActionRow(rows, R.drawable.ic_screenshot, R.string.quick_screenshot, this::takeScreenshot);
        // Through Home, so it rebuilds with the new choices when Launcher settings closes.
        addRow(rows, R.drawable.ic_home_settings, R.string.launcher_settings, 0,
                new Intent(this, MainActivity.class).setAction(MainActivity.ACTION_LAUNCHER_SETTINGS));
        rows.getChildAt(0).requestFocus();
    }

    /** Settings pressed again while the panel is up. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        finish();
    }

    /** Anything else taking the screen (Home, an app, a screensaver) closes the panel. */
    @Override
    @SuppressWarnings("deprecation")
    protected void onPause() {
        super.onPause();
        finish();
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!screenshotWhenStopped) return;
        screenshotWhenStopped = false;
        Context app = getApplicationContext();
        if (!HomeKeyService.takeScreenshot()) {
            Toast.makeText(app, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    private void addRow(LinearLayout rows, int icon, int title, int status, Intent target) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        row.findViewById(R.id.dot).setVisibility(View.GONE);
        TextView hint = row.findViewById(R.id.status);
        if (status != 0) hint.setText(status);
        else hint.setVisibility(View.GONE);
        row.setOnClickListener(v -> {
            try {
                startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException | SecurityException e) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
            }
            finish();
        });
        rows.addView(row);
    }

    private void addActionRow(LinearLayout rows, int icon, int title, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        row.findViewById(R.id.dot).setVisibility(View.GONE);
        row.findViewById(R.id.status).setVisibility(View.GONE);
        row.setOnClickListener(v -> action.run());
        rows.addView(row);
    }

    /** Keep the short utility list circular, as TV users expect from a menu panel. */
    private boolean wrapFocus(int keyCode) {
        LinearLayout rows = findViewById(R.id.rows);
        View focused = rows.findFocus();
        if (focused == null) return false;

        View first = null;
        View last = null;
        for (int i = 0; i < rows.getChildCount(); i++) {
            View row = rows.getChildAt(i);
            if (!row.isFocusable()) continue;
            if (first == null) first = row;
            last = row;
        }
        if (first == null || last == null) return false;
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP && focused == first) {
            last.requestFocus();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && focused == last) {
            first.requestFocus();
            return true;
        }
        return false;
    }

    /** Close the glass panel first, then capture the app that was underneath it. */
    private void takeScreenshot() {
        screenshotWhenStopped = true;
        finish();
    }
}
