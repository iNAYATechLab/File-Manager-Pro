package com.inayatechlab.filemanagerpro.saf

import android.net.Uri
import android.provider.DocumentsContract

/**
 * A document inside a granted SAF tree. [treeUri] anchors every operation;
 * [docId] is the provider-relative id of this document.
 */
data class SafEntry(
    val treeUri: Uri,
    val docId: String,
    val name: String,
    val isDir: Boolean,
    val mime: String,
    val size: Long,
    val lastModified: Long
) {
    /** Content URI of this document usable with the ContentResolver. */
    val uri: Uri
        get() = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

    /** "folder" marker mime used for directory creation. */
    companion object {
        const val MIME_DIR = DocumentsContract.Document.MIME_TYPE_DIR
    }
}
