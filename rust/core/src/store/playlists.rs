use crate::error::{Error, Result};
use crate::model::{id_for, now_millis, Playlist};
use crate::store::{load_json, save_json};
use std::path::{Path, PathBuf};

#[derive(Clone, Debug, serde::Serialize, serde::Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PlaylistStore {
    pub playlists: Vec<Playlist>,
    #[serde(skip)]
    root: PathBuf,
}

impl PlaylistStore {
    pub fn open(root: &Path) -> Self {
        let mut store: Self = load_json(
            &root.join("playlists.json"),
            Self {
                playlists: Vec::new(),
                root: root.to_path_buf(),
            },
        );
        store.root = root.to_path_buf();
        store
    }

    pub fn save(&self) -> Result<()> {
        save_json(&self.root.join("playlists.json"), self)
    }

    pub fn all(&self) -> Vec<Playlist> {
        let mut playlists = self.playlists.clone();
        playlists.sort_by(|a, b| a.created_at.cmp(&b.created_at));
        playlists
    }

    pub fn get(&self, id: &str) -> Option<&Playlist> {
        self.playlists.iter().find(|p| p.id == id)
    }

    pub fn create(&mut self, name: &str) -> Result<Playlist> {
        let name = name.trim();
        if name.is_empty() {
            return Err(Error::Invalid("playlist name is empty".into()));
        }
        let playlist = Playlist {
            id: id_for(&format!("playlist:{name}:{}", now_millis())),
            name: name.to_string(),
            track_ids: Vec::new(),
            created_at: now_millis(),
        };
        self.playlists.push(playlist.clone());
        self.save()?;
        Ok(playlist)
    }

    pub fn rename(&mut self, id: &str, name: &str) -> Result<()> {
        let playlist = self
            .playlists
            .iter_mut()
            .find(|p| p.id == id)
            .ok_or_else(|| Error::NotFound(id.to_string()))?;
        playlist.name = name.trim().to_string();
        self.save()
    }

    pub fn delete(&mut self, id: &str) -> Result<()> {
        self.playlists.retain(|p| p.id != id);
        self.save()
    }

    pub fn add_tracks(&mut self, id: &str, track_ids: &[String]) -> Result<()> {
        let playlist = self
            .playlists
            .iter_mut()
            .find(|p| p.id == id)
            .ok_or_else(|| Error::NotFound(id.to_string()))?;
        for track_id in track_ids {
            if !playlist.track_ids.contains(track_id) {
                playlist.track_ids.push(track_id.clone());
            }
        }
        self.save()
    }

    pub fn remove_track(&mut self, id: &str, track_id: &str) -> Result<()> {
        let playlist = self
            .playlists
            .iter_mut()
            .find(|p| p.id == id)
            .ok_or_else(|| Error::NotFound(id.to_string()))?;
        playlist.track_ids.retain(|t| t != track_id);
        self.save()
    }

    pub fn remove_track_from_all(&mut self, track_id: &str) -> Result<()> {
        for playlist in &mut self.playlists {
            playlist.track_ids.retain(|id| id != track_id);
        }
        self.save()
    }

    pub fn reorder(&mut self, id: &str, from: usize, to: usize) -> Result<()> {
        let playlist = self
            .playlists
            .iter_mut()
            .find(|p| p.id == id)
            .ok_or_else(|| Error::NotFound(id.to_string()))?;
        if from >= playlist.track_ids.len() || to >= playlist.track_ids.len() {
            return Err(Error::Invalid("reorder index out of range".into()));
        }
        let item = playlist.track_ids.remove(from);
        playlist.track_ids.insert(to, item);
        self.save()
    }
}
