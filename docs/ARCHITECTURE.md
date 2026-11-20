# Architecture

## Native contract (JNI)

Class: `com.berkchy.himemp.core.NativeBridge`; exported symbols follow
`Java_com_berkchy_himemp_core_NativeBridge_<method>`.

| Group | Methods | Return |
| --- | --- | --- |
| Lifecycle | `init(filesDir)`, `shutdown()`, `setEventListener(listener)` | boolean / void |
| Library | `getAllowedFolders`, `setAllowedFolders`, `scan`, `getTracks`, `getTrack`, `search`, `getAlbums`, `getArtists`, `getFolders` | JSON |
| Playlists | `getPlaylists`, `createPlaylist`, `renamePlaylist`, `deletePlaylist`, `addToPlaylist`, `removeFromPlaylist`, `reorderPlaylist`, `getPlaylistTracks`, `setFavorite`, `getFavorites`, `getRecentlyPlayed` | JSON |
| Queue | `setQueue`, `queueAdd`, `queueMove`, `queueRemove`, `queueClear`, `getQueue` | JSON |
| Transport | `play`, `pause`, `stop`, `next`, `previous`, `seek`, `setRepeat`, `setShuffle`, `setSpeed`, `setSleepTimer` | void |
| State | `getState`, `getSettings`, `setSetting`, `clearCaches`, `libraryStats` | JSON |

JSON field names are camelCase (serde `rename_all`). Kotlin parses them into immutable data classes in `core/Models.kt`.

## Event flow

```
Rust engine thread --EventSink.emit(event,payload)--> JNI (attached thread)
        --> GlobalRef(EventListener).onEvent(event,payload)
        --> PlaybackController (Main coroutine scope)
        --> StateFlow<PlaybackState> --> Compose + PlaybackService notification
```

The engine emits a full state snapshot every 250 ms and immediately after commands/track changes. `PlaybackController` refreshes the current `Track` only when its id changes, avoiding repeated native round-trips.

## Audio path

1. `PcmDecoder` (symphonia) opens the selected file and decodes packets to interleaved `i16`.
2. The engine asks `AudioSinkFactory.create(rate, channels)` for a sink. On Android this calls the static Kotlin method `NativeSinkFactory.create`, which builds a streaming `AudioTrack`.
3. The engine writes PCM to the sink in ~16k-sample chunks and advances `positionMs` from decoded frames.
4. When the sample rate/channel layout is unchanged, the sink is kept open across tracks, giving gapless-style transitions; otherwise it is closed and recreated.
5. Seeking reopens the decoder and discards frames up to the target offset (deterministic, container-independent).

`AudioSink` is a trait, so host tests use `SilentSink` and Android uses `AndroidAudioTrackSink`.

## Threading and lifetimes

- All filesystem scanning, tag parsing and artwork extraction run off the UI thread (`Dispatchers.IO` in Kotlin, scanner/decoder threads in Rust).
- The Rust engine owns its state; Kotlin never mutates native state directly, it sends commands through an `mpsc::Sender`.
- `JNIEnv` is never stored. Each callback attaches the current thread via `JavaVM::attach_current_thread()`. JNI globals (`GlobalRef`) are owned by Rust and released in `Drop`.
- `PlaybackRuntime::shutdown()` sends `Shutdown` and joins the playback thread before the app exits; the listener global ref is cleared afterwards.
- Stores write JSON atomically (write to `.tmp`, then rename) so state survives crashes.

## Artwork

Embedded artwork is extracted with `MediaMetadataRetriever` in `ArtCache` on `Dispatchers.IO`, cached in an `LruCache` sized from the available heap, and reused by the mini player, full player and notification. Text metadata (title, artist, album, duration, year, track number) comes from the Rust metadata parser.

## Build targets

| ABI | Rust target |
| --- | --- |
| arm64-v8a | `aarch64-linux-android` |
| armeabi-v7a | `armv7-linux-androideabi` |
| x86_64 | `x86_64-linux-android` |

Release profile: `opt-level = "z"`, thin LTO, single codegen unit, panic abort, symbol stripping. Compose release builds enable R8 and resource shrinking; unused Material icons are stripped.
