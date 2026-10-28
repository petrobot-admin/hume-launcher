package com.petrobot.hume;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Continue Watching from Android's Watch Next list (the "Play Next" row apps such as Netflix,
 * Prime Video, Crunchyroll and Jellyfin publish to the TV provider), its only source: on TVs
 * without it (Android 7) Continue Watching says it isn't supported. Reading other apps' rows
 * needs READ_TV_LISTINGS, a normal runtime
 * permission (Android's prompt, or adb shell pm grant com.petrobot.hume android.permission.READ_TV_LISTINGS);
 * the provider then returns every row its app left searchable, which is the default.
 *
 * Watch Next rows come with the series title, episode, artwork, position and the app's own link
 * to the title, so Resume opens the episode rather than the app. Rules (as in the mature
 * launchers that read this list): newest engagement first; one card per show per app; at most
 * three per app and twelve in all; anything under 10 minutes is left out; a CONTINUE row is
 * done at 95% or under 2 minutes left;
 * rows untouched for 60 days, non-browsable rows and Watchlist rows are left out. Apps' rows
 * can't be deleted by another app, so Remove hides a row until it is watched again.
 */
final class WatchNext {
    static final String PERM = "android.permission.READ_TV_LISTINGS";
    static final Uri URI = Uri.parse("content://android.media.tv/watch_next_program");

    static final int TYPE_CONTINUE = 0, TYPE_NEXT = 1, TYPE_NEW = 2, TYPE_WATCHLIST = 3;
    static final long MAX_AGE_MS = 60L * 24 * 60 * 60 * 1000;
    /** Shorter than this (trailers, clips, shorts, ads) never appears in Continue Watching. */
    static final long MIN_DURATION_MS = 10 * 60_000;
    static final int PER_APP = 3, MAX = 12;

    private WatchNext() {
    }

    /** Watch Next arrived in Android 8.0. */
    static boolean supported() {
        return Build.VERSION.SDK_INT >= 26;
    }

    static boolean granted(Context c) {
        return supported() && c.checkSelfPermission(PERM) == PackageManager.PERMISSION_GRANTED;
    }

    /** Continue Watching: Watch Next rows, at most three per app and twelve in all. */
    static List<Suggestions.Item> all(Context c) {
        Map<String, Integer> perApp = new HashMap<>();
        List<Suggestions.Item> out = new ArrayList<>();
        for (Suggestions.Item it : list(c)) {
            int n = perApp.containsKey(it.pkg) ? perApp.get(it.pkg) : 0;
            if (n >= PER_APP) continue;
            perApp.put(it.pkg, n + 1);
            out.add(it);
            if (out.size() >= MAX) break;
        }
        return out;
    }

    /**
     * Removes what 7.6 and earlier saved from media sessions (files/continue*.json and the cw/
     * artwork folder); Continue Watching now comes only from Watch Next.
     */
    static void deleteOldHistory(Context c) {
        SharedPreferences p = c.getSharedPreferences("perms", Context.MODE_PRIVATE);
        if (p.getBoolean("old_cw_deleted", false)) return;
        java.io.File[] files = c.getFilesDir().listFiles();
        if (files != null) {
            for (java.io.File f : files) {
                if (f.getName().startsWith("continue") && f.getName().endsWith(".json")) f.delete();
            }
        }
        java.io.File[] art = new java.io.File(c.getFilesDir(), "cw").listFiles();
        if (art != null) for (java.io.File f : art) f.delete();
        new java.io.File(c.getFilesDir(), "cw").delete();
        p.edit().putBoolean("old_cw_deleted", true).apply();
    }

    static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    /** Watch Next rows as carousel items, newest first (empty without the permission). */
    static List<Suggestions.Item> list(Context c) {
        List<Suggestions.Item> out = new ArrayList<>();
        if (!granted(c)) return out;
        Set<String> hidden = dismissed(c);
        Set<String> shows = new HashSet<>();
        long now = System.currentTimeMillis();
        // The provider rejects any selection from other apps; filtering happens here.
        try (Cursor cur = c.getContentResolver().query(URI, null, null, null, "last_engagement_time_utc_millis DESC")) {
            if (cur == null) return out;
            while (cur.moveToNext()) {
                String pkg = str(cur, "package_name"), title = str(cur, "title");
                if (pkg.isEmpty() || title.isEmpty() || pkg.equals(c.getPackageName())) continue;
                if (num(cur, "browsable", 1) == 0) continue;
                int type = (int) num(cur, "watch_next_type", TYPE_CONTINUE);
                if (type == TYPE_WATCHLIST) continue;
                long id = num(cur, "_id", 0);
                long played = millis(num(cur, "last_engagement_time_utc_millis", 0));
                if (played > 0 && now - played > MAX_AGE_MS) continue;
                String key = "wn|" + pkg + "|" + id + "|" + played;
                if (hidden.contains(key) || !Suggestions.installed(c, pkg)) continue;
                long pos = num(cur, "last_playback_position_millis", 0), dur = num(cur, "duration_millis", 0);
                // Some apps (e.g. Nuvio) store progress on a 100/1000/... scale instead of milliseconds.
                boolean scale = dur == 100 || dur == 1000 || dur == 10_000 || dur == 100_000;
                if (!scale && dur > 0 && dur < MIN_DURATION_MS) continue;
                float progress = -1f;
                int left = -1;
                if (type == TYPE_CONTINUE && dur > 0 && pos > 0 && pos <= dur) {
                    progress = pos / (float) dur;
                    if (progress >= 0.95f) continue;
                    if (!scale) {
                        if (dur - pos < 2 * 60_000) continue;
                        left = (int) Math.max(1, (dur - pos) / 60_000);
                    }
                }
                if (!shows.add(pkg + "|" + norm(title))) continue;  // one card per show per app

                Suggestions.Item it = new Suggestions.Item();
                it.type = "continue";
                it.pkg = pkg;
                it.key = key;
                it.id = key.hashCode();
                it.title = title;
                it.overview = episode(cur);
                it.backdrop = art(cur);
                it.link = str(cur, "intent_uri");
                it.progress = progress;
                it.remainingMin = left;
                it.played = played;
                it.year = "";
                it.genres = type == TYPE_NEXT ? "Up Next" : type == TYPE_NEW ? "New" : "";
                out.add(it);
            }
        } catch (Exception e) {
            android.util.Log.w("Hume", "watch next: " + e);
        }
        return out;
    }

    /** "S2 E5 · The Episode" for episodes, else the app's short description. */
    static String episode(Cursor cur) {
        String s = str(cur, "season_display_number"), e = str(cur, "episode_display_number");
        String et = str(cur, "episode_title");
        StringBuilder b = new StringBuilder();
        if (!s.isEmpty()) b.append('S').append(s);
        if (!e.isEmpty()) b.append(b.length() > 0 ? " " : "").append('E').append(e);
        if (!et.isEmpty()) b.append(b.length() > 0 ? "  ·  " : "").append(et);
        return b.length() > 0 ? b.toString() : str(cur, "short_description");
    }

    /** Landscape artwork for the full-screen carousel: a wide poster, else the thumbnail, else any poster. */
    static String art(Cursor cur) {
        String poster = str(cur, "poster_art_uri"), thumb = str(cur, "thumbnail_uri");
        int ratio = (int) num(cur, "poster_art_aspect_ratio", -1);  // 0 16:9, 1 3:2, 2 4:3, 3 1:1, 4 2:3, 5 movie poster
        if (!poster.isEmpty() && ratio >= 0 && ratio <= 2) return poster;
        return !thumb.isEmpty() ? thumb : poster;
    }

    /** Some apps write seconds instead of milliseconds. */
    static long millis(long t) {
        return t > 0 && t < 100_000_000_000L ? t * 1000 : t;
    }

    static String str(Cursor c, String col) {
        int i = c.getColumnIndex(col);
        if (i < 0 || c.isNull(i)) return "";
        String s = c.getString(i);
        return s == null ? "" : s.trim();
    }

    static long num(Cursor c, String col, long def) {
        int i = c.getColumnIndex(col);
        return i < 0 || c.isNull(i) ? def : c.getLong(i);
    }

    /** Resume: the app's own link to the title, else the app itself. */
    static void open(Context c, Suggestions.Item it) {
        if (!it.link.isEmpty()) {
            try {
                Intent i = it.link.startsWith("intent:") || it.link.startsWith("#Intent")
                        ? Intent.parseUri(it.link, Intent.URI_INTENT_SCHEME)
                        : new Intent(Intent.ACTION_VIEW, Uri.parse(it.link));
                i.setSelector(null);
                // Keep a plain link inside the app that published it, not a browser or chooser.
                if (i.getPackage() == null && i.getComponent() == null) {
                    Intent own = new Intent(i).setPackage(it.pkg);
                    if (own.resolveActivity(c.getPackageManager()) != null) i = own;
                }
                if (Suggestions.start(c, i)) return;
            } catch (Exception e) {
                android.util.Log.w("Hume", "watch next link: " + e);
            }
        }
        Intent i = c.getPackageManager().getLeanbackLaunchIntentForPackage(it.pkg);
        if (i == null) i = c.getPackageManager().getLaunchIntentForPackage(it.pkg);
        if (i != null) Suggestions.start(c, i);
    }

    // ---------------------------------------------------------------- Remove

    static boolean isRow(Suggestions.Item it) {
        return it.key.startsWith("wn|");
    }

    static Set<String> dismissed(Context c) {
        return new HashSet<>(Profiles.homePrefs(c).getStringSet("wn_dismissed", Collections.emptySet()));
    }

    /** Hides the row until the app reports it watched again (its engagement time is part of the key). */
    static void dismiss(Context c, String key) {
        SharedPreferences p = Profiles.homePrefs(c);
        Set<String> s = dismissed(c);
        s.add(key);
        // Keys of rows that have since changed or gone are useless; keep the set small.
        if (s.size() > 200) s.clear();
        p.edit().putStringSet("wn_dismissed", s).apply();
    }

    /** Clear history: hide every row showing now (they come back only when watched again). */
    static void dismissAll(Context c) {
        SharedPreferences p = Profiles.homePrefs(c);
        Set<String> s = new HashSet<>();
        for (Suggestions.Item it : list(c)) s.add(it.key);
        p.edit().putStringSet("wn_dismissed", s).apply();
    }
}
