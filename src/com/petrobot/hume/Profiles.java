package com.petrobot.hume;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Launcher profiles (like Apple TV's "Who's watching?"). Each profile keeps its own home layout
 * (order, hidden apps), background, top-shelf tab and Continue Watching list. No lock.
 * The first profile ("p1") uses the original storage, so nothing is lost when profiles are added.
 */
final class Profiles {
    private Profiles() { }

    /** At most three profiles. */
    static final int MAX = 3;

    static boolean canAdd(Context c) {
        return list(c).size() < MAX;
    }

    static final class Profile {
        String id, name, emoji = "";
        int color;
    }

    /** Avatar emoji; each profile's must be unique. */
    static final String[] EMOJIS = {"\uD83D\uDE00", "\uD83D\uDE0E", "\uD83E\uDD20", "\uD83E\uDD73", "\uD83E\uDD8A",
            "\uD83D\uDC3C", "\uD83D\uDC2F", "\uD83E\uDD81", "\uD83D\uDC38", "\uD83D\uDC35",
            "\uD83D\uDC28", "\uD83E\uDD84", "\uD83D\uDC36", "\uD83D\uDC31", "\uD83D\uDC3B",
            "\uD83D\uDC30", "\uD83D\uDC7B", "\uD83E\uDD16", "\uD83D\uDC7D", "\uD83C\uDF83"};
    static final String[] EMOJI_NAMES = {"Grin", "Cool", "Cowboy", "Party", "Fox", "Panda", "Tiger", "Lion", "Frog",
            "Monkey", "Koala", "Unicorn", "Dog", "Cat", "Bear", "Bunny", "Ghost", "Robot", "Alien", "Pumpkin"};

    /** Emoji not used by any profile other than `exceptId`. */
    static List<String> freeEmojis(Context c, String exceptId) {
        java.util.Set<String> used = new java.util.HashSet<>();
        for (Profile p : list(c)) if (!p.id.equals(exceptId)) used.add(p.emoji);
        List<String> out = new ArrayList<>();
        for (String e : EMOJIS) if (!used.contains(e)) out.add(e);
        return out;
    }

    static String emojiName(String e) {
        for (int i = 0; i < EMOJIS.length; i++) if (EMOJIS[i].equals(e)) return EMOJI_NAMES[i];
        return "";
    }

    /** Monogram gradients (top, bottom), Apple-style. */
    /** Pastel avatar discs (Apple's profile bubbles), with darker initials. {background, text} */
    static final int[][] COLORS = {
            {0xFFA9D8F5, 0xFF1F5A82},   // sky
            {0xFFF8B9CF, 0xFF8A2A51},   // pink
            {0xFFB4E8C2, 0xFF1E6B3A},   // mint
            {0xFFF6E59A, 0xFF7A5A07},   // lemon
            {0xFFD3C4F7, 0xFF4B2E99},   // lavender
            {0xFFFFCFA8, 0xFF8A4512},   // peach
            {0xFFD9DCE3, 0xFF3A3D45}};  // silver
    static final String[] COLOR_NAMES = {"Sky", "Pink", "Mint", "Lemon", "Lavender", "Peach", "Silver"};

    static SharedPreferences store(Context c) {
        return c.getSharedPreferences("profiles", Context.MODE_PRIVATE);
    }

    static List<Profile> list(Context c) {
        List<Profile> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(store(c).getString("list", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Profile p = new Profile();
                p.id = o.getString("id");
                p.name = o.getString("name");
                p.color = o.optInt("color", 0);
                p.emoji = o.optString("emoji", "");
                out.add(p);
            }
        } catch (Exception ignored) {
        }
        if (out.isEmpty()) {
            Profile p = new Profile();
            p.id = "p1";
            p.name = "Me";
            p.color = 0;
            out.add(p);
        }
        boolean fixed = false;
        java.util.Set<String> used = new java.util.HashSet<>();
        for (Profile p : out) {
            if (p.emoji.isEmpty() || used.contains(p.emoji)) {
                for (String e : EMOJIS) {
                    if (!used.contains(e) && !emojiTakenLater(out, p, e)) {
                        p.emoji = e;
                        fixed = true;
                        break;
                    }
                }
            }
            used.add(p.emoji);
        }
        if (fixed || store(c).getString("list", "").isEmpty()) save(c, out);
        return out;
    }

    private static boolean emojiTakenLater(List<Profile> ps, Profile self, String e) {
        boolean after = false;
        for (Profile p : ps) {
            if (p == self) {
                after = true;
                continue;
            }
            if (after && e.equals(p.emoji)) return true;
        }
        return false;
    }

    static void save(Context c, List<Profile> ps) {
        JSONArray a = new JSONArray();
        try {
            for (Profile p : ps) {
                a.put(new JSONObject().put("id", p.id).put("name", p.name).put("color", p.color).put("emoji", p.emoji));
            }
        } catch (Exception ignored) {
        }
        store(c).edit().putString("list", a.toString()).apply();
    }

    static Profile current(Context c) {
        String id = store(c).getString("current", "p1");
        List<Profile> ps = list(c);
        for (Profile p : ps) if (p.id.equals(id)) return p;
        return ps.get(0);
    }

    static void setCurrent(Context c, String id) {
        store(c).edit().putString("current", id).apply();
    }

    static Profile add(Context c, String name) {
        List<Profile> ps = list(c);
        if (ps.size() >= MAX) return null;
        int n = 1;
        for (Profile p : ps) {
            try {
                n = Math.max(n, Integer.parseInt(p.id.substring(1)) + 1);
            } catch (NumberFormatException ignored) {
            }
        }
        Profile p = new Profile();
        p.id = "p" + n;
        p.name = name;
        p.color = ps.size() % COLORS.length;
        java.util.Set<String> used = new java.util.HashSet<>();
        for (Profile x : ps) used.add(x.emoji);
        for (String e : EMOJIS) {
            if (!used.contains(e)) {
                p.emoji = e;
                break;
            }
        }
        ps.add(p);
        save(c, ps);
        return p;
    }

    static void update(Context c, Profile changed) {
        List<Profile> ps = list(c);
        for (Profile p : ps) {
            if (p.id.equals(changed.id)) {
                p.name = changed.name;
                p.color = changed.color;
                p.emoji = changed.emoji;
            }
        }
        save(c, ps);
    }

    /** Removes a profile and its layout and Continue Watching data (never the last profile). */
    static void delete(Context c, String id) {
        List<Profile> ps = list(c);
        if (ps.size() <= 1) return;
        List<Profile> keep = new ArrayList<>();
        for (Profile p : ps) if (!p.id.equals(id)) keep.add(p);
        save(c, keep);
        c.getSharedPreferences(prefsName(id), Context.MODE_PRIVATE).edit().clear().apply();
        if (current(c).id.equals(id) || store(c).getString("current", "p1").equals(id)) setCurrent(c, keep.get(0).id);
    }

    static boolean askOnWake(Context c) {
        return store(c).getBoolean("ask_on_wake", true);
    }

    static void setAskOnWake(Context c, boolean on) {
        store(c).edit().putBoolean("ask_on_wake", on).apply();
    }

    static String prefsName(String id) {
        return id.equals("p1") ? "home" : "home_" + id;
    }


    /** Layout/background preferences of the current profile. */
    static SharedPreferences homePrefs(Context c) {
        return c.getSharedPreferences(prefsName(current(c).id), Context.MODE_PRIVATE);
    }

    static String initials(String name) {
        String[] parts = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty() && sb.length() < 2) sb.appendCodePoint(p.codePointAt(0));
        }
        return sb.length() == 0 ? "?" : sb.toString().toUpperCase();
    }

    /** Profile bubble: pastel disc with the profile's emoji (initials as a fallback). */
    static Bitmap avatar(Context c, Profile p, int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        int[] g = COLORS[Math.max(0, Math.min(COLORS.length - 1, p.color))];
        paint.setColor(g[0]);
        float r = size / 2f;
        cv.drawCircle(r, r, r, paint);
        paint.setColor(g[1]);
        paint.setTextAlign(Paint.Align.CENTER);
        String t;
        if (!p.emoji.isEmpty()) {
            t = p.emoji;
            paint.setTypeface(android.graphics.Typeface.DEFAULT);
            paint.setTextSize(size * 0.56f);
        } else {
            t = initials(p.name);
            paint.setTypeface(Ui.font(c, "semibold"));
            paint.setTextSize(size * (t.length() > 1 ? 0.38f : 0.46f));
        }
        Paint.FontMetrics fm = paint.getFontMetrics();
        cv.drawText(t, r, r - (fm.ascent + fm.descent) / 2f, paint);
        return b;
    }
}
