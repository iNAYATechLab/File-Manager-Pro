package com.inayatechlab.filemanagerpro.recent

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.inayatechlab.filemanagerpro.R
import com.inayatechlab.filemanagerpro.model.FileEntry
import com.inayatechlab.filemanagerpro.model.RecentEntry
import com.inayatechlab.filemanagerpro.util.FileCat
import com.inayatechlab.filemanagerpro.util.FormatUtils
import com.inayatechlab.filemanagerpro.util.Icons
import com.inayatechlab.filemanagerpro.util.RelativeTime

/** Recent row: type icon, name and "Opened • 5 min ago • size/folder" sub-line. */
class RecentRowAdapter(
    private val onOpen: (RecentEntry) -> Unit,
    private val onMenu: (RecentEntry) -> Unit
) : RecyclerView.Adapter<RecentRowAdapter.Holder>() {

    private var rows: List<RecentEntry> = emptyList()

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val root: View = view.findViewById(R.id.itemRoot)
        val icon: ImageView = view.findViewById(R.id.ivIcon)
        val name: TextView = view.findViewById(R.id.tvName)
        val sub: TextView = view.findViewById(R.id.tvSub)
    }

    fun submit(list: List<RecentEntry>) {
        rows = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_recent_row, parent, false))

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = rows[position]
        val ctx = holder.root.context
        val fe = FileEntry(entry.name, entry.path, entry.isDir)
        val fileCat = FileCat.of(fe)

        val kind = ctx.getString(entry.kind.labelRes)
        val ago = RelativeTime.format(ctx, entry.time)
        val extra = if (entry.isDir) {
            " • " + ctx.getString(R.string.fav_type_folder)
        } else if (fe.size > 0) {
            " • " + FormatUtils.formatSize(fe.size)
        } else {
            ""
        }
        holder.name.text = entry.name
        holder.sub.text = "$kind • $ago$extra"

        holder.icon.background = circle(ctx.getColor(Icons.color(fileCat)))
        holder.icon.setImageResource(Icons.glyph(fileCat))
        holder.icon.imageTintList = ColorStateList.valueOf(Color.WHITE)

        holder.root.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onOpen(rows[pos])
        }
        holder.root.setOnLongClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onMenu(rows[pos])
            true
        }
    }

    private fun circle(color: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
}
