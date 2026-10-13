# Trimr — fast, lossless on-device video trimmer

A single-purpose Android app: trim videos **fast**, **on-device**, with **zero
quality loss**. Every cut is a stream copy (`MediaExtractor` + `MediaMuxer`) —
no decoding, no re-encoding, no FFmpeg, no native binaries, no network. A 1 GB+
clip trims in about a second or two, which is the proof it isn't re-encoding.

- **Kotlin + Jetpack Compose + Material 3**, dark theme by default
- **minSdk 26**, **compileSdk / targetSdk 35**
- Fully offline (no `INTERNET` permission is declared)
- Scoped-storage compliant (saves via `MediaStore`)

## Build & install

You need JDK 17 (bundled with a recent Android Studio) and the Android SDK.
Android Studio will fetch the SDK platforms/build-tools automatically the first
time you open the project.

```bash
cd VideoTrimmer

# Build the debug APK
./gradlew assembleDebug

# The APK lands here:
#   app/build/outputs/apk/debug/app-debug.apk

# Install onto a connected device / emulator and launch:
./gradlew installDebug
adb shell monkey -p com.rottenpizza.videotrimmer.debug -c android.intent.category.LAUNCHER 1
```

Or just open the `VideoTrimmer/` folder in Android Studio and press **Run**.

> First build downloads the Android Gradle Plugin, AndroidX, Compose and Media3
> from Google's Maven repository, so it needs normal internet access.

## How it works

### Core trim (stream copy)
`trim/StreamCopyTrimmer.kt`:
1. Opens the source with `MediaExtractor` and enumerates audio + video tracks.
2. Adds each track to an MP4 `MediaMuxer`.
3. Seeks each track with `SEEK_TO_PREVIOUS_SYNC` to the start (cuts snap to the
   nearest keyframe — frame accuracy is intentionally not a goal) and copies
   samples (`readSampleData` → `writeSampleData`) until the end timestamp.
4. A single shared timestamp offset (the earliest first-sample time across
   tracks) keeps audio and video in sync and every written timestamp ≥ 0.
5. Rotation is read from the source and applied via
   `MediaMuxer.setOrientationHint()`.

### Saving
`data/GalleryStore.kt` writes through `MediaStore` into `Movies/Trimr`, setting
`DATE_TAKEN` to the source's capture time so the gallery shows the original
date. On Android 10+ this is fully scoped and permission-free; Android 9 and
below fall back to the legacy path (permission capped at API 28).

### Features
- **Pick** a video with the system photo picker.
- **Source info**: duration, resolution, size, format.
- **Range-trim UI**: two-handle range slider + a Media3/ExoPlayer preview that
  follows the active handle, with live start / end / duration readouts.
- **Multiple cuts**: queue several named ranges from one source; one **Trim**
  exports each as its own clip in a single batch with progress.
- **In-app rename**: every queued range and every saved clip renames with one
  tap into an editable field — never a file manager.
- **Library**: saved clips with thumbnails, playback, share, rename, delete, and
  sort by date / size / name / duration in either direction.
- **Optional (Settings)**: experimental patch of the MP4 `moov` `creation_time`
  atom so the embedded timestamp also matches the source. Off by default; the
  gallery date is already correct without it.

## Suggested test pass (on device)
1. Pick a large clip (1 GB+). Confirm info is correct, then Trim the default
   selection — it should save in ~1–2 s. Play it: correct orientation, audio in
   sync, correct capture date in the gallery.
2. Define 2–3 ranges, name each, hit Trim once — one correctly-named clip each.
3. In Library, flip through all four sort keys and both directions.
4. Rename and delete a clip; share one.

## Codec notes / quirks to watch
- **HEVC / H.265** and **HDR** streams copy fine (samples are opaque to a
  stream copy), but some players render HDR differently — playback quirks there
  are the player's, not the trim's.
- **Unusual audio** (multiple audio tracks, or non-AAC codecs some muxers
  dislike): every audio/video track is copied; a container/codec a `MediaMuxer`
  can't accept will surface as a per-clip error rather than a crash.
- **Keyframe snapping**: the start moves back to the previous keyframe, so a
  clip may begin slightly before the chosen point. This is by design (lossless,
  no re-encode).
