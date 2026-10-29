# Features

Everything Hume Launcher does, by area. For how the code is organised see
[architecture.md](architecture.md); to build it yourself see [getting-started.md](getting-started.md).

- [First-time setup](#first-time-setup)
- [Home screen](#home-screen) · [Remote controls](#remote-controls)
- [Status row](#status-row) · [Search](#search) · [Carousel (top shelf)](#carousel-top-shelf) · [Idle full-screen carousel](#idle-full-screen-carousel)
- [Weather](#weather) · [Control Center](#control-center) · [Profiles](#profiles) · [Inputs](#inputs)
- [Presentation mode](#presentation-mode) · [Screensaver](#screensaver) · [AirPlay](#airplay-with-the-airplay-server-app)
- [Motion & sound](#motion--sound) · [Settings](#settings) · [Permissions](#permissions) · [Display scaling](#display-scaling)

## First-time setup

The first time Hume opens it walks through Apple-style pages, one question each. Choices sit in a
list; moving on is always a separate pill button under it (**Back** · **Continue**), so picking an
option never skips a page. Each row shows what kind it is: an **On/Off switch**, a **radio dot**
(one of a list), or an **action** with its state on the right (*Allow ›*, *✓ Allowed*, *✓ Done*).
Choices change in place, and the remote's Back key also goes back.

1. **Welcome**, fading through Indian and world languages, then *Get Started*.
2. **Create Your Profile**: name (shown on the row; OK edits it) and an emoji avatar (ringed when chosen).
3. **Choose a Look**: backgrounds previewed live as you move; OK picks one (radio).
4. **Your Home Screen** (this profile): switches for Recommendations, News, Continue Watching and
   From Your Apps. The last two need the TV-listings permission: turning one on asks right there
   (on Android 7 they say *Needs Android TV 8 or later*).
5. **Weather**: use my location (shows the detected city), choose a city (opens Settings, comes
   back selected), or don't show weather.
6. **Presentation** (whole TV): off for most homes; if on, *Start with the TV* and *Keep Screen Awake*.
7. **Finish Setting Up**: Home Screen, Continue Watching & Your Apps, Power Off, Wi-Fi name — each
   with its state; nothing is asked until its row is pressed. On TVs without a home-app chooser
   (Google TV), *Set Up* turns on Hume's switch for the Home button.
8. **You're All Set** → Home.

Settings → Home → **Run setup again** repeats it. If Home is pressed mid-way, setup resumes.

| | | |
|---|---|---|
| ![Welcome](images/setup-0.jpg) | ![Profile](images/setup-1.jpg) | ![Look](images/setup-2.jpg) |
| ![Home Screen](images/setup-3.jpg) | ![Weather](images/setup-4.jpg) | ![Presentation](images/setup-5.jpg) |
| ![Finish](images/setup-6.jpg) | ![Done](images/setup-7.jpg) | |

## Home screen

- **Dock**: the first row of apps sits on a frosted Liquid Glass panel (a blurred, colour-boosted
  copy of what is behind it, with lensed edges and a faint rim).
- **Grid**: 6 cards per row in Apple's 5:3 shape; spacing, margins, corner radii and the dock
  position were measured from Apple's tvOS 26 screenshots and the HIG (60 pt / 80 pt safe area).
- **Focus**: the card lifts to 1.2× with a soft shadow and a glare sweep; OK dips the card (the
  HIG's pressed state) and zooms it to full screen while the app opens.
- **Names** appear under focused cards below the dock (none inside the dock, as on tvOS).
- **More Apps**: hidden apps live in a folder card at the end of the grid.
- **System apps** (Settings, Play Store…) get tvOS-style cards: a gradient with a white glyph.
- **Background**: Aurora, Midnight, Sunset, Ocean or Graphite (per profile).

### Remote controls

| Where | Key | Action |
|---|---|---|
| Any card | OK | Open (zoom animation) |
| Any card | Hold OK / Menu | Options: Open, Move, Hide, App Info, Uninstall, Reset Layout |
| Move mode | Arrows / OK | Move the wiggling card / finish |
| Dock | Up | Carousel |
| Carousel | Left / Right | Previous / next item (wraps around) |
| Carousel (Continue Watching item) | OK / Hold OK or Menu | **Resume** / options: Resume, Remove (Left/Right keep browsing) |
| Status row | Left / Right | Weather pill ⇄ Refresh ⇄ Search ⇄ Control Center ⇄ avatar |
| Anywhere on Home | Hold Back for 1 second | Opens Search (a short press of Back works as before) |
| Anywhere on Home | Search key on remotes that have one | Opens Search and starts dictation |
| Anywhere on Home | Keyboard: type a letter or digit · `/` · Ctrl+F · Ctrl+K | Opens Search (with the letter typed) |
| Status row | Down | Carousel (or dock) |
| Anywhere | Home | Closes any overlay and goes to the first app in the dock |
| Idle screen | Any key | Brings the Home Screen back (the press doesn't move focus) |

## Status row

Top right, as on tvOS:

- **Internet indicator and time** on a faint dark chip for contrast over bright artwork. The
  indicator is drawn like Apple's symbols: Wi-Fi bars lit by signal strength (or an Ethernet
  symbol) when the internet works; dimmed bars with **!** on a network that can't reach the
  internet; dimmed bars with a slash when there is no network.
- **Refresh**: fetches a fresh set of recommendations and news, reloads Continue Watching and
  forces a weather update.
- **Search**, **Control Center** and the **profile avatar**.

Glass controls adapt to what is behind them: the backdrop is desaturated and the tint darkens over
bright artwork and lightens over dark artwork, so white text stays legible.

## Search

The magnifier next to Control Center opens Search over the blurred Home Screen, as on tvOS: what
you typed at the top, a one-row keyboard under it, results below. Shortcuts: **hold Back** on any
remote, the Search key on remotes that have one, or just start typing on a keyboard. (The
Assistant/mic button on most Android TV remotes is taken by Google Assistant before any app sees
it, so it can't open Hume's Search.)

- **Keyboard**: a–z, space, delete, **123** (digits and punctuation), **अआ** (Hindi letters) and
  clear. A USB/Bluetooth keyboard and the remote's number keys type too.
- **Dictation**: the **mic** beside the search field (Up from the keyboard), or the remote's
  search/mic button on Home or in Search. Where the TV has Google's voice service, the words appear
  in the field as you speak and results follow; the microphone is asked for on screen the first
  time. Esc on a keyboard, or Back, closes Search. Without that permission or service, the TV's own voice prompt (Google's) listens instead.
  English and Hindi (Indian English, with Hindi as a second language).
- **Before typing**: your recent searches (per profile) and what's trending in the carousel.
- **Apps & Settings** (instant, no internet): installed apps including hidden ones, TV inputs
  ("HDMI 2"), Hume screens (Control Center, Weather, Switch Profile, Hume Settings) and TV settings
  ("wifi", "bluetooth", "screensaver"…).
- **Movies & TV**: every title on any streaming service in India from JustWatch — Hindi and
  regional titles included, and searches typed in Hindi (पंचायत) work — as posters with the logos
  of the services that have each one. With Stremio or Nuvio installed, **On Stremio** adds titles
  only Stremio's catalogue has. Titles are searched once typing pauses; repeat searches are
  instant.
- **How to Watch**: OK on a poster opens the title's page (artwork, year, genres, IMDb rating,
  summary) with a button per app: **▶ Open in …** for apps you have (straight to the title where
  the app allows it) and **Get …** for the rest (the app's Play Store page), each marked
  *Subscription*, *Free*, *Free with ads*, *Rent* or *Buy*.
- **Any service, no list**: Hume matches each service to its app itself — the app named in the
  service's Android TV link, an installed app that opens the service's website, or an installed
  app with the same name — so new services work without an update.
- Settings → Home → Search: turn Movies & TV results off, or clear recent searches. Searches go
  only to JustWatch (and Stremio's catalogue when Stremio/Nuvio is installed); nothing is logged.

| | |
|---|---|
| ![Search before typing](images/search-empty.jpg) | ![Movies & TV results](images/search-results.jpg) |
| ![Apps & Settings and titles](images/search-apps-settings.jpg) | ![How to Watch: Open](images/search-how-to-watch.jpg) |
| ![How to Watch: Get](images/search-get.jpg) | ![Search button in the status row](images/status-row-search.jpg) |

## Carousel (top shelf)

Full-screen artwork behind the dock while a dock app is focused. One carousel holds, in order:

1. **Continue Watching** (up to six): what you're in the middle of, newest first, from apps'
   **Watch Next lists** (Netflix, Prime Video, Crunchyroll, Jellyfin, Nuvio and other apps that
   publish one). **Android TV 8.0 and later**; on older TVs Hume says it isn't supported.
   - The show, season and episode, artwork, time left, and **Up Next** / **New** episodes.
     **▶ Resume** opens the episode itself in the app. Works on low-RAM TVs.
   - Nothing under **10 minutes** (trailers, clips, shorts, ads).
   - One card per show per app, at most three per app. A title is done at 95% or with under
     2 minutes left (the credits). **Hold OK** (or Menu) → **Remove** hides it until you watch
     it again; Left/Right always move through the carousel. Titles not played for 60 days drop off.
   - Apps that don't publish a Watch Next list (or hide it from other apps) don't appear.
   Items from apps' own *Continue watching* rows (e.g. Stremio's) follow the dated ones.
2. **From your apps**: the rows apps publish for the home screen (YouTube, Stremio, Jellyfin, …),
   one row per app, at most two items per app and eight in all, taken in turn across apps. Which
   items show rotates every two hours. **▶ Open** goes to the item in its app; Hold OK →
   *Don't show <app> here*. Settings → Top Shelf → From Your Apps turns them off or picks apps.
3. **News** and **recommendations**, alternating; after Continue Watching, one app item is
   placed for every two of these. A title appears once (Continue Watching, then an app's row,
   then a recommendation).

- **Recommendations**: popular titles in India on your services. Source **JustWatch** (no key,
  unofficial API) or **TMDB** (free key). Each refresh picks at random from each service's top 20,
  so the set changes through the day (every 2 hours, or Refresh). **▶ Open** goes to the title's
  page in the app.
- **Stremio** as the source instead: Cinemeta's popular movies and series (no key). **▶ Open**
  goes to the title's page in **Stremio**, or in **[Nuvio](https://github.com/NuvioMedia/NuvioTV)**
  when only Nuvio is installed; **Get** opens Stremio on the Play Store. Settings → Top Shelf →
  Source; with Stremio chosen, *Opens in* shows which app will be used.

  ![Stremio as the source](images/settings-stremio.jpg)
- **News**: India, local (your city, from the weather location) and world, from Indian newsrooms'
  RSS feeds with large images: Times of India, Hindustan Times, The Indian Express, India Today and
  News18 by default (NDTV and The Hindu optional). An editorial layout made for reading: headline,
  summary, section, newsroom and age; no button. Updates every 30 minutes (15/30/60).
- Each profile chooses what it shows: Settings → **Top Shelf** has a switch for each source, in the
  carousel's order — Continue Watching, From Your Apps, Recommendations, News — then each one's
  options (Clear history, which apps, source, services, newsrooms…). Setup asks the same.

## Idle full-screen carousel

After 20 seconds without input on Home (Settings → Home: Off, 10, 20, 30 or 60 s) the dock and grid
slide down, the weather pill fades, and the carousel keeps playing full screen with its title and
summary at the bottom. The first key press only brings the Home Screen back.

![Idle carousel](images/idle-carousel.jpg)

## Weather

- A small glass **weather pill** in the top-left corner: icon, temperature, condition, city and
  today's high/low. OK opens the forecast: current conditions, humidity and wind, the next six
  hours, and a 5-day list with temperature range bars (blue → red, a dot for now), as in Apple
  Weather.
- Free sources with no key: [Open-Meteo](https://open-meteo.com) (default),
  [MET Norway](https://api.met.no) and [wttr.in](https://wttr.in); the chosen one first, the others
  as fallbacks. The panel shows which answered and when.
- Updates every **15 minutes** by default (10/15/30/60), and when the internet returns. The last
  forecast is cached, so it shows instantly and offline.
- Location from the internet address (ipapi.co → ipwho.is → geojs.io) unless you choose a city.

## Control Center

Opened from the button next to the time; the whole Home Screen is blurred behind it. The focused
tile turns solid white.

| Tile | What it does |
|---|---|
| Power Off | Puts the TV to sleep (see [Permissions](#permissions)) |
| Memory | Usage bar; OK closes the apps opened from Home since the last clean |
| Wi-Fi | Network name; opens Wi-Fi settings |
| Do Not Disturb | On / off |
| Audio | Current output; opens sound settings |
| Sleep Timer | Off → 15 → 30 → 60 → 90 min |
| Motion | Full → Reduced → Off (see [Motion & sound](#motion--sound)) |
| Sounds | Navigation sounds on / off |
| Round shortcuts | Inputs (TVs with inputs; Accessibility otherwise), Bluetooth/controllers, Settings, Search |

![Control Center](images/control-center.jpg)

## Profiles

- Up to **3 profiles**, each with a **unique emoji** on a pastel circle.
- Each keeps its own app layout and hidden apps, background, carousel choices and Continue Watching.
- The avatar opens a full-screen **Who's Watching?** switcher with large centred avatars over a
  blurred Home Screen and **+ Add User**. Long-press OK (or Menu) on a profile: Rename, Change
  Emoji, Change Colour, Delete.
- With two or more profiles, the switcher can appear when the TV wakes (Settings → Profiles).

## Home button

On TVs that keep their own launcher in front (Google TV and most TV brands), Hume doesn't need it
disabled: with **Hume's switch** on (an accessibility service), Hume opens whenever the TV's own
home screen appears, from the Home button or Back out of an app. The stock launcher's window
may flash for a moment. Only the name of the window that opened is checked. Settings → Home →
*Home button opens Hume* (shows *Not needed* when Hume is already the home app).

If Hume is **force-stopped** (App info → Force stop, or some TV "cleaner" apps), Android pauses its
switch until the TV restarts or the switch is turned off and on again; Hume's prompt says so.
Ordinary memory pressure doesn't do this — Android restarts the switch by itself.

## Start with the TV

Settings → Home → **Start with the TV**: open an app or an input (HDMI 1, …) **when the TV starts**,
or **when it starts or wakes from standby**, instead of Home. For the whole TV, not per profile.
Waking into an app needs Hume's switch (Android only lets an accessibility service open screens
from the background); at start-up Hume is on screen anyway. Presentation's own *Start on boot*
wins at boot. When it opens something on wake, the *Who's Watching?* picker is skipped.

## Inputs

Only on TVs with hardware inputs (HDMI, AV, component, tuner): an **Inputs** card starts first in the
dock and an Inputs shortcut sits in Control Center. The picker shows each input with its connector
icon (HDMI, AV, component, S-Video, VGA/DVI, DisplayPort, SCART, USB, antenna); a plugged-in USB
drive appears too. App channel sources (e.g. Mi TV+) are not inputs. The card can be moved, hidden
or switched off (Settings → Inputs).

![Input picker](images/inputs-picker.jpg)

## Presentation mode

Send images, videos and webpages from any browser on the same Wi-Fi and play them full screen in a
loop, optionally starting with the TV (signage). See **[presentation.md](presentation.md)**.

## Screensaver

Hume never holds the screen on, so the TV's own screensaver (e.g. Aerial Views) starts after the
TV's timeout. Settings → Home → **Screensaver on Home**: *System screensaver* (default) or *Hume
full-screen carousel*, which keeps the screen on and lets the carousel play instead. Also shows the
TV's screensaver (opens its settings or app) and **Start screensaver now**.

## AirPlay (with the AirPlay Server app)

Android TV can't receive AirPlay by itself. Install the free, open-source
**[AirPlay Server](https://github.com/jqssun/android-airplay-server)** app
(`io.github.jqssun.airplay`, GPL-3.0) and the TV shows up in AirPlay on Macs, iPhones and iPads for
screen mirroring, video and audio. See [install.md](install.md#optional-airplay-receiver) to set it
up. It appears in Hume like any other app.

**AirPlay guard** (automatic, only when that app is installed): after long sleeps the AirPlay app
can keep running without announcing itself, so the TV vanishes from AirPlay lists. On wake, when
the network returns and every 15 minutes on Home, Hume asks the network (an mDNS query, as a Mac
does) whether the receiver is visible; if not, it restarts the receiver's server through the app's
settings receiver, or starts a stopped app through its start-up receiver. Nothing appears on screen.
The receiver's name is set in the AirPlay app (e.g. "Mi Box").

## Motion & sound

**Motion** (Settings → Home → Motion & Sound, or the Control Center tile; whole TV):

| | |
|---|---|
| **Full** (default) | Every transition, plus motion that runs by itself: focus sway, and the carousel artwork slowly drifting closer (1.0 → 1.06 over 18 s, like Apple TV's Top Shelf) |
| **Reduced** | Transitions at 60% length and half the slide distance; nothing moves by itself |
| **Off** | Changes are instant |

Apple-style cascades, everywhere something appears as a group (each item fades up from slightly
below and slightly smaller, 22 ms after the one before): the app grid on first start and on
switching profile, Control Center and Inputs tiles, the *Who's Watching?* avatars, Settings rows
on every page, and each setup page's title, choices and buttons. Moving between apps (move mode)
still wiggles at every level, since it shows what is being moved.

**Navigation sounds**: Android TV's own system sounds, a focus sound per D-pad direction and the
key click on select and Back, at 60% of the normal level. Settings → Home → Motion & Sound, or the
Control Center tile.

## Settings

Opened from the gear in Control Center. A main list opens a page per section (Back returns):
Profiles, Top Shelf, Presentation, Inputs, Weather, Continue Watching, Home, Permissions, About, and
the TV's own settings.

| | |
|---|---|
| ![Settings](images/settings-root.jpg) | ![Top Shelf](images/settings-top-shelf.jpg) |

## Permissions

Hume asks on screen wherever the TV allows it; adb is only a last resort.

| Feature | How it is granted |
|---|---|
| Wi-Fi name (location) | Android's permission prompt |
| Continue Watching (apps' Watch Next lists) | Android's permission prompt (*TV listings*). **Android TV 8.0 and later** — Watch Next and this permission both arrived in 8.0; on Android 7 Continue Watching isn't supported, and setup says so |
| Do Not Disturb | The system notification-access screen. Not possible on low-RAM (1 GB) TVs, where Android disables it — Hume says so |
| Power Off & Sleep Timer | Android's "Activate device admin app?" screen; where device admin is switched off (e.g. Mi Box), Hume's accessibility switch **Hume Launcher** in the TV's Accessibility settings (opened for you). The same switch lets Clean close apps, and powers the Home button override and waking into an app |
| Search dictation (microphone) | Android's permission prompt the first time the mic is used; without it, the TV's voice prompt is used |
| Home screen | Android 10+ "default home app" prompt, the TV's Home-app settings, or Android's chooser; Hume offers this once if another launcher is home |

Only when a TV has none of these screens is the one-time adb command shown.

## Display scaling

All sizes are relative to the screen's resolution (a 960×540 design grid), so the layout keeps its
proportions on 720p, 1080p and 4K and rebuilds itself if the display changes.
