package com.inayatechlab.filemanagerpro.saf

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * All SAF document operations. Everything runs on [Dispatchers.IO]; functions
 * return an error message (null = success) so the UI never needs exceptions
 * for expected failure paths.
 */
object SafOps {

    const val AUTHORITY_EXT_STORAGE = "com.android.externalstorage.documents"

    private val COLUMNS = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        DocumentsContract.Document.COLUMN_FLAGS
    )

    private val SORT: Comparator<SafEntry> = Comparator { a, b ->
        if (a.isDir != b.isDir) return@Comparator if (a.isDir) -1 else 1
        a.name.lowercase().compareTo(b.name.lowercase())
    }

    /** Children of [dirDocId] inside [treeUri]; null when the query fails. */
    suspend fun listChildren(
        context: Context,
        treeUri: Uri,
        dirDocId: String
    ): List<SafEntry>? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val query = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirDocId)
        val docs = mutableListOf<SafEntry>()
        try {
            resolver.query(
                query, COLUMNS, null, null, null,
                null
            )?.use { cursor ->
                val colId = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val colName = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val colMime = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val colSize = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val colModified = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(colId) ?: continue
                    val name = cursor.getString(colName) ?: continue
                    val mime = cursor.getString(colMime) ?: ""
                    val isDir = mime == SafEntry.MIME_DIR
                    val size = if (colSize >= 0) cursor.getLong(colSize) else 0L
                    val modified = if (colModified >= 0) cursor.getLong(colModified) else 0L
                    docs.add(
                        SafEntry(treeUri, docId, name, isDir, mime, size, modified)
                    )
                }
            }
        } catch (e: Exception) {
            return@withContext null
        }
        docs.sortWith(SORT)
        docs
    }

    /** Creates a directory under [parentDocId]; returns new docId or error. */
    suspend fun createFolder(
        context: Context,
        treeUri: Uri,
        parentDocId: String,
        desiredName: String
    ): Pair<String?, String?> = withContext(Dispatchers.IO) {
        val existing = existingNames(context, treeUri, parentDocId)
        val name = SafText.uniqueChildName(desiredName.trim(), existing)
        if (name.isEmpty()) return@withContext null to "empty name"
        try {
            val created = DocumentsContract.createDocument(
                context.contentResolver,
                DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId),
                SafEntry.MIME_DIR,
                name
            )
            if (created == null) null to "create failed"
            else DocumentsContract.getDocumentId(created) to null
        } catch (e: Exception) {
            null to (e.message ?: "create failed")
        }
    }

    /** Renames a document; returns null on success else an error message. */
    suspend fun rename(
        context: Context,
        entry: SafEntry,
        newName: String
    ): String? = withContext(Dispatchers.IO) {
        val name = newName.trim()
        if (name.isEmpty() || name.contains('/')) return@withContext "invalid name"
        try {
            val moved = DocumentsContract.renameDocument(
                context.contentResolver, entry.uri, name
            )
            if (moved == null) "rename failed" else null
        } catch (e: Exception) {
            e.message ?: "rename failed"
        }
    }

    /** Deletes documents (files or whole folders). Returns per-item errors. */
    suspend fun delete(
        context: Context,
        entries: List<SafEntry>,
        onProgress: (String) -> Unit = {}
    ): List<String> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val errors = mutableListOf<String>()
        for (entry in entries) {
            onProgress(entry.name)
            try {
                if (!DocumentsContract.deleteDocument(resolver, entry.uri)) {
                    errors.add("${entry.name}: delete refused")
                }
            } catch (e: Exception) {
                errors.add("${entry.name}: ${e.message ?: "delete failed"}")
            }
        }
        errors
    }

    /**
     * Copies [items] from [srcTree] into the root of [destTree], preserving
     * folder structure. Originals are never touched. Auto-uniquifies names.
     */
    suspend fun copyInto(
        context: Context,
        srcTree: Uri,
        items: List<SafEntry>,
        destTree: Uri,
        onProgress: (String) -> Unit = {}
    ): List<String> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val errors = mutableListOf<String>()
        val destRootId = runCatching {
            DocumentsContract.getTreeDocumentId(destTree)
        }.getOrNull() ?: return@withContext listOf("bad destination")
        val used = existingNames(context, destTree, destRootId).toMutableSet()
        for (item in items) {
            try {
                val targetName = SafText.uniqueChildName(item.name, used)
                used.add(targetName)
                onProgress(item.name)
                copyNode(
                    resolver, srcTree, item, destTree, destRootId, targetName, errors
                )
            } catch (e: Exception) {
                errors.add("${item.name}: ${e.message ?: "copy failed"}")
            }
        }
        errors
    }

    private suspend fun copyNode(
        resolver: ContentResolver,
        srcTree: Uri,
        src: SafEntry,
        destTree: Uri,
        destParentDocId: String,
        targetName: String,
        errors: MutableList<String>
    ) {
        val destParentUri = DocumentsContract.buildDocumentUriUsingTree(destTree, destParentDocId)
        val created = DocumentsContract.createDocument(
            resolver, destParentUri, src.mime, targetName
        ) ?: run {
            errors.add("${src.name}: could not create destination")
            return
        }
        if (src.isDir) {
            // recurse: children get copied into the newly created folder
            val destDirId = DocumentsContract.getDocumentId(created)
            val children = listChildrenSync(resolver, srcTree, src.docId)
            val childUsed = existingNames(resolver, destTree, destDirId).toMutableSet()
            for (child in children) {
                val childName = SafText.uniqueChildName(child.name, childUsed)
                childUsed.add(childName)
                copyNode(resolver, srcTree, child, destTree, destDirId, childName, errors)
            }
        } else {
            // stream the bytes
            resolver.openInputStream(src.uri)?.use { input ->
                resolver.openOutputStream(created, "w")?.use { output ->
                    input.copyTo(output, DEFAULT_BUFFER_SIZE)
                } ?: run { errors.add("${src.name}: cannot write") }
            } ?: run { errors.add("${src.name}: cannot read") }
        }
    }

    private fun listChildrenSync(
        resolver: ContentResolver,
        treeUri: Uri,
        dirDocId: String
    ): List<SafEntry> {
        val query = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirDocId)
        val out = mutableListOf<SafEntry>()
        try {
            resolver.query(query, COLUMNS, null, null, null, null)?.use { cursor ->
                val colId = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val colName = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val colMime = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val colSize = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val colModified = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(colId) ?: continue
                    val name = cursor.getString(colName) ?: continue
                    val mime = cursor.getString(colMime) ?: ""
                    out.add(
                        SafEntry(
                            treeUri, docId, name,
                            mime == SafEntry.MIME_DIR, mime,
                            if (colSize >= 0) cursor.getLong(colSize) else 0L,
                            if (colModified >= 0) cursor.getLong(colModified) else 0L
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // treat as empty — caller reports overall failure
        }
        return out
    }

    /** Names already present in a directory (for conflict-free creation). */
    private fun existingNames(
        context: Context,
        treeUri: Uri,
        dirDocId: String
    ): Set<String> {
        val names = linkedSetOf<String>()
        try {
            context.contentResolver.query(
                DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirDocId),
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null, null
            )?.use { cursor ->
                val colName = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    cursor.getString(colName)?.let { names.add(it) }
                }
            }
        } catch (e: Exception) {
            // no names known — createDocument may auto-rename instead
        }
        return names
    }

    private fun existingNames(
        resolver: ContentResolver,
        treeUri: Uri,
        dirDocId: String
    ): Set<String> {
        val names = linkedSetOf<String>()
        try {
            resolver.query(
                DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirDocId),
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null, null
            )?.use { cursor ->
                val colName = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    cursor.getString(colName)?.let { names.add(it) }
                }
            }
        } catch (e: Exception) {
        }
        return names
    }

    /**
     * Copies a SAF document into the app cache so classic File-based tools
     * (preview, share, open-with) can consume it. Returns the cache file.
     */
    suspend fun materialize(context: Context, entry: SafEntry): File? =
        withContext(Dispatchers.IO) {
            try {
                val cache = File(context.cacheDir, "saf_open").apply { mkdirs() }
                val existing = (cache.listFiles() ?: emptyArray()).map { it.name }.toSet()
                val target = File(cache, SafText.uniqueChildName(entry.name, existing))
                context.contentResolver.openInputStream(entry.uri)?.use { input ->
                    target.outputStream().use { output ->
                        input.copyTo(output, DEFAULT_BUFFER_SIZE)
                    }
                } ?: return@withContext null
                target
            } catch (e: Exception) {
                null
            }
        }

    /** Guess a mime type for names without one (e.g. cache copies). */
    fun guessMime(name: String): String {
        val ext = MimeTypeMap.getFileExtensionFromUrl(name).lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: "application/octet-stream"
    }
}
