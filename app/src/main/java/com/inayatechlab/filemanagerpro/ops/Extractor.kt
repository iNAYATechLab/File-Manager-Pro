package com.inayatechlab.filemanagerpro.ops

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import com.github.junrar.Archive as RarArchive
import com.github.junrar.exception.UnsupportedRarV5Exception

/**
 * Extraction engine for ZIP, TAR, TAR.GZ/TGZ, 7Z and RAR (RAR4) archives.
 *
 * ZIP/TAR/TGZ stream entries entry-by-entry; 7Z and RAR use the library
 * iterators. Every entry is zip-slip guarded and every stream is re-opened so
 * the first pass can count entries for progress reporting.
 */
object Extractor {

    /** Reason an archive cannot be opened. UI layer maps this to a string. */
    enum class Failure {
        UNSUPPORTED, RAR5, ENCRYPTED, CORRUPT
    }

    /** Thrown by [extract]; [failure] is null for a generic IO error. */
    class ExtractException(val failure: Failure?, message: String) : Exception(message)

    /** True when [name] looks like one of the supported archive formats. */
    fun isSupportedName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".zip") || lower.endsWith(".tar") ||
            lower.endsWith(".tar.gz") || lower.endsWith(".tgz") ||
            lower.endsWith(".7z") || lower.endsWith(".rar")
    }

    /**
     * Extracts [archive] into a sibling folder named after the archive
     * (a new name is picked automatically when the folder already exists).
     * Returns the created destination folder.
     */
    suspend fun extract(
        archive: File,
        onProgress: (done: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        val dest = com.inayatechlab.filemanagerpro.ops.FileOps.uniqueTarget(
            archive.parentFile ?: File("/"),
            baseNameOf(archive.name)
        )
        dest.mkdirs()

        val name = archive.name.lowercase()
        val failure: Failure? = when {
            name.endsWith(".zip") -> null
            name.endsWith(".tar") || name.endsWith(".tar.gz") || name.endsWith(".tgz") -> null
            name.endsWith(".7z") -> null
            name.endsWith(".rar") -> null
            else -> Failure.UNSUPPORTED
        }
        if (failure != null) throw ExtractException(failure, "Unsupported archive")

        try {
            when {
                name.endsWith(".zip") -> extractZip(archive, dest, onProgress)
                name.endsWith(".tar.gz") || name.endsWith(".tgz") -> extractTar(archive, true, dest, onProgress)
                name.endsWith(".tar") -> extractTar(archive, false, dest, onProgress)
                name.endsWith(".7z") -> extractSevenZ(archive, dest, onProgress)
                name.endsWith(".rar") -> extractRar(archive, dest, onProgress)
            }
        } catch (e: ExtractException) {
            throw e
        } catch (e: UnsupportedRarV5Exception) {
            throw ExtractException(Failure.RAR5, "RAR5 not supported")
        } catch (e: Exception) {
            val message = e.message ?: "extract failed"
            if (message.contains("password", ignoreCase = true) ||
                message.contains("encrypted", ignoreCase = true)
            ) {
                throw ExtractException(Failure.ENCRYPTED, message)
            }
            throw e
        }
        dest
    }

    private fun baseNameOf(fileName: String): String {
        var out = fileName
        for (suffix in listOf(".tar.gz", ".tgz", ".zip", ".tar", ".7z", ".rar")) {
            if (out.lowercase().endsWith(suffix)) {
                out = out.dropLast(suffix.length)
                break
            }
        }
        return out
    }

    // ---------------------------------------------------------------- zip

    private fun extractZip(
        archive: File,
        dest: File,
        onProgress: (Int, Int, String) -> Unit
    ) {
        val total = countZipEntries(archive).coerceAtLeast(1)
        var done = 0
        ZipInputStream(FileInputStream(archive).buffered(1 shl 16)).use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                onProgress(done, total, entry.name)
                writeEntry(entry.isDirectory, entry.name, zin, dest)
                done++
                entry = zin.nextEntry
            }
        }
    }

    private fun countZipEntries(archive: File): Int {
        var count = 0
        ZipInputStream(FileInputStream(archive).buffered(1 shl 16)).use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                count++
                entry = zin.nextEntry
            }
        }
        return count
    }

    // ---------------------------------------------------------------- tar / tar.gz

    private fun extractTar(
        archive: File,
        gzipped: Boolean,
        dest: File,
        onProgress: (Int, Int, String) -> Unit
    ) {
        val total = countTarEntries(archive, gzipped).coerceAtLeast(1)
        var done = 0
        TarArchiveInputStream(openForTar(archive, gzipped)).use { tin ->
            var entry = tin.nextTarEntry
            while (entry != null) {
                onProgress(done, total, entry.name)
                writeEntry(entry.isDirectory, entry.name, tin, dest)
                done++
                entry = tin.nextTarEntry
            }
        }
    }

    private fun openForTar(archive: File, gzipped: Boolean): java.io.InputStream {
        val raw = FileInputStream(archive).buffered(1 shl 16)
        return if (gzipped) GZIPInputStream(raw) else raw
    }

    private fun countTarEntries(archive: File, gzipped: Boolean): Int {
        var count = 0
        TarArchiveInputStream(openForTar(archive, gzipped)).use { tin ->
            while (tin.nextTarEntry != null) count++
        }
        return count
    }

    // ---------------------------------------------------------------- 7z

    private fun extractSevenZ(
        archive: File,
        dest: File,
        onProgress: (Int, Int, String) -> Unit
    ) {
        val total = SevenZFile(archive).use { file ->
            file.entries.count()
        }.coerceAtLeast(1)
        var done = 0
        SevenZFile(archive).use { file ->
            for (entry in file.entries) {
                onProgress(done, total, entry.name)
                if (entry.isDirectory) {
                    writeEntry(true, entry.name, null, dest)
                } else {
                    file.getInputStream(entry).use { input ->
                        writeEntry(false, entry.name, input, dest)
                    }
                }
                done++
            }
        }
    }

    // ---------------------------------------------------------------- rar (RAR4)

    private fun extractRar(
        archive: File,
        dest: File,
        onProgress: (Int, Int, String) -> Unit
    ) {
        val total = RarArchive(archive).use { rar ->
            var n = 0
            rar.getFileHeaders().forEach {
                if (!it.isDirectory) n++
            }
            n.coerceAtLeast(1)
        }
        var done = 0
        RarArchive(archive).use { rar ->
            rar.getFileHeaders().forEach { header ->
                if (header.isDirectory) {
                    writeEntry(true, header.fileName, null, dest)
                    return@forEach
                }
                onProgress(done, total, header.fileName)
                if (header.isEncrypted) {
                    throw ExtractException(Failure.ENCRYPTED, "password protected")
                }
                rar.getInputStream(header).use { input ->
                    writeEntry(false, header.fileName, input, dest)
                }
                done++
            }
        }
    }

    // ---------------------------------------------------------------- shared

    /**
     * Writes one archive member to [dest]. [stream] provides the content for
     * file entries (null for directory-only members).
     */
    private fun writeEntry(
        isDirectory: Boolean,
        entryName: String,
        stream: java.io.InputStream?,
        dest: File
    ) {
        val normalizedName = entryName.replace('\\', '/')
        val target = File(dest, normalizedName.replace('/', File.separatorChar))
        val canonical = target.canonicalPath
        if (!canonical.startsWith(dest.canonicalPath + File.separator)) {
            throw ExtractException(null, "Unsafe entry path: $entryName")
        }
        if (isDirectory) {
            target.mkdirs()
            return
        }
        target.parentFile?.mkdirs()
        FileOutputStream(target).use { out ->
            stream!!.copyTo(out, 1 shl 16)
        }
    }
}
