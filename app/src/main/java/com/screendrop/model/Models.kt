package com.screendrop.model

import kotlinx.serialization.Serializable

/** Lifecycle of the receiving server, shown in the TV UI. */
enum class ServerState {
    STOPPED,
    STARTING,
    WAITING_NETWORK,
    CHECKING_NETWORK,
    READY,
    RECEIVING,
    ERROR
}

/** Result of network probing by [com.screendrop.network.NetworkMonitor]. */
data class NetworkStatus(
    val connected: Boolean = false,
    val ipv4: String? = null
)

/** Persisted metadata of one received file. Never exposes client-controlled paths. */
@Serializable
data class FileMetadata(
    val id: String,
    val originalName: String,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String,
    val sha256: String,
    val senderName: String? = null,
    val receivedAt: Long,
    val storagePath: String,
    /** Public MediaStore URI for API 29+ devices; null = app-private storage. */
    val mediaUri: String? = null
)

/** Public API projection of a file (no storage internals). */
@Serializable
data class FileEntry(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val sha256: String,
    val senderName: String? = null,
    val receivedAt: String
)

fun FileMetadata.toEntry(): FileEntry = FileEntry(
    id = id,
    name = displayName,
    sizeBytes = sizeBytes,
    mimeType = mimeType,
    sha256 = sha256,
    senderName = senderName,
    receivedAt = java.time.Instant.ofEpochMilli(receivedAt).toString()
)

@Serializable
data class HealthResponse(val status: String, val server: String, val version: String)

@Serializable
data class SessionResponse(
    val active: Boolean,
    val expiresAt: String,
    val maxFileSizeBytes: Long,
    val allowedExtensions: List<String>
)

@Serializable
data class UploadResponse(val success: Boolean, val file: FileEntry)

@Serializable
data class FilesResponse(val files: List<FileEntry>)

@Serializable
data class SuccessResponse(val success: Boolean, val status: String? = null)

@Serializable
data class ErrorResponse(val error: ErrorBody) {
    @Serializable
    data class ErrorBody(val code: String, val message: String)

    companion object {
        fun Error(code: String, message: String) = ErrorResponse(ErrorBody(code, message))
    }
}
