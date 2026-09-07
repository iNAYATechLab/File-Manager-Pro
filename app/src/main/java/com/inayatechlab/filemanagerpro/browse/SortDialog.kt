package com.inayatechlab.filemanagerpro.browse

import android.content.Context
import android.view.LayoutInflater
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.inayatechlab.filemanagerpro.R
import com.inayatechlab.filemanagerpro.databinding.DialogSortBinding

/** Dialog with sort criterion + order + list/grid toggle. */
object SortDialog {

    fun show(
        context: Context,
        sortMode: Int,
        sortAsc: Boolean,
        grid: Boolean,
        onResult: (mode: Int, asc: Boolean, grid: Boolean) -> Unit
    ) {
        val binding = DialogSortBinding.inflate(LayoutInflater.from(context))

        when (sortMode) {
            1 -> binding.rbDate.isChecked = true
            2 -> binding.rbSize.isChecked = true
            3 -> binding.rbType.isChecked = true
            else -> binding.rbName.isChecked = true
        }
        if (sortAsc) binding.rbAsc.isChecked = true else binding.rbDesc.isChecked = true
        if (grid) binding.rbGridView.isChecked = true else binding.rbListView.isChecked = true

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.action_sort)
            .setView(binding.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                val mode = when {
                    binding.rbDate.isChecked -> 1
                    binding.rbSize.isChecked -> 2
                    binding.rbType.isChecked -> 3
                    else -> 0
                }
                onResult(mode, binding.rbAsc.isChecked, binding.rbGridView.isChecked)
            }
            .show()
    }
}
