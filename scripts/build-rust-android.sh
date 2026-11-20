#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/android/app/src/main/jniLibs"
MANIFEST="$ROOT/rust/core/Cargo.toml"

mkdir -p "$OUT"

if command -v cargo-ndk >/dev/null 2>&1; then
    : "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to your NDK installation}"
    cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -o "$OUT" \
        build --manifest-path "$MANIFEST" --release
else
    echo "cargo-ndk not found; building aarch64-linux-android only (Termux/device builds)."
    cargo build --manifest-path "$MANIFEST" --release --target aarch64-linux-android
    mkdir -p "$OUT/arm64-v8a"
    cp "$ROOT/rust/core/target/aarch64-linux-android/release/libhimemp_core.so" "$OUT/arm64-v8a/"
fi

ls -lh "$OUT"
