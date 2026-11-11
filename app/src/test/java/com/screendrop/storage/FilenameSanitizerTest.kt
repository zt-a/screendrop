package com.screendrop.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class FilenameSanitizerTest {

    @Test
    fun `strips path traversal`() {
        assertEquals("passwd.txt", FilenameSanitizer.sanitize("../../../etc/passwd.txt"))
        assertEquals("secret.pdf", FilenameSanitizer.sanitize("../../../data/secret.pdf"))
    }

    @Test
    fun `strips windows paths`() {
        assertEquals("file.pdf", FilenameSanitizer.sanitize("C:\\Users\\bob\\file.pdf"))
    }

    @Test
    fun `removes control chars`() {
        assertEquals("name.pdf", FilenameSanitizer.sanitize("na\nme.pdf"))
        assertEquals("name.pdf", FilenameSanitizer.sanitize("na\u0000me.pdf"))
    }

    @Test
    fun `removes forbidden separator chars`() {
        assertEquals("abcdef.txt", FilenameSanitizer.sanitize("a:b*c?d<e>f.txt"))
    }

    @Test
    fun `falls back for blank input`() {
        assertEquals("file", FilenameSanitizer.sanitize(null))
        assertEquals("file", FilenameSanitizer.sanitize(""))
        assertEquals("file", FilenameSanitizer.sanitize("   "))
        assertEquals("file", FilenameSanitizer.sanitize("///"))
    }

    @Test
    fun `caps length at 255`() {
        val long = "a".repeat(1000) + ".pdf"
        assertEquals(255, FilenameSanitizer.sanitize(long).length)
    }

    @Test
    fun `extension is lowercased`() {
        assertEquals("png", FilenameSanitizer.extension("Photo.PNG"))
        assertEquals("", FilenameSanitizer.extension("noext"))
        assertEquals("txt", FilenameSanitizer.extension("archive.tar.txt"))
    }
}
