package com.quickdelete.app

import android.content.Context
import java.util.Calendar

/**
 * Permanent-delete counters (after recycle bin empty succeeds) + approx bytes freed.
 */
class StatsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun deletedToday(): Int {
        ensureDayRollover()
        return prefs.getInt(KEY_DELETED_TODAY, 0)
    }

    fun deletedTotal(): Int = prefs.getInt(KEY_DELETED_TOTAL, 0)

    fun bytesFreed(): Long = prefs.getLong(KEY_BYTES_FREED, 0L)

    fun softDeletedToday(): Int {
        ensureDayRollover()
        return prefs.getInt(KEY_SOFT_TODAY, 0)
    }

    fun softDeletedTotal(): Int = prefs.getInt(KEY_SOFT_TOTAL, 0)

    fun recordSoftDelete(count: Int = 1) {
        if (count <= 0) return
        ensureDayRollover()
        prefs.edit()
            .putInt(KEY_SOFT_TODAY, prefs.getInt(KEY_SOFT_TODAY, 0) + count)
            .putInt(KEY_SOFT_TOTAL, prefs.getInt(KEY_SOFT_TOTAL, 0) + count)
            .apply()
    }

    fun recordPermanentDelete(count: Int, bytes: Long) {
        if (count <= 0) return
        ensureDayRollover()
        prefs.edit()
            .putInt(KEY_DELETED_TODAY, prefs.getInt(KEY_DELETED_TODAY, 0) + count)
            .putInt(KEY_DELETED_TOTAL, prefs.getInt(KEY_DELETED_TOTAL, 0) + count)
            .putLong(KEY_BYTES_FREED, prefs.getLong(KEY_BYTES_FREED, 0L) + bytes.coerceAtLeast(0L))
            .apply()
    }

    private fun ensureDayRollover() {
        val today = dayKey()
        val stored = prefs.getString(KEY_DAY, null)
        if (stored != today) {
            prefs.edit()
                .putString(KEY_DAY, today)
                .putInt(KEY_DELETED_TODAY, 0)
                .putInt(KEY_SOFT_TODAY, 0)
                .apply()
        }
    }

    private fun dayKey(): String {
        val c = Calendar.getInstance()
        return "${c.get(Calendar.YEAR)}-${c.get(Calendar.DAY_OF_YEAR)}"
    }

    companion object {
        const val PREFS_NAME = "sudelan_stats"
        const val KEY_DAY = "day_key"
        const val KEY_DELETED_TODAY = "deleted_today"
        const val KEY_DELETED_TOTAL = "deleted_total"
        const val KEY_BYTES_FREED = "bytes_freed"
        const val KEY_SOFT_TODAY = "soft_today"
        const val KEY_SOFT_TOTAL = "soft_total"
    }
}
