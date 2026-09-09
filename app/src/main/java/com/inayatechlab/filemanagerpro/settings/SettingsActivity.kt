package com.inayatechlab.filemanagerpro.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.inayatechlab.filemanagerpro.R
import com.inayatechlab.filemanagerpro.databinding.ActivitySettingsBinding
import com.inayatechlab.filemanagerpro.util.SettingsStore

/** App settings: file-browser behaviour + about. Values persist via [SettingsStore]. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.title = getString(R.string.action_settings)

        // File browser
        binding.swHidden.isChecked = SettingsStore.showHiddenFiles(this)
        binding.swHidden.setOnCheckedChangeListener { _, checked ->
            SettingsStore.setShowHiddenFiles(this, checked)
        }

        binding.swGrid.isChecked = SettingsStore.gridView(this)
        binding.swGrid.setOnCheckedChangeListener { _, checked ->
            SettingsStore.setGridView(this, checked)
        }

        binding.swConfirmDelete.isChecked = SettingsStore.confirmDelete(this)
        binding.swConfirmDelete.setOnCheckedChangeListener { _, checked ->
            SettingsStore.setConfirmDelete(this, checked)
        }

        // Default sort preference (applies to new folder listings)
        binding.rowSort.setOnClickListener { showSortDialog() }
        updateSortSummary()

        // About: version line + dialog
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "?"
        }
        binding.tvVersion.text = getString(R.string.settings_version_value, version)
        binding.rowAbout.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.app_name)
                .setMessage(
                    getString(R.string.settings_version_value, version) + "\n" +
                        getString(R.string.settings_about_text)
                )
                .setPositiveButton(R.string.action_ok, null)
                .show()
        }
    }

    private fun updateSortSummary() {
        val ctx = this
        val mode = SettingsStore.sortMode(ctx)
        val asc = SettingsStore.sortAscending(ctx)
        val labelRes = when {
            mode == SettingsStore.SORT_NAME && asc -> R.string.sort_mode_name_asc
            mode == SettingsStore.SORT_NAME -> R.string.sort_mode_name_desc
            mode == SettingsStore.SORT_DATE && !asc -> R.string.sort_mode_date_newest
            mode == SettingsStore.SORT_DATE -> R.string.sort_mode_date_oldest
            mode == SettingsStore.SORT_SIZE && !asc -> R.string.sort_mode_size_largest
            mode == SettingsStore.SORT_SIZE -> R.string.sort_mode_size_smallest
            else -> R.string.sort_mode_type
        }
        binding.tvSortSummary.text = getString(labelRes)
    }

    private fun showSortDialog() {
        val labels = arrayOf(
            getString(R.string.sort_mode_name_asc),
            getString(R.string.sort_mode_name_desc),
            getString(R.string.sort_mode_date_newest),
            getString(R.string.sort_mode_date_oldest),
            getString(R.string.sort_mode_size_largest),
            getString(R.string.sort_mode_size_smallest),
            getString(R.string.sort_mode_type)
        )
        val current = when {
            SettingsStore.sortMode(this) == SettingsStore.SORT_NAME && SettingsStore.sortAscending(this) -> 0
            SettingsStore.sortMode(this) == SettingsStore.SORT_NAME -> 1
            SettingsStore.sortMode(this) == SettingsStore.SORT_DATE && !SettingsStore.sortAscending(this) -> 2
            SettingsStore.sortMode(this) == SettingsStore.SORT_DATE -> 3
            SettingsStore.sortMode(this) == SettingsStore.SORT_SIZE && !SettingsStore.sortAscending(this) -> 4
            SettingsStore.sortMode(this) == SettingsStore.SORT_SIZE -> 5
            else -> 6
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_default_sort)
            .setSingleChoiceItems(labels, current) { _, which ->
                val mode: Int
                val asc: Boolean
                when (which) {
                    0 -> { mode = SettingsStore.SORT_NAME; asc = true }
                    1 -> { mode = SettingsStore.SORT_NAME; asc = false }
                    2 -> { mode = SettingsStore.SORT_DATE; asc = false }
                    3 -> { mode = SettingsStore.SORT_DATE; asc = true }
                    4 -> { mode = SettingsStore.SORT_SIZE; asc = false }
                    5 -> { mode = SettingsStore.SORT_SIZE; asc = true }
                    else -> { mode = SettingsStore.SORT_TYPE; asc = true }
                }
                SettingsStore.setSortMode(this, mode)
                SettingsStore.setSortAscending(this, asc)
                updateSortSummary()
            }
            .setPositiveButton(R.string.action_ok, null)
            .show()
    }
}
