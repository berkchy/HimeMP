package com.berkchy.himemp.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.core.content.ContextCompat
import android.Manifest

object Permissions {
    fun audioPermission(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    fun hasAudioPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, audioPermission()) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    fun takePersistablePermission(context: Context, uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        }
    }

    /** Converts an SAF tree URI into a filesystem path for the native scanner. */
    fun treeUriToPath(uri: Uri): String? {
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
        val parts = documentId.split(":", limit = 2)
        val root = parts[0]
        val suffix = parts.getOrNull(1).orEmpty().trimStart('/')
        return when (root) {
            "primary", "home" -> "/storage/emulated/0/${suffix}".trimEnd('/')
            "raw" -> "/storage/emulated/0/${suffix}".trimEnd('/')
            else -> "/storage/$root/${suffix}".trimEnd('/')
        }
    }
}
