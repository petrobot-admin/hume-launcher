package com.petrobot.hume;

import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Top-shelf suggestions: what's popular in India on the user's streaming services, from TMDB
 * (themoviedb.org). Results and artwork are cached on disk; everything here runs off the UI thread.
 */
final class Suggestions {

    static final String PREFS = "suggestions";
    static final String K_ENABLED = "enabled", K_TOKEN = "tmdb_token", K_TYPES = "types",
            K_PROVIDERS = "providers", K_LANGS = "languages", K_ADVANCE = "advance_sec", K_VERSION = "version",
            K_SOURCE = "source", K_SHELF = "shelf";
    /** What the top shelf shows: recommendations, news, both (alternating) or nothing. */
    static final String SHELF_RECS = "recs", SHELF_NEWS = "news", SHELF_BOTH = "both", SHELF_NONE = "none";
    /**
     * Recommendation sources: JustWatch needs no key (unofficial API); TMDB needs a token; Stremio
     * is Cinemeta's public top lists, opened in the Stremio or Nuvio app.
     */
    static final String SRC_JUSTWATCH = "justwatch", SRC_TMDB = "tmdb", SRC_STREMIO = "stremio";
    static final long MAX_AGE_MS = 2L * 60 * 60 * 1000;  // recommendations rotate every 2 hours
    /** POOL titles are fetched per service; PER_SOURCE of them are picked at random each refresh. */
    static final int PER_SOURCE = 3, POOL = 20, MAX_ITEMS = 10;

    /** A streaming service available in India: TMDB provider id, display name, Android TV package. */
    static final class Provider {
        final int id;
        final String name, pkg, jwShort, jwTech;

        Provider(int id, String name, String pkg, String jwShort, String jwTech) {
            this.id = id;
            this.name = name;
            this.pkg = pkg;
            this.jwShort = jwShort;
            this.jwTech = jwTech;
        }
    }

    static final List<Provider> PROVIDERS = Arrays.asList(
            new Provider(8, "Netflix", "com.netflix.ninja", "nfx", "netflix"),
            new Provider(119, "Prime Video", "com.amazon.amazonvideo.livingroom", "prv", "amazonprimevideo"),
            new Provider(2336, "JioHotstar", "in.startv.hotstar", "jhs", "jiohotstar"),
            new Provider(350, "Apple TV", "com.apple.atve.androidtv.appletv", "atp", "appletvplus"),
            new Provider(232, "ZEE5", "com.graymatrix.did", "zee", "zee5"),
            new Provider(237, "SonyLIV", "com.sonyliv", "snl", "sonyliv"));

    /**
     * Apps that open Cinemeta titles by IMDb id. Not streaming services, so they never appear in
     * PROVIDERS; Stremio comes first because it is the one on the Play Store.
     */
    static final int STREMIO_ID = -1, NUVIO_ID = -2;
    static final Provider STREMIO = new Provider(STREMIO_ID, "Stremio", "com.stremio.one", "", ""),
            NUVIO = new Provider(NUVIO_ID, "Nuvio", "com.nuvio.tv", "", "");

    /** Original-language filters offered in settings (TMDB ISO 639-1 codes). */
    static final String[] LANG_CODES = {"hi", "en", "ta", "te", "ml", "kn", "bn", "mr"};
    static final String[] LANG_NAMES = {"Hindi", "English", "Tamil", "Telugu", "Malayalam", "Kannada", "Bengali", "Marathi"};

    static final class Item {
        int id, providerId;
        String type, title, overview, backdrop, logo, year, genres;
        /** Exact title page on the service (JustWatch), opened directly in the app when it supports it. */
        String link = "";
        /** Continue Watching entries: the app that played it, its store key, and progress. */
        String pkg = "", key = "";
        float progress = -1f;
        int remainingMin = -1;
        /** Continue Watching: when it was last played (ms), for ordering. */
        long played;
        double rating;
        /** News: when the story was published (ms), for "2 h ago". */
        long published;

        JSONObject toJson() throws Exception {
            return new JSONObject().put("id", id).put("providerId", providerId).put("type", type)
                    .put("title", title).put("overview", overview).put("backdrop", backdrop)
                    .put("logo", logo == null ? "" : logo).put("year", year).put("genres", genres).put("rating", rating)
                    .put("link", link).put("pub", published);
        }

        static Item fromJson(JSONObject o) {
            Item i = new Item();
            i.id = o.optInt("id");
            i.providerId = o.optInt("providerId");
            i.type = o.optString("type");
            i.title = o.optString("title");
            i.overview = o.optString("overview");
            i.backdrop = o.optString("backdrop");
            i.logo = o.optString("logo");
            if (i.logo.isEmpty()) i.logo = null;
            i.year = o.optString("year");
            i.genres = o.optString("genres");
            i.rating = o.optDouble("rating", 0);
            i.link = o.optString("link");
            i.published = o.optLong("pub");
            return i;
        }

        Provider provider() {
            for (Provider p : PROVIDERS) if (p.id == providerId) return p;
            return null;
        }

        /** The app that opens this title: its service, or for Stremio titles whichever player is installed. */
        Provider provider(Context c) {
            return providerId == STREMIO_ID ? stremioApp(c) : provider();
        }
    }

    // ------------------------------------------------------------------ settings

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** What this profile's top shelf shows (each profile chooses; older global setting as fallback). */
    static String shelf(Context c) {
        String own = Profiles.homePrefs(c).getString(K_SHELF, null);
        if (own != null) return own;
        SharedPreferences p = prefs(c);
        if (!p.contains(K_SHELF) && !p.getBoolean(K_ENABLED, true)) return SHELF_NONE;  // older setting
        return p.getString(K_SHELF, SHELF_BOTH);
    }

    static void setShelf(Context c, String value) {
        Profiles.homePrefs(c).edit().putString(K_SHELF, value).apply();
        changed(c);
    }

    /** Recommendations and/or news for this profile, as two switches. */
    static void setShelf(Context c, boolean recs, boolean news) {
        setShelf(c, recs && news ? SHELF_BOTH : recs ? SHELF_RECS : news ? SHELF_NEWS : SHELF_NONE);
    }

    static boolean showRecs(Context c) {
        String s = shelf(c);
        return s.equals(SHELF_RECS) || s.equals(SHELF_BOTH);
    }

    static boolean showNews(Context c) {
        String s = shelf(c);
        return s.equals(SHELF_NEWS) || s.equals(SHELF_BOTH);
    }

    static boolean enabled(Context c) {
        return !shelf(c).equals(SHELF_NONE);
    }

    static String source(Context c) {
        return prefs(c).getString(K_SOURCE, SRC_JUSTWATCH);
    }

    /** Stremio when installed (or neither is), else Nuvio. */
    static Provider stremioApp(Context c) {
        return !installed(c, STREMIO.pkg) && installed(c, NUVIO.pkg) ? NUVIO : STREMIO;
    }

    static String sourceName(String s) {
        return s.equals(SRC_TMDB) ? "TMDB" : s.equals(SRC_STREMIO) ? "Stremio" : "JustWatch";
    }

    /** Whether the chosen source is usable right now (JustWatch and Stremio always; TMDB once a token is set). */
    static boolean ready(Context c) {
        return showNews(c) || !source(c).equals(SRC_TMDB) || hasToken(c);
    }

    static boolean hasToken(Context c) {
        return !prefs(c).getString(K_TOKEN, "").trim().isEmpty();
    }

    /** "both", "movie" or "tv". */
    static String types(Context c) {
        return prefs(c).getString(K_TYPES, "both");
    }

    static Set<String> providers(Context c) {
        Set<String> def = new HashSet<>();
        for (Provider p : PROVIDERS) def.add(String.valueOf(p.id));
        return new HashSet<>(prefs(c).getStringSet(K_PROVIDERS, def));
    }

    static Set<String> languages(Context c) {
        return new HashSet<>(prefs(c).getStringSet(K_LANGS, new HashSet<>()));
    }

    static int advanceSeconds(Context c) {
        return prefs(c).getInt(K_ADVANCE, 8);
    }

    /** Bumped on every settings change so the home screen knows to refetch. */
    static void changed(Context c) {
        SharedPreferences p = prefs(c);
        p.edit().putInt(K_VERSION, p.getInt(K_VERSION, 0) + 1).apply();
        cacheFile(c).delete();
    }

    static int version(Context c) {
        return prefs(c).getInt(K_VERSION, 0);
    }

    // ------------------------------------------------------------------ data

    /** One cache per content mix, so profiles with different choices don't overwrite each other. */
    static File cacheFile(Context c) {
        return new File(c.getFilesDir(), "suggestions_" + shelf(c) + ".json");
    }

    /** Cached list, or null when missing/stale. */
    static List<Item> cached(Context c, boolean allowStale) {
        File f = cacheFile(c);
        if (!f.exists()) return null;
        long maxAge = showNews(c) ? Math.min(News.refreshMinutes(c) * 60000L, MAX_AGE_MS) : MAX_AGE_MS;
        if (!allowStale && System.currentTimeMillis() - f.lastModified() > maxAge) return null;
        try {
            JSONArray a = new JSONArray(new String(readAll(new FileInputStream(f)), StandardCharsets.UTF_8));
            List<Item> out = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) out.add(Item.fromJson(a.getJSONObject(i)));
            return out.isEmpty() ? null : out;
        } catch (Exception e) {
            return null;
        }
    }

    /** Fetches fresh suggestions from the chosen source (blocking; call off the UI thread). */
    static List<Item> fetch(Context c) throws Exception {
        List<Item> recs = new ArrayList<>(), news = new ArrayList<>();
        Exception err = null;
        if (showRecs(c) && (!source(c).equals(SRC_TMDB) || hasToken(c))) {
            try {
                recs = fetchRecs(c);
            } catch (Exception e) {
                err = e;
            }
        }
        if (showNews(c)) news = News.fetch(c, showRecs(c) ? 6 : MAX_ITEMS + 2);
        // Both: alternate a recommendation and a story.
        List<Item> out = new ArrayList<>();
        for (int i = 0; i < Math.max(recs.size(), news.size()); i++) {
            if (i < recs.size()) out.add(recs.get(i));
            if (i < news.size()) out.add(news.get(i));
        }
        android.util.Log.i("TvHome", "top shelf: " + recs.size() + " recommendations, " + news.size() + " news");
        if (out.isEmpty() && err != null) throw err;
        JSONArray a = new JSONArray();
        for (Item it : out) a.put(it.toJson());
        try (OutputStream os = new FileOutputStream(cacheFile(c))) {
            os.write(a.toString().getBytes(StandardCharsets.UTF_8));
        }
        return out;
    }

    /** Recommendations from the chosen source, interleaved across services. */
    static List<Item> fetchRecs(Context c) throws Exception {
        String from = source(c);
        List<List<Item>> sources = from.equals(SRC_TMDB) ? fetchTmdb(c)
                : from.equals(SRC_STREMIO) ? fetchStremio(c) : fetchJustWatch(c);
        // Rotate: a random pick from each service's popular pool, so the carousel changes through the day.
        java.util.Random rnd = new java.util.Random();
        for (List<Item> src : sources) java.util.Collections.shuffle(src, rnd);
        java.util.Collections.shuffle(sources, rnd);
        List<Item> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int round = 0; round < PER_SOURCE && out.size() < MAX_ITEMS; round++) {
            for (List<Item> src : sources) {
                if (round < src.size() && out.size() < MAX_ITEMS) {
                    Item it = src.get(round);
                    if (seen.add(it.title.toLowerCase())) out.add(it);
                }
            }
        }
        return out;
    }

    static final String JW_QUERY = "query P($country: Country!, $language: Language!, $first: Int!, $filter: TitleFilter) {"
            + " popularTitles(country: $country, filter: $filter, first: $first, sortBy: POPULAR, sortRandomSeed: 0) {"
            + " edges { node { id objectType content(country: $country, language: $language) {"
            + " title originalReleaseYear shortDescription genres { shortName } backdrops { backdropUrl } scoring { imdbScore } }"
            + " offers(country: $country, platform: WEB) { standardWebURL package { technicalName } } } } } }";

    /** What's popular in India per service, from JustWatch's public GraphQL endpoint (no key). */
    static List<List<Item>> fetchJustWatch(Context c) throws Exception {
        String t = types(c);
        List<String> kinds = t.equals("both") ? Arrays.asList("tv", "movie") : Arrays.asList(t);
        Set<String> provs = providers(c);
        List<List<Item>> sources = new ArrayList<>();
        for (Provider p : PROVIDERS) {
            if (!provs.contains(String.valueOf(p.id))) continue;
            for (String kind : kinds) {
                JSONObject filter = new JSONObject()
                        .put("packages", new JSONArray().put(p.jwShort))
                        .put("objectTypes", new JSONArray().put(kind.equals("tv") ? "SHOW" : "MOVIE"))
                        .put("monetizationTypes", new JSONArray().put("FLATRATE").put("FREE").put("ADS"));
                JSONObject vars = new JSONObject().put("country", "IN").put("language", "en")
                        .put("first", POOL).put("filter", filter);
                JSONObject body = new JSONObject().put("query", JW_QUERY).put("variables", vars);
                JSONObject res = post("https://apis.justwatch.com/graphql", body.toString());
                JSONArray edges = res.getJSONObject("data").getJSONObject("popularTitles").getJSONArray("edges");
                List<Item> list = new ArrayList<>();
                for (int i = 0; i < edges.length() && list.size() < POOL; i++) {
                    JSONObject n = edges.getJSONObject(i).getJSONObject("node");
                    JSONObject ct = n.getJSONObject("content");
                    JSONArray bds = ct.optJSONArray("backdrops");
                    if (bds == null || bds.length() == 0) continue;
                    Item it = new Item();
                    it.id = Math.abs(n.optString("id").hashCode());
                    it.type = kind;
                    it.providerId = p.id;
                    it.title = ct.optString("title");
                    it.overview = ct.optString("shortDescription");
                    it.backdrop = "https://images.justwatch.com" + bds.getJSONObject(0).getString("backdropUrl")
                            .replace("{profile}", "s1440").replace("{format}", "jpg");
                    it.logo = "";
                    int y = ct.optInt("originalReleaseYear", 0);
                    it.year = y > 0 ? String.valueOf(y) : "";
                    JSONObject sc = ct.optJSONObject("scoring");
                    it.rating = sc == null ? 0 : sc.optDouble("imdbScore", 0);
                    it.genres = jwGenres(ct.optJSONArray("genres"));
                    JSONArray offers = n.optJSONArray("offers");
                    for (int k = 0; offers != null && k < offers.length(); k++) {
                        JSONObject o = offers.getJSONObject(k);
                        if (p.jwTech.equals(o.getJSONObject("package").optString("technicalName"))) {
                            it.link = o.optString("standardWebURL");
                            break;
                        }
                    }
                    list.add(it);
                }
                sources.add(list);
            }
        }
        return sources;
    }

    static String jwGenres(JSONArray g) {
        Map<String, String> names = new LinkedHashMap<>();
        String[][] table = {{"act", "Action"}, {"ani", "Animation"}, {"cmy", "Comedy"}, {"crm", "Crime"},
                {"doc", "Documentary"}, {"drm", "Drama"}, {"fml", "Family"}, {"fnt", "Fantasy"}, {"hst", "History"},
                {"hrr", "Horror"}, {"msc", "Music"}, {"trl", "Thriller"}, {"rma", "Romance"}, {"scf", "Sci-Fi"},
                {"spt", "Sport"}, {"war", "War"}, {"wsn", "Western"}, {"rly", "Reality"}};
        for (String[] r : table) names.put(r[0], r[1]);
        List<String> out = new ArrayList<>();
        for (int i = 0; g != null && i < g.length() && out.size() < 2; i++) {
            String n = names.get(g.optJSONObject(i).optString("shortName"));
            if (n != null) out.add(n);
        }
        return String.join(" \u00b7 ", out);
    }

    static JSONObject post(String url, String json) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(12000);
        con.setDoOutput(true);
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json");
        con.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android TV)");
        try {
            try (OutputStream os = con.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
            int code = con.getResponseCode();
            if (code != 200) throw new IllegalStateException("HTTP " + code);
            return new JSONObject(new String(readAll(con.getInputStream()), StandardCharsets.UTF_8));
        } finally {
            con.disconnect();
        }
    }

    /**
     * Stremio's Cinemeta "Popular" lists (public, no key): one per kind. Backdrops and logos are
     * absolute metahub.space URLs; the IMDb id goes in link for the deep link.
     */
    static List<List<Item>> fetchStremio(Context c) throws Exception {
        String t = types(c);
        List<String> kinds = t.equals("both") ? Arrays.asList("tv", "movie") : Arrays.asList(t);
        List<List<Item>> sources = new ArrayList<>();
        for (String kind : kinds) {
            String url = "https://v3-cinemeta.strem.io/catalog/" + (kind.equals("tv") ? "series" : "movie") + "/top.json";
            HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(8000);
            con.setReadTimeout(20000);
            JSONArray metas;
            try {
                if (con.getResponseCode() != 200) throw new IllegalStateException("Cinemeta HTTP " + con.getResponseCode());
                metas = new JSONObject(new String(readAll(con.getInputStream()), StandardCharsets.UTF_8)).optJSONArray("metas");
            } finally {
                con.disconnect();
            }
            List<Item> list = new ArrayList<>();
            for (int i = 0; metas != null && i < metas.length() && list.size() < POOL; i++) {
                JSONObject m = metas.getJSONObject(i);
                String id = m.optString("id"), backdrop = m.optString("background");
                if (!id.startsWith("tt") || backdrop.isEmpty()) continue;
                Item it = new Item();
                it.id = id.hashCode();
                it.type = kind;
                it.providerId = STREMIO_ID;
                it.title = m.optString("name");
                it.overview = m.optString("description");
                it.backdrop = backdrop;
                it.logo = m.optString("logo");
                String year = m.optString("releaseInfo", m.optString("year"));
                it.year = year.length() >= 4 ? year.substring(0, 4) : "";
                it.rating = m.optDouble("imdbRating", 0);
                JSONArray g = m.optJSONArray("genres");
                List<String> names = new ArrayList<>();
                for (int j = 0; g != null && j < g.length() && names.size() < 2; j++) names.add(g.optString(j));
                it.genres = String.join(", ", names);
                it.link = id;
                list.add(it);
            }
            sources.add(list);
        }
        return sources;
    }

    static List<List<Item>> fetchTmdb(Context c) throws Exception {
        Map<Integer, String> movieGenres = genres(c, "movie"), tvGenres = genres(c, "tv");
        String t = types(c);
        List<String> kinds = t.equals("both") ? Arrays.asList("tv", "movie") : Arrays.asList(t);
        Set<String> provs = providers(c), langs = languages(c);

        // One popular list per (service, kind), then interleave so every service gets a turn.
        List<List<Item>> sources = new ArrayList<>();
        for (Provider p : PROVIDERS) {
            if (!provs.contains(String.valueOf(p.id))) continue;
            for (String kind : kinds) {
                StringBuilder q = new StringBuilder("/discover/").append(kind)
                        .append("?watch_region=IN&with_watch_monetization_types=flatrate&sort_by=popularity.desc")
                        .append("&language=en-IN&include_adult=false&with_watch_providers=").append(p.id);
                if (!langs.isEmpty()) q.append("&with_original_language=").append(URLEncoder.encode(String.join("|", langs), "UTF-8"));
                JSONArray results = get(c, q.toString()).optJSONArray("results");
                List<Item> list = new ArrayList<>();
                for (int i = 0; results != null && i < results.length() && list.size() < POOL; i++) {
                    JSONObject r = results.getJSONObject(i);
                    String backdrop = r.optString("backdrop_path", "");
                    if (backdrop.isEmpty() || backdrop.equals("null")) continue;
                    Item it = new Item();
                    it.id = r.getInt("id");
                    it.type = kind;
                    it.providerId = p.id;
                    it.title = r.optString(kind.equals("tv") ? "name" : "title");
                    it.overview = r.optString("overview");
                    it.backdrop = backdrop;
                    String date = r.optString(kind.equals("tv") ? "first_air_date" : "release_date", "");
                    it.year = date.length() >= 4 ? date.substring(0, 4) : "";
                    it.rating = r.optDouble("vote_average", 0);
                    it.genres = genreNames(r.optJSONArray("genre_ids"), kind.equals("tv") ? tvGenres : movieGenres);
                    list.add(it);
                }
                sources.add(list);
            }
        }
        return sources;
    }

    /** Title-treatment logo for an item (English preferred), fetched once and cached in the list. */
    static String logoFor(Context c, Item it) {
        if (it.logo != null) return it.logo;
        if (!source(c).equals(SRC_TMDB)) return "";
        try {
            JSONArray logos = get(c, "/" + it.type + "/" + it.id + "/images?include_image_language=en,hi,null")
                    .optJSONArray("logos");
            String best = "";
            for (int i = 0; logos != null && i < logos.length(); i++) {
                JSONObject l = logos.getJSONObject(i);
                if (best.isEmpty() || "en".equals(l.optString("iso_639_1"))) {
                    best = l.optString("file_path");
                    if ("en".equals(l.optString("iso_639_1"))) break;
                }
            }
            it.logo = best;
        } catch (Exception e) {
            it.logo = "";
        }
        return it.logo;
    }

    static Map<Integer, String> genres(Context c, String kind) {
        File f = new File(c.getFilesDir(), "genres_" + kind + ".json");
        try {
            JSONObject o;
            if (f.exists()) {
                o = new JSONObject(new String(readAll(new FileInputStream(f)), StandardCharsets.UTF_8));
            } else {
                o = get(c, "/genre/" + kind + "/list?language=en");
                try (OutputStream os = new FileOutputStream(f)) {
                    os.write(o.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            Map<Integer, String> m = new LinkedHashMap<>();
            JSONArray g = o.optJSONArray("genres");
            for (int i = 0; g != null && i < g.length(); i++) {
                m.put(g.getJSONObject(i).getInt("id"), g.getJSONObject(i).getString("name"));
            }
            return m;
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    static String genreNames(JSONArray ids, Map<Integer, String> names) {
        List<String> out = new ArrayList<>();
        for (int i = 0; ids != null && i < ids.length() && out.size() < 2; i++) {
            String n = names.get(ids.optInt(i));
            if (n != null) out.add(n.replace("Action & Adventure", "Action").replace("Sci-Fi & Fantasy", "Sci-Fi"));
        }
        return String.join(" \u00b7 ", out);
    }

    /** TMDB GET. Accepts either a v4 read-access token (eyJ\u2026) or a v3 API key. */
    static JSONObject get(Context c, String path) throws Exception {
        String token = prefs(c).getString(K_TOKEN, "").trim();
        if (token.isEmpty()) throw new IllegalStateException("No TMDB token");
        String url = "https://api.themoviedb.org/3" + path;
        boolean bearer = token.startsWith("eyJ");
        if (!bearer) url += (url.contains("?") ? "&" : "?") + "api_key=" + URLEncoder.encode(token, "UTF-8");
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(10000);
        con.setRequestProperty("Accept", "application/json");
        if (bearer) con.setRequestProperty("Authorization", "Bearer " + token);
        try {
            int code = con.getResponseCode();
            if (code != 200) throw new IllegalStateException("TMDB HTTP " + code);
            return new JSONObject(new String(readAll(con.getInputStream()), StandardCharsets.UTF_8));
        } finally {
            con.disconnect();
        }
    }

    // ------------------------------------------------------------------ images

    /** TMDB image, cached on disk, decoded no larger than maxWidth (the box has little RAM). */
    static Bitmap image(Context c, String path, String size, int maxWidth) {
        if (path == null || path.isEmpty()) return null;
        if (path.startsWith("/")) {  // local artwork (Continue Watching)
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, o);
            o.inSampleSize = 1;
            while (o.outWidth / (o.inSampleSize * 2) >= maxWidth) o.inSampleSize *= 2;
            o.inJustDecodeBounds = false;
            return BitmapFactory.decodeFile(path, o);
        }
        if (path.startsWith("content:") || path.startsWith("android.resource:")) {  // Watch Next artwork
            try {
                Uri u = Uri.parse(path);
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                try (InputStream in = c.getContentResolver().openInputStream(u)) {
                    BitmapFactory.decodeStream(in, null, o);
                }
                o.inSampleSize = 1;
                while (o.outWidth / (o.inSampleSize * 2) >= maxWidth) o.inSampleSize *= 2;
                o.inJustDecodeBounds = false;
                try (InputStream in = c.getContentResolver().openInputStream(u)) {
                    return BitmapFactory.decodeStream(in, null, o);
                }
            } catch (Exception e) {
                return null;
            }
        }
        try {
            File dir = new File(c.getCacheDir(), "tmdb");
            dir.mkdirs();
            boolean abs = path.startsWith("http");
            File f = new File(dir, (abs ? "u" + Integer.toHexString(path.hashCode()) : size + "_" + path.replace("/", "")) + ".img");
            if (!f.exists()) {
                HttpURLConnection con = (HttpURLConnection) new URL(abs ? path : "https://image.tmdb.org/t/p/" + size + path).openConnection();
                con.setConnectTimeout(8000);
                con.setReadTimeout(15000);
                try {
                    if (con.getResponseCode() != 200) return null;
                    byte[] data = readAll(con.getInputStream());
                    File tmp = new File(dir, f.getName() + ".part");
                    try (OutputStream os = new FileOutputStream(tmp)) {
                        os.write(data);
                    }
                    tmp.renameTo(f);
                } finally {
                    con.disconnect();
                }
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            o.inSampleSize = 1;
            while (o.outWidth / (o.inSampleSize * 2) >= maxWidth) o.inSampleSize *= 2;
            o.inJustDecodeBounds = false;
            return BitmapFactory.decodeFile(f.getPath(), o);
        } catch (Exception e) {
            return null;
        }
    }

    static byte[] readAll(InputStream in) throws Exception {
        try (InputStream is = in) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        }
    }

    // ------------------------------------------------------------------ deep links

    static boolean installed(Context c, String pkg) {
        try {
            c.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * Opens the title in its streaming app as directly as the app allows: the app's own search
     * for the title (search intent, then its web-search link), falling back to launching the app,
     * and to its Play Store page when it isn't installed. TMDB has no per-service title ids, so
     * search is the most precise link available.
     */
    static void open(Context c, Item it) {
        Provider p = it.provider(c);
        if (p == null) return;
        if (!installed(c, p.pkg)) {
            start(c, new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + p.pkg)));
            return;
        }
        if (it.providerId == STREMIO_ID) {
            openStremio(c, it, p);
            return;
        }
        String q = it.title;
        List<Intent> tries = new ArrayList<>();
        if (it.link != null && !it.link.isEmpty()) tries.add(view(it.link, p.pkg));
        Intent search = new Intent(Intent.ACTION_SEARCH).setPackage(p.pkg).putExtra(SearchManager.QUERY, q);
        tries.add(search);
        String enc;
        try {
            enc = URLEncoder.encode(q, "UTF-8");
        } catch (Exception e) {
            enc = q;
        }
        switch (p.id) {
            case 8: tries.add(view("https://www.netflix.com/search?q=" + enc, p.pkg)); break;
            case 119: tries.add(view("https://www.primevideo.com/search/?phrase=" + enc, p.pkg)); break;
            case 2336: tries.add(view("https://www.hotstar.com/in/search?q=" + enc, p.pkg)); break;
            case 350: tries.add(view("https://tv.apple.com/search?term=" + enc, p.pkg)); break;
            case 232: tries.add(view("https://www.zee5.com/search?q=" + enc, p.pkg)); break;
            case 237: tries.add(view("https://www.sonyliv.com/search?searchTerm=" + enc, p.pkg)); break;
            default: break;
        }
        PackageManager pm = c.getPackageManager();
        for (Intent i : tries) {
            if (i.resolveActivity(pm) != null && start(c, i)) return;
        }
        Intent launch = pm.getLeanbackLaunchIntentForPackage(p.pkg);
        if (launch == null) launch = pm.getLaunchIntentForPackage(p.pkg);
        if (launch != null) start(c, launch);
    }

    /**
     * Stremio titles open on their detail page: Stremio takes stremio:///detail/{type}/{id}/{videoId},
     * Nuvio takes nuvio://detail/{type}/{id} (its stremio:// handler only installs add-ons).
     */
    static void openStremio(Context c, Item it, Provider p) {
        String type = it.type.equals("tv") ? "series" : "movie";
        String url = p.id == NUVIO_ID ? "nuvio://detail/" + type + "/" + it.link
                : "stremio:///detail/" + type + "/" + it.link + (type.equals("movie") ? "/" + it.link : "");
        PackageManager pm = c.getPackageManager();
        Intent i = view(url, p.pkg);
        if (i.resolveActivity(pm) != null && start(c, i)) return;
        Intent launch = pm.getLeanbackLaunchIntentForPackage(p.pkg);
        if (launch == null) launch = pm.getLaunchIntentForPackage(p.pkg);
        if (launch != null) start(c, launch);
    }

    static Intent view(String url, String pkg) {
        return new Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(pkg);
    }

    static boolean start(Context c, Intent i) {
        try {
            c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }
}
