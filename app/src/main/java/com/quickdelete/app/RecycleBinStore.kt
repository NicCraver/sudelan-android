package com.quickdelete.app

import android.content.Context

/**
 * Soft-deleted photo IDs persisted until the user empties the recycle bin
 * (which then issues one MediaStore createDeleteRequest).
 */
class RecycleBinStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getBinIds(): Set<Long> {
        return prefs.getStringSet(KEY_BIN_IDS, emptySet())
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet()
            ?: emptySet()
    }

    fun add(id: Long) {
        val current = prefs.getStringSet(KEY_BIN_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (current.add(id.toString())) {
            prefs.edit().putStringSet(KEY_BIN_IDS, current).apply()
        }
    }

    fun addAll(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val current = prefs.getStringSet(KEY_BIN_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        var changed = false
        for (id in ids) {
            if (current.add(id.toString())) changed = true
        }
        if (changed) {
            prefs.edit().putStringSet(KEY_BIN_IDS, current).apply()
        }
    }

    fun remove(id: Long) {
        val current = prefs.getStringSet(KEY_BIN_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (current.remove(id.toString())) {
            prefs.edit().putStringSet(KEY_BIN_IDS, current).apply()
        }
    }

    fun clear() {
        prefs.edit().remove(KEY_BIN_IDS).apply()
    }

    fun count(): Int = prefs.getStringSet(KEY_BIN_IDS, emptySet())?.size ?: 0

    companion object {
        const val PREFS_NAME = "recycle_bin"
        const val KEY_BIN_IDS = "recycle_bin_ids"
    }
}
