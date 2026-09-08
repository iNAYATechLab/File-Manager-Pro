package com.inayatechlab.filemanagerpro.browse

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.inayatechlab.filemanagerpro.R
import com.inayatechlab.filemanagerpro.databinding.ItemStorageRootBinding
import com.inayatechlab.filemanagerpro.util.FormatUtils
import com.inayatechlab.filemanagerpro.util.StorageRoot

/** List of storage volume cards shown on the Storage home screen. */
class StorageRootAdapter(
    private val roots: List<StorageRoot>,
    private val onOpen: (StorageRoot) -> Unit
) : RecyclerView.Adapter<StorageRootAdapter.Holder>() {

    class Holder(val binding: ItemStorageRootBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemStorageRootBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = roots.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val root = roots[position]
        val b = holder.binding
        val ctx: Context = holder.itemView.context

        b.tvName.text = root.label
        b.tvPath.text = root.file.path

        val used = (root.total - root.free).coerceAtLeast(0L)
        val pct = FormatUtils.formatPercent(used, root.total)
        b.progress.setProgressCompat(pct, true)
        b.tvStats.text = ctx.getString(
            R.string.storage_summary,
            FormatUtils.formatSize(used),
            FormatUtils.formatSize(root.free),
            FormatUtils.formatSize(root.total)
        )
        b.root.setOnClickListener { onOpen(root) }
    }
}
