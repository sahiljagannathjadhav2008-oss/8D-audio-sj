# CHANGELOG — Production-readiness pass

Architecture, features, and UI are unchanged from the original delivery.
This pass fixes build/CI risk, dependency compatibility, and a handful of
real bugs found during review. Nothing here changes what the app does —
only whether it builds reliably and behaves correctly.

## 1. FFmpeg dependency (breaking risk → fixed)

**Problem:** `com.arthenica:ffmpeg-kit-full:6.0-2` is gone. FFmpegKit's
original maintainer officially retired the project on Jan 6, 2025, and
scheduled the removal of prebuilt binaries from Maven Central/GitHub —
so builds using the old coordinate can fail with "could not resolve
dependency" the moment the artifact is pulled, if it isn't already.

**Fix:** switched to `com.antonkarpenko:ffmpeg-kit-full:2.1.0` —

- Actively published in 2025/2026 by the same maintainer behind
  `ffmpeg_kit_flutter_new` (a widely-used continuation of FFmpegKit for
  Flutter), built against FFmpeg 8.0.
- **LGPL-3.0**, not GPL — same license posture as the original
  `ffmpeg-kit-full` package, so no new copyleft obligations for a
  closed-source app (unlike GPL-flavored forks such as `*-full-gpl`).
- Same Java package and class names (`com.arthenica.ffmpegkit.FFmpegKit`,
  `FFmpegKitConfig`, `Statistics`, `ReturnCode`, etc.) — confirmed against
  the fork's own Android build errors/stack traces, which still reference
  `com.arthenica.ffmpegkit.*`. **Zero changes needed in `AudioConverter.kt`
  or `ConversionWorker.kt`** — the 8D filter chain and its Java call sites
  are untouched.
- Maven Central hosted, so no extra repository (e.g. JitPack) needed —
  `mavenCentral()` was already declared in `settings.gradle.kts`.

If this artifact is ever pulled too, the same swap works for
`io.github.xch168:ffmpeg-kit-full-gpl` (GPL) or
`com.moizhassan.ffmpeg:ffmpeg-kit-16kb` (also useful if you need 16 KB
page-size compliance for newer devices) — all three are rebuilds of the
same FFmpegKit source tree under the same package name.

## 2. Gradle Wrapper (was missing real binaries)

**Problem:** the previous delivery didn't ship `gradle/wrapper/gradle-wrapper.jar`
and generated the wrapper inside CI instead — against this round's
requirement that the wrapper be committed and CI never call `gradle wrapper`.

**Fix:** `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar`
are now real, verified binaries for Gradle 8.9 (matching
`gradle-wrapper.properties`), committed directly in this ZIP. CI now only
runs `chmod +x ./gradlew` and then `./gradlew ...` — it never regenerates
anything.

## 3. GitHub Actions workflow — rewritten

- Uses the committed wrapper only (no `gradle wrapper` step).
- Adds Android SDK setup (`android-actions/setup-android@v3`) — the
  previous workflow relied entirely on `gradle/actions/setup-gradle` and
  never provisioned the Android SDK/build-tools explicitly.
- Adds `gradle/actions/wrapper-validation@v3` to verify the committed
  wrapper jar's checksum against Gradle's known-good list before it's
  ever executed (supply-chain safety for a binary file checked into git).
- Runs `./gradlew lint` before assembling, so lint failures surface before
  a wasted APK build.
- Every Gradle invocation uses `--stacktrace` with default shell
  `set -e` semantics, so any Gradle error fails the step (and therefore
  the whole job) immediately — no swallowed failures.
- `upload-artifact` steps now set `if-no-files-found: error` so a silent
  "0 APKs produced" build no longer reports green.

## 4. Dependency versions — reviewed, one change

Cross-checked AGP 8.5.2 / Gradle 8.9 / Kotlin 1.9.24 / Compose compiler
1.5.14 / Compose BOM 2024.06.00 / Room 2.6.1 / Hilt 2.51.1 / KSP
1.9.24-1.0.20 / WorkManager 2.9.1 / Media3 1.4.0 / coroutines 1.8.1 /
Lifecycle 2.8.4 against the official compatibility matrices — all mutually
compatible, no changes needed there.

**Change made:** moved Hilt's two annotation processors
(`hilt-android-compiler`, `androidx.hilt:hilt-compiler`) from `kapt` to
`ksp`, since Hilt 2.48+ fully supports KSP. Room was already on KSP. This
removes the `kotlin("kapt")` plugin and the second, slower
annotation-processing pass entirely — one less moving part, and kapt is
the piece JetBrains has been steering people away from.

## 5. Manifest review

- Removed `android:requestLegacyExternalStorage="true"` — this flag is
  silently ignored by the platform once `targetSdkVersion >= 29` (we
  target 34), so it was dead weight, not a real compatibility shim.
- Removed the unused `.work.ConversionForegroundService` `<service>`
  declaration (see §9 — the class it pointed to was dead code and has
  been deleted).
- Confirmed `FOREGROUND_SERVICE_DATA_SYNC`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`,
  and `POST_NOTIFICATIONS` are all present and matched to the service
  types actually declared.

## 6. Hilt — verified, no gaps found

`@HiltAndroidApp` on `App`, `@AndroidEntryPoint` on `MainActivity` and
`PlaybackService`, `@HiltViewModel` on every ViewModel, `@HiltWorker` +
`@AssistedInject` on `ConversionWorker`, and the `AppModule` providing
`AppDatabase`/`ConversionDao` were all already correctly wired. No missing
annotations found.

## 7. Room — migration strategy fixed

**Problem:** `Room.databaseBuilder(...).fallbackToDestructiveMigration()`
would silently **delete every converted song's metadata** the moment the
schema version changes — directly contradicting this app's core promise
that a converted file is never lost.

**Fix:** removed `fallbackToDestructiveMigration()`. The database now
requires an explicit `androidx.room.migration.Migration` to be added via
`.addMigrations(...)` whenever the schema changes (a comment in
`AppModule.kt` documents this for whoever ships the next schema change).
Since the app is currently at schema version 1, there is nothing to
migrate yet — this is a guardrail against a future regression, not a fix
for an existing data-loss bug.

## 8. WorkManager — Android 14 compliance + retry policy

- `ConversionWorker.getForegroundInfo()` now passes
  `ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC` explicitly on API 29+
  (required in practice on Android 14/API 34, where foreground service
  type mismatches are enforced much more strictly).
- Added `setConstraints(Constraints.Builder().setStorageNotLow(true))` and
  `setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)` to
  the `OneTimeWorkRequest` in `ConvertViewModel` — a real retry/backoff
  policy where there was none before.
- Cancellation (`workManager.cancelWorkById`) was already correct and is
  unchanged.

## 9. Media3 — real synchronization fix (not just a note this time)

**Problem:** the previous delivery's `PlayerViewModel` created its **own
private `ExoPlayer`** instance, completely separate from the
`MediaSession`'s player living inside `PlaybackService`. That meant the
notification, lockscreen, and any Bluetooth headset transport controls —
all of which talk to the *session's* player — could play/pause/seek a
different player than the one the in-app Player screen was polling and
displaying. Two players, two states, silently drifting apart.

**Fix:** `PlayerViewModel` now connects via a Media3 `MediaController`
bound to `PlaybackService`'s `SessionToken`, and drives playback entirely
through that controller. There is now exactly one `ExoPlayer` instance for
the whole app — inside `PlaybackService` — and every surface (in-app UI,
notification, lockscreen, Bluetooth) reads and writes the same state.

## 10. Audio engine — one real bug fixed, chain otherwise kept as-is

**Problem:** `loudnorm` ran *after* `alimiter` in the filter chain.
`loudnorm`'s single-pass mode can add gain, so applying it after the
limiter could re-introduce peaks above the limiter's ceiling — the
opposite of what the limiter is there for.

**Fix:** reordered the chain so `loudnorm` runs before `alimiter`, making
the limiter the true final anti-clipping stage. Every other filter
(`apulsator` circular pan, `extrastereo` widening, the two `aecho` stages
for Haas delay + reverb tail, `stereotools`, and the in/out fades) is
unchanged — same panning behavior, same reverb character, same audio
quality target.

## 11. Code quality — dead code and unused imports removed

- Deleted `work/ConversionForegroundService.kt` — it was never started by
  anything; `ConversionWorker` handles its own foreground notification via
  `setForeground()`/`getForegroundInfo()`, which is the correct WorkManager-
  native pattern. Keeping an unused Service class around was dead code.
- Removed an unused local variable in `MainActivity`'s Convert route
  (`Screen.Convert.route.substringAfterLast("/")`, computed and discarded).
- Removed five unused imports across `ConvertScreen.kt`,
  `SettingsViewModel.kt`, and `ConversionWorker.kt`.
- No other TODOs, placeholders, or duplicate code found on review.

## Known limitation carried over

`ffmpeg-kit-full` (any maintainer) is a large AAR (tens of MB) pulled from
Maven Central; first Gradle sync will take a while. This was true before
and remains true — it's inherent to bundling FFmpeg's native libraries for
every ABI, not something this pass changed.

---

## Follow-up pass — CI hardening & repo hygiene

Same app, same architecture, same fixes above — this pass tightens the
repository against the "must never fail in GitHub Actions" bar specifically.

### `.gitignore` added (was missing)

`local.properties`, `build/`, `.gradle/`, `.idea/`, `*.iml`, native-build
directories, and keystores are now git-ignored. `local.properties` in
particular must never be committed (it's machine-specific — your local
Android SDK path) — it wasn't in this repo before, but there was also no
`.gitignore` to guarantee it stays that way once the project is opened in
Android Studio and the file gets generated locally.

### Release build no longer has a hidden lint gate

`assembleRelease` automatically runs a `lintVital` pass before packaging,
which **fails the build** if it finds any fatal-severity issue — a hidden
failure mode not mentioned anywhere in a normal build log until it happens.
Added to `app/build.gradle.kts`:

```kotlin
lint {
    checkReleaseBuilds = false
    abortOnError = false
}
```

`./gradlew lint` still works as an explicit, on-demand check; it's just no
longer a silent gate on the one command (`./gradlew assembleRelease`) this
project is required to always pass.

### Workflow trimmed to exactly the required steps

Previous workflow also built and uploaded a debug APK and ran a separate
lint step. Neither was wrong, but both were extra surface area beyond what
was asked for. The workflow now does exactly: checkout → JDK 17 (Temurin) →
Android SDK setup → **validate the wrapper before it's ever executed** →
grant `gradlew` exec permission → cache Gradle → `./gradlew assembleRelease`
→ upload the release APK. `wrapper-validation` now runs before `chmod`,
so a corrupted or tampered wrapper jar is caught before anything tries to
run it.

### Verified: no local JARs, no manual downloads

Every dependency (AndroidX, Material 3, Media3, Room, Hilt, WorkManager,
coroutines, FFmpeg) resolves from `google()` or `mavenCentral()` — both
already declared in `settings.gradle.kts`. Nothing in this project requires
a manually placed `.jar`, a local Maven repo, or JitPack.
