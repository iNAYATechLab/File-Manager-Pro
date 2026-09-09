package com.inayatechlab.filemanagerpro.saf

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri

/**
 * Persisted registry of user-granted SAF tree locations. The content URIs are
 * usable across app restarts because the activity takes persistable URI
 * permissions at grant time.
 */
object SafGrants {

    private const val PREFS = "saf_grants"
    private const val KEY_URIS = "uris"
    private const val LABEL_PREFIX = "label#"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** All granted tree URIs, sorted by label for a stable UI order. */
    fun list(context: Context): List<Uri> {
        val p = prefs(context)
        val raw = p.getStringSet(KEY_URIS, emptySet()) ?: emptySet()
        return raw
            .mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
            .sortedBy { labelFor(context, it)?.lowercase() ?: it.toString() }
    }

    fun labelFor(context: Context, uri: Uri): String? =
        prefs(context).getString(LABEL_PREFIX + uri.toString(), null)

    /** Remembers a grant; [label] is what the overview row displays. */
    fun remember(context: Context, uri: Uri, label: String) {
        val p = prefs(context)
        val set = (p.getStringSet(KEY_URIS, emptySet()) ?: emptySet()).toMutableSet()
        set.add(uri.toString())
        p.edit()
            .putStringSet(KEY_URIS, set)
            .putString(LABEL_PREFIX + uri.toString(), SafText.sanitizeLabel(label))
            .apply()
    }

    /** Removes a grant (caller should also release the URI permission). */
    fun forget(context: Context, uri: Uri) {
        val p = prefs(context)
        val set = (p.getStringSet(KEY_URIS, emptySet()) ?: emptySet()).toMutableSet()
        set.remove(uri.toString())
        p.edit()
            .putStringSet(KEY_URIS, set)
            .remove(LABEL_PREFIX + uri.toString())
            .apply()
    }
}
