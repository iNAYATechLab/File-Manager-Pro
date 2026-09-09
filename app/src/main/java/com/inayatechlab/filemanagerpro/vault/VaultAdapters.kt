package com.inayatechlab.filemanagerpro.vault

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.inayatechlab.filemanagerpro.R
import com.inayatechlab.filemanagerpro.databinding.ItemVaultEntryBinding
import com.inayatechlab.filemanagerpro.databinding.ItemVaultOverviewBinding
import com.inayatechlab.filemanagerpro.util.FileCat
import com.inayatechlab.filemanagerpro.util.FormatUtils
import com.inayatechlab.filemanagerpro.util.Icons
import java.io.File

/** One row on the vault overview screen (a single .fmpvault). */
data class VaultRow(
    val vaultDir: File,
    val name: String,
    val subText: String
)

class VaultOverviewAdapter(
    private var rows: List<VaultRow>,
    private val onOpen: (File) -> Unit
) : RecyclerView.Adapter<VaultOverviewAdapter.Holder>() {

    class Holder(val binding: ItemVaultOverviewBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemVaultOverviewBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = rows.size

    fun submit(list: List<VaultRow>) {
        rows = list
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = rows[position]
        holder.binding.tvName.text = row.name
        holder.binding.tvSub.text = row.subText
        holder.binding.root.setOnClickListener { onOpen(row.vaultDir) }
    }
}

/** One row inside an unlocked vault (a plaintext entry index item). */
class VaultEntriesAdapter(
    private val onOpen: (VaultFormat.VaultEntry) -> Unit,
    private val onMenu: (VaultFormat.VaultEntry) -> Unit
) : RecyclerView.Adapter<VaultEntriesAdapter.Holder>() {

    private var entries: List<VaultFormat.VaultEntry> = emptyList()

    class Holder(val binding: ItemVaultEntryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemVaultEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = entries.size

    fun submit(list: List<VaultFormat.VaultEntry>) {
        entries = list
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = entries[position]
        val ctx = holder.binding.root.context
        holder.binding.tvName.text = entry.name
        val parentDir = entry.relPath.substringBeforeLast('/', "")
        val where = if (parentDir.isEmpty()) ctx.getString(R.string.vlt_loc_root)
        else parentDir
        holder.binding.tvSub.text = "$where • ${FormatUtils.formatSize(entry.size)} • " +
            FormatUtils.formatDate(entry.mtimeMs)
        val ext = entry.name.substringAfterLast('.', "").lowercase()
        val cat = FileCat.ofExtension(ext)
        holder.binding.ivIcon.setImageResource(if (cat == null) Icons.glyph(FileCat.GENERIC) else Icons.glyph(cat))
        holder.binding.root.setOnClickListener { onOpen(entry) }
        holder.binding.btnMore.setOnClickListener { onMenu(entry) }
    }
}
