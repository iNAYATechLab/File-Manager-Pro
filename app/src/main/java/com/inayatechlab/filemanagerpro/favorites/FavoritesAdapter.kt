package com.inayatechlab.filemanagerpro.favorites

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import com.inayatechlab.filemanagerpro.R
import com.inayatechlab.filemanagerpro.databinding.ItemFavHeaderBinding
import com.inayatechlab.filemanagerpro.model.FileEntry
import com.inayatechlab.filemanagerpro.model.RecentEntry
import com.inayatechlab.filemanagerpro.util.FileCat
import com.inayatechlab.filemanagerpro.util.FormatUtils
import com.inayatechlab.filemanagerpro.util.Icons

/**
 * Grouped list of favorites. With [groupByType] the entries are split under
 * localized headers ("Folders", "Images", …); without grouping all are plain.
 */
class FavoritesAdapter(
    private val onOpen: (RecentEntry) -> Unit,
    private val onMenu: (RecentEntry) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private class HeaderLabel(@StringRes val labelRes: Int, val count: Int)

    private class RowHolder(view: View) : RecyclerView.ViewHolder(view) {
        val root: View = view.findViewById(R.id.itemRoot)
        val icon: ImageView = view.findViewById(R.id.ivIcon)
        val name: TextView = view.findViewById(R.id.tvName)
        val sub: TextView = view.findViewById(R.id.tvSub)
    }

    private class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        val binding = ItemFavHeaderBinding.bind(view)
    }

    private val rows = mutableListOf<Any>() // RecentEntry | HeaderLabel

    private val groupLabels: List<Pair<Int, (FileCat, Boolean) -> Boolean>> = listOf(
        R.string.fav_group_images to { cat, _ -> cat == FileCat.IMAGE },
        R.string.fav_group_videos to { cat, _ -> cat == FileCat.VIDEO },
        R.string.fav_group_audio to { cat, _ -> cat == FileCat.AUDIO },
        R.string.fav_group_archives to { cat, _ -> cat == FileCat.ARCHIVE },
        R.string.fav_group_documents to { cat, _ -> cat in setOf(FileCat.PDF, FileCat.DOC, FileCat.TEXT) },
        R.string.fav_group_apps to { cat, _ -> cat == FileCat.APK }
    )

    fun submit(entries: List<RecentEntry>, groupByType: Boolean, onLoaded: (Int) -> Unit = {}) {
        rows.clear()
        if (groupByType) {
            val remaining = entries.toMutableList()
            val folders = remaining.filter { it.isDir }
            if (folders.isNotEmpty()) {
                rows.add(HeaderLabel(R.string.fav_group_folders, folders.size))
                rows.addAll(folders)
                remaining.removeAll(folders.toSet())
            }
            for ((labelRes, pred) in groupLabels) {
                val matched = remaining.filter {
                    pred(FileCat.of(FileEntry(it.name, it.path, it.isDir)), it.isDir)
                }
                if (matched.isEmpty()) continue
                rows.add(HeaderLabel(labelRes, matched.size))
                rows.addAll(matched)
                remaining.removeAll(matched.toSet())
            }
            if (remaining.isNotEmpty()) {
                rows.add(HeaderLabel(R.string.fav_group_files, remaining.size))
                rows.addAll(remaining)
            }
        } else {
            rows.addAll(entries)
        }
        onLoaded(entries.size)
        notifyDataSetChanged()
    }

    /** Flattened (un-grouped) entries, used for gallery navigation. */
    fun plainRows(): List<RecentEntry> = rows.filterIsInstance<RecentEntry>()

    override fun getItemViewType(position: Int): Int =
        if (rows[position] is HeaderLabel) TYPE_HEADER else TYPE_ROW

    override fun getItemCount(): Int = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderHolder(inflater.inflate(R.layout.item_fav_header, parent, false))
        } else {
            RowHolder(inflater.inflate(R.layout.item_fav_row, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = rows[position]) {
            is HeaderLabel -> {
                (holder as HeaderHolder).binding.tvHeader.text =
                    holder.itemView.context.getString(item.labelRes, item.count)
            }
            is RecentEntry -> {
                val h = holder as RowHolder
                val ctx = h.root.context
                val fe = FileEntry(item.name, item.path, item.isDir)
                val fileCat = FileCat.of(fe)
                h.name.text = item.name
                val type = if (item.isDir) ctx.getString(R.string.fav_type_folder) else Icons.label(fileCat)
                val size = if (item.isDir || fe.size <= 0) "" else " • ${FormatUtils.formatSize(fe.size)}"
                h.sub.text = "$type$size • ${FormatUtils.formatDate(item.time)}"
                h.icon.background = circle(ctx.getColor(Icons.color(fileCat)))
                h.icon.setImageResource(Icons.glyph(fileCat))
                h.icon.imageTintList = ColorStateList.valueOf(Color.WHITE)
                h.root.setOnClickListener {
                    val pos = h.bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) onOpen(rows[pos] as RecentEntry)
                }
                h.root.setOnLongClickListener {
                    val pos = h.bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) onMenu(rows[pos] as RecentEntry)
                    true
                }
            }
        }
    }

    private fun circle(color: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ROW = 1
    }
}
