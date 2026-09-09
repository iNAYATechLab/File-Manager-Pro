package com.inayatechlab.filemanagerpro.model

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Trash store round-trips, restore/purge/empty and auto-cleanup (#basic-core). */
class TrashStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun storeDir(): File = TrashStore.storeDir(tmp.newFolder("files"))

    private fun newFile(dir: File, name: String, content: String = "x"): File {
        val f = File(dir, name)
        f.writeText(content)
        return f
    }

    private fun newFolder(dir: File, name: String): File {
        val f = File(dir, name)
        f.mkdirs()
        return f
    }

    @Test
    fun moveToTrash_renamesToHiddenSibling_andRecords() {
        val dir = tmp.newFolder()
        val file = newFile(dir, "notes.txt", "hello")
        val store = storeDir()

        assertNull(TrashStore.moveToTrash(store, file))
        assertFalse(file.exists())
        val items = TrashStore.list(store)
        assertEquals(1, items.size)
        assertEquals("notes.txt", items[0].name)
        assertEquals(file.path, items[0].origPath)
        assertFalse(items[0].isDir)
        assertEquals(5L, items[0].size)
        val hidden = items[0].location()
        assertNotNull(hidden)
        assertTrue(hidden!!.isFile)
        assertTrue(hidden.name.startsWith(TrashStore.HIDDEN_PREFIX))
    }

    @Test
    fun restore_putsFileBack_andClearsRow() {
        val dir = tmp.newFolder()
        val file = newFile(dir, "a.txt")
        val store = storeDir()
        TrashStore.moveToTrash(store, file)

        val items = TrashStore.list(store)
        assertNull(TrashStore.restore(store, items[0]))

        assertTrue(file.isFile)
        assertEquals("x", file.readText())
        assertTrue(TrashStore.list(store).isEmpty())
    }

    @Test
    fun restore_failsWhenOriginalExistsAgain() {
        val dir = tmp.newFolder()
        val store = storeDir()
        TrashStore.moveToTrash(store, newFile(dir, "a.txt"))
        val items = TrashStore.list(store)

        newFile(dir, "a.txt", "new") // recreated in the meantime
        assertNotNull(TrashStore.restore(store, items[0]))
        // the trashed copy is still in the trash, not overwritten
        assertEquals(1, TrashStore.list(store).size)
    }

    @Test
    fun folderTrash_roundTrip() {
        val dir = tmp.newFolder()
        val folder = newFolder(dir, "docs")
        newFile(folder, "inner.txt")
        val store = storeDir()

        assertNull(TrashStore.moveToTrash(store, folder))
        assertFalse(folder.exists())
        val items = TrashStore.list(store)
        assertEquals(1, items.size)
        assertTrue(items[0].isDir)

        assertNull(TrashStore.restore(store, items[0]))
        assertTrue(folder.isDirectory)
        assertTrue(File(folder, "inner.txt").isFile)
    }

    @Test
    fun sameNameTwice_getsDistinctIds() {
        val dir = tmp.newFolder()
        val store = storeDir()
        val a = newFile(dir, "a.txt")
        TrashStore.moveToTrash(store, a)
        val first = TrashStore.list(store)[0]
        assertNull(TrashStore.restore(store, first))

        val b = newFile(dir, "a.txt", "again")
        TrashStore.moveToTrash(store, b)
        val second = TrashStore.list(store)[0]
        assertTrue(first.id != second.id)
        assertEquals(1, TrashStore.list(store).size)
    }

    @Test
    fun purge_deletesHiddenAndRow() {
        val dir = tmp.newFolder()
        val file = newFile(dir, "gone.txt")
        val store = storeDir()
        TrashStore.moveToTrash(store, file)

        val items = TrashStore.list(store)
        assertNull(TrashStore.purge(store, items[0]))
        assertTrue(TrashStore.list(store).isEmpty())
        // nothing remains in the source dir either
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test
    fun empty_removesEverything() {
        val dir = tmp.newFolder()
        val store = storeDir()
        TrashStore.moveToTrash(store, newFile(dir, "1.txt"))
        TrashStore.moveToTrash(store, newFile(dir, "2.txt"))

        assertEquals(0, TrashStore.empty(store))
        assertTrue(TrashStore.list(store).isEmpty())
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test
    fun list_prunesExpiredEntries() {
        val dir = tmp.newFolder()
        val store = storeDir()
        TrashStore.moveToTrash(store, newFile(dir, "old.txt"))

        val now = System.currentTimeMillis()
        val expired = TrashStore.list(store, now + TrashStore.MAX_AGE_MILLIS + 1000)
        assertTrue(expired.isEmpty())
        // hidden file was physically deleted
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test
    fun list_dropsOrphanRows() {
        val dir = tmp.newFolder()
        val store = storeDir()
        val file = newFile(dir, "x.txt")
        TrashStore.moveToTrash(store, file)
        val items = TrashStore.list(store)
        // simulate the hidden file disappearing (cleaned by another tool)
        items[0].location()!!.delete()

        assertTrue(TrashStore.list(store).isEmpty())
    }

    @Test
    fun move_evictsOldestPastCap() {
        val dir = tmp.newFolder()
        val store = storeDir()
        for (i in 0 until TrashStore.MAX_ITEMS + 5) {
            assertNull(TrashStore.moveToTrash(store, newFile(dir, "f$i.txt")))
        }
        val items = TrashStore.list(store)
        assertEquals(TrashStore.MAX_ITEMS, items.size)
        // evicted = physically deleted; newest 200 remain
        assertFalse(File(dir, "f0.txt").exists())
        assertTrue(items.any { it.name == "f${TrashStore.MAX_ITEMS + 4}.txt" })
    }

    @Test
    fun exoticNames_surviveRoundTrip() {
        val dir = tmp.newFolder()
        val store = storeDir()
        val file = newFile(dir, "we|ird\nname.txt", "z")
        assertNull(TrashStore.moveToTrash(store, file))
        val items = TrashStore.list(store)
        assertEquals(file.path, items[0].origPath)
        assertNull(TrashStore.restore(store, items[0]))
        assertTrue(file.isFile)
    }

    @Test
    fun corruptLines_areIgnored() {
        val store = storeDir()
        val index = TrashStore.indexFile(store)
        index.parentFile.mkdirs()
        index.writeText("garbage\nbroken|line\n|\n")
        assertTrue(TrashStore.list(store).isEmpty())
        // and a valid line is still parsed next to corruption
        val dir = tmp.newFolder()
        val file = newFile(dir, "ok.txt")
        assertNull(TrashStore.moveToTrash(store, file))
        index.appendText("garbage2\n")
        val items = TrashStore.list(store)
        assertEquals(1, items.size)
        assertEquals("ok.txt", items[0].name)
    }

    @Test
    fun moveToTrash_rejectsMissingFile() {
        val dir = tmp.newFolder()
        val store = storeDir()
        assertNotNull(TrashStore.moveToTrash(store, File(dir, "nope.txt")))
        assertTrue(TrashStore.list(store).isEmpty())
    }

    @Test
    fun storeDir_isUnderFilesDir() {
        val files = tmp.newFolder("f")
        assertEquals(File(files, "trash"), TrashStore.storeDir(files))
        assertEquals(File(files, "trash/index.lst"), TrashStore.indexFile(TrashStore.storeDir(files)))
    }
}
