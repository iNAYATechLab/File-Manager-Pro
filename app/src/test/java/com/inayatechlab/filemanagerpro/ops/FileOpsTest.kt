package com.inayatechlab.filemanagerpro.ops

import com.inayatechlab.filemanagerpro.model.FileEntry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileOpsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun entryOf(f: File, isDir: Boolean = false) =
        FileEntry(f.name, f.canonicalPath, isDir, if (isDir) 0 else f.length(), f.lastModified())

    @Test
    fun uniqueTarget_appendsCounterBeforeExtension() {
        val dir = tmp.newFolder("u")
        File(dir, "report.txt").writeText("x")

        val t1 = FileOps.uniqueTarget(dir, "report.txt")
        assertEquals("report (1).txt", t1.name)
        t1.writeText("x")

        val t2 = FileOps.uniqueTarget(dir, "report.txt")
        assertEquals("report (2).txt", t2.name)
    }

    @Test
    fun uniqueTarget_noExtensionName() {
        val dir = tmp.newFolder("u2")
        File(dir, "notes").writeText("x")
        assertEquals("notes (1)", FileOps.uniqueTarget(dir, "notes").name)
    }

    @Test
    fun createFolder_rejectsInvalidAndDuplicates() = runBlocking {
        val dir = tmp.newFolder("cf")

        assertNull(FileOps.createFolder(dir, "New Folder"))
        assertTrue(File(dir, "New Folder").isDirectory)

        assertNotNull("duplicate folder should fail", FileOps.createFolder(dir, "New Folder"))
        assertNotNull("invalid name should fail", FileOps.createFolder(dir, "a/b"))
        assertNotNull("blank name should fail", FileOps.createFolder(dir, "   "))
    }

    @Test
    fun rename_succeedsAndRejectsCollision() = runBlocking {
        val dir = tmp.newFolder("rn")
        File(dir, "a.txt").writeText("1")
        File(dir, "c.txt").writeText("2")

        assertNull(FileOps.rename(dir, "a.txt", "b.txt"))
        assertTrue(File(dir, "b.txt").exists())
        assertFalse(File(dir, "a.txt").exists())

        assertNotNull("collision should fail", FileOps.rename(dir, "c.txt", "b.txt"))
        assertTrue(File(dir, "c.txt").exists())
    }

    @Test
    fun copyOrMove_copyKeepsSource() = runBlocking {
        val src = tmp.newFolder("s1")
        val dst = tmp.newFolder("d1")
        val f = File(src, "keep.txt")
        f.writeText("data")

        val result = FileOps.copyOrMove(listOf(entryOf(f)), dst, cut = false)

        assertEquals(0, result.failed)
        assertTrue(f.exists())
        assertEquals("data", File(dst, "keep.txt").readText())
    }

    @Test
    fun copyOrMove_moveRemovesSource() = runBlocking {
        val src = tmp.newFolder("s2")
        val dst = tmp.newFolder("d2")
        val f = File(src, "move.txt")
        f.writeText("data")

        val result = FileOps.copyOrMove(listOf(entryOf(f)), dst, cut = true)

        assertEquals(0, result.failed)
        assertFalse(f.exists())
        assertEquals("data", File(dst, "move.txt").readText())
    }

    @Test
    fun copyOrMove_movesDirectoryRecursively() = runBlocking {
        val src = tmp.newFolder("s3")
        val dst = tmp.newFolder("d3")
        val sub = File(src, "tree")
        sub.mkdirs()
        File(sub, "leaf.txt").writeText("leaf")

        val result = FileOps.copyOrMove(listOf(entryOf(sub, isDir = true)), dst, cut = true)

        assertEquals(0, result.failed)
        assertFalse(sub.exists())
        assertEquals("leaf", File(File(dst, "tree"), "leaf.txt").readText())
    }

    @Test
    fun delete_removesRecursively() = runBlocking {
        val dir = tmp.newFolder("del")
        val sub = File(dir, "tree")
        sub.mkdirs()
        File(sub, "leaf.txt").writeText("leaf")

        val result = FileOps.delete(listOf(entryOf(sub, isDir = true)))

        assertEquals(0, result.failed)
        assertFalse(sub.exists())
    }

    @Test
    fun zipThenExtract_roundtripsFilesAndFolders() = runBlocking {
        val dir = tmp.newFolder("zip")
        File(dir, "one.txt").writeText("hello")
        val sub = File(dir, "sub")
        sub.mkdirs()
        File(sub, "two.txt").writeText("world")

        val zip = FileOps.zip(
            listOf(
                entryOf(File(dir, "one.txt")),
                entryOf(sub, isDir = true)
            )
        )

        assertTrue(zip.name.endsWith(".zip"))
        assertTrue(zip.exists())

        val out = FileOps.extract(zip)

        assertTrue(out.isDirectory)
        assertEquals("hello", File(out, "one.txt").readText())
        assertEquals("world", File(File(out, "sub"), "two.txt").readText())
    }

    // ------------------------------------------------ conflict handling (#17)

    @Test
    fun copyOrMove_defaultConflictKeepsBoth() = runBlocking {
        val src = tmp.newFolder("cb1")
        val dst = tmp.newFolder("cb2")
        val f = File(src, "same.txt")
        f.writeText("original-src")
        File(dst, "same.txt").writeText("existing-dest")

        val result = FileOps.copyOrMove(listOf(entryOf(f)), dst, cut = false)

        assertEquals(0, result.failed)
        assertEquals("existing-dest", File(dst, "same.txt").readText())
        assertTrue(File(dst, "same (1).txt").exists())
        assertEquals(1, result.done)
    }

    @Test
    fun copyOrMove_overwriteReplacesExisting() = runBlocking {
        val src = tmp.newFolder("ow1")
        val dst = tmp.newFolder("ow2")
        val f = File(src, "same.txt")
        f.writeText("new-content")
        File(dst, "same.txt").writeText("old-content")

        val result = FileOps.copyOrMove(
            listOf(entryOf(f)), dst, cut = false,
            conflictHandler = { ConflictPolicy.OVERWRITE }
        )

        assertEquals(0, result.failed)
        assertEquals(1, result.replaced)
        assertEquals("new-content", File(dst, "same.txt").readText())
        assertFalse(File(dst, "same (1).txt").exists())
    }

    @Test
    fun copyOrMove_skipLeavesExisting() = runBlocking {
        val src = tmp.newFolder("sk1")
        val dst = tmp.newFolder("sk2")
        val f = File(src, "same.txt")
        f.writeText("new-content")
        File(dst, "same.txt").writeText("old-content")

        val result = FileOps.copyOrMove(
            listOf(entryOf(f)), dst, cut = false,
            conflictHandler = { ConflictPolicy.SKIP }
        )

        assertEquals(0, result.done)
        assertEquals(1, result.skipped)
        assertEquals(0, result.failed)
        assertEquals("old-content", File(dst, "same.txt").readText())
    }

    @Test
    fun copyOrMove_nullHandlerCancelsWholeOperation() = runBlocking {
        val src = tmp.newFolder("cn1")
        val dst = tmp.newFolder("cn2")
        val first = File(src, "first.txt").apply { writeText("1") }
        val clash = File(src, "clash.txt").apply { writeText("2") }
        File(dst, "clash.txt").writeText("existing")
        var calls = 0

        val result = FileOps.copyOrMove(
            listOf(entryOf(first), entryOf(clash)),
            dst, cut = false,
            conflictHandler = {
                calls++
                if (it == "clash.txt") null else ConflictPolicy.KEEP_BOTH
            }
        )

        assertTrue(result.cancelled)
        assertEquals(1, calls) // stops before the conflicting entry
        assertTrue(File(dst, "first.txt").exists())
        assertFalse(File(dst, "clash (1).txt").exists())
    }
}
