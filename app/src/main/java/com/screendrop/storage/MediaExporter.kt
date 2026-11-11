package com.screendrop.storage

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.io.IOException

/**
 * Stores received files in the shared Documents/ScreenDrop folder via
 * MediaStore (no storage permission needed on API 29+). Files are visible
 * to any file manager under their real display names.
 *
 * On API < 29 this backend is not used; FileStorage falls back to
 * app-private storage.
 */
class MediaExporter(private val resolver: ContentResolver) {

    /** Public directory where received files are placed. */
    val relativePath: String = "${android.os.Environment.DIRECTORY_DOCUMENTS}/ScreenDrop"

    /**
     * Copies [src] into Documents/ScreenDrop as [displayName]. The row is
     * created with IS_PENDING and published only after the full copy, so
     * other apps never observe partial files. Returns the content URI.
     */
    fun insert(displayName: String, mimeType: String, src: File): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values)
            ?: throw IOException("MediaStore insert failed for $displayName")
        try {
            val out = resolver.openOutputStream(uri, "w")
                ?: throw IOException("Cannot open output stream for $uri")
            out.use { o -> src.inputStream().use { it.copyTo(o) } }
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
        return uri
    }

    fun delete(uri: Uri): Boolean = try {
        resolver.delete(uri, null, null) > 0
    } catch (t: Throwable) {
        false
    }

    companion object {
        val available: Boolean get() = Build.VERSION.SDK_INT >= 29
    }
}
