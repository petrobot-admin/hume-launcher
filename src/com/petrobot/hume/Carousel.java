package com.petrobot.hume;

import android.content.Context;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Composes the top-shelf carousel from its sources, in this order of priority:
 * <ol>
 * <li><b>Continue Watching</b> (up to six, first): apps' Watch Next rows, then items from apps'
 *     own "Continue watching" channels (undated, so after the dated ones).</li>
 * <li><b>From your apps</b>: apps' preview rows, taken round-robin across apps so no app floods
 *     the carousel (at most two per app, eight in all). Which items an app contributes rotates
 *     every two hours through its first eight, so the carousel changes through the day without
 *     jumping on every reload.</li>
 * <li><b>Recommendations and news</b> (already alternated by Suggestions).</li>
 * </ol>
 * After Continue Watching, one app item is placed for every two recommendation/news items, so
 * each kind keeps its share; whichever runs out, the other fills in. A title shows once: Continue
 * Watching beats an app's row, which beats a recommendation (it opens in an installed app).
 */
final class Carousel {
    static final int CONTINUE_MAX = 6, APPS_MAX = 8, PER_APP = 2, TOTAL_MAX = 24;
    static final long ROTATE_MS = 2 * 60 * 60_000L;

    private Carousel() {
    }

    /** Continue Watching: Watch Next rows, then apps' resume channels (not removed, not short, not seen). */
    static List<Suggestions.Item> continueWatching(Context c, List<Suggestions.Item> watchNext, List<Suggestions.Item> resume) {
        List<Suggestions.Item> out = new ArrayList<>(watchNext);
        Set<String> seen = new HashSet<>();
        for (Suggestions.Item it : watchNext) seen.add(it.pkg + "|" + WatchNext.norm(it.title));
        Set<String> hidden = WatchNext.dismissed(c);
        for (Suggestions.Item it : resume) {
            if (hidden.contains(it.key) || !seen.add(it.pkg + "|" + WatchNext.norm(it.title))) continue;
            if (it.remainingMin > 0 && it.progress > 0 && it.remainingMin / (1f - it.progress) < 10) continue;  // < 10 min
            out.add(it);
        }
        return out;
    }

    /** Apps' rows for this profile: round-robin across apps, rotating which items each app offers. */
    static List<Suggestions.Item> fromApps(Context c, Map<String, List<Suggestions.Item>> byApp) {
        Set<String> hidden = AppRows.hidden(c);
        List<List<Suggestions.Item>> pools = new ArrayList<>();
        for (Map.Entry<String, List<Suggestions.Item>> e : byApp.entrySet()) {
            if (!hidden.contains(e.getKey()) && !e.getValue().isEmpty()) pools.add(e.getValue());
        }
        if (pools.isEmpty()) return new ArrayList<>();
        long slot = System.currentTimeMillis() / ROTATE_MS;
        // The app that leads changes with the slot too.
        int lead = (int) (slot % pools.size());
        List<Suggestions.Item> out = new ArrayList<>();
        for (int round = 0; round < PER_APP && out.size() < APPS_MAX; round++) {
            for (int k = 0; k < pools.size() && out.size() < APPS_MAX; k++) {
                List<Suggestions.Item> pool = pools.get((lead + k) % pools.size());
                if (round >= pool.size()) continue;
                int start = (int) ((slot * PER_APP) % pool.size());
                out.add(pool.get((start + round) % pool.size()));
            }
        }
        return out;
    }

    /** The carousel: Continue Watching, then app items and recommendations/news interleaved 1:2. */
    static List<Suggestions.Item> compose(List<Suggestions.Item> cw, List<Suggestions.Item> apps, List<Suggestions.Item> base) {
        List<Suggestions.Item> out = new ArrayList<>();
        Set<String> titles = new HashSet<>();
        for (int i = 0; i < cw.size() && out.size() < CONTINUE_MAX; i++) add(out, titles, cw.get(i));
        List<Suggestions.Item> rest = base == null ? new ArrayList<>() : base;
        int a = 0, b = 0;
        while ((a < apps.size() || b < rest.size()) && out.size() < TOTAL_MAX) {
            if (a < apps.size()) add(out, titles, apps.get(a++));
            for (int k = 0; k < 2 && b < rest.size(); k++) add(out, titles, rest.get(b++));
        }
        return out;
    }

    private static void add(List<Suggestions.Item> out, Set<String> titles, Suggestions.Item it) {
        // News headlines never clash with titles; everything else shows once.
        if (!"news".equals(it.type) && !titles.add(WatchNext.norm(it.title))) return;
        out.add(it);
    }
}
