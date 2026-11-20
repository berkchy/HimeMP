pub mod library;
pub mod playlists;
pub mod settings;
pub mod state;

use crate::error::Result;
use serde::{de::DeserializeOwned, Serialize};
use std::path::Path;

pub fn load_json<T: DeserializeOwned>(path: &Path, default: T) -> T {
    std::fs::read_to_string(path)
        .ok()
        .and_then(|data| serde_json::from_str(&data).ok())
        .unwrap_or(default)
}

pub fn save_json<T: Serialize>(path: &Path, value: &T) -> Result<()> {
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent)?;
    }
    let tmp = path.with_extension("tmp");
    std::fs::write(
        &tmp,
        serde_json::to_vec(value).map_err(|e| crate::error::Error::Store(e.to_string()))?,
    )?;
    std::fs::rename(&tmp, path)?;
    Ok(())
}
