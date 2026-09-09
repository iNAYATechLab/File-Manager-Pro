package com.inayatechlab.filemanagerpro.vault

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.inayatechlab.filemanagerpro.MainActivity
import com.inayatechlab.filemanagerpro.R
import com.inayatechlab.filemanagerpro.databinding.FragmentVaultBinding
import com.inayatechlab.filemanagerpro.preview.PreviewActivity
import com.inayatechlab.filemanagerpro.util.Dialogs
import com.inayatechlab.filemanagerpro.textviewer.TextActivity
import com.inayatechlab.filemanagerpro.util.FormatUtils
import com.inayatechlab.filemanagerpro.util.TextFiles
import com.inayatechlab.filemanagerpro.util.OpenUtils
import com.inayatechlab.filemanagerpro.util.StorageUtils
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Vault tab: lists the device's .fmpvault containers, unlocks one (password
 * or biometric), and browses its plaintext index. Entries can be opened
 * (decrypted to a private cache), restored, or deleted; the whole vault can
 * be locked or destroyed from the toolbar.
 */
class VaultFragment : Fragment() {

    companion object {
        fun newInstance() = VaultFragment()
    }

    private var _binding: FragmentVaultBinding? = null
    private val binding get() = _binding!!
    private val scope get() = viewLifecycleOwner.lifecycleScope

    private val overviewAdapter = VaultOverviewAdapter(emptyList(), ::openVaultDir)
    private val entriesAdapter = VaultEntriesAdapter(::openEntry, ::showEntryMenu)

    /** The vault folder currently opened (content view) — null on overview. */
    private var openedDir: File? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentVaultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.fabCreate.setOnClickListener { showCreateDialog() }
    }

    override fun onResume() {
        super.onResume()
        if (!isHidden) becomeVisible()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) becomeVisible()
    }

    override fun onStop() {
        // The app left the foreground — wipe the unlocked session from memory.
        if (VaultSessionManager.isUnlocked) {
            VaultSessionManager.close()
            openedDir = null
            clearOpenCache()
        }
        super.onStop()
    }

    override fun onDestroyView() {
        clearOpenCache()
        _binding = null
        super.onDestroyView()
    }

    /** Back key: content view locks and returns to the overview; overview exits the tab. */
    fun handleBack(): Boolean {
        if (openedDir != null) {
            lockVault()
            return true
        }
        return false
    }

    fun becomeVisible() {
        if (_binding == null) return
        val act = activity as? MainActivity ?: return
        val session = VaultSessionManager.session
        if (openedDir != null && session != null && session.dir == openedDir && session.dir.isDirectory) {
            renderContent(session)
        } else {
            if (openedDir != null) VaultSessionManager.close()
            openedDir = null
            renderOverview(act)
        }
    }

    // ---------------------------------------------------------------- rendering

    private fun syncToolbar() {
        val act = activity as? MainActivity ?: return
        act.installVaultMenu(::onMenuItem)
        val content = openedDir != null
        act.setAppTitle(
            if (content) getString(R.string.nav_vault) + " — " + vaultName()
            else getString(R.string.nav_vault)
        )
        act.showCrumbBarVisible(false)
        act.setUpButton(false) {}
        val menu = act.currentMenu()
        menu.findItem(R.id.action_vault_create)?.isVisible = !content
        menu.findItem(R.id.action_vault_lock)?.isVisible = content
        menu.findItem(R.id.action_vault_restore_all)?.isVisible =
            content && (VaultSessionManager.session?.entriesSnapshot?.isNotEmpty() == true)
        menu.findItem(R.id.action_vault_bio)?.let {
            it.isVisible = content
            val hasBio = VaultSessionManager.session?.hasBiometric == true
            it.title = getString(if (hasBio) R.string.vlt_bio_disable else R.string.vlt_bio_enable)
            if (hasBio) it.icon = null
        }
        menu.findItem(R.id.action_vault_delete)?.isVisible = content
    }

    private fun vaultName(): String =
        VaultSessionManager.session?.name ?: VaultFormat.displayName(openedDir ?: File(""))

    private fun renderOverview(act: MainActivity) {
        binding.fabCreate.isVisible = true
        binding.recycler.adapter = overviewAdapter
        binding.tvEmptyEntry.isVisible = false
        val ctx = requireContext()
        if (!StorageUtils.isStoragePermitted(ctx)) {
            binding.tvEmpty.isVisible = true
            binding.tvHint.isVisible = false
            binding.tvEmpty.text = getString(R.string.storage_permission_needed)
            binding.tvEmpty.setOnClickListener { StorageUtils.requestStorageAccess(ctx) }
            return
        }
        binding.tvEmpty.setOnClickListener(null)
        binding.tvEmpty.isVisible = false
        binding.tvHint.isVisible = false
        syncToolbar()
        loadOverviewRows()
    }

    private fun renderContent(session: VaultEngine.Session) {
        binding.fabCreate.isVisible = false
        binding.recycler.adapter = entriesAdapter
        binding.tvEmpty.isVisible = false
        binding.tvHint.isVisible = false
        binding.tvEmptyEntry.isVisible = session.entriesSnapshot.isEmpty()
        syncToolbar()
        entriesAdapter.submit(session.entriesSnapshot)
    }

    private fun loadOverviewRows() {
        val progress = Dialogs.showProgress(requireContext(), getString(R.string.vlt_loading))
        binding.progress.isVisible = true
        scope.launch {
            try {
                val ctx = requireContext()
                val roots = withContext(Dispatchers.IO) {
                    StorageUtils.roots().map { it.file }
                }
                val rows = withContext(Dispatchers.IO) {
                    VaultEngine.discoverVaults(roots).map { dir ->
                        val conf = VaultEngine.metaOf(dir)
                        val size = VaultEngine.vaultSize(dir)
                        val bioMark = if (conf?.bioB64 != null) " • " + ctx.getString(R.string.vlt_bio_marker) else ""
                        VaultRow(
                            dir,
                            VaultFormat.displayName(dir),
                            ctx.getString(
                                R.string.vlt_overview_sub_fmt,
                                FormatUtils.formatSize(size),
                                FormatUtils.formatDate(conf?.createdMs ?: dir.lastModified())
                            ) + bioMark
                        )
                    }
                }
                if (_binding == null) return@launch
                binding.progress.isVisible = false
                overviewAdapter.submit(rows)
                binding.tvEmpty.isVisible = rows.isEmpty()
                binding.tvHint.isVisible = rows.isEmpty()
            } finally {
                progress.dismiss()
                binding.progress.isVisible = false
            }
        }
    }

    // ---------------------------------------------------------------- actions

    private fun openVaultDir(dir: File) {
        val act = activity as? AppCompatActivity ?: return
        openedDir = dir
        VaultUnlock.prompt(act, scope, dir,
            onUnlocked = { session ->
                openedDir = session.dir
                renderContent(session)
            },
            onDismissed = {
                if (VaultSessionManager.session == null) {
                    openedDir = null
                    if (_binding != null) {
                        binding.fabCreate.isVisible = true
                        binding.tvEmpty.isVisible = false
                        binding.tvHint.isVisible = false
                        (activity as? MainActivity)?.let { renderOverview(it) }
                    }
                }
            }
        )
    }

    private fun showCreateDialog() {
        val ctx = requireContext()
        val nameInput = com.google.android.material.textfield.TextInputEditText(ctx).apply {
            hint = getString(R.string.vlt_hint_name)
        }
        val pwInput = com.google.android.material.textfield.TextInputEditText(ctx).apply {
            hint = getString(R.string.vlt_hint_password)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val confirmInput = com.google.android.material.textfield.TextInputEditText(ctx).apply {
            hint = getString(R.string.vlt_hint_confirm)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val wrap = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(64, 8, 64, 0)
            addView(nameInput)
            addView(pwInput)
            addView(confirmInput)
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.vlt_action_create)
            .setView(wrap)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_ok, null)
            .show()
            .let { dialog ->
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val name = nameInput.text?.toString()?.trim().orEmpty()
                    val pw = pwInput.text?.toString().orEmpty()
                    val confirm = confirmInput.text?.toString().orEmpty()
                    when {
                        name.isEmpty() || !isValidVaultName(name) ->
                            snack(getString(R.string.vlt_error_name))
                        pw.length < 4 -> snack(getString(R.string.vlt_error_short_pw))
                        pw != confirm -> snack(getString(R.string.vlt_error_mismatch))
                        else -> {
                            dialog.dismiss()
                            createVault(name, pw)
                        }
                    }
                }
            }
    }

    private fun isValidVaultName(name: String): Boolean =
        !name.contains('/') && !name.contains('\\') && !name.contains("..") && name.isNotBlank()

    private fun createVault(name: String, password: String) {
        val root = StorageUtils.primaryRoot()
        val dir = File(VaultEngine.defaultParent(root), "$name${VaultFormat.EXTENSION}")
        val progress = Dialogs.showProgress(requireContext(), getString(R.string.vlt_creating))
        scope.launch {
            try {
                val session = withContext(Dispatchers.IO) { VaultEngine.create(dir, password) }
                VaultSessionManager.open(session)
                openedDir = session.dir
                renderContent(session)
                snack(getString(R.string.vlt_created_fmt, session.name))
            } catch (e: VaultCryptoException) {
                snack(localizeError(e))
            } catch (e: Exception) {
                snack(getString(R.string.error))
            } finally {
                progress.dismiss()
            }
        }
    }

    private fun lockVault() {
        VaultSessionManager.close()
        openedDir = null
        clearOpenCache()
        (activity as? MainActivity)?.let { renderOverview(it) }
    }

    private fun deleteVault() {
        val session = VaultSessionManager.session ?: return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.vlt_delete_vault_title)
            .setMessage(getString(R.string.vlt_delete_vault_message, session.name))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete_confirm) { _, _ ->
                val dir = session.dir
                val progress = Dialogs.showProgress(requireContext(), getString(R.string.vlt_deleting))
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { VaultEngine.deleteVault(dir) }
                        VaultSessionManager.close()
                        openedDir = null
                        clearOpenCache()
                        renderOverview(activity as MainActivity)
                    } finally {
                        progress.dismiss()
                    }
                }
            }
            .show()
    }

    private fun toggleBiometric() {
        val session = VaultSessionManager.session ?: return
        val act = activity as? AppCompatActivity ?: return
        if (session.hasBiometric) {
            // Disable: drop the wrapped password blob + keystore key.
            val alias = VaultBiometric.aliasFor(session.name)
            scope.launch {
                withContext(Dispatchers.IO) {
                    VaultEngine.setBiometricBlob(session.dir, null)
                }
                VaultBiometric.deleteKey(alias)
                session.refreshMeta()
                syncToolbar()
                snack(getString(R.string.vlt_bio_disabled))
            }
        } else {
            if (!VaultBiometric.canAuthenticate(act)) {
                snack(getString(R.string.vlt_bio_not_supported))
                return
            }
            // Enable: ask the password once, wrap it with the gated key.
            VaultUnlock.promptForPassword(
                act,
                title = getString(R.string.vlt_bio_enable),
                message = getString(R.string.vlt_bio_setup_message),
                onSubmit = { password ->
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            runCatching {
                                val alias = VaultBiometric.aliasFor(session.name)
                                VaultBiometric.createKey(alias)
                                val blob = VaultBiometric.wrapPassword(alias, password)
                                VaultEngine.setBiometricBlob(session.dir, blob)
                            }.isSuccess
                        }
                        session.refreshMeta()
                        syncToolbar()
                        if (ok) snack(getString(R.string.vlt_bio_enabled))
                        else snack(getString(R.string.vlt_bio_enable_failed))
                    }
                }
            )
        }
    }

    private fun restoreAll() {
        val session = VaultSessionManager.session ?: return
        val fallback = File(File(StorageUtils.primaryRoot(), "Restored"), session.name)
        val progress = Dialogs.showProgress(requireContext(), getString(R.string.vlt_restoring))
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                VaultEngine.restoreFiles(session, null, fallback)
            }
            progress.dismiss()
            val msg = if (outcome.errors.isEmpty()) {
                getString(R.string.vlt_restored_fmt, outcome.restored.size)
            } else {
                getString(R.string.vlt_restore_partial_fmt, outcome.restored.size, outcome.errors.size)
            }
            snack(msg)
            renderContent(session)
        }
    }

    private fun openEntry(entry: VaultFormat.VaultEntry) {
        val session = VaultSessionManager.session ?: return
        val ctx = requireContext()
        val cache = File(ctx.cacheDir, "vault_open")
        val progress = Dialogs.showProgress(ctx, getString(R.string.vlt_opening))
        scope.launch {
            val file = try {
                withContext(Dispatchers.IO) { VaultEngine.decryptTo(session, entry.relPath, cache) }
            } catch (e: Exception) {
                null
            }
            progress.dismiss()
            val f = file
            if (f == null || !f.isFile) {
                snack(getString(R.string.error))
                return@launch
            }
            val ext = entry.name.substringAfterLast('.', "").lowercase()
            if (ext == "jpg" || ext == "jpeg" || ext == "png" || ext == "gif" || ext == "webp") {
                startActivity(
                    Intent(ctx, PreviewActivity::class.java)
                        .putStringArrayListExtra(PreviewActivity.EXTRA_PATHS, arrayListOf(f.path))
                        .putExtra(PreviewActivity.EXTRA_INDEX, 0)
                )
            } else if (TextFiles.isTextFile(f.name)) {
                TextActivity.start(ctx, f)
            } else if (!OpenUtils.openExternal(ctx, f)) {
                snack(getString(R.string.no_app_found))
            }
        }
    }

    private fun showEntryMenu(entry: VaultFormat.VaultEntry) {
        val session = VaultSessionManager.session ?: return
        val options = arrayOf(
            getString(R.string.vlt_action_restore),
            getString(R.string.action_delete)
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(entry.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> restoreEntry(entry, session)
                    1 -> confirmRemoveEntry(entry, session)
                }
            }
            .show()
    }

    private fun restoreEntry(entry: VaultFormat.VaultEntry, session: VaultEngine.Session) {
        val fallback = File(File(StorageUtils.primaryRoot(), "Restored"), session.name)
        val progress = Dialogs.showProgress(requireContext(), getString(R.string.vlt_restoring))
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                VaultEngine.restoreFiles(session, listOf(entry.relPath), fallback)
            }
            progress.dismiss()
            if (outcome.errors.isEmpty()) {
                snack(getString(R.string.vlt_restored_fmt, outcome.restored.size))
            } else {
                snack(getString(R.string.error))
            }
        }
    }

    private fun confirmRemoveEntry(entry: VaultFormat.VaultEntry, session: VaultEngine.Session) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.vlt_remove_entry_title)
            .setMessage(entry.name)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete_confirm) { _, _ ->
                scope.launch {
                    withContext(Dispatchers.IO) { VaultEngine.removeFiles(session, listOf(entry.relPath)) }
                    renderContent(session)
                    snack(getString(R.string.vlt_removed_fmt, entry.name))
                }
            }
            .show()
    }

    private fun onMenuItem(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_vault_create -> {
                showCreateDialog()
                true
            }
            R.id.action_vault_lock -> {
                lockVault()
                true
            }
            R.id.action_vault_restore_all -> {
                restoreAll()
                true
            }
            R.id.action_vault_bio -> {
                toggleBiometric()
                true
            }
            R.id.action_vault_delete -> {
                deleteVault()
                true
            }
            else -> false
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun localizeError(e: VaultCryptoException): String = when (e.reason) {
        VaultCryptoException.Reason.WRONG_PASSWORD -> getString(R.string.vlt_wrong_password)
        VaultCryptoException.Reason.CORRUPT -> getString(R.string.vlt_error_corrupt)
        VaultCryptoException.Reason.TOO_LARGE -> getString(R.string.vlt_error_too_large)
        VaultCryptoException.Reason.IO -> getString(R.string.vlt_error_io)
    }

    private fun clearOpenCache() {
        runCatching {
            File(requireContext().cacheDir, "vault_open").deleteRecursively()
        }
    }

    private fun snack(text: String) {
        view?.let { Snackbar.make(it, text, Snackbar.LENGTH_LONG).show() }
    }
}
