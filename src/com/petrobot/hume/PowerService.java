package com.petrobot.hume;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Hume's accessibility switch, turned on by the user in the TV's Accessibility settings. It:
 * - puts the TV to sleep (Power Off, Sleep Timer) on TVs where device admin is switched off;
 * - closes apps for Clean by pressing Force stop on each app's info page;
 * - Home button: when the TV's own launcher comes up (Home pressed, or Back out of an app) while
 *   Hume isn't the home app, opens Hume instead, so the stock launcher needn't be disabled. Only
 *   the package and class of the window that opened are looked at, nothing on screen;
 * - Start with the TV: opens the chosen app or input on wake (Android allows an accessibility
 *   service to open screens from the background).
 */
public class PowerService extends AccessibilityService {
    static PowerService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<String> queue = new ArrayList<>();
    private Runnable onDone;
    private int step;

    private BroadcastReceiver screenOn;

    @Override
    protected void onServiceConnected() {
        instance = this;
        screenOn = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                Startup.onScreenOn(c);
            }
        };
        registerReceiver(screenOn, new IntentFilter(Intent.ACTION_SCREEN_ON));
        // Bound early after boot, and allowed to open screens: a reliable boot trigger too.
        if (com.petrobot.hume.present.Presenter.justBooted()) Startup.onBoot(this);
        // The stock launcher may already be on screen (just booted, or the switch was turned on
        // over it): no window change will be reported for it, so look once now.
        handler.postDelayed(this::homeIfStockShowing, 800);
    }

    private void homeIfStockShowing() {
        if (!HomeButton.wanted(this)) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        CharSequence pkg = root == null ? null : root.getPackageName();
        if (pkg == null) return;
        for (String h : HomeButton.stockHomes(this)) {
            if (h.startsWith(pkg + "/")) {
                lastRedirect = android.os.SystemClock.elapsedRealtime();
                startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            }
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || !queue.isEmpty()) return;
        CharSequence pkg = e.getPackageName(), cls = e.getClassName();
        if (pkg == null || cls == null || !HomeButton.wanted(this)) return;
        if (!HomeButton.stockHomes(this).contains(pkg + "/" + cls)) return;
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastRedirect < 800) return;  // the stock launcher may report its window twice
        lastRedirect = now;
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private long lastRedirect;

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        if (screenOn != null) unregisterReceiver(screenOn);
        instance = null;
        super.onDestroy();
    }

    /** Screen off / sleep. Returns false when the service isn't on. */
    static boolean sleep() {
        PowerService s = instance;
        return s != null && android.os.Build.VERSION.SDK_INT >= 28 && s.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }

    /** Force-stops each app via its App info page, then returns to Hume and runs {@code done}. */
    static void forceStop(Context c, List<String> pkgs, Runnable done) {
        PowerService s = instance;
        if (s == null) {
            done.run();
            return;
        }
        s.queue.clear();
        s.queue.addAll(pkgs);
        s.onDone = done;
        s.next();
    }

    private void next() {
        if (queue.isEmpty()) {
            Intent home = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(home);
            if (onDone != null) handler.postDelayed(onDone, 300);
            onDone = null;
            return;
        }
        String pkg = queue.remove(0);
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY));
        } catch (Exception e) {
            next();
            return;
        }
        step = 0;
        handler.postDelayed(this::pressForceStop, 900);
    }

    /** Presses "Force stop", then the confirmation "OK"; gives up on an app after a few tries. */
    private void pressForceStop() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        boolean clicked = false;
        if (root != null) {
            String[] labels = step == 0 ? new String[]{"Force stop", "FORCE STOP", "Force Stop"} : new String[]{"OK", "Ok"};
            for (String l : labels) {
                List<AccessibilityNodeInfo> found = root.findAccessibilityNodeInfosByText(l);
                for (AccessibilityNodeInfo n : found) {
                    AccessibilityNodeInfo t = n;
                    while (t != null && !t.isClickable()) t = t.getParent();
                    if (t != null && t.isEnabled() && t.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        clicked = true;
                        break;
                    }
                }
                if (clicked) break;
            }
        }
        if (clicked && step == 0) {
            step = 1;
            handler.postDelayed(this::pressForceStop, 600);
            return;
        }
        if (!clicked && step == 0 && retries++ < 3) {
            handler.postDelayed(this::pressForceStop, 500);
            return;
        }
        retries = 0;
        performGlobalAction(GLOBAL_ACTION_BACK);
        handler.postDelayed(this::next, 400);
    }

    private int retries;

    /** Home button override (see the class comment); the switch lives in Settings → Home. */
    static final class HomeButton {
        private static Set<String> homes;
        private static long homesAt;

        private HomeButton() {
        }

        static boolean enabled(Context c) {
            return c.getSharedPreferences("startup", MODE_PRIVATE).getBoolean("home_override", true);
        }

        static void setEnabled(Context c, boolean on) {
            c.getSharedPreferences("startup", MODE_PRIVATE).edit().putBoolean("home_override", on).apply();
        }

        /** On, and needed: Hume isn't the home app Android would open. */
        static boolean wanted(Context c) {
            return enabled(c) && !isDefaultHome(c);
        }

        /** Active right now: wanted, and the accessibility switch is on. */
        static boolean active(Context c) {
            return instance != null && wanted(c);
        }

        static boolean isDefaultHome(Context c) {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo ri = c.getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            return ri != null && ri.activityInfo != null && c.getPackageName().equals(ri.activityInfo.packageName);
        }

        /**
         * The other launchers' home activities ("package/class"). Android's boot-time FallbackHome
         * in Settings is left alone. Rechecked every minute (apps change).
         */
        static synchronized Set<String> stockHomes(Context c) {
            long now = android.os.SystemClock.elapsedRealtime();
            if (homes != null && now - homesAt < 60_000) return homes;
            Set<String> s = new HashSet<>();
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            for (ResolveInfo r : c.getPackageManager().queryIntentActivities(home, 0)) {
                String pkg = r.activityInfo.packageName;
                if (pkg.equals(c.getPackageName()) || r.activityInfo.name.endsWith("FallbackHome")) continue;
                s.add(pkg + "/" + r.activityInfo.name);
                // An activity-alias reports its target class in window events.
                if (r.activityInfo.targetActivity != null) s.add(pkg + "/" + r.activityInfo.targetActivity);
            }
            homes = s;
            homesAt = now;
            return s;
        }
    }
}
