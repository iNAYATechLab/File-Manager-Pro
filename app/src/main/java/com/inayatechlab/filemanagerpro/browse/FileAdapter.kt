package com.inayatechlab.filemanagerpro.browse

import android.content.res.ColorStateList
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

    /** Selects (or unselects, when all matches are selected) entries matching [pred]. */
    fun selectAllWhere(pred: (FileEntry) -> Boolean) {
        val matching = entries.filter(pred)
        if (matching.isEmpty()) return
        val paths = matching.map { it.path }
        if (paths.all { it in selected }) {
            selected.removeAll(paths.toSet())
        } else {
            selected.addAll(paths)
        }
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
        val meta: android.widget.TextView = view.findViewById(R.id.tvMeta)
        val more: View = view.findViewById(R.id.btnMore)
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
        // Wired once per view: the handler looks the entry up by position, so
        // it never has to be re-assigned while the list scrolls.
        (root as? android.view.ViewGroup)?.findViewById<View?>(R.id.btnMore)
            ?.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                onItemMenu?.invoke(entries[pos])
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
        val ctx = holder.itemView.context
        val tint = ContextCompat.getColor(ctx, Icons.color(cat))

        /** Paints the glyph in its type colour on a soft tinted tile. */
        fun paintGlyph(icon: ImageView) {
            icon.background = Icons.tile(tint, ctx)
            icon.setImageResource(Icons.glyph(cat))
            icon.imageTintList = ColorStateList.valueOf(tint)
        }

        fun bindCommon(
            root: View,
            name: android.widget.TextView,
            sub: android.widget.TextView,
            check: ImageView,
            meta: android.widget.TextView?
        ) {
            name.text = entry.name
            val catLabel = ctx.getString(Icons.labelRes(cat))
            sub.text = if (entry.isDir && entry.childCount >= 0) {
                "${entry.childCount} ${ctx.getString(R.string.items_count)} • " +
                    ctx.getString(R.string.label_folder)
            } else {
                "$catLabel • ${FormatUtils.formatSize(entry.size)}"
            }
            // The mockup keeps the modification date in its own trailing column.
            meta?.text = FormatUtils.formatDate(entry.lastModified)
            root.isSelected = isSelected(entry)
            check.isVisible = isSelected(entry)
        }

        if (isGrid && holder is GridHolder) {
            bindCommon(holder.root, holder.name, holder.sub, holder.check, null)
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
                paintGlyph(holder.icon)
            }
        } else if (holder is ListHolder) {
            bindCommon(holder.root, holder.name, holder.sub, holder.check, holder.meta)
            paintGlyph(holder.icon)
            // The ⋮ menu belongs to normal browsing; selection has its own bar.
            holder.more.isVisible = onItemMenu != null && !selectionMode
        }
    }

}
