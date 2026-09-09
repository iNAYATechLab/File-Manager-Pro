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
import com.inayatechlab.filemanagerpro.model.LibraryStore
import com.inayatechlab.filemanagerpro.model.PathCrumb
import com.inayatechlab.filemanagerpro.ops.ConflictPolicy
import com.inayatechlab.filemanagerpro.ops.Extractor
import com.inayatechlab.filemanagerpro.ops.FileOps
import com.inayatechlab.filemanagerpro.ops.TrashOps
import com.inayatechlab.filemanagerpro.vault.VaultEngine
import com.inayatechlab.filemanagerpro.vault.VaultFormat
import com.inayatechlab.filemanagerpro.vault.VaultSessionManager
import com.inayatechlab.filemanagerpro.vault.VaultUnlock
import com.inayatechlab.filemanagerpro.preview.PreviewActivity
import com.inayatechlab.filemanagerpro.saf.SafBrowserActivity
import com.inayatechlab.filemanagerpro.saf.SafGrants
import com.inayatechlab.filemanagerpro.search.SearchActivity
import com.inayatechlab.filemanagerpro.textviewer.TextActivity
import com.inayatechlab.filemanagerpro.settings.SettingsActivity
import com.inayatechlab.filemanagerpro.util.Dialogs
import com.inayatechlab.filemanagerpro.util.OpProgressDialog
import com.inayatechlab.filemanagerpro.util.FileCat
import com.inayatechlab.filemanagerpro.util.OpenUtils
import com.inayatechlab.filemanagerpro.util.TextFiles
import com.inayatechlab.filemanagerpro.util.SettingsStore
import com.inayatechlab.filemanagerpro.util.StorageRoot
import com.inayatechlab.filemanagerpro.util.StorageUtils
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.suspendCancellableCoroutine
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
    /** Active copy/move job so the user can cancel it from the progress dialog. */
    private var transferJob: Job? = null

    private var sortMode = 0
    private var sortAsc = true
    private var isGrid = false
    private var foldersFirst = true
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
        // Storage-home quick access shortcuts -> Library tab sections.
        binding.chipHomeFavorites.setOnClickListener {
            (activity as? MainActivity)?.openLibrarySection(
                com.inayatechlab.filemanagerpro.library.LibrarySection.FAVORITES
            )
        }
        binding.chipHomeRecents.setOnClickListener {
            (activity as? MainActivity)?.openLibrarySection(
                com.inayatechlab.filemanagerpro.library.LibrarySection.RECENT
            )
        }
        binding.chipHomeDownloads.setOnClickListener {
            (activity as? MainActivity)?.openLibrarySection(
                com.inayatechlab.filemanagerpro.library.LibrarySection.DOWNLOADS
            )
        }
        binding.chipHomeTrash.setOnClickListener {
            (activity as? MainActivity)?.openLibrarySection(
                com.inayatechlab.filemanagerpro.library.LibrarySection.TRASH
            )
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
                SortDialog.show(requireContext(), sortMode, sortAsc, isGrid, foldersFirst) { mode, asc, grid, folders ->
                    sortMode = mode
                    sortAsc = asc
                    isGrid = grid
                    foldersFirst = folders
                    SettingsStore.setSortMode(requireContext(), mode)
                    SettingsStore.setSortAscending(requireContext(), asc)
                    SettingsStore.setGridView(requireContext(), grid)
                    SettingsStore.setFoldersFirst(requireContext(), folders)
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

    /** Selection-mode helper: select all entries of one kind in this folder. */
    private fun showSelectTypeDialog() {
        val ctx = requireContext()
        val options = listOf<Pair<String, (FileEntry) -> Boolean>>(
            getString(R.string.select_files) to { e -> !e.isDir },
            getString(R.string.select_folders) to { e -> e.isDir },
            getString(R.string.cat_images) to { e -> FileCat.of(e) == FileCat.IMAGE },
            getString(R.string.cat_videos) to { e -> FileCat.of(e) == FileCat.VIDEO },
            getString(R.string.cat_audio) to { e -> FileCat.of(e) == FileCat.AUDIO },
            getString(R.string.cat_documents) to { e ->
                FileCat.of(e) == FileCat.DOC || FileCat.of(e) == FileCat.PDF ||
                    FileCat.of(e) == FileCat.TEXT
            },
            getString(R.string.cat_archives) to { e -> FileCat.of(e) == FileCat.ARCHIVE },
            getString(R.string.cat_apk) to { e -> FileCat.of(e) == FileCat.APK }
        )
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.action_select_type)
            .setItems(options.map { it.first }.toTypedArray()) { _, which ->
                adapter?.selectAllWhere(options[which].second)
                actionMode?.invalidate()
            }
            .show()
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
                count == 1 && Extractor.isSupportedName(
                    adapter?.selectedEntries()?.firstOrNull()?.name.orEmpty()
                )
            menu.findItem(R.id.action_open_with)?.isVisible =
                count == 1 && adapter?.selectedEntries()?.firstOrNull()?.let { !it.isDir } == true
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
                    confirmTrash(selected)
                    true
                }
                R.id.action_open_with -> {
                    val entry = selected.firstOrNull()
                    if (entry != null && !entry.isDir) {
                        OpenUtils.openWith(requireContext(), entry.file)
                    }
                    mode.finish()
                    true
                }
                R.id.action_select_type -> {
                    showSelectTypeDialog()
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
                    extractArchive(selected.first())
                    mode.finish()
                    true
                }
                R.id.action_vault -> {
                    addSelectionToVault(selected)
                    mode.finish()
                    true
                }
                R.id.action_favorite -> {
                    addSelectionToFavorites(selected)
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
            snack(getString(R.string.xfer_paste_self))
            return
        }
        runTransfer(items, dir, ClipboardBus.isCut)
    }

    /**
     * Runs a copy/move with a cancellable, counter-based progress dialog.
     * Name conflicts ask the user for overwrite / skip / keep-both.
     */
    private fun runTransfer(items: List<FileEntry>, destDir: File, cut: Boolean) {
        val title = getString(if (cut) R.string.xfer_moving else R.string.xfer_copying)
        val progress = OpProgressDialog.create(requireContext(), title, items.size) {
            transferJob?.cancel()
        }
        var applyAll: ConflictPolicy? = null
        transferJob = scope.launch {
            try {
                val r = FileOps.copyOrMove(
                    items, destDir, cut,
                    conflictHandler = { name ->
                        // Runs on the IO dispatcher; resolve decisions on Main.
                        withContext(Dispatchers.Main) {
                            applyAll ?: askConflict(name).also { decision ->
                                if (decision.second) applyAll = decision.first
                            }.first
                        }
                    },
                    onProgress = { done, total, label ->
                        withContext(Dispatchers.Main) { progress.update(done, total, label) }
                    }
                )
                withContext(Dispatchers.Main) {
                    progress.dismiss()
                    if (!r.cancelled) {
                        ClipboardBus.entries = emptyList()
                        ClipboardBus.isCut = false
                        syncMenu()
                    }
                    if (r.cancelled) {
                        snack(getString(R.string.xfer_cancelled))
                    } else if (r.failed > 0) {
                        snack(
                            getString(R.string.xfer_result_failed, r.failed) +
                                "\n" + r.errors.take(2).joinToString("\n")
                        )
                    } else if (r.done == 0 && r.skipped > 0) {
                        snack(getString(R.string.xfer_all_skipped))
                    } else {
                        val base = if (cut) R.string.xfer_moved_fmt else R.string.xfer_copied_fmt
                        var text = getString(base, r.done)
                        if (r.skipped > 0) text += "  " + getString(R.string.xfer_skipped_fmt, r.skipped)
                        if (r.replaced > 0) text += "  " + getString(R.string.xfer_replaced_fmt, r.replaced)
                        snack(text)
                    }
                    reload()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    snack(e.message ?: getString(R.string.error))
                }
            } finally {
                progress.dismiss()
            }
        }
    }

    /**
     * Suspends while showing a conflict dialog. Returns the chosen policy and
     * whether it should be applied to every remaining conflict. Returns a
     * null policy — encoded as cancellation of the whole operation — when the
     * user dismisses the dialog.
     */
    private suspend fun askConflict(fileName: String): Pair<ConflictPolicy, Boolean> =
        suspendCancellableCoroutine { cont ->
            val density = resources.displayMetrics.density
            val cb = android.widget.CheckBox(this@BrowseFragment.requireContext()).apply {
                text = getString(R.string.xfer_conflict_apply_all)
                textSize = 14f
                val pad = (8 * density).toInt()
                setPadding(pad, pad / 2, pad, 0)
            }
            val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.xfer_conflict_title)
                .setMessage(getString(R.string.xfer_conflict_message, fileName))
                .setView(cb)
                .setPositiveButton(R.string.xfer_conflict_overwrite) { _, _ ->
                    cont.resume(ConflictPolicy.OVERWRITE to cb.isChecked)
                }
                .setNeutralButton(R.string.xfer_conflict_keep_both) { _, _ ->
                    cont.resume(ConflictPolicy.KEEP_BOTH to cb.isChecked)
                }
                .setNegativeButton(R.string.xfer_conflict_skip) { _, _ ->
                    cont.resume(ConflictPolicy.SKIP to cb.isChecked)
                }
                .setOnCancelListener { cont.resume(ConflictPolicy.SKIP to cb.isChecked) }
                .show()
            cont.invokeOnCancellation { dialog.dismiss() }
        }

    private fun confirmTrash(selected: List<FileEntry>) {
        if (!SettingsStore.confirmDelete(requireContext())) {
            doTrash(selected)
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
            getString(R.string.trash_confirm_title),
            getString(R.string.trash_confirm_message) + "\n\n$sample$more",
            getString(R.string.trash_confirm_ok)
        ) {
            doTrash(selected)
        }
    }

    /** Deletes are recoverable: items move to the app-managed Trash. */
    private fun doTrash(selected: List<FileEntry>) {
        runOp(getString(R.string.trash_op_progress)) {
            val r = TrashOps.move(requireContext(), selected)
            withContext(Dispatchers.Main) {
                exitSelectionMode()
                snack(
                    when {
                        r.failed == 0 -> getString(R.string.trash_moved_fmt, r.done)
                        r.done == 0 -> getString(R.string.trash_failed_fmt, r.firstFailedName.orEmpty())
                        else -> getString(R.string.trash_partial_fmt, r.done, r.failed)
                    }
                )
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

    /**
     * Extracts any supported archive (zip/tar/tar.gz/tgz/7z/rar) into a
     * sibling folder. Format-specific failures are shown in the UI language.
     */
    private fun extractArchive(entry: FileEntry) {
        runOp(getString(R.string.extracting)) {
            try {
                val dest = Extractor.extract(File(entry.path))
                withContext(Dispatchers.Main) {
                    snack(getString(R.string.arch_extract_done, dest.name))
                    reload()
                }
            } catch (e: Extractor.ExtractException) {
                withContext(Dispatchers.Main) {
                    val res = when (e.failure) {
                        Extractor.Failure.UNSUPPORTED -> R.string.arch_extract_unsupported
                        Extractor.Failure.RAR5 -> R.string.arch_extract_rar5
                        Extractor.Failure.ENCRYPTED -> R.string.arch_extract_encrypted
                        Extractor.Failure.CORRUPT -> R.string.arch_extract_corrupt
                        null -> R.string.arch_extract_failed
                    }
                    snack(getString(res))
                }
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
            b.quickAccessBar.isVisible = true
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
                val perms = requireContext().contentResolver.persistedUriPermissions
                    .map { it.uri }.toSet()
                val granted = SafGrants.list(requireContext()).count { it in perms }
                bb.recycler.adapter = StorageRootAdapter(
                    roots,
                    { openRoot(it) },
                    grantedSafCount = granted,
                    onSafClick = {
                        startActivity(
                            Intent(requireContext(), SafBrowserActivity::class.java)
                        )
                    })
                bb.swipe.isRefreshing = false
                loading.set(false)
            }
            return
        }

        val listDir = dir
        val mode = sortMode
        val asc = sortAsc
        val grid = isGrid
        val folders = foldersFirst
        val showHidden = SettingsStore.showHiddenFiles(requireContext())
        b.quickAccessBar.isVisible = false
        scope.launch {
            val entries = withContext(Dispatchers.IO) {
                val files = listDir.listFiles() ?: return@withContext emptyList<FileEntry>()
                val listed = files
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
                    .toList()
                // Creation time is not exposed via java.io — query it once per
                // entry only when the "Created" sort mode is active.
                val created = if (mode == SettingsStore.SORT_CREATED) {
                    listed.associate { e ->
                        val millis = try {
                            java.nio.file.Files.readAttributes(
                                e.file.toPath(),
                                java.nio.file.attribute.BasicFileAttributes::class.java
                            ).creationTime().toMillis()
                        } catch (ex: Exception) {
                            e.lastModified
                        }
                        e.path to millis
                    }
                } else {
                    null
                }
                listed.sortedWith(FileOpsComparator.comparator(mode, asc, folders, created))
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
        LibraryStore.addRecent(LibraryStore.storeDir(requireContext().filesDir), entry)
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
        } else if (TextFiles.isTextFile(entry.name)) {
            TextActivity.start(requireContext(), entry.file)
        } else if (!OpenUtils.openExternal(requireContext(), entry.file)) {
            snack(getString(R.string.no_app_found))
        }
    }

    /** Adds the selected items to the favorites list. */
    private fun addSelectionToFavorites(selected: List<FileEntry>) {
        if (selected.isEmpty()) return
        val storeDir = LibraryStore.storeDir(requireContext().filesDir)
        selected.forEach { LibraryStore.addFavorite(storeDir, it) }
        snack(getString(R.string.lib_fav_added_fmt, selected.size))
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
        foldersFirst = SettingsStore.foldersFirst(requireContext())
    }
}

/** Sorting logic shared with other lists. */
object FileOpsComparator {
    /**
     * [createdAt] optionally carries real creation times (path -> millis)
     * precomputed by the caller; entries missing from the map fall back to
     * lastModified (creation time is not exposed on every filesystem).
     */
    fun comparator(
        mode: Int,
        asc: Boolean,
        foldersFirst: Boolean = true,
        createdAt: Map<String, Long>? = null
    ): Comparator<FileEntry> = Comparator { a, b ->
        if (a.isDir != b.isDir) {
            // directories first (default) or files first when disabled
            return@Comparator if (a.isDir == foldersFirst) -1 else 1
        }
        val cmp: Int = when (mode) {
            1 -> a.lastModified.compareTo(b.lastModified)
            2 -> a.size.compareTo(b.size)
            3 -> FileCat.of(a).ordinal.compareTo(FileCat.of(b).ordinal)
            4 -> a.extension.lowercase().compareTo(b.extension.lowercase())
            5 -> (createdAt?.get(a.path) ?: a.lastModified)
                .compareTo(createdAt?.get(b.path) ?: b.lastModified)
            else -> a.name.lowercase().compareTo(b.name.lowercase())
        }
        if (asc) cmp else -cmp
    }
}
