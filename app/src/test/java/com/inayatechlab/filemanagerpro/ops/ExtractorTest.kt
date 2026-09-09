package com.inayatechlab.filemanagerpro.ops

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Extraction tests for zip/tar/tar.gz plus format dispatch (#18). */
class ExtractorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun seed(dir: File) {
        File(dir, "one.txt").writeText("hello-one")
        val sub = File(dir, "sub")
        sub.mkdirs()
        File(sub, "two.txt").writeText("hello-two")
    }

    @Test
    fun supportsKnownAndRejectsUnknown() {
        assertTrue(Extractor.isSupportedName("a.zip"))
        assertTrue(Extractor.isSupportedName("a.tar"))
        assertTrue(Extractor.isSupportedName("a.tar.gz"))
        assertTrue(Extractor.isSupportedName("A.TGZ"))
        assertTrue(Extractor.isSupportedName("a.7z"))
        assertTrue(Extractor.isSupportedName("a.rar"))
        assertFalse(Extractor.isSupportedName("a.xyz"))
        assertFalse(Extractor.isSupportedName("notes.txt"))
    }

    @Test
    fun zipExtractsFilesAndFolders() = runBlocking {
        val dir = tmp.newFolder("seed")
        seed(dir)
        val zip = File(tmp.root, "fixture.zip")
        ZipOutputStream(FileOutputStream(zip)).use { zos ->
            zos.putNextEntry(ZipEntry("one.txt"))
            File(dir, "one.txt").inputStream().use { it.copyTo(zos) }
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("sub/"))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("sub/two.txt"))
            File(dir, "sub/two.txt").inputStream().use { it.copyTo(zos) }
            zos.closeEntry()
        }

        val out = Extractor.extract(zip)
        assertEquals("fixture", out.name)
        assertEquals("hello-one", File(out, "one.txt").readText())
        assertEquals("hello-two", File(File(out, "sub"), "two.txt").readText())
    }

    @Test
    fun tarExtractsFilesAndFolders() = runBlocking {
        val dir = tmp.newFolder("seed2")
        seed(dir)
        val tar = File(tmp.root, "fixture.tar")
        TarArchiveOutputStream(FileOutputStream(tar)).use { tos ->
            fun add(f: File, parent: String?) {
                val entryName = if (parent == null) f.name else "$parent/${f.name}"
                if (f.isDirectory) {
                    tos.putArchiveEntry(TarArchiveEntry(entryName + "/"))
                    tos.closeArchiveEntry()
                    f.listFiles()?.forEach { add(it, entryName) }
                } else {
                    val entry = TarArchiveEntry(entryName)
                    entry.size = f.length()
                    tos.putArchiveEntry(entry)
                    FileInputStream(f).use { it.copyTo(tos) }
                    tos.closeArchiveEntry()
                }
            }
            add(File(dir, "one.txt"), null)
            add(File(dir, "sub"), null)
        }

        val out = Extractor.extract(tar)
        assertEquals("fixture", out.name)
        assertEquals("hello-one", File(out, "one.txt").readText())
        assertEquals("hello-two", File(File(out, "sub"), "two.txt").readText())
    }

    @Test
    fun tarGzExtracts() = runBlocking {
        val dir = tmp.newFolder("seed3")
        seed(dir)
        val tgz = File(tmp.root, "fixture.tar.gz")
        GZIPOutputStream(FileOutputStream(tgz)).use { gz ->
            TarArchiveOutputStream(gz).use { tos ->
                val entry = TarArchiveEntry("one.txt")
                entry.size = File(dir, "one.txt").length()
                tos.putArchiveEntry(entry)
                File(dir, "one.txt").inputStream().use { it.copyTo(tos) }
                tos.closeArchiveEntry()
            }
        }

        val out = Extractor.extract(tgz)
        assertEquals("fixture", out.name)
        assertEquals("hello-one", File(out, "one.txt").readText())
    }

    @Test
    fun unknownExtensionThrowsUnsupported() = runBlocking {
        val bogus = File(tmp.root, "file.xyz").apply { writeText("nope") }
        try {
            Extractor.extract(bogus)
            assertTrue("should have thrown", false)
        } catch (e: Extractor.ExtractException) {
            assertEquals(Extractor.Failure.UNSUPPORTED, e.failure)
        }
    }

    @Test
    fun zipSlipEntriesAreRejected() = runBlocking {
        val evil = File(tmp.root, "evil.zip")
        ZipOutputStream(FileOutputStream(evil)).use { zos ->
            zos.putNextEntry(ZipEntry("../escape.txt"))
            zos.write("bad".toByteArray())
            zos.closeEntry()
        }
        try {
            Extractor.extract(evil)
            assertTrue("should have thrown", false)
        } catch (e: Extractor.ExtractException) {
            assertTrue(e.message!!.contains("Unsafe"))
        }
        assertFalse(File(tmp.root.parentFile, "escape.txt").exists())
    }
}
