package com.inayatechlab.filemanagerpro.search

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.inayatechlab.filemanagerpro.MainActivity
import com.inayatechlab.filemanagerpro.R
import com.inayatechlab.filemanagerpro.browse.FileAdapter
import com.inayatechlab.filemanagerpro.databinding.ActivitySearchBinding
import com.inayatechlab.filemanagerpro.model.FileEntry
import com.inayatechlab.filemanagerpro.util.FileCat
import com.inayatechlab.filemanagerpro.util.OpenUtils
import com.inayatechlab.filemanagerpro.util.Scanner
import com.inayatechlab.filemanagerpro.util.StorageUtils
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ROOT = "extra_root"
    }

    private lateinit var binding: ActivitySearchBinding
    private var searchJob: Job? = null
    private val searching = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        val rootDir = intent.getStringExtra(EXTRA_ROOT)
            ?.let { File(it) }
            ?.takeIf { it.isDirectory }
            ?: StorageUtils.primaryRoot()

        val adapter = FileAdapter(isGrid = false, selectable = false).apply {
            onItemClick = { entry -> onResultClick(entry, rootDir) }
        }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        binding.etQuery.requestFocus()

        binding.etQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                runSearch(s?.toString().orEmpty(), rootDir, adapter)
            }
        })
    }

    private fun runSearch(query: String, root: File, adapter: FileAdapter) {
        searchJob?.cancel()
        searching.set(false)
        adapter.entries = mutableListOf()
        binding.tvEmpty.isVisible = false
        if (query.trim().isEmpty()) return

        searching.set(true)
        searchJob = lifecycleScope.launch {
            val pending = mutableListOf<FileEntry>()
            val count = withContext(kotlinx.coroutines.Dispatchers.IO) {
                Scanner.search(root, query) { entry ->
                    pending.add(entry)
                    if (pending.size % 25 == 0) {
                        flush(pending, adapter)
                    }
                }
            }
            flush(pending, adapter)
            binding.tvEmpty.isVisible = count == 0
            binding.tvEmpty.text = getString(R.string.search_no_results)
        }
    }

    private fun flush(pending: MutableList<FileEntry>, adapter: FileAdapter) {
        if (pending.isEmpty()) return
        val added = pending.toList()
        pending.clear()
        runOnUiThread {
            adapter.entries = (adapter.entries + added).toMutableList()
        }
    }

    private fun onResultClick(entry: FileEntry, root: File) {
        com.inayatechlab.filemanagerpro.util.EntryTracker.onOpened(this, entry.path)
        if (entry.isDir) {
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_OPEN_PATH, entry.path)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(intent)
            finish()
        } else if (FileCat.of(entry) == FileCat.IMAGE) {
            val found = (binding.recycler.adapter as? FileAdapter)
                ?.entries
                ?.filter { FileCat.of(it) == FileCat.IMAGE }
                ?.map { it.path }
                .orEmpty()
            val images = if (entry.path in found) found else listOf(entry.path)
            val index = images.indexOf(entry.path).coerceAtLeast(0)
            startActivity(
                Intent(this, com.inayatechlab.filemanagerpro.preview.PreviewActivity::class.java)
                    .putStringArrayListExtra(
                        com.inayatechlab.filemanagerpro.preview.PreviewActivity.EXTRA_PATHS,
                        ArrayList(images)
                    )
                    .putExtra(com.inayatechlab.filemanagerpro.preview.PreviewActivity.EXTRA_INDEX, index)
            )
        } else if (!OpenUtils.openExternal(this, entry.file)) {
            binding.tvEmpty.isVisible = true
            binding.tvEmpty.text = getString(R.string.no_app_found)
        }
    }
}
