package com.inayatechlab.filemanagerpro.model

import java.io.File

/**
 * Lightweight representation of a filesystem entry used across the UI.
 */
data class FileEntry(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long = 0L,
    val lastModified: Long = 0L,
    val childCount: Int = -1
) {
    val extension: String
        get() = if (isDir) "" else path.substringAfterLast('.', "").lowercase()

    val file: File
        get() = File(path)

    fun withChildCount(count: Int) = copy(childCount = count)
}

/** A clickable breadcrumb segment. */
data class PathCrumb(val label: String, val path: String)

/** Global (in-process) clipboard for copy / cut operations. */
object ClipboardBus {
    var entries: List<FileEntry> = emptyList()
    var isCut: Boolean = false

    val isActive: Boolean get() = entries.isNotEmpty()
}
