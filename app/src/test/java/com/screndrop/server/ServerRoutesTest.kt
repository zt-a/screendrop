package com.screendrop.server

import com.screendrop.storage.FileStorage
import com.screendrop.transfer.UploadManager
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Integration tests: full HTTP surface on the JVM (Ktor test host),
 * mirroring TC-002..TC-009, TC-016 from docs/TESTING.md.
 */
class ServerRoutesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun makeCore(): ServerCore {
        val storage = FileStorage(tmp.newFolder(), tmp.newFolder())
        val sessions = com.screendrop.session.SessionManager()
        val uploads = UploadManager(storage)
        return ServerCore(sessions, storage, uploads, assetReader = { "test-asset".toByteArray() })
    }

    private fun ApplicationTestBuilder.install(core: ServerCore) {
        application { screendropModule(core) }
    }

    private fun filePart(name: String, bytes: ByteArray, mime: String = "text/plain") =
        MultiPartFormDataContent(
            formData {
                append("senderName", "Иван")
                append("file", bytes, Headers.build {
                    append(HttpHeaders.ContentDisposition, "form-data; name=\"file\"; filename=\"$name\"")
                    append(HttpHeaders.ContentType, mime)
                })
            }
        )

    @Test
    fun `health endpoint is public`() = testApplication {
        val core = makeCore()
        install(core)
        val r = client.get("/api/health")
        assertEquals(HttpStatusCode.OK, r.status)
        assertTrue(r.bodyAsText().contains("\"server\":\"ScreenDrop\""))
    }

    @Test
    fun `index page is served`() = testApplication {
        val core = makeCore()
        install(core)
        val r = client.get("/")
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals("test-asset", r.bodyAsText())
    }

    @Test
    fun `session endpoint requires token`() = testApplication {
        val core = makeCore()
        install(core)
        val r = client.get("/api/session")
        assertEquals(HttpStatusCode.Unauthorized, r.status)
        val r2 = client.get("/api/session") { header(HttpHeaders.Authorization, "Bearer wrong") }
        assertEquals(HttpStatusCode.Unauthorized, r2.status)
    }

    @Test
    fun `upload stores file and returns 201`() = testApplication {
        val core = makeCore()
        install(core)
        val session = core.sessions.createNew()
        val r = client.post("/api/upload") {
            header(HttpHeaders.Authorization, "Bearer ${session.token}")
            setBody(filePart("test.txt", "HELLO".toByteArray()))
        }
        assertEquals(HttpStatusCode.Created, r.status)
        assertTrue(r.bodyAsText().contains("\"name\":\"test.txt\""))
        val stored = core.storage.list()
        assertEquals(1, stored.size)
        assertEquals("Иван", stored[0].senderName)
        assertEquals("HELLO", String(core.storage.openFile(stored[0].id)!!.readBytes()))
    }

    @Test
    fun `upload with invalid token is rejected`() = testApplication {
        val core = makeCore()
        install(core)
        core.sessions.createNew()
        val r = client.post("/api/upload") {
            header(HttpHeaders.Authorization, "Bearer invalid-token")
            setBody(filePart("test.txt", "HELLO".toByteArray()))
        }
        assertEquals(HttpStatusCode.Unauthorized, r.status)
        assertTrue(core.storage.list().isEmpty())
    }

    @Test
    fun `closed session rejects upload`() = testApplication {
        val core = makeCore()
        install(core)
        val session = core.sessions.createNew()
        val token = session.token
        core.sessions.invalidateCurrent()
        val r = client.post("/api/upload") {
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(filePart("test.txt", "HELLO".toByteArray()))
        }
        assertEquals(HttpStatusCode.Unauthorized, r.status)
        assertTrue(core.storage.list().isEmpty())
    }

    @Test
    fun `files list and delete`() = testApplication {
        val core = makeCore()
        install(core)
        val session = core.sessions.createNew()
        val auth = HttpHeaders.Authorization to "Bearer ${session.token}"

        client.post("/api/upload") {
            header(auth.first, auth.second)
            setBody(filePart("a.txt", "AAA".toByteArray()))
        }.let { assertEquals(HttpStatusCode.Created, it.status) }

        val list = client.get("/api/files") { header(auth.first, auth.second) }
        assertEquals(HttpStatusCode.OK, list.status)
        assertTrue(list.bodyAsText().contains("a.txt"))

        val id = core.storage.list().first().id
        val del = client.delete("/api/files/$id") { header(auth.first, auth.second) }
        assertEquals(HttpStatusCode.OK, del.status)
        assertTrue(core.storage.list().isEmpty())

        val del2 = client.delete("/api/files/$id") { header(auth.first, auth.second) }
        assertEquals(HttpStatusCode.NotFound, del2.status)
    }

    @Test
    fun `path traversal in delete id rejected`() = testApplication {
        val core = makeCore()
        install(core)
        val session = core.sessions.createNew()
        val r = client.delete("/api/files/..%2Fetc") {
            header(HttpHeaders.Authorization, "Bearer ${session.token}")
        }
        assertEquals(HttpStatusCode.NotFound, r.status)
    }

    @Test
    fun `session close invalidates token`() = testApplication {
        val core = makeCore()
        install(core)
        val session = core.sessions.createNew()
        val auth = HttpHeaders.Authorization to "Bearer ${session.token}"

        val close = client.post("/api/session/close") { header(auth.first, auth.second) }
        assertEquals(HttpStatusCode.OK, close.status)
        assertTrue(close.bodyAsText().contains("\"status\":\"closed\""))

        val after = client.get("/api/session") { header(auth.first, auth.second) }
        assertEquals(HttpStatusCode.Unauthorized, after.status)
    }

    @Test
    fun `unsupported extension rejected with 415`() = testApplication {
        val core = makeCore()
        install(core)
        val session = core.sessions.createNew()
        val r = client.post("/api/upload") {
            header(HttpHeaders.Authorization, "Bearer ${session.token}")
            setBody(filePart("evil.exe", "MZ".toByteArray()))
        }
        assertEquals(HttpStatusCode.UnsupportedMediaType, r.status)
        assertTrue(r.bodyAsText().contains("UNSUPPORTED_TYPE"))
    }

    @Test
    fun `oversized file rejected with 413`() = testApplication {
        val storage = FileStorage(tmp.newFolder(), tmp.newFolder())
        val uploads = UploadManager(
            storage,
            UploadManager.UploadConfig(maxFileSizeBytes = 5)
        )
        val core = ServerCore(
            com.screendrop.session.SessionManager(), storage, uploads,
            assetReader = { "x".toByteArray() }
        )
        install(core)
        val session = core.sessions.createNew()
        val r = client.post("/api/upload") {
            header(HttpHeaders.Authorization, "Bearer ${session.token}")
            setBody(filePart("big.txt", ByteArray(100)))
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, r.status)
        assertTrue(core.storage.list().isEmpty())
    }
}
