package com.petrobot.hume;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Apps' own home-screen rows ("preview channels": YouTube's recommendations, Stremio's and
 * Jellyfin's rows, …) from the TV provider, read with the same READ_TV_LISTINGS permission as
 * Watch Next. One channel per app is used: its first channel the system marked browsable, else
 * its first. A channel that is really a resume list ("Continue watching", Stremio's for one) is
 * handed to Continue Watching instead, since Watch Next rows have dates and these don't.
 * Programs keep the app's order (weight, then insertion). The result is cached for two minutes:
 * the carousel reloads often and these tables can hold hundreds of rows.
 */
final class AppRows {
    static final Uri CHANNELS = Uri.parse("content://android.media.tv/channel");
    static final Uri PROGRAMS = Uri.parse("content://android.media.tv/preview_program");
    /** Kept per app; the carousel shows a few of them, rotating through the day. */
    static final int POOL = 8;
    static final long CACHE_MS = 2 * 60_000;

    /** What the TV provider holds right now, split by purpose. */
    static final class Rows {
        /** Per app (in a stable order), its channel's programs. */
        final Map<String, List<Suggestions.Item>> byApp = new LinkedHashMap<>();
        /** Items from apps' "Continue watching" channels (undated). */
        final List<Suggestions.Item> resume = new ArrayList<>();
    }

    private static Rows cache;
    private static long cachedAt;

    private AppRows() {
    }

    /** This profile shows apps' rows in the carousel (Settings → Top Shelf → From Your Apps). */
    static boolean enabled(Context c) {
        return Profiles.homePrefs(c).getBoolean("app_rows", true) && WatchNext.granted(c);
    }

    static void setEnabled(Context c, boolean on) {
        Profiles.homePrefs(c).edit().putBoolean("app_rows", on).apply();
    }

    /** Apps this profile turned off in the carousel. */
    static Set<String> hidden(Context c) {
        return new HashSet<>(Profiles.homePrefs(c).getStringSet("app_rows_hidden", Collections.emptySet()));
    }

    static void setHidden(Context c, String pkg, boolean hide) {
        Set<String> s = hidden(c);
        if (hide) s.add(pkg);
        else s.remove(pkg);
        Profiles.homePrefs(c).edit().putStringSet("app_rows_hidden", s).apply();
    }

    static synchronized void invalidate() {
        cache = null;
    }

    /** Every app's rows (hidden apps included; callers filter). Off the UI thread. */
    static synchronized Rows load(Context c) {
        if (cache != null && SystemClock.elapsedRealtime() - cachedAt < CACHE_MS) return cache;
        Rows r = new Rows();
        if (WatchNext.granted(c)) {
            try {
                read(c, r);
            } catch (Exception e) {
                android.util.Log.w("Hume", "app rows: " + e);
            }
        }
        cache = r;
        cachedAt = SystemClock.elapsedRealtime();
        return r;
    }

    private static void read(Context c, Rows r) {
        // Channels: pick each app's channel, and note which ones are resume lists.
        Map<Long, String> chosen = new HashMap<>();   // channel id -> package
        Map<Long, String> names = new HashMap<>();
        Set<Long> resume = new HashSet<>();
        Map<String, Long> fallback = new LinkedHashMap<>();
        // Selections from other apps are refused by the provider; everything is filtered here.
        try (Cursor cur = c.getContentResolver().query(CHANNELS, null, null, null, null)) {
            if (cur == null) return;
            while (cur.moveToNext()) {
                if (!"TYPE_PREVIEW".equals(WatchNext.str(cur, "type"))) continue;
                String pkg = WatchNext.str(cur, "package_name");
                if (pkg.isEmpty() || pkg.equals(c.getPackageName()) || !Suggestions.installed(c, pkg)) continue;
                long id = WatchNext.num(cur, "_id", -1);
                String name = WatchNext.str(cur, "display_name");
                names.put(id, name);
                if (isResume(name)) {
                    resume.add(id);
                    chosen.put(id, pkg);
                    continue;
                }
                boolean browsable = WatchNext.num(cur, "browsable", 1) != 0;
                if (browsable && !chosen.containsValue(pkg)) chosen.put(id, pkg);
                else if (!fallback.containsKey(pkg)) fallback.put(pkg, id);
            }
        }
        for (Map.Entry<String, Long> e : fallback.entrySet()) {
            if (!hasMain(chosen, resume, e.getKey())) chosen.put(e.getValue(), e.getKey());
        }
        if (chosen.isEmpty()) return;

        Map<Long, List<long[]>> order = new HashMap<>();    // channel -> (weight, id) for sorting
        Map<Long, Suggestions.Item> items = new HashMap<>();
        Set<String> titles = new HashSet<>();
        try (Cursor cur = c.getContentResolver().query(PROGRAMS, null, null, null, null)) {
            if (cur == null) return;
            while (cur.moveToNext()) {
                long ch = WatchNext.num(cur, "channel_id", -1);
                String pkg = chosen.get(ch);
                if (pkg == null || WatchNext.num(cur, "browsable", 1) == 0) continue;
                String title = WatchNext.str(cur, "title");
                String art = WatchNext.art(cur);
                if (title.isEmpty() || art.isEmpty()) continue;  // the carousel is artwork-first
                if (!titles.add(pkg + "|" + WatchNext.norm(title))) continue;
                long id = WatchNext.num(cur, "_id", 0);
                Suggestions.Item it = new Suggestions.Item();
                it.type = resume.contains(ch) ? "continue" : "app";
                it.pkg = pkg;
                it.key = "pp|" + pkg + "|" + id;
                it.id = it.key.hashCode();
                it.title = title;
                it.overview = WatchNext.episode(cur);
                it.backdrop = art;
                it.logo = "";
                it.link = WatchNext.str(cur, "intent_uri");
                it.year = "";
                it.genres = resume.contains(ch) ? "" : names.get(ch);
                long pos = WatchNext.num(cur, "last_playback_position_millis", 0), dur = WatchNext.num(cur, "duration_millis", 0);
                if (dur > 0 && pos > 0 && pos < dur) {
                    it.progress = pos / (float) dur;
                    if (dur >= 60_000) it.remainingMin = (int) Math.max(1, (dur - pos) / 60_000);
                }
                items.put(id, it);
                List<long[]> l = order.get(ch);
                if (l == null) order.put(ch, l = new ArrayList<>());
                l.add(new long[]{WatchNext.num(cur, "weight", 0), id});
            }
        }
        // App order: alphabetical by package for stability (the carousel rotates which app leads).
        List<Long> channels = new ArrayList<>(order.keySet());
        Collections.sort(channels, (a, b) -> chosen.get(a).compareTo(chosen.get(b)));
        for (long ch : channels) {
            List<long[]> l = order.get(ch);
            Collections.sort(l, (a, b) -> a[0] != b[0] ? Long.compare(b[0], a[0]) : Long.compare(a[1], b[1]));
            if (resume.contains(ch)) {
                for (long[] w : l) r.resume.add(items.get(w[1]));
                continue;
            }
            List<Suggestions.Item> pool = new ArrayList<>();
            for (int i = 0; i < l.size() && pool.size() < POOL; i++) pool.add(items.get(l.get(i)[1]));
            if (!pool.isEmpty()) r.byApp.put(chosen.get(ch), pool);
        }
    }

    private static boolean hasMain(Map<Long, String> chosen, Set<Long> resume, String pkg) {
        for (Map.Entry<Long, String> e : chosen.entrySet()) {
            if (e.getValue().equals(pkg) && !resume.contains(e.getKey())) return true;
        }
        return false;
    }

    /** A channel that lists what you were watching ("Continue Watching", "Resume", "Recently watched"). */
    static boolean isResume(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.contains("continue watching") || n.contains("continue") && n.contains("watch")
                || n.startsWith("resume") || n.contains("recently watched") || n.contains("watch next");
    }
}
