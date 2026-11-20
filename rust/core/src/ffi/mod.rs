mod bridge;

use crate::error::{Error, Result};
use crate::playback::{AudioSink, AudioSinkFactory, EventSink};
use jni::objects::{GlobalRef, JByteArray, JClass, JObject, JValue};
use jni::{AttachGuard, JavaVM};
use std::sync::{Arc, Mutex};

fn take_exception(env: &mut jni::JNIEnv<'_>) -> Result<()> {
    if env
        .exception_check()
        .map_err(|e| Error::Jni(e.to_string()))?
    {
        let _ = env.exception_describe();
        env.exception_clear()
            .map_err(|e| Error::Jni(e.to_string()))?;
        return Err(Error::Jni(
            "Java exception raised during native audio call".into(),
        ));
    }
    Ok(())
}

pub struct AndroidSinkFactory {
    vm: Arc<JavaVM>,
    sink_class: GlobalRef,
}

impl AndroidSinkFactory {
    pub fn new(vm: Arc<JavaVM>, sink_class: GlobalRef) -> Self {
        Self { vm, sink_class }
    }
}

impl AudioSinkFactory for AndroidSinkFactory {
    fn create(&self, sample_rate: u32, channels: u16) -> Result<Box<dyn AudioSink>> {
        let mut env = self
            .vm
            .attach_current_thread()
            .map_err(|e| Error::Jni(e.to_string()))?;
        let class = unsafe { JClass::from_raw(self.sink_class.as_obj().as_raw()) };
        let rate = sample_rate.clamp(8_000, 192_000);
        let channel_count = channels.clamp(1, 2);
        let result = env.call_static_method(
            class,
            "create",
            "(II)Ljava/lang/Object;",
            &[JValue::Int(rate as i32), JValue::Int(channel_count as i32)],
        );
        take_exception(&mut env)?;
        let track = result
            .map_err(|e| Error::Jni(e.to_string()))?
            .l()
            .map_err(|e| Error::Jni(e.to_string()))?;
        AndroidAudioTrackSink::new(self.vm.clone(), track)
    }
}

/// Rust owns the JNI global references and releases them in `Drop`, so no
/// native heap or JNI local references leak when a track changes or the app exits.
struct AndroidAudioTrackSink {
    vm: Arc<JavaVM>,
    track: GlobalRef,
    bytes: GlobalRef,
    capacity: usize,
}

impl AndroidAudioTrackSink {
    fn new(vm: Arc<JavaVM>, local: JObject<'_>) -> Result<Box<dyn AudioSink>> {
        let (track, bytes) = {
            let env = vm
                .attach_current_thread()
                .map_err(|e| Error::Jni(e.to_string()))?;
            let track = env
                .new_global_ref(local)
                .map_err(|e| Error::Jni(e.to_string()))?;
            let empty = env
                .new_byte_array(0)
                .map_err(|e| Error::Jni(e.to_string()))?;
            let bytes = env
                .new_global_ref(empty)
                .map_err(|e| Error::Jni(e.to_string()))?;
            (track, bytes)
        };
        Ok(Box::new(Self {
            vm,
            track,
            bytes,
            capacity: 0,
        }))
    }

    fn env(&self) -> Result<AttachGuard<'_>> {
        self.vm
            .attach_current_thread()
            .map_err(|e| Error::Jni(e.to_string()))
    }

    fn call_void(&self, name: &str) {
        if let Ok(mut env) = self.env() {
            let _ = env.call_method(self.track.as_obj(), name, "()V", &[]);
            let _ = env.exception_clear();
        }
    }
}

impl AudioSink for AndroidAudioTrackSink {
    fn open(&mut self, _sample_rate: u32, _channels: u16) -> Result<()> {
        Ok(())
    }

    fn write(&mut self, pcm: &[i16]) -> Result<()> {
        let needed = pcm.len().saturating_mul(2);
        if needed == 0 {
            return Ok(());
        }
        let mut env = self
            .vm
            .attach_current_thread()
            .map_err(|e| Error::Jni(e.to_string()))?;
        if needed > self.capacity {
            let array = env
                .new_byte_array(needed as i32)
                .map_err(|e| Error::Jni(e.to_string()))?;
            self.bytes = env
                .new_global_ref(array)
                .map_err(|e| Error::Jni(e.to_string()))?;
            self.capacity = needed;
        }
        let mut bytes = Vec::with_capacity(needed);
        for sample in pcm {
            bytes.extend_from_slice(&sample.to_le_bytes());
        }
        let array = self.bytes.as_obj();
        let byte_array = unsafe { JByteArray::from_raw(array.as_raw()) };
        let raw = unsafe {
            std::slice::from_raw_parts(bytes.as_ptr() as *const jni::sys::jbyte, bytes.len())
        };
        env.set_byte_array_region(&byte_array, 0, raw)
            .map_err(|e| Error::Jni(e.to_string()))?;
        take_exception(&mut env)?;
        let mut offset = 0usize;
        let mut zeros = 0u32;
        while offset < bytes.len() {
            let count = (bytes.len() - offset) as i32;
            let value = env.call_method(
                self.track.as_obj(),
                "write",
                "([BII)I",
                &[
                    JValue::Object(array),
                    JValue::Int(offset as i32),
                    JValue::Int(count),
                ],
            );
            take_exception(&mut env)?;
            let written = value
                .map_err(|e| Error::Jni(e.to_string()))?
                .i()
                .map_err(|e| Error::Jni(e.to_string()))?;
            if written < 0 {
                return Err(Error::Jni(format!("AudioTrack.write failed ({written})")));
            }
            if written == 0 {
                zeros += 1;
                if zeros == 5 {
                    let _ = env.call_method(self.track.as_obj(), "play", "()V", &[]);
                    let _ = env.exception_clear();
                }
                if zeros > 200 {
                    return Err(Error::Jni(
                        "AudioTrack.write made no progress after repeated retries".into(),
                    ));
                }
                std::thread::sleep(std::time::Duration::from_millis(2));
                continue;
            }
            zeros = 0;
            offset += written as usize;
        }
        Ok(())
    }

    fn pause(&mut self) {
        self.call_void("pause");
    }

    fn resume(&mut self) {
        self.call_void("play");
    }

    fn flush(&mut self) {
        self.call_void("flush");
    }

    fn close(&mut self) {
        self.call_void("stop");
        self.call_void("release");
    }
}

/// Bridges engine events to a Kotlin `EventListener` without holding a
/// `JNIEnv` across threads: the VM is attached per call, the listener is a
/// single global reference, and the mutex guard is held only for the call.
pub struct JniEventSink {
    vm: Arc<JavaVM>,
    listener: Arc<Mutex<Option<GlobalRef>>>,
}

impl JniEventSink {
    pub fn new(vm: Arc<JavaVM>, listener: Arc<Mutex<Option<GlobalRef>>>) -> Self {
        Self { vm, listener }
    }

    pub fn set_listener(&self, listener: GlobalRef) {
        *self
            .listener
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner()) = Some(listener);
    }

    pub fn clear_listener(&self) {
        *self
            .listener
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner()) = None;
    }
}

impl EventSink for JniEventSink {
    fn emit(&self, event: &str, payload: &str) {
        let guard = self
            .listener
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner());
        let Some(listener) = guard.as_ref() else {
            return;
        };
        let mut env = match self.vm.attach_current_thread() {
            Ok(env) => env,
            Err(_) => return,
        };
        let event = match env.new_string(event) {
            Ok(value) => value,
            Err(_) => return,
        };
        let payload = match env.new_string(payload) {
            Ok(value) => value,
            Err(_) => return,
        };
        let _ = env.call_method(
            listener.as_obj(),
            "onEvent",
            "(Ljava/lang/String;Ljava/lang/String;)V",
            &[JValue::Object(&event), JValue::Object(&payload)],
        );
        if env.exception_check().unwrap_or(false) {
            let _ = env.exception_clear();
        }
    }
}
