package com.inayatechlab.filemanagerpro.util

import android.content.Context

/**
 * Central access to user preferences for File Manager Pro.
 * Single source of truth for every persisted setting (key set is stable).
 */
object SettingsStore {

    private const val PREFS = "fm_settings"

    // Sort modes — keep numeric values stable (persisted).
    const val SORT_NAME = 0
    const val SORT_DATE = 1
    const val SORT_SIZE = 2
    const val SORT_TYPE = 3

    fun sortMode(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt("sort_mode", SORT_NAME)

    fun sortAscending(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("sort_asc", true)

    fun gridView(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("grid_view", false)

    fun showHiddenFiles(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("show_hidden", false)

    fun confirmDelete(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("confirm_delete", true)

    fun setSortMode(context: Context, mode: Int) =
        edit(context).putInt("sort_mode", mode).apply()

    fun setSortAscending(context: Context, asc: Boolean) =
        edit(context).putBoolean("sort_asc", asc).apply()

    fun setGridView(context: Context, grid: Boolean) =
        edit(context).putBoolean("grid_view", grid).apply()

    fun setShowHiddenFiles(context: Context, show: Boolean) =
        edit(context).putBoolean("show_hidden", show).apply()

    fun setConfirmDelete(context: Context, confirm: Boolean) =
        edit(context).putBoolean("confirm_delete", confirm).apply()

    private fun edit(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
}
