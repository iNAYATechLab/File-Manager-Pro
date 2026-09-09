package com.inayatechlab.filemanagerpro.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.inayatechlab.filemanagerpro.model.FileEntry
import java.io.File

object OpenUtils {

    fun mimeFor(file: File): String {
        val ext = file.extension.lowercase()
        val fromMap = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        if (!fromMap.isNullOrBlank()) return fromMap
        return when (ext) {
            "md", "markdown" -> "text/markdown"
            "log", "txt" -> "text/plain"
            "kt", "kts" -> "text/plain"
            "yaml", "yml" -> "text/plain"
            "sh" -> "text/plain"
            "gradle", "properties" -> "text/plain"
            "apk" -> "application/vnd.android.package-archive"
            "csv" -> "text/comma-separated-values"
            "webp" -> "image/webp"
            "heic", "heif" -> "image/heic"
            else -> "application/octet-stream"
        }
    }

    private fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)

    /** Open a file with an external app. Throws if nothing can handle it. */
    fun openExternal(context: Context, file: File): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uriFor(context, file), mimeFor(file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrElse {
            // Second attempt with generic mime
            val generic = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uriFor(context, file), "application/octet-stream")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { context.startActivity(generic); true }.getOrDefault(false)
        }
    }

    /** Lets the user pick any app for this file (explicit chooser). */
    fun openWith(context: Context, file: File): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uriFor(context, file), mimeFor(file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(
            intent,
            context.getString(com.inayatechlab.filemanagerpro.R.string.action_open_with)
        )
        return runCatching {
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }

    fun share(context: Context, files: List<FileEntry>): Boolean {
        if (files.isEmpty()) return false
        val uris = files.map { uriFor(context, File(it.path)) }
        val singleMime = if (files.size == 1) mimeFor(File(files[0].path)) else "*/*"
        val intent = if (files.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = singleMime
                putExtra(Intent.EXTRA_STREAM, uris.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = singleMime
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
        }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        return runCatching {
            context.startActivity(Intent.createChooser(intent, "Share"))
            true
        }.getOrDefault(false)
    }
}
