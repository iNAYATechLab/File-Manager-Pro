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
            append(context.getString(R.string.action_open).let { "" })
            appendLine("Name: ${entry.name}")
            appendLine("Type: ${Icons.label(cat)}${if (!entry.isDir) " (${entry.extension.uppercase()})" else ""}")
            appendLine("Location: ${entry.file.parentFile?.path ?: entry.path}")
            if (!entry.isDir) appendLine("Size: ${FormatUtils.formatSize(entry.size)}")
            appendLine("Modified: ${FormatUtils.formatDate(entry.lastModified)}")
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
                    dialog.setMessage(body + "Content size: " + size)
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

/**
 * Determinate progress dialog used by transfer operations: a horizontal bar,
 * a "done/total" counter and the name of the entry being processed, plus a
 * Cancel action. All UI updates must go through [update]/[dismiss], which are
 * thread-safe (they post to the main looper).
 */
class OpProgressDialog private constructor(
    private val dialog: androidx.appcompat.app.AlertDialog,
    private val bar: android.widget.ProgressBar,
    private val tvItem: TextView,
    private val tvCount: TextView,
    private val onCancel: () -> Unit
) {
    /** Update progress from any thread. */
    fun update(done: Int, total: Int, label: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            if (!dialog.isShowing) return@post
            bar.max = total.coerceAtLeast(1)
            bar.progress = done.coerceIn(0, bar.max)
            tvCount.text = "$done/$total"
            tvItem.text = label
        }
    }

    /** Dismiss from any thread. */
    fun dismiss() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            if (dialog.isShowing) dialog.dismiss()
        }
    }

    /** User pressed Cancel or dismissed the dialog; cancels the operation. */
    fun cancel() = onCancel()

    companion object {
        fun create(
            context: Context,
            title: String,
            total: Int,
            onCancel: () -> Unit = {}
        ): OpProgressDialog {
            val density = context.resources.displayMetrics.density
            val pad = (20 * density).toInt()
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(pad, 0, pad, 0)
            }
            val bar = android.widget.ProgressBar(
                context, null, android.R.attr.progressBarStyleHorizontal
            ).apply {
                max = total.coerceAtLeast(1)
                progress = 0
            }
            val countParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (10 * density).toInt() }
            val tvCount = TextView(context).apply {
                textSize = 13f
                setTextColor(
                    androidx.core.content.ContextCompat.getColor(
                        context, android.R.color.secondary_text_dark
                    )
                )
            }
            val itemParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (4 * density).toInt() }
            val tvItem = TextView(context).apply {
                textSize = 13f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                setTextColor(
                    androidx.core.content.ContextCompat.getColor(
                        context, android.R.color.secondary_text_dark
                    )
                )
            }
            root.addView(bar, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            root.addView(tvCount, countParams)
            root.addView(tvItem, itemParams)

            val dialog = MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(root)
                .setCancelable(true)
                .setNegativeButton(R.string.action_cancel, null)
                .create()
            dialog.setOnShowListener {
                dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)
                    .setOnClickListener { onCancel() }
            }
            dialog.setOnCancelListener { onCancel() }
            dialog.show()
            return OpProgressDialog(dialog, bar, tvItem, tvCount, onCancel)
        }
    }
}
