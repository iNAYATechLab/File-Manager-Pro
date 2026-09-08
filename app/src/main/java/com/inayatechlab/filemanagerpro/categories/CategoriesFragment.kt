package com.inayatechlab.filemanagerpro.categories

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.inayatechlab.filemanagerpro.MainActivity
import com.inayatechlab.filemanagerpro.R
import com.inayatechlab.filemanagerpro.browse.FileAdapter
import com.inayatechlab.filemanagerpro.browse.FileOpsComparator
import com.inayatechlab.filemanagerpro.databinding.FragmentCategoriesBinding
import com.inayatechlab.filemanagerpro.model.FileEntry
import com.inayatechlab.filemanagerpro.ops.FileOps
import com.inayatechlab.filemanagerpro.preview.PreviewActivity
import com.inayatechlab.filemanagerpro.util.Dialogs
import com.inayatechlab.filemanagerpro.util.FileCat
import com.inayatechlab.filemanagerpro.util.MediaCat
import com.inayatechlab.filemanagerpro.util.OpenUtils
import com.inayatechlab.filemanagerpro.util.Scanner
import com.inayatechlab.filemanagerpro.util.StorageUtils
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CategoriesFragment : Fragment() {

    companion object {
        fun newInstance() = CategoriesFragment()
    }

    private var _binding: FragmentCategoriesBinding? = null
    private val binding get() = _binding!!
    private val scope get() = viewLifecycleOwner.lifecycleScope

    private var adapter: FileAdapter? = null
    private var currentCat = MediaCat.IMAGES
    private var chipAdapter: CategoryChipAdapter? = null
    private var scanJob: Job? = null
    private val scanning = AtomicBoolean(false)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCategoriesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.chipRecycler.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        chipAdapter = CategoryChipAdapter(MediaCat.values().toList(), 0) { cat ->
            currentCat = cat
            loadCategory()
        }
        binding.chipRecycler.adapter = chipAdapter

        adapter = FileAdapter(isGrid = false, selectable = false).apply {
            onItemClick = { openEntry(it) }
            onItemMenu = { showItemActions(it) }
        }
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
        if (_binding == null) return // view not ready yet — onResume will handle
        val act = activity as? MainActivity ?: return
        act.setAppTitle(getString(R.string.nav_categories))
        act.clearMenu()
        act.showCrumbBarVisible(false)
        act.setUpButton(false) {}
        loadCategory()
    }

    override fun onDestroyView() {
        scanJob?.cancel()
        _binding = null
        super.onDestroyView()
    }

    private fun loadCategory() {
        val b = _binding ?: return
        val ctx = requireContext()
        if (!StorageUtils.isStoragePermitted(ctx)) {
            b.tvEmpty.isVisible = true
            b.tvEmpty.text = getString(R.string.storage_permission_needed)
            b.tvEmpty.setOnClickListener {
                StorageUtils.requestStorageAccess(ctx)
            }
            return
        }
        b.tvEmpty.setOnClickListener(null)
        if (scanning.getAndSet(true)) return

        scanJob = scope.launch {
            try {
                val cat = currentCat
                _binding?.progress?.isVisible = true
                val result = withContext(Dispatchers.IO) {
                    val found = if (cat.filter == FileCat.IMAGE || cat.filter == FileCat.VIDEO || cat.filter == FileCat.AUDIO) {
                        Scanner.scanMedia(ctx, cat.filter!!)
                    } else {
                        Scanner.scanFilesystem(cat.filter ?: FileCat.GENERIC)
                    }
                    found.sortedWith(FileOpsComparator.comparator(1, false))
                }
                val b1 = _binding
                if (b1 == null) return@launch
                b1.progress.isVisible = false
                adapter?.entries = result.toMutableList()
                b1.tvEmpty.isVisible = result.isEmpty()
                b1.tvEmpty.text = getString(R.string.cat_empty)
                (activity as? MainActivity)?.setAppTitle(
                    getString(cat.titleRes) + (if (result.isNotEmpty()) " (${result.size})" else "")
                )
            } finally {
                scanning.set(false)
            }
        }
    }

    private fun openEntry(entry: FileEntry) {
        if (entry.isDir) return // categories only list files
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

    private fun showItemActions(entry: FileEntry) {
        val options = arrayOf(
            getString(R.string.action_share),
            getString(R.string.action_properties),
            getString(R.string.action_delete)
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(entry.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> if (!OpenUtils.share(requireContext(), listOf(entry))) snack(getString(R.string.no_app_found))
                    1 -> Dialogs.properties(requireContext(), entry, scope)
                    2 -> Dialogs.confirm(
                        requireContext(),
                        getString(R.string.dialog_delete_title),
                        entry.name,
                        getString(R.string.action_delete_confirm)
                    ) {
                        scope.launch {
                            FileOps.delete(listOf(entry))
                            loadCategory()
                        }
                    }
                }
            }
            .show()
    }

    private fun snack(text: String) {
        view?.let { Snackbar.make(it, text, Snackbar.LENGTH_LONG).show() }
    }
}
