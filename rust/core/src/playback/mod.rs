pub mod decoder;
pub mod engine;
pub mod sink;

pub use engine::{Command, EngineHandle, PlaybackRuntime};
pub use sink::{AudioSink, AudioSinkFactory, SilentSink, SilentSinkFactory};

/// Minimal abstraction used by the engine to report state changes back to Kotlin.
pub trait EventSink: Send + Sync {
    fn emit(&self, event: &str, payload: &str);
}

pub struct NoopEventSink;

impl EventSink for NoopEventSink {
    fn emit(&self, _event: &str, _payload: &str) {}
}
