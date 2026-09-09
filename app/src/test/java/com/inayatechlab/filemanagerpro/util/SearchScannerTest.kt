package com.inayatechlab.filemanagerpro.util

import com.inayatechlab.filemanagerpro.model.FileEntry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Name-search filtering + scoping tests (#15). */
class SearchScannerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun buildTree(): File {
        val root = tmp.newFolder("tree")
        File(root, "annual_report.pdf").writeText("x")
        File(root, "photo.jpg").writeText("x")
        val docs = File(root, "Docs")
        docs.mkdirs()
        File(docs, "annual_notes.txt").writeText("x")
        val pictures = File(docs, "Pictures")
        pictures.mkdirs()
        File(pictures, "holiday_photo.png").writeText("x")
        File(root, ".annual_hidden").writeText("x")
        return root
    }

    private fun collect(roots: List<File>, query: String, type: Scanner.SearchType): List<FileEntry> =
        runBlocking {
            val out = mutableListOf<FileEntry>()
            Scanner.search(roots, query, type) { out.add(it) }
            out
        }

    @Test
    fun findsFilesAndFoldersCaseInsensitively() {
        val root = buildTree()
        val hits = collect(listOf(root), "ANNUAL", Scanner.SearchType.ALL)

        assertEquals(2, hits.size)
        assertTrue(hits.any { it.name == "annual_report.pdf" && !it.isDir })
        assertTrue(hits.any { it.name == "annual_notes.txt" && !it.isDir })
    }

    @Test
    fun filesFilterReturnsOnlyFiles() {
        val root = buildTree()
        val hits = collect(listOf(root), "o", Scanner.SearchType.FILES)

        assertTrue(hits.isNotEmpty())
        assertTrue(hits.all { !it.isDir })
        assertEquals(4, hits.size) // photo.jpg + annual_report.pdf + annual_notes.txt + holiday_photo.png
    }

    @Test
    fun foldersFilterReturnsOnlyDirectories() {
        val root = buildTree()
        val hits = collect(listOf(root), "pic", Scanner.SearchType.FOLDERS)

        assertEquals(1, hits.size)
        assertTrue(hits[0].isDir)
        assertEquals("Pictures", hits[0].name)
    }

    @Test
    fun multipleRootsDeduplicate() {
        val root = buildTree()
        // Same file reachable from two roots must be reported once.
        val sub = File(root, "Docs")
        val hits = collect(listOf(root, sub), "annual", Scanner.SearchType.ALL)

        assertEquals(2, hits.size)
    }

    @Test
    fun hiddenFilesAreNotReturned() {
        val root = buildTree()
        val hits = collect(listOf(root), "annual", Scanner.SearchType.ALL)

        assertTrue(hits.none { it.name == ".annual_hidden" })
    }

    @Test
    fun emptyQueryReturnsNothing() {
        val root = buildTree()
        val hits = collect(listOf(root), "   ", Scanner.SearchType.ALL)
        assertTrue(hits.isEmpty())
    }
}
