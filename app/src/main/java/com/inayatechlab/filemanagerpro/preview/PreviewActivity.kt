package com.inayatechlab.filemanagerpro.preview

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.inayatechlab.filemanagerpro.R
import com.inayatechlab.filemanagerpro.databinding.ActivityPreviewBinding
import com.inayatechlab.filemanagerpro.model.FileEntry
import com.inayatechlab.filemanagerpro.ops.FileOps
import com.inayatechlab.filemanagerpro.util.Dialogs
import com.inayatechlab.filemanagerpro.util.OpenUtils
import java.io.File
import kotlinx.coroutines.launch

/**
 * Full-screen image viewer. Shows one image at a time; swipe left/right to move
 * through the sibling images that were passed in [EXTRA_PATHS].
 */
class PreviewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PATHS = "paths"
        const val EXTRA_INDEX = "index"
    }

    private lateinit var binding: ActivityPreviewBinding
    private val images = mutableListOf<File>()
    private var current = 0
    private lateinit var adapter: ImagePagerAdapter
    private val scope get() = lifecycleScope

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val paths = intent.getStringArrayListExtra(EXTRA_PATHS) ?: emptyList()
        images.addAll(paths.mapNotNull { p ->
            File(p).takeIf { it.isFile && it.exists() }
        })
        if (images.isEmpty()) {
            finish()
            return
        }
        val startIndex = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, images.lastIndex)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.inflateMenu(R.menu.menu_preview)
        binding.toolbar.setOnMenuItemClickListener { item -> onMenu(item) }

        adapter = ImagePagerAdapter(images)
        binding.pager.adapter = adapter
        binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                current = position
                updateTitle()
            }
        })
        binding.pager.setCurrentItem(startIndex, false)
        current = startIndex
        updateTitle()
    }

    private fun currentEntry(): FileEntry {
        val f = images[current]
        return FileEntry(f.name, f.canonicalPath, false, f.length(), f.lastModified())
    }

    private fun updateTitle() {
        if (current !in images.indices) return
        val name = images[current].name
        binding.toolbar.title = "$name  (${current + 1}/${images.size})"
    }

    private fun onMenu(item: MenuItem): Boolean {
        if (images.isEmpty()) return true
        return when (item.itemId) {
            R.id.action_share -> {
                if (!OpenUtils.share(this, listOf(currentEntry()))) {
                    com.google.android.material.snackbar.Snackbar
                        .make(binding.root, getString(R.string.no_app_found), com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                        .show()
                }
                true
            }
            R.id.action_delete -> {
                confirmDeleteCurrent()
                true
            }
            R.id.action_properties -> {
                Dialogs.properties(this, currentEntry(), scope)
                true
            }
            else -> false
        }
    }

    private fun confirmDeleteCurrent() {
        if (images.isEmpty()) return
        val entry = currentEntry()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_delete_title)
            .setMessage(entry.name)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete_confirm) { _, _ ->
                deleteCurrent()
            }
            .show()
    }

    private fun deleteCurrent() {
        val entry = currentEntry()
        val dlg = Dialogs.showProgress(this, "Deleting…")
        scope.launch {
            val result = FileOps.delete(listOf(entry))
            dlg.dismiss()
            if (result.failed == 0) {
                images.removeAt(current)
                if (images.isEmpty()) {
                    finish()
                    return@launch
                }
                val pos = current.coerceIn(0, images.lastIndex)
                adapter.notifyDataSetChanged()
                binding.pager.setCurrentItem(pos, false)
                current = pos
                updateTitle()
            } else {
                com.google.android.material.snackbar.Snackbar
                    .make(binding.root, result.errors.firstOrNull() ?: getString(R.string.error), com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                    .show()
            }
        }
    }
}
