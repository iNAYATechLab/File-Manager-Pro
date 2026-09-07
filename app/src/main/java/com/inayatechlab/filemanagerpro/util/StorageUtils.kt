package com.inayatechlab.filemanagerpro.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import androidx.core.content.ContextCompat

data class StorageRoot(
    val label: String,
    val file: java.io.File,
    val total: Long,
    val free: Long
)

object StorageUtils {

    /** Primary shared storage (e.g. /storage/emulated/0). */
    fun primaryRoot(): java.io.File = Environment.getExternalStorageDirectory()

    /** All mount roots the app can browse: primary + removable / SD volumes. */
    fun roots(): List<StorageRoot> {
        val result = mutableListOf<StorageRoot>()
        val primary = primaryRoot()
        result.add(buildRoot("Internal storage", primary))

        val storageDir = java.io.File("/storage")
        if (storageDir.exists()) {
            val primaryCanonical = canonical(primary)
            storageDir.listFiles()?.forEach { vol ->
                val name = vol.name
                if (name == "emulated" || name == "self") return@forEach
                if (!vol.isDirectory) return@forEach
                val canon = canonical(vol)
                if (canon == primaryCanonical) return@forEach
                // find the actual user dir inside the volume if present
                val userDir = java.io.File(vol, "0").takeIf { it.exists() } ?: vol
                result.add(buildRoot(vol.name.replaceFirstChar { it.uppercase() }, userDir))
            }
        }
        return result
    }

    private fun canonical(f: java.io.File): String = try {
        f.canonicalPath
    } catch (e: Exception) {
        f.absolutePath
    }

    private fun buildRoot(label: String, dir: java.io.File): StorageRoot {
        val stat = try {
            StatFs(dir.absolutePath)
        } catch (e: Exception) {
            return StorageRoot(label, dir, 0L, 0L)
        }
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong
        return StorageRoot(label, dir, total, free)
    }

    fun isStoragePermitted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    /** Open the system screen where the user can grant storage access. */
    fun requestStorageAccess(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:${context.packageName}")
                context.startActivity(intent)
            } catch (e: Exception) {
                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            // Legacy path — normal runtime permission
            val activity = context as? android.app.Activity ?: return
            val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            } else {
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            }
            androidx.core.app.ActivityCompat.requestPermissions(activity, perms, 100)
        }
    }

    /** Directories that should be skipped during recursive scans. */
    val SKIP_DIR_NAMES = setOf(
        "android", "Android", "LOST.DIR", "lost+found", ".thumbnails", ".Trash",
        "app_chrome", "app_webview", "cache", "code_cache", "files", "databases",
        "shared_prefs", "no_backup", "lib", "gamedata"
    )

    /** Volumes excluded when walking /storage. */
    private val EXCLUDED_STORAGE_NAMES = setOf("emulated", "self")

    fun isExcludedScanPath(path: String): Boolean {
        val lower = path.lowercase()
        return lower.contains("/android/data/") ||
                lower.contains("/android/obb/") ||
                lower.contains("/android/media/") ||
                lower.contains("/lost.dir/") ||
                lower.endsWith("/android")
    }
}
