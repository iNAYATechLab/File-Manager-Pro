package com.inayatechlab.filemanagerpro.vault

import java.io.File
import java.util.Base64
import java.util.UUID
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * File-level operations of a vault. The engine is pure JVM (android-free);
 * everything runs on [Dispatchers.IO]. A [Session] keeps the derived key and
 * the decrypted entry index in memory until [Session.close] is called.
 */
object VaultEngine {

    class Session internal constructor(
        val dir: File,
        internal var conf: VaultFormat.VaultConf,
        internal var key: javax.crypto.SecretKey,
        private var entries: MutableList<VaultFormat.VaultEntry>
    ) : AutoCloseable {

        val name: String get() = VaultFormat.displayName(dir)
        val createdMs: Long get() = conf.createdMs
        val hasBiometric: Boolean get() = !conf.bioB64.isNullOrEmpty()

        val entriesSnapshot: List<VaultFormat.VaultEntry> get() = entries.toList()

        /** Re-reads vault.conf (e.g. after toggling biometric unlock). */
        fun refreshMeta() {
            VaultFormat.readConf(dir)?.let { conf = it }
        }

        fun findEntry(relPath: String): VaultFormat.VaultEntry? = entries.firstOrNull { it.relPath == relPath }

        internal fun mutate(block: (MutableList<VaultFormat.VaultEntry>) -> Unit) {
            block(entries)
            persistManifest()
        }

        internal fun persistManifest() {
            val plain = VaultFormat.encodeManifest(entries)
            val sealed = VaultCrypto.seal(key, plain)
            File(dir, VaultFormat.MANIFEST_NAME).let { mf ->
                mf.parentFile?.mkdirs()
                mf.writeBytes(sealed)
            }
        }

        /** Wipes key material + entry index from memory. */
        override fun close() {
            key = SecretKeySpec(ByteArray(32), "AES")
            entries = mutableListOf()
        }
    }

    /** Outcome of adding files to a vault. */
    data class AddOutcome(
        val added: Int,
        val bytes: Long,
        val errors: List<String>,
        /** Relative paths that were successfully encrypted (safe to delete originals of). */
        val addedRelPaths: List<String>
    ) {
        val ok: Boolean get() = errors.isEmpty()
    }

    /** One source to encrypt into the vault. */
    data class AddItem(val relPath: String, val source: File)

    /** Outcome of restoring files from a vault. */
    data class RestoreOutcome(val restored: List<File>, val errors: List<String>)

    // ------------------------------------------------------------ lifecycle

    /**
     * Creates a vault at [dir] (must not exist yet). [bioB64] is an optional
     * opaque blob (biometric-wrapped password) stored in the config.
     * Returns the freshly unlocked session.
     */
    suspend fun create(
        dir: File,
        password: String,
        iterations: Int = VaultCrypto.DEFAULT_ITERATIONS,
        bioB64: String? = null
    ): Session = withContext(Dispatchers.IO) {
        if (dir.exists()) throw VaultCryptoException(VaultCryptoException.Reason.IO, "vault already exists")
        if (password.length < 4) {
            throw VaultCryptoException(VaultCryptoException.Reason.WRONG_PASSWORD, "password too short")
        }
        val parent = dir.parentFile ?: throw VaultCryptoException(VaultCryptoException.Reason.IO, "no parent")
        if (!parent.exists() && !parent.mkdirs() || !parent.isDirectory) {
            throw VaultCryptoException(VaultCryptoException.Reason.IO, "cannot create vault location")
        }
        if (!dir.mkdir()) throw VaultCryptoException(VaultCryptoException.Reason.IO, "cannot create vault folder")
        File(dir, VaultFormat.BLOBS_DIR).mkdirs()

        val salt = VaultCrypto.newSalt()
        val conf = VaultFormat.VaultConf(
            format = VaultFormat.FORMAT_VERSION,
            iterations = iterations,
            saltB64 = Base64.getEncoder().encodeToString(salt),
            createdMs = System.currentTimeMillis(),
            bioB64 = bioB64
        )
        VaultFormat.writeConf(dir, conf)

        val key = VaultCrypto.deriveKey(password, salt, iterations)
        val session = Session(dir, conf, key, mutableListOf())
        session.persistManifest()
        session
    }

    /** Reads metadata of an existing vault without a password. */
    fun metaOf(dir: File): VaultFormat.VaultConf? = VaultFormat.readConf(dir)

    /**
     * Unlocks the vault. Wrong password and corrupt vault both surface as
     * [VaultCryptoException.Reason.CORRUPT] — [WRONG_PASSWORD] is used when the
     * caller knows the vault is intact (manifest parsing succeeded before).
     */
    suspend fun unlock(dir: File, password: String): Session = withContext(Dispatchers.IO) {
        val conf = VaultFormat.readConf(dir)
            ?: throw VaultCryptoException(VaultCryptoException.Reason.IO, "not a vault")
        val salt = Base64.getDecoder().decode(conf.saltB64)
        val key = VaultCrypto.deriveKey(password, salt, conf.iterations)
        val entries = loadEntries(key, dir)
        Session(dir, conf, key, entries.toMutableList())
    }

    private fun loadEntries(key: javax.crypto.SecretKey, dir: File): List<VaultFormat.VaultEntry> {
        val mf = File(dir, VaultFormat.MANIFEST_NAME)
        if (!mf.isFile) throw VaultCryptoException(VaultCryptoException.Reason.CORRUPT, "manifest missing")
        val sealed = mf.readBytes()
        val plain = try {
            VaultCrypto.open(key, sealed)
        } catch (e: VaultCryptoException) {
            throw VaultCryptoException(VaultCryptoException.Reason.CORRUPT, "wrong password or damaged vault")
        }
        return VaultFormat.decodeManifest(plain)
    }

    /**
     * Replaces the opaque biometric blob in vault.conf (null clears it).
     * The blob itself is produced/consumed by the Android Keystore layer.
     */
    suspend fun setBiometricBlob(dir: File, bioB64: String?) = withContext(Dispatchers.IO) {
        val conf = VaultFormat.readConf(dir) ?: return@withContext
        VaultFormat.writeConf(dir, conf.copy(bioB64 = bioB64))
    }

    /** Default parent folder for vaults on a storage root (".../Vaults"). */
    fun defaultParent(root: File): File = File(root, "Vaults")

    /** Discovers all vault folders inside the default Vaults parents of [roots]. */
    fun discoverVaults(roots: List<File>): List<File> =
        roots.map { defaultParent(it) }
            .flatMap { VaultFormat.findVaults(it) }
            .distinctBy { it.canonicalPath }

    /** Total size of encrypted blobs (no password needed). */
    suspend fun vaultSize(dir: File): Long = withContext(Dispatchers.IO) {
        val blobs = File(dir, VaultFormat.BLOBS_DIR)
        blobs.listFiles()?.sumOf { it.length() } ?: 0L
    }

    suspend fun deleteVault(dir: File): Boolean = withContext(Dispatchers.IO) {
        if (!dir.exists()) return@withContext true
        val deleted = dir.walkBottomUp().map { it.delete() }.all { it }
        deleted
    }

    // ------------------------------------------------------------ entries

    /**
     * Encrypts [items] into the vault and records them in the manifest.
     * Per-item failures are collected (a failed item leaves no partial blob).
     */
    suspend fun addFiles(
        session: Session,
        items: List<AddItem>,
        onProgress: (done: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ): AddOutcome = withContext(Dispatchers.IO) {
        val blobsDir = File(session.dir, VaultFormat.BLOBS_DIR)
        blobsDir.mkdirs()
        var added = 0
        var bytes = 0L
        val errors = mutableListOf<String>()
        val addedRelPaths = mutableListOf<String>()
        val newEntries = mutableListOf<VaultFormat.VaultEntry>()
        val createdBlobs = mutableListOf<File>()
        val total = items.size
        var done = 0
        try {
            for (item in items) {
                onProgress(done, total, item.relPath)
                try {
                    if (session.findEntry(item.relPath) != null) {
                        errors.add("already in vault: ${item.relPath}")
                        continue
                    }
                    if (!item.source.isFile) {
                        errors.add("not a file: ${item.relPath}")
                        continue
                    }
                    val blobName = UUID.randomUUID().toString()
                    val blob = File(blobsDir, blobName)
                    VaultCrypto.sealFileTo(session.key, item.source, blob)
                    val origin = item.source.parentFile?.absolutePath.orEmpty()
                    newEntries.add(
                        VaultFormat.VaultEntry(
                            relPath = item.relPath,
                            blob = blobName,
                            size = item.source.length(),
                            mtimeMs = item.source.lastModified(),
                            originDirB64 = if (origin.isEmpty()) "" else
                                Base64.getEncoder().encodeToString(origin.toByteArray(Charsets.UTF_8))
                        )
                    )
                    createdBlobs.add(blob)
                    bytes += item.source.length()
                    added++
                    addedRelPaths.add(item.relPath)
                } catch (e: VaultCryptoException) {
                    errors.add("${item.relPath}: ${e.message ?: "encrypt failed"}")
                } catch (e: Exception) {
                    errors.add("${item.relPath}: ${e.message ?: "error"}")
                }
                done++
            }
            if (newEntries.isNotEmpty()) {
                session.mutate { list -> list.addAll(newEntries) }
            }
        } catch (e: Exception) {
            // Manifest update failed — roll the freshly sealed blobs back.
            createdBlobs.forEach { runCatching { it.delete() } }
            throw e
        }
        AddOutcome(added, bytes, errors, addedRelPaths)
    }

    /**
     * Restores [relPaths] (all entries when null) into their original folders
     * when those still exist, otherwise into [fallbackDir]. Name collisions
     * get a " (n)" suffix like regular file operations.
     */
    suspend fun restoreFiles(
        session: Session,
        relPaths: List<String>?,
        fallbackDir: File,
        onProgress: (done: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ): RestoreOutcome = withContext(Dispatchers.IO) {
        val selected = if (relPaths == null) session.entriesSnapshot
        else relPaths.mapNotNull { session.findEntry(it) }
        val total = selected.size
        var done = 0
        val restored = mutableListOf<File>()
        val errors = mutableListOf<String>()
        for (entry in selected) {
            onProgress(done, total, entry.relPath)
            try {
                val origin = entry.originDir?.let { File(it) }
                val targetDir = when {
                    origin != null && origin.isDirectory -> origin
                    else -> fallbackDir
                }
                if (!targetDir.isDirectory && !targetDir.mkdirs()) {
                    errors.add("${entry.relPath}: cannot create ${targetDir.path}")
                    continue
                }
                val target = uniqueTarget(targetDir, entry.name)
                val blob = File(File(session.dir, VaultFormat.BLOBS_DIR), entry.blob)
                VaultCrypto.openFileTo(session.key, blob, target)
                if (entry.mtimeMs > 0) target.setLastModified(entry.mtimeMs)
                restored.add(target)
            } catch (e: VaultCryptoException) {
                errors.add("${entry.relPath}: ${e.message ?: "restore failed"}")
            } catch (e: Exception) {
                errors.add("${entry.relPath}: ${e.message ?: "restore failed"}")
            }
            done++
        }
        RestoreOutcome(restored, errors)
    }

    /** Removes entries (blobs + manifest lines). */
    suspend fun removeFiles(session: Session, relPaths: List<String>): Int =
        withContext(Dispatchers.IO) {
            val blobsDir = File(session.dir, VaultFormat.BLOBS_DIR)
            val doomed = relPaths.mapNotNull { session.findEntry(it) }
            session.mutate { list ->
                list.removeAll { e -> e.relPath in relPaths.toSet() }
            }
            var removed = 0
            for (e in doomed) {
                val blob = File(blobsDir, e.blob)
                if (blob.delete()) removed++
            }
            removed
        }

    /** Decrypts one entry into [destDir] (used for temporary open). */
    suspend fun decryptTo(
        session: Session,
        relPath: String,
        destDir: File
    ): File? = withContext(Dispatchers.IO) {
        val entry = session.findEntry(relPath) ?: return@withContext null
        val blob = File(File(session.dir, VaultFormat.BLOBS_DIR), entry.blob)
        val out = File(destDir, blob.name + "_" + entry.name)
        VaultCrypto.openFileTo(session.key, blob, out)
        out
    }

    private fun uniqueTarget(dir: File, name: String): File {
        var target = File(dir, name)
        var i = 1
        while (target.exists()) {
            val dot = name.lastIndexOf('.')
            val candidate = if (dot > 0) {
                "${name.substring(0, dot)} ($i)${name.substring(dot)}"
            } else {
                "$name ($i)"
            }
            target = File(dir, candidate)
            i++
        }
        return target
    }
}
