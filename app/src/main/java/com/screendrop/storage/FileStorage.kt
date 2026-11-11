package com.screendrop.storage

import android.content.Context
import android.net.Uri
import com.screendrop.model.FileMetadata
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Metadata lives in private app storage:
 *
 *   baseDir/<file-id>/metadata.json  — metadata (always)
 *   tempDir/<upload-id>.part         — in-flight upload
 *
 * File bytes go to Documents/ScreenDrop via MediaStore on API 29+
 * ([MediaExporter]); on older devices (or when MediaStore fails) bytes stay
 * in the private app storage at baseDir/<file-id>/content. Lookups always go
 * through the opaque internal ID, which is format-validated to make path
 * traversal impossible. Client-controlled names are display names only.
 */
class FileStorage(
    baseDir: File,
    private val tempDir: File,
    private val media: MediaExporter? = null
) {

    constructor(context: Context) : this(
        File(context.filesDir, "received"),
        File(context.filesDir, "temporary"),
        if (MediaExporter.available) MediaExporter(context.contentResolver) else null
    )

    private val baseDir: File = baseDir
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val lock = Any()

    /** Internal IDs: hex/UUID-like, no separators — traversal impossible. */
    private fun safeIdDir(id: String): File? {
        if (!id.matches(Regex("[A-Za-z0-9_-]{4,64}"))) return null
        val dir = File(baseDir, id)
        return if (dir.isDirectory) dir else null
    }

    private fun readMeta(dir: File): FileMetadata? {
        val mf = File(dir, "metadata.json")
        if (!mf.isFile) return null
        return runCatching {
            json.decodeFromString(FileMetadata.serializer(), mf.readText())
        }.getOrNull()
    }

    private fun writeMeta(dir: File, meta: FileMetadata) {
        File(dir, "metadata.json").writeText(json.encodeToString(FileMetadata.serializer(), meta))
    }

    fun newTempFile(id: String): File {
        tempDir.mkdirs()
        return File(tempDir, "$id.part")
    }

    /**
     * Publishes a finished upload: content + metadata. Tries the public
     * Documents/ScreenDrop folder first; any MediaStore failure falls back
     * to app-private storage so an upload never fails at the last step.
     */
    fun finalize(temp: File, id: String, meta: FileMetadata) {
        synchronized(lock) {
            val dir = File(baseDir, id).apply { mkdirs() }
            var stored = meta
            var published = false
            if (media != null) {
                published = try {
                    val uri = media.insert(meta.displayName, meta.mimeType, temp)
                    stored = meta.copy(mediaUri = uri.toString())
                    temp.delete()
                    true
                } catch (t: Throwable) {
                    false // fall back to private storage below
                }
            }
            if (!published) {
                val content = File(dir, "content")
                if (!temp.renameTo(content)) {
                    temp.copyTo(content, overwrite = true)
                    temp.delete()
                }
            }
            writeMeta(dir, stored)
        }
    }

    /** Moves legacy private-storage files into Documents/ScreenDrop. */
    fun migrateLegacy(onMigrated: (FileMetadata) -> Unit = {}): Boolean {
        var any = false
        synchronized(lock) {
            if (media == null) return false
            baseDir.listFiles { f -> f.isDirectory }?.forEach { dir ->
                val meta = readMeta(dir) ?: return@forEach
                if (meta.mediaUri != null) return@forEach
                val content = File(dir, "content")
                if (!content.isFile) return@forEach
                try {
                    val uri = media.insert(meta.displayName, meta.mimeType, content)
                    content.delete()
                    writeMeta(dir, meta.copy(mediaUri = uri.toString()))
                    onMigrated(meta.copy(mediaUri = uri.toString()))
                    any = true
                } catch (t: Throwable) {
                    // keep the private copy; try again next launch
                }
            }
        }
        return any
    }

    fun list(): List<FileMetadata> = synchronized(lock) {
        baseDir.listFiles { f -> f.isDirectory }
            ?.mapNotNull { dir -> readMeta(dir) }
            ?.sortedByDescending { it.receivedAt }
            ?: emptyList()
    }

    fun delete(id: String): Boolean = synchronized(lock) {
        val dir = safeIdDir(id) ?: return false
        val uriStr = readMeta(dir)?.mediaUri
        if (uriStr != null) {
            runCatching { media?.delete(Uri.parse(uriStr)) }
        }
        dir.deleteRecursively()
    }

    fun openFile(id: String): File? {
        val content = safeIdDir(id)?.let { File(it, "content") } ?: return null
        return if (content.isFile) content else null
    }

    /** Removes stale .part files (crashed/interrupted uploads). */
    fun cleanupTemp(olderThanMs: Long, now: Long = System.currentTimeMillis()) {
        tempDir.listFiles()?.forEach { f ->
            if (f.isFile && f.name.endsWith(".part") && f.lastModified() < now - olderThanMs) {
                f.delete()
            }
        }
    }
}
