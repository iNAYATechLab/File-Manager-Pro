package com.inayatechlab.filemanagerpro.saf

/**
 * Pure helpers for Storage Access Framework tree identifiers and labels.
 *
 * A tree document id looks like `primary:Android/data` (volume before the
 * colon, provider-relative path after it). These functions deliberately avoid
 * Android types so they are unit-testable on the JVM.
 */
object SafText {

    /** Split `primary:Android/data` into ("primary", "Android/data"). */
    fun splitDocId(docId: String): Pair<String, String?> {
        val idx = docId.indexOf(':')
        if (idx < 0) return docId to null
        val volume = docId.substring(0, idx)
        val path = docId.substring(idx + 1).takeIf { it.isNotEmpty() }
        return volume to path
    }

    /** Human volume name: "primary" is the emulated internal storage. */
    fun volumeLabel(volume: String): String =
        if (volume.equals("primary", ignoreCase = true)) "Internal storage" else volume

    /** Last path segment of a tree doc id, or null when the id has no path. */
    fun folderName(docId: String): String? {
        val path = splitDocId(docId).second ?: return null
        val trimmed = path.trimEnd('/')
        if (trimmed.isEmpty()) return null
        val last = trimmed.substringAfterLast('/')
        return last.ifEmpty { null }
    }

    /** e.g. "Android/data • Internal storage" — subtitle under a grant row. */
    fun subtitleOf(docId: String): String {
        val (volume, path) = splitDocId(docId)
        val base = volumeLabel(volume)
        return if (path.isNullOrEmpty()) base else "$path • $base"
    }

    /** Sanitizes a user-facing label (no control chars, bounded length). */
    fun sanitizeLabel(label: String, max: Int = 48): String {
        val cleaned = label
            .replace(Regex("[\\u0000-\\u001F\\u007F]"), " ")
            .trim()
        return if (cleaned.length <= max) cleaned else cleaned.take(max - 1).trimEnd() + "…"
    }

    /**
     * Unique child name for copy conflicts: "a.txt", "a (1).txt", "a (2).txt"…
     * Insertion point is before the extension so "report.pdf (1).pdf" never happens.
     */
    fun uniqueChildName(desired: String, existing: Collection<String>): String {
        if (desired !in existing) return desired
        val dot = desired.lastIndexOf('.')
        val base = if (dot > 0) desired.substring(0, dot) else desired
        val ext = if (dot > 0) desired.substring(dot) else ""
        var n = 1
        while (true) {
            val candidate = "$base ($n)$ext"
            if (candidate !in existing) return candidate
            n++
        }
    }
}
