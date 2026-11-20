pub mod app;
pub mod error;
pub mod ffi;
pub mod library;
pub mod model;
pub mod playback;
pub mod store;

#[cfg(test)]
mod tests {
    use super::app::App;
    use super::playback::{NoopEventSink, SilentSinkFactory};
    use super::store::state::PlaybackState;
    use std::path::Path;
    use std::sync::Arc;
    use std::time::Duration;

    fn write_test_wav(path: &Path, seconds: u32) {
        let rate = 8_000u32;
        let samples = rate * seconds;
        let data_len = samples * 2;
        let mut bytes = Vec::with_capacity(44 + data_len as usize);
        bytes.extend_from_slice(b"RIFF");
        bytes.extend_from_slice(&(36 + data_len).to_le_bytes());
        bytes.extend_from_slice(b"WAVEfmt ");
        bytes.extend_from_slice(&16u32.to_le_bytes());
        bytes.extend_from_slice(&1u16.to_le_bytes());
        bytes.extend_from_slice(&1u16.to_le_bytes());
        bytes.extend_from_slice(&rate.to_le_bytes());
        bytes.extend_from_slice(&(rate * 2).to_le_bytes());
        bytes.extend_from_slice(&2u16.to_le_bytes());
        bytes.extend_from_slice(&16u16.to_le_bytes());
        bytes.extend_from_slice(b"data");
        bytes.extend_from_slice(&data_len.to_le_bytes());
        for _ in 0..samples {
            bytes.extend_from_slice(&0i16.to_le_bytes());
        }
        std::fs::write(path, bytes).unwrap();
    }

    #[test]
    fn decodes_and_advances_playback() {
        let root = std::env::temp_dir().join(format!("hime-play-test-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&root);
        std::fs::create_dir_all(&root).unwrap();
        let wav = root.join("tone.wav");
        write_test_wav(&wav, 2);

        let app = App::init(
            root.to_str().unwrap(),
            Arc::new(SilentSinkFactory),
            Some(Arc::new(NoopEventSink)),
        )
        .unwrap();
        let track = super::model::Track::from_path(
            wav.to_str().unwrap(),
            Default::default(),
            std::fs::metadata(&wav).unwrap().len(),
            0,
        );
        let id = track.id.clone();
        app.library
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner())
            .tracks
            .push(track);
        app.handle()
            .send(super::playback::Command::SetQueue(vec![id], 0));
        app.handle().send(super::playback::Command::Play);
        std::thread::sleep(Duration::from_millis(400));
        let position = app
            .state
            .read()
            .unwrap_or_else(|poisoned| poisoned.into_inner())
            .position_ms;
        app.runtime
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner())
            .shutdown();
        let _ = std::fs::remove_dir_all(&root);
        assert!(position > 0, "playback did not advance: {position}ms");
    }

    #[test]
    fn playlists_and_state_round_trip() {
        let root = std::env::temp_dir().join(format!("hime-test-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&root);
        let app = App::init(
            root.to_str().unwrap(),
            Arc::new(SilentSinkFactory),
            Some(Arc::new(NoopEventSink)),
        )
        .unwrap();
        {
            let mut playlists = app
                .playlists
                .lock()
                .unwrap_or_else(|poisoned| poisoned.into_inner());
            let playlist = playlists.create("Road trip").unwrap();
            playlists
                .add_tracks(&playlist.id, &["a".into(), "b".into()])
                .unwrap();
            playlists.reorder(&playlist.id, 0, 1).unwrap();
            assert_eq!(
                playlists.get(&playlist.id).unwrap().track_ids,
                vec!["b", "a"]
            );
        }
        {
            let mut settings = app
                .settings
                .lock()
                .unwrap_or_else(|poisoned| poisoned.into_inner());
            settings.set("theme", "dark").unwrap();
            assert_eq!(settings.get("theme", "system"), "dark");
        }
        app.handle().send(super::playback::Command::SetQueue(
            vec!["a".into(), "b".into()],
            0,
        ));
        app.runtime
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner())
            .shutdown();
        let restored = PlaybackState::open(&root);
        assert_eq!(restored.queue, vec!["a", "b"]);
        let _ = std::fs::remove_dir_all(&root);
    }
}
