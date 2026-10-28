# Architecture

## Build

No Gradle and no runtime libraries. `build.sh` runs `aapt2` (resources + assets) → `javac
--release 11` against `android.jar` (API 34) → `d8` (min API 24) → `zipalign` → `apksigner` (local debug key, or the private release key via
`RELEASE_KEYSTORE`).
Fonts (Inter, OFL) and the Presentation web app are plain assets; the QR encoder is vendored
source (Nayuki, MIT) under `src/io/nayuki/qrcodegen`.

## Code map

| Path | Responsibility |
|---|---|
| `MainActivity.java` | Home screen: grid/dock, focus & move, launch zoom, carousel (Continue Watching + news + recommendations), status row, Liquid Glass, Control Center (incl. Memory/Clean), profile switcher, idle mode, menus |
| `SetupActivity.java` | First-time setup pages (Apple-style, one question per page) |
| `SettingsActivity.java` | All settings, tvOS list layout |
| `Suggestions.java` | JustWatch / TMDB / Stremio (Cinemeta) recommendations: fetch, cache, artwork, deep links |
| `Search.java` | Search data: JustWatch title search (every service in India) and Cinemeta, matching each service to its installed app, Open / Get, recent searches |
| `SearchPanel.java` | The search screen: one-row keyboard (English, digits, Hindi, voice), Apps & Settings and Movies & TV rows, the How to Watch sheet |
| `WatchNext.java` | Continue Watching: apps' Watch Next rows (TV provider); ranking, Resume links, Remove |
| `MediaWatchService.java` | Notification listener for Do Not Disturb |
| `Profiles.java` | Profiles (max 3), unique emoji avatars, per-profile storage names |
| `SleepReceiver.java` | Sleep via device admin (or PowerService); sleep timer alarm |
| `Inputs.java` | TV inputs from the TV input framework; switching via the passthrough channel URI |
| `News.java` | News for the top shelf: Indian newsrooms' RSS (India, local city, world), images, freshness |
| `Perms.java` | On-screen permission and default-home requests, with adb only as a last resort |
| `Weather.java` | Forecast from Open-Meteo, MET Norway or wttr.in (with fallback) in one format, IP location / city search, cache, refresh schedule |
| `WeatherView.java`, `WeatherIcons.java` | Weather overview card and forecast panel (drawn in code), multicolour weather icons |
| `Clicks.java` | Navigation sounds (Android TV system sound effects at 60%) |
| `PowerService.java` | Accessibility service the user turns on: sleep (Power Off / Sleep Timer) where device admin is unavailable; Force stop for Clean |
| `AirPlayGuard.java` | Keeps the AirPlay receiver app visible: mDNS check, restart via its settings / start-up receivers |
| `Glyphs.java` | Line glyphs drawn in code (Control Center, Presentation tile, inputs, refresh) |
| `Ui.java` | Resolution-based sizing (960×540 design grid), fonts |
| `present/Presenter.java` | Presentation entry point: settings, server lifecycle, player bridge, boot start |
| `present/AdminServer.java` | HTTP server (port 8080), API, streaming multipart uploads |
| `present/Repo.java`, `Model.java`, `Pairing.java` | SQLite storage, slide model & validation, pairing tokens |
| `present/PresentationActivity.java` | Full-screen player (images, MediaPlayer video, WebView with auto-scroll) |
| `present/PresentIcon.java` | The Presentation tile icon (drawn in code; `assets/present/icon.svg` is the same design) |
| `present/LegacyCss.java`, `Qr.java`, `Net.java` | `@layer` unwrapping for old WebViews, QR bitmaps, LAN URLs |
| `assets/present/` | Browser admin app (`index.html`, `app.css`, `app.js`) |

## Design decisions

- **Measure Apple first.** Geometry comes from Apple's tvOS 26 press screenshot scaled to
  1920×1080 (dock 48 px from the edges, 248×148 px cards, 51 px gaps, dock top at 73%, focus 1.2×)
  and the HIG (60/80 pt safe area, 800×480 icons, focus/pressed states).
- **Liquid Glass without a backdrop-blur API.** The launcher knows what is behind its glass (its
  wallpaper or the carousel artwork), so it bakes a small blurred, saturation-boosted copy once per
  background and the GPU draws glass as shader-filled rounded rectangles aligned to the surface's
  screen position, plus magnified edge bands (lensing) and a gradient specular rim.
- **Recommendations without system access.** The top shelf uses JustWatch/TMDB/Stremio.
- **Continue Watching without system access.** `ACCESS_ALL_EPG_DATA` is signature-only, but the TV
  provider also returns other apps' *searchable* rows (the default) to holders of
  `READ_TV_LISTINGS`, a dangerous (runtime) permission. So Watch Next is read directly: the
  provider refuses a `selection` from other apps, so filtering (browsable, type, 95% / 2 min
  done, 60-day age, one per show) happens in `WatchNext`. Rows can't be deleted by another app,
  so Remove keeps a per-profile hidden set keyed on package, row id and engagement time — a row
  returns once it's watched again. Watch Next is the only source (Android TV 8+): 7.6's
  media-session history caught trailers and music, couldn't open the episode, and is blocked on
  low-RAM TVs, so it was removed (its saved files are deleted on first run).

- **Search without a list of services.** JustWatch says which services in India have a title and
  gives each one's links; Hume finds the app for each service itself (the package in the Android
  TV link, an installed non-browser handler of the web link, a remembered match, or an installed
  app with the same name), so new services need no update. Android 11+ hides other apps unless
  declared, so the manifest's `<queries>` lists launcher apps, voice input, the speech service
  and https handlers.
- **Search stays light.** It is an overlay in `MainActivity` (no new activity): apps match from
  the list already in memory on each key press; titles are fetched 350 ms after typing pauses with
  the previous request cut off, the last 20 searches are cached, posters are decoded at card size
  into a heap-capped LRU, and while Search is open the Home layers under it stop drawing and the
  carousel pauses. Typing measured p50 6 ms / p90 11 ms per frame on the 1 GB Android 9 TV.
- **Dictation.** `SpeechRecognizer` (Google's TV voice service) with partial results fills the
  field live after Android's microphone prompt; otherwise `RecognizerIntent` opens the TV's voice
  prompt. The remotes' Assistant key is routed by Android to Google Assistant before apps, so the
  remote shortcut is holding Back for 1 s (Back is replayed when released sooner).

## Performance (Mi Box, Mali-450 MP, GLES 2.0)

- Startup: the app list (package manager, banners, icon colours) and the wallpaper are prepared on
  background threads while the window's views are built: cold start ~1.0 s → ~0.86 s on a 1 GHz
  Android 9 TV.

- No perpetual animations (no wallpaper drift, no idle sway): idle draws 0 frames.
- Nothing is rendered in software on the UI thread during transitions (the original glass cache
  caused ~1 s freezes moving to/from the dock).
- Legibility gradients are baked into the wallpaper and artwork; the window background is removed
  to avoid hidden full-screen fills.
- Measure with `adb shell dumpsys gfxinfo com.petrobot.hume` — dock navigation runs at a 16 ms
  median; full-screen transitions are limited by the GPU's fill rate (~30 fps on this box).
