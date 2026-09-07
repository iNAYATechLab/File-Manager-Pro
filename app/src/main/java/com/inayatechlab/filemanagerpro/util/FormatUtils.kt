package com.inayatechlab.filemanagerpro.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FormatUtils {

    fun formatSize(bytes: Long): String {
        if (bytes < 0) return "—"
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
        val gb = mb / 1024.0
        if (gb < 1024) return String.format(Locale.US, "%.2f GB", gb)
        return String.format(Locale.US, "%.2f TB", gb / 1024.0)
    }

    fun formatDate(millis: Long): String {
        if (millis <= 0) return "—"
        return SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(millis))
    }

    fun formatPercent(used: Long, total: Long): Int {
        if (total <= 0) return 0
        return ((used * 100) / total).toInt().coerceIn(0, 100)
    }
}
