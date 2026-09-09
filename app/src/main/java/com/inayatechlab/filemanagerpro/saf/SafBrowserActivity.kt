package com.inayatechlab.filemanagerpro.saf

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.addCallback
import android.provider.DocumentsContract
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.view.ActionMode
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.inayatechlab.filemanagerpro.R
import com.inayatechlab.filemanagerpro.databinding.ActivitySafBrowserBinding
import com.inayatechlab.filemanagerpro.model.FileEntry
import com.inayatechlab.filemanagerpro.preview.PreviewActivity
import com.inayatechlab.filemanagerpro.util.Dialogs
import com.inayatechlab.filemanagerpro.textviewer.TextActivity
import com.inayatechlab.filemanagerpro.util.FormatUtils
import com.inayatechlab.filemanagerpro.util.TextFiles
import com.inayatechlab.filemanagerpro.util.OpenUtils
import java.io.File
import java.util.ArrayDeque
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Storage Access Framework browser (#21).
 *
 * The Storage home card opens this screen: grant access to protected folders
 * (Android/data, Android/obb or any system-picker folder) and browse them
 * document by document. Originals are never modified implicitly: files can be
 * opened, shared, copied out to another granted/picked location, renamed or
 * deleted — always after an explicit user action.
 */
class SafBrowserActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySafBrowserBinding
    private val scope get() = lifecycleScope

    /** One directory level inside a granted tree. */
    private data class Level(val treeUri: Uri, val dirDocId: String, val label: String)

    private val stack = ArrayDeque<Level>()
    private var actionMode: ActionMode? = null

    private val dirAdapter = SafDirAdapter(mutableListOf(), ::onEntryOpen, ::onEntryLong)
    private var homeAdapter: SafHomeAdapter? = null

    /** Entries waiting for a destination picker (copy-to flow). */
    private var pendingCopy: List<SafEntry>? = null

    // Android/data & obb open at the provider's well-known folder.
    private fun initialTreeUri(relPath: String): Uri? {
        val authority = SafOps.AUTHORITY_EXT_STORAGE
        val docId = "primary:$relPath"
        return Uri.parse("content://$authority/document/${Uri.encode(docId)}")
    }

    private val grantLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> onGrantResult(uri) }

    private val destLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> onDestResult(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySafBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { goBack() }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        onBackPressedDispatcher.addCallback(this) { goBack() }

        renderHome()
    }

    private fun goBack() {
        if (stack.isEmpty()) {
            finish()
        } else {
            stack.removeLast()
            if (stack.isEmpty()) renderHome() else openCurrentLevel()
        }
    }

    // ---------------------------------------------------------------- rendering

    private fun renderHome() {
        exitSelectionMode()
        stack.clear()
        val grants = SafGrants.list(this)
        val persisted = persistedUris()

        val rows = mutableListOf<SafHomeRow>()
        rows += SafHomeRow.AddData(getString(R.string.saf_hint_protected))
        rows += SafHomeRow.AddObb(getString(R.string.saf_hint_protected))
        rows += SafHomeRow.AddCustom(getString(R.string.saf_hint_custom))
        grants.forEach { uri ->
            val docId = treeDocId(uri)
            val title = SafGrants.labelFor(this, uri)
                ?: docId?.let { SafText.folderName(it) ?: SafText.volumeLabel(SafText.splitDocId(it).first) }
                ?: getString(R.string.saf_unknown_location)
            val sub = docId?.let { SafText.subtitleOf(it) } ?: uri.toString()
            rows += SafHomeRow.Location(uri, title, sub, uri in persisted)
        }

        binding.tvEmpty.isVisible = grants.isEmpty()
        binding.tvEmpty.text = getString(R.string.saf_empty)
        homeAdapter = SafHomeAdapter(
            rows,
            onAddData = { promptGrantTree("Android/data") },
            onAddObb = { promptGrantTree("Android/obb") },
            onAddCustom = { grantLauncher.launch(null) },
            onLocationClick = { loc ->
                if (loc.granted) openTree(loc.uri)
                else {
                    snack(getString(R.string.saf_denied))
                    confirmRemove(loc.uri)
                }
            },
            onLocationLong = { loc -> locationMenu(loc) }
        )
        binding.recycler.adapter = homeAdapter
        binding.toolbar.title = getString(R.string.saf_title)
        binding.toolbar.menu.findItem(R.id.action_new_folder)?.isVisible = false
    }

    /** Opens the root of a granted tree. */
    private fun openTree(uri: Uri) {
        val docId = treeDocId(uri)
        if (docId == null) {
            snack(getString(R.string.saf_denied))
            return
        }
        stack.clear()
        stack.add(
            Level(
                uri, docId,
                SafGrants.labelFor(this, uri)
                    ?: SafText.folderName(docId) ?: uri.toString()
            )
        )
        openCurrentLevel()
    }

    private fun openCurrentLevel() {
        val level = stack.lastOrNull() ?: return renderHome()
        exitSelectionMode()
        binding.toolbar.title = level.label
        binding.toolbar.menu.findItem(R.id.action_new_folder)?.isVisible = true
        binding.tvEmpty.isVisible = false
        val progress = Dialogs.showProgress(this, getString(R.string.saf_loading))
        scope.launch {
            val entries = try {
                withContext(Dispatchers.IO) {
                    SafOps.listChildren(this@SafBrowserActivity, level.treeUri, level.dirDocId)
                }
            } catch (e: Exception) {
                null
            }
            progress.dismiss()
            if (entries == null) {
                snack(getString(R.string.saf_list_failed))
                return@launch
            }
            dirAdapter.entries.clear()
            dirAdapter.entries.addAll(entries)
            dirAdapter.selectionMode = false
            dirAdapter.notifyDataSetChanged()
            binding.recycler.adapter = dirAdapter
            binding.tvEmpty.isVisible = entries.isEmpty()
        }
    }

    private fun reloadCurrent() {
        if (stack.isEmpty()) renderHome() else openCurrentLevel()
    }

    private fun persistedUris(): Set<Uri> {
        val set = linkedSetOf<Uri>()
        for (p in contentResolver.persistedUriPermissions) {
            if (p.isReadPermission || p.isWritePermission) {
                set.add(p.uri)
            }
        }
        return set
    }

    // ---------------------------------------------------------------- entries

    private fun onEntryOpen(entry: SafEntry) {
        if (entry.isDir) {
            stack.add(Level(entry.treeUri, entry.docId, entry.name))
            openCurrentLevel()
            return
        }
        openFile(entry)
    }

    private fun onEntryLong(entry: SafEntry) {
        actionMode = startSupportActionMode(actionModeCallback)
        actionMode?.invalidate()
    }

    private fun openFile(entry: SafEntry) {
        val progress = Dialogs.showProgress(this, getString(R.string.saf_preparing))
        scope.launch {
            val file = withContext(Dispatchers.IO) { SafOps.materialize(this@SafBrowserActivity, entry) }
            progress.dismiss()
            if (file == null) {
                snack(getString(R.string.error))
                return@launch
            }
            val ext = entry.name.substringAfterLast('.', "").lowercase()
            if (entry.mime.startsWith("image/") ||
                ext in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
            ) {
                startActivity(
                    Intent(this@SafBrowserActivity, PreviewActivity::class.java)
                        .putStringArrayListExtra(
                            PreviewActivity.EXTRA_PATHS, arrayListOf(file.path)
                        )
                        .putExtra(PreviewActivity.EXTRA_INDEX, 0)
                )
            } else if (TextFiles.isTextFile(file.name)) {
                TextActivity.start(this@SafBrowserActivity, file)
            } else if (!OpenUtils.openExternal(this@SafBrowserActivity, file)) {
                snack(getString(R.string.no_app_found))
            }
        }
    }

    private fun shareSelected(selected: List<SafEntry>) {
        val progress = Dialogs.showProgress(this, getString(R.string.saf_preparing))
        scope.launch {
            val files = mutableListOf<FileEntry>()
            val failed = mutableListOf<String>()
            for (entry in selected) {
                val f = withContext(Dispatchers.IO) {
                    SafOps.materialize(this@SafBrowserActivity, entry)
                }
                if (f != null) {
                    files += FileEntry(f.name, f.path, false, f.length(), f.lastModified())
                } else {
                    failed += entry.name
                }
            }
            progress.dismiss()
            if (files.isEmpty()) {
                snack(getString(R.string.error))
            } else {
                if (!OpenUtils.share(this@SafBrowserActivity, files)) {
                    snack(getString(R.string.no_app_found))
                } else if (failed.isNotEmpty()) {
                    snack(getString(R.string.saf_share_partial, failed.size))
                }
            }
        }
    }

    // ---------------------------------------------------------------- grants

    /** Starts the system tree picker for one of the protected folders. */
    private fun promptGrantTree(relPath: String) {
        grantLauncher.launch(initialTreeUri(relPath))
    }

    private fun onGrantResult(uri: Uri?) {
        if (uri == null) return
        val docId = treeDocId(uri)
        if (docId == null) {
            snack(getString(R.string.saf_grant_failed))
            return
        }
        // Keep the grant across restarts; some providers reject this, in which
        // case the location still works while the app process is alive.
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        val (volume, path) = SafText.splitDocId(docId)
        val label = when (path?.lowercase()) {
            "android/data" -> getString(R.string.saf_add_data)
            "android/obb" -> getString(R.string.saf_add_obb)
            else -> SafText.sanitizeLabel(
                SafText.folderName(docId) ?: SafText.volumeLabel(volume)
            )
        }
        SafGrants.remember(this, uri, label)
        snack(getString(R.string.saf_granted_fmt, label))
        renderHome()
    }

    private fun locationMenu(loc: SafHomeRow.Location) {
        val options = arrayOf(
            getString(R.string.saf_open),
            getString(R.string.saf_remove)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(loc.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> if (loc.uri in persistedUris()) openTree(loc.uri)
                    else snack(getString(R.string.saf_denied))
                    1 -> confirmRemove(loc.uri)
                }
            }
            .show()
    }

    private fun confirmRemove(uri: Uri) {
        val label = SafGrants.labelFor(this, uri) ?: getString(R.string.saf_unknown_location)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.saf_remove_confirm_title)
            .setMessage(getString(R.string.saf_remove_confirm_msg, label))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.saf_remove) { _, _ ->
                runCatching {
                    contentResolver.releasePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                }
                SafGrants.forget(this, uri)
                renderHome()
            }
            .show()
    }

    // ---------------------------------------------------------------- copy to…

    private fun copyTo(selected: List<SafEntry>) {
        val others = SafGrants.list(this)
        val names = others.map {
            SafGrants.labelFor(this, it)
                ?: treeDocId(it)?.let { d -> SafText.subtitleOf(d) }
                ?: it.toString()
        }.toMutableList<CharSequence>()
        names += getString(R.string.saf_copy_choose)
        val options = names.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.saf_copy_title)
            .setItems(options) { _, which ->
                if (which < others.size) {
                    val dest = others[which]
                    if (dest == selected.firstOrNull()?.treeUri) {
                        snack(getString(R.string.saf_copy_same))
                        return@setItems
                    }
                    performCopy(selected, dest)
                } else {
                    pendingCopy = selected
                    destLauncher.launch(null)
                }
            }
            .show()
    }

    private fun onDestResult(uri: Uri?) {
        val selected = pendingCopy ?: return
        pendingCopy = null
        if (uri == null) return
        val destDocId = treeDocId(uri) ?: run {
            snack(getString(R.string.saf_grant_failed))
            return
        }
        if (uri == selected.firstOrNull()?.treeUri) {
            snack(getString(R.string.saf_copy_same))
            return
        }
        performCopy(selected, uri)
    }

    private fun performCopy(selected: List<SafEntry>, destTree: Uri) {
        val srcTree = selected.first().treeUri
        val progress = Dialogs.showProgress(this, getString(R.string.saf_copying))
        scope.launch {
            val errors = withContext(Dispatchers.IO) {
                SafOps.copyInto(this@SafBrowserActivity, srcTree, selected, destTree) {}
            }
            progress.dismiss()
            if (errors.isEmpty()) {
                snack(getString(R.string.saf_copy_done_fmt, selected.size))
            } else {
                snack(
                    getString(
                        R.string.saf_copy_partial_fmt,
                        (selected.size - errors.size).coerceAtLeast(0),
                        errors.size
                    )
                )
            }
        }
    }

    // ---------------------------------------------------------------- other ops

    private fun confirmDelete(selected: List<SafEntry>) {
        val sample = selected.take(3).joinToString { it.name }
        val more = if (selected.size > 3) "\n+${selected.size - 3} more" else ""
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_delete_title)
            .setMessage(getString(R.string.dialog_delete_message) + "\n\n$sample$more")
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete_confirm) { _, _ ->
                val progress = Dialogs.showProgress(this, getString(R.string.saf_deleting))
                scope.launch {
                    val errors = withContext(Dispatchers.IO) {
                        SafOps.delete(this@SafBrowserActivity, selected)
                    }
                    progress.dismiss()
                    if (errors.isNotEmpty()) {
                        snack(errors.joinToString("\n").take(240))
                    }
                    exitSelectionMode()
                    reloadCurrent()
                }
            }
            .show()
    }

    private fun renameEntry(entry: SafEntry) {
        Dialogs.promptText(
            this,
            getString(R.string.dialog_rename_title),
            entry.name,
            getString(R.string.hint_new_name),
            getString(R.string.action_ok)
        ) { newName ->
            scope.launch {
                val err = withContext(Dispatchers.IO) {
                    SafOps.rename(this@SafBrowserActivity, entry, newName)
                }
                if (err != null) snack(err) else reloadCurrent()
            }
        }
    }

    private fun properties(entry: SafEntry) {
        val level = stack.lastOrNull()
        val sb = StringBuilder()
        sb.append(getString(R.string.saf_props_name_fmt, entry.name)).append('\n')
        sb.append(
            getString(
                R.string.saf_props_type_fmt,
                if (entry.isDir) getString(R.string.saf_props_type_folder)
                else entry.mime.ifEmpty { getString(R.string.saf_props_type_file) }
            )
        ).append('\n')
        if (!entry.isDir) {
            sb.append(
                getString(R.string.saf_props_size_fmt, FormatUtils.formatSize(entry.size))
            ).append('\n')
        }
        sb.append(getString(R.string.saf_props_modified_fmt, FormatUtils.formatDate(entry.lastModified)))
        level?.let {
            sb.append('\n')
            sb.append(getString(R.string.saf_props_location_fmt, it.label))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(entry.name)
            .setMessage(sb.toString())
            .setPositiveButton(R.string.action_ok, null)
            .show()
    }

    // ---------------------------------------------------------------- selection

    private fun exitSelectionMode() {
        actionMode?.finish()
        actionMode = null
        dirAdapter.selectionMode = false
    }

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.menu_saf_selection, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            val count = dirAdapter.selected.size
            mode.title = "$count ${getString(R.string.action_selected)}"
            menu.findItem(R.id.action_rename)?.isVisible = count == 1
            menu.findItem(R.id.action_properties)?.isVisible = count == 1
            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val selected = dirAdapter.selectedEntries()
            if (selected.isEmpty()) return false
            return when (item.itemId) {
                R.id.action_clear_selection -> {
                    dirAdapter.selectionMode = false
                    mode.finish()
                    true
                }
                R.id.action_select_all -> {
                    dirAdapter.selectAll()
                    mode.invalidate()
                    true
                }
                R.id.action_copy -> {
                    copyTo(selected)
                    true
                }
                R.id.action_share -> {
                    mode.finish()
                    shareSelected(selected)
                    true
                }
                R.id.action_delete -> {
                    confirmDelete(selected)
                    true
                }
                R.id.action_rename -> {
                    mode.finish()
                    renameEntry(selected.first())
                    true
                }
                R.id.action_properties -> {
                    properties(selected.first())
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            dirAdapter.selectionMode = false
            actionMode = null
        }
    }

    // ---------------------------------------------------------------- menu

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_saf_browse, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_new_folder -> {
                newFolder()
                true
            }
            R.id.action_refresh -> {
                reloadCurrent()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun newFolder() {
        val level = stack.lastOrNull() ?: return
        Dialogs.promptText(
            this,
            getString(R.string.dialog_new_folder_title),
            "",
            getString(R.string.hint_name),
            getString(R.string.action_ok)
        ) { name ->
            scope.launch {
                val progress = Dialogs.showProgress(this@SafBrowserActivity, getString(R.string.saf_creating))
                val (docId, err) = withContext(Dispatchers.IO) {
                    SafOps.createFolder(this@SafBrowserActivity, level.treeUri, level.dirDocId, name)
                }
                progress.dismiss()
                if (err != null) {
                    snack(getString(R.string.error))
                } else {
                    snack(getString(R.string.saf_folder_created))
                    openCurrentLevel()
                }
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Tree document id of a granted tree; null when the URI is not a tree. */
    private fun treeDocId(uri: Uri): String? = try {
        DocumentsContract.getTreeDocumentId(uri)
    } catch (e: Exception) {
        null
    }

    private fun snack(text: String) {
        Snackbar.make(binding.root, text, Snackbar.LENGTH_LONG).show()
    }
}
