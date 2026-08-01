# 8D Audio Converter

Offline Android app that renders a permanent, new 8D-panned copy of a song
(not a live playback effect) using FFmpegKit, stores it in Room so it's never
re-converted, and plays it back with full transport controls, background
playback, and lockscreen/notification/Bluetooth controls kept in sync via a
single shared Media3 session.

## Build

**Android Studio:** open the project, let Gradle sync, then Run ▸ app.
Android Studio generates its own `local.properties` (your local SDK path) on
first sync — it's git-ignored and never committed; nothing to set up by hand.

**GitHub Actions:** push to `main` (or open a PR against it) and
`.github/workflows/android.yml` runs `./gradlew assembleRelease` using the
**committed** Gradle wrapper — CI never runs `gradle wrapper` and never
generates the jar, it only validates the one already in the repo via
`gradle/actions/wrapper-validation`. The resulting APK is uploaded as a
workflow artifact (Actions tab ▸ latest run ▸ Artifacts ▸ `app-release`).
Need a debug build too? `./gradlew assembleDebug` works locally or add the
step back to the workflow — omitted from CI here to keep the pipeline to
exactly what was asked for (one release build, validated wrapper, cached
Gradle, one upload).

Release APKs are signed with Android's auto-generated debug keystore so CI
needs no secrets — replace with a real release keystore before publishing to
the Play Store.

See **CHANGELOG.md** for a full account of everything changed across each
review pass (FFmpeg dependency swap, wrapper, CI, Room migration safety,
Media3 sync fix, filter-chain ordering fix, `.gitignore`, lint-safety, dead
code removal).

## How the 8D effect actually works

`audio/AudioConverter.kt` builds a real FFmpeg `-af` filter chain
(`apulsator` → `extrastereo` → `aecho` ×2 → `stereotools` → `loudnorm` →
`alimiter` → `afade` in/out) and renders it to a brand-new file in
`filesDir/converted/`. That file, plus its metadata, is written to Room
(`ConversionEntity`), so re-selecting the same source `Uri` opens the
existing converted file instead of reprocessing (see
`ConversionRepository.findExistingConversion`).

## Known limitations

This is a full, real implementation of every screen and system described —
not stubs — but a project this size can't be guaranteed 100% warning-free on
first compile in an environment where I can't actually run Gradle/AGP myself.
If Android Studio flags anything on first sync, it's most likely:

- **`ffmpeg-kit-full` AAR size** — it's tens of MB (bundles FFmpeg's native
  libraries for every ABI), so first sync/build will take a while. If you
  only need MP3/AAC/WAV output (no exotic codecs), a slimmer FFmpegKit
  variant would shrink the APK, at the cost of writing your own build.
- **Dependency versions drifting further** — everything here was verified
  mutually compatible at writing (see CHANGELOG §4), but Android tooling
  moves fast; if Studio's upgrade prompt flags a newer AGP/Kotlin/Compose
  pairing, it's safe to take it.

## App icon

`res/drawable/ic_launcher_{background,foreground}.xml` + the adaptive-icon
XML define a custom vector icon (purple gradient disc, orbit ring + dot for
the "circular 8D pan" motif, waveform bars). Regenerate via Studio's Image
Asset tool if you want a different design — right-click `res` ▸ New ▸ Image
Asset ▸ Launcher Icons (Adaptive and Legacy).
