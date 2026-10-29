package com.petrobot.hume;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Search, as on tvOS: the Home Screen blurred behind, what you typed at the top, a one-row
 * keyboard, and rows of results under it. Apps, settings and inputs match on every key press with
 * no network; movies and series (JustWatch, any service in India, and Stremio) are fetched once
 * typing pauses. OK on a title opens its "How to Watch" sheet with an Open / Get button per app.
 *
 * Geometry in design units (960 x 540, half of Apple's 1920 x 1080 points): 48 side margin as the
 * Home Screen, query 26 high at the top, keys on a 30 pitch, poster lockups 96 x 144 (2:3).
 */
final class SearchPanel {

    static final int REQ_VOICE = 7301, REQ_MIC = 7302;
    static final long DEBOUNCE_MS = 350;
    static final String LATIN = "abcdefghijklmnopqrstuvwxyz";
    static final String DIGITS = "1234567890.-'&:!?";
    static final String HINDI = "अआइईउऊएऐओऔ"
            + "कखगघचछजझटठडढण"
            + "तथदधनपफबभमयरलव"
            + "शषसहािीुूेैोौं्";

    final MainActivity a;
    final FrameLayout layer;
    final TextView queryView;
    final LinearLayout keys, list;
    final HorizontalScrollView keyScroll;
    final ScrollView results;
    final StringBuilder query = new StringBuilder();
    final Search.Apps apps;
    final ExecutorService net = Executors.newFixedThreadPool(2), img = Executors.newSingleThreadExecutor();
    /** Recent searches' results, so deleting a letter shows the earlier results at once. */
    final Map<String, List<Search.Title>> cache = new LinkedHashMap<String, List<Search.Title>>(24, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, List<Search.Title>> e) {
            return size() > 20;
        }
    };
    final Map<String, List<Search.Title>> stremioCache = new LinkedHashMap<String, List<Search.Title>>(24, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, List<Search.Title>> e) {
            return size() > 20;
        }
    };
    /** Decoded posters and logos, capped at 1/12 of the heap (the 1 GB TV gets ~16 MB). */
    final LruCache<String, Bitmap> bitmaps = new LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 12)) {
        @Override protected int sizeOf(String k, Bitmap b) {
            return b.getByteCount();
        }
    };
    final Runnable fetch = this::fetchTitles;
    /** The TV has voice input (a prompt app); live dictates in place when it also has a speech service. */
    final boolean voice, live;
    ImageView mic;
    GradientDrawable micBg;
    SpeechRecognizer recognizer;
    boolean listening, heard;
    int mode, gen;
    String failed;
    FrameLayout sheet;
    View sheetReturn;
    LinearLayout titleRow, stremioRow;
    TextView titleStatus;
    boolean closed;

    SearchPanel(MainActivity a) {
        this.a = a;
        apps = Search.apps(a, a.all);
        voice = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(a.getPackageManager()) != null;
        live = SpeechRecognizer.isRecognitionAvailable(a);

        layer = new FrameLayout(a);
        layer.setClickable(true);
        layer.setClipChildren(false);
        a.addBlurredBackdrop(layer, 0xD9000000);

        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        layer.addView(col, a.match());

        // Header: the dictation mic (as in tvOS's search field) and what you typed or said.
        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setClipChildren(false);
        head.setPadding(a.dockOuter, a.dp(30), a.dockOuter, 0);
        if (voice || live) {
            mic = new ImageView(a);
            mic.setId(View.generateViewId());
            mic.setScaleType(ImageView.ScaleType.CENTER);
            mic.setFocusable(true);
            mic.setClickable(true);
            micBg = new GradientDrawable();
            micBg.setShape(GradientDrawable.OVAL);
            mic.setBackground(micBg);
            mic.setOnFocusChangeListener((v, has) -> {
                paintMic();
                v.animate().scaleX(has ? 1.12f : 1f).scaleY(has ? 1.12f : 1f).setDuration(Motion.d(180)).setInterpolator(MainActivity.LIFT).start();
            });
            mic.setOnClickListener(v -> listen());
            paintMic();
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(a.dp(34), a.dp(34));
            mlp.rightMargin = a.dp(14);
            head.addView(mic, mlp);
        }
        queryView = a.text(26, Ui.font(a, "display"), 1f);
        queryView.setSingleLine(true);
        queryView.setEllipsize(TextUtils.TruncateAt.START);
        head.addView(queryView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        col.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        keyScroll = new HorizontalScrollView(a);
        keyScroll.setHorizontalScrollBarEnabled(false);
        keyScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        keyScroll.setClipToPadding(false);
        keyScroll.setClipChildren(false);
        keyScroll.setPadding(a.dockOuter - a.dp(6), a.dp(8), a.dockOuter, a.dp(8));
        keys = new LinearLayout(a);
        keys.setOrientation(LinearLayout.HORIZONTAL);
        keys.setClipChildren(false);
        keyScroll.addView(keys);
        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, a.dp(50));
        klp.topMargin = a.dp(10);
        col.addView(keyScroll, klp);

        results = new ScrollView(a);
        results.setVerticalScrollBarEnabled(false);
        results.setOverScrollMode(View.OVER_SCROLL_NEVER);
        results.setClipChildren(false);
        results.setClipToPadding(false);
        results.setPadding(0, a.dp(4), 0, a.dp(40));
        list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        results.addView(list, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(results, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        buildKeys(null);
        showQuery();
        render();
    }

    // ---------------------------------------------------------------- keyboard

    /** The one-row keyboard for the current mode, then space, delete, mode switches and the mic. */
    void buildKeys(String focusLabel) {
        keys.removeAllViews();
        String chars = mode == 1 ? DIGITS : mode == 2 ? HINDI : LATIN;
        List<View> made = new ArrayList<>();
        for (int i = 0; i < chars.length(); i++) {
            String ch = String.valueOf(chars.charAt(i));
            made.add(key(ch, a.dp(30), () -> type(ch)));
        }
        made.add(key("space", a.dp(64), () -> type(" ")));
        made.add(key("⌫", a.dp(40), this::backspace));
        made.add(key(mode == 1 ? "abc" : "123", a.dp(48), () -> switchMode(mode == 1 ? 0 : 1)));
        made.add(key(mode == 2 ? "abc" : "अआ", a.dp(48), () -> switchMode(mode == 2 ? 0 : 2)));
        made.add(key("clear", a.dp(56), this::clear));
        View focus = made.get(0);
        for (View k : made) {
            keys.addView(k);
            if (focusLabel != null && focusLabel.equals(k.getTag())) focus = k;
        }
        // The ends of the row stop focus instead of wrapping into the results.
        made.get(0).setNextFocusLeftId(made.get(0).getId());
        made.get(made.size() - 1).setNextFocusRightId(made.get(made.size() - 1).getId());
        if (focusLabel != null || keys.getTag() == null) focus.requestFocus();
        keys.setTag("built");
    }

    View key(String label, int w, Runnable click) {
        FrameLayout k = new FrameLayout(a);
        k.setId(View.generateViewId());
        k.setTag(label);
        k.setFocusable(true);
        k.setClickable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(a.dp(8));
        bg.setColor(0x00FFFFFF);
        k.setBackground(bg);
        TextView face = a.text(label.length() > 2 ? 13 : 17, Ui.font(a, "medium"), 1f);
        face.setText(label);
        face.setGravity(Gravity.CENTER);
        face.setShadowLayer(0, 0, 0, 0);
        k.addView(face, a.match());
        k.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? Color.WHITE : 0x00FFFFFF);
            face.setTextColor(has ? 0xFF111114 : Color.WHITE);
            v.animate().scaleX(has ? 1.15f : 1f).scaleY(has ? 1.15f : 1f).setDuration(Motion.d(150)).start();
        });
        k.setOnClickListener(v -> click.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, a.dp(34));
        lp.gravity = Gravity.CENTER_VERTICAL;
        k.setLayoutParams(lp);
        return k;
    }

    /** Focus stays on the switch just pressed (its label flips to "abc" and back). */
    void switchMode(int m) {
        int prev = mode;
        mode = m;
        buildKeys(m != 0 ? "abc" : prev == 1 ? "123" : "\u0905\u0906");
    }

    void type(String s) {
        if (s.equals(" ") && (query.length() == 0 || query.charAt(query.length() - 1) == ' ')) return;
        query.append(s);
        changed();
    }

    void backspace() {
        if (query.length() == 0) return;
        query.setLength(query.length() - 1);
        changed();
    }

    void clear() {
        query.setLength(0);
        changed();
    }

    void setQuery(String q) {
        query.setLength(0);
        query.append(q);
        changed();
    }

    // ---------------------------------------------------------------- dictation

    /** Focused white; red while listening; glass otherwise. */
    void paintMic() {
        if (mic == null) return;
        boolean f = mic.isFocused();
        micBg.setColor(listening ? 0xFFFF3B30 : f ? Color.WHITE : 0x26FFFFFF);
        mic.setImageBitmap(Glyphs.get("mic", a.dp(20), listening || !f ? Color.WHITE : 0xFF111114));
    }

    /**
     * Dictation. With the TV's speech service (Google's on Android TV) and the microphone allowed,
     * the words appear in the search field as you speak and results follow; otherwise the TV's
     * own voice prompt (Google's) listens and hands back what it heard. The microphone is asked
     * for on screen the first time. Pressing the mic again stops listening.
     */
    void listen() {
        if (listening) {
            if (recognizer != null) recognizer.stopListening();
            return;
        }
        if (!live) {
            prompt();
            return;
        }
        if (a.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            a.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        try {
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(a);
                recognizer.setRecognitionListener(new Dictation());
            }
            listening = true;
            heard = false;
            paintMic();
            showQuery();
            recognizer.startListening(speechIntent());
        } catch (Exception e) {
            stopped();
            prompt();
        }
    }

    /** The microphone prompt was answered: dictate in place, or fall back to the voice prompt. */
    void onMicPermission(boolean granted) {
        if (granted) listen();
        else prompt();
    }

    Intent speechIntent() {
        return new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                .putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", new String[]{"hi-IN"})
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, a.getPackageName())
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a movie, show or app");
    }

    /** The TV's own voice prompt (Google's on Android TV); the answer arrives in onVoice. */
    void prompt() {
        if (!voice) {
            a.toast("Voice input isn't available on this TV");
            return;
        }
        try {
            Intent i = speechIntent();
            i.removeExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS);
            a.startActivityForResult(i, REQ_VOICE);
        } catch (Exception e) {
            a.toast("Voice input isn't available on this TV");
        }
    }

    void stopped() {
        listening = false;
        if (mic != null) mic.animate().scaleX(mic.isFocused() ? 1.12f : 1f).scaleY(mic.isFocused() ? 1.12f : 1f).setDuration(Motion.d(150)).start();
        paintMic();
        showQuery();
    }

    /** Live words go straight into the query, so results follow the voice like typing. */
    final class Dictation implements RecognitionListener {
        @Override public void onReadyForSpeech(Bundle b) { }
        @Override public void onBeginningOfSpeech() { }
        @Override public void onBufferReceived(byte[] buf) { }
        @Override public void onEndOfSpeech() { }
        @Override public void onEvent(int type, Bundle b) { }

        /** The mic swells gently with the voice level, only while listening. */
        @Override public void onRmsChanged(float db) {
            if (mic == null || !listening) return;
            float s = 1f + Math.max(0f, Math.min(1f, (db + 2f) / 12f)) * 0.18f;
            mic.setScaleX(s);
            mic.setScaleY(s);
        }

        @Override public void onPartialResults(Bundle b) {
            String t = first(b);
            if (!t.isEmpty() && !closed) {
                heard = true;
                setQuery(t.toLowerCase());
            }
        }

        @Override public void onResults(Bundle b) {
            String t = first(b);
            if (closed) return;
            stopped();
            if (!t.isEmpty()) setQuery(t.toLowerCase());
        }

        @Override public void onError(int error) {
            if (closed) return;
            boolean wasHeard = heard;
            stopped();
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
                    || (!wasHeard && (error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_SERVER
                    || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY))) {
                prompt();  // the service won't dictate for us: use the TV's voice prompt
            } else if (!wasHeard) {
                queryView.setText(error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT
                        ? "Voice needs the internet" : "Didn\u2019t catch that \u2014 press the mic to try again");
                queryView.setAlpha(0.6f);
            }
        }

        String first(Bundle b) {
            ArrayList<String> r = b == null ? null : b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            return r == null || r.isEmpty() || r.get(0) == null ? "" : r.get(0).trim();
        }
    }

    void onVoice(Intent data) {
        if (data == null) return;
        ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (r != null && !r.isEmpty()) setQuery(r.get(0));
    }

    void showQuery() {
        boolean empty = query.length() == 0;
        queryView.setText(empty ? (listening ? "Listening\u2026" : "Search apps, movies and shows") : query);
        queryView.setAlpha(empty ? 0.45f : 1f);
    }

    /** Every key press: the local rows at once; titles from the cache, or fetched once typing pauses. */
    void changed() {
        showQuery();
        a.handler.removeCallbacks(fetch);
        gen++;
        Search.cancel();
        render();
        String q = q();
        if (q.length() >= 2 && Search.titlesEnabled(a) && !cache.containsKey(q.toLowerCase())) {
            a.handler.postDelayed(fetch, DEBOUNCE_MS);
        }
    }

    String q() {
        return query.toString().trim();
    }

    // ---------------------------------------------------------------- results

    void render() {
        View focus = a.getCurrentFocus();
        boolean inResults = focus != null && isInside(focus, list);
        list.removeAllViews();
        titleRow = stremioRow = null;
        titleStatus = null;
        String q = q();
        if (q.isEmpty()) {
            renderSuggestions();
        } else {
            List<View> local = localResults(q);
            if (!local.isEmpty()) addRow("Apps & Settings", local, a.dp(84));
            if (q.length() >= 2 && Search.titlesEnabled(a)) {
                titleRow = addRow("Movies & TV", new ArrayList<>(), a.dp(176));
                List<Search.Title> hit = cache.get(q.toLowerCase());
                if (hit != null) fillTitles(hit);
                else titleStatus(q.equals(failed) ? "Can’t reach the internet" : "Searching…");
                if (Search.hasStremioApp(a)) {
                    List<Search.Title> sh = stremioCache.get(q.toLowerCase());
                    if (sh != null) fillStremio(sh);
                }
            } else if (local.isEmpty()) {
                addNote(q.length() < 2 ? "Keep typing to search movies and shows" : "No results");
            }
        }
        if (inResults) firstKey().requestFocus();
    }

    View firstKey() {
        return keys.getChildAt(0);
    }

    static boolean isInside(View v, View parent) {
        for (Object p = v; p instanceof View; p = ((View) p).getParent()) if (p == parent) return true;
        return false;
    }

    /** Before typing: recent searches and what is trending in the carousel. */
    void renderSuggestions() {
        List<String> recent = Search.recent(a);
        if (!recent.isEmpty()) {
            List<View> chips = new ArrayList<>();
            for (String r : recent) chips.add(chip(r, () -> setQuery(r)));
            chips.add(chip("Clear", () -> {
                Search.clearRecent(a);
                render();
                firstKey().requestFocus();
            }));
            addRow("Recent Searches", chips, a.dp(44));
        }
        List<View> trend = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<Suggestions.Item> items = a.heroItems;
        if (items == null || items.isEmpty()) items = Suggestions.cached(a, true);
        if (items != null) {
            for (Suggestions.Item it : items) {
                if ("news".equals(it.type) || it.title == null || it.title.isEmpty() || !seen.add(it.title)) continue;
                if (!it.pkg.isEmpty()) continue;  // Continue Watching entries
                trend.add(chip(it.title, () -> setQuery(it.title)));
                if (trend.size() >= 10) break;
            }
        }
        if (!trend.isEmpty()) addRow("Trending", trend, a.dp(44));
        if (recent.isEmpty() && trend.isEmpty()) addNote("Type to search your apps, settings, movies and shows");
    }

    /** Apps (including hidden ones), TV inputs, Hume actions and TV settings matching q, best first. */
    List<View> localResults(String q) {
        List<Object[]> hits = new ArrayList<>();
        for (MainActivity.AppEntry e : a.all.values()) {
            if (e.key.equals(MainActivity.FOLDER_KEY) || e.key.equals(MainActivity.INPUTS_KEY)) continue;
            int s = Search.score(e.label, null, q);
            if (s >= 0) hits.add(new Object[]{s, appCard(e)});
        }
        for (Inputs.Input in : a.inputs) {
            int s = Search.score(in.label, "input hdmi av tv source", q);
            if (s >= 0) hits.add(new Object[]{s, actionCard(in.label, Inputs.glyph(in), () -> Inputs.open(a, in))});
        }
        for (Object[] act : actions()) {
            int s = Search.score((String) act[0], (String) act[1], q);
            if (s >= 0) hits.add(new Object[]{s + 1, actionCard((String) act[0], (String) act[2], (Runnable) act[3])});
        }
        hits.sort((x, y) -> Integer.compare((int) x[0], (int) y[0]));
        List<View> out = new ArrayList<>();
        for (int i = 0; i < hits.size() && i < 14; i++) out.add((View) hits.get(i)[1]);
        return out;
    }

    /** Hume's own screens and the TV settings people look for: name, keywords, glyph, action. */
    List<Object[]> actions() {
        List<Object[]> l = new ArrayList<>();
        l.add(new Object[]{"Control Center", "controls power sleep", "controls", (Runnable) () -> { close(); a.showControlCenter(); }});
        l.add(new Object[]{"Weather Forecast", "weather temperature rain", "refresh", (Runnable) () -> { close(); a.showWeather(); }});
        l.add(new Object[]{"Switch Profile", "profile user who", "controls", (Runnable) () -> { close(); a.showProfilePicker(false); }});
        l.add(new Object[]{"Hume Settings", "settings launcher preferences", "gear", (Runnable) () -> {
            a.returningFromSettings = true;
            a.startActivity(new Intent(a, SettingsActivity.class));
        }});
        l.add(new Object[]{"Refresh Carousel", "refresh reload news recommendations", "refresh", (Runnable) () -> { close(); a.refreshAll(); }});
        l.add(new Object[]{"Wi-Fi", "wifi network internet", "wifi", settings(Settings.ACTION_WIFI_SETTINGS)});
        l.add(new Object[]{"Bluetooth", "bluetooth remote controller headphones speaker pair", "gamepad", settings(Settings.ACTION_BLUETOOTH_SETTINGS)});
        l.add(new Object[]{"Sound", "sound audio volume speaker", "audio", settings("android.settings.SOUND_SETTINGS")});
        l.add(new Object[]{"Display", "display resolution screen picture", "present", settings(Settings.ACTION_DISPLAY_SETTINGS)});
        l.add(new Object[]{"Screen Saver", "screensaver dream ambient", "moon", settings("android.settings.DREAM_SETTINGS")});
        l.add(new Object[]{"Date & Time", "date time clock", "timer", settings(Settings.ACTION_DATE_SETTINGS)});
        l.add(new Object[]{"Accessibility", "accessibility captions", "accessibility", settings(Settings.ACTION_ACCESSIBILITY_SETTINGS)});
        l.add(new Object[]{"Apps", "apps applications uninstall storage", "memory", settings(Settings.ACTION_APPLICATION_SETTINGS)});
        l.add(new Object[]{"Storage", "storage space memory", "memory", settings(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)});
        l.add(new Object[]{"TV Settings", "settings system device", "gear", settings(Settings.ACTION_SETTINGS)});
        return l;
    }

    Runnable settings(String action) {
        return () -> {
            if (!Suggestions.start(a, new Intent(action))) Suggestions.start(a, new Intent(Settings.ACTION_SETTINGS));
        };
    }

    /** A section title and a horizontal row; items keep focus inside the row at its ends. */
    LinearLayout addRow(String title, List<View> items, int height) {
        TextView h = a.text(13, Ui.font(a, "semibold"), 0.7f);
        h.setText(title);
        h.setShadowLayer(0, 0, 0, 0);
        h.setPadding(a.dockOuter, a.dp(14), 0, a.dp(2));
        list.addView(h);
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hs.setClipToPadding(false);
        hs.setClipChildren(false);
        hs.setPadding(a.dockOuter, a.dp(10), a.dockOuter, a.dp(10));
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        hs.addView(row);
        list.addView(hs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height + a.dp(20)));
        setItems(row, items);
        return row;
    }

    void setItems(LinearLayout row, List<View> items) {
        row.removeAllViews();
        for (int i = 0; i < items.size(); i++) {
            View v = items.get(i);
            if (v.getId() == View.NO_ID) v.setId(View.generateViewId());
            LinearLayout.LayoutParams lp = v.getLayoutParams() instanceof LinearLayout.LayoutParams
                    ? (LinearLayout.LayoutParams) v.getLayoutParams()
                    : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = a.dp(16);
            row.addView(v, lp);
        }
        if (!items.isEmpty()) {
            items.get(0).setNextFocusLeftId(items.get(0).getId());
            View last = items.get(items.size() - 1);
            last.setNextFocusRightId(last.getId());
        }
    }

    void addNote(String s) {
        TextView t = a.text(15, Ui.font(a, "regular"), 0.6f);
        t.setText(s);
        t.setShadowLayer(0, 0, 0, 0);
        t.setPadding(a.dockOuter, a.dp(24), a.dockOuter, 0);
        list.addView(t);
    }

    void titleStatus(String s) {
        if (titleRow == null) return;
        titleRow.removeAllViews();
        titleStatus = a.text(14, Ui.font(a, "regular"), 0.6f);
        titleStatus.setText(s);
        titleStatus.setShadowLayer(0, 0, 0, 0);
        titleRow.addView(titleStatus, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, a.dp(144)));
        titleStatus.setGravity(Gravity.CENTER_VERTICAL);
    }

    // ---------------------------------------------------------------- titles (network)

    void fetchTitles() {
        String q = q();
        if (q.length() < 2 || closed) return;
        int g = gen;
        String key = q.toLowerCase();
        net.execute(() -> {
            List<Search.Title> r = null;
            try {
                r = Search.titles(a, q, apps);
            } catch (Exception e) {
                android.util.Log.w("TvHome", "search: " + e);
            }
            List<Search.Title> res = r;
            a.handler.post(() -> {
                if (closed) return;
                if (res != null) cache.put(key, res);
                else failed = q;
                if (g != gen) return;
                if (res != null) fillTitles(res);
                else titleStatus("Can’t reach the internet");
            });
        });
        if (Search.hasStremioApp(a) && !stremioCache.containsKey(key)) {
            net.execute(() -> {
                List<Search.Title> r;
                try {
                    r = Search.stremio(a, q);
                } catch (Exception e) {
                    return;
                }
                a.handler.post(() -> {
                    if (closed) return;
                    stremioCache.put(key, r);
                    if (g == gen) fillStremio(r);
                });
            });
        }
    }

    void fillTitles(List<Search.Title> ts) {
        if (titleRow == null) return;
        if (ts.isEmpty()) {
            titleStatus("No movies or shows found");
            return;
        }
        List<View> cards = new ArrayList<>();
        for (Search.Title t : ts) cards.add(titleCard(t));
        setItems(titleRow, cards);
        // Stremio-only titles may have arrived first; drop the ones JustWatch now lists.
        List<Search.Title> sh = stremioCache.get(q().toLowerCase());
        if (sh != null) fillStremio(sh);
    }

    /** Titles only Stremio's catalogue has (JustWatch lists the rest with their services). */
    void fillStremio(List<Search.Title> ts) {
        Set<String> listed = new HashSet<>();
        List<Search.Title> jw = cache.get(q().toLowerCase());
        if (jw != null) for (Search.Title t : jw) if (!t.imdb.isEmpty()) listed.add(t.imdb);
        List<View> cards = new ArrayList<>();
        for (Search.Title t : ts) if (!listed.contains(t.imdb)) cards.add(titleCard(t));
        if (cards.isEmpty()) return;
        if (stremioRow == null) stremioRow = addRow("On " + Suggestions.stremioApp(a).name, cards, a.dp(176));
        else setItems(stremioRow, cards);
    }

    // ---------------------------------------------------------------- cards

    /** An app's own banner in Apple's 5:3 card, name below; focus lifts it as on the Home Screen. */
    View appCard(MainActivity.AppEntry e) {
        int w = a.dp(105), h = a.dp(63);
        ImageView art = new ImageView(a);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Drawable d = e.banner != null ? e.banner : e.icon;
        art.setImageDrawable(d);
        View cell = cell(art, w, h, e.label, a.dp(8));
        cell.setOnClickListener(v -> {
            Search.remember(a, q());
            a.launchAnimated(v, e);
        });
        return cell;
    }

    View actionCard(String label, String glyph, Runnable run) {
        int w = a.dp(105), h = a.dp(63);
        ImageView art = new ImageView(a);
        art.setScaleType(ImageView.ScaleType.CENTER);
        art.setImageBitmap(Glyphs.get(glyph, a.dp(26), Color.WHITE));
        art.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF5A5A60, 0xFF2C2C30}));
        View cell = cell(art, w, h, label, a.dp(8));
        cell.setOnClickListener(v -> {
            Search.remember(a, q());
            run.run();
        });
        return cell;
    }

    /** A movie or series poster (2:3) with the logos of up to three services that have it. */
    View titleCard(Search.Title t) {
        int w = a.dp(96), h = a.dp(144);
        FrameLayout art = new FrameLayout(a);
        art.setBackgroundColor(0x26FFFFFF);
        ImageView poster = new ImageView(a);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.addView(poster, a.match());
        TextView fallback = a.text(12, Ui.font(a, "semibold"), 0.85f);
        fallback.setText(t.title);
        fallback.setGravity(Gravity.CENTER);
        fallback.setPadding(a.dp(6), 0, a.dp(6), 0);
        art.addView(fallback, a.match());
        LinearLayout logos = new LinearLayout(a);
        logos.setOrientation(LinearLayout.HORIZONTAL);
        logos.setPadding(a.dp(4), 0, 0, a.dp(4));
        int n = 0;
        for (Search.Offer o : t.offers) {
            if (o.icon.isEmpty() || n >= 3) continue;
            ImageView li = new ImageView(a);
            li.setScaleType(ImageView.ScaleType.FIT_CENTER);
            round(li, a.dp(4));
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(a.dp(20), a.dp(20));
            llp.rightMargin = a.dp(3);
            logos.addView(li, llp);
            load(li, o.icon, a.dp(20), null);
            n++;
        }
        art.addView(logos, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START));
        if (!t.poster.isEmpty()) load(poster, t.poster, w, fallback);
        View cell = cell(art, w, h, t.title + (t.year.isEmpty() ? "" : "  ·  " + t.year), a.dp(8));
        cell.setOnClickListener(v -> showSheet(t, v));
        return cell;
    }

    /** Card art with a label below; the art lifts 1.1x on focus and the label brightens. */
    View cell(View art, int w, int h, String label, int radius) {
        LinearLayout cell = new LinearLayout(a);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setFocusable(true);
        cell.setClickable(true);
        cell.setClipChildren(false);
        round(art, radius);
        cell.addView(art, new LinearLayout.LayoutParams(w, h));
        TextView l = a.text(11.5f, Ui.font(a, "medium"), 0.6f);
        l.setText(label);
        l.setSingleLine(true);
        l.setEllipsize(TextUtils.TruncateAt.END);
        l.setShadowLayer(0, 0, 0, 0);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = a.dp(6);
        cell.addView(l, llp);
        cell.setOnFocusChangeListener((v, has) -> {
            art.animate().scaleX(has ? 1.1f : 1f).scaleY(has ? 1.1f : 1f).translationZ(has ? a.dp(10) : 0)
                    .setDuration(Motion.d(200)).setInterpolator(MainActivity.LIFT).start();
            l.setAlpha(has ? 1f : 0.6f);
            l.animate().translationY(has ? a.dp(5) : 0).setDuration(Motion.d(200)).start();
        });
        art.setPivotY(h);
        art.setPivotX(w / 2f);
        return cell;
    }

    View chip(String label, Runnable click) {
        TextView t = a.text(13, Ui.font(a, "medium"), 1f);
        t.setText(label);
        t.setSingleLine(true);
        t.setGravity(Gravity.CENTER);
        t.setShadowLayer(0, 0, 0, 0);
        t.setPadding(a.dp(16), 0, a.dp(16), 0);
        t.setFocusable(true);
        t.setClickable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(a.dp(15));
        bg.setColor(0x26FFFFFF);
        t.setBackground(bg);
        t.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? Color.WHITE : 0x26FFFFFF);
            t.setTextColor(has ? 0xFF111114 : Color.WHITE);
            v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).setDuration(Motion.d(180)).start();
        });
        t.setOnClickListener(v -> click.run());
        t.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, a.dp(30)));
        return t;
    }

    void round(View v, int r) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline o) {
                o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
            }
        });
        v.setClipToOutline(true);
    }

    /** Decodes an image at the size it is shown, off the UI thread, newest request wins per view. */
    void load(ImageView iv, String url, int width, View hideWhenLoaded) {
        iv.setTag(url);
        Bitmap mem = bitmaps.get(url);
        if (mem != null) {
            iv.setImageBitmap(mem);
            if (hideWhenLoaded != null) hideWhenLoaded.setVisibility(View.GONE);
            return;
        }
        img.execute(() -> {
            if (closed || !url.equals(iv.getTag())) return;
            Bitmap b = Suggestions.image(a, url, "", Math.max(64, width));
            if (b == null) return;
            a.handler.post(() -> {
                if (closed) return;
                bitmaps.put(url, b);
                if (url.equals(iv.getTag())) {
                    iv.setImageBitmap(b);
                    iv.setAlpha(0f);
                    iv.animate().alpha(1f).setDuration(Motion.d(160)).start();
                    if (hideWhenLoaded != null) hideWhenLoaded.setVisibility(View.GONE);
                }
            });
        });
    }

    // ---------------------------------------------------------------- How to Watch sheet

    /**
     * The title's page, like the Apple TV app's: artwork behind, title, details and summary on
     * the left, and "How to Watch" with one button per app (Open when installed, Get otherwise).
     */
    void showSheet(Search.Title t, View from) {
        Search.remember(a, q());
        sheetReturn = from;
        FrameLayout s = new FrameLayout(a);
        s.setClickable(true);
        s.setBackgroundColor(0xFF0B0B0F);
        ImageView back = new ImageView(a);
        back.setScaleType(ImageView.ScaleType.CENTER_CROP);
        s.addView(back, a.match());
        View scrim = new View(a);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xF20B0B0F, 0xCC0B0B0F, 0x330B0B0F}));
        s.addView(scrim, a.match());
        String art = !t.backdrop.isEmpty() ? t.backdrop : t.poster;
        if (!art.isEmpty()) load(back, art, Math.min(1280, a.screenW), null);

        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setPadding(a.dockOuter, a.dp(60), 0, a.dp(30));
        TextView title = a.text(34, Ui.font(a, "display"), 1f);
        title.setText(t.title);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(title, new LinearLayout.LayoutParams(a.dp(520), ViewGroup.LayoutParams.WRAP_CONTENT));
        List<String> meta = new ArrayList<>();
        if (!t.year.isEmpty()) meta.add(t.year);
        meta.add(t.type.equals("tv") ? "Series" : "Movie");
        if (!t.genres.isEmpty()) meta.add(t.genres);
        if (t.rating > 0) meta.add(String.format(java.util.Locale.US, "IMDb %.1f", t.rating));
        TextView m = a.text(13, Ui.font(a, "medium"), 0.75f);
        m.setText(String.join("  ·  ", meta));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = a.dp(6);
        col.addView(m, mlp);
        if (!t.overview.isEmpty()) {
            TextView o = a.text(14, Ui.font(a, "regular"), 0.85f);
            o.setText(t.overview);
            o.setMaxLines(4);
            o.setEllipsize(TextUtils.TruncateAt.END);
            o.setLineSpacing(0, 1.15f);
            LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(a.dp(440), ViewGroup.LayoutParams.WRAP_CONTENT);
            olp.topMargin = a.dp(12);
            col.addView(o, olp);
        }
        TextView how = a.text(13, Ui.font(a, "semibold"), 0.7f);
        how.setText("HOW TO WATCH");
        how.setLetterSpacing(0.06f);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = a.dp(22);
        hlp.bottomMargin = a.dp(8);
        col.addView(how, hlp);
        List<View> buttons = new ArrayList<>();
        for (Search.Offer o : t.offers) {
            if (buttons.size() >= 5) break;
            View b = offerButton(t, o);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(a.dp(380), a.dp(40));
            blp.bottomMargin = a.dp(8);
            col.addView(b, blp);
            buttons.add(b);
        }
        if (buttons.isEmpty()) {
            TextView none = a.text(14, Ui.font(a, "regular"), 0.7f);
            none.setText("Not streaming in India right now.");
            col.addView(none);
        }
        s.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        layer.addView(s, a.match());
        sheet = s;
        s.setAlpha(0f);
        s.animate().alpha(1f).setDuration(Motion.d(220)).start();
        if (!buttons.isEmpty()) buttons.get(0).requestFocus();
        else s.setFocusable(true);
        if (buttons.isEmpty()) s.requestFocus();
    }

    /** [logo] Open in Prime Video ... Subscription, or Get JioHotstar for apps that aren't installed. */
    View offerButton(Search.Title t, Search.Offer o) {
        LinearLayout b = new LinearLayout(a);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setFocusable(true);
        b.setClickable(true);
        b.setPadding(a.dp(8), 0, a.dp(16), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(a.dp(20));
        bg.setColor(0x33FFFFFF);
        b.setBackground(bg);
        ImageView logo = new ImageView(a);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        round(logo, a.dp(6));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(a.dp(26), a.dp(26));
        llp.rightMargin = a.dp(10);
        b.addView(logo, llp);
        if (!o.icon.isEmpty()) load(logo, o.icon, a.dp(26), null);
        else logo.setImageBitmap(Glyphs.get("present", a.dp(26), Color.WHITE));
        TextView name = a.text(15, Ui.font(a, "semibold"), 1f);
        name.setShadowLayer(0, 0, 0, 0);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        boolean have = o.installed();
        name.setText((have ? "▶  Open in " : "Get ") + o.service);
        b.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView kind = a.text(12, Ui.font(a, "medium"), 0.7f);
        kind.setShadowLayer(0, 0, 0, 0);
        kind.setText(have ? o.label() : o.label().isEmpty() ? "Not installed" : o.label() + "  ·  Not installed");
        b.addView(kind);
        b.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? Color.WHITE : 0x33FFFFFF);
            name.setTextColor(has ? 0xFF111114 : Color.WHITE);
            kind.setTextColor(has ? 0xFF111114 : Color.WHITE);
            v.animate().scaleX(has ? 1.05f : 1f).scaleY(has ? 1.05f : 1f).setDuration(Motion.d(180)).setInterpolator(MainActivity.LIFT).start();
        });
        b.setPivotX(0);
        b.setOnClickListener(v -> {
            if (o.installed()) {
                a.noteLaunched(o.pkg);
                Search.open(a, t, o);
            } else {
                Search.get(a, o);
            }
        });
        return b;
    }

    void closeSheet() {
        FrameLayout s = sheet;
        sheet = null;
        if (s == null) return;
        s.animate().alpha(0f).setDuration(Motion.d(180)).withEndAction(() -> layer.removeView(s)).start();
        if (sheetReturn != null && sheetReturn.isAttachedToWindow()) sheetReturn.requestFocus();
        else firstKey().requestFocus();
    }

    // ---------------------------------------------------------------- keys and lifecycle

    /** Keys while search is open; false lets the framework move focus. */
    boolean dispatch(KeyEvent ev) {
        int k = ev.getKeyCode();
        boolean down = ev.getAction() == KeyEvent.ACTION_DOWN;
        if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_ESCAPE) {
            if (!down) {
                if (sheet != null) closeSheet();
                else a.closeSearch();
            }
            return true;
        }
        if (sheet != null) {
            return k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT;
        }
        if (k == KeyEvent.KEYCODE_DEL) {
            if (down) backspace();
            return true;
        }
        if (k == KeyEvent.KEYCODE_SEARCH || k == KeyEvent.KEYCODE_VOICE_ASSIST) {
            if (!down && ev.getRepeatCount() == 0) {
                if (mic != null) mic.requestFocus();
                listen();
            }
            return true;
        }
        if (down && k != KeyEvent.KEYCODE_DPAD_CENTER && k != KeyEvent.KEYCODE_ENTER) {
            int ch = ev.getUnicodeChar();
            if (ch > 0 && !Character.isISOControl(ch)) {  // a hardware keyboard, or the remote's digits
                type(String.valueOf((char) ch).toLowerCase());
                return true;
            }
        }
        View f = a.getCurrentFocus();
        if (mic != null && k == KeyEvent.KEYCODE_DPAD_UP && f != null && f.getParent() == keys) {
            if (down) mic.requestFocus();
            return true;
        }
        if (f == mic && mic != null) {
            if (k == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (down) firstKey().requestFocus();
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) return true;
        }
        // Down from the keyboard lands on the first item of the first row, not wherever is nearest.
        if (k == KeyEvent.KEYCODE_DPAD_DOWN && f != null && f.getParent() == keys) {
            if (down) {
                View first = firstResult();
                if (first != null) first.requestFocus();
            }
            return true;
        }
        if (k == KeyEvent.KEYCODE_DPAD_UP && f != null && isInside(f, list) && f.getParent() == firstRow()) {
            if (down) firstKey().requestFocus();
            return true;
        }
        return false;
    }

    LinearLayout firstRow() {
        for (int i = 0; i < list.getChildCount(); i++) {
            View c = list.getChildAt(i);
            if (c instanceof HorizontalScrollView) {
                LinearLayout row = (LinearLayout) ((HorizontalScrollView) c).getChildAt(0);
                if (row.getChildCount() > 0 && row.getChildAt(0).isFocusable()) return row;
            }
        }
        return null;
    }

    View firstResult() {
        LinearLayout r = firstRow();
        return r == null ? null : r.getChildAt(0);
    }

    void close() {
        a.closeSearch();
    }

    /** Stops work and frees images; the layer itself is removed by MainActivity. */
    void release() {
        closed = true;
        if (recognizer != null) {
            recognizer.destroy();
            recognizer = null;
        }
        a.handler.removeCallbacks(fetch);
        Search.cancel();
        net.shutdownNow();
        img.shutdownNow();
        bitmaps.evictAll();
    }
}
