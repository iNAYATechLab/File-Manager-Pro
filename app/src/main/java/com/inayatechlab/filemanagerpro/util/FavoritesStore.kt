package com.inayatechlab.filemanagerpro.util

import android.content.Context
import com.inayatechlab.filemanagerpro.model.FileEntry
import com.inayatechlab.filemanagerpro.model.RecentEntry
import java.io.File
import java.util.TreeMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Persistent favorites storage. Records live in a private app file, keyed by
 * canonical path and kept sorted by the time they were starred (newest first).
 *
 * Public functions are safe from any thread.
 */
object FavoritesStore {

    private const val FILE_NAME = "favorites.txt"
    private const val MAX_ENTRIES = 500

    private val lock = Any()
    private var cached: MutableMap<String, RecentEntry> = TreeMap()

    fun favorites(context: Context): List<RecentEntry> =
        synchronized(lock) { resolve(context, cached.values.sortedByDescending { it.time }) }

    /** Star an existing file/folder (upsert; refreshes the time). */
    fun add(context: Context, entry: FileEntry, scope: CoroutineScope) {
        val f = File(entry.path)
        if (!f.exists()) return
        val canonical = canonicalOf(f)
        scope.launch {
            val changed = withContext(Dispatchers.IO) {
                synchronized(lock) {
                    val existed = cached.containsKey(canonical)
                    cached[canonical] = RecentEntry(
                        name = f.name,
                        path = canonical,
                        isDir = f.isDirectory,
                        time = System.currentTimeMillis()
                    )
                    while (cached.size > MAX_ENTRIES) {
                        val eldest = cached.values.minByOrNull { it.time } ?: break
                        cached.remove(eldest.path)
                    }
                    persistLocked(context)
                    !existed
                }
            }
            if (changed) onChange?.invoke()
        }
    }

    /** Synchronous remove (UI-thread bookkeeping of renamed/deleted files). */
    fun removeNow(context: Context, path: String) {
        synchronized(lock) {
            if (cached.remove(canonicalOf(path)) != null) persistLocked(context)
        }
        onChange?.invoke()
    }

    fun remove(context: Context, path: String, scope: CoroutineScope) {
        scope.launch {
            val changed = withContext(Dispatchers.IO) {
                synchronized(lock) {
                    if (cached.remove(canonicalOf(path)) != null) {
                        persistLocked(context)
                        true
                    } else {
                        false
                    }
                }
            }
            if (changed) onChange?.invoke()
        }
    }

    fun replacePath(context: Context, oldPath: String, newPath: String) {
        val newFile = File(newPath)
        if (!newFile.exists()) {
            removeNow(context, oldPath)
            return
        }
        val newCanonical = canonicalOf(newFile)
        synchronized(lock) {
            val key = canonicalOf(oldPath)
            val cur = cached[key] ?: return
            val migrated = cur.copy(name = newFile.name, path = newCanonical)
            cached.remove(key)
            cached[newCanonical] = migrated
            persistLocked(context)
        }
        onChange?.invoke()
    }

    /** Drop favorites whose file no longer exists. */
    fun pruneMissing(context: Context, scope: CoroutineScope) {
        scope.launch {
            val changed = withContext(Dispatchers.IO) {
                synchronized(lock) {
                    val gone = cached.values.filter { !File(it.path).exists() }
                    gone.forEach { cached.remove(it.path) }
                    if (gone.isNotEmpty()) {
                        persistLocked(context)
                        true
                    } else {
                        false
                    }
                }
            }
            if (changed) onChange?.invoke()
        }
    }

    /** Optional callback after any content change (main thread). */
    var onChange: (() -> Unit)? = null

    private fun canonicalOf(file: File): String = try {
        file.canonicalPath
    } catch (e: Exception) {
        file.path
    }

    private fun canonicalOf(path: String): String = try {
        File(path).canonicalPath
    } catch (e: Exception) {
        path
    }

    private fun persistLocked(context: Context) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            file.parentFile?.mkdirs()
            file.writeText(buildString {
                for (r in cached.values.sortedByDescending { it.time }) {
                    appendLine("${r.time}|${r.path}")
                }
            })
        } catch (_: Exception) {
        }
    }

    /** (Re)load records from disk. Call after process start. */
    fun reload(context: Context) {
        synchronized(lock) {
            try {
                val lines = File(context.filesDir, FILE_NAME).takeIf { it.exists() }?.readLines().orEmpty()
                val fresh = TreeMap<String, RecentEntry>()
                for (line in lines) {
                    val pipe = line.lastIndexOf('|')
                    if (pipe <= 0 || pipe == line.length - 1) continue
                    val time = line.substring(0, pipe).toLongOrNull() ?: continue
                    val path = line.substring(pipe + 1)
                    if (path.isBlank() || !File(path).exists()) continue
                    fresh[path] = RecentEntry(
                        name = File(path).name,
                        path = path,
                        isDir = File(path).isDirectory,
                        time = time
                    )
                }
                cached = fresh
            } catch (e: Exception) {
                cached = TreeMap()
            }
        }
    }

    private fun resolve(context: Context, records: List<RecentEntry>): List<RecentEntry> {
        if (records.isEmpty()) return emptyList()
        val keep = records.filter { File(it.path).exists() }
        if (keep.size != records.size) {
            synchronized(lock) {
                val present = keep.mapTo(mutableSetOf()) { it.path }
                cached = TreeMap(cached.filterKeys { it in present })
                persistLocked(context)
            }
        }
        return keep
    }
}
