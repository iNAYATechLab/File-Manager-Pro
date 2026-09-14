package com.inayatechlab.filemanagerpro.browse

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
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
        val ctx = holder.itemView.context
        val last = position == crumbs.lastIndex
        with(holder.binding) {
            tvCrumb.text = crumb.label
            tvSep.isVisible = !last
            tvCrumb.setTextColor(
                androidx.core.content.ContextCompat.getColor(
                    ctx,
                    if (last) com.inayatechlab.filemanagerpro.R.color.ui_accent
                    else com.inayatechlab.filemanagerpro.R.color.ui_muted
                )
            )
            tvCrumb.setOnClickListener { onClick(crumb) }
        }
    }
}
