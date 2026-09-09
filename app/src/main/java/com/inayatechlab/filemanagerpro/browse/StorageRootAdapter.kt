package com.inayatechlab.filemanagerpro.browse

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.inayatechlab.filemanagerpro.R
import com.inayatechlab.filemanagerpro.databinding.ItemSafCardBinding
import com.inayatechlab.filemanagerpro.databinding.ItemStorageRootBinding
import com.inayatechlab.filemanagerpro.util.FormatUtils
import com.inayatechlab.filemanagerpro.util.StorageRoot

/**
 * Storage home list: volume cards followed by the SAF (protected folders)
 * card when [grantedSafCount] is provided.
 */
class StorageRootAdapter(
    private val roots: List<StorageRoot>,
    private val onOpen: (StorageRoot) -> Unit,
    private val grantedSafCount: Int? = null,
    private val onSafClick: (() -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val safVisible: Boolean get() = grantedSafCount != null

    class RootHolder(val binding: ItemStorageRootBinding) : RecyclerView.ViewHolder(binding.root)

    class SafHolder(val binding: ItemSafCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int): Int =
        if (safVisible && position == roots.size) 1 else 0

    override fun getItemCount(): Int = roots.size + if (safVisible) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == 1) {
            SafHolder(ItemSafCardBinding.inflate(inflater, parent, false))
        } else {
            RootHolder(ItemStorageRootBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is RootHolder -> bindRoot(holder, roots[position])
            is SafHolder -> bindSaf(holder)
        }
    }

    private fun bindRoot(holder: RootHolder, root: StorageRoot) {
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

    private fun bindSaf(holder: SafHolder) {
        val b = holder.binding
        val ctx: Context = holder.itemView.context
        b.tvName.text = ctx.getString(R.string.saf_footer_title)
        val n = grantedSafCount ?: 0
        b.tvSub.text = if (n == 0) ctx.getString(R.string.saf_footer_sub_none)
        else ctx.getString(R.string.saf_footer_sub_fmt, n)
        b.ivIcon.setImageResource(R.drawable.ic_locked_folder)
        b.ivIcon.imageTintList =
            android.content.res.ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.brand_primary))
        b.root.setOnClickListener { onSafClick?.invoke() }
    }
}
