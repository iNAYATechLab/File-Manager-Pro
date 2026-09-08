package com.inayatechlab.filemanagerpro.util

import android.content.Context
import com.inayatechlab.filemanagerpro.model.RecentEntry
import java.io.File
import java.util.TreeMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Auto-tracked recent history (opened / modified / created).
 *
 * * [record] — explicit events (file/folder opened, newly created items).
 * * [scanAndTrack] — bounded background walk of every mounted volume that finds
 *   files & folders changed/created within the last [WINDOW_MS] and records them
 *   once (unless a newer event already exists).
 *
 * Records persist in a private app file; entries whose file disappeared are
 * dropped lazily on read.
 */
object RecentStore {

    private const val FILE_NAME = "recent.txt"
    private const val MAX_ENTRIES = 120
    private const val WINDOW_MS = 3L * 24 * 60 * 60 * 1000 // 3 days
    private const val MAX_SCAN_FILES = 8000
    private const val MAX_SCAN_MS = 4000L

    enum class Kind(val labelRes: Int) {
        OPENED(com.inayatechlab.filemanagerpro.R.string.recent_event_opened),
        MODIFIED(com.inayatechlab.filemanagerpro.R.string.recent_event_modified),
        CREATED(com.inayatechlab.filemanagerpro.R.string.recent_event_created)
    }

    enum class Filter(val labelRes: Int, val kind: Kind?) {
        ALL(com.inayatechlab.filemanagerpro.R.string.recent_filter_all, null),
        FILES(com.inayatechlab.filemanagerpro.R.string.recent_filter_files, null),
        FOLDERS(com.inayatechlab.filemanagerpro.R.string.recent_filter_folders, null),
        OPENED(com.inayatechlab.filemanagerpro.R.string.recent_filter_opened, Kind.OPENED),
        MODIFIED(com.inayatechlab.filemanagerpro.R.string.recent_filter_modified, Kind.MODIFIED),
        CREATED(com.inayatechlab.filemanagerpro.R.string.recent_filter_created, Kind.CREATED)
    }

    private val lock = Any()
    private var cached: MutableMap<String, RecentEntry> = TreeMap()

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun all(context: Context): List<RecentEntry> =
        synchronized(lock) { resolve(context, cached.values.sortedByDescending { it.time }) }

    fun record(context: Context, path: String, kind: Kind, time: Long = System.currentTimeMillis()) {
        val f = File(path)
        if (!f.exists()) return
        val canonical = try {
            f.canonicalPath
        } catch (e: Exception) {
            path
        }
        ioScope.launch {
            synchronized(lock) {
                val entry = RecentEntry(
                    name = f.name,
                    path = canonical,
                    isDir = f.isDirectory,
                    size = if (f.isFile) f.length() else 0L,
                    time = time,
                    kind = kind
                )
                cached[canonical] = entry
                trimLocked()
                persistLocked(context)
            }
        }
    }

    /** Synchronous variant used from the UI for single-path bookkeeping. */
    fun removePath(context: Context, path: String) {
        val key = try {
            File(path).canonicalPath
        } catch (e: Exception) {
            path
        }
        synchronized(lock) {
            if (cached.remove(key) != null) persistLocked(context)
        }
    }

    fun replacePath(context: Context, oldPath: String, newPath: String) {
        val newFile = File(newPath)
        if (!newFile.exists()) {
            removePath(context, oldPath)
            return
        }
        val newCanonical = try {
            newFile.canonicalPath
        } catch (e: Exception) {
            newPath
        }
        synchronized(lock) {
            val cur = cached.remove(oldCanonical(oldPath)) ?: return
            cached[newCanonical] = cur.copy(name = newFile.name, path = newCanonical)
            persistLocked(context)
        }
    }

    fun clear(context: Context) {
        synchronized(lock) {
            cached.clear()
            try {
                File(context.filesDir, FILE_NAME).delete()
            } catch (_: Exception) {
            }
        }
    }

    /** Background, bounded freshness scan of all mounted volumes. */
    fun scanAndTrack(context: Context) {
        ioScope.launch {
            val windowStart = System.currentTimeMillis() - WINDOW_MS
            val collected = mutableListOf<Triple<String, Kind, Long>>() // canonical, kind, time
            val deadline = System.currentTimeMillis() + MAX_SCAN_MS
            var visited = 0
            val budget = object {
                var files = 0
                fun ok(): Boolean = files < MAX_SCAN_FILES && System.currentTimeMillis() < deadline
            }
            fun walk(dir: File, depth: Int) {
                if (!budget.ok() || depth > 6) return
                val list = try {
                    dir.listFiles() ?: return
                } catch (e: Exception) {
                    return
                }
                for (child in list) {
                    if (!budget.ok()) return
                    val name = child.name
                    if (name.startsWith(".")) continue
                    if (name == "Android" || name.equals("LOST.DIR", true)) continue
                    val mtime = child.lastModified()
                    if (mtime < windowStart) continue // children only make mtime newer
                    val isDir = child.isDirectory
                    if (isDir) {
                        collected.add(Triple(canonical(child), Kind.MODIFIED, mtime))
                        walk(child, depth + 1)
                    } else {
                        budget.files++
                        val ctime = creationMillis(child)
                        val kind = if (ctime in windowStart..mtime && mtime - ctime < 60 * 60 * 1000L) {
                            Kind.CREATED
                        } else {
                            Kind.MODIFIED
                        }
                        val time = if (kind == Kind.CREATED) ctime else mtime
                        if (time >= windowStart) collected.add(Triple(canonical(child), kind, time))
                    }
                }
            }
            for (root in mountedRoots(context)) {
                if (!budget.ok()) break
                walk(root, 0)
            }
            if (collected.isEmpty()) return@launch
            synchronized(lock) {
                var added = 0
                for ((path, kind, time) in collected) {
                    val existing = cached[path]
                    if (existing == null || existing.time < time) {
                        cached[path] = RecentEntry(
                            name = File(path).name,
                            path = path,
                            isDir = File(path).isDirectory,
                            size = if (File(path).isFile) File(path).length() else 0L,
                            time = time,
                            kind = kind
                        )
                        added++
                    }
                }
                if (added > 0) {
                    trimLocked()
                    persistLocked(context)
                }
            }
            visited.let { }
        }
    }

    private fun trimLocked() {
        while (cached.size > MAX_ENTRIES) {
            val eldest = cached.values.minByOrNull { it.time } ?: break
            cached.remove(eldest.path)
        }
    }

    private fun canonical(file: File): String = try {
        file.canonicalPath
    } catch (e: Exception) {
        file.path
    }

    private fun oldCanonical(path: String): String = try {
        File(path).canonicalPath
    } catch (e: Exception) {
        path
    }

    private fun creationMillis(file: File): Long = try {
        val attrs = java.nio.file.Files.readAttributes(
            file.toPath(), java.nio.file.attribute.BasicFileAttributes::class.java
        )
        attrs.creationTime().toMillis()
    } catch (e: Exception) {
        file.lastModified()
    }

    private fun persistLocked(context: Context) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            file.parentFile?.mkdirs()
            file.writeText(buildString {
                for (r in cached.values.sortedByDescending { it.time }) {
                    appendLine("${r.time}|${r.kind.name}|${r.path}")
                }
            })
        } catch (_: Exception) {
        }
    }

    /** (Re)load records from disk; drops entries whose file no longer exists. */
    fun reload(context: Context) {
        synchronized(lock) {
            try {
                val lines = File(context.filesDir, FILE_NAME).takeIf { it.exists() }?.readLines().orEmpty()
                val fresh = TreeMap<String, RecentEntry>()
                for (line in lines) {
                    val parts = line.split("|", limit = 3)
                    if (parts.size != 3) continue
                    val time = parts[0].toLongOrNull() ?: continue
                    val kind = runCatching { Kind.valueOf(parts[1]) }.getOrDefault(Kind.OPENED)
                    val path = parts[2]
                    val f = File(path)
                    if (path.isBlank() || !f.exists()) continue
                    fresh[path] = RecentEntry(
                        name = f.name,
                        path = path,
                        isDir = f.isDirectory,
                        size = if (f.isFile) f.length() else 0L,
                        time = time,
                        kind = kind
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

    fun mountedRoots(context: Context): List<File> {
        val primary = StorageUtils.primaryRoot()
        val list = mutableListOf<File>()
        if (primary.exists()) list.add(primary)
        for (r in StorageUtils.roots()) {
            if (r.file.exists() && canonical(r.file) != canonical(primary)) list.add(r.file)
        }
        return list.distinctBy { canonical(it) }
    }
}
