package com.inayatechlab.filemanagerpro.util

import com.inayatechlab.filemanagerpro.model.FileEntry

/** High level category of a file entry, used for icons, colors and filters. */
enum class FileCat {
    FOLDER, IMAGE, VIDEO, AUDIO, ARCHIVE, PDF, DOC, TEXT, APK, GENERIC;

    companion object {
        private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")
        private val VIDEO_EXT = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "flv", "wmv", "m4v", "ts")
        private val AUDIO_EXT = setOf("mp3", "wav", "ogg", "aac", "flac", "m4a", "opus", "mid", "midi", "wma", "amr")
        private val ARCHIVE_EXT = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "zst", "jar", "cbz", "cbr", "tgz")
        private val DOC_EXT = setOf(
            "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp",
            "rtf", "pages", "numbers", "key", "csv", "ics", "vcf"
        )
        private val TEXT_EXT = setOf(
            "txt", "md", "json", "xml", "html", "htm", "css", "js", "kt", "kts", "java",
            "py", "c", "cpp", "h", "sh", "log", "ini", "cfg", "yaml", "yml", "sql", "bat", "gradle", "properties"
        )

        fun of(entry: FileEntry): FileCat = when {
            entry.isDir -> FOLDER
            entry.extension in IMAGE_EXT -> IMAGE
            entry.extension in VIDEO_EXT -> VIDEO
            entry.extension in AUDIO_EXT -> AUDIO
            entry.extension == "apk" -> APK
            entry.extension == "pdf" -> PDF
            entry.extension in ARCHIVE_EXT -> ARCHIVE
            entry.extension in DOC_EXT -> DOC
            entry.extension in TEXT_EXT -> TEXT
            else -> GENERIC
        }

        fun ofExtension(ext: String): FileCat? = when (ext.lowercase()) {
            in IMAGE_EXT -> IMAGE
            in VIDEO_EXT -> VIDEO
            in AUDIO_EXT -> AUDIO
            "apk" -> APK
            "pdf" -> PDF
            in ARCHIVE_EXT -> ARCHIVE
            in DOC_EXT -> DOC
            in TEXT_EXT -> TEXT
            else -> null
        }
    }
}

/** File categories that appear on the Categories tab. */
enum class MediaCat(val titleRes: Int, val filter: FileCat?) {
    IMAGES(com.inayatechlab.filemanagerpro.R.string.cat_images, FileCat.IMAGE),
    VIDEOS(com.inayatechlab.filemanagerpro.R.string.cat_videos, FileCat.VIDEO),
    AUDIO(com.inayatechlab.filemanagerpro.R.string.cat_audio, FileCat.AUDIO),
    DOCUMENTS(com.inayatechlab.filemanagerpro.R.string.cat_documents, FileCat.DOC),
    ARCHIVES(com.inayatechlab.filemanagerpro.R.string.cat_archives, FileCat.ARCHIVE),
    APPS(com.inayatechlab.filemanagerpro.R.string.cat_apk, FileCat.APK)
}
