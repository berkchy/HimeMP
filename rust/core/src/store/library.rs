use crate::error::Result;
use crate::library::metadata;
use crate::model::{
    id_for, now_millis, Album, Artist, FolderInfo, LibraryStats, ScanReport, Track,
};
use crate::store::{load_json, save_json};
use std::collections::HashMap;
use std::path::{Path, PathBuf};

const AUDIO_EXTENSIONS: &[&str] = &[
    "mp3", "flac", "ogg", "oga", "opus", "wav", "m4a", "mp4", "aac", "aiff",
];

#[derive(Clone, Debug, serde::Serialize, serde::Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LibraryStore {
    pub tracks: Vec<Track>,
    pub allowed_folders: Vec<String>,
    pub last_scan: u64,
    #[serde(skip)]
    root: PathBuf,
}

impl LibraryStore {
    pub fn open(root: &Path) -> Self {
        let mut store: Self = load_json(
            &root.join("library.json"),
            Self {
                tracks: Vec::new(),
                allowed_folders: Vec::new(),
                last_scan: 0,
                root: root.to_path_buf(),
            },
        );
        store.root = root.to_path_buf();
        store
    }

    pub fn save(&self) -> Result<()> {
        save_json(&self.root.join("library.json"), self)
    }

    pub fn set_allowed_folders(&mut self, folders: Vec<String>) -> Result<()> {
        self.allowed_folders = folders;
        self.save()
    }

    pub fn add_folder(&mut self, folder: String) -> Result<()> {
        if !self.allowed_folders.contains(&folder) {
            self.allowed_folders.push(folder);
            self.save()?;
        }
        Ok(())
    }

    pub fn remove_folder(&mut self, folder: &str) -> Result<()> {
        self.allowed_folders.retain(|f| f != folder);
        self.save()
    }

    pub fn find(&self, id: &str) -> Option<&Track> {
        self.tracks.iter().find(|t| t.id == id)
    }

    pub fn find_mut(&mut self, id: &str) -> Option<&mut Track> {
        self.tracks.iter_mut().find(|t| t.id == id)
    }

    pub fn scan(&mut self, incremental: bool) -> Result<ScanReport> {
        let mut found: Vec<(String, u64, u64)> = Vec::new();
        for folder in &self.allowed_folders {
            collect_audio_files(Path::new(folder), &mut found, 0);
        }
        found.sort_by(|a, b| a.0.cmp(&b.0));

        let previous: HashMap<String, Track> = self
            .tracks
            .iter()
            .map(|t| (t.path.clone(), t.clone()))
            .collect();
        let mut next = Vec::with_capacity(found.len());
        let mut added = 0;
        let mut updated = 0;

        for (path, size, mtime) in found {
            if let Some(old) = previous.get(&path) {
                if incremental && old.size == size && old.mtime == mtime {
                    next.push(old.clone());
                    continue;
                }
                let mut track = Track::from_path(
                    &path,
                    metadata::read(&path).unwrap_or_default(),
                    size,
                    mtime,
                );
                track.favorite = old.favorite;
                track.play_count = old.play_count;
                track.last_played = old.last_played;
                track.date_added = old.date_added;
                next.push(track);
                updated += 1;
            } else {
                let track = Track::from_path(
                    &path,
                    metadata::read(&path).unwrap_or_default(),
                    size,
                    mtime,
                );
                next.push(track);
                added += 1;
            }
        }
        let removed = self.tracks.len().saturating_sub(next.len());
        self.tracks = next;
        self.last_scan = now_millis();
        self.save()?;
        Ok(ScanReport {
            total: self.tracks.len(),
            added,
            updated,
            removed,
        })
    }

    pub fn tracks(&self) -> Vec<Track> {
        let mut tracks = self.tracks.clone();
        tracks.sort_by(|a, b| {
            a.artist
                .to_lowercase()
                .cmp(&b.artist.to_lowercase())
                .then(a.album.to_lowercase().cmp(&b.album.to_lowercase()))
                .then(a.track_number.cmp(&b.track_number))
                .then(a.title.to_lowercase().cmp(&b.title.to_lowercase()))
        });
        tracks
    }

    pub fn albums(&self) -> Vec<Album> {
        let mut groups: HashMap<String, Vec<&Track>> = HashMap::new();
        for track in &self.tracks {
            let key = format!(
                "{}|{}",
                track.album.to_lowercase(),
                track.album_artist.to_lowercase()
            );
            groups.entry(key).or_default().push(track);
        }
        let mut albums: Vec<Album> = groups
            .into_iter()
            .map(|(key, tracks)| {
                let first = tracks[0];
                Album {
                    id: id_for(&key),
                    title: first.album.clone(),
                    artist: first.album_artist.clone(),
                    track_count: tracks.len(),
                    duration_ms: tracks.iter().map(|t| t.duration_ms).sum(),
                }
            })
            .collect();
        albums.sort_by(|a, b| a.title.to_lowercase().cmp(&b.title.to_lowercase()));
        albums
    }

    pub fn artists(&self) -> Vec<Artist> {
        let mut groups: HashMap<String, Vec<&Track>> = HashMap::new();
        for track in &self.tracks {
            groups
                .entry(track.artist.to_lowercase())
                .or_default()
                .push(track);
        }
        let mut artists: Vec<Artist> = groups
            .into_iter()
            .map(|(key, tracks)| Artist {
                id: id_for(&key),
                name: tracks[0].artist.clone(),
                album_count: tracks
                    .iter()
                    .map(|t| t.album.to_lowercase())
                    .collect::<std::collections::HashSet<_>>()
                    .len(),
                track_count: tracks.len(),
            })
            .collect();
        artists.sort_by(|a, b| a.name.to_lowercase().cmp(&b.name.to_lowercase()));
        artists
    }

    pub fn folders(&self) -> Vec<FolderInfo> {
        let mut groups: HashMap<String, usize> = HashMap::new();
        for track in &self.tracks {
            if let Some(parent) = Path::new(&track.path).parent().and_then(|p| p.to_str()) {
                *groups.entry(parent.to_string()).or_insert(0) += 1;
            }
        }
        let mut folders: Vec<FolderInfo> = groups
            .into_iter()
            .map(|(path, track_count)| FolderInfo {
                name: Path::new(&path)
                    .file_name()
                    .and_then(|n| n.to_str())
                    .unwrap_or(&path)
                    .to_string(),
                path,
                track_count,
            })
            .collect();
        folders.sort_by(|a, b| a.path.cmp(&b.path));
        folders
    }

    pub fn search(&self, query: &str) -> Vec<Track> {
        let needle = query.trim().to_lowercase();
        if needle.is_empty() {
            return Vec::new();
        }
        self.tracks()
            .into_iter()
            .filter(|t| {
                t.title.to_lowercase().contains(&needle)
                    || t.artist.to_lowercase().contains(&needle)
                    || t.album.to_lowercase().contains(&needle)
                    || t.genre.to_lowercase().contains(&needle)
            })
            .collect()
    }

    pub fn favorites(&self) -> Vec<Track> {
        let mut tracks: Vec<Track> = self.tracks.iter().filter(|t| t.favorite).cloned().collect();
        tracks.sort_by(|a, b| b.last_played.cmp(&a.last_played));
        tracks
    }

    pub fn recently_played(&self) -> Vec<Track> {
        let mut tracks: Vec<Track> = self
            .tracks
            .iter()
            .filter(|t| t.last_played > 0)
            .cloned()
            .collect();
        tracks.sort_by(|a, b| b.last_played.cmp(&a.last_played));
        tracks.truncate(100);
        tracks
    }

    pub fn remove_tracks(&mut self, ids: &[String]) -> Result<usize> {
        let before = self.tracks.len();
        self.tracks.retain(|track| !ids.contains(&track.id));
        let removed = before - self.tracks.len();
        if removed > 0 {
            self.save()?;
        }
        Ok(removed)
    }

    pub fn set_favorite(&mut self, id: &str, favorite: bool) -> Result<()> {
        if let Some(track) = self.find_mut(id) {
            track.favorite = favorite;
            self.save()?;
        }
        Ok(())
    }

    pub fn note_play(&mut self, id: &str) -> Result<()> {
        if let Some(track) = self.find_mut(id) {
            track.play_count += 1;
            track.last_played = now_millis();
            self.save()?;
        }
        Ok(())
    }

    pub fn stats(&self, playlist_count: usize) -> LibraryStats {
        LibraryStats {
            tracks: self.tracks.len(),
            albums: self.albums().len(),
            artists: self.artists().len(),
            playlists: playlist_count,
            favorites: self.tracks.iter().filter(|t| t.favorite).count(),
            total_duration_ms: self.tracks.iter().map(|t| t.duration_ms).sum(),
            total_bytes: self.tracks.iter().map(|t| t.size).sum(),
        }
    }
}

fn collect_audio_files(dir: &Path, out: &mut Vec<(String, u64, u64)>, depth: u8) {
    if depth > 12 {
        return;
    }
    let entries = match std::fs::read_dir(dir) {
        Ok(entries) => entries,
        Err(_) => return,
    };
    for entry in entries.flatten() {
        let path = entry.path();
        if path.is_dir() {
            if path
                .file_name()
                .and_then(|n| n.to_str())
                .map(|n| n.starts_with('.'))
                .unwrap_or(false)
            {
                continue;
            }
            collect_audio_files(&path, out, depth + 1);
        } else if path.is_file() {
            let supported = path
                .extension()
                .and_then(|e| e.to_str())
                .map(|e| AUDIO_EXTENSIONS.contains(&e.to_ascii_lowercase().as_str()))
                .unwrap_or(false);
            if supported {
                if let (Some(path), Ok(meta)) = (path.to_str(), entry.metadata()) {
                    let mtime = meta
                        .modified()
                        .ok()
                        .and_then(|t| t.duration_since(std::time::UNIX_EPOCH).ok())
                        .map(|d| d.as_secs())
                        .unwrap_or(0);
                    out.push((path.to_string(), meta.len(), mtime));
                }
            }
        }
    }
}
