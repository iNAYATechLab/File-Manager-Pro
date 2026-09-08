package com.inayatechlab.filemanagerpro.util

import android.content.Context
import com.inayatechlab.filemanagerpro.R

/** Relative-time formatting ("Just now", "5 min ago", "3 d ago", …). */
object RelativeTime {

    private const val DAY = 24 * 60 * 60 * 1000L

    fun format(context: Context, millis: Long, now: Long = System.currentTimeMillis()): String {
        val diff = now - millis
        return when {
            diff < 60_000L -> context.getString(R.string.time_just_now)
            diff < 60 * 60_000L -> context.getString(R.string.time_min_ago, diff / 60_000L)
            diff < DAY -> context.getString(R.string.time_h_ago, diff / 3_600_000L)
            diff < 7 * DAY -> context.getString(R.string.time_d_ago, diff / DAY)
            else -> context.getString(R.string.time_w_ago, diff / (7 * DAY))
        }
    }
}
