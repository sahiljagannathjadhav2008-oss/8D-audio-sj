# 8D Audio

An offline Android music player that applies a **real-time, in-pipeline 8D
spatial-movement effect** to your local music while it plays. Nothing is
converted, duplicated, or uploaded — the source file on your device is never
touched, and the effect exists only in the live audio stream leaving your
speakers or headphones.

## What "real-time 8D" means here

Most "8D audio" apps you'll find are either a pre-rendered MP3 someone
processed once in a DAW, or a thin wrapper around Android's built-in
`Virtualizer`/`Equalizer` effects. This app is neither. `Circular8DAudioProcessor`
(`app/src/main/kotlin/com/sj/audio8d/dsp/Circular8DAudioProcessor.kt`) is a
custom [Media3 `AudioProcessor`](https://developer.android.com/reference/androidx/media3/common/audio/AudioProcessor)
inserted directly into ExoPlayer's `DefaultAudioSink` pipeline. It runs on
every decoded PCM buffer during playback:

1. **True bypass check.** When 8D is off and bass boost and reverb are
   also off, and the smoothed ramp state has already settled, the buffer is
   bulk-copied to the output with zero floating-point math — a genuine
   "unchanged signal" path, not just a zero-gain DSP pass. Right after you
   flip 8D off, a few milliseconds of real per-sample smoothing still run so
   the transition doesn't click; once that settles, the fast path takes over
   for as long as everything stays off.
2. **Mid/side decode + stereo-width scaling** (stereo input) — width is
   adjustable from collapsed-mono to wider-than-original, and is itself
   gated by the 8D toggle (see "Bypass and independent effects" below).
3. **Equal-power panning driven by a continuously advancing phase
   accumulator** — not a left/right toggle. Circular, horizontal, vertical,
   and a smooth pseudo-random pattern are all expressed as continuous
   functions of that phase, so there is no pattern that can produce a hard
   L/R jump. A mono source is spatialized directly from its single sample
   rather than merely duplicated to both channels — see "Mono handling"
   below.
4. **Optional one-pole low-shelf bass boost** — has its own switch,
   independent of the 8D toggle.
5. **Optional short feedback-delay "ambience"** (off by default, subtle
   when on) — also independently switched.
6. **NaN/Infinity guard + soft-knee limiter** so a bad input or an aggressive
   parameter combination can never produce a click, a pop, or a corrupted
   sample.

Every continuous parameter (intensity, width, bass gain, reverb mix) is
one-pole smoothed toward its latest target on every sample. That's what lets
you drag a slider mid-song without hearing a click — the DSP state chases the
new target instead of jumping to it.

**Bypass and independent effects, stated precisely:** the 8D master toggle
gates panning and stereo width only. Bass boost and reverb have their own
switches in 8D Controls and keep working even with 8D off, since they're
separately user-enabled effects, not part of the spatial movement itself.
With 8D, bass boost, and reverb all off, playback is an exact sample-for-
sample passthrough (see `Circular8DAudioProcessorTest`'s bypass test) — not
an approximation.

**Mono handling, stated precisely:** a mono 16-bit PCM source is
reconfigured to a genuine stereo output (`onConfigure` changes the pipeline's
channel count from 1 to 2) and spatialized from the single sample using the
same phase-driven equal-power pan as stereo sources. With 8D off, mono is
still centered evenly across both channels at constant equal-power gain
(cheap — no trigonometry needed when the pan angle isn't advancing).

**Zero-allocation hot path:** panning gains are written directly into two
primitive instance fields (`panGainL`/`panGainR`) instead of being returned
as a `Pair`, and the pattern dispatch is a plain `when` over an
already-existing enum value. No `Pair`/`Triple`/collection/lambda/data class
is created inside the per-frame loop in `queueInput`.

**Format scope, stated plainly:** the spatial effect processes 16-bit PCM
mono or stereo, which is what ExoPlayer's decoders output for the vast
majority of local music files (MP3, AAC, M4A, WAV, FLAC, OGG, Opus all
decode to this). Multichannel (>2) sources, or the rare device/format
combination that decodes to a different PCM encoding, are detected in
`onConfigure()` and passed through unprocessed rather than risking a crash
or corrupted audio — see the class doc comment for the exact contract.

## Architecture

```
UI (Jetpack Compose)
   |
PlayerViewModel / LibraryViewModel
   |
PlayerRepository  ---------------->  MediaController
   |                                      |
MusicRepository (MediaStore)       PlaybackService (MediaSessionService)
FavoritesRepository (DataStore)          |
RecentlyPlayedRepository (DataStore)  ExoPlayer + DefaultAudioSink
SettingsRepository (DataStore)           |
                                   Circular8DAudioProcessor (custom AudioProcessor)
                                          |
                                   Android audio output
```

- **UI**: Jetpack Compose + Material 3, dark/cyan theme inspired by the
  supplied reference. Screens: Splash (native `SplashScreen` API), Home,
  All Songs, Search, Artists, Albums, Now Playing (with the live 8D
  visualizer and "RUN IN 8D"), 8D Controls, Queue, Favorites, Recently
  Played, Settings, About, and a Permission screen.
- **Playback**: a single `ExoPlayer` + `MediaSession` live inside
  `PlaybackService`, a `MediaSessionService`, so playback and the 8D effect
  keep running when the app is minimized, the screen is locked, or another
  app is opened. Audio focus, media notification controls, and
  pause-on-headphone-disconnect are handled by Media3's built-in behavior
  (`setHandleAudioBecomingNoisy(true)`, audio-focus handling enabled on
  `AudioAttributes`). The custom `Circular8DAudioProcessor` is spliced into
  the pipeline via `EightDRenderersFactory`, a `DefaultRenderersFactory`
  subclass that overrides `buildAudioSink()` — `ExoPlayer.Builder` itself has
  no public "set audio sink" hook, so this factory-override is the only
  supported way to do this, and `ExoPlayer.Builder(context,
  EightDRenderersFactory(...))` is how the player is constructed.
- **Library**: scanned via `MediaStore` only — no raw filesystem walking.
  Missing/blank metadata falls back to safe defaults; a single malformed row
  is skipped rather than aborting the scan.
- **Persistence**: Jetpack DataStore for favorites, recently played, and the
  default 8D preset/app settings. No Room, no local database file — this
  keeps the dependency surface (and therefore the CI build) smaller and more
  reliable, per this project's stated priority order (build reliability
  before extra features).
- **8D visualizer**: reads the *same* `MovementPattern` and speed the DSP
  uses (see `EightDVisualizer.kt`), so the on-screen animation is a
  representation of the real audio state, not an independent animation.

## Supported audio formats

MP3, AAC, M4A, WAV, FLAC, OGG, and Opus — whatever the device's Media3/
platform decoders support. An unsupported or corrupted file is skipped with
a message; the app does not crash and does not promise a format the device
can't actually decode.

## Permissions

- `READ_MEDIA_AUDIO` (Android 13+) / `READ_EXTERNAL_STORAGE` (below) — to
  read the local music library via MediaStore.
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` — background
  playback.
- `POST_NOTIFICATIONS` — the media/playback notification.
- No `INTERNET` permission is requested. The app makes no network calls of
  any kind.

## Offline-first

Playback, the 8D DSP, library scanning, search, favorites, queue, and
settings all work with the device fully offline, because none of them ever
touch the network. There's no account and nothing to sign in to.

## Build

This project is built entirely through GitHub Actions
(`.github/workflows/android.yml`) from a clean checkout — no manual SDK
install, no local Gradle install, no Android Studio required:

```
./gradlew assembleRelease
```

The workflow: checks out the repo, sets up JDK 17 (Temurin) and the Android
SDK, validates the Gradle wrapper, runs unit tests (`./gradlew test`), runs
lint, builds the release APK, verifies the APK actually contains
`AndroidManifest.xml` and `classes.dex`, and uploads it as the
`8DAudio-release-apk` workflow artifact.

**Release signing**: no release keystore is supplied or expected. The
release build type is signed with the debug keystore so `assembleRelease`
succeeds unattended in CI. Replace `signingConfigs.release` in
`app/build.gradle.kts` with a real signing config (backed by
`secrets.*` in the workflow) before distributing the APK anywhere users
would install it directly, since a debug-signed APK cannot be upgraded to a
release-signed one later without users uninstalling first.

### Dependency versions actually used

| Component | Version |
|---|---|
| Android Gradle Plugin | 8.6.1 |
| Gradle | 8.9 |
| Kotlin | 2.0.21 |
| JDK | 17 |
| compileSdk / targetSdk | 34 |
| minSdk | 26 |
| Compose BOM | 2024.09.03 |
| Media3 (exoplayer/session/common/ui) | 1.4.1 |
| AndroidX Navigation Compose | 2.8.1 |
| AndroidX Lifecycle | 2.8.6 |
| AndroidX DataStore Preferences | 1.1.1 |
| kotlinx-coroutines | 1.8.1 |
| kotlinx-serialization-json | 1.7.1 |

These were selected as a mutually compatible stable set at time of writing.
Re-check compatibility before bumping any one of them in isolation.

## Testing

`app/src/test/kotlin/com/sj/audio8d/dsp/Circular8DAudioProcessorTest.kt` feeds
known synthetic PCM into the DSP engine directly (no device/emulator needed —
these are plain JVM unit tests) and asserts, among other things:

- silence produces no meaningful output energy
- a constant mono signal produces valid, non-silent stereo output
- output stays finite and within 16-bit range under extreme parameters
  (max intensity, max width, bass boost, reverb, fastest speed, random
  pattern) across many consecutive buffers
- the panning envelope changes smoothly frame-to-frame — no abrupt jumps
- a live parameter snap (e.g. intensity 0 → 1 mid-stream) does not produce
  a near-full-scale sample-to-sample discontinuity
- 8D off, with bass boost and reverb also off, is an **exact** sample-for-
  sample passthrough after the smoothing ramp settles
- the processor survives repeated enable/disable cycles, and a `flush()`,
  without corrupting state
- correct frame counts across sample rates (22.05/44.1/48/96 kHz) and across
  varied and deliberately irregular buffer sizes (1, 7, 64, 100, 512, 4096,
  8191 frames), including buffer-boundary continuity
- an unsupported channel layout (e.g. 5.1) is passed through inactive rather
  than crashing

`DspParametersTest` and `FormatDurationTest` cover the parameter model and a
small formatting helper. Run everything with:

```
./gradlew test
```

Lint runs via `./gradlew lint`. GitHub Actions runs both before ever
attempting `assembleRelease`, and fails the build if either fails.

### How this test suite has actually been verified so far

The development environment used to build this project has no Android SDK
and no network access to Google's Maven repository, so `./gradlew test` has
not been executed against the real `androidx.media3` artifacts here. What
*has* been done, concretely: a from-scratch Kotlin recreation of the exact
`androidx.media3.common.audio.AudioProcessor` / `BaseAudioProcessor` contract
(matching the real method signatures and semantics as precisely as possible
from documentation and the Media3 source) was compiled with the Kotlin
compiler directly, `Circular8DAudioProcessor.kt` and this whole test file
were compiled against it with zero errors, and all 14 tests were then
actually executed (not just compiled) and passed. The same technique was
used to compile `PlayerRepository.kt`, `EightDRenderersFactory.kt`,
`PlaybackService.kt`, `MusicRepository.kt`, `SettingsRepository.kt`,
`FavoritesRepository.kt`, `RecentlyPlayedRepository.kt`, `PlayerViewModel.kt`,
and `LibraryViewModel.kt` against faithful stubs of the Media3
session/exoplayer, AndroidX DataStore/lifecycle, Guava, and kotlinx-coroutines/
serialization surfaces they use — this caught two real bugs (missing
`kotlinx.serialization.decodeFromString` imports in the two DataStore
repositories) that a plain read-through had missed. This is meaningfully
more evidence than "the code looks right," but it is still not the same as
a real Gradle/Android Gradle Plugin build against the actual published
artifacts, which can surface things a hand-written stub can't (an API that
moved between the exact versions pinned here, a resource/manifest merge
issue, an annotation-processing failure). The Compose UI layer (all
`ui/screens/*.kt`, `ui/navigation/NavGraph.kt`, `ui/theme/*.kt`) was
reviewed by hand, not compiled — stubbing Compose's runtime faithfully
enough to be worth the effort wasn't practical in this environment. The
real, authoritative build is the one GitHub Actions runs on push.

## Known limitations / honest notes

- The spatial DSP path is implemented for 16-bit PCM mono and stereo, which
  covers effectively all local music playback on Android; multichannel
  sources pass through unprocessed rather than being silently faked (see
  above).
- No release signing keystore is included (by design — see Build, above).
- Instrumented (on-device) UI tests are not included; the test suite here is
  JVM unit tests for the DSP engine, parameter model, and small pure helpers,
  which is what actually needs verifying for the DSP's correctness claims.
- This repository was authored and assembled outside of Android Studio and
  without Android SDK access in the authoring environment. See "How this
  test suite has actually been verified so far," above, for exactly what was
  and wasn't executed, and by what method. The release build is verified for
  real by GitHub Actions on push — that is the first point at which the
  complete project (Compose UI included) is compiled by the actual Android
  Gradle Plugin against the actual published dependencies.

## Troubleshooting

- **"No music found"** — the app only lists files MediaStore classifies as
  music (`IS_MUSIC != 0`) with a non-zero duration. Confirm the files show up
  in another music app first.
- **Build fails on a fresh clone** — check the Actions log for the specific
  Gradle task; the workflow validates the wrapper and prints `--stacktrace`
  output for `test`, `lint`, and `assembleRelease`.
- **8D effect isn't audible** — confirm 8D Mode is toggled on in 8D Controls
  and Intensity isn't at 0%; also confirm the source is genuinely stereo (a
  mono recording has nothing to pan between channels).
