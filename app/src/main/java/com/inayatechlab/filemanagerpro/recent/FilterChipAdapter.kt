package com.inayatechlab.filemanagerpro.recent

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.inayatechlab.filemanagerpro.R
import com.inayatechlab.filemanagerpro.databinding.ItemFilterChipBinding
import com.inayatechlab.filemanagerpro.util.RecentStore

/** Horizontal single-select row of recent-history filter chips. */
class FilterChipAdapter(
    private val filters: List<RecentStore.Filter>,
    initial: Int,
    private val onSelect: (RecentStore.Filter) -> Unit
) : RecyclerView.Adapter<FilterChipAdapter.Holder>() {

    private var selected = initial

    class Holder(val binding: ItemFilterChipBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemFilterChipBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = filters.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val filter = filters[position]
        val chip = holder.binding.chipFilter
        chip.text = holder.itemView.context.getString(filter.labelRes)
        chip.isChecked = position == selected
        chip.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
            if (selected != pos) {
                val old = selected
                selected = pos
                notifyItemChanged(old)
                notifyItemChanged(pos)
                onSelect(filters[pos])
            }
        }
    }
}
