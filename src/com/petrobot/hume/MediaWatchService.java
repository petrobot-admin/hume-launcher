package com.petrobot.hume;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Continue Watching for apps that don't publish Android's Watch Next list (see WatchNext): listens
 * to the media sessions apps publish while they play (title, artwork, position) and remembers the
 * latest per title. Music, trailers, ads and anything under 10 minutes are left out (see Store.worthKeeping).
 * Requires notification-listener access, which Android withholds on low-RAM TVs:
 *   adb shell cmd notification allow_listener com.petrobot.hume/com.petrobot.hume.MediaWatchService
 */
public class MediaWatchService extends NotificationListenerService {

    MediaSessionManager msm;
    final Map<MediaSession.Token, MediaController.Callback> tracked = new HashMap<>();
    final MediaSessionManager.OnActiveSessionsChangedListener sessions = this::onSessions;

    /** Live instance while the listener is connected (Control Center uses it for Do Not Disturb). */
    static MediaWatchService instance;

    @Override
    public void onListenerConnected() {
        instance = this;
        msm = getSystemService(MediaSessionManager.class);
        ComponentName me = new ComponentName(this, MediaWatchService.class);
        try {
            msm.addOnActiveSessionsChangedListener(sessions, me);
            onSessions(msm.getActiveSessions(me));
        } catch (SecurityException e) {
            android.util.Log.w("Hume", "media sessions: " + e);
        }
    }

    @Override
    public void onListenerDisconnected() {
        instance = null;
        if (msm != null) msm.removeOnActiveSessionsChangedListener(sessions);
        tracked.clear();
    }

    void onSessions(List<MediaController> list) {
        if (list == null) return;
        for (MediaController mc : list) {
            if (tracked.containsKey(mc.getSessionToken())) continue;
            MediaController.Callback cb = new MediaController.Callback() {
                @Override public void onMetadataChanged(MediaMetadata m) {
                    Store.record(MediaWatchService.this, mc.getPackageName(), m, mc.getPlaybackState());
                }

                @Override public void onPlaybackStateChanged(PlaybackState s) {
                    Store.record(MediaWatchService.this, mc.getPackageName(), mc.getMetadata(), s);
                }

                @Override public void onSessionDestroyed() {
                    // The last position, for apps that close without pausing first.
                    Store.record(MediaWatchService.this, mc.getPackageName(), mc.getMetadata(), mc.getPlaybackState());
                    tracked.remove(mc.getSessionToken());
                }
            };
            mc.registerCallback(cb);
            tracked.put(mc.getSessionToken(), cb);
            Store.record(this, mc.getPackageName(), mc.getMetadata(), mc.getPlaybackState());
        }
    }

    static boolean granted(Context c) {
        android.app.NotificationManager nm = c.getSystemService(android.app.NotificationManager.class);
        return nm != null && nm.isNotificationListenerAccessGranted(new ComponentName(c, MediaWatchService.class));
    }

    /** The Continue Watching list on disk (files/continue.json + artwork in files/cw/). */
    static final class Store {
        static final int MAX = 20;
        /** Shorter than this is a trailer, ad, clip or short, not something to come back to. */
        static final long MIN_DURATION_MS = WatchNext.MIN_DURATION_MS;
        /** Played less than this: sampled, not watched. With no duration (live, some apps) the
         *  10-minute bar applies to what was played instead. */
        static final long MIN_WATCHED_MS = 60_000, MIN_WATCHED_NO_DURATION_MS = WatchNext.MIN_DURATION_MS;
        /** Music apps publish sessions too; they belong to the apps, not Continue Watching. */
        static final Set<String> AUDIO_APPS = new HashSet<>(Arrays.asList("com.spotify.tv.android",
                "com.google.android.youtube.tvmusic", "com.jio.media.jiobeats", "com.gaana", "com.bsbportal.music",
                "com.amazon.mp3", "com.apple.android.music", "com.wynk.music"));

        static File file(Context c) {
            return new File(c.getFilesDir(), Profiles.continueFile(Profiles.current(c).id));
        }

        static synchronized JSONArray read(Context c) {
            try {
                return new JSONArray(new String(Suggestions.readAll(new FileInputStream(file(c))), StandardCharsets.UTF_8));
            } catch (Exception e) {
                return new JSONArray();
            }
        }

        static synchronized void write(Context c, JSONArray a) {
            try (OutputStream os = new FileOutputStream(file(c))) {
                os.write(a.toString().getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) {
            }
        }

        static String text(MediaMetadata m, String... keys) {
            for (String k : keys) {
                CharSequence s = m.getText(k);
                if (s != null && s.length() > 0) return s.toString();
            }
            return "";
        }

        static synchronized void record(Context c, String pkg, MediaMetadata m, PlaybackState s) {
            if (m == null || pkg == null || pkg.equals(c.getPackageName())) return;
            String title = text(m, MediaMetadata.METADATA_KEY_DISPLAY_TITLE, MediaMetadata.METADATA_KEY_TITLE);
            if (title.isEmpty()) return;
            String sub = text(m, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, MediaMetadata.METADATA_KEY_ARTIST,
                    MediaMetadata.METADATA_KEY_ALBUM);
            long dur = m.getLong(MediaMetadata.METADATA_KEY_DURATION);
            long pos = -1;
            if (s != null) {
                pos = s.getPosition();
                if (s.getState() == PlaybackState.STATE_PLAYING && s.getLastPositionUpdateTime() > 0) {
                    pos += (long) ((SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime()) * s.getPlaybackSpeed());
                }
            }
            if (isAudioApp(c, pkg) || s != null && s.getState() == PlaybackState.STATE_ERROR) return;
            String key = pkg + "|" + title;
            JSONArray old = read(c);
            JSONArray out = new JSONArray();
            JSONObject prev = null;
            for (int i = 0; i < old.length(); i++) {
                JSONObject o = old.optJSONObject(i);
                if (o == null) continue;
                if (key.equals(o.optString("key"))) prev = o;
                else out.put(o);
            }
            // Finished (credits: the last 5% or 2 minutes): drop it from the list.
            if (dur > 0 && (pos >= dur * 0.95 || dur - pos < 2 * 60_000)) {
                if (prev != null) write(c, out);
                return;
            }
            if (!worthKeeping(pos, dur)) {
                // A short sample of something already listed keeps its entry; nothing new is added.
                return;
            }
            try {
                String art = prev != null ? prev.optString("art") : "";
                Bitmap bmp = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
                if (bmp == null) bmp = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                if (bmp == null) bmp = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
                if (bmp != null && bmp.getWidth() >= 160) {
                    File dir = new File(c.getFilesDir(), "cw");
                    dir.mkdirs();
                    File f = new File(dir, Integer.toHexString(key.hashCode()) + ".jpg");
                    float sc = Math.min(1f, 960f / bmp.getWidth());
                    Bitmap small = sc < 1f ? Bitmap.createScaledBitmap(bmp, Math.round(bmp.getWidth() * sc),
                            Math.round(bmp.getHeight() * sc), true) : bmp;
                    try (OutputStream os = new FileOutputStream(f)) {
                        small.compress(Bitmap.CompressFormat.JPEG, 88, os);
                    }
                    art = f.getAbsolutePath();
                } else if (art.isEmpty()) {
                    String uri = text(m, MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
                            MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI);
                    if (uri.startsWith("http")) art = uri;
                }
                JSONObject e = new JSONObject().put("key", key).put("pkg", pkg).put("title", title).put("sub", sub)
                        .put("art", art).put("pos", pos).put("dur", dur).put("ts", System.currentTimeMillis());
                JSONArray res = new JSONArray().put(e);
                for (int i = 0; i < out.length() && res.length() < MAX; i++) res.put(out.get(i));
                write(c, res);
            } catch (Exception ignored) {
            }
        }

        /**
         * Only episode- or feature-length video actually watched: known duration of at least
         * 10 minutes and a minute in, or (live or unknown duration) 10 minutes in.
         */
        static boolean worthKeeping(long pos, long dur) {
            if (dur > 0) return dur >= MIN_DURATION_MS && pos >= MIN_WATCHED_MS;
            return pos >= MIN_WATCHED_NO_DURATION_MS;
        }

        static boolean isAudioApp(Context c, String pkg) {
            if (AUDIO_APPS.contains(pkg)) return true;
            if (android.os.Build.VERSION.SDK_INT < 26) return false;
            try {
                return c.getPackageManager().getApplicationInfo(pkg, 0).category
                        == android.content.pm.ApplicationInfo.CATEGORY_AUDIO;
            } catch (Exception e) {
                return false;
            }
        }

        /** Drops entries last played more than 30 days ago (and their saved artwork). */
        static synchronized JSONArray pruneExpired(Context c) {
            JSONArray a = read(c), keep = new JSONArray();
            long now = System.currentTimeMillis();
            boolean changed = false;
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                if (now - o.optLong("ts", now) > EXPIRE_MS) {
                    changed = true;
                    String art = o.optString("art");
                    if (art.startsWith("/")) new File(art).delete();
                } else {
                    keep.put(o);
                }
            }
            if (changed) write(c, keep);
            return keep;
        }

        static synchronized void remove(Context c, String key) {
            JSONArray old = read(c), out = new JSONArray();
            for (int i = 0; i < old.length(); i++) {
                JSONObject o = old.optJSONObject(i);
                if (o != null && !key.equals(o.optString("key"))) out.put(o);
            }
            write(c, out);
        }

        static synchronized void clear(Context c) {
            write(c, new JSONArray());
        }

        /** Newest first, as carousel items (pkg set, progress 0..1 or -1). */
        /** Titles not played again for this long drop off Continue Watching. */
        static final long EXPIRE_MS = 30L * 24 * 60 * 60 * 1000;

        static List<Suggestions.Item> list(Context c) {
            JSONArray a = pruneExpired(c);
            List<Suggestions.Item> out = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null || !Suggestions.installed(c, o.optString("pkg"))) continue;
                Suggestions.Item it = new Suggestions.Item();
                it.id = o.optString("key").hashCode();
                it.type = "continue";
                it.pkg = o.optString("pkg");
                it.key = o.optString("key");
                it.title = o.optString("title");
                it.overview = o.optString("sub");
                it.backdrop = o.optString("art");
                it.year = "";
                it.genres = "";
                long pos = o.optLong("pos", -1), dur = o.optLong("dur", 0);
                it.progress = dur > 0 && pos >= 0 ? Math.min(1f, pos / (float) dur) : -1f;
                it.remainingMin = dur > 0 && pos >= 0 ? (int) Math.max(1, (dur - pos) / 60000) : -1;
                it.played = o.optLong("ts");
                out.add(it);
            }
            return out;
        }
    }
}
