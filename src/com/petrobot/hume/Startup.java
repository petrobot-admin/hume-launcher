package com.petrobot.hume;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * "Start with the TV" (Settings → Home): when the TV starts, or also whenever it wakes from
 * standby, open an app or a TV input instead of staying on Home. For the whole TV, not per
 * profile. Fired once per boot (boot broadcast or Home starting just after boot, whichever comes
 * first) and on SCREEN_ON. Android lets an app open a screen from the background only in a few
 * cases; Hume's accessibility switch is one, so waking into an app is reliable only with it on
 * (at boot Hume is usually on screen anyway, as the home app). Presentation's own "Start on boot"
 * takes precedence at boot.
 */
final class Startup {
    static final String K_TARGET = "target", K_LABEL = "label", K_WAKE = "on_wake";
    /** Lets the TV, HDMI-CEC and the network settle before switching. */
    static final long BOOT_DELAY_MS = 2500, WAKE_DELAY_MS = 1200;

    private static boolean bootHandled;
    private static long lastFired;

    private Startup() {
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("startup", Context.MODE_PRIVATE);
    }

    /** "" (stay on Home), "app:<package>" or "input:<TV input id>". */
    static String target(Context c) {
        return prefs(c).getString(K_TARGET, "");
    }

    static String label(Context c) {
        String t = target(c);
        return t.isEmpty() ? "Home Screen" : prefs(c).getString(K_LABEL, t);
    }

    static boolean onWake(Context c) {
        return prefs(c).getBoolean(K_WAKE, false);
    }

    static void set(Context c, String target, String label) {
        prefs(c).edit().putString(K_TARGET, target).putString(K_LABEL, label).apply();
    }

    static void setOnWake(Context c, boolean on) {
        prefs(c).edit().putBoolean(K_WAKE, on).apply();
    }

    /** Whether this wake will open something (so Home skips the "Who's watching?" picker). */
    static boolean opensOnWake(Context c) {
        return !target(c).isEmpty() && onWake(c);
    }

    /** Once per boot, from the boot broadcast or Home starting up just after boot. */
    static synchronized void onBoot(Context c) {
        if (bootHandled) return;
        bootHandled = true;
        if (target(c).isEmpty()) return;
        if (com.petrobot.hume.present.Presenter.enabled(c) && com.petrobot.hume.present.Presenter.startOnBoot(c)) return;
        later(c, BOOT_DELAY_MS);
    }

    static void onScreenOn(Context c) {
        if (!opensOnWake(c)) return;
        later(c, WAKE_DELAY_MS);
    }

    private static void later(Context c, long delay) {
        Context app = c.getApplicationContext();
        new Handler(Looper.getMainLooper()).postDelayed(() -> fire(app), delay);
    }

    /** Opens the target; the boot path and the wake receivers can all call this, so repeats within 10 s are dropped. */
    static synchronized void fire(Context c) {
        long now = SystemClock.elapsedRealtime();
        if (lastFired != 0 && now - lastFired < 10_000) return;
        lastFired = now;
        String t = target(c);
        if (t.startsWith("app:")) {
            String pkg = t.substring(4);
            PackageManager pm = c.getPackageManager();
            Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (i == null) i = pm.getLaunchIntentForPackage(pkg);
            if (i != null) Suggestions.start(c, i);
        } else if (t.startsWith("input:")) {
            String id = t.substring(6);
            for (Inputs.Input in : Inputs.list(c)) {
                if (id.equals(in.id)) {
                    Inputs.open(c, in);
                    break;
                }
            }
        }
    }

    /** Launchable apps for the chooser: {package, label}, by label. */
    static List<String[]> apps(Context c) {
        PackageManager pm = c.getPackageManager();
        List<ResolveInfo> all = new ArrayList<>();
        all.addAll(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
        all.addAll(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
        Set<String> seen = new HashSet<>();
        List<String[]> out = new ArrayList<>();
        for (ResolveInfo r : all) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(c.getPackageName()) || !seen.add(pkg)) continue;
            out.add(new String[]{pkg, String.valueOf(r.loadLabel(pm))});
        }
        Collections.sort(out, (a, b) -> a[1].compareToIgnoreCase(b[1]));
        return out;
    }
}
