package com.screendrop.transfer

import com.screendrop.model.FileMetadata
import com.screendrop.storage.FileStorage
import com.screendrop.storage.FilenameSanitizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Upload Manager: enforces limits, streams multipart file content into a
 * temporary file while computing SHA-256 over the same bytes, then atomically
 * finalizes the file. Nothing is ever fully buffered in RAM.
 */
class UploadManager(
    private val storage: FileStorage,
    val config: UploadConfig = UploadConfig()
) {
    data class UploadConfig(
        val maxFileSizeBytes: Long = 100L * 1024 * 1024,
        val maxConcurrentUploads: Int = 3,
        val allowedExtensions: Set<String> = setOf(
            "pdf", "ppt", "pptx", "doc", "docx", "txt", "jpg", "jpeg", "png", "mp4"
        ),
        val maxSenderNameLength: Int = 64
    )

    class UploadException(
        val statusCode: Int,
        val code: String,
        message: String
    ) : Exception(message)

    private val slots = java.util.concurrent.Semaphore(config.maxConcurrentUploads)
    private val random = SecureRandom()
    private val _activeUploads = MutableStateFlow(0)
    val activeUploads: StateFlow<Int> = _activeUploads.asStateFlow()

    val allowedExtensions: List<String> get() = config.allowedExtensions.sorted()

    /**
     * Streams one file to storage. [read] fills [ByteArray] chunks and returns
     * the number of bytes read (<= 0 means EOF); [close] releases the source
     * (Ktor multipart Input / test stream).
     */
    fun handleFileUpload(
        read: (ByteArray) -> Int,
        close: () -> Unit,
        originalName: String?,
        senderName: String?
    ): FileMetadata {
        if (!slots.tryAcquire()) {
            throw UploadException(429, "TOO_MANY_UPLOADS", "Too many concurrent uploads")
        }
        _activeUploads.update { it + 1 }
        try {
            return process(read, close, originalName, senderName)
        } finally {
            _activeUploads.update { it - 1 }
            slots.release()
        }
    }

    private fun process(
        read: (ByteArray) -> Int,
        close: () -> Unit,
        originalName: String?,
        senderName: String?
    ): FileMetadata {
        val displayName = FilenameSanitizer.sanitize(originalName)
        val ext = FilenameSanitizer.extension(displayName)
        if (ext !in config.allowedExtensions) {
            throw UploadException(415, "UNSUPPORTED_TYPE", "File extension .$ext is not allowed")
        }
        val sender = senderName
            ?.trim()
            ?.take(config.maxSenderNameLength)
            ?.takeIf { it.isNotEmpty() }

        val id = newId()
        val temp = storage.newTempFile(id)
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        try {
            FileOutputStream(temp).use { out ->
                try {
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val readCount = read(buffer)
                        if (readCount <= 0) break
                        size += readCount
                        if (size > config.maxFileSizeBytes) {
                            throw UploadException(413, "FILE_TOO_LARGE", "File exceeds the size limit")
                        }
                        out.write(buffer, 0, readCount)
                        digest.update(buffer, 0, readCount)
                    }
                } finally {
                    close()
                }
            }
            if (size == 0L) {
                throw UploadException(400, "EMPTY_FILE", "Uploaded file is empty")
            }
            val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
            val meta = FileMetadata(
                id = id,
                originalName = originalName ?: displayName,
                displayName = displayName,
                sizeBytes = size,
                mimeType = mimeFor(ext),
                sha256 = sha256,
                senderName = sender,
                receivedAt = System.currentTimeMillis(),
                storagePath = "received/$id/content"
            )
            storage.finalize(temp, id, meta)
            return meta
        } catch (t: Throwable) {
            temp.delete()
            throw t
        }
    }

    private fun newId(): String =
        ByteArray(16).also(random::nextBytes).joinToString("") { "%02x".format(it) }

    companion object {
        fun mimeFor(ext: String): String = when (ext) {
            "pdf" -> "application/pdf"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "txt" -> "text/plain"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "mp4" -> "video/mp4"
            else -> "application/octet-stream"
        }
    }
}
