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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.inayatechlab.filemanagerpro.MainActivity
import com.inayatechlab.filemanagerpro.R
import com.inayatechlab.filemanagerpro.databinding.FragmentBrowseBinding
import com.inayatechlab.filemanagerpro.model.ClipboardBus
import com.inayatechlab.filemanagerpro.model.FileEntry
import com.inayatechlab.filemanagerpro.model.PathCrumb
import com.inayatechlab.filemanagerpro.ops.FileOps
import com.inayatechlab.filemanagerpro.vault.VaultEngine
import com.inayatechlab.filemanagerpro.vault.VaultFormat
import com.inayatechlab.filemanagerpro.vault.VaultSessionManager
import com.inayatechlab.filemanagerpro.vault.VaultUnlock
import com.inayatechlab.filemanagerpro.preview.PreviewActivity
import com.inayatechlab.filemanagerpro.search.SearchActivity
import com.inayatechlab.filemanagerpro.settings.SettingsActivity
import com.inayatechlab.filemanagerpro.util.Dialogs
import com.inayatechlab.filemanagerpro.util.FileCat
import com.inayatechlab.filemanagerpro.util.OpenUtils
import com.inayatechlab.filemanagerpro.util.SettingsStore
import com.inayatechlab.filemanagerpro.util.StorageRoot
import com.inayatechlab.filemanagerpro.util.StorageUtils
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BrowseFragment : Fragment() {

    companion object {
        fun newInstance() = BrowseFragment()
    }

    private var _binding: FragmentBrowseBinding? = null
    private val binding get() = _binding!!
    private val scope get() = viewLifecycleOwner.lifecycleScope

    private var dir: File = StorageUtils.primaryRoot()
    private var baseDir: File = StorageUtils.primaryRoot()
    private var baseLabel: String = "Internal storage"
    /** When true the Storage home (volume cards) is shown instead of a folder. */
    private var storageHome = true
    private var adapter: FileAdapter? = null
    private var actionMode: ActionMode? = null
    private val loading = AtomicBoolean(false)

    private var sortMode = 0
    private var sortAsc = true
    private var isGrid = false
    /** Folder (canonical path) for which the hidden-files snackbar was already shown. */
    private var hiddenNoticeShownFor: String? = null

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
        readPrefs()

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
        readPrefs()
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
        if (_binding == null) return // view not created yet — onResume will reload
        syncMenu()
        reload()
    }

    // ---------------------------------------------------------------- browsing

    fun isAtRoot(): Boolean =
        dir.canonicalPath == baseDir.canonicalPath

    fun navigateInto(newDir: File) {
        if (!newDir.isDirectory) return
        dir = newDir
        storageHome = false
        syncMenu()
        reload()
    }

    fun navigateToPath(path: String) {
        val f = File(path)
        if (!f.isDirectory) return
        dir = f
        storageHome = false
        syncMenu()
        reload()
    }

    /** Opens a storage volume card from the Storage home. */
    private fun openRoot(root: StorageRoot) {
        dir = root.file
        baseDir = root.file
        baseLabel = root.label
        storageHome = false
        syncMenu()
        reload()
    }

    fun goUp(): Boolean {
        if (storageHome) return false // back button exits the app from home
        if (isAtRoot()) { // top of a volume → back to the Storage home
            storageHome = true
            reload()
            return true
        }
        val parent = dir.parentFile
        if (parent == null) {
            storageHome = true
            reload()
            return true
        }
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
        act.installBrowseMenu(::onMenuItem)

        if (storageHome) {
            act.setAppTitle(getString(R.string.nav_storage))
            act.showCrumbBarVisible(false)
            act.setUpButton(false) {}
            act.currentMenu()?.let { menu ->
                menu.findItem(R.id.action_search)?.isVisible = false
                menu.findItem(R.id.action_new_file)?.isVisible = false
                menu.findItem(R.id.action_new_folder)?.isVisible = false
                menu.findItem(R.id.action_paste)?.isVisible = false
                menu.findItem(R.id.action_sort)?.isVisible = false
                menu.findItem(R.id.action_hidden)?.isVisible = false
                menu.findItem(R.id.action_select_all)?.isVisible = false
            }
            return
        }

        val crumbs = crumbs()
        // Up arrow always shown inside a volume: at the top level it goes back
        // to the Storage home screen.
        act.showCrumbBarVisible(true)
        act.setCrumbs(crumbs) { crumb ->
            val target = File(crumb.path)
            if (target.isDirectory) navigateInto(target)
        }
        act.setUpButton(true) { goUp() }
        act.setAppTitle(if (isAtRoot()) baseLabel else dir.name)

        act.currentMenu()?.findItem(R.id.action_paste)?.isVisible = ClipboardBus.isActive
        act.currentMenu()?.findItem(R.id.action_hidden)?.let {
            val show = SettingsStore.showHiddenFiles(requireContext())
            it.isChecked = show
            it.title = getString(if (show) R.string.hidden_hide else R.string.hidden_show)
        }
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
                        if (err == null) reload() else snack(localizeOpError(err))
                    }
                }
                true
            }
            R.id.action_new_file -> {
                Dialogs.promptText(
                    requireContext(),
                    getString(R.string.dialog_new_file_title),
                    "", getString(R.string.hint_new_file_name), getString(R.string.action_ok)
                ) { name ->
                    scope.launch {
                        val err = FileOps.createFile(dir, name)
                        if (err == null) {
                            snack(getString(R.string.ops_created, name.trim()))
                            reload()
                        } else {
                            snack(localizeOpError(err))
                        }
                    }
                }
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(requireContext(), SettingsActivity::class.java))
                true
            }
            R.id.action_hidden -> {
                val show = !SettingsStore.showHiddenFiles(requireContext())
                SettingsStore.setShowHiddenFiles(requireContext(), show)
                hiddenNoticeShownFor = null
                snack(getString(if (show) R.string.hidden_now_visible else R.string.hidden_now_hidden))
                syncMenu()
                reload()
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
                    SettingsStore.setSortMode(requireContext(), mode)
                    SettingsStore.setSortAscending(requireContext(), asc)
                    SettingsStore.setGridView(requireContext(), grid)
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
                R.id.action_vault -> {
                    addSelectionToVault(selected)
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
        val res = if (cut) R.string.ops_cut_clipboard else R.string.ops_copied_clipboard
        snack(getString(res, items.size))
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
            snack(getString(R.string.ops_paste_self))
            return
        }
        val cut = ClipboardBus.isCut
        runOp(getString(if (cut) R.string.ops_moving else R.string.ops_copying)) {
            val r = FileOps.copyOrMove(items, dir, cut)
            withContext(Dispatchers.Main) {
                ClipboardBus.entries = emptyList()
                ClipboardBus.isCut = false
                syncMenu()
                if (r.failed > 0) snack(r.errors.joinToString("\n").take(240))
                else snack(getString(R.string.ops_pasted, r.done))
                reload()
            }
        }
    }

    private fun confirmAndDelete(selected: List<FileEntry>) {
        if (!SettingsStore.confirmDelete(requireContext())) {
            doDelete(selected)
            return
        }
        val sample = selected.take(3).joinToString { it.name }
        val more = if (selected.size > 3) {
            "\n" + getString(R.string.ops_and_more, selected.size - 3)
        } else {
            ""
        }
        Dialogs.confirm(
            requireContext(),
            getString(R.string.dialog_delete_title),
            getString(R.string.dialog_delete_message) + "\n\n$sample$more",
            getString(R.string.action_delete_confirm)
        ) {
            doDelete(selected)
        }
    }

    private fun doDelete(selected: List<FileEntry>) {
        runOp(getString(R.string.ops_deleting)) {
            val r = FileOps.delete(selected)
            withContext(Dispatchers.Main) {
                exitSelectionMode()
                if (r.failed > 0) snack(r.errors.joinToString("\n").take(240))
                else snack(getString(R.string.ops_deleted, r.done))
                reload()
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
                    if (err != null) snack(localizeOpError(err))
                    else {
                        exitSelectionMode()
                        reload()
                    }
                }
            }
        }
    }

    private fun compress(selected: List<FileEntry>) {
        runOp(getString(R.string.compressing)) {
            val created = FileOps.zip(selected)
            withContext(Dispatchers.Main) {
                snack(getString(R.string.ops_created, created.name))
                reload()
            }
        }
    }

    private fun extractZip(entry: FileEntry) {
        runOp(getString(R.string.extracting)) {
            val dest = FileOps.extract(File(entry.path))
            withContext(Dispatchers.Main) {
                snack(getString(R.string.ops_extracted_to, dest.name))
                reload()
            }
        }
    }

    // ---------------------------------------------------------------- vault (#20)

    /** Adds the selected items into a vault: pick -> unlock -> encrypt -> optional delete. */
    private fun addSelectionToVault(items: List<FileEntry>) {
        val act = requireActivity() as? androidx.appcompat.app.AppCompatActivity ?: return
        val ctx = requireContext()
        scope.launch {
            val vaults = withContext(Dispatchers.IO) {
                VaultEngine.discoverVaults(StorageUtils.roots().map { it.file })
            }
            if (_binding == null) return@launch
            if (vaults.isEmpty()) {
                snack(getString(R.string.vlt_none_hint))
                return@launch
            }
            val target = if (vaults.size == 1) vaults.first() else {
                val names: Array<CharSequence> =
                    vaults.map { VaultFormat.displayName(it) as CharSequence }.toTypedArray()
                var picked: java.io.File? = null
                val latch = java.util.concurrent.CountDownLatch(1)
                // Back on the main thread here (lifecycleScope), safe to show UI.
                MaterialAlertDialogBuilder(ctx)
                    .setTitle(R.string.vlt_pick_vault)
                    .setItems(names) { _, which ->
                        picked = vaults[which]
                        latch.countDown()
                    }
                    .setOnCancelListener { latch.countDown() }
                    .setNegativeButton(R.string.action_cancel) { _, _ -> latch.countDown() }
                    .show()
                withContext(Dispatchers.IO) { latch.await() }
                picked ?: return@launch
            }
            // Unlock flow (password or biometric) then confirm originals handling.
            VaultUnlock.prompt(act, scope, target,
                onUnlocked = { session -> confirmVaultAdd(session, items) },
                onDismissed = {}
            )
        }
    }

    private fun confirmVaultAdd(session: VaultEngine.Session, items: List<FileEntry>) {
        val ctx = requireContext()
        val options = arrayOf(
            getString(R.string.vlt_move_delete_originals),
            getString(R.string.vlt_move_copy_originals),
            getString(R.string.action_cancel)
        )
        MaterialAlertDialogBuilder(ctx)
            .setTitle(getString(R.string.vlt_move_title, items.size, session.name))
            .setItems(options) { _, which ->
                if (which == 2) {
                    VaultSessionManager.close()
                    return@setItems
                }
                val deleteOriginals = which == 0
                val progress = Dialogs.showProgress(ctx, getString(R.string.vlt_encrypting))
                scope.launch {
                    try {
                        val expanded = withContext(Dispatchers.IO) {
                            expandVaultItems(items)
                        }
                        if (expanded.isEmpty()) {
                            snack(getString(R.string.vlt_move_nothing))
                            return@launch
                        }
                        val outcome = withContext(Dispatchers.IO) {
                            VaultEngine.addFiles(
                                session, expanded,
                                onProgress = { _, _, _ -> }
                            )
                        }
                        if (deleteOriginals && outcome.addedRelPaths.isNotEmpty()) {
                            val toDelete = expanded
                                .filter { it.relPath in outcome.addedRelPaths }
                                .map { it.source }
                            withContext(Dispatchers.IO) { deleteQuietly(toDelete) }
                        }
                        val msg = if (outcome.errors.isEmpty()) {
                            getString(R.string.vlt_move_done_fmt, outcome.added, session.name)
                        } else {
                            getString(R.string.vlt_move_partial_fmt, outcome.added, outcome.errors.size)
                        }
                        snack(msg)
                    } finally {
                        progress.dismiss()
                        VaultSessionManager.close()
                    }
                }
            }
            .show()
    }

    private suspend fun deleteQuietly(files: List<java.io.File>) {
        fun del(f: java.io.File) {
            if (f.isFile) f.delete()
            else if (f.isDirectory) f.deleteRecursively()
        }
        for (f in files) runCatching { del(f) }
    }

    /** Expands selected files/folders into vault items with relative paths. */
    private suspend fun expandVaultItems(items: List<FileEntry>): List<VaultEngine.AddItem> {
        val out = mutableListOf<VaultEngine.AddItem>()
        val seen = HashSet<String>()
        fun walk(f: java.io.File, prefix: String?) {
            val rel = if (prefix == null) f.name else "$prefix/${f.name}"
            if (!seen.add(rel)) return
            if (f.isDirectory) {
                f.listFiles()?.forEach { child -> walk(child, rel) }
            } else if (f.isFile) {
                out.add(VaultEngine.AddItem(rel, f))
            }
        }
        val topLevel = items.filter { e ->
            val path = e.file.canonicalPath
            items.none { other ->
                other !== e && other.isDir && path.startsWith(other.file.canonicalPath + java.io.File.separator)
            }
        }
        topLevel.forEach { walk(it.file, null) }
        return out
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
        val b = _binding
        if (b == null) { // view gone/not ready — skip; a later lifecycle event reloads
            loading.set(false)
            return
        }
        b.swipe.isRefreshing = false

        if (!StorageUtils.isStoragePermitted(requireContext())) {
            loading.set(false)
            b.tvEmpty.isVisible = true
            b.tvEmpty.text = getString(R.string.storage_permission_needed)
            b.recycler.adapter = null
            return
        }

        if (storageHome) {
            scope.launch {
                val roots = withContext(Dispatchers.IO) { StorageUtils.roots() }
                val bb = _binding
                if (bb == null) {
                    loading.set(false)
                    return@launch
                }
                bb.tvEmpty.isVisible = roots.isEmpty()
                bb.tvEmpty.text = getString(R.string.storage_empty)
                bb.tvEmpty.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                if (bb.recycler.layoutManager !is LinearLayoutManager) {
                    bb.recycler.layoutManager = LinearLayoutManager(bb.recycler.context)
                }
                bb.recycler.adapter = StorageRootAdapter(roots) { openRoot(it) }
                bb.swipe.isRefreshing = false
                loading.set(false)
            }
            return
        }

        val listDir = dir
        val mode = sortMode
        val asc = sortAsc
        val grid = isGrid
        val showHidden = SettingsStore.showHiddenFiles(requireContext())
        scope.launch {
            val entries = withContext(Dispatchers.IO) {
                val files = listDir.listFiles() ?: return@withContext emptyList<FileEntry>()
                files
                    .asSequence()
                    .filter { showHidden || !it.name.startsWith(".") }
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
            val b = _binding
            if (b == null || listDir != dir) { // view destroyed or stale request
                loading.set(false)
                return@launch
            }
            b.tvEmpty.isVisible = entries.isEmpty()
            b.tvEmpty.text = getString(R.string.folder_empty)
            b.tvEmpty.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)

            val newAdapter = FileAdapter(grid).apply {
                this.entries = entries.toMutableList()
                onItemClick = { openEntry(it) }
                onItemLongClick = { enterSelectionMode() }
                onSelectionChanged = { actionMode?.invalidate() }
            }
            adapter = newAdapter
            b.recycler.adapter = newAdapter
            val lm = b.recycler.layoutManager
            val needNewLm = if (grid) lm !is GridLayoutManager else lm !is LinearLayoutManager
            if (needNewLm || b.recycler.layoutManager == null) {
                b.recycler.layoutManager =
                    if (grid) GridLayoutManager(b.recycler.context, gridSpan())
                    else LinearLayoutManager(b.recycler.context)
            }
            b.swipe.isRefreshing = false
            loading.set(false)
            syncMenu()
            if (showHidden) {
                val hasHidden = entries.any { it.name.startsWith(".") }
                if (hasHidden && hiddenNoticeShownFor != listDir.canonicalPath) {
                    hiddenNoticeShownFor = listDir.canonicalPath
                    showHiddenNotice()
                }
            }
        }
    }

    /** Snackbar that tells the user hidden files are on screen + one-tap hide action. */
    private fun showHiddenNotice() {
        view?.let {
            Snackbar.make(it, getString(R.string.hidden_files_visible), Snackbar.LENGTH_LONG)
                .setAction(getString(R.string.hidden_toggle_hide)) {
                    SettingsStore.setShowHiddenFiles(requireContext(), false)
                    hiddenNoticeShownFor = null
                    syncMenu()
                    reload()
                }
                .show()
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

    /** Translate FileOps error keys to the current UI language (fallback: raw message). */
    private fun localizeOpError(err: String): String = when (err) {
        "Invalid name" -> getString(R.string.ops_error_invalid_name)
        "A folder with this name already exists" -> getString(R.string.ops_error_name_exists)
        "A file with this name already exists" -> getString(R.string.ops_error_name_exists)
        "Could not create folder" -> getString(R.string.ops_error_cannot_create)
        "Could not create file" -> getString(R.string.ops_error_cannot_create)
        "Rename failed" -> getString(R.string.ops_error_rename_failed)
        else -> err
    }

    private fun readPrefs() {
        sortMode = SettingsStore.sortMode(requireContext())
        sortAsc = SettingsStore.sortAscending(requireContext())
        isGrid = SettingsStore.gridView(requireContext())
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
