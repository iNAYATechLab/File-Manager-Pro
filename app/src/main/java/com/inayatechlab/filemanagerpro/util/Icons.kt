package com.inayatechlab.filemanagerpro.util

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.inayatechlab.filemanagerpro.R

object Icons {

    @DrawableRes
    fun glyph(cat: FileCat): Int = when (cat) {
        FileCat.FOLDER -> R.drawable.ic_folder
        FileCat.IMAGE -> R.drawable.ic_image
        FileCat.VIDEO -> R.drawable.ic_video
        FileCat.AUDIO -> R.drawable.ic_audio
        FileCat.ARCHIVE -> R.drawable.ic_archive
        FileCat.PDF -> R.drawable.ic_pdf
        FileCat.DOC -> R.drawable.ic_doc
        FileCat.TEXT -> R.drawable.ic_text_file
        FileCat.APK -> R.drawable.ic_apk
        FileCat.GENERIC -> R.drawable.ic_generic_file
    }

    @ColorRes
    fun color(cat: FileCat): Int = when (cat) {
        FileCat.FOLDER -> R.color.file_folder
        FileCat.IMAGE -> R.color.file_image
        FileCat.VIDEO -> R.color.file_video
        FileCat.AUDIO -> R.color.file_audio
        FileCat.ARCHIVE -> R.color.file_archive
        FileCat.PDF -> R.color.file_pdf
        FileCat.DOC -> R.color.file_doc
        FileCat.TEXT -> R.color.file_text
        FileCat.APK -> R.color.file_apk
        FileCat.GENERIC -> R.color.file_generic
    }

    /** Short human label of a category, used in list subtext. */
    fun label(cat: FileCat): String = when (cat) {
        FileCat.FOLDER -> "Folder"
        FileCat.IMAGE -> "Image"
        FileCat.VIDEO -> "Video"
        FileCat.AUDIO -> "Audio"
        FileCat.ARCHIVE -> "Archive"
        FileCat.PDF -> "PDF"
        FileCat.DOC -> "Document"
        FileCat.TEXT -> "Text"
        FileCat.APK -> "App"
        FileCat.GENERIC -> "File"
    }
}
