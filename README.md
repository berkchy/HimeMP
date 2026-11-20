# HimeMP

A lightweight Android music player: a small **Rust** core handles library indexing, tag parsing, decoding, the playback state machine and persistence; a **Jetpack Compose / Material 3** UI provides the interface, custom media notification, mini player and full player.

## Highlights

- Folder-scoped library via Android SAF (add / remove folders, incremental or full rescan).
- Tracks / Albums / Artists / Folders views, search, playlists, Liked songs and Recently played.
- Rust decoding engine (MP3, FLAC, OGG, WAV, M4A/AAC) streaming PCM into Android `AudioTrack` over JNI.
- Gapless-style track transitions, repeat off/all/one, shuffle, queue reordering, sleep timer, playback speed.
- Material 3 light/dark/dynamic color, reduced-motion option, animated icons and mini↔full player transition.
- Foreground service with a colorized `MediaStyle` notification, media buttons, audio-focus and headset/Bluetooth handling.
- State (last track, position, queue, settings, playlists) persisted by the Rust core across restarts.

## Repository layout

```
rust/core/                  Rust crate (cdylib + rlib) — all heavy logic
  src/library/              folder scanner + metadata parser
  src/playback/             decoder, engine, audio sink abstraction
  src/store/                library/playlist/settings/state stores (atomic JSON writes)
  src/ffi/                  JNI exports, AudioTrack sink, event bridge
android/app/src/main/java/  Compose UI, ViewModels, service, repository
android/app/src/main/res/   Manifest resources, theme, launcher icons
.github/workflows/build.yml CI + APK artifacts + GitHub Releases
scripts/build-rust-android.sh  helper for local native builds
```

## How Rust and Android talk

Kotlin calls `NativeBridge` `external` methods. JNI exports in `rust/core/src/ffi/bridge.rs` map 1:1 to those methods. Data crossing the boundary is compact JSON, parsed with `org.json` on the Kotlin side. The Rust engine emits `state`, `trackChanged`, `error`, `sleepTimerFired` and `sleepTimer` events through a single `EventListener.onEvent(event, payload)` global reference; the callback is re-dispatched to the Main coroutine scope before touching Compose state.

Audio output is owned by Rust: the engine asks `NativeSinkFactory.create(sampleRate, channels)` for a streaming `AudioTrack`, then writes interleaved little-endian PCM from the Rust decode thread. The JavaVM is attached per call; the only long-lived JNI objects are two `GlobalRef`s (listener + byte buffer + AudioTrack), dropped when the track changes or the engine shuts down. The engine thread is joined on `shutdown()`, so no native threads or references leak.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the full contract and lifetime model.

## Local builds

Requirements: Rust (stable, `rustfmt`, `clippy`), JDK 17, Android SDK platform 35 + build-tools 35.0.0, NDK 28.2.13676358, Gradle wrapper 8.9 (included), `cargo-ndk` for cross builds.

```bash
# Rust core (host)
cargo fmt --manifest-path rust/core/Cargo.toml
cargo test --manifest-path rust/core/Cargo.toml
cargo clippy --manifest-path rust/core/Cargo.toml --all-targets

# Native libraries for Android (CI uses the same command)
cargo install cargo-ndk
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/28.2.13676358"
./scripts/build-rust-android.sh

# APK
cd android
./gradlew assembleDebug       # android/app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease     # unsigned unless HIMEMP_KEYSTORE is set
```

On Termux (aarch64 host) `cargo build --target aarch64-linux-android --release` can be copied to `android/app/src/main/jniLibs/arm64-v8a/` as a quick single-ABI build; the script handles this fallback automatically when `cargo-ndk` is unavailable.

Version overrides for release builds:

```bash
./gradlew assembleRelease -PversionCode=42 -PversionName=0.2.0
```

## Signing and releases

Set repository secrets `HIMEMP_KEYSTORE` (base64 or absolute path available to the runner), `HIMEMP_STORE_PASSWORD`, `HIMEMP_KEY_ALIAS` and `HIMEMP_KEY_PASSWORD` to sign release APKs. Without them the release APK is unsigned. The workflow:

- runs Rust fmt/clippy/tests plus cross-target checks on every push and PR;
- builds three ABI native libraries with `cargo-ndk`, then debug + release APKs;
- uploads APKs as the `himemp-apks` artifact;
- on `v*` tag pushes creates a GitHub Release with tagged APK assets;
- on `main` pushes / manual runs refreshes a rolling **nightly** prerelease with `nightly` tag.

`permissions: contents: write` is scoped to the publish job only.

## Permissions used

`READ_MEDIA_AUDIO` (API 33+) / `READ_EXTERNAL_STORAGE` (≤32), `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK`. Folder access uses `ACTION_OPEN_DOCUMENT_TREE` with persisted URI permission; the tree URI is converted to a filesystem path for the native scanner (works for primary/internal storage, which is the supported case).

## Status

The Rust core is compiled and unit-tested locally. Android compilation runs in GitHub Actions; the on-device build was not run in the authoring environment because Gradle dependency downloads timed out there.

## License

MIT — see [LICENSE](LICENSE).
