package com.screendrop.server

import com.screendrop.model.ErrorResponse
import com.screendrop.model.FilesResponse
import com.screendrop.model.HealthResponse
import com.screendrop.session.SessionManager
import com.screendrop.model.SessionResponse
import com.screendrop.model.SuccessResponse
import com.screendrop.model.UploadResponse
import com.screendrop.model.toEntry
import com.screendrop.session.SessionRejection
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
import io.ktor.http.content.PartData
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.header
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.core.readAvailable
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * All ScreenDrop HTTP routes, served by the embedded Ktor server on the
 * Android device; also installed directly by JVM integration tests.
 *
 * Public: GET / (web sender page), GET /api/health.
 * Everything else requires a valid session token (Authorization: Bearer).
 */
fun Application.screendropModule(core: ServerCore) {
    install(ContentNegotiation) {
        json(Json { encodeDefaults = true })
    }

    routing {
        get("/") {
            call.respondBytes(
                core.assetReader("web/index.html"),
                ContentType.Text.Html.withParameter("charset", "utf-8")
            )
        }
        get("/styles.css") {
            call.respondBytes(core.assetReader("web/styles.css"), ContentType.Text.CSS)
        }
        get("/app.js") {
            call.respondBytes(core.assetReader("web/app.js"), ContentType.Text.JavaScript)
        }

        get("/api/health") {
            call.respond(HealthResponse(status = "ready", server = "ScreenDrop", version = core.version))
        }

        get("/api/session") {
            val session = call.authorize(core) ?: return@get
            call.respond(
                SessionResponse(
                    active = true,
                    expiresAt = Instant.ofEpochMilli(session.lastActivity + core.sessions.idleTimeoutMs).toString(),
                    maxFileSizeBytes = core.uploads.config.maxFileSizeBytes,
                    allowedExtensions = core.uploads.allowedExtensions
                )
            )
        }

        post("/api/upload") {
            call.authorize(core) ?: return@post
            var senderName: String? = null
            var uploaded: com.screendrop.model.FileMetadata? = null
            try {
                val multipart = call.receiveMultipart()
                while (true) {
                    val part = multipart.readPart() ?: break
                    try {
                        when (part) {
                            is PartData.FormItem -> {
                                if (part.name == "senderName") senderName = part.value
                            }
                            is PartData.FileItem -> {
                                if (uploaded != null) {
                                    throw UploadReject("MULTIPLE_FILES", "Send one file per request")
                                }
                                val input = part.provider()
                                uploaded = core.uploads.handleFileUpload(
                                    read = { buffer -> input.readAvailable(buffer) },
                                    close = { input.close() },
                                    originalName = part.originalFileName,
                                    senderName = senderName
                                )
                            }
                            else -> Unit
                        }
                    } finally {
                        part.dispose()
                    }
                }
            } catch (e: com.screendrop.transfer.UploadManager.UploadException) {
                call.respond(HttpStatusCode.fromValue(e.statusCode), ErrorResponse.Error(e.code, e.message ?: "Upload failed"))
                return@post
            } catch (e: UploadReject) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse.Error(e.code, e.message))
                return@post
            }
            val meta = uploaded
            if (meta == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse.Error("NO_FILE", "No file part found"))
            } else {
                call.respond(HttpStatusCode.Created, UploadResponse(success = true, file = meta.toEntry()))
            }
        }

        get("/api/files") {
            call.authorize(core) ?: return@get
            call.respond(FilesResponse(files = core.storage.list().map { it.toEntry() }))
        }

        delete("/api/files/{id}") {
            call.authorize(core) ?: return@delete
            val id = call.parameters["id"] ?: ""
            if (core.storage.delete(id)) {
                call.respond(SuccessResponse(success = true))
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse.Error("NOT_FOUND", "File not found"))
            }
        }

        post("/api/session/close") {
            call.authorize(core) ?: return@post
            core.sessions.invalidateCurrent()
            call.respond(SuccessResponse(success = true, status = "closed"))
        }
    }
}

private class UploadReject(val code: String, override val message: String) : Exception(message)

/** Bearer token check; responds 401/403 itself and returns null on failure. */
private suspend fun ApplicationCall.authorize(core: ServerCore): SessionManager.Session? {
    val header = request.header(HttpHeaders.Authorization)
    val token = if (header != null && header.startsWith("Bearer ", ignoreCase = true)) {
        header.substring(7).trim()
    } else null
    val (session, rejection) = core.sessions.check(token)
    if (session != null) return session
    val status = if (rejection == SessionRejection.CLOSED) HttpStatusCode.Forbidden else HttpStatusCode.Unauthorized
    val code = if (rejection == SessionRejection.CLOSED) "SESSION_CLOSED" else "UNAUTHORIZED"
    respond(status, ErrorResponse.Error(code, "Session token is missing, invalid or closed"))
    return null
}
