package com.inayatechlab.filemanagerpro.model

import java.io.File
import java.util.Base64

/**
 * Persistent favorites & recents (pure JVM logic over plain files in
 * app-private storage; unit-testable with a TemporaryFolder).
 *
 * Lines: `base64url(path)|isDir|size|mtime` — the path is base64-encoded so
 * exotic file names (newlines, pipes) cannot corrupt the store.
 */
object LibraryStore {

    const val MAX_RECENTS = 150
    const val MAX_FAVORITES = 1000

    data class Item(
        val path: String,
        val isDir: Boolean,
        val size: Long,
        val lastModified: Long
    ) {
        val name: String get() = path.substringAfterLast('/')
    }

    fun toFileEntry(item: Item): FileEntry {
        val f = File(item.path)
        return FileEntry(
            name = f.name,
            path = item.path,
            isDir = item.isDir,
            size = item.size,
            lastModified = item.lastModified
        )
    }

    fun favoritesFile(dir: File): File = File(dir, "favorites.lst")
    fun recentsFile(dir: File): File = File(dir, "recents.lst")

    /** App-private directory holding the two lists. */
    fun storeDir(filesDir: File): File = File(filesDir, "library")

    // ---------------------------------------------------------------- favorites

    fun isFavorite(storeDir: File, path: String): Boolean =
        load(favoritesFile(storeDir)).any { it.path == path }

    fun addFavorite(storeDir: File, entry: FileEntry) {
        val items = load(favoritesFile(storeDir)).toMutableList()
        items.removeAll { it.path == entry.path }
        items.add(0, toItem(entry))
        save(favoritesFile(storeDir), items.take(MAX_FAVORITES))
    }

    fun removeFavorite(storeDir: File, path: String) {
        save(favoritesFile(storeDir), load(favoritesFile(storeDir)).filterNot { it.path == path })
    }

    fun favorites(storeDir: File): List<Item> = load(favoritesFile(storeDir))

    // ---------------------------------------------------------------- recents

    /** Records a file open; most recent first, deduplicated, capped. */
    fun addRecent(storeDir: File, entry: FileEntry) {
        if (entry.isDir) return
        val items = load(recentsFile(storeDir)).toMutableList()
        items.removeAll { it.path == entry.path }
        items.add(0, toItem(entry))
        save(recentsFile(storeDir), items.take(MAX_RECENTS))
    }

    fun recents(storeDir: File): List<Item> = load(recentsFile(storeDir))

    fun removeRecent(storeDir: File, path: String) {
        save(recentsFile(storeDir), load(recentsFile(storeDir)).filterNot { it.path == path })
    }

    // ---------------------------------------------------------------- io

    private fun toItem(e: FileEntry) = Item(e.path, e.isDir, e.size, e.lastModified)

    internal fun encode(path: String, isDir: Boolean, size: Long, mtime: Long): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(path.toByteArray(Charsets.UTF_8)) +
            "|" + (if (isDir) 1 else 0) + "|" + size + "|" + mtime

    internal fun decode(line: String): Item? {
        val sep = line.indexOf('|')
        if (sep <= 0) return null
        val encoded = line.substring(0, sep)
        val parts = line.substring(sep + 1).split('|')
        if (parts.size != 3) return null
        val path = try {
            String(Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8)
        } catch (e: Exception) {
            return null
        }
        val isDir = parts[0] == "1"
        val size = parts[1].toLongOrNull() ?: 0L
        val mtime = parts[2].toLongOrNull() ?: 0L
        return Item(path, isDir, size, mtime)
    }

    internal fun load(file: File): List<Item> {
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

    internal fun save(file: File, items: List<Item>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.bufferedWriter(Charsets.UTF_8).use { w ->
            for (item in items) {
                w.write(encode(item.path, item.isDir, item.size, item.lastModified))
                w.newLine()
            }
        }
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}
