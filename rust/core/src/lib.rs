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
    use std::sync::Arc;

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
            let mut playlists = app.playlists.lock().unwrap();
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
            let mut settings = app.settings.lock().unwrap();
            settings.set("theme", "dark").unwrap();
            assert_eq!(settings.get("theme", "system"), "dark");
        }
        app.handle().send(super::playback::Command::SetQueue(
            vec!["a".into(), "b".into()],
            0,
        ));
        app.runtime.lock().unwrap().shutdown();
        let restored = PlaybackState::open(&root);
        assert_eq!(restored.queue, vec!["a", "b"]);
        let _ = std::fs::remove_dir_all(&root);
    }
}
