package com.petrobot.hume;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * tvOS-style settings: a main list of sections, each opening its own page, with the page's icon
 * and title on the left and its rows on the right; Back returns to the main list.
 *
 * A TMDB token can also be supplied over adb:
 *   adb shell am start -n com.petrobot.hume/.SettingsActivity --es tmdb_token &lt;token&gt;
 */
public class SettingsActivity extends Activity {

    LinearLayout list;
    final List<Runnable> refreshers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Clicks.init(this);
        String token = getIntent().getStringExtra("tmdb_token");
        if (token != null && !token.trim().isEmpty()) {
            Suggestions.prefs(this).edit().putString(Suggestions.K_TOKEN, token.trim()).apply();
            Suggestions.changed(this);
            Toast.makeText(this, "TMDB connected", Toast.LENGTH_SHORT).show();
        }
        build();
        String pg = getIntent().getStringExtra("page");  // adb: --es page "Top Shelf"
        if (pg != null) show(pg);
    }

    // tvOS-style pages: the main list opens a page per section; Back returns to the main list.
    static final String ROOT = "root", PROFILES = "Profiles", SHELF = "Top Shelf", PRESENT = "Presentation",
            INPUTS = "Inputs", WEATHER = "Weather", WATCHING = "Continue Watching", HOME = "Home",
            PERMS = "Permissions", ABOUT = "About";

    FrameLayout leftPane;
    ScrollView scroller;
    String page = ROOT, lastPage = null;
    final java.util.Map<String, android.view.View> navRows = new java.util.HashMap<>();

    void build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B0B10);
        LinearLayout split = new LinearLayout(this);
        split.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(split, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        leftPane = new FrameLayout(this);
        split.addView(leftPane, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        scroller = new ScrollView(this);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setClipToPadding(false);
        scroller.setClipChildren(false);
        scroller.setPadding(0, Ui.px(this, 60), Ui.px(this, 44), Ui.px(this, 60));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        list.setClipToPadding(false);
        list.setPadding(Ui.px(this, 12), 0, Ui.px(this, 12), 0);
        scroller.addView(list);
        split.addView(scroller, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.25f));
        setContentView(root);
        show(ROOT);
    }

    /** Builds one page: its icon and title on the left, its rows on the right. */
    void show(String p) {
        lastPage = page;
        page = p;
        list.removeAllViews();
        refreshers.clear();
        navRows.clear();
        buildLeft(p);
        switch (p) {
            case PROFILES: pageProfiles(); break;
            case SHELF: pageShelf(); break;
            case PRESENT: pagePresentation(); break;
            case INPUTS: pageInputs(); break;
            case WEATHER: pageWeather(); break;
            case WATCHING: pageWatching(); break;
            case HOME: pageHome(); break;
            case PERMS: pagePermissions(); break;
            case ABOUT: pageAbout(); break;
            default: pageRoot(); break;
        }
        scroller.scrollTo(0, 0);
        // Returning to the main list puts focus back on the section you came from.
        android.view.View focus = p.equals(ROOT) && lastPage != null ? navRows.get(lastPage) : null;
        if (focus == null) {
            for (int i = 0; i < list.getChildCount(); i++) {
                if (list.getChildAt(i).isFocusable()) {
                    focus = list.getChildAt(i);
                    break;
                }
            }
        }
        if (focus != null) focus.requestFocus();
        list.setAlpha(0f);
        list.setTranslationX(Ui.px(this, p.equals(ROOT) ? -16 : 16));
        list.animate().alpha(1f).translationX(0).setDuration(220).start();
    }

    @Override
    public void onBackPressed() {
        if (!page.equals(ROOT)) show(ROOT);
        else super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAll();  // e.g. a permission granted on a system screen
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        refreshAll();
    }

    /** Left side: the page's icon (Petrobot on the main list), its title, and the brand line. */
    void buildLeft(String p) {
        leftPane.removeAllViews();
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setGravity(Gravity.CENTER_HORIZONTAL);
        android.view.View icon;
        if (p.equals(ROOT)) {
            // Petrobot's mark on a white 5:3 tile, the same shape and radius as the Home cards.
            FrameLayout tile = new FrameLayout(this);
            GradientDrawable plate = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0xFFFFFFFF, 0xFFE9EAEE});
            plate.setCornerRadius(Ui.px(this, 12));
            tile.setBackground(plate);
            ImageView mark = new ImageView(this);
            mark.setImageResource(R.drawable.petrobot_mark);
            mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
            tile.addView(mark, new FrameLayout.LayoutParams(Ui.px(this, 58), Ui.px(this, 58), Gravity.CENTER));
            icon = tile;
        } else {
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(pageIcon(p, Ui.px(this, 150), Ui.px(this, 90)));
            iv.setClipToOutline(true);
            GradientDrawable shape = new GradientDrawable();
            shape.setCornerRadius(Ui.px(this, 12));
            iv.setBackground(shape);
            icon = iv;
        }
        icon.setElevation(Ui.px(this, 8));
        left.addView(icon, new LinearLayout.LayoutParams(Ui.px(this, 150), Ui.px(this, 90)));

        TextView title = new TextView(this);
        title.setText(p.equals(ROOT) ? "Settings" : p);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Ui.font(this, "display"));
        title.setLetterSpacing(-0.02f);
        Ui.textSize(title, p.equals(ROOT) ? 34 : 28);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Ui.px(this, 22);
        left.addView(title, tlp);

        if (p.equals(ROOT)) {
            TextView by = new TextView(this);
            by.setText("Hume Launcher by Petrobot");
            by.setTextColor(0x8CFFFFFF);
            by.setTypeface(Ui.font(this, "regular"));
            Ui.textSize(by, 13);
            by.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            blp.topMargin = Ui.px(this, 4);
            left.addView(by, blp);
        }
        leftPane.addView(left, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
    }

    /** A 5:3 icon for a section page: a gradient tile with a white glyph (or the section's own art). */
    android.graphics.Bitmap pageIcon(String p, int w, int h) {
        if (p.equals(PRESENT)) return com.petrobot.hume.present.PresentIcon.banner(w, h);
        int[] grad;
        String glyph;
        switch (p) {
            case PROFILES: grad = new int[]{0xFFBF5AF2, 0xFF7D3CCB}; glyph = null; break;
            case SHELF: grad = new int[]{0xFF64D2FF, 0xFF0A84FF}; glyph = "shelf"; break;
            case INPUTS: grad = new int[]{0xFF6E6E73, 0xFF2C2C2E}; glyph = "input"; break;
            case WEATHER: grad = new int[]{0xFF5AC8FA, 0xFF2D6BD8}; glyph = null; break;
            case WATCHING: grad = new int[]{0xFFFF6482, 0xFFD6305A}; glyph = "present"; break;
            case HOME: grad = new int[]{0xFF30D158, 0xFF1E9E48}; glyph = "house"; break;
            case PERMS: grad = new int[]{0xFF8E8E93, 0xFF48484A}; glyph = "shield"; break;
            default: grad = new int[]{0xFF8E8E93, 0xFF3A3A3C}; glyph = "gear"; break;
        }
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(b);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new android.graphics.LinearGradient(0, 0, 0, h, grad[0], grad[1], android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, paint);
        android.graphics.Bitmap g;
        if (p.equals(PROFILES)) g = Profiles.avatar(this, Profiles.current(this), (int) (h * 0.62f));
        else if (p.equals(WEATHER)) g = WeatherIcons.get(0, true, (int) (h * 0.7f));
        else g = Glyphs.get(glyph, (int) (h * 0.56f), Color.WHITE);
        c.drawBitmap(g, (w - g.getWidth()) / 2f, (h - g.getHeight()) / 2f, null);
        return b;
    }

    // ---------------------------------------------------------------- pages

    void pageRoot() {
        nav(PROFILES, () -> Profiles.current(this).name);
        nav(SHELF, () -> {
            switch (Suggestions.shelf(this)) {
                case Suggestions.SHELF_RECS: return "Recommendations";
                case Suggestions.SHELF_NEWS: return "News";
                case Suggestions.SHELF_NONE: return "Off";
                default: return "Recommendations & News";
            }
        });
        nav(PRESENT, () -> com.petrobot.hume.present.Presenter.enabled(this) ? "On" : "Off");
        if (!Inputs.list(this).isEmpty()) nav(INPUTS, () -> Inputs.showCard(this) ? "On" : "Off");
        nav(WEATHER, () -> !Weather.enabled(this) ? "Off" : Weather.place(this).isEmpty() ? "On" : Weather.place(this));
        nav(WATCHING, () -> !Profiles.homePrefs(this).getBoolean("cw_enabled", true) ? "Off" : Perms.hasTvListings(this) ? "On"
                : WatchNext.supported() ? "Needs permission" : "Not on this TV");
        nav(HOME, () -> {
            String k = Profiles.homePrefs(this).getString("wallpaper", "aurora");
            int i = java.util.Arrays.asList(MainActivity.WALL_KEYS).indexOf(k);
            return MainActivity.WALL_NAMES[Math.max(0, i)];
        });
        nav(PERMS, () -> {
            int missing = (Perms.hasListener(this) || !Perms.listenerSupported(this) ? 0 : 1) + (Perms.hasLocation(this) ? 0 : 1)
                    + (Perms.hasTvListings(this) || !WatchNext.supported() ? 0 : 1)
                    + (Perms.hasAdmin(this) || !Perms.adminSupported(this) ? 0 : 1) + (Perms.isDefaultHome(this) ? 0 : 1);
            return missing == 0 ? "All allowed" : missing + " to allow";
        });
        nav(ABOUT, () -> "Version " + version());
        row("TV Settings", () -> "", () -> Suggestions.start(this, new Intent(Settings.ACTION_SETTINGS)));
    }

    void pageProfiles() {
        row("Current profile", () -> Profiles.current(this).name, () -> {
            startActivity(new Intent(this, MainActivity.class).putExtra("pick_profile", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
        });
        row("Show profiles on wake", () -> Profiles.askOnWake(this) ? "On" : "Off",
                () -> Profiles.setAskOnWake(this, !Profiles.askOnWake(this)));
    }

    void pageShelf() {
        row("Show in carousel", () -> {
            switch (Suggestions.shelf(this)) {
                case Suggestions.SHELF_RECS: return "Recommendations";
                case Suggestions.SHELF_NEWS: return "News";
                case Suggestions.SHELF_NONE: return "Nothing";
                default: return "Both";
            }
        }, () -> {
            String[] keys = {Suggestions.SHELF_BOTH, Suggestions.SHELF_RECS, Suggestions.SHELF_NEWS, Suggestions.SHELF_NONE};
            String[] names = {"Recommendations & News", "Recommendations", "News", "Nothing"};
            int sel = Math.max(0, java.util.Arrays.asList(keys).indexOf(Suggestions.shelf(this)));
            dialog("Show in carousel").setSingleChoiceItems(names, sel, (d, w) -> {
                Suggestions.setShelf(this, keys[w]);
                d.dismiss();
                show(SHELF);
            }).show();
        });
        if (Suggestions.showRecs(this)) {
            section("Recommendations");
            row("Source", () -> Suggestions.sourceName(Suggestions.source(this)), this::chooseSource);
            row("Content", () -> {
                String t = Suggestions.types(this);
                return t.equals("movie") ? "Movies" : t.equals("tv") ? "TV Series" : "Movies & Series";
            }, this::chooseTypes);
        }
        if (Suggestions.showRecs(this) && Suggestions.source(this).equals(Suggestions.SRC_STREMIO)) {
            row("Opens in", () -> {
                Suggestions.Provider p = Suggestions.stremioApp(this);
                return Suggestions.installed(this, p.pkg) ? p.name : "Stremio (not installed)";
            }, () -> Toast.makeText(this, "Titles open in Stremio, or in Nuvio when only Nuvio is installed.",
                    Toast.LENGTH_LONG).show());
        } else if (Suggestions.showRecs(this)) {
            row("Streaming services", () -> {
                int n = Suggestions.providers(this).size();
                return n == Suggestions.PROVIDERS.size() ? "All" : n + " selected";
            }, this::chooseProviders);
            row("Languages", () -> {
                Set<String> l = Suggestions.languages(this);
                if (l.isEmpty()) return "Any";
                List<String> names = new ArrayList<>();
                for (int i = 0; i < Suggestions.LANG_CODES.length; i++) {
                    if (l.contains(Suggestions.LANG_CODES[i])) names.add(Suggestions.LANG_NAMES[i]);
                }
                return names.size() > 2 ? names.size() + " languages" : String.join(", ", names);
            }, this::chooseLanguages);
            row("TMDB token", () -> Suggestions.hasToken(this) ? "Connected" : "Not set", this::enterToken);
        }
        if (Suggestions.showNews(this)) {
            section("News");
            row("Newsrooms", () -> {
                int n = News.sources(this).size();
                return n == News.SOURCES.size() ? "All" : n + " selected";
            }, this::chooseNewsrooms);
            row("Local news for", () -> News.city(this).isEmpty() ? "Unknown" : News.city(this), () ->
                    Toast.makeText(this, "Local news follows the weather location (Settings \u2192 Weather \u2192 Location).",
                            Toast.LENGTH_LONG).show());
            row("Update news every", () -> News.refreshMinutes(this) + " minutes", () -> {
                int[] mins = News.REFRESH_CHOICES;
                String[] names = new String[mins.length];
                int sel = 1;
                for (int i = 0; i < mins.length; i++) {
                    names[i] = mins[i] + " minutes" + (mins[i] == 30 ? "  (default)" : "");
                    if (mins[i] == News.refreshMinutes(this)) sel = i;
                }
                dialog("Update news every").setSingleChoiceItems(names, sel, (d, w) -> {
                    Suggestions.prefs(this).edit().putInt(News.K_REFRESH, mins[w]).apply();
                    refreshAll();
                    d.dismiss();
                }).show();
            });
        }
        if (Suggestions.enabled(this)) {
            section("Carousel");
            row("Auto-advance", () -> {
                int sec = Suggestions.advanceSeconds(this);
                return sec == 0 ? "Off" : "Every " + sec + " s";
            }, this::chooseAdvance);
            row("Refresh now", () -> "", () -> {
                Suggestions.changed(this);
                Toast.makeText(this, "The carousel will refresh on Home", Toast.LENGTH_SHORT).show();
            });
        }
    }

    void chooseNewsrooms() {
        String[] names = new String[News.SOURCES.size()];
        boolean[] on = new boolean[names.length];
        Set<String> cur = News.sources(this);
        for (int i = 0; i < names.length; i++) {
            names[i] = News.SOURCES.get(i).name;
            on[i] = cur.contains(News.SOURCES.get(i).id);
        }
        dialog("Newsrooms").setMultiChoiceItems(names, on, (d, w, checked) -> on[w] = checked)
                .setPositiveButton("Done", (d, w) -> {
                    Set<String> ids = new java.util.HashSet<>();
                    for (int i = 0; i < on.length; i++) if (on[i]) ids.add(News.SOURCES.get(i).id);
                    if (ids.isEmpty()) ids.add("toi");
                    News.setSources(this, ids);
                    Suggestions.changed(this);
                    refreshAll();
                }).setNegativeButton("Cancel", null).show();
    }

    void pagePresentation() {
        row("Presentation mode", () -> com.petrobot.hume.present.Presenter.enabled(this) ? "On" : "Off", () -> {
            boolean on = !com.petrobot.hume.present.Presenter.enabled(this);
            com.petrobot.hume.present.Presenter.set(this, com.petrobot.hume.present.Presenter.KEY_ENABLED, on);
            if (on) Toast.makeText(this, "Presentation is on. Send content from a browser.", Toast.LENGTH_LONG).show();
        });
        row("Start on boot", () -> com.petrobot.hume.present.Presenter.startOnBoot(this) ? "On" : "Off", () ->
                com.petrobot.hume.present.Presenter.set(this, com.petrobot.hume.present.Presenter.KEY_BOOT,
                        !com.petrobot.hume.present.Presenter.startOnBoot(this)));
        row("Keep screen awake", () -> com.petrobot.hume.present.Presenter.keepAwake(this) ? "On" : "Off", () ->
                com.petrobot.hume.present.Presenter.set(this, com.petrobot.hume.present.Presenter.KEY_AWAKE,
                        !com.petrobot.hume.present.Presenter.keepAwake(this)));
        row("Address & pairing code", () -> com.petrobot.hume.present.Presenter.enabled(this)
                ? com.petrobot.hume.present.Presenter.address().replace("http://", "") : "Turn on first", this::showPresentInfo);
        row("Start presentation now", () -> "", () -> {
            if (!com.petrobot.hume.present.Presenter.enabled(this)) {
                com.petrobot.hume.present.Presenter.set(this, com.petrobot.hume.present.Presenter.KEY_ENABLED, true);
            }
            com.petrobot.hume.present.Presenter.open(this);
        });
    }

    void pageInputs() {
        row("Inputs card on Home", () -> Inputs.showCard(this) ? "On" : "Off", () ->
                Inputs.prefs(this).edit().putBoolean(Inputs.K_CARD, !Inputs.showCard(this)).apply());
        section("This TV");
        for (Inputs.Input in : Inputs.list(this)) {
            row(in.label, () -> in.disconnected ? "Not connected" : "", () -> {
                if (!Inputs.open(this, in)) Toast.makeText(this, "Can't switch to " + in.label, Toast.LENGTH_SHORT).show();
            });
        }
    }

    void pageWeather() {
        row("Show weather", () -> Weather.enabled(this) ? "On" : "Off", () -> {
            Weather.prefs(this).edit().putBoolean(Weather.K_ENABLED, !Weather.enabled(this)).apply();
            Weather.changed(this);
        });
        row("Location", () -> Weather.manual(this) ? Weather.place(this)
                : Weather.place(this).isEmpty() ? "Automatic" : "Automatic \u00b7 " + Weather.place(this), this::chooseLocation);
        row("Temperature", () -> {
            String u = Weather.prefs(this).getString(Weather.K_UNITS, "auto");
            return u.equals("c") ? "Celsius" : u.equals("f") ? "Fahrenheit" : "Automatic";
        }, () -> {
            String[] keys = {"auto", "c", "f"};
            String[] names = {"Automatic", "Celsius (\u00b0C)", "Fahrenheit (\u00b0F)"};
            String cur = Weather.prefs(this).getString(Weather.K_UNITS, "auto");
            int sel = cur.equals("c") ? 1 : cur.equals("f") ? 2 : 0;
            dialog("Temperature").setSingleChoiceItems(names, sel, (d, w) -> {
                Weather.prefs(this).edit().putString(Weather.K_UNITS, keys[w]).apply();
                Weather.changed(this);
                refreshAll();
                d.dismiss();
            }).show();
        });
        section("Updates");
        row("Source", () -> Weather.sourceName(Weather.source(this)), () -> {
            String[] keys = Weather.SOURCES;
            String[] names = {"Open-Meteo", "MET Norway (Yr)", "wttr.in  (3 days)"};
            int sel = java.util.Arrays.asList(keys).indexOf(Weather.source(this));
            dialog("Weather source").setSingleChoiceItems(names, Math.max(0, sel), (d, w) -> {
                Weather.prefs(this).edit().putString(Weather.K_SOURCE, keys[w]).apply();
                Weather.changed(this);
                refreshAll();
                d.dismiss();
                Toast.makeText(this, "If it can't be reached, the other sources are used.", Toast.LENGTH_LONG).show();
            }).show();
        });
        row("Update every", () -> Weather.refreshMinutes(this) + " minutes", () -> {
            int[] mins = Weather.REFRESH_CHOICES;
            String[] names = new String[mins.length];
            int sel = 1;
            for (int i = 0; i < mins.length; i++) {
                names[i] = mins[i] + " minutes" + (mins[i] == 15 ? "  (default)" : "");
                if (mins[i] == Weather.refreshMinutes(this)) sel = i;
            }
            dialog("Update weather every").setSingleChoiceItems(names, sel, (d, w) -> {
                Weather.prefs(this).edit().putInt(Weather.K_REFRESH, mins[w]).apply();
                refreshAll();
                d.dismiss();
            }).show();
        });
    }

    void pageWatching() {
        row("Show Continue Watching", () -> Profiles.homePrefs(this).getBoolean("cw_enabled", true) ? "On" : "Off", () -> {
            boolean on = !Profiles.homePrefs(this).getBoolean("cw_enabled", true);
            Profiles.homePrefs(this).edit().putBoolean("cw_enabled", on).apply();
            if (!on) Toast.makeText(this, "Continue Watching titles won\u2019t appear in the carousel", Toast.LENGTH_SHORT).show();
        });
        row("Apps\u2019 Watch Next lists", () -> Perms.hasTvListings(this) ? "Allowed"
                : WatchNext.supported() ? "Allow\u2026" : "Not on this TV", () -> Perms.requestTvListings(this));
        row("Clear history", () -> "", () -> {
            WatchNext.dismissAll(this);
            Toast.makeText(this, "Continue Watching cleared", Toast.LENGTH_SHORT).show();
        });
    }

    void pageHome() {
        row("Home screen", () -> Perms.isDefaultHome(this) ? "Hume Launcher" : "Make Hume the home screen\u2026",
                () -> Perms.requestDefaultHome(this));
        row("Background", () -> {
            String k = Profiles.homePrefs(this).getString("wallpaper", "aurora");
            int i = java.util.Arrays.asList(MainActivity.WALL_KEYS).indexOf(k);
            return MainActivity.WALL_NAMES[Math.max(0, i)];
        }, () -> {
            String k = Profiles.homePrefs(this).getString("wallpaper", "aurora");
            int cur = Math.max(0, java.util.Arrays.asList(MainActivity.WALL_KEYS).indexOf(k));
            dialog("Background").setSingleChoiceItems(MainActivity.WALL_NAMES, cur, (d, w) -> {
                Profiles.homePrefs(this).edit().putString("wallpaper", MainActivity.WALL_KEYS[w]).apply();
                refreshAll();
                d.dismiss();
            }).show();
        });
        row("Navigation sounds", () -> Clicks.enabled(this) ? "On" : "Off",
                () -> Clicks.setEnabled(this, !Clicks.enabled(this)));
        row("Full-screen carousel when idle", () -> {
            int sec = Profiles.homePrefs(this).getInt("idle_sec", 20);
            return sec == 0 ? "Off" : "After " + sec + " s";
        }, () -> {
            int[] secs = {0, 10, 20, 30, 60};
            String[] names = {"Off", "After 10 seconds", "After 20 seconds  (default)", "After 30 seconds", "After 1 minute"};
            int cur = Profiles.homePrefs(this).getInt("idle_sec", 20), sel = 2;
            for (int i = 0; i < secs.length; i++) if (secs[i] == cur) sel = i;
            dialog("Full-screen carousel").setMessage(null).setSingleChoiceItems(names, sel, (d, w) -> {
                Profiles.homePrefs(this).edit().putInt("idle_sec", secs[w]).apply();
                refreshAll();
                d.dismiss();
            }).show();
        });
        section("Screensaver");
        row("Screensaver on Home", () -> screensaverAllowed() ? "System screensaver" : "Hume carousel", () -> {
            String[] names = {"System screensaver  (" + dreamLabel() + ")", "Hume full-screen carousel"};
            dialog("Screensaver on Home").setSingleChoiceItems(names, screensaverAllowed() ? 0 : 1, (d, w) -> {
                getSharedPreferences("screensaver", MODE_PRIVATE).edit().putBoolean("allow", w == 0).apply();
                refreshAll();
                d.dismiss();
                Toast.makeText(this, w == 0 ? "The TV's screensaver starts when Home is idle"
                        : "Home stays on and plays the carousel full screen instead", Toast.LENGTH_LONG).show();
            }).show();
        });
        row("Screensaver", () -> dreamLabel(), this::openDreamSettings);
        row("Start screensaver now", () -> "", () -> {
            Intent i = new Intent(Intent.ACTION_MAIN).setClassName("com.android.systemui", "com.android.systemui.Somnambulator")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (!Suggestions.start(this, i)) Toast.makeText(this, "This TV can't start the screensaver from an app", Toast.LENGTH_LONG).show();
        });
        section("Search");
        row("Movies & TV in search", () -> Search.titlesEnabled(this) ? "On" : "Off", () -> {
            Search.prefs(this).edit().putBoolean(Search.K_TITLES, !Search.titlesEnabled(this)).apply();
            refreshAll();
        });
        row("Recent searches", () -> Search.recent(this).isEmpty() ? "None" : "Clear", () -> {
            Search.clearRecent(this);
            refreshAll();
        });
        section("Setup");
        row("Run setup again", () -> "", () -> startActivity(new Intent(this, SetupActivity.class)));
        row("Reset app layout", () -> "", () -> {
            Profiles.homePrefs(this).edit().remove("order").apply();
            Toast.makeText(this, "Layout reset", Toast.LENGTH_SHORT).show();
        });
    }

    void pagePermissions() {
        row("Continue Watching (apps\u2019 Watch Next)", () -> Perms.hasTvListings(this) ? "Allowed"
                : WatchNext.supported() ? "Allow\u2026" : "Not on this TV", () -> Perms.requestTvListings(this));
        row("Do Not Disturb", () -> Perms.hasListener(this) ? "Allowed"
                : Perms.listenerSupported(this) ? "Allow\u2026" : "Not on this TV",
                () -> Perms.requestListener(this));
        row("Power Off & Sleep Timer", () -> Perms.hasAdmin(this) ? "Allowed"
                : Perms.adminSupported(this) ? "Allow\u2026" : "Not on this TV", () -> Perms.requestAdmin(this));
        row("Hume as the home screen", () -> Perms.isDefaultHome(this) ? "Yes" : "Set\u2026",
                () -> Perms.requestDefaultHome(this));
        row("Wi-Fi name in Control Center", () -> Perms.hasLocation(this) ? "Allowed" : "Allow\u2026",
                () -> Perms.requestLocation(this));
    }

    void pageAbout() {
        row("Hume Launcher", () -> "Version " + version(), () -> { });
        row("Made by", () -> "Petrobot", () -> { });
        row("Weather", () -> Weather.sourceName(Weather.source(this)), () -> { });
        row("News", () -> News.sources(this).size() + " Indian newsrooms", () -> { });
    }

    boolean screensaverAllowed() {
        return getSharedPreferences("screensaver", MODE_PRIVATE).getBoolean("allow", true);
    }

    /** The TV's chosen screensaver (readable without permission), e.g. "Aerial Views". */
    String dreamLabel() {
        String comp = Settings.Secure.getString(getContentResolver(), "screensaver_components");
        if ("0".equals(Settings.Secure.getString(getContentResolver(), "screensaver_enabled"))) return "Off in TV settings";
        if (comp == null || comp.isEmpty()) return "TV default";
        android.content.ComponentName cn = android.content.ComponentName.unflattenFromString(comp.split(",")[0]);
        if (cn == null) return "TV default";
        try {
            return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(cn.getPackageName(), 0)).toString();
        } catch (Exception e) {
            return "TV default";
        }
    }

    /** Choose / set up the screensaver: the TV's screensaver settings, else the screensaver app itself. */
    void openDreamSettings() {
        if (Suggestions.start(this, new Intent("android.settings.DREAM_SETTINGS"))) return;
        String comp = Settings.Secure.getString(getContentResolver(), "screensaver_components");
        android.content.ComponentName cn = comp == null ? null : android.content.ComponentName.unflattenFromString(comp.split(",")[0]);
        if (cn != null) {
            Intent l = getPackageManager().getLeanbackLaunchIntentForPackage(cn.getPackageName());
            if (l == null) l = getPackageManager().getLaunchIntentForPackage(cn.getPackageName());
            if (l != null && Suggestions.start(this, l)) return;
        }
        Toast.makeText(this, "Choose the screensaver in TV Settings \u2192 Device Preferences \u2192 Screen saver", Toast.LENGTH_LONG).show();
        Suggestions.start(this, new Intent(Settings.ACTION_SETTINGS));
    }

    String version() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    void section(String name) {
        TextView t = new TextView(this);
        t.setText(name.toUpperCase());
        t.setTextColor(0x8CFFFFFF);
        t.setTypeface(Ui.font(this, "medium"));
        t.setLetterSpacing(0.06f);
        Ui.textSize(t, 11.5f);
        t.setPadding(Ui.px(this, 14), Ui.px(this, 18), 0, Ui.px(this, 6));
        list.addView(t);
    }

    interface Value {
        String get();
    }

    /** A row that opens a section page: label, current value and a chevron. */
    void nav(String target, Value value) {
        android.view.View r = row(target, () -> value.get() + "   \u203a", () -> show(target));
        navRows.put(target, r);
    }

    /** A settings row: label left, current value right; focus lifts it onto a light plate. */
    android.view.View row(String label, Value value, Runnable action) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setClickable(true);
        int padH = Ui.px(this, 16);
        r.setPadding(padH, 0, padH, 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Ui.px(this, 10));
        bg.setColor(0x14FFFFFF);
        r.setBackground(bg);

        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(Color.WHITE);
        l.setTypeface(Ui.font(this, "medium"));
        Ui.textSize(l, 15);
        l.setSingleLine(true);
        r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = new TextView(this);
        v.setTextColor(0x99FFFFFF);
        v.setTypeface(Ui.font(this, "regular"));
        Ui.textSize(v, 15);
        v.setSingleLine(true);
        r.addView(v);
        Runnable refresh = () -> v.setText(value.get());
        refresh.run();
        refreshers.add(refresh);

        r.setOnFocusChangeListener((view, has) -> {
            bg.setColor(has ? 0xF2FFFFFF : 0x14FFFFFF);
            l.setTextColor(has ? 0xFF111114 : Color.WHITE);
            v.setTextColor(has ? 0x99111114 : 0x99FFFFFF);
            view.animate().withLayer().scaleX(has ? 1.03f : 1f).scaleY(has ? 1.03f : 1f).setDuration(180).start();
        });
        r.setOnClickListener(view -> {
            action.run();
            for (Runnable x : refreshers) x.run();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.px(this, 40));
        lp.bottomMargin = Ui.px(this, 6);
        list.addView(r, lp);
        return r;
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent ev) {
        MainActivity.navSound(this, ev);
        return super.dispatchKeyEvent(ev);
    }

    AlertDialog.Builder dialog(String title) {
        return new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle(title);
    }

    void refreshAll() {
        for (Runnable x : refreshers) x.run();
    }

    void chooseSource() {
        String[] keys = {Suggestions.SRC_JUSTWATCH, Suggestions.SRC_TMDB, Suggestions.SRC_STREMIO};
        String[] names = {"JustWatch  (no setup needed)", "TMDB  (needs a free token)", "Stremio  (opens in Stremio or Nuvio)"};
        int cur = Math.max(0, java.util.Arrays.asList(keys).indexOf(Suggestions.source(this)));
        dialog("Recommendation source").setSingleChoiceItems(names, cur, (d, w) -> {
            Suggestions.prefs(this).edit().putString(Suggestions.K_SOURCE, keys[w]).apply();
            Suggestions.changed(this);
            refreshAll();
            d.dismiss();
            show(SHELF);
            if (w == 1 && !Suggestions.hasToken(this)) enterToken();
        }).show();
    }

    /** Token entry on the TV itself (no adb needed). Accepts the v4 read token or the short v3 API key. */
    void enterToken() {
        android.widget.EditText in = new android.widget.EditText(this);
        in.setSingleLine(true);
        in.setHint("v3 API key (32 characters) or read-access token");
        in.setText(Suggestions.prefs(this).getString(Suggestions.K_TOKEN, ""));
        in.setSelection(in.getText().length());
        in.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        int pad = Ui.px(this, 14);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(in);
        dialog("TMDB token")
                .setMessage("Get it free at themoviedb.org \u2192 Settings \u2192 API. The short \"API Key\" is easiest to type.")
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    Suggestions.prefs(this).edit().putString(Suggestions.K_TOKEN, in.getText().toString().trim()).apply();
                    Suggestions.changed(this);
                    refreshAll();
                })
                .setNeutralButton("Clear", (d, w) -> {
                    Suggestions.prefs(this).edit().remove(Suggestions.K_TOKEN).apply();
                    Suggestions.changed(this);
                    refreshAll();
                })
                .setNegativeButton("Cancel", null)
                .show();
        in.requestFocus();
    }

    /** Automatic (from the internet address) or a city found by name. */
    void chooseLocation() {
        String[] names = {"Automatic", "Choose City\u2026"};
        dialog("Weather location").setSingleChoiceItems(names, Weather.manual(this) ? 1 : 0, (d, w) -> {
            d.dismiss();
            if (w == 0) {
                Weather.setPlace(this, null);
                refreshAll();
            } else {
                searchCity();
            }
        }).show();
    }

    void searchCity() {
        android.widget.EditText in = new android.widget.EditText(this);
        in.setSingleLine(true);
        in.setHint("City name, e.g. Bengaluru");
        in.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        int pad = Ui.px(this, 14);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(in);
        dialog("Choose City")
                .setView(box)
                .setPositiveButton("Search", (d, w) -> {
                    String q = in.getText().toString().trim();
                    if (q.isEmpty()) return;
                    new Thread(() -> {
                        List<Weather.Place> found;
                        try {
                            found = Weather.search(q);
                        } catch (Exception e) {
                            found = null;
                        }
                        List<Weather.Place> res = found;
                        runOnUiThread(() -> showCities(q, res));
                    }).start();
                })
                .setNegativeButton("Cancel", null)
                .show();
        in.requestFocus();
    }

    void showCities(String q, List<Weather.Place> res) {
        if (isFinishing()) return;
        if (res == null) {
            Toast.makeText(this, "Couldn't search. Check the internet connection.", Toast.LENGTH_LONG).show();
            return;
        }
        if (res.isEmpty()) {
            Toast.makeText(this, "No city called \u201c" + q + "\u201d", Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[res.size()];
        for (int i = 0; i < names.length; i++) {
            Weather.Place pl = res.get(i);
            names[i] = pl.detail.isEmpty() ? pl.name : pl.name + "  \u00b7  " + pl.detail;
        }
        dialog("Choose City").setItems(names, (d, w) -> {
            Weather.setPlace(this, res.get(w));
            refreshAll();
        }).setNegativeButton("Cancel", null).show();
    }

    /** Address, QR and pairing code for sending content from a phone or computer. */
    void showPresentInfo() {
        if (!com.petrobot.hume.present.Presenter.enabled(this)) {
            Toast.makeText(this, "Turn on Presentation mode first", Toast.LENGTH_SHORT).show();
            return;
        }
        com.petrobot.hume.present.Presenter.ensureRunning(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Ui.px(this, 16);
        box.setPadding(pad, pad, pad, pad / 2);
        android.widget.ImageView qr = new android.widget.ImageView(this);
        qr.setImageBitmap(com.petrobot.hume.present.Presenter.qr(Ui.px(this, 110)));
        qr.setBackgroundColor(Color.WHITE);
        qr.setPadding(Ui.px(this, 6), Ui.px(this, 6), Ui.px(this, 6), Ui.px(this, 6));
        box.addView(qr, new LinearLayout.LayoutParams(Ui.px(this, 122), Ui.px(this, 122)));
        TextView info = new TextView(this);
        String pin = com.petrobot.hume.present.Presenter.pin(this);
        info.setText("Open on a phone or computer on the same Wi-Fi:\n\n"
                + com.petrobot.hume.present.Presenter.address() + "\n\nPairing code\n"
                + pin.substring(0, 3) + " " + pin.substring(3));
        info.setTextColor(Color.WHITE);
        info.setTypeface(Ui.font(this, "medium"));
        Ui.textSize(info, 14);
        info.setPadding(Ui.px(this, 18), 0, 0, 0);
        box.addView(info);
        dialog("Send to this TV").setView(box).setPositiveButton("Done", null).show();
    }

    void chooseTypes() {
        String[] keys = {"both", "movie", "tv"};
        String[] names = {"Movies & Series", "Movies", "TV Series"};
        int cur = java.util.Arrays.asList(keys).indexOf(Suggestions.types(this));
        dialog("Content").setSingleChoiceItems(names, cur, (d, w) -> {
            Suggestions.prefs(this).edit().putString(Suggestions.K_TYPES, keys[w]).apply();
            Suggestions.changed(this);
            refreshAll();
            d.dismiss();
        }).show();
    }

    void chooseProviders() {
        int n = Suggestions.PROVIDERS.size();
        String[] names = new String[n];
        boolean[] on = new boolean[n];
        Set<String> cur = Suggestions.providers(this);
        for (int i = 0; i < n; i++) {
            Suggestions.Provider p = Suggestions.PROVIDERS.get(i);
            names[i] = p.name + (Suggestions.installed(this, p.pkg) ? "" : "  (not installed)");
            on[i] = cur.contains(String.valueOf(p.id));
        }
        dialog("Streaming services").setMultiChoiceItems(names, on, (d, w, c) -> on[w] = c)
                .setPositiveButton("Done", (d, w) -> {
                    Set<String> sel = new java.util.HashSet<>();
                    for (int i = 0; i < n; i++) if (on[i]) sel.add(String.valueOf(Suggestions.PROVIDERS.get(i).id));
                    if (sel.isEmpty()) {
                        Toast.makeText(this, "Keep at least one service", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Suggestions.prefs(this).edit().putStringSet(Suggestions.K_PROVIDERS, sel).apply();
                    Suggestions.changed(this);
                    refreshAll();
                }).setNegativeButton("Cancel", null).show();
    }

    void chooseLanguages() {
        int n = Suggestions.LANG_CODES.length;
        boolean[] on = new boolean[n];
        Set<String> cur = Suggestions.languages(this);
        for (int i = 0; i < n; i++) on[i] = cur.contains(Suggestions.LANG_CODES[i]);
        dialog(Suggestions.source(this).equals(Suggestions.SRC_TMDB) ? "Languages (none = any)" : "Languages (TMDB source only)")
                .setMultiChoiceItems(Suggestions.LANG_NAMES, on, (d, w, c) -> on[w] = c)
                .setPositiveButton("Done", (d, w) -> {
                    Set<String> sel = new java.util.HashSet<>();
                    for (int i = 0; i < n; i++) if (on[i]) sel.add(Suggestions.LANG_CODES[i]);
                    Suggestions.prefs(this).edit().putStringSet(Suggestions.K_LANGS, sel).apply();
                    Suggestions.changed(this);
                    refreshAll();
                }).setNegativeButton("Cancel", null).show();
    }

    void chooseAdvance() {
        int[] secs = {0, 6, 8, 12};
        String[] names = {"Off", "Every 6 s", "Every 8 s", "Every 12 s"};
        int cur = 0;
        for (int i = 0; i < secs.length; i++) if (secs[i] == Suggestions.advanceSeconds(this)) cur = i;
        dialog("Auto-advance").setSingleChoiceItems(names, cur, (d, w) -> {
            Suggestions.prefs(this).edit().putInt(Suggestions.K_ADVANCE, secs[w]).apply();
            refreshAll();
            d.dismiss();
        }).show();
    }
}
