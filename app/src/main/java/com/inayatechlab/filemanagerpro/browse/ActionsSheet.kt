package com.inayatechlab.filemanagerpro.browse

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.inayatechlab.filemanagerpro.R
import com.inayatechlab.filemanagerpro.databinding.ItemActionRowBinding
import com.inayatechlab.filemanagerpro.databinding.SheetActionsBinding

/**
 * The mockup's bottom action sheet: rounded top, grab handle, a small header
 * and a list of icon + label rows (mockup `.sheet`).
 *
 * Used for the overflow actions of the selection bar today, and later for the
 * per-item `⋮` menu. Actions are supplied as lambdas, so the sheet dismisses
 * itself if it is ever recreated without them (e.g. after a rotation).
 */
class ActionsSheet : BottomSheetDialogFragment() {

    data class Action(
        @StringRes val label: Int,
        @DrawableRes val icon: Int,
        val danger: Boolean = false,
        val onPick: () -> Unit
    )

    var title: String = ""
    var actions: List<Action> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val b = SheetActionsBinding.inflate(inflater, container, false)
        if (actions.isEmpty()) {
            // Recreated without its actions (process/rotation) — nothing to show.
            dismiss()
            return b.root
        }

        b.tvSheetTitle.text = title
        b.tvSheetClose.setOnClickListener { dismiss() }

        actions.forEach { action ->
            val row = ItemActionRowBinding.inflate(inflater, b.sheetList, false)
            row.tvLabel.text = getString(action.label)
            row.ivIcon.setImageResource(action.icon)
            val colour = ContextCompat.getColor(
                requireContext(),
                if (action.danger) R.color.ui_danger else R.color.ui_muted
            )
            row.tvLabel.setTextColor(colour)
            row.ivIcon.setColorFilter(colour)
            row.actionRoot.setOnClickListener {
                dismiss()
                action.onPick()
            }
            b.sheetList.addView(row.root)
        }
        return b.root
    }

    companion object {
        const val TAG = "ActionsSheet"

        fun show(fragment: androidx.fragment.app.Fragment, title: String, actions: List<Action>) {
            ActionsSheet().apply {
                this.title = title
                this.actions = actions
            }.show(fragment.childFragmentManager, TAG)
        }

        /** Same sheet from a plain activity (e.g. the search screen). */
        fun show(
            activity: androidx.fragment.app.FragmentActivity,
            title: String,
            actions: List<Action>
        ) {
            ActionsSheet().apply {
                this.title = title
                this.actions = actions
            }.show(activity.supportFragmentManager, TAG)
        }
    }
}
