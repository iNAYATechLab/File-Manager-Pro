package com.inayatechlab.filemanagerpro.library

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.inayatechlab.filemanagerpro.MainActivity
import com.inayatechlab.filemanagerpro.R
import com.inayatechlab.filemanagerpro.browse.FileAdapter
import com.inayatechlab.filemanagerpro.browse.FileOpsComparator
import com.inayatechlab.filemanagerpro.databinding.FragmentLibraryBinding
import com.inayatechlab.filemanagerpro.model.FileEntry
import com.inayatechlab.filemanagerpro.model.LibraryStore
import com.inayatechlab.filemanagerpro.model.TrashStore
import com.inayatechlab.filemanagerpro.ops.TrashOps
import com.inayatechlab.filemanagerpro.preview.PreviewActivity
import com.inayatechlab.filemanagerpro.textviewer.TextActivity
import com.inayatechlab.filemanagerpro.util.Dialogs
import com.inayatechlab.filemanagerpro.util.FileCat
import com.inayatechlab.filemanagerpro.util.OpenUtils
import com.inayatechlab.filemanagerpro.util.StorageUtils
import com.inayatechlab.filemanagerpro.util.TextFiles
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Public sections of the Library tab, addressable from other screens. */
enum class LibrarySection { FAVORITES, RECENT, DOWNLOADS, TRASH }

/**
 * Quick access (core, beta scope): Favorites, Recent files, Downloads and
 * the Trash (recycle bin) in one place. Covers the master list's Quick
 * Access / Recent Files / Favorite Files & Folders / Downloads shortcut /
 * Trash rows.
 */
class LibraryFragment : Fragment() {

    companion object {
        fun newInstance() = LibraryFragment()
    }

    private enum class Section(@Suppress("unused") val chipId: Int, val emptyRes: Int) {
        FAVORITES(R.id.chipFavorites, R.string.lib_empty_favorites),
        RECENT(R.id.chipRecents, R.string.lib_empty_recent),
        DOWNLOADS(R.id.chipDownloads, R.string.lib_empty_downloads),
        TRASH(R.id.chipTrash, R.string.trash_empty)
    }

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!
    private val scope get() = viewLifecycleOwner.lifecycleScope

    private var current = Section.FAVORITES
    private var fileAdapter: FileAdapter? = null
    private var trashAdapter: TrashAdapter? = null
    private val loading = AtomicBoolean(false)
    private var pendingJump: LibrarySection? = null

    private val storeDir: File get() = LibraryStore.storeDir(requireContext().filesDir)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        fileAdapter = FileAdapter(isGrid = false, selectable = false).apply {
            onItemClick = { openEntry(it) }
            onItemMenu = { showItemActions(it) }
        }
        trashAdapter = TrashAdapter().apply {
            onItemClick = { showTrashItemActions(it) }
        }
        binding.recycler.adapter = fileAdapter

        binding.chipFavorites.setOnClickListener { select(Section.FAVORITES) }
        binding.chipRecents.setOnClickListener { select(Section.RECENT) }
        binding.chipDownloads.setOnClickListener { select(Section.DOWNLOADS) }
        binding.chipTrash.setOnClickListener { select(Section.TRASH) }
        binding.btnEmptyTrash.setOnClickListener { confirmEmptyTrash() }
        refreshChipState()
        pendingJump?.let { jumpTo(it) }
        pendingJump = null
    }

    /** Opens a specific section on request from another screen (home shortcuts). */
    fun jumpTo(section: LibrarySection) {
        if (_binding == null) {
            pendingJump = section
            return
        }
        when (section) {
            LibrarySection.FAVORITES -> select(Section.FAVORITES)
            LibrarySection.RECENT -> select(Section.RECENT)
            LibrarySection.DOWNLOADS -> select(Section.DOWNLOADS)
            LibrarySection.TRASH -> select(Section.TRASH)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isHidden) becomeVisible()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) becomeVisible()
    }

    fun becomeVisible() {
        if (_binding == null) return
        (activity as? MainActivity)?.let {
            it.setAppTitle(getString(R.string.nav_library))
            it.clearMenu()
            it.showCrumbBarVisible(false)
            it.setUpButton(false) {}
        }
        load()
    }

    private fun select(section: Section) {
        if (current == section) return
        current = section
        refreshChipState()
        load()
    }

    private fun refreshChipState() {
        val b = _binding ?: return
        b.chipFavorites.isChecked = current == Section.FAVORITES
        b.chipRecents.isChecked = current == Section.RECENT
        b.chipDownloads.isChecked = current == Section.DOWNLOADS
        b.chipTrash.isChecked = current == Section.TRASH
        b.btnEmptyTrash.isVisible = current == Section.TRASH
    }

    private fun load() {
        val b = _binding ?: return
        val ctx = requireContext()
        if (!StorageUtils.isStoragePermitted(ctx)) {
            b.tvEmpty.isVisible = true
            b.tvEmpty.text = getString(R.string.storage_permission_needed)
            b.tvEmpty.setOnClickListener { StorageUtils.requestStorageAccess(ctx) }
            b.progress.isVisible = false
            return
        }
        b.tvEmpty.setOnClickListener(null)
        if (loading.getAndSet(true)) return
        b.progress.isVisible = true

        scope.launch {
            try {
                val section = current
                val ctx = requireContext()
                val dir = storeDir
                val rows = withContext(Dispatchers.IO) { rowsFor(section, ctx, dir) }
                val bb = _binding
                if (bb == null) return@launch
                bb.progress.isVisible = false
                when (section) {
                    Section.TRASH -> {
                        bb.recycler.adapter = trashAdapter
                        trashAdapter?.items = (rows as List<TrashStore.Item>).toMutableList()
                    }
                    else -> {
                        bb.recycler.adapter = fileAdapter
                        fileAdapter?.entries = (rows as List<FileEntry>).toMutableList()
                    }
                }
                bb.tvEmpty.isVisible = rows.isEmpty()
                bb.tvEmpty.text = getString(section.emptyRes)
            } finally {
                loading.set(false)
            }
        }
    }

    /** Builds the row list for a section (IO thread). */
    @Suppress("UNCHECKED_CAST")
    private suspend fun rowsFor(section: Section, ctx: android.content.Context, dir: File): List<Any> {
        val out = when (section) {
            Section.FAVORITES -> {
                val rows = mutableListOf<FileEntry>()
                for (item in LibraryStore.favorites(dir)) {
                    val f = File(item.path)
                    val ok = if (item.isDir) f.isDirectory else f.isFile
                    if (ok) rows.add(LibraryStore.toFileEntry(item))
                }
                rows
            }
            Section.RECENT -> {
                val rows = mutableListOf<FileEntry>()
                for (item in LibraryStore.recents(dir)) {
                    val f = File(item.path)
                    if (f.isFile) rows.add(LibraryStore.toFileEntry(item))
                }
                rows
            }
            Section.DOWNLOADS -> {
                val rows = mutableListOf<FileEntry>()
                val folders = mutableListOf<File>()
                folders.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
                StorageUtils.roots().forEach { root ->
                    val candidate = File(root.file, "Download")
                    if (candidate != folders.first() && candidate.isDirectory) folders.add(candidate)
                }
                val seen = HashSet<String>()
                folders.forEach { folder ->
                    val list = folder.listFiles() ?: return@forEach
                    list.filter { it.isFile }
                        .sortedByDescending { it.lastModified() }
                        .take(60)
                        .forEach { f ->
                            if (seen.add(f.path)) {
                                rows.add(FileEntry(f.name, f.path, false, f.length(), f.lastModified()))
                            }
                        }
                }
                rows.sortWith(FileOpsComparator.comparator(1, false))
                rows
            }
            Section.TRASH -> TrashOps.items(ctx)
        }
        return out as List<Any>
    }

    // ---------------------------------------------------------------- opening

    private fun openEntry(entry: FileEntry) {
        if (entry.isDir) {
            (activity as? MainActivity)?.openFolderInBrowse(entry.path)
            return
        }
        openFile(entry)
    }

    private fun openFile(entry: FileEntry) {
        val ctx = requireContext()
        LibraryStore.addRecent(storeDir, entry)
        if (FileCat.of(entry) == FileCat.IMAGE) {
            val images = (fileAdapter?.entries ?: emptyList())
                .filter { FileCat.of(it) == FileCat.IMAGE }
                .map { it.path }
            val index = images.indexOf(entry.path).coerceAtLeast(0)
            startActivity(
                Intent(ctx, PreviewActivity::class.java)
                    .putStringArrayListExtra(PreviewActivity.EXTRA_PATHS, ArrayList(images))
                    .putExtra(PreviewActivity.EXTRA_INDEX, index)
            )
        } else if (TextFiles.isTextFile(entry.name)) {
            TextActivity.start(ctx, entry.file)
        } else if (!OpenUtils.openExternal(ctx, entry.file)) {
            snack(getString(R.string.no_app_found))
        }
    }

    // ---------------------------------------------------------------- file actions

    private fun showItemActions(entry: FileEntry) {
        val ctx = requireContext()
        val actions = mutableListOf<String>()
        when (current) {
            Section.FAVORITES -> {
                actions += getString(R.string.lib_remove_from_favorites)
            }
            Section.RECENT -> {
                actions += getString(R.string.lib_remove_from_recents)
                actions += getString(R.string.action_add_favorite)
            }
            Section.DOWNLOADS -> {
                actions += getString(R.string.action_add_favorite)
            }
            Section.TRASH -> Unit
        }
        actions += getString(R.string.action_share)
        actions += getString(R.string.action_properties)
        if (!entry.isDir) actions += getString(R.string.action_open_with)

        MaterialAlertDialogBuilder(ctx)
            .setTitle(entry.name)
            .setItems(actions.toTypedArray()) { _, which ->
                when {
                    which == 0 && current == Section.FAVORITES ->
                        LibraryStore.removeFavorite(storeDir, entry.path).also { reloadAfterRemove() }
                    which == 0 && current == Section.RECENT ->
                        LibraryStore.removeRecent(storeDir, entry.path).also { reloadAfterRemove() }
                    (which == 0 && current == Section.DOWNLOADS) ||
                        (which == 1 && current == Section.RECENT) -> {
                        LibraryStore.addFavorite(storeDir, entry)
                        snack(getString(R.string.lib_added_favorite_fmt, entry.name))
                    }
                    else -> {
                        val relWhich = when (current) {
                            Section.FAVORITES -> which - 1
                            Section.RECENT -> which - 2
                            Section.DOWNLOADS -> which - 1
                            Section.TRASH -> which - 1
                        }
                        when (relWhich) {
                            0 -> if (!OpenUtils.share(ctx, listOf(entry))) snack(getString(R.string.no_app_found))
                            1 -> Dialogs.properties(ctx, entry, scope)
                            2 -> if (!OpenUtils.openWith(ctx, entry.file)) snack(getString(R.string.no_app_found))
                        }
                    }
                }
            }
            .show()
    }

    // ---------------------------------------------------------------- trash actions

    private fun showTrashItemActions(item: TrashStore.Item) {
        val ctx = requireContext()
        MaterialAlertDialogBuilder(ctx)
            .setTitle(item.name)
            .setItems(
                arrayOf(getString(R.string.trash_restore), getString(R.string.trash_purge))
            ) { _, which ->
                when (which) {
                    0 -> confirmRestore(item)
                    1 -> confirmPurge(item)
                }
            }
            .show()
    }

    private fun confirmRestore(item: TrashStore.Item) {
        scope.launch {
            val ok = TrashOps.restore(requireContext(), item)
            if (ok) {
                snack(getString(R.string.trash_restored_fmt, item.name))
                load()
            } else {
                snack(getString(R.string.trash_restore_failed_fmt, item.name))
            }
        }
    }

    private fun confirmPurge(item: TrashStore.Item) {
        val ctx = requireContext()
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.trash_purge_confirm_title)
            .setMessage(getString(R.string.trash_purge_confirm_message, item.name))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.trash_purge) { _, _ ->
                scope.launch {
                    val ok = TrashOps.purge(ctx, item)
                    if (ok) {
                        snack(getString(R.string.trash_purged_fmt, item.name))
                        load()
                    } else {
                        snack(getString(R.string.trash_purge_failed_fmt, item.name))
                    }
                }
            }
            .show()
    }

    private fun confirmEmptyTrash() {
        val ctx = requireContext()
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.trash_empty_confirm_title)
            .setMessage(R.string.trash_empty_confirm_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.trash_empty_btn) { _, _ ->
                scope.launch {
                    val failed = TrashOps.empty(ctx)
                    if (failed == 0) snack(getString(R.string.trash_empty_done))
                    else snack(getString(R.string.trash_empty_failed_fmt, failed))
                    load()
                }
            }
            .show()
    }

    private fun reloadAfterRemove() {
        load()
        snack(getString(R.string.lib_removed))
    }

    private fun snack(text: String) {
        view?.let { Snackbar.make(it, text, Snackbar.LENGTH_LONG).show() }
    }
}
