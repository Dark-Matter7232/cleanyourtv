package com.gathin.tvhome;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Process;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Lightweight, permission-gated recent-app list for TVs whose SystemUI has no Recents provider. */
final class Recents {
    private static final long WINDOW_MS = 3L * 24L * 60L * 60L * 1000L;
    private static final String PREFS = "recent_apps";
    private static final String CLEARED_AT = "cleared_at";

    private Recents() {
    }

    static boolean hasUsageAccess(Context context) {
        AppOpsManager ops = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (ops == null) return false;
        int mode;
        if (Build.VERSION.SDK_INT >= 29) {
            mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), context.getPackageName());
        } else {
            mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), context.getPackageName());
        }
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    static List<String> recent(Context context, int max) {
        List<String> result = new ArrayList<>();
        UsageStatsManager usage = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usage == null || !hasUsageAccess(context) || max <= 0) return result;

        long now = System.currentTimeMillis();
        long clearedAt = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(CLEARED_AT, 0L);
        Map<String, UsageStats> stats = usage.queryAndAggregateUsageStats(now - WINDOW_MS, now);
        List<UsageStats> used = new ArrayList<>();
        for (UsageStats stat : stats.values()) {
            if (stat.getLastTimeUsed() > clearedAt && stat.getTotalTimeInForeground() > 0) used.add(stat);
        }
        Collections.sort(used, (left, right) ->
                Long.compare(right.getLastTimeUsed(), left.getLastTimeUsed()));

        Set<String> launchable = launchable(context, false);
        for (UsageStats stat : used) {
            if (launchable.contains(stat.getPackageName())) result.add(stat.getPackageName());
            if (result.size() >= max) break;
        }
        return result;
    }

    /** Frees background processes for launchable user apps without touching system apps or Home. */
    static void closeAll(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(CLEARED_AT, System.currentTimeMillis()).apply();
        android.app.ActivityManager activity =
                (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activity == null) return;
        for (String pkg : launchable(context, true)) {
            activity.killBackgroundProcesses(pkg);
        }
    }

    static long availableMemory(Context context) {
        android.app.ActivityManager activity =
                (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activity == null) return 0L;
        android.app.ActivityManager.MemoryInfo info = new android.app.ActivityManager.MemoryInfo();
        activity.getMemoryInfo(info);
        return info.availMem;
    }

    private static Set<String> launchable(Context context, boolean userAppsOnly) {
        PackageManager pm = context.getPackageManager();
        Set<String> packages = new LinkedHashSet<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo info : pm.queryIntentActivities(query, 0)) {
                ApplicationInfo app = info.activityInfo.applicationInfo;
                boolean userApp = (app.flags & (ApplicationInfo.FLAG_SYSTEM
                        | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) == 0;
                if (!app.packageName.equals(context.getPackageName()) && app.enabled
                        && (!userAppsOnly || userApp)) {
                    packages.add(app.packageName);
                }
            }
        }
        return packages;
    }

}
