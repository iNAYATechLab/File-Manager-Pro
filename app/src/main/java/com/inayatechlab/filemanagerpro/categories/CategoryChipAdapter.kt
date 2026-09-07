package com.inayatechlab.filemanagerpro.categories

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.inayatechlab.filemanagerpro.databinding.ItemCategoryBinding
import com.inayatechlab.filemanagerpro.util.MediaCat

/** Horizontal row of single-select category chips. */
class CategoryChipAdapter(
    private val cats: List<MediaCat>,
    initialSelection: Int,
    private val onSelect: (MediaCat) -> Unit
) : RecyclerView.Adapter<CategoryChipAdapter.Holder>() {

    private var selected = initialSelection

    class Holder(val binding: ItemCategoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = cats.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val cat = cats[position]
        val chip = holder.binding.chipCategory
        chip.text = holder.itemView.context.getString(cat.titleRes)
        chip.isChecked = position == selected
        chip.setOnClickListener {
            if (selected != position) {
                val old = selected
                selected = position
                notifyItemChanged(old)
                notifyItemChanged(position)
                onSelect(cat)
            }
        }
    }
}
