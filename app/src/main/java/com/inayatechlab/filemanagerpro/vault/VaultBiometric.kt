package com.inayatechlab.filemanagerpro.vault

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Optional biometric unlock: the vault password is wrapped with an AES key
 * held in the Android Keystore that only releases after a successful
 * biometric authentication (BiometricPrompt CryptoObject).
 *
 * The wrapped blob is stored opaquely in `vault.conf` (bio field) as
 * base64(iv|ct). Keys are invalidated when new biometrics are enrolled, so a
 * stale blob simply falls back to password unlock.
 */
object VaultBiometric {

    private const val PROVIDER = "AndroidKeyStore"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128

    fun aliasFor(vaultName: String): String {
        val clean = vaultName.lowercase().replace(Regex("[^a-z0-9_.-]"), "_").take(48)
        return "fmp_vault_bio_$clean"
    }

    fun canAuthenticate(context: Context): Boolean {
        val result = BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun hasKey(alias: String): Boolean = runCatching {
        val ks = KeyStore.getInstance(PROVIDER).apply { load(null) }
        ks.containsAlias(alias)
    }.getOrDefault(false)

    /** Creates (or resets) the biometric-gated key for [alias]. */
    fun createKey(alias: String) {
        deleteKey(alias)
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .build()
        generator.init(spec)
        generator.generateKey()
    }

    fun deleteKey(alias: String) {
        runCatching {
            val ks = KeyStore.getInstance(PROVIDER).apply { load(null) }
            if (ks.containsAlias(alias)) ks.deleteEntry(alias)
        }
    }

    /** Encrypts the vault password with the biometric-gated key. */
    fun wrapPassword(alias: String, password: String): String {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, loadKey(alias))
        val ct = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "." +
            Base64.encodeToString(ct, Base64.NO_WRAP)
    }

    /**
     * A decrypt cipher ready to be handed to BiometricPrompt as CryptoObject.
     * Throws [KeyPermanentlyInvalidatedException] when the enrolled
     * biometrics changed since the key was created.
     */
    fun newDecryptCipher(alias: String, blob: String): Cipher {
        val dot = blob.indexOf('.')
        require(dot > 0) { "bad biometric blob" }
        val iv = Base64.decode(blob.substring(0, dot), Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(
            Cipher.DECRYPT_MODE,
            loadKey(alias),
            GCMParameterSpec(TAG_BITS, iv)
        )
        return cipher
    }

    /** Finishes decryption with the authenticated cipher (from the prompt). */
    fun unwrapPassword(cipher: Cipher, blob: String): String {
        val dot = blob.indexOf('.')
        val ct = Base64.decode(blob.substring(dot + 1), Base64.NO_WRAP)
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }

    private fun loadKey(alias: String): SecretKey {
        val ks = KeyStore.getInstance(PROVIDER).apply { load(null) }
        return ks.getKey(alias, null) as SecretKey
    }
}
