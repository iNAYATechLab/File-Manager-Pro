package com.inayatechlab.filemanagerpro.util

import android.content.Context
import android.provider.MediaStore
import com.inayatechlab.filemanagerpro.model.FileEntry
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** File scanning helpers (categories + search). All functions run on Dispatchers.IO. */
object Scanner {

    const val MAX_RESULTS = 4000

    /**
     * MediaStore-backed scan for image / video / audio categories — fast and standard.
     */
    suspend fun scanMedia(context: Context, cat: FileCat, onFound: (FileEntry) -> Unit = {}): List<FileEntry> =
        withContext(Dispatchers.IO) {
            val uri = when (cat) {
                FileCat.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                FileCat.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                FileCat.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                else -> return@withContext emptyList()
            }
            val projection = arrayOf(
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED
            )
            val result = mutableListOf<FileEntry>()
            try {
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    val colData = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                    val colName = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val colSize = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                    val colDate = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                    while (cursor.moveToNext() && coroutineContext.isActive) {
                        val path = cursor.getString(colData) ?: continue
                        val name = cursor.getString(colName) ?: File(path).name
                        val size = cursor.getLong(colSize)
                        val date = cursor.getLong(colDate) * 1000L
                        val f = File(path)
                        if (!f.isFile || f.length() <= 0) continue
                        val entry = FileEntry(name, path, false, size.takeIf { it > 0 } ?: f.length(), date)
                        result.add(entry)
                        onFound(entry)
                        if (result.size >= MAX_RESULTS) break
                    }
                }
            } catch (_: Exception) {
            }
            result
        }

    /**
     * Recursive filesystem scan for the given file category across all readable
     * storage volumes, skipping Android's protected directories.
     */
    suspend fun scanFilesystem(cat: FileCat, onFound: (FileEntry) -> Unit = {}): List<FileEntry> =
        withContext(Dispatchers.IO) {
            val result = mutableListOf<FileEntry>()
            val roots = StorageUtils.roots().map { it.file }

            fun walk(dir: File) {
                if (result.size >= MAX_RESULTS) return
                if (!coroutineContext.isActive) return
                val children = runCatching { dir.listFiles() }.getOrNull() ?: return
                for (child in children) {
                    if (result.size >= MAX_RESULTS || !coroutineContext.isActive) return
                    val name = child.name
                    if (name.startsWith(".")) continue
                    if (StorageUtils.isExcludedScanPath(child.canonicalPath)) continue
                    try {
                        if (child.isDirectory) {
                            if (StorageUtils.SKIP_DIR_NAMES.contains(name)) continue
                            walk(child)
                        } else {
                            if (FileCat.ofExtension(child.extension) == cat) {
                                val entry = FileEntry(
                                    name, child.canonicalPath, false,
                                    child.length(), child.lastModified()
                                )
                                result.add(entry)
                                onFound(entry)
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            }
            roots.forEach { walk(it) }
            result
        }

    /** Search files + folders whose name contains [query] (case-insensitive). */
    suspend fun search(
        root: File,
        query: String,
        onFound: (FileEntry) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return@withContext 0
        var count = 0

        fun walk(dir: File) {
            if (count >= MAX_RESULTS || !coroutineContext.isActive) return
            val children = runCatching { dir.listFiles() }.getOrNull() ?: return
            for (child in children) {
                ensureActive()
                if (count >= MAX_RESULTS) return
                val name = child.name
                if (name.startsWith(".")) continue
                try {
                    val canonical = child.canonicalPath
                    if (StorageUtils.isExcludedScanPath(canonical)) continue
                    if (child.isDirectory) {
                        if (StorageUtils.SKIP_DIR_NAMES.contains(name)) continue
                        if (name.lowercase().contains(q)) {
                            onFound(FileEntry(name, canonical, true, 0, child.lastModified()))
                            count++
                        }
                        walk(child)
                    } else {
                        if (name.lowercase().contains(q)) {
                            onFound(
                                FileEntry(
                                    name, canonical, false,
                                    child.length(), child.lastModified()
                                )
                            )
                            count++
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
        walk(root)
        count
    }
}
