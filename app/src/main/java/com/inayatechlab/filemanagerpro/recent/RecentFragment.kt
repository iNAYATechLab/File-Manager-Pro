package com.inayatechlab.filemanagerpro.recent

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
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
import com.inayatechlab.filemanagerpro.databinding.FragmentRecentBinding
import com.inayatechlab.filemanagerpro.model.FileEntry
import com.inayatechlab.filemanagerpro.model.RecentEntry
import com.inayatechlab.filemanagerpro.preview.PreviewActivity
import com.inayatechlab.filemanagerpro.util.Dialogs
import com.inayatechlab.filemanagerpro.util.FileCat
import com.inayatechlab.filemanagerpro.util.OpenUtils
import com.inayatechlab.filemanagerpro.util.RecentStore
import com.inayatechlab.filemanagerpro.util.StorageUtils
import java.io.File

/** Recent tab: automatically tracked history, filterable and clearable. */
class RecentFragment : Fragment() {

    private var _binding: FragmentRecentBinding? = null
    private val binding get() = _binding!!
    private val scope get() = viewLifecycleOwner.lifecycleScope

    private var adapter: RecentRowAdapter? = null
    private var chipAdapter: FilterChipAdapter? = null
    private var filter = RecentStore.Filter.ALL

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRecentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.chipRecycler.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        val filters = RecentStore.Filter.values().toList()
        chipAdapter = FilterChipAdapter(filters, 0) { filter = it; load() }
        binding.chipRecycler.adapter = chipAdapter

        adapter = RecentRowAdapter(
            onOpen = { open(it) },
            onMenu = { showItemActions(it) }
        )
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter
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
        if (_binding == null) return // view not created yet — onResume will reload
        val act = activity as? MainActivity ?: return
        act.setAppTitle(getString(R.string.nav_recent))
        act.showCrumbBarVisible(false)
        act.setUpButton(false) {}
        installMenu(act)
        load()
    }

    private fun installMenu(act: MainActivity) {
        val menu = act.installBrowseMenu(::onMenuItem)
        menu.findItem(R.id.action_search)?.isVisible = false
        menu.findItem(R.id.action_new_file)?.isVisible = false
        menu.findItem(R.id.action_new_folder)?.isVisible = false
        menu.findItem(R.id.action_paste)?.isVisible = false
        menu.findItem(R.id.action_sort)?.isVisible = false
        menu.findItem(R.id.action_hidden)?.isVisible = false
        menu.findItem(R.id.action_select_all)?.isVisible = false
        menu.findItem(R.id.action_fav_sort)?.isVisible = false
        menu.findItem(R.id.action_fav_group)?.isVisible = false
        menu.findItem(R.id.action_clear_history)?.isVisible = true
    }

    private fun onMenuItem(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_clear_history -> {
            Dialogs.confirm(
                requireContext(),
                getString(R.string.action_clear_history),
                getString(R.string.recent_clear_message),
                getString(R.string.action_ok)
            ) {
                RecentStore.clear(requireContext())
                load()
                snack(getString(R.string.recent_cleared))
            }
            true
        }
        else -> false
    }

    private fun load() {
        val ctx = requireContext()
        if (!StorageUtils.isStoragePermitted(ctx)) {
            binding.tvEmpty.isVisible = true
            binding.tvEmpty.text = getString(R.string.storage_permission_needed)
            return
        }
        binding.tvEmpty.setOnClickListener(null)
        val all = RecentStore.all(ctx)
        val rows = filtered(all)
        binding.tvEmpty.isVisible = rows.isEmpty()
        binding.tvEmpty.text = if (all.isEmpty()) getString(R.string.recent_empty)
        else getString(R.string.recent_empty_filter)
        adapter?.submit(rows)
        (activity as? MainActivity)?.setAppTitle(getString(R.string.nav_recent) + " (${rows.size})")
    }

    private fun open(entry: RecentEntry) {
        val file = File(entry.path)
        if (!file.exists()) {
            snack(getString(R.string.error))
            load()
            return
        }
        if (entry.isDir) {
            RecentStore.record(requireContext(), entry.path, RecentStore.Kind.OPENED)
            (activity as? MainActivity)?.openBrowsePath(entry.path)
            return
        }
        RecentStore.record(requireContext(), entry.path, RecentStore.Kind.OPENED)
        val fe = FileEntry(entry.name, entry.path, isDir = false)
        if (FileCat.of(fe) == FileCat.IMAGE) {
            val images = (adapterRows()).filter { FileCat.of(it.asFileEntry()) == FileCat.IMAGE }.map { it.path }
            val index = images.indexOf(entry.path).coerceAtLeast(0)
            startActivity(
                Intent(requireContext(), PreviewActivity::class.java)
                    .putStringArrayListExtra(PreviewActivity.EXTRA_PATHS, ArrayList(images))
                    .putExtra(PreviewActivity.EXTRA_INDEX, index)
            )
        } else if (!OpenUtils.openExternal(requireContext(), file)) {
            snack(getString(R.string.no_app_found))
        }
    }

    private fun filtered(all: List<RecentEntry>): List<RecentEntry> = when {
        filter == RecentStore.Filter.ALL -> all
        filter == RecentStore.Filter.FILES -> all.filter { !it.isDir }
        filter == RecentStore.Filter.FOLDERS -> all.filter { it.isDir }
        else -> all.filter { it.kind == filter.kind }
    }

    private fun adapterRows(): List<RecentEntry> =
        filtered(RecentStore.all(requireContext()))

    private fun showItemActions(entry: RecentEntry) {
        val options = arrayOf(
            getString(R.string.action_open),
            getString(R.string.action_properties),
            getString(R.string.action_remove_from_history)
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(entry.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> open(entry)
                    1 -> Dialogs.properties(
                        requireContext(),
                        FileEntry(entry.name, entry.path, entry.isDir),
                        scope
                    )
                    2 -> {
                        RecentStore.removePath(requireContext(), entry.path)
                        load()
                        snack(getString(R.string.recent_removed))
                    }
                }
            }
            .show()
    }

    private fun snack(text: String) {
        view?.let { Snackbar.make(it, text, Snackbar.LENGTH_LONG).show() }
    }

    companion object {
        fun newInstance() = RecentFragment()
    }
}
