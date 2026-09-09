package com.inayatechlab.filemanagerpro.ops

import com.inayatechlab.filemanagerpro.model.FileEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** How to resolve a name conflict during copy/move. */
enum class ConflictPolicy { OVERWRITE, SKIP, KEEP_BOTH }

/**
 * Result summary of a batch operation.
 * [done] counts entries transferred successfully, [failed] errors,
 * [skipped] conflicts resolved with SKIP, [replaced] conflicts overwritten.
 * [cancelled] is true when the caller aborted the whole operation.
 */
data class OpResult(
    val done: Int,
    val failed: Int,
    val errors: List<String>,
    val skipped: Int = 0,
    val replaced: Int = 0,
    val cancelled: Boolean = false
) {
    val ok: Boolean get() = failed == 0
    val message: String
        get() = if (errors.isEmpty()) "$done done" else errors.joinToString(separator = "\n", limit = 3)
}

object FileOps {

    class FileOpsException(message: String) : Exception(message)

    /**
     * Copy or move [entries] into [destDir].
     *
     * When a destination entry with the same name exists, [conflictHandler] is
     * invoked (on the IO dispatcher) with the entry name; it must return the
     * chosen [ConflictPolicy], or null to abort the whole operation.
     * [onProgress] is invoked on the IO dispatcher with (processed, total, currentLabel).
     */
    suspend fun copyOrMove(
        entries: List<FileEntry>,
        destDir: File,
        cut: Boolean,
        conflictHandler: suspend (name: String) -> ConflictPolicy? = { ConflictPolicy.KEEP_BOTH },
        onProgress: suspend (done: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ): OpResult = withContext(Dispatchers.IO) {
        require(destDir.isDirectory)
        if (!destDir.exists() || !destDir.canWrite()) {
            throw FileOpsException("Destination is not writable")
        }
        val total = entries.size
        var done = 0
        var processed = 0
        var skipped = 0
        var replaced = 0
        var cancelled = false
        val errors = mutableListOf<String>()
        for (e in entries) {
            val src = File(e.path)
            processed++
            onProgress(processed, total, e.name)
            try {
                if (isInside(src, destDir)) {
                    errors.add("Cannot ${if (cut) "move" else "copy"} \"${e.name}\" into itself")
                } else {
                    var target = File(destDir, src.name)
                    if (target.exists()) {
                        val policy = conflictHandler(e.name)
                        when (policy) {
                            null -> {
                                cancelled = true
                                break
                            }
                            ConflictPolicy.SKIP -> {
                                skipped++
                                continue
                            }
                            ConflictPolicy.OVERWRITE -> {
                                if (!deleteRecursively(target)) {
                                    errors.add("${e.name}: could not replace existing file")
                                    continue
                                }
                                replaced++
                            }
                            ConflictPolicy.KEEP_BOTH -> target = uniqueTarget(destDir, src.name)
                        }
                    }
                    if (cut) {
                        if (!src.renameTo(target)) {
                            copyRecursively(src, target)
                            if (!deleteRecursively(src)) errors.add("Could not delete source: ${e.name}")
                        }
                    } else {
                        copyRecursively(src, target)
                    }
                    done++
                }
            } catch (ex: Exception) {
                errors.add("${e.name}: ${ex.message ?: "error"}")
            }
        }
        OpResult(done, errors.size, errors, skipped, replaced, cancelled)
    }

    suspend fun delete(entries: List<FileEntry>, onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }): OpResult =
        withContext(Dispatchers.IO) {
            val total = entries.size
            var done = 0
            val errors = mutableListOf<String>()
            for (e in entries) {
                onProgress(done, total, e.name)
                try {
                    if (!deleteRecursively(File(e.path))) errors.add("Could not delete ${e.name}")
                } catch (ex: Exception) {
                    errors.add("${e.name}: ${ex.message ?: "error"}")
                }
                done++
            }
            OpResult(done, errors.size, errors)
        }

    suspend fun rename(dir: File, oldName: String, newName: String): String? = withContext(Dispatchers.IO) {
        val clean = newName.trim()
        if (clean.isEmpty() || clean == "." || clean == ".." || clean.contains('/') || clean.contains('\\')) {
            return@withContext "Invalid name"
        }
        val src = File(dir, oldName)
        val dst = File(dir, clean)
        if (dst.exists()) return@withContext "A file with this name already exists"
        if (!src.renameTo(dst)) return@withContext "Rename failed"
        null
    }

    suspend fun createFolder(dir: File, name: String): String? = withContext(Dispatchers.IO) {
        val clean = name.trim()
        if (clean.isEmpty() || clean.contains('/') || clean.contains('\\')) return@withContext "Invalid name"
        val target = File(dir, clean)
        if (target.exists()) return@withContext "A folder with this name already exists"
        if (!target.mkdir()) return@withContext "Could not create folder"
        null
    }

    /** Create <name>.zip next to the selected items. Returns created file or throws. */
    suspend fun zip(entries: List<FileEntry>, onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }): File =
        withContext(Dispatchers.IO) {
            val first = File(entries.first().path)
            val baseName = if (entries.size == 1) first.nameWithoutExtension
            else first.parentFile?.name ?: "archive"
            val zipFile = uniqueTarget(first.parentFile ?: File("/"), "$baseName.zip")

            ZipOutputStream(FileOutputStream(zipFile).buffered(1 shl 16)).use { zos ->
                var done = 0
                val total = entries.size
                for (e in entries) {
                    onProgress(done, total, e.name)
                    addToZip(zos, File(e.path), null)
                    done++
                }
            }
            zipFile
        }

    private fun addToZip(zos: ZipOutputStream, file: File, relativePrefix: String?) {
        val entryName = if (relativePrefix == null) file.name
        else "$relativePrefix/${file.name}"
        if (file.isDirectory) {
            val prefix = entryName.trimEnd('/')
            zos.putNextEntry(ZipEntry("$prefix/"))
            zos.closeEntry()
            file.listFiles()?.forEach { child ->
                if (child.exists()) addToZip(zos, child, prefix)
            }
        } else {
            zos.putNextEntry(ZipEntry(entryName))
            FileInputStream(file).use { input ->
                input.copyTo(zos, 1 shl 16)
            }
            zos.closeEntry()
        }
    }

    /** Extract an archive into a sibling folder. Delegates to [Extractor]. */
    suspend fun extract(zipFile: File, onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }): File =
        Extractor.extract(zipFile, onProgress)

    /** Recursively count the size of a folder (files only). */
    suspend fun folderSize(file: File, onProgress: (Long) -> Unit = {}): Long = withContext(Dispatchers.IO) {
        var total = 0L
        fun walk(f: File) {
            if (!f.exists()) return
            if (f.isDirectory) {
                f.listFiles()?.forEach {
                    if (it.isDirectory && it.name.startsWith(".")) return@forEach
                    walk(it)
                }
            } else {
                total += f.length()
                if (total % 2_000_000L < 100_000L) onProgress(total)
            }
        }
        walk(file)
        total
    }

    private fun copyRecursively(src: File, dst: File) {
        if (src.isDirectory) {
            if (!dst.exists()) dst.mkdirs()
            src.listFiles()?.forEach { child ->
                if (child.exists()) copyRecursively(child, File(dst, child.name))
            }
        } else {
            dst.parentFile?.mkdirs()
            FileInputStream(src).use { input ->
                FileOutputStream(dst).use { output ->
                    input.copyTo(output, 1 shl 16)
                }
            }
        }
    }

    private fun deleteRecursively(file: File): Boolean {
        if (file.isDirectory) {
            file.listFiles()?.forEach { child -> deleteRecursively(child) }
        }
        return !file.exists() || file.delete()
    }

    private fun isInside(candidate: File, parent: File): Boolean {
        var current: File? = candidate.canonicalFile
        val parentCanonical = parent.canonicalPath
        while (current != null) {
            if (current.canonicalPath == parentCanonical) return true
            current = current.parentFile
        }
        return false
    }

    /** Returns a path in [dir] with [name], appending " (n)" until it does not collide. */
    fun uniqueTarget(dir: File, name: String): File {
        var target = File(dir, name)
        var i = 1
        while (target.exists()) {
            val dot = name.lastIndexOf('.')
            val candidate = if (dot > 0) {
                "${name.substring(0, dot)} ($i)${name.substring(dot)}"
            } else {
                "$name ($i)"
            }
            target = File(dir, candidate)
            i++
        }
        return target
    }
}
