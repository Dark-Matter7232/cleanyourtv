package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.Settings;

/** The standard settings intent, or, when nothing usable answers it, the first vendor settings entry
 *  from settings_fallback_components that is installed (Xiaomi China, TCL / FFALCON, Fire OS), else the
 *  AOSP TV settings app's main screen where it is installed. */
final class SystemSettings {
    private SystemSettings() {}

    static Intent intent(Context context, String action) {
        PackageManager pm = context.getPackageManager();
        Intent standard = new Intent(action);
        ResolveInfo resolved = pm.resolveActivity(standard, PackageManager.MATCH_DEFAULT_ONLY);
        // Fire OS resolves the standard actions to a CTS compliance stub that opens nothing;
        // treat it as missing so the vendor fallback runs.
        if (resolved != null && !resolved.activityInfo.name.contains("CTSDummy")) return standard;
        Intent vendor = vendorIntent(context);
        if (vendor != null) return vendor;
        // Some firmware ships the AOSP TV settings app with no filter for the standard actions (HY300 Pro+
        // projector, rk3566, Android 14): open its main screen directly. Not a vendor entry, so the Settings
        // menu's "Native settings" row stays as it was.
        Intent aosp = new Intent().setComponent(AOSP_TV_SETTINGS);
        try {
            pm.getPackageInfo(AOSP_TV_SETTINGS.getPackageName(), 0);
            return aosp;
        } catch (PackageManager.NameNotFoundException e) {
            return standard;
        }
    }

    private static final ComponentName AOSP_TV_SETTINGS =
            new ComponentName("com.android.tv.settings", "com.android.tv.settings.MainSettings");

    /** The first vendor settings entry from settings_fallback_components that is installed, or null
     *  when none is. China-market firmware keeps pages the AOSP TV settings has no row for -
     *  Xiaomi's Account & Security, where the ADB switch lives - in the vendor's own settings app;
     *  MainActivity's Settings menu offers it as "Native settings" while one is installed. */
    static Intent vendorIntent(Context context) {
        PackageManager pm = context.getPackageManager();
        for (String value : context.getResources().getStringArray(R.array.settings_fallback_components)) {
            ComponentName component = ComponentName.unflattenFromString(value);
            if (component == null) continue;
            // Explicit resolveActivity() always succeeds, so check the package exists instead.
            try {
                pm.getPackageInfo(component.getPackageName(), 0);
            } catch (PackageManager.NameNotFoundException e) {
                continue;
            }
            return new Intent().setComponent(component);
        }
        return null;
    }

    /** The TV's App info page for pkg, or null where the firmware has none (CVTE's settings app, #1202). */
    static Intent appInfo(Context context, String pkg) {
        Intent info = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg));
        return context.getPackageManager().resolveActivity(info, PackageManager.MATCH_DEFAULT_ONLY) != null ? info : null;
    }

    /**
     * The TV's display or picture settings. On Xiaomi sets, the AOSP DISPLAY_SETTINGS action resolves to
     * an empty stub in DroidTvSettings; prefer Xiaomi's own PictureActivity where installed.
     */
    static Intent displayIntent(Context context) {
        ComponentName xiaomi = new ComponentName("com.android.tv.settings",
                "com.xiaomi.mitv.settings.picture.PictureActivity");
        if (context.getPackageManager().resolveActivity(new Intent().setComponent(xiaomi), 0) != null) {
            return new Intent().setComponent(xiaomi);
        }
        return intent(context, Settings.ACTION_DISPLAY_SETTINGS);
    }

    /** The TV exposes both a dedicated sound page and a combined display/sound page for SOUND_SETTINGS.
     *  Use the dedicated page so Quick Access never shows Android's chooser. */
    static Intent soundIntent(Context context) {
        ComponentName nativeSound = new ComponentName("com.android.tv.settings",
                "com.android.tv.settings.device.sound.SoundActivity");
        if (context.getPackageManager().resolveActivity(new Intent().setComponent(nativeSound), 0) != null) {
            return new Intent().setComponent(nativeSound);
        }
        return intent(context, Settings.ACTION_SOUND_SETTINGS);
    }

    /** The Android TV Remote & accessories page. Changhong's TvSettings exposes it through the
     * Settings app's SliceActivity. The URI must be passed as the private slice_uri extra; using
     * the CONNECT_INPUT action opens only the pairing/search screen. */
    static Intent accessoriesIntent(Context context) {
        ComponentName sliceActivity = new ComponentName("com.android.tv.settings",
                "com.android.tv.settings.slice.SliceActivity");
        Intent explicit = new Intent("android.settings.SLICE_SETTINGS")
                .setComponent(sliceActivity)
                .putExtra("slice_uri",
                        "content://com.android.tv.settings.accessories.sliceprovider/general")
                .putExtra("startup_verification_required", false);
        try {
            context.getPackageManager().getActivityInfo(sliceActivity, 0);
            return explicit;
        } catch (PackageManager.NameNotFoundException unavailable) {
            // Older/vendor settings packages may not ship the Slice-backed accessories page.
        }
        return new Intent("com.google.android.intent.action.CONNECT_INPUT");
    }
}
