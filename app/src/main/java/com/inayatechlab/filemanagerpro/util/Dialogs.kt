package com.inayatechlab.filemanagerpro.util

import android.app.ProgressDialog
import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.inayatechlab.filemanagerpro.R
import com.inayatechlab.filemanagerpro.model.FileEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object Dialogs {

    /** Simple indeterminate progress dialog shown while an operation runs. */
    fun showProgress(context: Context, message: String): ProgressDialog =
        ProgressDialog.show(context, null, message, true, false)

    fun promptText(
        context: Context,
        title: String,
        initial: String,
        hint: String,
        positive: String,
        onPositive: (String) -> Unit
    ) {
        val input = com.google.android.material.textfield.TextInputEditText(context).apply {
            this.hint = hint
            setText(initial)
            setSelection(initial.length)
            textSize = 16f
        }
        val wrap = LinearLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(64, 8, 64, 0)
        }
        wrap.addView(input)
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(wrap)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(positive) { _, _ -> onPositive(input.text?.toString().orEmpty()) }
            .show()
    }

    fun confirm(
        context: Context,
        title: String,
        message: String,
        positiveText: String,
        onYes: () -> Unit
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(positiveText) { _, _ -> onYes() }
            .show()
    }

    /** Properties dialog; [asyncSizeInfo] can supply an extra line once computed. */
    fun properties(context: Context, entry: FileEntry, scope: CoroutineScope) {
        val cat = FileCat.of(entry)
        val body = buildString {
            appendLine(context.getString(R.string.props_name_fmt, entry.name))
            val catLabel = context.getString(Icons.labelRes(cat))
            val typeLine = if (entry.isDir) {
                catLabel
            } else {
                context.getString(R.string.props_type_file_fmt, catLabel, entry.extension.uppercase())
            }
            appendLine(context.getString(R.string.props_type_fmt, typeLine))
            appendLine(context.getString(R.string.props_location_fmt, entry.file.parentFile?.path ?: entry.path))
            if (!entry.isDir) appendLine(context.getString(R.string.props_size_fmt, FormatUtils.formatSize(entry.size)))
            appendLine(context.getString(R.string.props_modified_fmt, FormatUtils.formatDate(entry.lastModified)))
        }
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.action_properties)
            .setMessage(body)
            .setPositiveButton(R.string.action_ok, null)
            .show()
        if (entry.isDir) {
            scope.launch(Dispatchers.IO) {
                val size = FileOpsSafe.sizeOf(entry.file)
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    dialog.setMessage(body + context.getString(R.string.props_content_size_fmt, size) + "\n")
                }
            }
        }
    }
}

/** Kept separate to avoid pulling FileOps (ops package) into the util layer cycles. */
object FileOpsSafe {
    fun sizeOf(file: java.io.File): String {
        var total = 0L
        try {
            fun walk(f: java.io.File) {
                if (!f.exists()) return
                if (f.isDirectory) {
                    f.listFiles()?.forEach { walk(it) }
                } else {
                    total += f.length()
                    if (total > 1_000_000_000L) throw RuntimeException("too large")
                }
            }
            walk(file)
        } catch (_: Exception) {
        }
        return com.inayatechlab.filemanagerpro.util.FormatUtils.formatSize(total)
    }
}
