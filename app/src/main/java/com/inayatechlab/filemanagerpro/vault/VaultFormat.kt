package com.inayatechlab.filemanagerpro.vault

import java.io.File

/**
 * On-disk structure of a vault. A vault is a folder named `<Name>.fmpvault`
 * containing:
 *
 *  - `vault.conf`   — plaintext metadata: KDF parameters (salt + iterations),
 *                     creation time and an opaque biometric blob (base64) when
 *                     biometric unlock is enabled. Contains no secrets.
 *  - `manifest.vlt` — the encrypted entry index: `nonce || ciphertext` of the
 *                     plaintext line list described in [Manifest].
 *  - `blobs/`       — one encrypted blob per stored file, named by random id
 *                     (`nonce || ciphertext`, self-contained).
 *
 * File names inside the vault never reveal their plaintext counterpart.
 */
object VaultFormat {

    const val EXTENSION = ".fmpvault"
    const val CONF_NAME = "vault.conf"
    const val MANIFEST_NAME = "manifest.vlt"
    const val BLOBS_DIR = "blobs"

    const val FORMAT_VERSION = 1
    const val MANIFEST_MAGIC = "FMPV1"

    /** A stored vault entry (plaintext side of the manifest). */
    data class VaultEntry(
        /** Path inside the vault, e.g. "Docs/report.pdf". */
        val relPath: String,
        /** Random blob id (file name inside blobs/). */
        val blob: String,
        /** Plaintext size in bytes. */
        val size: Long,
        /** Source file modification time (ms). */
        val mtimeMs: Long,
        /** Base64 of the absolute source directory the file was vaulted from (may be empty). */
        val originDirB64: String
    ) {
        val name: String get() = relPath.substringAfterLast('/')

        val originDir: String?
            get() = if (originDirB64.isEmpty()) null
            else String(java.util.Base64.getDecoder().decode(originDirB64), Charsets.UTF_8)
    }

    /** Plaintext `vault.conf` content. */
    data class VaultConf(
        val format: Int,
        val iterations: Int,
        val saltB64: String,
        val createdMs: Long,
        val bioB64: String? = null
    ) {
        fun isVault(): Boolean = format == FORMAT_VERSION && saltB64.isNotEmpty() && iterations > 0
    }

    // ------------------------------------------------------------ conf codec

    fun readConf(dir: File): VaultConf? {
        val f = File(dir, CONF_NAME)
        if (!f.isFile) return null
        val text = try {
            f.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            return null
        }
        var format = 0
        var iterations = 0
        var salt = ""
        var created = 0L
        var bio: String? = null
        for (line in text.lineSequence()) {
            val idx = line.indexOf('=')
            if (idx <= 0) continue
            val k = line.substring(0, idx).trim()
            val v = line.substring(idx + 1).trim()
            when (k) {
                "format" -> format = v.toIntOrNull() ?: 0
                "iterations" -> iterations = v.toIntOrNull() ?: 0
                "salt" -> salt = v
                "created" -> created = v.toLongOrNull() ?: 0L
                "bio" -> if (v.isNotEmpty()) bio = v
            }
        }
        val conf = VaultConf(format, iterations, salt, created, bio)
        return conf.takeIf { it.isVault() }
    }

    fun writeConf(dir: File, conf: VaultConf) {
        val sb = StringBuilder()
        sb.append("format=").append(conf.format).append('\n')
        sb.append("iterations=").append(conf.iterations).append('\n')
        sb.append("salt=").append(conf.saltB64).append('\n')
        sb.append("created=").append(conf.createdMs).append('\n')
        if (!conf.bioB64.isNullOrEmpty()) sb.append("bio=").append(conf.bioB64).append('\n')
        File(dir, CONF_NAME).writeText(sb.toString(), Charsets.UTF_8)
    }

    // --------------------------------------------------------- manifest codec

    private const val SEP = "\t"

    /** Serialises the entry index into the plaintext that gets encrypted. */
    fun encodeManifest(entries: List<VaultEntry>): ByteArray {
        val sb = StringBuilder()
        sb.append(MANIFEST_MAGIC).append('\n')
        for (e in entries) {
            sb.append('E').append(SEP)
                .append(e.relPath).append(SEP)
                .append(e.blob).append(SEP)
                .append(e.size).append(SEP)
                .append(e.mtimeMs).append(SEP)
                .append(e.originDirB64).append('\n')
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    /** Parses the decrypted manifest payload. */
    fun decodeManifest(plain: ByteArray): List<VaultEntry> {
        val out = mutableListOf<VaultEntry>()
        val text = String(plain, Charsets.UTF_8)
        val lines = text.lineSequence()
        var first = true
        for (line in lines) {
            if (first) {
                first = false
                if (line != MANIFEST_MAGIC) {
                    throw VaultCryptoException(VaultCryptoException.Reason.CORRUPT, "bad manifest header")
                }
                continue
            }
            if (line.isBlank()) continue
            val parts = line.split(SEP)
            if (parts.size != 6 || parts[0] != "E") {
                throw VaultCryptoException(VaultCryptoException.Reason.CORRUPT, "bad manifest line")
            }
            out.add(
                VaultEntry(
                    relPath = parts[1],
                    blob = parts[2],
                    size = parts[3].toLongOrNull() ?: 0L,
                    mtimeMs = parts[4].toLongOrNull() ?: 0L,
                    originDirB64 = parts[5]
                )
            )
        }
        return out
    }

    // ------------------------------------------------------------ discovery

    fun isVaultDir(dir: File): Boolean = dir.isDirectory && readConf(dir) != null

    /** Finds `<Name>.fmpvault` folders inside [parent]. */
    fun findVaults(parent: File): List<File> {
        if (!parent.isDirectory) return emptyList()
        return parent.listFiles()
            ?.filter { it.isDirectory && it.name.endsWith(EXTENSION) && isVaultDir(it) }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()
    }

    /** Display name: folder name without the extension. */
    fun displayName(vaultDir: File): String =
        vaultDir.name.removeSuffix(EXTENSION)
}
