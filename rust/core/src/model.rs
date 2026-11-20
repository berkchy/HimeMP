use serde::{Deserialize, Serialize};
use std::collections::hash_map::DefaultHasher;
use std::hash::{Hash, Hasher};

pub fn id_for(path: &str) -> String {
    let mut hasher = DefaultHasher::new();
    path.hash(&mut hasher);
    format!("{:016x}", hasher.finish())
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Track {
    pub id: String,
    pub path: String,
    pub title: String,
    pub artist: String,
    pub album: String,
    pub album_artist: String,
    pub genre: String,
    pub year: u32,
    pub track_number: u32,
    pub duration_ms: u64,
    pub size: u64,
    pub mtime: u64,
    pub date_added: u64,
    pub favorite: bool,
    pub play_count: u32,
    pub last_played: u64,
}

impl Track {
    pub fn from_path(
        path: &str,
        meta: super::library::metadata::TrackMeta,
        size: u64,
        mtime: u64,
    ) -> Self {
        let name = std::path::Path::new(path)
            .file_stem()
            .and_then(|s| s.to_str())
            .unwrap_or("Unknown");
        let (fallback_artist, fallback_title) = match name.split_once(" - ") {
            Some((a, t)) => (a.trim().to_string(), t.trim().to_string()),
            None => ("Unknown Artist".to_string(), name.to_string()),
        };
        Self {
            id: id_for(path),
            path: path.to_string(),
            title: if meta.title.is_empty() {
                fallback_title
            } else {
                meta.title
            },
            artist: if meta.artist.is_empty() {
                fallback_artist.clone()
            } else {
                meta.artist
            },
            album: if meta.album.is_empty() {
                "Unknown Album".to_string()
            } else {
                meta.album
            },
            album_artist: if meta.album_artist.is_empty() {
                fallback_artist
            } else {
                meta.album_artist
            },
            genre: meta.genre,
            year: meta.year,
            track_number: meta.track_number,
            duration_ms: meta.duration_ms,
            size,
            mtime,
            date_added: now_millis(),
            favorite: false,
            play_count: 0,
            last_played: 0,
        }
    }
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Album {
    pub id: String,
    pub title: String,
    pub artist: String,
    pub track_count: usize,
    pub duration_ms: u64,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Artist {
    pub id: String,
    pub name: String,
    pub album_count: usize,
    pub track_count: usize,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct FolderInfo {
    pub path: String,
    pub name: String,
    pub track_count: usize,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Playlist {
    pub id: String,
    pub name: String,
    pub track_ids: Vec<String>,
    pub created_at: u64,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ScanReport {
    pub total: usize,
    pub added: usize,
    pub updated: usize,
    pub removed: usize,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LibraryStats {
    pub tracks: usize,
    pub albums: usize,
    pub artists: usize,
    pub playlists: usize,
    pub favorites: usize,
    pub total_duration_ms: u64,
    pub total_bytes: u64,
}

pub fn now_millis() -> u64 {
    use std::time::{SystemTime, UNIX_EPOCH};
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis() as u64)
        .unwrap_or(0)
}
