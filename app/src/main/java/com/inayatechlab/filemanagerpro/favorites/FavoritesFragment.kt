package com.inayatechlab.filemanagerpro.favorites

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
import com.inayatechlab.filemanagerpro.databinding.FragmentFavoritesBinding
import com.inayatechlab.filemanagerpro.model.FileEntry
import com.inayatechlab.filemanagerpro.model.RecentEntry
import com.inayatechlab.filemanagerpro.preview.PreviewActivity
import com.inayatechlab.filemanagerpro.util.Dialogs
import com.inayatechlab.filemanagerpro.util.FileCat
import com.inayatechlab.filemanagerpro.util.FavoritesStore
import com.inayatechlab.filemanagerpro.util.OpenUtils
import com.inayatechlab.filemanagerpro.util.StorageUtils
import java.io.File

/** Favorites tab: user-starred files & folders with sort / grouping options. */
class FavoritesFragment : Fragment() {

    private var _binding: FragmentFavoritesBinding? = null
    private val binding get() = _binding!!
    private val scope get() = viewLifecycleOwner.lifecycleScope

    private var adapter: FavoritesAdapter? = null
    private var sortByName = true
    private var groupByType = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFavoritesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        adapter = FavoritesAdapter(
            onOpen = { open(it) },
            onMenu = { showItemActions(it) }
        )
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
        act.setAppTitle(getString(R.string.nav_favorites))
        act.showCrumbBarVisible(false)
        act.setUpButton(false) {}
        installMenu(act)
        FavoritesStore.pruneMissing(requireContext(), scope)
        load()
    }

    /** (Re)installs the browse toolbar with only the favorites items visible. */
    private fun installMenu(act: MainActivity) {
        val menu = act.installBrowseMenu(::onMenuItem)
        menu.findItem(R.id.action_search)?.isVisible = false
        menu.findItem(R.id.action_new_file)?.isVisible = false
        menu.findItem(R.id.action_new_folder)?.isVisible = false
        menu.findItem(R.id.action_paste)?.isVisible = false
        menu.findItem(R.id.action_sort)?.isVisible = false
        menu.findItem(R.id.action_hidden)?.isVisible = false
        menu.findItem(R.id.action_select_all)?.isVisible = false
        menu.findItem(R.id.action_clear_history)?.isVisible = false
        menu.findItem(R.id.action_fav_group)?.isChecked = groupByType
    }

    private fun onMenuItem(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_fav_sort -> {
            val checked = if (sortByName) 0 else 1
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.fav_sort_title)
                .setSingleChoiceItems(
                    arrayOf(
                        getString(R.string.fav_sort_name),
                        getString(R.string.fav_sort_newest),
                        getString(R.string.fav_sort_oldest)
                    ),
                    checked
                ) { _, which ->
                    sortByName = which != 1
                }
                .setPositiveButton(R.string.action_ok) { _, _ -> load() }
                .show()
            true
        }
        R.id.action_fav_group -> {
            groupByType = !groupByType
            item.isChecked = groupByType
            load()
            true
        }
        else -> false
    }

    private fun load() {
        val ctx = requireContext()
        if (!StorageUtils.isStoragePermitted(ctx)) {
            binding.tvEmpty.isVisible = true
            binding.tvEmpty.text = getString(R.string.storage_permission_needed)
            binding.recycler.adapter = null
            return
        }
        binding.tvEmpty.setOnClickListener(null)
        val rows = FavoritesStore.favorites(ctx).sortedWith(
            if (sortByName) {
                compareBy<RecentEntry> { !it.isDir }.thenBy { it.name.lowercase() }
            } else {
                compareByDescending<RecentEntry> { it.time }
            }
        )
        binding.tvEmpty.isVisible = rows.isEmpty()
        binding.tvEmpty.text = getString(R.string.fav_empty)
        adapter?.submit(rows, groupByType) { count ->
            (activity as? MainActivity)?.setAppTitle(
                getString(R.string.nav_favorites) + " ($count)"
            )
        }
    }

    private fun open(entry: RecentEntry) {
        val file = File(entry.path)
        if (!file.exists()) {
            snack(getString(R.string.error))
            FavoritesStore.pruneMissing(requireContext(), scope)
            load()
            return
        }
        if (entry.isDir) {
            (activity as? MainActivity)?.openBrowsePath(entry.path)
            return
        }
        val fe = FileEntry(entry.name, entry.path, isDir = false)
        if (FileCat.of(fe) == FileCat.IMAGE) {
            val images = (adapter?.plainRows() ?: emptyList())
                .filter { FileCat.of(it.asFileEntry()) == FileCat.IMAGE }
                .map { it.path }
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

    private fun showItemActions(entry: RecentEntry) {
        val options = arrayOf(
            getString(R.string.action_open),
            getString(R.string.action_properties),
            getString(R.string.action_remove_favorite)
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
                        FavoritesStore.remove(requireContext(), entry.path, scope)
                        snack(getString(R.string.fav_removed))
                        load()
                    }
                }
            }
            .show()
    }

    private fun snack(text: String) {
        view?.let { Snackbar.make(it, text, Snackbar.LENGTH_LONG).show() }
    }

    companion object {
        fun newInstance() = FavoritesFragment()
    }
}
