package com.inayatechlab.filemanagerpro.model

import com.inayatechlab.filemanagerpro.util.RecentStore

/**
 * A persisted entry for Favorites / Recent screens.
 * [kind] is only meaningful inside [RecentStore].
 */
data class RecentEntry(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val time: Long,
    val kind: RecentStore.Kind = RecentStore.Kind.OPENED,
    val size: Long = 0L
) {
    val file: java.io.File
        get() = java.io.File(path)

    /** Plain UI entry (no timestamps resolved) for icon/category lookups. */
    fun asFileEntry() = FileEntry(name, path, isDir)
}
