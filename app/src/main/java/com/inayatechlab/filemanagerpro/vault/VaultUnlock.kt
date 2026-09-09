package com.inayatechlab.filemanagerpro.vault

import android.content.Context
import android.text.InputType
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.inayatechlab.filemanagerpro.R
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shared unlock UI used by the Vault tab and by "move to vault" from Storage:
 *
 *  1. password dialog (always available), or
 *  2. biometric prompt when the vault has biometric unlock enabled and the
 *     device can authenticate (the Keystore only releases the wrapped
 *     password after a successful prompt).
 *
 * A successful unlock opens [VaultSessionManager] and calls [onUnlocked].
 */
object VaultUnlock {

    /** Shows the right unlock dialog for [vaultDir] and reports back. */
    fun prompt(
        activity: AppCompatActivity,
        scope: CoroutineScope,
        vaultDir: File,
        onUnlocked: (VaultEngine.Session) -> Unit,
        onDismissed: () -> Unit = {}
    ) {
        val conf = VaultEngine.metaOf(vaultDir) // cheap conf read, no PBKDF
        if (conf == null) {
            onDismissed()
            return
        }
        val bioReady = conf.bioB64 != null &&
            VaultBiometric.canAuthenticate(activity) &&
            VaultBiometric.hasKey(VaultBiometric.aliasFor(VaultFormat.displayName(vaultDir)))
        if (bioReady) {
            val options = arrayOf(
                activity.getString(R.string.vlt_unlock_biometric),
                activity.getString(R.string.vlt_unlock_password)
            )
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.vlt_unlock_title)
                .setItems(options) { _, which ->
                    if (which == 0) promptBiometric(activity, vaultDir, onUnlocked, onDismissed)
                    else promptPassword(activity, scope, vaultDir, onUnlocked)
                }
                .setOnCancelListener { onDismissed() }
                .show()
        } else {
            if (conf.bioB64 != null && !VaultBiometric.canAuthenticate(activity)) {
                snack(activity, activity.getString(R.string.vlt_bio_unavailable))
            }
            promptPassword(activity, scope, vaultDir, onUnlocked)
        }
    }

    /**
     * Generic password prompt (used by biometric setup, where the password is
     * needed once to wrap it with the Keystore key).
     */
    fun promptForPassword(
        activity: AppCompatActivity,
        title: String,
        message: String? = null,
        onSubmit: (String) -> Unit,
        onCancel: () -> Unit = {}
    ) {
        val input = passwordInput(activity)
        val wrap = LinearLayout(activity).apply {
            setPadding(64, 8, 64, 0)
            addView(input)
        }
        val builder = MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setView(wrap)
            .setNegativeButton(R.string.action_cancel) { _, _ -> onCancel() }
            .setPositiveButton(R.string.action_ok, null)
        if (message != null) builder.setMessage(message)
        builder.show().getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener {
                val pw = input.text?.toString().orEmpty()
                if (pw.length < 4) {
                    input.error = activity.getString(R.string.vlt_error_short_pw)
                } else {
                    onSubmit(pw)
                }
            }
    }

    private fun promptPassword(
        activity: AppCompatActivity,
        scope: CoroutineScope,
        vaultDir: File,
        onUnlocked: (VaultEngine.Session) -> Unit
    ) {
        val input = passwordInput(activity)
        val wrap = LinearLayout(activity).apply {
            setPadding(64, 8, 64, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.vlt_unlock_title)
            .setMessage(VaultFormat.displayName(vaultDir))
            .setView(wrap)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                unlockAsync(activity, scope, vaultDir, input.text?.toString().orEmpty(), onUnlocked)
            }
            .show()
    }

    private fun promptBiometric(
        activity: AppCompatActivity,
        vaultDir: File,
        onUnlocked: (VaultEngine.Session) -> Unit,
        onDismissed: () -> Unit
    ) {
        val conf = VaultEngine.metaOf(vaultDir) ?: return onDismissed()
        val alias = VaultBiometric.aliasFor(VaultFormat.displayName(vaultDir))
        val blob = conf.bioB64 ?: return onDismissed()
        val cipher = try {
            VaultBiometric.newDecryptCipher(alias, blob)
        } catch (e: Exception) {
            // Key invalidated by a new biometric enrollment — force password.
            snack(activity, activity.getString(R.string.vlt_bio_unavailable))
            return promptPassword(activity, activity.lifecycleScope, vaultDir, onUnlocked)
        }
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    val password = try {
                        VaultBiometric.unwrapPassword(result.cryptoObject?.cipher ?: return, blob)
                    } catch (e: Exception) {
                        snack(activity, activity.getString(R.string.vlt_wrong_password))
                        return
                    }
                    unlockAsync(activity, activity.lifecycleScope, vaultDir, password, onUnlocked)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onDismissed()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.vlt_unlock_biometric))
            .setSubtitle(VaultFormat.displayName(vaultDir))
            .setNegativeButtonText(activity.getString(R.string.action_cancel))
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    private fun unlockAsync(
        activity: AppCompatActivity,
        scope: CoroutineScope,
        vaultDir: File,
        password: String,
        onUnlocked: (VaultEngine.Session) -> Unit
    ) {
        scope.launch {
            try {
                val session = withContext(Dispatchers.IO) { VaultEngine.unlock(vaultDir, password) }
                VaultSessionManager.open(session)
                onUnlocked(session)
            } catch (e: VaultCryptoException) {
                snack(activity, activity.getString(R.string.vlt_wrong_password))
            } catch (e: Exception) {
                snack(activity, activity.getString(R.string.error))
            }
        }
    }

    private fun passwordInput(context: Context) =
        com.google.android.material.textfield.TextInputEditText(context).apply {
            hint = context.getString(R.string.vlt_hint_password)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
        }

    private fun snack(activity: AppCompatActivity, text: String) {
        activity.findViewById<android.view.View>(android.R.id.content)?.let {
            Snackbar.make(it, text, Snackbar.LENGTH_LONG).show()
        }
    }
}
