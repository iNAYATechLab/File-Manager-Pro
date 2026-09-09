package com.inayatechlab.filemanagerpro.vault

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Failure categories surfaced to the UI. */
class VaultCryptoException(val reason: VaultCryptoException.Reason, message: String) : Exception(message) {
    enum class Reason { WRONG_PASSWORD, CORRUPT, TOO_LARGE, IO }
}

/**
 * Low-level cryptography for the .fmpvault vaults: PBKDF2 key derivation and
 * AES-256-GCM authenticated encryption. Pure JVM — no Android dependencies —
 * so the format is unit-testable and stable across platforms.
 *
 * Sealed blobs are self-describing: `nonce(12) || ciphertext` with the 16-byte
 * GCM tag appended by the cipher. Authentication failures surface as
 * [VaultCryptoException.Reason.CORRUPT] (wrong password yields the same tag
 * mismatch and is reported by the caller context).
 */
object VaultCrypto {

    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val PBKDF2_ALGO = "PBKDF2WithHmacSHA256"
    private const val NONCE_BYTES = 12
    private const val KEY_BITS = 256

    /** OWASP-recommended floor for PBKDF2-HMAC-SHA256 (device adjusted). */
    const val DEFAULT_ITERATIONS = 150_000
    const val SALT_BYTES = 16

    /** Vault entries larger than this are refused (whole-file encryption). */
    const val MAX_FILE_BYTES = 128L * 1024 * 1024

    private val random = SecureRandom()

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { random.nextBytes(it) }

    fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKey {
        if (password.isEmpty()) throw VaultCryptoException(VaultCryptoException.Reason.WRONG_PASSWORD, "empty password")
        val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGO)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    /** Seals [plain] into `nonce || ciphertext`. */
    fun seal(key: SecretKey, plain: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
        val ct = cipher.doFinal(plain)
        return nonce + ct
    }

    /** Opens a blob produced by [seal]; throws on tampering or wrong key. */
    fun open(key: SecretKey, sealed: ByteArray): ByteArray {
        if (sealed.size < NONCE_BYTES + 16) {
            throw VaultCryptoException(VaultCryptoException.Reason.CORRUPT, "blob too short")
        }
        val nonce = sealed.copyOfRange(0, NONCE_BYTES)
        val ct = sealed.copyOfRange(NONCE_BYTES, sealed.size)
        return try {
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))
            cipher.doFinal(ct)
        } catch (e: AEADBadTagException) {
            throw VaultCryptoException(VaultCryptoException.Reason.CORRUPT, "authentication failed")
        } catch (e: javax.crypto.BadPaddingException) {
            throw VaultCryptoException(VaultCryptoException.Reason.CORRUPT, "authentication failed")
        } catch (e: Exception) {
            if (e is VaultCryptoException) throw e
            throw VaultCryptoException(VaultCryptoException.Reason.IO, e.message ?: "crypto error")
        }
    }

    /** Encrypts a whole file (bounded by [MAX_FILE_BYTES]) into [dst]. */
    fun sealFileTo(key: SecretKey, src: File, dst: File) {
        if (!src.isFile) throw VaultCryptoException(VaultCryptoException.Reason.IO, "not a file: ${src.path}")
        if (src.length() > MAX_FILE_BYTES) {
            throw VaultCryptoException(
                VaultCryptoException.Reason.TOO_LARGE,
                "file too large for the vault: ${src.name}"
            )
        }
        val bytes = try {
            FileInputStream(src).use { it.readBytes() }
        } catch (e: Exception) {
            throw VaultCryptoException(VaultCryptoException.Reason.IO, e.message ?: "read failed")
        }
        val sealed = seal(key, bytes)
        try {
            FileOutputStream(dst).use { it.write(sealed) }
        } catch (e: Exception) {
            throw VaultCryptoException(VaultCryptoException.Reason.IO, e.message ?: "write failed")
        }
    }

    /** Decrypts a vault blob file into [dst]. */
    fun openFileTo(key: SecretKey, src: File, dst: File) {
        val bytes = try {
            FileInputStream(src).use { it.readBytes() }
        } catch (e: Exception) {
            throw VaultCryptoException(VaultCryptoException.Reason.IO, e.message ?: "read failed")
        }
        val plain = open(key, bytes)
        dst.parentFile?.mkdirs()
        try {
            FileOutputStream(dst).use { it.write(plain) }
        } catch (e: Exception) {
            throw VaultCryptoException(VaultCryptoException.Reason.IO, e.message ?: "write failed")
        }
    }
}
