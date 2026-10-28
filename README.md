# Hume Launcher

A tvOS 26–style home screen for Android TV, by Petrobot. It replaces the stock launcher with one
modelled on Apple TV — glass dock, full-screen carousel, Control Center, profiles — and is built to
stay smooth on low-end TV boxes like the Xiaomi Mi Box. Plain Java, no Gradle, no third-party
runtime libraries; the APK is about 1 MB.

![Home screen](docs/images/home-dock-glass.jpg)

## Features

- **Home**: Liquid Glass dock, 6-across app grid in Apple's measured proportions, focus lift and
  zoom-to-open, More Apps folder, five backgrounds.
- **Carousel**: Continue Watching first (apps' Watch Next lists, Android TV 8+; Resume opens the
  episode), then Indian news
  (India, your city, world) and rotating recommendations from your streaming services. Goes full
  screen when Home is idle.
- **Status row**: internet indicator, time, Refresh, Search, Control Center, profile avatar.
- **Search**: apps, settings and inputs instantly, plus movies and shows on every streaming service
  in India (Indian titles included, typed in English or Hindi, or spoken) with **Open** in the app
  you have or **Get** for the one you don't.
- **Weather**: a glass pill with a 5-day forecast panel (Open-Meteo, MET Norway or wttr.in — no keys).
- **Control Center**: Power Off, Memory/Clean, Wi-Fi, Do Not Disturb, audio, sleep timer, inputs,
  settings, search.
- **Profiles**: up to 3, each with its own layout, background and carousel; full-screen switcher.
- **Inputs**: HDMI/AV/TV picker with connector icons, on TVs that have inputs.
- **Presentation mode**: send images, videos and webpages from a browser and loop them full screen;
  can start with the TV (signage).
- **First-time setup** in Apple's style, **multi-page Settings**, permissions asked **on screen**
  (adb only as a last resort), system or carousel **screensaver**, and Android TV navigation sounds.
- **AirPlay**: works with the open-source [AirPlay Server](https://github.com/jqssun/android-airplay-server)
  app; Hume keeps the receiver visible to Macs and iPhones after the TV sleeps.

Full list with screenshots: **[docs/features.md](docs/features.md)**.

## Quick start

```sh
# needs JDK 17+ and Android SDK build-tools 34.0.0 + platform android-34 (see getting started)
./build.sh                                   # → build/tvos-home.apk
adb connect <tv-ip>:5555
adb install -r build/tvos-home.apk
adb shell am start -n com.petrobot.hume/.MainActivity
```

## Documentation

| | |
|---|---|
| **[Getting started](docs/getting-started.md)** | Install the tools, build, run on a TV or the emulator, developer shortcuts |
| **[Features](docs/features.md)** | Everything Hume does, by area |
| **[Install and set up](docs/install.md)** | Make it the home screen, permissions, undo |
| **[Presentation mode](docs/presentation.md)** | Sending content from a browser, the player, the web API |
| **[Architecture](docs/architecture.md)** | Code map, build, design decisions, performance |
| **[Contributing](CONTRIBUTING.md)** | Workflow, code style, design and performance rules, testing, pull requests |

## Requirements

Android TV 7.0+ (API 24). Developed and tested on a Xiaomi Mi Box 4 (Android 12) and an Android 9
TV with 1 GB RAM.

## License

Apache License 2.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE).

## Credits

Inter font (SIL OFL) · Nayuki QR Code generator (MIT) · weather from Open-Meteo, MET Norway and
wttr.in · recommendations from JustWatch / TMDB / Stremio Cinemeta · news from Indian newsrooms' public RSS feeds.
