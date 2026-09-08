package com.inayatechlab.filemanagerpro.util

import android.content.Context
import com.inayatechlab.filemanagerpro.model.FileEntry
import java.io.File

/**
 * Cross-cutting bookkeeping for FavoritesStore / RecentStore: every destructive
 * or naming operation performed anywhere in the app goes through here so both
 * stores keep pointing at files that still exist.
 */
object EntryTracker {

    fun onOpened(context: Context, path: String) =
        RecentStore.record(context, path, RecentStore.Kind.OPENED)

    fun onCreated(context: Context, path: String) =
        RecentStore.record(context, path, RecentStore.Kind.CREATED)

    fun onRenamed(context: Context, oldPath: String, newPath: String) {
        RecentStore.replacePath(context, oldPath, newPath)
        FavoritesStore.replacePath(context, oldPath, newPath)
    }

    /** After deletes: drop stored entries whose file disappeared. */
    fun onDeleted(context: Context, paths: List<String>) {
        for (path in paths) {
            val f = File(path)
            val gone = !f.exists()
            if (gone) {
                RecentStore.removePath(context, path)
                FavoritesStore.removeNow(context, path)
            }
        }
    }

    /** After a successful cut-paste into [destDir]: re-point or drop records. */
    fun onMovedCut(context: Context, from: List<FileEntry>, destDir: File) {
        for (e in from) {
            val src = File(e.path)
            if (src.exists()) continue // copy mode or source still there — nothing to do
            val dst = File(destDir, e.name)
            if (dst.exists()) {
                onRenamed(context, e.path, dst.canonicalPath)
            } else {
                RecentStore.removePath(context, e.path)
                FavoritesStore.removeNow(context, e.path)
            }
        }
    }
}
