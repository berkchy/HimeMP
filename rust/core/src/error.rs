use thiserror::Error;

#[derive(Debug, Error)]
pub enum Error {
    #[error("I/O error: {0}")]
    Io(#[from] std::io::Error),
    #[error("media format error: {0}")]
    Format(String),
    #[error("decode error: {0}")]
    Decode(String),
    #[error("JNI error: {0}")]
    Jni(String),
    #[error("store error: {0}")]
    Store(String),
    #[error("not found: {0}")]
    NotFound(String),
    #[error("invalid argument: {0}")]
    Invalid(String),
}

pub type Result<T> = std::result::Result<T, Error>;
