use crate::error::{Error, Result};
use std::path::Path;
use symphonia::core::audio::{AudioBufferRef, Signal};
use symphonia::core::codecs::{Decoder, DecoderOptions, CODEC_TYPE_NULL};
use symphonia::core::conv::FromSample;
use symphonia::core::formats::{FormatOptions, FormatReader};
use symphonia::core::io::MediaSourceStream;
use symphonia::core::meta::MetadataOptions;
use symphonia::core::probe::Hint;

macro_rules! flatten {
    ($buf:expr, $out:expr) => {{
        let buffer = $buf;
        let channels = buffer.spec().channels.count().max(1) as usize;
        let frames = buffer.frames();
        for frame in 0..frames {
            for channel in 0..channels {
                $out.push(FromSample::from_sample(buffer.chan(channel)[frame]));
            }
        }
    }};
}

fn append_interleaved(decoded: AudioBufferRef<'_>, out: &mut Vec<i16>) {
    match decoded {
        AudioBufferRef::U8(b) => flatten!(b, out),
        AudioBufferRef::U16(b) => flatten!(b, out),
        AudioBufferRef::U24(b) => flatten!(b, out),
        AudioBufferRef::U32(b) => flatten!(b, out),
        AudioBufferRef::S8(b) => flatten!(b, out),
        AudioBufferRef::S16(b) => flatten!(b, out),
        AudioBufferRef::S24(b) => flatten!(b, out),
        AudioBufferRef::S32(b) => flatten!(b, out),
        AudioBufferRef::F32(b) => flatten!(b, out),
        AudioBufferRef::F64(b) => flatten!(b, out),
    }
}

/// Streaming PCM decoder. Produces interleaved signed 16-bit samples so the
/// Android side can hand them straight to an `AudioTrack` in streaming mode.
pub struct PcmDecoder {
    path: String,
    format: Box<dyn FormatReader>,
    decoder: Box<dyn Decoder>,
    track_id: u32,
    pub sample_rate: u32,
    pub channels: u16,
    /// Channels handed to the audio sink; multichannel input is folded to stereo.
    pub output_channels: u16,
    pub duration_ms: u64,
    pub frames_emitted: u64,
}

impl PcmDecoder {
    pub fn open(path: &str) -> Result<Self> {
        let file = std::fs::File::open(path)?;
        let source = MediaSourceStream::new(Box::new(file), Default::default());
        let mut hint = Hint::new();
        if let Some(extension) = Path::new(path).extension().and_then(|e| e.to_str()) {
            hint.with_extension(extension);
        }
        let probed = symphonia::default::get_probe()
            .format(
                &hint,
                source,
                &FormatOptions::default(),
                &MetadataOptions::default(),
            )
            .map_err(|e| Error::Format(e.to_string()))?;
        let track = probed
            .format
            .default_track()
            .ok_or_else(|| Error::Format(format!("no playable track in {path}")))?;
        if track.codec_params.codec == CODEC_TYPE_NULL {
            return Err(Error::Format(format!("unsupported codec in {path}")));
        }
        let track_id = track.id;
        let sample_rate = track.codec_params.sample_rate.unwrap_or(44_100);
        let channels = track
            .codec_params
            .channels
            .map(|c| c.count() as u16)
            .unwrap_or(2);
        let duration_ms = track
            .codec_params
            .n_frames
            .map(|frames| frames.saturating_mul(1000) / sample_rate as u64)
            .unwrap_or(0);
        let decoder = symphonia::default::get_codecs()
            .make(&track.codec_params, &DecoderOptions::default())
            .map_err(|e| Error::Decode(e.to_string()))?;
        Ok(Self {
            path: path.to_string(),
            format: probed.format,
            decoder,
            track_id,
            sample_rate,
            channels,
            output_channels: channels.min(2),
            duration_ms,
            frames_emitted: 0,
        })
    }

    /// Appends decoded frames to `out`, returning the number of audio frames.
    pub fn next_frames(&mut self, out: &mut Vec<i16>) -> Result<usize> {
        loop {
            let packet = match self.format.next_packet() {
                Ok(packet) => packet,
                Err(symphonia::core::errors::Error::ResetRequired) => {
                    self.decoder.reset();
                    continue;
                }
                Err(symphonia::core::errors::Error::IoError(ref e))
                    if e.kind() == std::io::ErrorKind::UnexpectedEof =>
                {
                    return Ok(0)
                }
                Err(error) => return Err(Error::Decode(error.to_string())),
            };
            if packet.track_id() != self.track_id {
                continue;
            }
            let decoded = match self.decoder.decode(&packet) {
                Ok(decoded) => decoded,
                Err(symphonia::core::errors::Error::DecodeError(_)) => continue,
                Err(error) => return Err(Error::Decode(error.to_string())),
            };
            self.sample_rate = decoded.spec().rate.clamp(8_000, 192_000);
            self.channels = decoded.spec().channels.count().max(1) as u16;
            self.output_channels = self.channels.min(2);
            let mut decoded_samples = Vec::new();
            append_interleaved(decoded, &mut decoded_samples);
            let frames = decoded_samples.len() / self.channels as usize;
            if self.channels > 2 {
                for frame in 0..frames {
                    let base = frame * self.channels as usize;
                    out.push(decoded_samples[base]);
                    out.push(decoded_samples[base + 1]);
                }
            } else {
                out.extend_from_slice(&decoded_samples);
            }
            self.frames_emitted += frames as u64;
            return Ok(frames);
        }
    }

    pub fn position_ms(&self) -> u64 {
        self.frames_emitted
            .saturating_mul(1000)
            .saturating_div(self.sample_rate as u64)
    }

    /// Reopens and discards frames up to the requested offset. Deterministic
    /// across formats (no reliance on container-specific seek tables).
    pub fn seek(&mut self, target_ms: u64) -> Result<()> {
        let target_frames = target_ms.saturating_mul(self.sample_rate as u64) / 1000;
        *self = Self::open(&self.path)?;
        let mut scratch = Vec::with_capacity(8192);
        while self.frames_emitted < target_frames {
            scratch.clear();
            if self.next_frames(&mut scratch)? == 0 {
                break;
            }
        }
        Ok(())
    }
}
