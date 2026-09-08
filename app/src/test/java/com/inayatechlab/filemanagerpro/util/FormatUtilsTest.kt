package com.inayatechlab.filemanagerpro.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatUtilsTest {

    @Test
    fun formatSize_bytes() {
        assertEquals("0 B", FormatUtils.formatSize(0))
        assertEquals("512 B", FormatUtils.formatSize(512))
        assertEquals("1023 B", FormatUtils.formatSize(1023))
    }

    @Test
    fun formatSize_kilobytes() {
        assertEquals("1.0 KB", FormatUtils.formatSize(1024))
        assertEquals("1.5 KB", FormatUtils.formatSize(1536))
    }

    @Test
    fun formatSize_megabytesAndGigabytes() {
        assertEquals("1.0 MB", FormatUtils.formatSize(1024L * 1024))
        assertEquals("1.00 GB", FormatUtils.formatSize(1024L * 1024 * 1024))
    }

    @Test
    fun formatSize_negativeIsPlaceholder() {
        assertEquals("\u2014", FormatUtils.formatSize(-1))
    }

    @Test
    fun formatPercent_bounds() {
        assertEquals(50, FormatUtils.formatPercent(50, 100))
        assertEquals(100, FormatUtils.formatPercent(150, 100))
        assertEquals(0, FormatUtils.formatPercent(0, 100))
        assertEquals(0, FormatUtils.formatPercent(0, 0))
    }
}
