use crate::error::Result;

pub trait AudioSink: Send {
    fn open(&mut self, sample_rate: u32, channels: u16) -> Result<()>;
    fn write(&mut self, pcm: &[i16]) -> Result<()>;
    fn pause(&mut self);
    fn resume(&mut self);
    fn flush(&mut self);
    fn close(&mut self);
}

pub trait AudioSinkFactory: Send + Sync {
    fn create(&self, sample_rate: u32, channels: u16) -> Result<Box<dyn AudioSink>>;
}

/// Counter sink used by host-side tests and by builds without an Android VM.
pub struct SilentSink {
    pub frames: u64,
}

impl AudioSink for SilentSink {
    fn open(&mut self, _sample_rate: u32, _channels: u16) -> Result<()> {
        Ok(())
    }
    fn write(&mut self, pcm: &[i16]) -> Result<()> {
        self.frames += pcm.len() as u64;
        Ok(())
    }
    fn pause(&mut self) {}
    fn resume(&mut self) {}
    fn flush(&mut self) {}
    fn close(&mut self) {}
}

pub struct SilentSinkFactory;

impl AudioSinkFactory for SilentSinkFactory {
    fn create(&self, _sample_rate: u32, _channels: u16) -> Result<Box<dyn AudioSink>> {
        Ok(Box::new(SilentSink { frames: 0 }))
    }
}
