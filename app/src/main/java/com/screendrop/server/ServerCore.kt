package com.screendrop.server

import com.screendrop.session.SessionManager
import com.screendrop.storage.FileStorage
import com.screendrop.transfer.UploadManager

/** Shared dependencies of the HTTP server (Android-independent, JVM-testable). */
class ServerCore(
    val sessions: SessionManager,
    val storage: FileStorage,
    val uploads: UploadManager,
    val assetReader: (String) -> ByteArray,
    val version: String = "1.0.0"
)
