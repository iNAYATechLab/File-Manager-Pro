package com.inayatechlab.filemanagerpro.saf

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.inayatechlab.filemanagerpro.R
import com.inayatechlab.filemanagerpro.util.FileCat
import com.inayatechlab.filemanagerpro.util.FormatUtils
import com.inayatechlab.filemanagerpro.util.Icons

/**
 * Row kinds of the Protected folders (grant manager) screen.
 */
sealed class SafHomeRow {
    data class AddData(val hint: String) : SafHomeRow()
    data class AddObb(val hint: String) : SafHomeRow()
    data class AddCustom(val hint: String) : SafHomeRow()
    data class Location(val uri: Uri, val title: String, val sub: String, val granted: Boolean) : SafHomeRow()
}

/** Grant-manager list: "add" shortcuts on top, saved locations below. */
class SafHomeAdapter(
    private val rows: List<SafHomeRow>,
    private val onAddData: (SafHomeRow.AddData) -> Unit,
    private val onAddObb: (SafHomeRow.AddObb) -> Unit,
    private val onAddCustom: (SafHomeRow.AddCustom) -> Unit,
    private val onLocationClick: (SafHomeRow.Location) -> Unit,
    private val onLocationLong: (SafHomeRow.Location) -> Unit
) : RecyclerView.Adapter<SafHomeAdapter.Holder>() {

    companion object {
        private const val TYPE_ADD = 0
        private const val TYPE_LOCATION = 1
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val root: View = view.findViewById(R.id.itemRoot)
        val icon: ImageView = view.findViewById(R.id.ivIcon)
        val name: TextView = view.findViewById(R.id.tvName)
        val sub: TextView = view.findViewById(R.id.tvSub)
        val chevron: View = view.findViewById(R.id.ivChevron)
    }

    override fun getItemViewType(position: Int): Int =
        if (rows[position] is SafHomeRow.Location) TYPE_LOCATION else TYPE_ADD

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_saf_home, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val ctx = holder.itemView.context
        val row = rows[position]
        when (row) {
            is SafHomeRow.AddData -> {
                bindAdd(holder, ctx, R.drawable.ic_locked_folder, ctx.getString(R.string.saf_add_data), row.hint)
                holder.root.setOnClickListener { onAddData(row) }
            }
            is SafHomeRow.AddObb -> {
                bindAdd(holder, ctx, R.drawable.ic_locked_folder, ctx.getString(R.string.saf_add_obb), row.hint)
                holder.root.setOnClickListener { onAddObb(row) }
            }
            is SafHomeRow.AddCustom -> {
                bindAdd(holder, ctx, R.drawable.ic_folder, ctx.getString(R.string.saf_add_custom), row.hint)
                holder.root.setOnClickListener { onAddCustom(row) }
            }
            is SafHomeRow.Location -> {
                holder.name.text = row.title
                holder.sub.text = row.sub
                holder.sub.isVisible = true
                holder.icon.setImageResource(if (row.granted) R.drawable.ic_folder else R.drawable.ic_locked_folder)
                holder.icon.imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(
                        ctx,
                        if (row.granted) R.color.brand_primary else android.R.color.darker_gray
                    )
                )
                holder.chevron.isVisible = row.granted
                holder.root.setOnClickListener { onLocationClick(row) }
                holder.root.setOnLongClickListener {
                    onLocationLong(row)
                    true
                }
            }
        }
    }

    private fun bindAdd(holder: Holder, ctx: Context, iconRes: Int, title: String, hint: String) {
        holder.name.text = title
        holder.sub.text = hint
        holder.sub.isVisible = true
        holder.icon.setImageResource(iconRes)
        holder.icon.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(ctx, R.color.brand_primary)
        )
        holder.chevron.isVisible = false
        holder.root.setOnLongClickListener(null)
    }
}

/** Directory listing inside a granted tree (single-line file rows). */
class SafDirAdapter(
    val entries: MutableList<SafEntry>,
    val onOpen: (SafEntry) -> Unit,
    val onSelectionMode: (SafEntry) -> Unit
) : RecyclerView.Adapter<SafDirAdapter.Holder>() {

    val selected = linkedSetOf<String>() // docIds in selection order

    var selectionMode: Boolean = false
        set(value) {
            field = value
            if (!value) selected.clear()
            notifyDataSetChanged()
        }

    fun toggleSelected(entry: SafEntry) {
        if (!selected.remove(entry.docId)) selected.add(entry.docId)
        notifyDataSetChanged()
    }

    fun selectAll() {
        if (entries.isEmpty()) return
        val allIds = entries.map { it.docId }
        selected.addAll(allIds)
        notifyDataSetChanged()
    }

    fun selectedEntries(): List<SafEntry> =
        selected.mapNotNull { id -> entries.firstOrNull { it.docId == id } }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val root: View = view.findViewById(R.id.itemRoot)
        val icon: ImageView = view.findViewById(R.id.ivIcon)
        val name: TextView = view.findViewById(R.id.tvName)
        val sub: TextView = view.findViewById(R.id.tvSub)
        val check: ImageView = view.findViewById(R.id.ivCheck)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file_list, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = entries.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val ctx = holder.itemView.context
        val entry = entries[position]
        val isSel = entry.docId in selected
        holder.name.text = entry.name
        holder.sub.text = subText(ctx, entry)
        holder.root.isSelected = isSel

        val cat = categoryOf(entry)
        val tint = ContextCompat.getColor(ctx, Icons.color(cat))
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(tint)
        }
        holder.icon.background = bg
        holder.icon.setImageResource(Icons.glyph(cat))
        holder.icon.imageTintList = ColorStateList.valueOf(Color.WHITE)

        holder.check.isVisible = selectionMode
        if (selectionMode) {
            holder.check.setImageResource(
                if (isSel) R.drawable.ic_check_circle else R.drawable.ic_circle_outline
            )
            holder.check.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    ctx,
                    if (isSel) R.color.brand_primary else android.R.color.darker_gray
                )
            )
        }

        holder.root.setOnClickListener {
            if (selectionMode) toggleSelected(entry) else onOpen(entry)
        }
        holder.root.setOnLongClickListener {
            if (!selectionMode) {
                selectionMode = true
                toggleSelected(entry)
                onSelectionMode(entry)
            } else {
                toggleSelected(entry)
            }
            true
        }
    }

    private fun subText(ctx: Context, entry: SafEntry): String {
        val cat = categoryOf(entry)
        if (entry.isDir) return Icons.label(cat)
        return "${Icons.label(cat)} • ${FormatUtils.formatSize(entry.size)} • " +
            FormatUtils.formatDate(entry.lastModified)
    }

    private fun categoryOf(entry: SafEntry): FileCat {
        if (entry.isDir) return FileCat.FOLDER
        val byExt = FileCat.ofExtension(entry.name.substringAfterLast('.', ""))
        if (byExt != null) return byExt
        return when {
            entry.mime.startsWith("image/") -> FileCat.IMAGE
            entry.mime.startsWith("video/") -> FileCat.VIDEO
            entry.mime.startsWith("audio/") -> FileCat.AUDIO
            entry.mime == "application/pdf" -> FileCat.PDF
            entry.mime.startsWith("text/") -> FileCat.TEXT
            entry.mime.contains("zip") || entry.mime.contains("compressed") -> FileCat.ARCHIVE
            else -> FileCat.GENERIC
        }
    }
}
