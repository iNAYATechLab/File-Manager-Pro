package com.inayatechlab.filemanagerpro.browse

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.inayatechlab.filemanagerpro.MainActivity
import com.inayatechlab.filemanagerpro.R
import com.inayatechlab.filemanagerpro.databinding.FragmentBrowseBinding
import com.inayatechlab.filemanagerpro.model.ClipboardBus
import com.inayatechlab.filemanagerpro.model.FileEntry
import com.inayatechlab.filemanagerpro.model.PathCrumb
import com.inayatechlab.filemanagerpro.ops.FileOps
import com.inayatechlab.filemanagerpro.preview.PreviewActivity
import com.inayatechlab.filemanagerpro.search.SearchActivity
import com.inayatechlab.filemanagerpro.util.Dialogs
import com.inayatechlab.filemanagerpro.util.FileCat
import com.inayatechlab.filemanagerpro.util.OpenUtils
import com.inayatechlab.filemanagerpro.util.StorageUtils
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BrowseFragment : Fragment() {

    companion object {
        private const val PREFS = "fm_settings"
        private const val KEY_SORT = "sort_mode" // 0 name, 1 date, 2 size, 3 type
        private const val KEY_SORT_ASC = "sort_asc"
        private const val KEY_GRID = "grid_view"
        fun newInstance() = BrowseFragment()
    }

    private var _binding: FragmentBrowseBinding? = null
    private val binding get() = _binding!!
    private val scope get() = viewLifecycleOwner.lifecycleScope

    private lateinit var prefs: android.content.SharedPreferences
    private var dir: File = StorageUtils.primaryRoot()
    private var baseDir: File = StorageUtils.primaryRoot()
    private var baseLabel: String = "Internal storage"
    private var adapter: FileAdapter? = null
    private var actionMode: ActionMode? = null
    private val loading = AtomicBoolean(false)

    private var sortMode = 0
    private var sortAsc = true
    private var isGrid = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { reloadIfPermitted() }

    private fun reloadIfPermitted() {
        if (StorageUtils.isStoragePermitted(requireContext())) reload()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBrowseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = requireContext().getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        sortMode = prefs.getInt(KEY_SORT, 0)
        sortAsc = prefs.getBoolean(KEY_SORT_ASC, true)
        isGrid = prefs.getBoolean(KEY_GRID, false)

        binding.swipe.setOnRefreshListener { reload() }
        binding.tvEmpty.setOnClickListener {
            if (!StorageUtils.isStoragePermitted(requireContext())) {
                StorageUtils.requestStorageAccess(requireContext())
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (isHidden) return // hidden fragments still receive onResume
        (activity as? MainActivity)?.showCrumbBarVisible(true)
        syncMenu()
        reload()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            exitSelectionMode()
        } else {
            becomeVisible()
        }
    }

    override fun onDestroyView() {
        exitSelectionMode()
        _binding = null
        super.onDestroyView()
    }

    /** Called whenever the storage tab is shown again. */
    fun becomeVisible() {
        exitSelectionMode()
        syncMenu()
        reload()
    }

    // ---------------------------------------------------------------- browsing

    fun isAtRoot(): Boolean =
        dir.canonicalPath == baseDir.canonicalPath

    fun navigateInto(newDir: File) {
        if (!newDir.isDirectory) return
        dir = newDir
        reload()
    }

    fun navigateToPath(path: String) {
        val f = File(path)
        if (!f.isDirectory) return
        dir = f
        syncMenu()
        reload()
    }

    fun goUp(): Boolean {
        if (isAtRoot()) return false
        val parent = dir.parentFile
        if (parent == null) return false
        dir = parent
        reload()
        return true
    }

    fun handleBack(): Boolean = goUp()

    private fun crumbs(): List<PathCrumb> {
        val out = mutableListOf<PathCrumb>()
        val basePath = baseDir.canonicalPath
        out.add(PathCrumb(baseLabel, basePath))
        if (dir.canonicalPath == basePath) return out
        val parts = mutableListOf<File>()
        var current: File? = dir
        while (current != null && current.canonicalPath != basePath) {
            parts.add(current)
            current = current.parentFile
        }
        parts.reverse()
        parts.forEach { out.add(PathCrumb(it.name, it.canonicalPath)) }
        return out
    }

    // ---------------------------------------------------------------- toolbar / menu

    private fun syncMenu() {
        val act = activity as? MainActivity ?: return
        val crumbs = crumbs()
        val upVisible = !isAtRoot()
        act.showCrumbBarVisible(upVisible)
        act.setCrumbs(crumbs) { crumb ->
            val target = File(crumb.path)
            if (target.isDirectory) navigateInto(target)
        }
        act.setUpButton(upVisible) { goUp() }
        act.setAppTitle(if (isAtRoot()) baseLabel else dir.name)

        act.installBrowseMenu(::onMenuItem)
        act.currentMenu()?.findItem(R.id.action_paste)?.isVisible = ClipboardBus.isActive
    }

    private fun onMenuItem(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_search -> {
                startActivity(
                    Intent(requireContext(), SearchActivity::class.java)
                        .putExtra(SearchActivity.EXTRA_ROOT, dir.canonicalPath)
                )
                true
            }
            R.id.action_new_folder -> {
                Dialogs.promptText(
                    requireContext(),
                    getString(R.string.dialog_new_folder_title),
                    "", getString(R.string.hint_name), getString(R.string.action_ok)
                ) { name ->
                    scope.launch {
                        val err = FileOps.createFolder(dir, name)
                        if (err == null) reload() else snack(err)
                    }
                }
                true
            }
            R.id.action_select_all -> {
                if (actionMode == null) enterSelectionMode()
                adapter?.selectAll()
                true
            }
            R.id.action_paste -> {
                performPaste()
                true
            }
            R.id.action_sort -> {
                SortDialog.show(requireContext(), sortMode, sortAsc, isGrid) { mode, asc, grid ->
                    sortMode = mode
                    sortAsc = asc
                    isGrid = grid
                    prefs.edit()
                        .putInt(KEY_SORT, mode)
                        .putBoolean(KEY_SORT_ASC, asc)
                        .putBoolean(KEY_GRID, grid)
                        .apply()
                    reload()
                }
                true
            }
            R.id.action_refresh -> {
                reload()
                true
            }
            else -> false
        }
    }

    // ---------------------------------------------------------------- selection

    private fun enterSelectionMode() {
        if (actionMode != null) return
        actionMode = (activity as AppCompatActivity).startSupportActionMode(actionModeCallback)
    }

    private fun exitSelectionMode() {
        actionMode?.finish()
        actionMode = null
        adapter?.selectionMode = false
        adapter?.clearSelection()
    }

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.menu_selection, menu)
            adapter?.selectionMode = true
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            val count = adapter?.selected?.size ?: 0
            mode.title = "$count ${getString(R.string.action_selected)}"
            menu.findItem(R.id.action_rename)?.isVisible = count == 1
            menu.findItem(R.id.action_extract)?.isVisible =
                count == 1 && adapter?.selectedEntries()?.firstOrNull()?.extension == "zip"
            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val selected = adapter?.selectedEntries()?.toList() ?: emptyList()
            if (selected.isEmpty()) return false
            return when (item.itemId) {
                R.id.action_clear_selection -> {
                    adapter?.clearSelection()
                    mode.finish()
                    true
                }
                R.id.action_select_all -> {
                    adapter?.selectAll()
                    true
                }
                R.id.action_cut -> {
                    stageClipboard(selected, cut = true)
                    mode.finish()
                    true
                }
                R.id.action_copy -> {
                    stageClipboard(selected, cut = false)
                    mode.finish()
                    true
                }
                R.id.action_delete -> {
                    confirmAndDelete(selected)
                    true
                }
                R.id.action_rename -> {
                    renameSingle(selected.first())
                    true
                }
                R.id.action_compress -> {
                    compress(selected)
                    mode.finish()
                    true
                }
                R.id.action_extract -> {
                    extractZip(selected.first())
                    mode.finish()
                    true
                }
                R.id.action_share -> {
                    share(selected)
                    mode.finish()
                    true
                }
                R.id.action_properties -> {
                    properties(selected)
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            adapter?.selectionMode = false
            adapter?.clearSelection()
            actionMode = null
        }
    }

    // ---------------------------------------------------------------- file operations

    private fun stageClipboard(items: List<FileEntry>, cut: Boolean) {
        ClipboardBus.entries = items
        ClipboardBus.isCut = cut
        snack("${items.size} item(s) ${if (cut) "cut" else "copied"} to clipboard")
        syncMenu()
    }

    private fun performPaste() {
        val items = ClipboardBus.entries
        if (items.isEmpty()) return
        val intoSelf = items.any { e ->
            val src = File(e.path)
            src == dir || (src.isDirectory && dir.canonicalPath.startsWith(src.canonicalPath + File.separator))
        }
        if (intoSelf) {
            snack("Cannot paste into its own source folder")
            return
        }
        val cut = ClipboardBus.isCut
        runOp(if (cut) "Moving…" else "Copying…") {
            val r = FileOps.copyOrMove(items, dir, cut)
            withContext(Dispatchers.Main) {
                ClipboardBus.entries = emptyList()
                ClipboardBus.isCut = false
                syncMenu()
                if (r.failed > 0) snack(r.errors.joinToString("\n").take(240))
                else snack("Pasted ${r.done} item(s)")
                reload()
            }
        }
    }

    private fun confirmAndDelete(selected: List<FileEntry>) {
        val sample = selected.take(3).joinToString { it.name }
        val more = if (selected.size > 3) "\n+${selected.size - 3} more" else ""
        Dialogs.confirm(
            requireContext(),
            getString(R.string.dialog_delete_title),
            getString(R.string.dialog_delete_message) + "\n\n$sample$more",
            getString(R.string.action_delete_confirm)
        ) {
            runOp("Deleting…") {
                val r = FileOps.delete(selected)
                withContext(Dispatchers.Main) {
                    exitSelectionMode()
                    if (r.failed > 0) snack(r.errors.joinToString("\n").take(240))
                    else snack("Deleted ${r.done} item(s)")
                    reload()
                }
            }
        }
    }

    private fun renameSingle(entry: FileEntry) {
        Dialogs.promptText(
            requireContext(),
            getString(R.string.dialog_rename_title),
            entry.name, getString(R.string.hint_new_name), getString(R.string.action_ok)
        ) { newName ->
            scope.launch {
                val err = FileOps.rename(dir, entry.name, newName)
                withContext(Dispatchers.Main) {
                    if (err != null) snack(err)
                    else {
                        exitSelectionMode()
                        reload()
                    }
                }
            }
        }
    }

    private fun compress(selected: List<FileEntry>) {
        runOp("Compressing…") {
            val created = FileOps.zip(selected)
            withContext(Dispatchers.Main) {
                snack("Created ${created.name}")
                reload()
            }
        }
    }

    private fun extractZip(entry: FileEntry) {
        runOp("Extracting…") {
            val dest = FileOps.extract(File(entry.path))
            withContext(Dispatchers.Main) {
                snack("Extracted to ${dest.name}")
                reload()
            }
        }
    }

    private fun share(selected: List<FileEntry>) {
        if (!OpenUtils.share(requireContext(), selected)) {
            snack(getString(R.string.no_app_found))
        }
    }

    private fun properties(selected: List<FileEntry>) {
        selected.forEach { Dialogs.properties(requireContext(), it, scope) }
    }

    private fun runOp(message: String, op: suspend () -> Unit) {
        val dlg = Dialogs.showProgress(requireContext(), message)
        scope.launch {
            try {
                op()
            } catch (e: Exception) {
                snack(e.message ?: getString(R.string.error))
            } finally {
                dlg.dismiss()
            }
        }
    }

    // ---------------------------------------------------------------- loading

    private fun reload() {
        if (loading.getAndSet(true)) return
        binding.swipe.isRefreshing = false

        if (!StorageUtils.isStoragePermitted(requireContext())) {
            loading.set(false)
            binding.tvEmpty.isVisible = true
            binding.tvEmpty.text = getString(R.string.storage_permission_needed)
            binding.recycler.adapter = null
            return
        }

        val listDir = dir
        val mode = sortMode
        val asc = sortAsc
        val grid = isGrid
        scope.launch {
            val entries = withContext(Dispatchers.IO) {
                val files = listDir.listFiles() ?: return@withContext emptyList<FileEntry>()
                files
                    .asSequence()
                    .filter { !it.name.startsWith(".") }
                    .map { f ->
                        if (f.isDirectory) {
                            FileEntry(f.name, f.canonicalPath, true, lastModified = f.lastModified())
                                .withChildCount(f.list()?.size ?: 0)
                        } else {
                            FileEntry(f.name, f.canonicalPath, false, f.length(), f.lastModified())
                        }
                    }
                    .sortedWith(FileOpsComparator.comparator(mode, asc))
                    .toList()
            }
            if (listDir != dir) {
                loading.set(false)
                return@launch
            }
            val cur = requireContext()
            binding.tvEmpty.isVisible = entries.isEmpty()
            binding.tvEmpty.text = getString(R.string.folder_empty)
            binding.tvEmpty.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)

            val newAdapter = FileAdapter(grid).apply {
                this.entries = entries.toMutableList()
                onItemClick = { openEntry(it) }
                onItemLongClick = { enterSelectionMode() }
                onSelectionChanged = { actionMode?.invalidate() }
            }
            adapter = newAdapter
            binding.recycler.adapter = newAdapter
            val lm = binding.recycler.layoutManager
            val needNewLm = if (grid) lm !is GridLayoutManager else lm !is LinearLayoutManager
            if (needNewLm || binding.recycler.layoutManager == null) {
                binding.recycler.layoutManager =
                    if (grid) GridLayoutManager(cur, gridSpan())
                    else LinearLayoutManager(cur)
            }
            binding.swipe.isRefreshing = false
            loading.set(false)
            syncMenu()
        }
    }

    private fun gridSpan(): Int {
        val density = resources.displayMetrics.density
        val widthDp = resources.displayMetrics.widthPixels / density
        return (widthDp / 112f).toInt().coerceIn(2, 6)
    }

    // ---------------------------------------------------------------- opening

    private fun openEntry(entry: FileEntry) {
        if (entry.isDir) {
            navigateInto(entry.file)
            return
        }
        if (FileCat.of(entry) == FileCat.IMAGE) {
            val images = (adapter?.entries ?: emptyList())
                .filter { FileCat.of(it) == FileCat.IMAGE }
                .map { it.path }
            val index = images.indexOf(entry.path).coerceAtLeast(0)
            startActivity(
                Intent(requireContext(), PreviewActivity::class.java)
                    .putStringArrayListExtra(PreviewActivity.EXTRA_PATHS, ArrayList(images))
                    .putExtra(PreviewActivity.EXTRA_INDEX, index)
            )
        } else if (!OpenUtils.openExternal(requireContext(), entry.file)) {
            snack(getString(R.string.no_app_found))
        }
    }

    private fun snack(text: String) {
        view?.let { Snackbar.make(it, text, Snackbar.LENGTH_LONG).show() }
    }
}

/** Sorting logic shared with other lists. */
object FileOpsComparator {
    fun comparator(mode: Int, asc: Boolean): Comparator<FileEntry> = Comparator { a, b ->
        // directories always first
        if (a.isDir != b.isDir) return@Comparator if (a.isDir) -1 else 1
        val cmp: Int = when (mode) {
            1 -> a.lastModified.compareTo(b.lastModified)
            2 -> a.size.compareTo(b.size)
            3 -> FileCat.of(a).ordinal.compareTo(FileCat.of(b).ordinal)
            else -> a.name.lowercase().compareTo(b.name.lowercase())
        }
        if (asc) cmp else -cmp
    }
}
