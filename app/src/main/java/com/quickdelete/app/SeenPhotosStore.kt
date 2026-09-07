package com.quickdelete.app

import android.content.Context

/**
 * Local persistence for photo IDs the user has already skipped / staged.
 * MediaStore _ID values are stored as strings in a SharedPreferences StringSet.
 */
class SeenPhotosStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSeenIds(): Set<Long> {
        return prefs.getStringSet(KEY_SEEN_IDS, emptySet())
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet()
            ?: emptySet()
    }

    fun markSeen(id: Long) {
        val current = prefs.getStringSet(KEY_SEEN_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (current.add(id.toString())) {
            prefs.edit().putStringSet(KEY_SEEN_IDS, current).apply()
        }
    }

    fun clear() {
        prefs.edit().remove(KEY_SEEN_IDS).apply()
    }

    fun seenCount(): Int = prefs.getStringSet(KEY_SEEN_IDS, emptySet())?.size ?: 0

    companion object {
        const val PREFS_NAME = "seen_photos"
        const val KEY_SEEN_IDS = "seen_photo_ids"
    }
}

