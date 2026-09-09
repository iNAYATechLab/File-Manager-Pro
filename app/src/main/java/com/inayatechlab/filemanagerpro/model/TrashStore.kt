package com.inayatechlab.filemanagerpro.model

import java.io.File
import java.security.MessageDigest
import java.util.Base64

/**
 * App-managed Trash / Recycle Bin (pure JVM logic; unit-testable).
 *
 * Trashing is an instant same-directory rename: the item is renamed to a
 * hidden `.fmptrash_<id>` sibling, and the index (app-private, one line per
 * item) remembers where it came from so it can be restored. Keeping the item
 * on its original volume avoids cross-filesystem copies.
 *
 * Lines: `id|base64url(origPath)|isDir|size|trashedAt`
 */
object TrashStore {

    const val MAX_ITEMS = 200
    const val MAX_AGE_MILLIS: Long = 30L * 24 * 60 * 60 * 1000 // 30 days
    const val HIDDEN_PREFIX = ".fmptrash_"

    data class Item(
        val id: String,
        val origPath: String,
        val isDir: Boolean,
        val size: Long,
        val trashedAt: Long
    ) {
        val name: String get() = origPath.substringAfterLast('/')

        /** The hidden sibling file inside the original parent folder. */
        fun location(): File? {
            val parent = File(origPath).parentFile ?: return null
            return File(parent, HIDDEN_PREFIX + id)
        }
    }

    /** App-private directory holding the trash index. */
    fun storeDir(filesDir: File): File = File(filesDir, "trash")

    fun indexFile(storeDir: File): File = File(storeDir, "index.lst")

    // ---------------------------------------------------------------- actions

    /**
     * Renames [file] to a hidden sibling and records it. Returns null on
     * success or an error message that the UI can turn into a generic
     * localized notice.
     */
    fun moveToTrash(storeDir: File, file: File): String? {
        val isDir = file.isDirectory
        val isFile = file.isFile
        if (!isDir && !isFile) return "Not found"
        val parent = file.parentFile ?: return "Cannot trash"
        if (file.name.startsWith(HIDDEN_PREFIX)) return "Cannot trash"
        val id = newId(file.path)
        val hidden = File(parent, HIDDEN_PREFIX + id)
        if (hidden.exists()) return "Cannot trash"
        val size = if (isDir) 0L else file.length()
        if (!file.renameTo(hidden)) return "Cannot trash"

        val items = load(indexFile(storeDir)).toMutableList()
        items.add(0, Item(id, file.path, isDir, size, System.currentTimeMillis()))
        saveEvicting(storeDir, items)
        return null
    }

    /** Moves the hidden sibling back to its original path. Error message on failure. */
    fun restore(storeDir: File, item: Item): String? {
        val hidden = item.location() ?: return "Cannot restore"
        if (!hidden.exists()) return "Cannot restore"
        val orig = File(item.origPath)
        if (orig.exists()) return "Exists"
        val parent = orig.parentFile ?: return "Cannot restore"
        if (!parent.isDirectory) return "Cannot restore"
        if (!hidden.renameTo(orig)) return "Cannot restore"
        removeLine(indexFile(storeDir), item.id)
        return null
    }

    /** Permanently deletes the hidden sibling. Error message on failure. */
    fun purge(storeDir: File, item: Item): String? {
        val hidden = item.location() ?: run {
            removeLine(indexFile(storeDir), item.id)
            return null
        }
        if (!hidden.exists()) {
            removeLine(indexFile(storeDir), item.id)
            return null
        }
        val deleted = if (item.isDir) hidden.deleteRecursively() else hidden.delete()
        if (!deleted) return "Cannot purge"
        removeLine(indexFile(storeDir), item.id)
        return null
    }

    /** Permanently deletes every trashed item. Returns how many failed. */
    fun empty(storeDir: File): Int {
        var failed = 0
        for (item in load(indexFile(storeDir))) {
            if (purge(storeDir, item) != null) failed++
        }
        return failed
    }

    /**
     * Current trash contents (newest first). Orphaned rows (hidden file gone)
     * and entries older than [MAX_AGE_MILLIS] are cleaned up as a side effect.
     */
    fun list(storeDir: File, now: Long = System.currentTimeMillis()): List<Item> {
        val items = load(indexFile(storeDir)).toMutableList()
        val kept = items.filter { item ->
            val expired = now - item.trashedAt > MAX_AGE_MILLIS
            val hidden = item.location()
            val gone = hidden == null || !hidden.exists()
            if (expired || gone) {
                if (expired && hidden != null && hidden.exists()) {
                    if (item.isDir) hidden.deleteRecursively() else hidden.delete()
                }
                false
            } else {
                true
            }
        }
        if (kept.size != items.size) save(indexFile(storeDir), kept)
        return kept.sortedByDescending { it.trashedAt }
    }

    // ---------------------------------------------------------------- io

    private fun newId(path: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest(path.toByteArray(Charsets.UTF_8))
            .take(8)
            .joinToString("") { "%02x".format(it) }
        val ts = java.lang.Long.toHexString(System.currentTimeMillis())
        return "$ts-$digest"
    }

    private fun encode(item: Item): String =
        item.id + "|" +
            Base64.getUrlEncoder().withoutPadding().encodeToString(item.origPath.toByteArray(Charsets.UTF_8)) +
            "|" + (if (item.isDir) 1 else 0) + "|" + item.size + "|" + item.trashedAt

    private fun decode(line: String): Item? {
        val parts = line.split('|')
        if (parts.size != 5 || parts[0].isBlank()) return null
        val path = try {
            String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
        } catch (e: Exception) {
            return null
        }
        val isDir = parts[2] == "1"
        val size = parts[3].toLongOrNull() ?: 0L
        val trashedAt = parts[4].toLongOrNull() ?: return null
        return Item(parts[0], path, isDir, size, trashedAt)
    }

    private fun load(file: File): List<Item> {
        if (!file.isFile) return emptyList()
        val items = mutableListOf<Item>()
        return try {
            file.forEachLine { line ->
                if (line.isNotBlank()) decode(line)?.let { items.add(it) }
            }
            items
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Rewrites the index, dropping the oldest items once the cap is exceeded. */
    private fun saveEvicting(storeDir: File, items: List<Item>) {
        val file = indexFile(storeDir)
        if (items.size > MAX_ITEMS) {
            // Physically delete the evicted hidden files, keep the rest.
            val kept = items.take(MAX_ITEMS)
            for (evicted in items.drop(MAX_ITEMS)) {
                val hidden = evicted.location() ?: continue
                if (evicted.isDir) hidden.deleteRecursively() else hidden.delete()
            }
            save(file, kept)
        } else {
            save(file, items)
        }
    }

    private fun removeLine(file: File, id: String) {
        val rest = load(file).filterNot { it.id == id }
        save(file, rest)
    }

    private fun save(file: File, items: List<Item>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.bufferedWriter(Charsets.UTF_8).use { w ->
            for (item in items) {
                w.write(encode(item))
                w.newLine()
            }
        }
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}
