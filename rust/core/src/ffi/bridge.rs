#![allow(unused_variables)]

use super::{AndroidSinkFactory, JniEventSink};
use crate::app::App;
use crate::error::{Error, Result};
use crate::playback::{Command, EventSink};
use jni::objects::{JClass, JObject, JObjectArray, JString};
use jni::sys::{jboolean, jfloat, jint, jlong, jstring, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;
use serde::Serialize;
use std::ptr::null_mut;
use std::sync::{Arc, Mutex, OnceLock};

static APP: OnceLock<Arc<App>> = OnceLock::new();
static JNI: OnceLock<JniRuntime> = OnceLock::new();

struct JniRuntime {
    events: Arc<JniEventSink>,
}

fn read_strings(env: &mut JNIEnv<'_>, array: JObjectArray<'_>) -> Result<Vec<String>> {
    let length = env
        .get_array_length(&array)
        .map_err(|e| Error::Jni(e.to_string()))?;
    (0..length)
        .map(|index| {
            let element = env
                .get_object_array_element(&array, index)
                .map_err(|e| Error::Jni(e.to_string()))?;
            let string = unsafe { JString::from_raw(element.into_raw()) };
            let value = env
                .get_string(&string)
                .map_err(|e| Error::Jni(e.to_string()))?;
            Ok(value.into())
        })
        .collect()
}

fn read_string(env: &mut JNIEnv<'_>, value: &JString<'_>) -> Result<String> {
    Ok(env
        .get_string(value)
        .map_err(|e| Error::Jni(e.to_string()))?
        .into())
}

fn panic_payload(payload: Box<dyn std::any::Any + Send>) -> String {
    if let Some(message) = payload.downcast_ref::<String>() {
        message.clone()
    } else if let Some(message) = payload.downcast_ref::<&str>() {
        (*message).to_string()
    } else {
        "unknown native panic".to_string()
    }
}

fn fatal(message: String) {
    if let Some(runtime) = JNI.get() {
        runtime.events.emit("fatal", &message);
    }
    log::error!("fatal native error: {message}");
}

fn error_json(error: &Error) -> String {
    format!(
        "{{\"error\":{}}}",
        serde_json::to_string(&error.to_string()).unwrap_or_else(|_| "\"error\"".into())
    )
}

fn reply(
    env: &mut JNIEnv<'_>,
    action: impl FnOnce(&mut JNIEnv<'_>, &Arc<App>) -> Result<String>,
) -> jstring {
    let result = match std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        APP.get()
            .ok_or_else(|| Error::Invalid("native core not initialized".into()))
            .and_then(|app| action(env, app))
    })) {
        Ok(result) => result,
        Err(payload) => {
            let message = panic_payload(payload);
            fatal(message.clone());
            Err(Error::Store(message))
        }
    };
    match result {
        Ok(value) => env
            .new_string(value)
            .map(|value| value.into_raw())
            .unwrap_or(null_mut()),
        Err(error) => {
            if let Some(runtime) = JNI.get() {
                runtime.events.emit("error", &error.to_string());
            }
            env.new_string(error_json(&error))
                .map(|value| value.into_raw())
                .unwrap_or(null_mut())
        }
    }
}

fn json<T: Serialize>(value: &T) -> Result<String> {
    serde_json::to_string(value).map_err(|e| Error::Store(e.to_string()))
}

fn fire(command: impl FnOnce() -> Command) {
    if let Some(app) = APP.get() {
        if let Err(payload) = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            app.handle().send(command())
        })) {
            fatal(panic_payload(payload));
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_hime_mp_core_NativeBridge_init(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    files_dir: JString<'_>,
) -> jboolean {
    let result = (|| -> Result<()> {
        let directory = read_string(&mut env, &files_dir)?;
        let vm = Arc::new(env.get_java_vm().map_err(|e| Error::Jni(e.to_string()))?);
        let listener = Arc::new(Mutex::new(None));
        let events = Arc::new(JniEventSink::new(vm.clone(), listener));
        let class = env
            .find_class("hime/mp/core/NativeSinkFactory")
            .map_err(|e| Error::Jni(e.to_string()))?;
        let sink_class = env
            .new_global_ref(class)
            .map_err(|e| Error::Jni(e.to_string()))?;
        let factory = Arc::new(AndroidSinkFactory::new(vm.clone(), sink_class));
        let app = App::init(&directory, factory, Some(events.clone()))?;
        APP.set(app)
            .map_err(|_| Error::Invalid("native core already initialized".into()))?;
        JNI.set(JniRuntime { events })
            .map_err(|_| Error::Invalid("JNI runtime already initialized".into()))?;
        Ok(())
    })();
    match result {
        Ok(()) => JNI_TRUE,
        Err(error) => {
            log::error!("native init failed: {error}");
            JNI_FALSE
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_hime_mp_core_NativeBridge_shutdown(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
) {
    if let Some(app) = APP.get() {
        app.runtime
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner())
            .shutdown();
    }
    if let Some(runtime) = JNI.get() {
        runtime.events.clear_listener();
    }
}

#[no_mangle]
pub extern "system" fn Java_hime_mp_core_NativeBridge_setEventListener(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    listener: JObject<'_>,
) -> jboolean {
    match JNI.get() {
        Some(runtime) => match env.new_global_ref(listener) {
            Ok(reference) => {
                runtime.events.set_listener(reference);
                JNI_TRUE
            }
            Err(_) => JNI_FALSE,
        },
        None => JNI_FALSE,
    }
}

#[no_mangle]
pub extern "system" fn Java_hime_mp_core_NativeBridge_getAllowedFolders(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    reply(&mut env, |env, app| {
        json(
            &app.library
                .lock()
                .unwrap_or_else(|poisoned| poisoned.into_inner())
                .allowed_folders,
        )
    })
}

#[no_mangle]
pub extern "system" fn Java_hime_mp_core_NativeBridge_setAllowedFolders(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    folders: JObjectArray<'_>,
) -> jstring {
    reply(&mut env, |env, app| {
        let folders = read_strings(env, folders)?;
        app.library
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner())
            .set_allowed_folders(folders)?;
        json(&serde_json::json!({ "ok": true }))
    })
}

#[no_mangle]
pub extern "system" fn Java_hime_mp_core_NativeBridge_scan(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    incremental: jboolean,
) -> jstring {
    reply(&mut env, |env, app| {
        let report = app
            .library
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner())
            .scan(incremental != 0)?;
        json(&report)
    })
}

#[no_mangle]
pub extern "system" fn Java_hime_mp_core_NativeBridge_getTracks(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    reply(&mut env, |env, app| {
        json(
            &app.library
                .lock()
                .unwrap_or_else(|poisoned| poisoned.into_inner())
                .tracks(),
        )
    })
}

#[no_mangle]
pub extern "system" fn Java_hime_mp_core_NativeBridge_getTrack(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    id: JString<'_>,
) -> jstring {
    reply(&mut env, |env, app| {
        let id = read_string(env, &id)?;
        let track = app
            .library
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner())
            .find(&id)
            .cloned()
            .ok_or_else(|| Error::NotFound(id))?;
        json(&track)
    })
}

#[no_mangle]
pub extern "system" fn Java_hime_mp_core_NativeBridge_search(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    query: JString<'_>,
) -> jstring {
    reply(&mut env, |env, app| {
        let query = read_string(env, &query)?;
        json(
            &app.library
                .lock()
                .unwrap_or_else(|poisoned| poisoned.into_inner())
                .search(&query),
        )
    })
}

#[no_mangle]
pub extern "system" fn Java_hime_mp_core_NativeBridge_getAlbums(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    reply(&mut env, |env, app| {
        json(
            &app.library
                .lock()
                .unwrap_or_else(|poisoned| poisoned.into_inner())
                .albums(),
        )
    })
}

#[no_mangle]
pub extern "system" fn Java_hime_mp_core_NativeBridge_getArtists(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    reply(&mut env, |env, app| {
        json(
            &app.library
                .lock()
                .unwrap_or_else(|poisoned| poisoned.into_inner())
                .artists(),
        )
    })
}

#[no_mangle]
pub extern "system" fn Java_hime_mp_core_NativeBridge_getFolders(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    reply(&mut env, |env, app| {
        json(
            &app.library
                .lock()
                .unwrap_or_else(|poisoned| poisoned.into_inner())
                .folders(),
        )
    })
}

#[no_mangle]
pub extern "system" fn Java_hime_mp_core_NativeBridge_getPlaylists(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    reply(&mut env, |env, app| {
        json(
            &app.playlists
                .lock()
                .unwrap_or_else(|poisoned| poisoned.into_inner())
                .all(),
        )
    })
}

#[no_mangle]
pub extern "system" fn Java_hime_mp_core_NativeBridge_createPlaylist(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    name: JString<'_>,
) -> jstring {
    reply(&mut env, |env, app| {
        let name = read_string(env, &name)?;
        json(
            &app.playlists
                .lock()
                .unwrap_or_else(|poisoned| poisoned.into_inner())
                .create(&name)?,
        )
    })
}

#[no_mangle]
pub extern "system" fn Java_hime_mp_core_NativeBridge_renamePlaylist(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    id: JString<'_>,
    name: JString<'_>,
) -> jstring {
    reply(&mut env, |env, app| {
        let id = read_string(env, &id)?;
        let name = read_string(env, &name)?;
        app.playlists
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner())
            .rename(&id, &name)?;
        json(&serde_json::json!({ "ok": true }))
    })
}

#[no_mangle]
pub extern "system" fn Java_hime_mp_core_NativeBridge_deletePlaylist(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    id: JString<'_>,
) -> jstring {
    reply(&mut env, |env, app| {
        let id = read_string(env, &id)?;
        app.playlists
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner())
            .delete(&id)?;
        json(&serde_json::json!({ "ok": true }))
    })
}

#[no_mangle]
pub extern "system" fn Java_hime_mp_core_NativeBridge_addToPlaylist(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    id: JString<'_>,
    track_ids: JObjectArray<'_>,
) -> jstring {
    reply(&mut env, |env, app| {
        let id = read_string(env, &id)?;
        let track_ids = read_strings(env, track_ids)?;
        app.playlists
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner())
            .add_tracks(&id, &track_ids)?;
        json(&serde_json::json!({ "ok": true }))
    })
}

#[no_mangle]
pub extern "system" fn Java_hime_mp_core_NativeBridge_removeFromPlaylist(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    id: JString<'_>,
    track_id: JString<'_>,
) -> jstring {
    reply(&mut env, |env, app| {
        let id = read_string(env, &id)?;
        let track_id = read_string(env, &track_id)?;
        app.playlists
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner())
            .remove_track(&id, &track_id)?;
        json(&serde_json::json!({ "ok": true }))
    })
}

#[no_mangle]
pub extern "system" fn Java_hime_mp_core_NativeBridge_reorderPlaylist(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    id: JString<'_>,
    from: jint,
    to: jint,
) -> jstring {
    reply(&mut env, |env, app| {
        let id = read_string(env, &id)?;
        app.playlists
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner())
            .reorder(&id, from.max(0) as usize, to.max(0) as usize)?;
        json(&serde_json::json!({ "ok": true }))
    })
}

#[no_mangle]
pub extern "system" fn Java_hime_mp_core_NativeBridge_getPlaylistTracks(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    id: JString<'_>,
) -> jstring {
    reply(&mut env, |env, app| {
        let id = read_string(env, &id)?;
        let ids = app
            .playlists
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner())
            .get(&id)
            .map(|playlist| playlist.track_ids.clone())
            .ok_or_else(|| Error::NotFound(id))?;
        let library = app
            .library
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner());
        let tracks: Vec<_> = ids
            .iter()
            .filter_map(|track_id| library.find(track_id).cloned())
            .collect();
        json(&tracks)
    })
}

#[no_mangle]
pub extern "system" fn Java_hime_mp_core_NativeBridge_setFavorite(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    id: JString<'_>,
    favorite: jboolean,
) -> jstring {
    reply(&mut env, |env, app| {
        let id = read_string(env, &id)?;
        app.library
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner())
            .set_favorite(&id, favorite != 0)?;
        json(&serde_json::json!({ "ok": true }))
    })
}

#[no_mangle]
pub extern "system" fn Java_hime_mp_core_NativeBridge_removeFromLibrary(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    ids: JObjectArray<'_>,
) -> jstring {
    reply(&mut env, |env, app| {
        let ids = read_strings(env, ids)?;
        for id in &ids {
            app.playlists
                .lock()
                .unwrap_or_else(|poisoned| poisoned.into_inner())
                .remove_track_from_all(id)?;
        }
        let removed = app
            .library
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner())
            .remove_tracks(&ids)?;
        json(&serde_json::json!({ "removed": removed }))
    })
}

#[no_mangle]
pub extern "system" fn Java_hime_mp_core_NativeBridge_getFavorites(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    reply(&mut env, |env, app| {
        json(
            &app.library
                .lock()
                .unwrap_or_else(|poisoned| poisoned.into_inner())
                .favorites(),
        )
    })
}

#[no_mangle]
pub extern "system" fn Java_hime_mp_core_NativeBridge_getRecentlyPlayed(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    reply(&mut env, |env, app| {
        json(
            &app.library
                .lock()
                .unwrap_or_else(|poisoned| poisoned.into_inner())
                .recently_played(),
        )
    })
}

#[no_mangle]
pub extern "system" fn Java_hime_mp_core_NativeBridge_setQueue(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    ids: JObjectArray<'_>,
    start_index: jint,
) -> jstring {
    reply(&mut env, |env, app| {
        let ids = read_strings(env, ids)?;
        app.handle().send(Command::SetQueue(ids, start_index));
        json(&serde_json::json!({ "ok": true }))
    })
}

#[no_mangle]
pub extern "system" fn Java_hime_mp_core_NativeBridge_queueAdd(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    ids: JObjectArray<'_>,
    at: jint,
) -> jstring {
    reply(&mut env, |env, app| {
        let ids = read_strings(env, ids)?;
        app.handle().send(Command::QueueAdd(ids, at));
        json(&serde_json::json!({ "ok": true }))
    })
}

#[no_mangle]
pub extern "system" fn Java_hime_mp_core_NativeBridge_queueMove(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    from: jint,
    to: jint,
) -> jstring {
    reply(&mut env, |env, app| {
        app.handle()
            .send(Command::QueueMove(from.max(0) as usize, to.max(0) as usize));
        json(&serde_json::json!({ "ok": true }))
    })
}

#[no_mangle]
pub extern "system" fn Java_hime_mp_core_NativeBridge_queueRemove(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    index: jint,
) -> jstring {
    reply(&mut env, |env, app| {
        app.handle()
            .send(Command::QueueRemove(index.max(0) as usize));
        json(&serde_json::json!({ "ok": true }))
    })
}

#[no_mangle]
pub extern "system" fn Java_hime_mp_core_NativeBridge_queueClear(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    reply(&mut env, |env, app| {
        app.handle().send(Command::QueueClear);
        json(&serde_json::json!({ "ok": true }))
    })
}

#[no_mangle]
pub extern "system" fn Java_hime_mp_core_NativeBridge_getQueue(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    reply(&mut env, |env, app| {
        json(
            &app.state
                .read()
                .unwrap_or_else(|poisoned| poisoned.into_inner())
                .queue,
        )
    })
}

#[no_mangle]
pub extern "system" fn Java_hime_mp_core_NativeBridge_play(_env: JNIEnv<'_>, _class: JClass<'_>) {
    fire(|| Command::Play);
}

#[no_mangle]
pub extern "system" fn Java_hime_mp_core_NativeBridge_pause(_env: JNIEnv<'_>, _class: JClass<'_>) {
    fire(|| Command::Pause);
}

#[no_mangle]
pub extern "system" fn Java_hime_mp_core_NativeBridge_stop(_env: JNIEnv<'_>, _class: JClass<'_>) {
    fire(|| Command::Stop);
}

#[no_mangle]
pub extern "system" fn Java_hime_mp_core_NativeBridge_next(_env: JNIEnv<'_>, _class: JClass<'_>) {
    fire(|| Command::Next);
}

#[no_mangle]
pub extern "system" fn Java_hime_mp_core_NativeBridge_previous(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
) {
    fire(|| Command::Previous);
}

#[no_mangle]
pub extern "system" fn Java_hime_mp_core_NativeBridge_seek(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
    position_ms: jlong,
) {
    fire(|| Command::Seek(position_ms.max(0) as u64));
}

#[no_mangle]
pub extern "system" fn Java_hime_mp_core_NativeBridge_setRepeat(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
    mode: jint,
) {
    fire(|| Command::SetRepeat(mode));
}

#[no_mangle]
pub extern "system" fn Java_hime_mp_core_NativeBridge_setShuffle(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
    enabled: jboolean,
) {
    fire(|| Command::SetShuffle(enabled != 0));
}

#[no_mangle]
pub extern "system" fn Java_hime_mp_core_NativeBridge_setSpeed(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
    speed: jfloat,
) {
    fire(|| Command::SetSpeed(speed));
}

#[no_mangle]
pub extern "system" fn Java_hime_mp_core_NativeBridge_setSleepTimer(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
    duration_ms: jlong,
) {
    fire(|| Command::SetSleepTimer(duration_ms));
}

#[no_mangle]
pub extern "system" fn Java_hime_mp_core_NativeBridge_getState(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    reply(&mut env, |env, app| {
        json(
            &*app
                .state
                .read()
                .unwrap_or_else(|poisoned| poisoned.into_inner()),
        )
    })
}

#[no_mangle]
pub extern "system" fn Java_hime_mp_core_NativeBridge_setSetting(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    key: JString<'_>,
    value: JString<'_>,
) -> jstring {
    reply(&mut env, |env, app| {
        let key = read_string(env, &key)?;
        let value = read_string(env, &value)?;
        app.settings
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner())
            .set(&key, &value)?;
        json(&serde_json::json!({ "ok": true }))
    })
}

#[no_mangle]
pub extern "system" fn Java_hime_mp_core_NativeBridge_getSettings(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    reply(&mut env, |env, app| {
        json(
            &app.settings
                .lock()
                .unwrap_or_else(|poisoned| poisoned.into_inner())
                .values,
        )
    })
}

#[no_mangle]
pub extern "system" fn Java_hime_mp_core_NativeBridge_clearCaches(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    reply(&mut env, |env, app| {
        let cache = app.root.join("cache");
        let mut removed = 0usize;
        if let Ok(entries) = std::fs::read_dir(&cache) {
            for entry in entries.flatten() {
                if entry.path().is_file() && std::fs::remove_file(entry.path()).is_ok() {
                    removed += 1;
                }
            }
        }
        json(&serde_json::json!({ "removed": removed }))
    })
}

#[no_mangle]
pub extern "system" fn Java_hime_mp_core_NativeBridge_libraryStats(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    reply(&mut env, |env, app| {
        let playlists = app
            .playlists
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner())
            .playlists
            .len();
        json(
            &app.library
                .lock()
                .unwrap_or_else(|poisoned| poisoned.into_inner())
                .stats(playlists),
        )
    })
}
