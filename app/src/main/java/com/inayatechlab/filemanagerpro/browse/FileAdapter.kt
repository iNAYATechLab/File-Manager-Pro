package com.inayatechlab.filemanagerpro.browse

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.inayatechlab.filemanagerpro.R
import com.inayatechlab.filemanagerpro.model.FileEntry
import com.inayatechlab.filemanagerpro.util.FileCat
import com.inayatechlab.filemanagerpro.util.FormatUtils
import com.inayatechlab.filemanagerpro.util.Icons

/**
 * Recycler adapter showing files in list or grid mode.
 *
 * When [selectable] is true, long-press toggles selection; a check indicator is drawn
 * on every item while [selectionMode] is active.
 */
class FileAdapter(
    private val isGrid: Boolean,
    private val selectable: Boolean = true
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var entries: MutableList<FileEntry> = mutableListOf()
        set(value) {
            field = value
            if (!selectable) selected.clear()
            notifyDataSetChanged()
        }

    val selected = linkedSetOf<String>() // entry paths in selection order

    var selectionMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    var onItemClick: ((FileEntry) -> Unit)? = null
    var onItemLongClick: ((FileEntry) -> Unit)? = null
    var onItemMenu: ((FileEntry) -> Unit)? = null
    var onSelectionChanged: (() -> Unit)? = null

    fun isSelected(entry: FileEntry) = entry.path in selected

    fun select(entry: FileEntry, toggle: Boolean) {
        if (toggle) {
            if (!selected.remove(entry.path)) selected.add(entry.path)
        } else if (!selected.contains(entry.path)) {
            selected.add(entry.path)
        }
        val pos = entries.indexOfFirst { it.path == entry.path }
        if (pos >= 0) notifyItemChanged(pos)
        onSelectionChanged?.invoke()
    }

    fun selectAll() {
        val paths = entries.map { it.path }
        if (selected.containsAll(paths)) {
            selected.clear()
        } else {
            selected.addAll(paths)
        }
        notifyDataSetChanged()
        onSelectionChanged?.invoke()
    }

    fun clearSelection() {
        if (selected.isEmpty()) return
        selected.clear()
        notifyDataSetChanged()
        onSelectionChanged?.invoke()
    }

    fun selectedEntries(): List<FileEntry> =
        entries.filter { it.path in selected }

    private class ListHolder(view: View) : RecyclerView.ViewHolder(view) {
        val root: View = view.findViewById(R.id.itemRoot)
        val icon: ImageView = view.findViewById(R.id.ivIcon)
        val name: android.widget.TextView = view.findViewById(R.id.tvName)
        val sub: android.widget.TextView = view.findViewById(R.id.tvSub)
        val check: ImageView = view.findViewById(R.id.ivCheck)
    }

    private class GridHolder(view: View) : RecyclerView.ViewHolder(view) {
        val root: View = view.findViewById(R.id.itemRoot)
        val icon: ImageView = view.findViewById(R.id.ivIcon)
        val thumb: ImageView = view.findViewById(R.id.ivThumb)
        val name: android.widget.TextView = view.findViewById(R.id.tvName)
        val sub: android.widget.TextView = view.findViewById(R.id.tvSub)
        val check: ImageView = view.findViewById(R.id.ivCheck)
    }

    override fun getItemViewType(position: Int): Int = if (isGrid) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = if (isGrid) {
            inflater.inflate(R.layout.item_file_grid, parent, false)
        } else {
            inflater.inflate(R.layout.item_file_list, parent, false)
        }
        val holder: RecyclerView.ViewHolder = if (isGrid) GridHolder(view) else ListHolder(view)
        val root: View = view.findViewById(R.id.itemRoot)
        root.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
            val entry = entries[pos]
            if (selectionMode) {
                select(entry, toggle = true)
            } else {
                onItemClick?.invoke(entry)
            }
        }
        root.setOnLongClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnLongClickListener false
            val entry = entries[pos]
            if (!selectable) {
                onItemMenu?.invoke(entry)
                return@setOnLongClickListener true
            }
            if (!selectionMode) {
                // Enter selection mode with this item selected
                selectionMode = true
                select(entry, toggle = true)
                onItemLongClick?.invoke(entry)
            } else {
                select(entry, toggle = true)
            }
            true
        }
        return holder
    }

    override fun getItemCount(): Int = entries.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val entry = entries[position]
        val cat = FileCat.of(entry)
        val tint = ContextCompat.getColor(holder.itemView.context, Icons.color(cat))
        val white = Color.WHITE

        fun bindCommon(root: View, icon: ImageView, name: android.widget.TextView, sub: android.widget.TextView, check: ImageView) {
            name.text = entry.name
            val ctx = holder.itemView.context
            val catLabel = ctx.getString(Icons.labelRes(cat))
            sub.text = if (entry.isDir) {
                if (entry.childCount >= 0) {
                    "${entry.childCount} ${ctx.getString(R.string.items_count)}"
                } else {
                    catLabel
                }
            } else {
                "$catLabel • ${FormatUtils.formatSize(entry.size)} • ${FormatUtils.formatDate(entry.lastModified)}"
            }
            root.isSelected = isSelected(entry)
            check.isVisible = selectionMode
            if (selectionMode) {
                check.setImageResource(if (isSelected(entry)) R.drawable.ic_check_circle else R.drawable.ic_circle_outline)
                check.imageTintList = ColorStateList.valueOf(
                    if (isSelected(entry)) {
                        ContextCompat.getColor(holder.itemView.context, R.color.brand_primary)
                    } else {
                        ContextCompat.getColor(holder.itemView.context, android.R.color.darker_gray)
                    }
                )
            }
        }

        if (isGrid && holder is GridHolder) {
            bindCommon(holder.root, holder.icon, holder.name, holder.sub, holder.check)
            holder.thumb.isVisible = cat == FileCat.IMAGE
            if (cat == FileCat.IMAGE) {
                holder.thumb.load(entry.file) {
                    crossfade(true)
                    transformations(RoundedCornersTransformation(20f))
                    error(android.R.color.transparent)
                    listener(
                        onError = { _, _ ->
                            holder.thumb.isVisible = false
                            holder.icon.visibility = View.VISIBLE
                        }
                    )
                }
                holder.icon.visibility = View.GONE
            } else {
                holder.icon.visibility = View.VISIBLE
                holder.icon.background = circle(tint)
                holder.icon.setImageResource(Icons.glyph(cat))
                holder.icon.imageTintList = ColorStateList.valueOf(white)
            }
        } else if (holder is ListHolder) {
            bindCommon(holder.root, holder.icon, holder.name, holder.sub, holder.check)
            holder.icon.background = circle(tint)
            holder.icon.setImageResource(Icons.glyph(cat))
            holder.icon.imageTintList = ColorStateList.valueOf(white)
        }
    }

    private fun circle(color: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
}
