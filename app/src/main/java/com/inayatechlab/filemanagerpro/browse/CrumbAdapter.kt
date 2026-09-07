package com.inayatechlab.filemanagerpro.browse

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.inayatechlab.filemanagerpro.databinding.ItemCrumbBinding
import com.inayatechlab.filemanagerpro.model.PathCrumb

class CrumbAdapter(
    private val crumbs: List<PathCrumb>,
    private val onClick: (PathCrumb) -> Unit
) : RecyclerView.Adapter<CrumbAdapter.Holder>() {

    class Holder(val binding: ItemCrumbBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemCrumbBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = crumbs.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val crumb = crumbs[position]
        holder.binding.tvCrumb.text = crumb.label
        holder.binding.tvCrumb.setTextColor(
            if (position == crumbs.lastIndex) {
                androidx.core.content.ContextCompat.getColor(holder.itemView.context, com.inayatechlab.filemanagerpro.R.color.brand_primary)
            } else {
                com.google.android.material.color.MaterialColors.getColor(
                    holder.itemView,
                    com.google.android.material.R.attr.colorOnSurfaceVariant
                )
            }
        )
        holder.binding.tvCrumb.setOnClickListener { onClick(crumb) }
    }
}
