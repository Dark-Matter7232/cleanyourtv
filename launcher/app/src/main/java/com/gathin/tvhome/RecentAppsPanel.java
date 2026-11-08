package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared Recents list content used by Home's popup and the Quick Access activity. */
final class RecentAppsPanel {
    interface Actions {
        void open(Intent launch);

        void closeAll();
    }

    private RecentAppsPanel() {
    }

    static void populate(Context context, LinearLayout rows, Actions actions) {
        LayoutInflater inflater = LayoutInflater.from(context);
        PackageManager pm = context.getPackageManager();
        for (String pkg : Recents.recent(context, 6)) {
            try {
                ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
                Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
                if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
                if (launch == null || pm.resolveActivity(launch, 0) == null) continue;

                View row = inflater.inflate(R.layout.item_input, rows, false);
                ImageView icon = row.findViewById(R.id.icon);
                icon.setImageDrawable(info.loadIcon(pm));
                icon.setImageTintList(null);
                ((TextView) row.findViewById(R.id.title)).setText(pm.getApplicationLabel(info));
                row.findViewById(R.id.dot).setVisibility(View.GONE);
                row.findViewById(R.id.status).setVisibility(View.GONE);
                Intent selected = new Intent(launch);
                row.setOnClickListener(v -> actions.open(selected));
                rows.addView(row);
            } catch (PackageManager.NameNotFoundException | RuntimeException unavailable) {
                // The app may have disappeared or become disabled while the panel was built.
            }
        }

        if (rows.getChildCount() == 0) {
            View empty = inflater.inflate(R.layout.item_input, rows, false);
            empty.findViewById(R.id.icon).setVisibility(View.GONE);
            ((TextView) empty.findViewById(R.id.title)).setText(
                    Recents.hasUsageAccess(context) ? R.string.recent_apps_empty : R.string.recents_need_access);
            empty.findViewById(R.id.dot).setVisibility(View.GONE);
            empty.findViewById(R.id.status).setVisibility(View.GONE);
            empty.setFocusable(false);
            empty.setClickable(false);
            rows.addView(empty);
        }

        addSeparator(context, rows);
        View closeAll = inflater.inflate(R.layout.item_input, rows, false);
        ((ImageView) closeAll.findViewById(R.id.icon)).setImageResource(R.drawable.ic_apps);
        ((TextView) closeAll.findViewById(R.id.title)).setText(R.string.close_all);
        closeAll.findViewById(R.id.dot).setVisibility(View.GONE);
        closeAll.findViewById(R.id.status).setVisibility(View.GONE);
        closeAll.setOnClickListener(v -> actions.closeAll());
        rows.addView(closeAll);
        installCircularNavigation(rows);
    }

    private static void addSeparator(Context context, LinearLayout rows) {
        float density = context.getResources().getDisplayMetrics().density;
        View line = new View(context);
        line.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, (int) density));
        int margin = (int) (6 * density);
        int side = (int) (12 * density);
        lp.setMargins(side, margin, side, margin);
        rows.addView(line, lp);
    }

    /** Wrap focus at both ends, including the Close all action. */
    private static void installCircularNavigation(LinearLayout rows) {
        for (int i = 0; i < rows.getChildCount(); i++) {
            View row = rows.getChildAt(i);
            if (!row.isFocusable()) continue;
            row.setOnKeyListener((view, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() != 0
                        || (keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN)) {
                    return false;
                }
                View first = null;
                View last = null;
                for (int child = 0; child < rows.getChildCount(); child++) {
                    View candidate = rows.getChildAt(child);
                    if (!candidate.isFocusable()) continue;
                    if (first == null) first = candidate;
                    last = candidate;
                }
                if (first == null || last == null) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP && view == first) {
                    last.requestFocus();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && view == last) {
                    first.requestFocus();
                    return true;
                }
                return false;
            });
        }
    }
}
