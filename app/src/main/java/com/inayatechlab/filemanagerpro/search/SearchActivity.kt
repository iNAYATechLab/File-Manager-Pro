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
import com.inayatechlab.filemanagerpro.browse.ActionsSheet
import com.inayatechlab.filemanagerpro.browse.FileAdapter
import com.inayatechlab.filemanagerpro.databinding.ActivitySearchBinding
import com.inayatechlab.filemanagerpro.model.FileEntry
import com.inayatechlab.filemanagerpro.util.Dialogs
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
    private var roots: List<File> = listOf(StorageUtils.primaryRoot())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        val folderRoot = intent.getStringExtra(EXTRA_ROOT)
            ?.let { File(it) }
            ?.takeIf { it.isDirectory }
            ?: StorageUtils.primaryRoot()

        val adapter = FileAdapter(isGrid = false, selectable = false).apply {
            onItemClick = { entry -> onResultClick(entry) }
            // Built once, here in onCreate, so the row's ⋮ (and long-press)
            // open the same action sheet the other screens use.
            onItemMenu = { entry -> showResultActions(entry) }
        }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        binding.etQuery.requestFocus()

        // Scope: current folder vs whole storage (all readable volumes).
        binding.chipScopeCurrent.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                roots = listOf(folderRoot)
                runSearch(adapter)
            }
        }
        binding.chipScopeWhole.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                roots = StorageUtils.roots().map { it.file }
                runSearch(adapter)
            }
        }

        // Result type filter: all / files / folders.
        binding.chipGroupType.setOnCheckedStateChangeListener { _, _ ->
            runSearch(adapter)
        }

        binding.etQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                runSearch(adapter)
            }
        })
    }

    private fun selectedType(): Scanner.SearchType = when (binding.chipGroupType.checkedChipId) {
        R.id.chipTypeFiles -> Scanner.SearchType.FILES
        R.id.chipTypeFolders -> Scanner.SearchType.FOLDERS
        else -> Scanner.SearchType.ALL
    }

    private fun runSearch(adapter: FileAdapter) {
        searchJob?.cancel()
        searching.set(false)
        adapter.entries = mutableListOf()
        binding.tvEmpty.isVisible = false
        val query = binding.etQuery.text?.toString().orEmpty().trim()
        if (query.isEmpty()) return

        searching.set(true)
        binding.progress.isVisible = true
        searchJob = lifecycleScope.launch {
            val pending = mutableListOf<FileEntry>()
            val count = withContext(kotlinx.coroutines.Dispatchers.IO) {
                Scanner.search(roots, query, selectedType()) { entry ->
                    pending.add(entry)
                    if (pending.size % 25 == 0) {
                        flush(pending, adapter)
                    }
                }
            }
            flush(pending, adapter)
            binding.progress.isVisible = false
            binding.tvEmpty.isVisible = count == 0
            binding.tvEmpty.text = getString(R.string.search_no_results)
            searching.set(false)
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

    /** Actions for one search result, in the mockup sheet. */
    private fun showResultActions(entry: FileEntry) {
        val picked = listOf(entry)
        val actions = mutableListOf<ActionsSheet.Action>()

        actions += ActionsSheet.Action(
            R.string.action_open,
            if (entry.isDir) R.drawable.ic_folder else R.drawable.ic_generic_file
        ) { onResultClick(entry) }
        if (!entry.isDir) {
            actions += ActionsSheet.Action(
                R.string.action_open_with, R.drawable.ic_generic_file
            ) {
                if (!OpenUtils.openWith(this, entry.file)) {
                    snack(getString(R.string.no_app_found))
                }
            }
        }
        actions += ActionsSheet.Action(R.string.action_properties, R.drawable.ic_info) {
            Dialogs.properties(this, entry, lifecycleScope)
        }
        actions += ActionsSheet.Action(R.string.action_share, R.drawable.ic_share) {
            if (!OpenUtils.share(this, picked)) snack(getString(R.string.no_app_found))
        }

        ActionsSheet.show(this, entry.name, actions)
    }

    private fun onResultClick(entry: FileEntry) {
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
        } else if (com.inayatechlab.filemanagerpro.util.TextFiles.isTextFile(entry.name)) {
            com.inayatechlab.filemanagerpro.textviewer.TextActivity.start(this, entry.file)
        } else if (!OpenUtils.openExternal(this, entry.file)) {
            binding.tvEmpty.isVisible = true
            binding.tvEmpty.text = getString(R.string.no_app_found)
        }
    }
}
