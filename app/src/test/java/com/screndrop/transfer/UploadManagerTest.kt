package com.screendrop.transfer

import com.screendrop.model.FileMetadata
import com.screendrop.storage.FileStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.security.MessageDigest

class UploadManagerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun newManager(maxSize: Long = 100_000_000L): Pair<FileStorage, UploadManager> {
        val storage = FileStorage(tmp.newFolder(), tmp.newFolder())
        return storage to UploadManager(storage, UploadManager.UploadConfig(maxFileSizeBytes = maxSize))
    }

    private fun upload(
        m: UploadManager,
        bytes: ByteArray,
        name: String,
        sender: String? = null
    ): FileMetadata {
        val input = ByteArrayInputStream(bytes)
        return m.handleFileUpload(
            read = { buf -> input.read(buf) },
            close = { input.close() },
            originalName = name,
            senderName = sender
        )
    }

    @Test
    fun `stores file with correct sha256 and metadata`() {
        val (storage, m) = newManager()
        val meta = upload(m, "HELLO".toByteArray(), "test.txt", "Иван")

        val expected = MessageDigest.getInstance("SHA-256")
            .digest("HELLO".toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals(expected, meta.sha256)
        assertEquals(5L, meta.sizeBytes)
        assertEquals("Иван", meta.senderName)
        assertEquals("test.txt", meta.displayName)
        assertEquals("text/plain", meta.mimeType)
        assertEquals("HELLO", String(storage.openFile(meta.id)!!.readBytes()))
        assertEquals(1, storage.list().size)
    }

    @Test
    fun `rejects unsupported extension`() {
        val (_, m) = newManager()
        try {
            upload(m, "x".toByteArray(), "evil.exe")
            fail("expected UploadException")
        } catch (e: UploadManager.UploadException) {
            assertEquals(415, e.statusCode)
            assertEquals("UNSUPPORTED_TYPE", e.code)
        }
    }

    @Test
    fun `rejects oversized file while streaming`() {
        val (_, m) = newManager(maxSize = 10)
        try {
            upload(m, ByteArray(11) { 7 }, "big.txt")
            fail("expected UploadException")
        } catch (e: UploadManager.UploadException) {
            assertEquals(413, e.statusCode)
            assertEquals("FILE_TOO_LARGE", e.code)
        }
    }

    @Test
    fun `rejects empty file`() {
        val (_, m) = newManager()
        try {
            upload(m, ByteArray(0), "empty.txt")
            fail("expected UploadException")
        } catch (e: UploadManager.UploadException) {
            assertEquals(400, e.statusCode)
            assertEquals("EMPTY_FILE", e.code)
        }
    }

    @Test
    fun `same display name never overwrites previous file`() {
        val (storage, m) = newManager()
        val a = upload(m, "A".toByteArray(), "doc.txt")
        val b = upload(m, "B".toByteArray(), "doc.txt")
        assertNotEquals(a.id, b.id)
        assertEquals("A", String(storage.openFile(a.id)!!.readBytes()))
        assertEquals("B", String(storage.openFile(b.id)!!.readBytes()))
        assertEquals(2, storage.list().size)
    }

    @Test
    fun `oversized upload leaves no temporary file`() {
        val (storage, m) = newManager(maxSize = 4)
        try {
            upload(m, ByteArray(10) { 1 }, "big.txt")
            fail("expected UploadException")
        } catch (ignored: UploadManager.UploadException) {
        }
        // Nothing finalized
        assertTrue(storage.list().isEmpty())
    }

    @Test
    fun `sender name is trimmed and capped`() {
        val (_, m) = newManager()
        val meta = upload(m, "x".toByteArray(), "a.txt", "   Иван   ")
        assertEquals("Иван", meta.senderName)
        val long = "x".repeat(100)
        val meta2 = upload(m, "x".toByteArray(), "b.txt", long)
        assertEquals(64, meta2.senderName!!.length)
    }

    @Test
    fun `filename is sanitized into display name`() {
        val (_, m) = newManager()
        val meta = upload(m, "x".toByteArray(), "../../../etc/passwd.txt")
        assertEquals("passwd.txt", meta.displayName)
        assertEquals("../../../etc/passwd.txt", meta.originalName)
    }
}
