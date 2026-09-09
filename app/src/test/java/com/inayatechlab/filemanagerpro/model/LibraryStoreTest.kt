package com.inayatechlab.filemanagerpro.model

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** LibraryStore round-trips, ordering, cap and exotic names. */
class LibraryStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun storeDir(): File {
        val d = tmp.newFolder("lib")
        d.mkdirs()
        return d
    }

    private fun entry(base: File, name: String, dir: Boolean = false, size: Long = 0L, mtime: Long = 0L): FileEntry =
        FileEntry(name, File(base, name).path, dir, size, mtime)

    @Test
    fun encodeDecode_roundTrip() {
        val line = LibraryStore.encode("a b|c\n xyz?&/fīle.txt", true, 42L, 123L)
        val item = LibraryStore.decode(line)!!
        assertEquals("a b|c\n xyz?&/fīle.txt", item.path)
        assertTrue(item.isDir)
        assertEquals(42L, item.size)
        assertEquals(123L, item.lastModified)
    }

    @Test
    fun saveLoad_preservesOrderAndFields() {
        val dir = tmp.newFolder("io")
        val f = File(dir, "f.lst")
        val items = listOf(
            LibraryStore.Item("/a/one.txt", false, 10L, 1L),
            LibraryStore.Item("/a/Dir", true, 0L, 2L)
        )
        LibraryStore.save(f, items)
        assertEquals(items, LibraryStore.load(f))
    }

    @Test
    fun corruptLines_areSkipped() {
        val dir = tmp.newFolder("io2")
        val f = File(dir, "f.lst")
        f.writeText("garbage\n!notbase64|0|1|2\n")
        assertEquals(0, LibraryStore.load(f).size)
    }

    @Test
    fun recents_dedupeMoveToFront_andRemove() {
        val dir = storeDir()
        val a = entry(dir, "1.txt", mtime = 1L)
        val b = entry(dir, "2.txt", mtime = 2L)
        LibraryStore.addRecent(dir, a)
        LibraryStore.addRecent(dir, b)
        LibraryStore.addRecent(dir, a) // dedupe → front again
        assertEquals(listOf("1.txt", "2.txt"), LibraryStore.recents(dir).map { it.name })

        LibraryStore.removeRecent(dir, a.path)
        assertEquals(listOf("2.txt"), LibraryStore.recents(dir).map { it.name })
        // directories are ignored
        LibraryStore.addRecent(dir, entry(dir, "Folder", dir = true))
        assertEquals(1, LibraryStore.recents(dir).size)
    }

    @Test
    fun favorites_addRemove_isFavorite() {
        val dir = storeDir()
        val f = entry(dir, "a.txt", size = 1L)
        val d = entry(dir, "Folder", dir = true)
        assertFalse(LibraryStore.isFavorite(dir, f.path))
        LibraryStore.addFavorite(dir, f)
        LibraryStore.addFavorite(dir, d)
        assertTrue(LibraryStore.isFavorite(dir, f.path))
        assertEquals(2, LibraryStore.favorites(dir).size)
        LibraryStore.removeFavorite(dir, f.path)
        assertFalse(LibraryStore.isFavorite(dir, f.path))
        assertEquals(listOf("Folder"), LibraryStore.favorites(dir).map { it.name })
    }

    @Test
    fun favorites_cappedAtMax_keepNewestFirst() {
        val dir = storeDir()
        for (i in 0 until LibraryStore.MAX_FAVORITES + 10) {
            LibraryStore.addFavorite(dir, entry(dir, "f$i.txt"))
        }
        assertEquals(LibraryStore.MAX_FAVORITES, LibraryStore.favorites(dir).size)
        assertEquals("f${LibraryStore.MAX_FAVORITES + 9}.txt", LibraryStore.favorites(dir).first().name)
    }

    @Test
    fun toFileEntry_reconstructsFileEntry() {
        val item = LibraryStore.Item("/s/emulated/0/Docs/x.txt", false, 7L, 9L)
        val fe = LibraryStore.toFileEntry(item)
        assertEquals("x.txt", fe.name)
        assertEquals("/s/emulated/0/Docs/x.txt", fe.path)
        assertFalse(fe.isDir)
        assertEquals(7L, fe.size)
        assertEquals(9L, fe.lastModified)
    }
}
