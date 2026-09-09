package com.inayatechlab.filemanagerpro.ops

import android.content.Context
import android.media.MediaScannerConnection
import com.inayatechlab.filemanagerpro.model.FileEntry
import com.inayatechlab.filemanagerpro.model.TrashStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android-side wrapper around [TrashStore]: moves files & folders to the
 * Trash from any UI flow and keeps the MediaStore consistent by rescanning
 * the original paths (a vanished path drops its stale media entry, a
 * restored file is re-indexed).
 */
object TrashOps {

    class MoveResult(val done: Int, val failed: Int, val firstFailedName: String?)

    fun storeDir(context: Context): File = TrashStore.storeDir(context.filesDir)

    /** Moves every entry to the Trash (IO). Result carries counts for snackbars. */
    suspend fun move(context: Context, entries: List<FileEntry>): MoveResult =
        withContext(Dispatchers.IO) {
            var done = 0
            var failed = 0
            var firstFailedName: String? = null
            val dir = storeDir(context)
            for (e in entries) {
                val err = TrashStore.moveToTrash(dir, File(e.path))
                if (err == null) {
                    done++
                    if (!e.isDir) dropMedia(context, e.path)
                } else {
                    failed++
                    if (firstFailedName == null) firstFailedName = e.name
                }
            }
            MoveResult(done, failed, firstFailedName)
        }

    /** Restores one item to its original location. */
    suspend fun restore(context: Context, item: TrashStore.Item): Boolean =
        withContext(Dispatchers.IO) {
            val ok = TrashStore.restore(storeDir(context), item) == null
            if (ok && !item.isDir) dropMedia(context, item.origPath)
            ok
        }

    /** Permanently deletes one trashed item. */
    suspend fun purge(context: Context, item: TrashStore.Item): Boolean =
        withContext(Dispatchers.IO) {
            val ok = TrashStore.purge(storeDir(context), item) == null
            if (ok && !item.isDir) dropMedia(context, item.origPath)
            ok
        }

    /** Permanently deletes everything in the Trash. Returns the failed count. */
    suspend fun empty(context: Context): Int = withContext(Dispatchers.IO) {
        var failed = 0
        for (item in TrashStore.list(storeDir(context))) {
            if (TrashStore.purge(storeDir(context), item) != null) failed++
            else if (!item.isDir) dropMedia(context, item.origPath)
        }
        failed
    }

    /** Trash contents, newest first (also prunes expired/orphaned rows). */
    suspend fun items(context: Context): List<TrashStore.Item> =
        withContext(Dispatchers.IO) { TrashStore.list(storeDir(context)) }

    private fun dropMedia(context: Context, path: String) {
        runCatching {
            MediaScannerConnection.scanFile(context.applicationContext, arrayOf(path), null, null)
        }
    }
}
