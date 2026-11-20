package com.berkchy.himemp.util

import kotlin.math.roundToInt

fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "${(bytes / 1_073_741_824f).roundToInt()} GB"
    bytes >= 1_048_576 -> "${(bytes / 1_048_576f).roundToInt()} MB"
    bytes >= 1024 -> "${(bytes / 1024f).roundToInt()} KB"
    else -> "$bytes B"
}
