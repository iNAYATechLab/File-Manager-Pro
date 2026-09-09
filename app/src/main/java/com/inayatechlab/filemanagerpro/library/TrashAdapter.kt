package com.inayatechlab.filemanagerpro.library

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.inayatechlab.filemanagerpro.R
import com.inayatechlab.filemanagerpro.model.TrashStore
import com.inayatechlab.filemanagerpro.util.FormatUtils
import java.io.File

/** Rows for the Trash section of the Library tab. */
class TrashAdapter : RecyclerView.Adapter<TrashAdapter.TrashHolder>() {

    var items: MutableList<TrashStore.Item> = mutableListOf()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var onItemClick: ((TrashStore.Item) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrashHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_trash, parent, false)
        return TrashHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: TrashHolder, position: Int) {
        val item = items[position]
        val ctx = holder.itemView.context
        holder.name.text = item.name
        val parent = File(item.origPath).parentFile?.name ?: "—"
        holder.sub.text = ctx.getString(
            R.string.trash_sub_fmt,
            parent,
            FormatUtils.formatDate(item.trashedAt)
        )
        holder.icon.setImageResource(R.drawable.ic_delete)
        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onItemClick?.invoke(items[pos])
        }
    }

    class TrashHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivIcon)
        val name: TextView = view.findViewById(R.id.tvName)
        val sub: TextView = view.findViewById(R.id.tvSub)
    }
}
