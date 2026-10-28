# Contributing to Hume Launcher

Thanks for helping. Hume Launcher is a tvOS 26–style home screen for Android TV: plain Java, no
Gradle, no third-party runtime libraries, built to stay smooth on low-end TV boxes. This guide
covers how to work on it; to set up your machine first, see
**[docs/getting-started.md](docs/getting-started.md)**.

## Contents

1. [Project in one page](#1-project-in-one-page)
2. [Workflow](#2-workflow)
3. [Code style](#3-code-style)
4. [Design rules](#4-design-rules)
5. [Performance rules](#5-performance-rules)
6. [Permissions and privacy rules](#6-permissions-and-privacy-rules)
7. [Testing a change](#7-testing-a-change)
8. [Pull requests](#8-pull-requests)
9. [Good first areas](#9-good-first-areas)
10. [Licensing](#10-licensing)

## 1. Project in one page

```
AndroidManifest.xml      package com.petrobot.hume, activities/services/receivers
build.sh                 the whole build (aapt2 → javac → d8 → zipalign → apksigner)
src/com/petrobot/hume/   the launcher (≈9,400 lines of Java)
src/com/petrobot/hume/present/   Presentation mode: HTTP server, player, storage
src/io/nayuki/qrcodegen/ vendored QR encoder (MIT)
res/                     manifest resources: icons, device-admin and accessibility XML, strings
assets/fonts/            Inter (OFL)
assets/present/          the browser admin app for Presentation (HTML/CSS/JS)
docs/                    user and developer documentation, screenshots
```

Key files (full map in **[docs/architecture.md](docs/architecture.md)**):

| File | What lives there |
|---|---|
| `MainActivity.java` | Home: dock/grid, focus, carousel, status row, Control Center, profiles UI, Liquid Glass, idle mode |
| `SettingsActivity.java` | Settings pages |
| `SetupActivity.java` | First-time setup pages |
| `Suggestions.java`, `News.java` | Carousel content (JustWatch/TMDB/Stremio recommendations, Indian news RSS) |
| `Search.java`, `SearchPanel.java` | Search: titles on every service with Open / Get, apps and settings; the search screen |
| `WatchNext.java` | Continue Watching from apps' Watch Next rows |
| `AppRows.java`, `Carousel.java` | Apps' own rows; composing the carousel from all its sources |
| `Startup.java` | Start with the TV (app or input at boot / wake) |
| `Motion.java` | Motion setting; wrap every new animation's duration in `Motion.d(...)`, and check `Motion.ambient()` before starting a loop |
| `MediaWatchService.java` | Notification listener for Do Not Disturb |
| `Weather*.java` | Weather data, pill and forecast panel, icons |
| `Perms.java`, `PowerService.java` | On-screen permission flows; accessibility service for Power Off / Clean / Home button / wake |
| `AirPlayGuard.java`, `Inputs.java`, `Clicks.java`, `Profiles.java` | AirPlay watchdog, TV inputs, sounds, profiles |
| `Ui.java`, `Glyphs.java` | Sizing (960×540 design grid), fonts, glyphs drawn in code |

Features are described in **[docs/features.md](docs/features.md)**.

## 2. Workflow

- `main` is protected: **never push to it**. Work on a branch and open a pull request; one approval
  is required to merge.
- Branch names: `feature/<short-name>`, `fix/<short-name>`, `docs/<short-name>`.
- Keep pull requests focused on one thing; several small ones beat one huge one.
- Commit messages: a short imperative summary line (≤ 72 chars), a blank line, then *what* and
  *why* in a few bullets. Mention what you tested and on which device.

```
Weather: retry the next source when one times out

- Open-Meteo timeouts left the pill empty until the next 15-minute tick.
- Tested on Mi Box (Android 12): pulled the network for 30 s, pill recovered.
```

- Bump the version only in release builds (`VERSION_CODE`/`VERSION_NAME` on the `build.sh` command
  line), not in source.

## 3. Code style

Match the code around you; consistency matters more than any rule here.

- **Java 11 language level**, Android API 24+ (`minSdk 24`, `targetSdk 34`). Guard newer APIs with
  `Build.VERSION.SDK_INT` checks.
- **No new runtime libraries.** The APK is ~1 MB and has no dependencies; keep it that way. Small,
  permissively licensed code may be vendored under `src/` with its licence (like Nayuki QR).
- **Views in code**, no XML layouts. Follow the existing patterns (`row(...)`, `ccTile(...)`,
  `option(...)`, `LiquidGlass`).
- **Sizes in design units**: always `dp(x)` / `Ui.px(ctx, x)`, never raw pixels, so 720p, 1080p and 4K
  keep their proportions. Text with `Ui.textSize`.
- **Fonts**: `Ui.font(ctx, "regular" | "medium" | "semibold" | "display")` (Inter).
- **ASCII-only source files.** Write non-ASCII characters as `\uXXXX` escapes (e.g. `"°"` for °).
- **Comments**: a short Javadoc on each class and non-obvious method saying *why*, matching the
  density of the surrounding code. No commented-out code.
- **Threads**: network, disk and bitmap decoding off the UI thread (`io` executor or a `Thread`);
  post results back with `handler`.
- **Strings**: user-facing text in sentence case for rows, Title Case for buttons/titles, as Apple
  does; keep it short and plain.

## 4. Design rules

Hume's whole point is to look and feel like Apple tvOS 26. Before building UI:

- **Measure Apple's real UI first** (Apple's tvOS screenshots and the Human Interface Guidelines),
  then implement and state the measured numbers in the code or PR. Don't improvise the look.
- Liquid Glass is for **controls and panels** (dock, buttons, Control Center, pills). App cards stay
  opaque.
- Focus: lift/scale (cards 1.2×, controls ~1.06–1.12×), white focused state for buttons/tiles, never
  a coloured outline. Make sure focused items have room to grow (no clipping by parents).
- Text must stay legible over any artwork: use the existing scrims/shades, minimum text size 11.5
  design units (≈ Apple's 23 pt tvOS minimum).
- Everything must work with a 5-key remote (arrows, OK, Back, Home): every focusable item reachable,
  focus never lost, Home always returns to the first dock app.

## 5. Performance rules

The target is a Mi Box 4 (Mali-450 MP GPU, GLES 2.0, fill-rate bound). Home navigation should stay
near a 16 ms median frame time.

- **No perpetual animations.** Idle Home draws ~0 frames per second.
- **Nothing rendered in software on the UI thread during transitions.** Bake expensive things
  (blurs, gradients, glass backdrops) into bitmaps once.
- Avoid hidden full-screen layers and overdraw; prefer one baked image to several stacked ones.
- Cache network results and images on disk; never block startup on the network.

Measure before and after any visual change:

```sh
adb shell dumpsys gfxinfo com.petrobot.hume reset
# …press Right/Left along the dock a few times…
adb shell dumpsys gfxinfo com.petrobot.hume | grep -E "Janky|50th|90th"
```

Include the numbers in your PR. `--es perf <layer>` (see getting-started) helps find costly layers.

## 6. Permissions and privacy rules

- **adb is the last resort.** Every permission must have an on-screen path where the TV allows it
  (runtime prompt, the system's own settings screen, accessibility switch, role request), and must
  degrade gracefully with a clear message where the TV can't do it. Add new flows to `Perms.java`.
- Detect what the device supports before offering a feature (e.g. low-RAM TVs can't grant
  notification access; some TVs have no device-admin feature).
- The accessibility service (`PowerService`) does only what the user asked from Hume (sleep, close
  apps). Don't extend it to read screen content.
- No analytics, no tracking, no accounts. Network calls go only to the content sources a feature
  needs (weather, news, recommendations, search, IP location) and never send personal data.
  Search queries go only to JustWatch (and Stremio's Cinemeta when Stremio/Nuvio is installed);
  recent searches stay on the TV.
- The microphone is used only while the user is dictating in Search (after Android's own prompt);
  Hume never records or stores audio itself.
- Never commit secrets (API tokens, passwords) — TMDB keys are entered by the user at runtime.

## 7. Testing a change

There is no automated test suite yet; changes are verified on devices. For every change:

1. `./build.sh` succeeds with no new warnings you introduced.
2. Install and exercise the change with the remote (or `adb shell input keyevent …`), including
   Back and Home from every new screen.
3. Take screenshots (`adb exec-out screencap -p > shot.png`) of new or changed screens; attach them
   to the PR.
4. If it touches the Home screen, record frame times (see §5).
5. If it touches permissions, network or storage, test on both an Android 12 device and an
   **Android 9 / 1 GB RAM** device, and with the network off.
6. Check `adb logcat -s TvHome AndroidRuntime` for errors and crashes.

Checklist for UI changes: legible over bright and dark artwork · nothing clipped when focused ·
reachable with arrows · Home returns to the dock · 720p and 1080p both look right.

## 8. Pull requests

Open the PR against `main` with:

- **What and why** (link the issue if there is one).
- **Screenshots** of every changed screen.
- **How you tested**: device(s), Android version, steps.
- **Frame-time numbers** for Home changes.
- Docs updated if behaviour changed (`docs/features.md`, and `docs/architecture.md` for new files).

Reviewers check: matches tvOS design, no performance regression, works with a remote, permissions
follow §6, code follows §3.

## 9. Good first areas

- New weather icons (`WeatherIcons.java`) or connector icons (`Glyphs.java`).
- More Indian city news feeds (`News.java`, `TOI_CITIES` and the city slugs).
- More streaming services for recommendations (`Suggestions.java`, `PROVIDERS`).
- Translations of setup and Settings text (no i18n yet — a good larger project).
- Automated checks: a script that builds and lints, or JVM tests for the parsers (weather, news,
  playlist model).
- Accessibility: TalkBack labels on custom views.

## 10. Licensing

Hume Launcher is licensed under the **[Apache License 2.0](LICENSE)**. By submitting a pull request
you agree that your contribution is licensed under the same terms (Apache-2.0, section 5). Keep the
[NOTICE](NOTICE) file accurate: add an entry for any third-party code or asset you bring in, and only
bring in code under a licence compatible with Apache-2.0 (MIT, BSD, Apache, OFL for fonts — not GPL).

Third-party code and assets already here: **Inter** font (SIL Open Font License,
`assets/fonts/Inter-LICENSE.txt`) and **Nayuki QR Code generator** (MIT, `src/io/nayuki/qrcodegen`).

### Signing

Development builds are signed with a local `debug.keystore` that `build.sh` creates on first use; it
is ignored by git. **Release builds are signed by maintainers only**, with the private key kept in
a separate private repository (never in this one):

```sh
RELEASE_KEYSTORE=/path/to/hume-release.jks RELEASE_KEYSTORE_PASS=... VERSION_CODE=.. VERSION_NAME=.. ./build.sh
```

A debug build can't update a release install (different key): uninstall first.
