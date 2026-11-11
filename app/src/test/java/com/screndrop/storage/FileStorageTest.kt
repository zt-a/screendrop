package com.screendrop.storage

import com.screendrop.model.FileMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileStorageTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun newStorage(): FileStorage =
        FileStorage(tmp.newFolder(), tmp.newFolder())

    private fun meta(id: String, name: String = "$id.txt") = FileMetadata(
        id = id,
        originalName = name,
        displayName = name,
        sizeBytes = 5,
        mimeType = "text/plain",
        sha256 = "00",
        senderName = "Test",
        receivedAt = 1000,
        storagePath = "received/$id/content"
    )

    @Test
    fun `finalize then list and open`() {
        val storage = newStorage()
        val m = meta("abcd1234abcd1234")
        val temp = storage.newTempFile(m.id)
        temp.writeBytes("HELLO".toByteArray())
        storage.finalize(temp, m.id, m)

        val list = storage.list()
        assertEquals(1, list.size)
        assertEquals(m.id, list[0].id)
        assertEquals("HELLO", String(storage.openFile(m.id)!!.readBytes()))
    }

    @Test
    fun `list sorted newest first`() {
        val storage = newStorage()
        listOf(
            meta("1111111111111111", "old.txt").copy(receivedAt = 1),
            meta("2222222222222222", "new.txt").copy(receivedAt = 2)
        ).forEach { m ->
            val t = storage.newTempFile(m.id)
            t.writeBytes(byteArrayOf(1))
            storage.finalize(t, m.id, m)
        }
        assertEquals("new.txt", storage.list().first().displayName)
    }

    @Test
    fun `delete removes file and metadata`() {
        val storage = newStorage()
        val m = meta("abcd1234abcd1234")
        val temp = storage.newTempFile(m.id)
        temp.writeBytes("X".toByteArray())
        storage.finalize(temp, m.id, m)

        assertTrue(storage.delete(m.id))
        assertTrue(storage.list().isEmpty())
        assertNull(storage.openFile(m.id))
        assertFalse(storage.delete(m.id))
    }

    @Test
    fun `path traversal in id is rejected`() {
        val storage = newStorage()
        assertFalse(storage.delete("../evil"))
        assertFalse(storage.delete("a/b"))
        assertNull(storage.openFile("../../etc/passwd"))
        assertFalse(storage.delete("safe1234"))
    }

    @Test
    fun `cleanup removes only stale part files`() {
        val storage = newStorage()
        val fresh = storage.newTempFile("fresh1234567890ab")
        fresh.writeBytes(byteArrayOf(1))
        val old = storage.newTempFile("old1234567890abcd")
        old.writeBytes(byteArrayOf(1))
        old.setLastModified(System.currentTimeMillis() - 100_000)

        storage.cleanupTemp(olderThanMs = 50_000)

        assertTrue(fresh.exists())
        assertFalse(old.exists())
    }
}
