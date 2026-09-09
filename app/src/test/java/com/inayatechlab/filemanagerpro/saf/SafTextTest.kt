package com.inayatechlab.filemanagerpro.saf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure SAF identifier/label helpers (#21). */
class SafTextTest {

    @Test
    fun splitDocId_handlesVolumeAndPath() {
        assertEquals("primary" to "Android/data", SafText.splitDocId("primary:Android/data"))
        assertEquals("primary" to "Download/a%20b", SafText.splitDocId("primary:Download/a%20b"))
        assertEquals("1234-ABCD" to "DCIM", SafText.splitDocId("1234-ABCD:DCIM"))
    }

    @Test
    fun splitDocId_withoutPath() {
        assertEquals("primary" to null, SafText.splitDocId("primary"))
        assertEquals("primary" to null, SafText.splitDocId("primary:"))
    }

    @Test
    fun volumeLabel_mapsPrimary() {
        assertEquals("Internal storage", SafText.volumeLabel("primary"))
        assertEquals("Internal storage", SafText.volumeLabel("PRIMARY"))
        assertEquals("1234-ABCD", SafText.volumeLabel("1234-ABCD"))
    }

    @Test
    fun folderName_returnsLastSegment() {
        assertEquals("data", SafText.folderName("primary:Android/data"))
        assertEquals("obb", SafText.folderName("primary:Android/obb"))
        assertNull(SafText.folderName("primary"))
        assertEquals("DCIM", SafText.folderName("primary:DCIM"))
    }

    @Test
    fun subtitleOf_combinesPathAndVolume() {
        assertEquals("Android/data • Internal storage", SafText.subtitleOf("primary:Android/data"))
        assertEquals("Internal storage", SafText.subtitleOf("primary"))
    }

    @Test
    fun sanitizeLabel_stripsControlCharsAndTrims() {
        assertEquals("My Folder", SafText.sanitizeLabel("  My\u0000Folder\n"))
        assertEquals("", SafText.sanitizeLabel("   "))
        val long = SafText.sanitizeLabel(
            "ToolongnameToolongnameToolongnameToolongnameToolongnameX", 50
        )
        assertTrue(long.length == 50)
        assertTrue(long.endsWith("…"))
        assertTrue(long.startsWith("ToolongnameToolongnameToolongname"))
        // ASCII control chars are always removed even in the middle
        assertEquals("A B C", SafText.sanitizeLabel("A\u0001B\u007FC"))
    }

    @Test
    fun uniqueChildName_insertsBeforeExtension() {
        val existing = setOf("report.pdf", "report (1).pdf")
        assertEquals("report (2).pdf", SafText.uniqueChildName("report.pdf", existing))
        assertEquals("photo.jpg", SafText.uniqueChildName("photo.jpg", setOf("a.txt")))
        assertEquals("pic (1).jpg", SafText.uniqueChildName("pic.jpg", setOf("pic.jpg")))
        assertEquals("folder (1)", SafText.uniqueChildName("folder", setOf("folder")))
        assertEquals("noext (2)", SafText.uniqueChildName("noext", setOf("noext", "noext (1)")))
    }
}
