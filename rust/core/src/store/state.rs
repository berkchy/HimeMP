use crate::error::Result;
use crate::store::{load_json, save_json};
use std::path::Path;

#[derive(Clone, Debug, PartialEq, serde::Serialize, serde::Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PlaybackState {
    pub queue: Vec<String>,
    pub index: i32,
    pub track_id: Option<String>,
    pub position_ms: u64,
    pub duration_ms: u64,
    pub playing: bool,
    pub repeat: i32,
    pub shuffle: bool,
    pub speed: f32,
    pub sleep_remaining_ms: i64,
}

impl Default for PlaybackState {
    fn default() -> Self {
        Self {
            queue: Vec::new(),
            index: -1,
            track_id: None,
            position_ms: 0,
            duration_ms: 0,
            playing: false,
            repeat: 0,
            shuffle: false,
            speed: 1.0,
            sleep_remaining_ms: -1,
        }
    }
}

impl PlaybackState {
    pub fn open(root: &Path) -> Self {
        load_json(&root.join("state.json"), Self::default())
    }

    pub fn save(&self, root: &Path) -> Result<()> {
        save_json(&root.join("state.json"), self)
    }

    pub fn current_id(&self) -> Option<&str> {
        self.queue
            .get(self.index.max(0) as usize)
            .map(String::as_str)
            .or(self.track_id.as_deref())
    }
}
