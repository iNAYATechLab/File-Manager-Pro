package com.inayatechlab.filemanagerpro.preview

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.inayatechlab.filemanagerpro.databinding.ItemPreviewImageBinding
import java.io.File

/**
 * ViewPager2 adapter rendering local image files with Coil. Per-image session
 * rotations (90-degree steps, not persisted) live in [rotations] keyed by the
 * absolute file path so they survive page recycling.
 */
class ImagePagerAdapter(
    private val files: MutableList<File>
) : RecyclerView.Adapter<ImagePagerAdapter.Holder>() {

    /** Rotation in degrees per absolute path, applied on every bind. */
    val rotations = HashMap<String, Float>()

    class Holder(val binding: ItemPreviewImageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemPreviewImageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = files.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val file = files[position]
        holder.binding.root.tag = file.path
        holder.binding.ivImage.resetZoom()
        holder.binding.ivImage.rotation = rotations[file.path] ?: 0f
        holder.binding.progress.isVisible = true
        holder.binding.ivImage.load(file) {
            crossfade(true)
            listener(
                onSuccess = { _, _ -> holder.binding.progress.isVisible = false },
                onError = { _, _ -> holder.binding.progress.isVisible = false }
            )
        }
    }
}
