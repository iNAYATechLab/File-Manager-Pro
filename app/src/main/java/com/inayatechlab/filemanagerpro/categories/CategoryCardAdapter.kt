package com.inayatechlab.filemanagerpro.categories

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.inayatechlab.filemanagerpro.R
import com.inayatechlab.filemanagerpro.databinding.ItemCategoryCardBinding
import com.inayatechlab.filemanagerpro.util.FileCat
import com.inayatechlab.filemanagerpro.util.Icons
import com.inayatechlab.filemanagerpro.util.MediaCat
import kotlin.math.roundToInt

/**
 * Category cards for the Categories screen: each card is a type-tinted glyph
 * plus the category name (mockup drawer row). The selected card fills with
 * accent-soft and takes an accent outline, so it reads the same way the
 * quick-access chips do.
 */
class CategoryCardAdapter(
    private val cats: List<MediaCat>,
    initialSelection: Int,
    private val onSelect: (MediaCat) -> Unit
) : RecyclerView.Adapter<CategoryCardAdapter.Holder>() {

    private var selected = initialSelection

    class Holder(val binding: ItemCategoryCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemCategoryCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = cats.size

    /** Programmatic selection (drawer deep links). */
    fun select(position: Int) {
        if (position == selected || position !in cats.indices) return
        val old = selected
        selected = position
        notifyItemChanged(old)
        notifyItemChanged(position)
        onSelect(cats[position])
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val cat = cats[position]
        val ctx = holder.itemView.context
        val on = position == selected
        val cat4Icon = cat.filter ?: FileCat.GENERIC
        val tint = ContextCompat.getColor(ctx, Icons.color(cat4Icon))

        holder.binding.ivGlyph.setImageResource(Icons.glyph(cat4Icon))
        holder.binding.ivGlyph.imageTintList = ColorStateList.valueOf(tint)
        holder.binding.ivGlyph.backgroundTintList = ColorStateList.valueOf(
            Color.argb(0x24, Color.red(tint), Color.green(tint), Color.blue(tint))
        )
        holder.binding.tvName.text = ctx.getString(cat.titleRes)
        holder.binding.tvName.setTextColor(
            ContextCompat.getColor(ctx, if (on) R.color.ui_accent else R.color.ui_ink2)
        )
        holder.binding.tvName.setTypeface(
            holder.binding.tvName.typeface,
            if (on) Typeface.BOLD else Typeface.NORMAL
        )
        holder.binding.cardRoot.setCardBackgroundColor(
            ContextCompat.getColor(ctx, if (on) R.color.ui_accent_soft else R.color.ui_surface)
        )
        holder.binding.cardRoot.setStrokeColor(
            ContextCompat.getColor(ctx, if (on) R.color.ui_accent else R.color.ui_line)
        )
        holder.binding.cardRoot.setStrokeWidth(
            ((if (on) 2 else 1) * ctx.resources.displayMetrics.density).roundToInt()
        )

        holder.binding.cardRoot.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
            if (selected != pos) {
                val old = selected
                selected = pos
                notifyItemChanged(old)
                notifyItemChanged(pos)
                onSelect(cats[pos])
            }
        }
    }
}
