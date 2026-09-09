package com.inayatechlab.filemanagerpro.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** TextFiles extension/encoding logic (#basic-core). */
class TextFilesTest {

    @Test
    fun isTextFile_byExtension() {
        assertTrue(TextFiles.isTextFile("notes.txt"))
        assertTrue(TextFiles.isTextFile("README.md"))
        assertTrue(TextFiles.isTextFile("build.gradle.kts"))
        assertTrue(TextFiles.isTextFile("LOG.TXT")) // case-insensitive
        assertFalse(TextFiles.isTextFile("photo.jpg"))
        assertFalse(TextFiles.isTextFile("archive.zip"))
        assertFalse(TextFiles.isTextFile("noext"))
    }

    @Test
    fun detect_utf8Bom() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "হ্যালো".toByteArray(Charsets.UTF_8)
        val (charset, label) = TextFiles.detect(bytes)
        assertEquals("UTF-8", label)
        val res = TextFiles.decodeHead(bytes)
        assertEquals("হ্যালো", res.text)
        assertFalse(res.truncated)
    }

    @Test
    fun decode_validUtf8_andFallbackWindows1252() {
        val utf8 = TextFiles.decodeHead("café".toByteArray(Charsets.UTF_8))
        assertEquals("café", utf8.text)
        assertEquals("UTF-8", utf8.charsetLabel)

        // Invalid UTF-8 (lone trailing 0xE9) falls back to legacy single byte
        val legacy = TextFiles.decodeHead(
            byteArrayOf('c'.code.toByte(), 'a'.code.toByte(), 'f'.code.toByte(), 0xE9.toByte())
        )
        assertEquals("café", legacy.text)
        assertEquals("Windows-1252", legacy.charsetLabel)
    }

    @Test
    fun decode_utf16Bom() {
        val le = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "A".toByteArray(Charsets.UTF_16LE)
        assertEquals("A", TextFiles.decodeHead(le).text)
        assertEquals("UTF-16 LE", TextFiles.decodeHead(le).charsetLabel)
    }

    @Test
    fun decodeHead_truncatesLongText() {
        val long = "x".repeat(TextFiles.MAX_CHARS + 5000)
        val res = TextFiles.decodeHead(long.toByteArray(Charsets.UTF_8))
        assertTrue(res.truncated)
        assertTrue(res.text.length <= TextFiles.MAX_CHARS + 2)
        assertTrue(res.text.endsWith("…"))
    }

    @Test
    fun readHead_bounded() {
        val dir = java.nio.file.Files.createTempDirectory("txt").toFile()
        val big = java.io.File(dir, "big.txt")
        java.io.RandomAccessFile(big, "rw").use { it.setLength(TextFiles.MAX_READ_BYTES + 100L) }
        assertEquals(TextFiles.MAX_READ_BYTES, TextFiles.readHead(big).size)
        big.delete(); dir.delete()
    }
}
