package com.inayatechlab.filemanagerpro.util

import com.inayatechlab.filemanagerpro.model.FileEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class FileCatTest {

    private fun entry(name: String, isDir: Boolean = false) =
        FileEntry(name = name, path = "/tmp/$name", isDir = isDir, size = 100, lastModified = 0)

    @Test
    fun imageExtensions_areCaseInsensitive() {
        assertEquals(FileCat.IMAGE, FileCat.of(entry("photo.JPG")))
        assertEquals(FileCat.IMAGE, FileCat.of(entry("anim.webp")))
        assertEquals(FileCat.IMAGE, FileCat.of(entry("scan.heic")))
    }

    @Test
    fun mediaAndArchiveTypes() {
        assertEquals(FileCat.VIDEO, FileCat.of(entry("movie.mp4")))
        assertEquals(FileCat.AUDIO, FileCat.of(entry("song.flac")))
        assertEquals(FileCat.ARCHIVE, FileCat.of(entry("bundle.zip")))
        assertEquals(FileCat.ARCHIVE, FileCat.of(entry("backup.7z")))
    }

    @Test
    fun documentAndAppTypes() {
        assertEquals(FileCat.PDF, FileCat.of(entry("report.pdf")))
        assertEquals(FileCat.DOC, FileCat.of(entry("sheet.xlsx")))
        assertEquals(FileCat.TEXT, FileCat.of(entry("notes.md")))
        assertEquals(FileCat.APK, FileCat.of(entry("app.apk")))
    }

    @Test
    fun directoriesAreFolders() {
        assertEquals(FileCat.FOLDER, FileCat.of(entry("folder", isDir = true)))
    }

    @Test
    fun unknownExtensionIsGeneric() {
        assertEquals(FileCat.GENERIC, FileCat.of(entry("mystery.xyz")))
        assertEquals(FileCat.GENERIC, FileCat.of(entry("noextension")))
    }

    @Test
    fun ofExtension_handlesCaseAndNull() {
        assertEquals(FileCat.DOC, FileCat.ofExtension("XLSX"))
        assertEquals(FileCat.IMAGE, FileCat.ofExtension("Png"))
        assertEquals(null, FileCat.ofExtension("zzz"))
    }
}
