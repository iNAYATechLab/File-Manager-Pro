package com.inayatechlab.filemanagerpro.browse

import android.content.Context
import android.view.LayoutInflater
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.inayatechlab.filemanagerpro.R
import com.inayatechlab.filemanagerpro.databinding.DialogSortBinding
import com.inayatechlab.filemanagerpro.util.SettingsStore

/** Dialog with sort criterion + order + list/grid toggle + folders-first. */
object SortDialog {

    fun show(
        context: Context,
        sortMode: Int,
        sortAsc: Boolean,
        grid: Boolean,
        foldersFirst: Boolean,
        onResult: (mode: Int, asc: Boolean, grid: Boolean, foldersFirst: Boolean) -> Unit
    ) {
        val binding = DialogSortBinding.inflate(LayoutInflater.from(context))

        when (sortMode) {
            SettingsStore.SORT_DATE -> binding.rbDate.isChecked = true
            SettingsStore.SORT_SIZE -> binding.rbSize.isChecked = true
            SettingsStore.SORT_TYPE -> binding.rbType.isChecked = true
            SettingsStore.SORT_EXT -> binding.rbExt.isChecked = true
            SettingsStore.SORT_CREATED -> binding.rbCreated.isChecked = true
            else -> binding.rbName.isChecked = true
        }
        if (sortAsc) binding.rbAsc.isChecked = true else binding.rbDesc.isChecked = true
        if (grid) binding.rbGridView.isChecked = true else binding.rbListView.isChecked = true
        binding.chkFoldersFirst.isChecked = foldersFirst

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.action_sort)
            .setView(binding.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                val mode = when {
                    binding.rbDate.isChecked -> SettingsStore.SORT_DATE
                    binding.rbSize.isChecked -> SettingsStore.SORT_SIZE
                    binding.rbType.isChecked -> SettingsStore.SORT_TYPE
                    binding.rbExt.isChecked -> SettingsStore.SORT_EXT
                    binding.rbCreated.isChecked -> SettingsStore.SORT_CREATED
                    else -> SettingsStore.SORT_NAME
                }
                onResult(
                    mode,
                    binding.rbAsc.isChecked,
                    binding.rbGridView.isChecked,
                    binding.chkFoldersFirst.isChecked
                )
            }
            .show()
    }
}
