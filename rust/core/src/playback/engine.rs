use super::{AudioSink, AudioSinkFactory, EventSink};
use crate::error::{Error, Result};
use crate::playback::decoder::PcmDecoder;
use crate::store::library::LibraryStore;
use crate::store::state::PlaybackState;
use std::path::PathBuf;
use std::sync::mpsc::{channel, Receiver, RecvTimeoutError, Sender};
use std::sync::{Arc, Mutex, RwLock};
use std::thread::{self, JoinHandle};
use std::time::{Duration, Instant};

fn panic_message(payload: Box<dyn std::any::Any + Send>) -> String {
    if let Some(message) = payload.downcast_ref::<String>() {
        message.clone()
    } else if let Some(message) = payload.downcast_ref::<&str>() {
        (*message).to_string()
    } else {
        "unknown panic".to_string()
    }
}

#[derive(Debug)]
pub enum Command {
    Play,
    Pause,
    Stop,
    Next,
    Previous,
    Seek(u64),
    SetRepeat(i32),
    SetShuffle(bool),
    SetSpeed(f32),
    SetSleepTimer(i64),
    SetQueue(Vec<String>, i32),
    QueueAdd(Vec<String>, i32),
    QueueMove(usize, usize),
    QueueRemove(usize),
    QueueClear,
    Shutdown,
}

#[derive(Clone)]
pub struct EngineHandle {
    tx: Sender<Command>,
}

impl EngineHandle {
    pub fn send(&self, command: Command) {
        let _ = self.tx.send(command);
    }
}

struct Engine {
    root: PathBuf,
    library: Arc<Mutex<LibraryStore>>,
    state: Arc<RwLock<PlaybackState>>,
    factory: Arc<dyn AudioSinkFactory>,
    events: Arc<dyn EventSink>,
    sink: Option<Box<dyn AudioSink>>,
    sink_spec: Option<(u32, u16)>,
    decoder: Option<PcmDecoder>,
    playing: bool,
    sleep_deadline: Option<Instant>,
    stop_at_track_end: bool,
}

impl Engine {
    fn spawn(
        root: PathBuf,
        library: Arc<Mutex<LibraryStore>>,
        state: Arc<RwLock<PlaybackState>>,
        factory: Arc<dyn AudioSinkFactory>,
        events: Arc<dyn EventSink>,
    ) -> (EngineHandle, JoinHandle<()>) {
        let (tx, rx) = channel();
        let handle = thread::Builder::new()
            .name("hime-playback".into())
            .spawn(move || {
                let mut engine = Self {
                    root,
                    library,
                    state,
                    factory,
                    events,
                    sink: None,
                    sink_spec: None,
                    decoder: None,
                    playing: false,
                    sleep_deadline: None,
                    stop_at_track_end: false,
                };
                engine.run(rx);
            })
            .expect("failed to spawn playback thread");
        (EngineHandle { tx }, handle)
    }

    fn run(&mut self, rx: Receiver<Command>) {
        let mut last_emit = Instant::now();
        let mut last_save = Instant::now();
        loop {
            match rx.recv_timeout(Duration::from_millis(10)) {
                Ok(command) => match std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
                    self.handle(command)
                })) {
                    Ok(true) => break,
                    Ok(false) => {}
                    Err(payload) => {
                        self.events.emit(
                            "fatal",
                            &format!("Playback command panicked: {}", panic_message(payload)),
                        );
                        break;
                    }
                },
                Err(RecvTimeoutError::Timeout) => {}
                Err(RecvTimeoutError::Disconnected) => break,
            }
            if self.playing {
                match std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| self.pump())) {
                    Ok(Ok(())) => {}
                    Ok(Err(error)) => {
                        self.playing = false;
                        self.events.emit("error", &error.to_string());
                        let _ = self.persist();
                    }
                    Err(payload) => {
                        self.playing = false;
                        self.events.emit(
                            "fatal",
                            &format!("Playback panicked: {}", panic_message(payload)),
                        );
                        break;
                    }
                }
            }
            if self
                .sleep_deadline
                .is_some_and(|deadline| Instant::now() >= deadline)
            {
                self.pause();
                self.stop_at_track_end = false;
                self.sleep_deadline = None;
                self.state
                    .write()
                    .unwrap_or_else(|poisoned| poisoned.into_inner())
                    .sleep_remaining_ms = -1;
                self.events.emit("sleepTimerFired", "{}");
            }
            if last_emit.elapsed() >= Duration::from_millis(250) {
                self.emit_state();
                last_emit = Instant::now();
            }
            if last_save.elapsed() >= Duration::from_secs(3) {
                let _ = self.persist();
                last_save = Instant::now();
            }
        }
        if let Some(mut sink) = self.sink.take() {
            sink.close();
        }
        self.state
            .write()
            .unwrap_or_else(|poisoned| poisoned.into_inner())
            .playing = false;
        let _ = self.persist();
    }

    fn handle(&mut self, command: Command) -> bool {
        match command {
            Command::Shutdown => return true,
            Command::Play => {
                if !self
                    .state
                    .read()
                    .unwrap_or_else(|poisoned| poisoned.into_inner())
                    .queue
                    .is_empty()
                {
                    self.playing = true;
                    self.state
                        .write()
                        .unwrap_or_else(|poisoned| poisoned.into_inner())
                        .playing = true;
                    self.note_current_play();
                }
            }
            Command::Pause => self.pause(),
            Command::Stop => {
                self.playing = false;
                self.state
                    .write()
                    .unwrap_or_else(|poisoned| poisoned.into_inner())
                    .playing = false;
                if let Some(decoder) = self.decoder.take() {
                    drop(decoder);
                }
                if let Some(sink) = self.sink.as_mut() {
                    sink.flush();
                }
                self.state
                    .write()
                    .unwrap_or_else(|poisoned| poisoned.into_inner())
                    .position_ms = 0;
            }
            Command::Next => {
                self.advance(1);
                self.note_current_play();
            }
            Command::Previous => {
                let position = self
                    .state
                    .read()
                    .unwrap_or_else(|poisoned| poisoned.into_inner())
                    .position_ms;
                if position > 3000 {
                    self.seek(0);
                } else {
                    self.advance(-1);
                }
                self.note_current_play();
            }
            Command::Seek(ms) => self.seek(ms),
            Command::SetRepeat(mode) => {
                self.state
                    .write()
                    .unwrap_or_else(|poisoned| poisoned.into_inner())
                    .repeat = mode.clamp(0, 2);
            }
            Command::SetShuffle(enabled) => {
                self.state
                    .write()
                    .unwrap_or_else(|poisoned| poisoned.into_inner())
                    .shuffle = enabled;
            }
            Command::SetSpeed(speed) => {
                self.state
                    .write()
                    .unwrap_or_else(|poisoned| poisoned.into_inner())
                    .speed = speed.clamp(0.5, 2.0);
            }
            Command::SetSleepTimer(ms) => {
                if ms < 0 {
                    self.stop_at_track_end = ms == -2;
                    self.sleep_deadline = None;
                    self.state
                        .write()
                        .unwrap_or_else(|poisoned| poisoned.into_inner())
                        .sleep_remaining_ms = ms;
                } else if ms == 0 {
                    self.sleep_deadline = None;
                    self.state
                        .write()
                        .unwrap_or_else(|poisoned| poisoned.into_inner())
                        .sleep_remaining_ms = -1;
                } else {
                    self.sleep_deadline =
                        Some(Instant::now() + Duration::from_millis(ms.max(0) as u64));
                    self.state
                        .write()
                        .unwrap_or_else(|poisoned| poisoned.into_inner())
                        .sleep_remaining_ms = ms as i64;
                }
                self.events.emit("sleepTimer", "{}");
            }
            Command::SetQueue(ids, start) => {
                {
                    let mut state = self
                        .state
                        .write()
                        .unwrap_or_else(|poisoned| poisoned.into_inner());
                    state.queue = ids;
                    state.index = start;
                    state.position_ms = 0;
                }
                self.decoder = None;
                self.note_current_play();
            }
            Command::QueueAdd(ids, at) => {
                let mut state = self
                    .state
                    .write()
                    .unwrap_or_else(|poisoned| poisoned.into_inner());
                let len = state.queue.len();
                let at = (at.max(0) as usize).min(len);
                for (offset, id) in ids.into_iter().enumerate() {
                    state.queue.insert(at + offset, id);
                }
                if state.index < 0 && !state.queue.is_empty() {
                    state.index = 0;
                }
            }
            Command::QueueMove(from, to) => {
                let mut state = self
                    .state
                    .write()
                    .unwrap_or_else(|poisoned| poisoned.into_inner());
                if from < state.queue.len() && to < state.queue.len() {
                    let id = state.queue.remove(from);
                    state.queue.insert(to, id);
                    state.index = to as i32;
                }
            }
            Command::QueueRemove(index) => {
                let mut state = self
                    .state
                    .write()
                    .unwrap_or_else(|poisoned| poisoned.into_inner());
                if index < state.queue.len() {
                    state.queue.remove(index);
                    if state.index as usize >= state.queue.len() {
                        state.index = state.queue.len().saturating_sub(1) as i32;
                    }
                }
            }
            Command::QueueClear => {
                self.decoder = None;
                self.playing = false;
                let mut state = self
                    .state
                    .write()
                    .unwrap_or_else(|poisoned| poisoned.into_inner());
                state.queue.clear();
                state.index = -1;
                state.position_ms = 0;
                state.playing = false;
            }
        }
        self.emit_state();
        let _ = self.persist();
        false
    }

    fn pause(&mut self) {
        self.playing = false;
        self.state
            .write()
            .unwrap_or_else(|poisoned| poisoned.into_inner())
            .playing = false;
        if let Some(sink) = self.sink.as_mut() {
            sink.pause();
        }
    }

    fn seek(&mut self, ms: u64) {
        self.state
            .write()
            .unwrap_or_else(|poisoned| poisoned.into_inner())
            .position_ms = ms;
        if let Some(decoder) = self.decoder.as_mut() {
            if let Err(error) = decoder.seek(ms) {
                self.events.emit("error", &error.to_string());
                return;
            }
        } else {
            self.decoder = None;
        }
        if let Some(sink) = self.sink.as_mut() {
            sink.flush();
        }
        self.emit_state();
    }

    fn note_current_play(&self) {
        let id = self
            .state
            .read()
            .unwrap_or_else(|poisoned| poisoned.into_inner())
            .current_id()
            .map(str::to_string);
        if let Some(id) = id {
            if let Err(error) = self
                .library
                .lock()
                .unwrap_or_else(|poisoned| poisoned.into_inner())
                .note_play(&id)
            {
                log::warn!("could not record play: {error}");
            }
        }
    }

    fn advance(&mut self, delta: i32) -> bool {
        let (len, current, shuffle, repeat) = {
            let state = self
                .state
                .read()
                .unwrap_or_else(|poisoned| poisoned.into_inner());
            (state.queue.len(), state.index, state.shuffle, state.repeat)
        };
        if len == 0 {
            return false;
        }
        let mut next = current + delta;
        if shuffle && len > 1 {
            let seed = current as u64 + crate::model::now_millis();
            let offset = (seed.wrapping_mul(6364136223846793005).wrapping_add(1) >> 33) as i32;
            next = current + 1 + offset.rem_euclid((len - 1) as i32);
        }
        if next >= len as i32 {
            if repeat == 1 {
                next = 0;
            } else {
                self.playing = false;
                self.state
                    .write()
                    .unwrap_or_else(|poisoned| poisoned.into_inner())
                    .playing = false;
                self.events.emit("queueEnded", "{}");
                return false;
            }
        }
        if next < 0 {
            next = len as i32 - 1;
        }
        self.decoder = None;
        let mut state = self
            .state
            .write()
            .unwrap_or_else(|poisoned| poisoned.into_inner());
        state.index = next;
        state.position_ms = 0;
        drop(state);
        self.emit_state();
        true
    }

    fn open_current(&mut self) -> Result<()> {
        let id = self
            .state
            .read()
            .unwrap_or_else(|poisoned| poisoned.into_inner())
            .current_id()
            .map(str::to_string)
            .ok_or_else(|| Error::Invalid("empty queue".into()))?;
        let path = self
            .library
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner())
            .find(&id)
            .map(|track| track.path.clone())
            .ok_or_else(|| Error::NotFound(id.clone()))?;
        let decoder = PcmDecoder::open(&path)?;
        let spec = (decoder.sample_rate, decoder.output_channels);
        if self.sink_spec != Some(spec) {
            if let Some(mut old) = self.sink.take() {
                old.close();
            }
            let mut sink = self.factory.create(spec.0, spec.1)?;
            sink.open(spec.0, spec.1)?;
            self.sink = Some(sink);
            self.sink_spec = Some(spec);
        }
        if let Some(sink) = self.sink.as_mut() {
            sink.resume();
        }
        {
            let mut state = self
                .state
                .write()
                .unwrap_or_else(|poisoned| poisoned.into_inner());
            state.duration_ms = decoder.duration_ms;
            state.track_id = Some(id);
            state.position_ms = 0;
        }
        self.decoder = Some(decoder);
        self.events.emit("trackChanged", "{}");
        self.emit_state();
        Ok(())
    }

    fn pump(&mut self) -> Result<()> {
        if self.decoder.is_none() {
            self.open_current()?;
        }
        let (frames, position, duration, pcm) = {
            let decoder = self.decoder.as_mut().unwrap();
            let mut pcm = Vec::with_capacity(16_384);
            let frames = decoder.next_frames(&mut pcm)?;
            (frames, decoder.position_ms(), decoder.duration_ms, pcm)
        };
        if frames == 0 {
            self.on_track_end();
            return Ok(());
        }
        if let Some(sink) = self.sink.as_mut() {
            sink.write(&pcm)?;
        }
        let mut state = self
            .state
            .write()
            .unwrap_or_else(|poisoned| poisoned.into_inner());
        state.position_ms = position;
        state.duration_ms = duration;
        Ok(())
    }

    fn on_track_end(&mut self) {
        self.decoder = None;
        if self.stop_at_track_end {
            self.stop_at_track_end = false;
            self.sleep_deadline = None;
            self.state
                .write()
                .unwrap_or_else(|poisoned| poisoned.into_inner())
                .sleep_remaining_ms = -1;
            self.events.emit("sleepTimerFired", "{}");
            self.pause();
            return;
        }
        let repeat = self
            .state
            .read()
            .unwrap_or_else(|poisoned| poisoned.into_inner())
            .repeat;
        if repeat == 2 {
            if let Err(error) = self.seek_and_continue() {
                self.events.emit("error", &error.to_string());
            }
            return;
        }
        if !self.advance(1) {
            if let Some(sink) = self.sink.as_mut() {
                sink.flush();
            }
            let duration = self
                .state
                .read()
                .unwrap_or_else(|poisoned| poisoned.into_inner())
                .duration_ms;
            self.state
                .write()
                .unwrap_or_else(|poisoned| poisoned.into_inner())
                .position_ms = duration;
            self.emit_state();
        }
        self.note_current_play();
    }

    fn seek_and_continue(&mut self) -> Result<()> {
        self.state
            .write()
            .unwrap_or_else(|poisoned| poisoned.into_inner())
            .position_ms = 0;
        self.decoder = None;
        self.open_current()
    }

    fn emit_state(&self) {
        let state = self
            .state
            .read()
            .unwrap_or_else(|poisoned| poisoned.into_inner());
        let mut payload = serde_json::to_value(&*state).unwrap_or_default();
        if self.sleep_deadline.is_some() && payload.get("sleepRemainingMs").is_none() {
            payload["sleepRemainingMs"] = serde_json::json!(-1);
        }
        self.events.emit("state", &payload.to_string());
    }

    fn persist(&self) -> Result<()> {
        let state = self
            .state
            .read()
            .unwrap_or_else(|poisoned| poisoned.into_inner());
        state.save(&self.root)
    }
}

pub struct PlaybackRuntime {
    pub handle: EngineHandle,
    thread: Option<JoinHandle<()>>,
}

impl PlaybackRuntime {
    pub fn start(
        root: PathBuf,
        library: Arc<Mutex<LibraryStore>>,
        state: Arc<RwLock<PlaybackState>>,
        factory: Arc<dyn AudioSinkFactory>,
        events: Arc<dyn EventSink>,
    ) -> Self {
        let (handle, thread) = Engine::spawn(root, library, state, factory, events);
        Self {
            handle,
            thread: Some(thread),
        }
    }

    pub fn shutdown(&mut self) {
        self.handle.send(Command::Shutdown);
        if let Some(thread) = self.thread.take() {
            let _ = thread.join();
        }
    }
}

impl Drop for PlaybackRuntime {
    fn drop(&mut self) {
        self.shutdown();
    }
}
