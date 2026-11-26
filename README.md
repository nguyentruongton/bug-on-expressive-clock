<p align="center">
  <img src="desktop/src/main/resources/icons/expressive-clock.svg" width="112" height="112" alt="Expressive Clock icon" />
</p>

<h1 align="center">Expressive Clock</h1>

<p align="center">
  <em>A Material 3 Expressive clock app for Android &amp; Linux Desktop.</em>
</p>

Expressive Clock is a personal evolution of [Material Clock](https://github.com/rbouaf/material-clock) by [Rayane Bouafia (@rbouaf)](https://github.com/rbouaf), further developed by [Samuel Beckman (@samuelbeckman)](https://github.com/samuelbeckman/expressive-clock).

This fork extends **Expressive Clock** with a native **Linux Desktop** port built on **Compose Multiplatform (JVM / Skia)** while keeping the upstream Android codebase (`app/` and `gradle/`) completely untouched (*zero-touch upstream*), making it effortless to pull future updates from upstream straight into the Linux Desktop build.

![Expressive Clock showing alarms, timers, stopwatch and world clock](docs/screenshots.png)

## Features

- **Alarms:** Schedule recurring or specific-date alarms with ringing, snooze and dismiss controls.
- **World Clock:** Track multiple cities and time zones with an expressive analog/digital view.
- **Timers:** Create and manage countdown timers with presets.
- **Stopwatch:** Track elapsed time and laps.
- **Expressive Design:** Material 3 Expressive styling, Google Sans Flex typography and carefully crafted animations.
- **Analog Clock Widget (Android):** Configurable home-screen clock widget.

### Linux Desktop Highlights

- **Compose Multiplatform (JVM / Skia):** Runs natively on Wayland and X11 with hardware-accelerated rendering and the exact same Material 3 Expressive UI as Android.
- **Background Alarms & Timers (`systemd`):** Scheduled via `systemd` user timers (`expressive-clock-alarm@.timer`, `expressive-clock-timer@.timer`) so alarms and timers fire reliably even when the app window is closed.
- **Native Audio & Notifications:** Looping alarm playback via PipeWire (`pw-play`, with `mpv` / `aplay` fallback) paired with critical `libnotify` (`notify-send`) notifications featuring direct **Snooze** and **Dismiss** actions.
- **Live Material You Dynamic Theming:** Automatically watches **Caelestia** (`~/.local/state/caelestia/scheme.json`) and **Matugen** (`~/.config/matugen/colors.json`) for real-time wallpaper color and light/dark mode synchronization.
- **Desktop Ergonomics:** Single-instance Unix domain socket (`$XDG_RUNTIME_DIR/expressive-clock.sock`), custom alarm sound file picker (`zenity`), and live UI scaling shortcuts (`Ctrl +`, `Ctrl -`, `Ctrl 0`).

---

## Linux Desktop: Building & Installing

### Prerequisites

- **Build:** JDK 21 (`jdk21-openjdk` or any JDK 21 toolchain)
- **Runtime:** `java-runtime >= 21`, `pipewire`, `libnotify`
- **Optional:**
  - `matugen` — live Material You wallpaper color synchronization
  - `zenity` — native Wayland/X11 file picker for custom alarm sounds
  - `mpv` — alternative audio playback backend
  - `socat` — fast Unix socket forwarding for single-instance alarm triggers

### Option 1: Install on Arch Linux (`PKGBUILD`)

```bash
git clone https://github.com/nguyentruongton/bug-on-expressive-clock.git
cd bug-on-expressive-clock/desktop/packaging
makepkg -si
```

This builds a self-contained distributable with a trimmed `jlink` runtime and installs:
- Launcher binary at `/usr/bin/expressive-clock` (bundle in `/usr/lib/expressive-clock/app`)
- Background trigger script & fallback alarm audio
- `systemd` user template units (`expressive-clock-alarm@.{timer,service}`, `expressive-clock-timer@.{timer,service}`)
- Desktop entry (`/usr/share/applications/expressive-clock.desktop`)
- Application icons (`48`–`512` PNG + scalable SVG) in `/usr/share/icons/hicolor/`

### Option 2: Run directly from source

```bash
./gradlew -p desktop run
```

### Option 3: Build AppImage or Standalone Distributable (Other Linux Distros)

Build a portable **AppImage**:

```bash
./gradlew -p desktop packageAppImage
```

Or build a standalone distributable directory (bundled with a trimmed `jlink` JVM runtime):

```bash
./gradlew -p desktop createDistributable
```

Outputs are generated under `desktop/build/compose/binaries/main/`.

---

## Syncing with Upstream

Because the Linux port lives entirely under `desktop/` and syncs shared Kotlin sources and resources from `app/` at build time, pulling updates from `samuelbeckman/expressive-clock` stays clean and conflict-free.

1. Configure the `upstream` remote (once):
   ```bash
   git remote add upstream https://github.com/samuelbeckman/expressive-clock.git
   git remote set-url --push upstream no_push
   ```
2. Run the automated sync script whenever upstream releases an update:
   ```bash
   ./desktop/scripts/sync-upstream.sh
   ```

The script automatically:
- Fetches and merges `upstream/main`
- Checks the 8 desktop-shadowed files for upstream drift (`DRIFT WARNING`)
- Verifies `./gradlew -p desktop compileKotlin`
- Confirms `app/` and `gradle/` remain untouched (*zero-touch upstream*)

---

## Building for Android

Expressive Clock is also a full Android application built with Kotlin and Jetpack Compose.

1. Clone this repository.
2. Open the project in Android Studio.
3. Allow Gradle to sync and download the required dependencies.
4. Build and run the app on a compatible Android device or emulator, or run `./gradlew assembleDebug` from the command line.

## Acknowledgements

- Original Android project: [Material Clock](https://github.com/rbouaf/material-clock) by [Rayane Bouafia (@rbouaf)](https://github.com/rbouaf).
- Upstream Expressive Clock: [samuelbeckman/expressive-clock](https://github.com/samuelbeckman/expressive-clock) by [Samuel Beckman (@samuelbeckman)](https://github.com/samuelbeckman).

This fork preserves the original project's licensing and attribution while adding Linux Desktop support.

## Licence

The project's original source code is licensed under the MIT Licence. See [LICENSE](LICENSE) for the full licence text.

Bundled font files are licensed separately under the SIL Open Font License 1.1. See [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) and the files in the `licenses/` directory for details.

Google, Google Sans, Material, Android and Pixel are trademarks of Google LLC. Expressive Clock is an independent project and is not affiliated with, sponsored by or endorsed by Google.
