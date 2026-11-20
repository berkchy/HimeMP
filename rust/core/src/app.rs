use crate::error::{Error, Result};
use crate::playback::engine::PlaybackRuntime;
use crate::playback::{AudioSinkFactory, EngineHandle, EventSink, NoopEventSink};
use crate::store::library::LibraryStore;
use crate::store::playlists::PlaylistStore;
use crate::store::settings::SettingsStore;
use crate::store::state::PlaybackState;
use serde::Serialize;
use std::path::PathBuf;
use std::sync::{Arc, Mutex, RwLock};

pub struct App {
    pub root: PathBuf,
    pub library: Arc<Mutex<LibraryStore>>,
    pub playlists: Arc<Mutex<PlaylistStore>>,
    pub settings: Arc<Mutex<SettingsStore>>,
    pub state: Arc<RwLock<PlaybackState>>,
    pub runtime: Mutex<PlaybackRuntime>,
    pub events: Arc<dyn EventSink>,
}

impl App {
    pub fn init(
        files_dir: &str,
        factory: Arc<dyn AudioSinkFactory>,
        events: Option<Arc<dyn EventSink>>,
    ) -> Result<Arc<Self>> {
        let root = PathBuf::from(files_dir);
        std::fs::create_dir_all(&root)?;
        let events = events.unwrap_or_else(|| Arc::new(NoopEventSink));
        let library = Arc::new(Mutex::new(LibraryStore::open(&root)));
        let playlists = Arc::new(Mutex::new(PlaylistStore::open(&root)));
        let settings = Arc::new(Mutex::new(SettingsStore::open(&root)));
        let state = Arc::new(RwLock::new(PlaybackState::open(&root)));
        let runtime = PlaybackRuntime::start(
            root.clone(),
            library.clone(),
            state.clone(),
            factory,
            events.clone(),
        );
        Ok(Arc::new(Self {
            root,
            library,
            playlists,
            settings,
            state,
            runtime: Mutex::new(runtime),
            events,
        }))
    }

    pub fn handle(&self) -> EngineHandle {
        self.runtime.lock().unwrap().handle.clone()
    }

    pub fn json<T: Serialize>(&self, value: &T) -> String {
        serde_json::to_string(value).unwrap_or_else(|_| "{}".to_string())
    }

    pub fn fail(&self, error: Error) -> String {
        self.events.emit("error", &error.to_string());
        self.json(&serde_json::json!({ "error": error.to_string() }))
    }
}
