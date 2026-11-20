use crate::error::{Error, Result};
use std::collections::HashMap;
use std::path::Path;
use symphonia::core::formats::FormatOptions;
use symphonia::core::io::MediaSourceStream;
use symphonia::core::meta::MetadataOptions;
use symphonia::core::probe::Hint;

#[derive(Clone, Debug, Default)]
pub struct TrackMeta {
    pub title: String,
    pub artist: String,
    pub album: String,
    pub album_artist: String,
    pub genre: String,
    pub year: u32,
    pub track_number: u32,
    pub duration_ms: u64,
}

fn first_number(s: &str) -> u32 {
    s.chars()
        .skip_while(|c| !c.is_ascii_digit())
        .take_while(|c| c.is_ascii_digit())
        .collect::<String>()
        .parse()
        .unwrap_or(0)
}

pub fn read(path: &str) -> Result<TrackMeta> {
    let file = std::fs::File::open(path)?;
    let source = MediaSourceStream::new(Box::new(file), Default::default());
    let mut hint = Hint::new();
    if let Some(extension) = Path::new(path).extension().and_then(|e| e.to_str()) {
        hint.with_extension(extension);
    }
    let mut probed = symphonia::default::get_probe()
        .format(
            &hint,
            source,
            &FormatOptions::default(),
            &MetadataOptions::default(),
        )
        .map_err(|e| Error::Format(e.to_string()))?;

    let mut tags: HashMap<String, String> = HashMap::new();
    if let Some(reader) = probed.metadata.get().as_ref().and_then(|m| m.current()) {
        for tag in reader.tags() {
            tags.insert(tag.key.to_ascii_lowercase(), tag.value.to_string());
        }
    }
    let get = |keys: &[&str]| -> String {
        keys.iter()
            .find_map(|k| tags.get(*k))
            .cloned()
            .unwrap_or_default()
            .trim()
            .to_string()
    };

    let track = probed
        .format
        .default_track()
        .ok_or_else(|| Error::Format(format!("no playable track in {path}")))?;
    let params = &track.codec_params;
    let sample_rate = params.sample_rate.unwrap_or(44_100) as u64;
    let duration_ms = params
        .n_frames
        .map(|frames| frames.saturating_mul(1000) / sample_rate)
        .unwrap_or(0);

    Ok(TrackMeta {
        title: get(&["title", "tit2"]),
        artist: get(&["artist", "tpe1", "performer"]),
        album: get(&["album", "talb"]),
        album_artist: get(&["albumartist", "album artist", "tpe2"]),
        genre: get(&["genre", "tcon"]),
        year: first_number(&get(&["date", "year", "tyer", "tdrc"])),
        track_number: first_number(&get(&["tracknumber", "track", "trck"])),
        duration_ms,
    })
}

#[cfg(test)]
mod tests {
    use super::first_number;

    #[test]
    fn parses_leading_number() {
        assert_eq!(first_number("7/12"), 7);
        assert_eq!(first_number("2024-01-02"), 2024);
        assert_eq!(first_number("none"), 0);
    }
}
