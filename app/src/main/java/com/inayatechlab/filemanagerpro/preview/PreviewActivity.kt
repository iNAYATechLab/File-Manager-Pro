package com.inayatechlab.filemanagerpro.preview

import android.app.WallpaperManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.inayatechlab.filemanagerpro.R
import com.inayatechlab.filemanagerpro.databinding.ActivityPreviewBinding
import com.inayatechlab.filemanagerpro.model.FileEntry
import com.inayatechlab.filemanagerpro.ops.TrashOps
import com.inayatechlab.filemanagerpro.util.Dialogs
import com.inayatechlab.filemanagerpro.util.OpenUtils
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full-screen image viewer with swipe paging, pinch zoom / double-tap zoom,
 * 90-degree rotation (session state), an EXIF details panel and a
 * set-as-wallpaper action. Deletion removes the image from the pager.
 */
class PreviewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PATHS = "paths"
        const val EXTRA_INDEX = "index"
    }

    private lateinit var binding: ActivityPreviewBinding
    private val images = mutableListOf<File>()
    private var current = 0
    private lateinit var adapter: ImagePagerAdapter
    private val scope get() = lifecycleScope

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val paths = intent.getStringArrayListExtra(EXTRA_PATHS) ?: emptyList()
        images.addAll(paths.mapNotNull { p ->
            File(p).takeIf { it.isFile && it.exists() }
        })
        if (images.isEmpty()) {
            finish()
            return
        }
        val startIndex = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, images.lastIndex)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.inflateMenu(R.menu.menu_preview)
        binding.toolbar.setOnMenuItemClickListener { item -> onMenu(item) }

        adapter = ImagePagerAdapter(images)
        binding.pager.adapter = adapter
        binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                current = position
                updateTitle()
            }
        })
        binding.pager.setCurrentItem(startIndex, false)
        current = startIndex
        updateTitle()
    }

    private fun currentFile(): File? = images.getOrNull(current)

    private fun currentEntry(): FileEntry {
        val f = images[current]
        return FileEntry(f.name, f.canonicalPath, false, f.length(), f.lastModified())
    }

    private fun currentRotation(): Float {
        val f = currentFile() ?: return 0f
        return adapter.rotations[f.path] ?: 0f
    }

    private fun updateTitle() {
        if (current !in images.indices) return
        val name = images[current].name
        binding.toolbar.title = "$name  (${current + 1}/${images.size})"
    }

    private fun onMenu(item: MenuItem): Boolean {
        if (images.isEmpty()) return true
        return when (item.itemId) {
            R.id.action_share -> {
                if (!OpenUtils.share(this, listOf(currentEntry()))) {
                    snack(getString(R.string.no_app_found))
                }
                true
            }
            R.id.action_delete -> {
                confirmDeleteCurrent()
                true
            }
            R.id.action_properties -> {
                Dialogs.properties(this, currentEntry(), scope)
                true
            }
            R.id.action_rotate -> {
                rotateCurrent()
                true
            }
            R.id.action_exif -> {
                showExifDialog()
                true
            }
            R.id.action_wallpaper -> {
                setWallpaper()
                true
            }
            else -> false
        }
    }

    // ------------------------------------------------------------ rotate

    private fun rotateCurrent() {
        val f = currentFile() ?: return
        val path = f.path
        val next = ((adapter.rotations[path] ?: 0f) + 90f) % 360f
        adapter.rotations[path] = next
        val pageView = binding.pager.findViewWithTag(path) as? android.view.View ?: return
        val image = pageView.findViewById<android.view.View>(R.id.ivImage) ?: return
        image.rotation = next
    }

    // ------------------------------------------------------------ EXIF

    private fun showExifDialog() {
        val f = currentFile() ?: return
        val lines = mutableListOf<String>()
        lines += getString(R.string.exif_name_fmt, f.name)
        try {
            val exif = ExifInterface(f)
            fun add(tag: String, label: String) {
                exif.getAttribute(tag)?.takeIf { it.isNotBlank() && it != "0" }?.let {
                    lines += getString(R.string.exif_line_fmt, label, it)
                }
            }
            add(ExifInterface.TAG_DATETIME_ORIGINAL, getString(R.string.exif_taken))
            add(ExifInterface.TAG_MAKE, getString(R.string.exif_make))
            add(ExifInterface.TAG_MODEL, getString(R.string.exif_model))
            add(ExifInterface.TAG_LENS_MODEL, getString(R.string.exif_lens))
            add(ExifInterface.TAG_F_NUMBER, getString(R.string.exif_aperture))
            add(ExifInterface.TAG_EXPOSURE_TIME, getString(R.string.exif_exposure))
            add(ExifInterface.TAG_ISO_SPEED_RATINGS, getString(R.string.exif_iso))
            add(ExifInterface.TAG_FOCAL_LENGTH, getString(R.string.exif_focal))
            add(ExifInterface.TAG_FLASH, getString(R.string.exif_flash))
            add(ExifInterface.TAG_WHITE_BALANCE, getString(R.string.exif_white_balance))
            add(ExifInterface.TAG_PIXEL_X_DIMENSION, getString(R.string.exif_width))
            add(ExifInterface.TAG_PIXEL_Y_DIMENSION, getString(R.string.exif_height))
            add(ExifInterface.TAG_SOFTWARE, getString(R.string.exif_software))
            val lat = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)
            val lon = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)
            val latRef = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF)
            val lonRef = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF)
            if (!lat.isNullOrBlank() && !lon.isNullOrBlank()) {
                lines += getString(
                    R.string.exif_line_fmt,
                    getString(R.string.exif_gps),
                    "$lat ${latRef ?: ""}, $lon ${lonRef ?: ""}"
                )
            }
        } catch (_: Exception) {
        }
        if (lines.size == 1) {
            lines += getString(R.string.exif_none)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.exif_title)
            .setMessage(lines.joinToString("\n"))
            .setPositiveButton(R.string.action_ok, null)
            .show()
    }

    // ------------------------------------------------------------ wallpaper

    private fun setWallpaper() {
        val f = currentFile() ?: return
        val rotation = currentRotation()
        snack(getString(R.string.wallpaper_setting))
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bitmap = decodeForWallpaper(f)
                    if (bitmap == null) {
                        null
                    } else {
                        val rotated = if (rotation != 0f) {
                            val m = Matrix().apply { postRotate(rotation) }
                            val out = Bitmap.createBitmap(
                                bitmap, 0, 0, bitmap.width, bitmap.height, m, true
                            )
                            if (out !== bitmap) bitmap.recycle()
                            out
                        } else {
                            bitmap
                        }
                        val wm = WallpaperManager.getInstance(this@PreviewActivity)
                        try {
                            wm.setBitmap(rotated, null, true, WallpaperManager.FLAG_SYSTEM)
                        } catch (_: SecurityException) {
                            wm.setBitmap(rotated)
                        }
                        rotated.recycle()
                        true
                    }
                }.getOrNull() ?: false
            }
            snack(
                if (result) getString(R.string.wallpaper_set_ok)
                else getString(R.string.wallpaper_set_failed)
            )
        }
    }

    /** Decodes the file at a bounded size to protect memory for wallpaper use. */
    private fun decodeForWallpaper(f: File): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
        val maxDim = 4096
        var sample = 1
        while (opts.outWidth / sample > maxDim || opts.outHeight / sample > maxDim) {
            sample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(f.path, decodeOpts)
    }

    // ------------------------------------------------------------ delete

    private fun confirmDeleteCurrent() {
        if (images.isEmpty()) return
        val entry = currentEntry()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.trash_confirm_title)
            .setMessage(getString(R.string.trash_confirm_message) + "\n\n" + entry.name)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.trash_confirm_ok) { _, _ ->
                deleteCurrent()
            }
            .show()
    }

    private fun deleteCurrent() {
        val entry = currentEntry()
        val path = entry.path
        val dlg = Dialogs.showProgress(this, getString(R.string.trash_op_progress))
        scope.launch {
            val result = TrashOps.move(this@PreviewActivity, listOf(entry))
            dlg.dismiss()
            if (result.failed == 0) {
                adapter.rotations.remove(path)
                images.removeAt(current)
                if (images.isEmpty()) {
                    finish()
                    return@launch
                }
                val pos = current.coerceIn(0, images.lastIndex)
                adapter.notifyDataSetChanged()
                binding.pager.setCurrentItem(pos, false)
                current = pos
                updateTitle()
            } else {
                snack(getString(R.string.trash_failed_fmt, entry.name))
            }
        }
    }

    private fun snack(text: String) {
        Snackbar.make(binding.root, text, Snackbar.LENGTH_SHORT).show()
    }
}
