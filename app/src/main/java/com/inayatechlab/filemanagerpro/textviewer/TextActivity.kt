package com.inayatechlab.filemanagerpro.textviewer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.inayatechlab.filemanagerpro.R
import com.inayatechlab.filemanagerpro.databinding.ActivityTextBinding
import com.inayatechlab.filemanagerpro.util.FormatUtils
import com.inayatechlab.filemanagerpro.util.TextFiles
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Built-in plain-text / source-code viewer (core, beta scope).
 * Encodings: UTF-8/16 with BOM, valid UTF-8, Windows-1252 fallback.
 * Files larger than a few MiB show a bounded head with a truncation note.
 */
class TextActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PATH = "extra_path"

        /** Opens [file] in the viewer when its name is text-like. */
        fun start(context: Context, file: File) {
            if (!TextFiles.isTextFile(file.name)) return
            context.startActivity(
                Intent(context, TextActivity::class.java)
                    .putExtra(EXTRA_PATH, file.path)
            )
        }
    }

    private lateinit var binding: ActivityTextBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTextBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val path = intent.getStringExtra(EXTRA_PATH)
        val file = path?.let { File(it) }
        binding.toolbar.setNavigationOnClickListener { finish() }
        if (file == null || !file.isFile) {
            binding.tvMeta.isVisible = false
            binding.tvContent.text = getString(R.string.txt_open_failed)
            return
        }
        binding.toolbar.title = file.name

        binding.tvContent.text = getString(R.string.txt_loading)
        lifecycleScope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    val head = TextFiles.readHead(file)
                    TextFiles.decodeHead(head)
                }.getOrNull()
            }
            if (res == null) {
                binding.tvContent.text = getString(R.string.txt_open_failed)
                binding.tvMeta.isVisible = false
                return@launch
            }
            val sizeNote = if (file.length() > TextFiles.MAX_READ_BYTES) {
                " " + getString(R.string.txt_showing_head)
            } else ""
            binding.tvMeta.text = getString(
                R.string.txt_meta_fmt,
                res.charsetLabel,
                FormatUtils.formatSize(file.length())
            ) + sizeNote
            binding.tvMeta.isVisible = true
            binding.tvContent.text = if (res.truncated) res.text else res.text
        }
    }
}
