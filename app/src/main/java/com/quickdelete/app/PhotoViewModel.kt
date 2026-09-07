package com.quickdelete.app

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Photo(
    val id: Long,
    val uri: Uri,
    val dateAdded: Long
)

class PhotoViewModel : ViewModel() {
    private val _photos = mutableStateListOf<Photo>()
    val photos: List<Photo> = _photos

    /** Photos staged for delete (removed from feed, awaiting one-shot system confirm). */
    private val _pendingDelete = mutableStateListOf<Photo>()
    val pendingDelete: List<Photo> = _pendingDelete

    private var seenStore: SeenPhotosStore? = null

    var currentIndex by mutableIntStateOf(0)
        private set

    /** Confirmed deletes today (after user confirms batch). */
    var deletedToday by mutableIntStateOf(0)
        private set

    val pendingCount: Int
        get() = _pendingDelete.size

    var isLoading by mutableStateOf(true)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    /**
     * True when the library has photos but every one is already in the seen set,
     * so the active feed is empty and the user may want 「重新浏览」.
     */
    var allPhotosSeen by mutableStateOf(false)
        private set

    /** Total images in MediaStore before filtering seen IDs. */
    var libraryCount by mutableIntStateOf(0)
        private set

    fun loadPhotos(context: Context) {
        if (seenStore == null) {
            seenStore = SeenPhotosStore(context)
        }
        viewModelScope.launch {
            isLoading = true
            errorMessage = null

            try {
                val store = seenStore!!
                val seenIds = store.getSeenIds()
                val photoList = withContext(Dispatchers.IO) {
                    queryPhotos(context.contentResolver)
                }
                libraryCount = photoList.size
                val filtered = photoList.filter { it.id !in seenIds }
                allPhotosSeen = photoList.isNotEmpty() && filtered.isEmpty()
                _photos.clear()
                _photos.addAll(filtered)
                _pendingDelete.clear()
                currentIndex = 0
            } catch (e: Exception) {
                errorMessage = e.message ?: "加载失败"
            } finally {
                isLoading = false
            }
        }
    }

    /** Clears seen set and reloads the full library into the feed. */
    fun clearSeenAndReload(context: Context) {
        if (seenStore == null) {
            seenStore = SeenPhotosStore(context)
        }
        seenStore?.clear()
        allPhotosSeen = false
        loadPhotos(context)
    }

    private fun markCurrentSeen() {
        val photo = getCurrentPhoto() ?: return
        seenStore?.markSeen(photo.id)
    }

    private fun queryPhotos(contentResolver: ContentResolver): List<Photo> {
        val photoList = mutableListOf<Photo>()

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        contentResolver.query(
            collection,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val dateAdded = cursor.getLong(dateColumn)
                val uri = ContentUris.withAppendedId(collection, id)

                photoList.add(Photo(id, uri, dateAdded))
            }
        }

        return photoList
    }

    /** Swipe UP: mark current as seen, then advance. */
    fun moveToNext() {
        if (currentIndex < _photos.size - 1) {
            markCurrentSeen()
            currentIndex++
        } else if (currentIndex == _photos.size - 1 && _photos.isNotEmpty()) {
            // Last photo: still mark seen so it won't return on restart.
            markCurrentSeen()
            // Stay on last index; feed may look "done" but pending delete can remain.
            // Optionally remove from active consideration — keep in list for swipe-down.
        }
    }

    fun moveToPrevious() {
        if (currentIndex > 0) {
            currentIndex--
        }
    }

    /**
     * Stage current photo for batch delete: remove from feed visually, do NOT call MediaStore yet.
     * Also mark as seen so it does not reappear if the user force-stops before confirming.
     */
    fun stageCurrentForDelete() {
        if (_photos.isEmpty() || currentIndex !in _photos.indices) return

        val photo = _photos[currentIndex]
        seenStore?.markSeen(photo.id)
        _photos.removeAt(currentIndex)
        _pendingDelete.add(photo)

        // Keep showing the photo that slid into this index; clamp if we removed the last one.
        if (currentIndex >= _photos.size && currentIndex > 0) {
            currentIndex = _photos.size - 1
        }
        if (_photos.isEmpty()) {
            currentIndex = 0
            // If library had only these (now pending/seen), surface escape hatch after confirm.
            if (libraryCount > 0 && pendingCount == 0) {
                allPhotosSeen = true
            }
        }
    }

    /**
     * Launch ONE system delete confirmation for the whole pending batch.
     * Caller should handle Activity result via [onBatchDeleteResult].
     */
    fun requestBatchDelete(
        context: Context,
        deleteRequestLauncher: ActivityResultLauncher<IntentSenderRequest>
    ) {
        if (_pendingDelete.isEmpty()) return

        viewModelScope.launch {
            try {
                val uris = _pendingDelete.map { it.uri }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val pendingIntent = withContext(Dispatchers.IO) {
                        MediaStore.createDeleteRequest(context.contentResolver, uris)
                    }
                    deleteRequestLauncher.launch(
                        IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                    )
                } else {
                    // Pre-R: delete directly without system picker; treat as confirmed.
                    val deleted = withContext(Dispatchers.IO) {
                        var count = 0
                        for (photo in _pendingDelete.toList()) {
                            val rows = context.contentResolver.delete(photo.uri, null, null)
                            if (rows > 0) count++
                        }
                        count
                    }
                    onBatchDeleteConfirmed(deleted)
                }
            } catch (e: Exception) {
                errorMessage = "删除失败: ${e.message}"
            }
        }
    }

    /** Call when system delete UI returns RESULT_OK. */
    fun onBatchDeleteConfirmed(count: Int = _pendingDelete.size) {
        deletedToday += count.coerceAtLeast(0)
        _pendingDelete.clear()
        if (_photos.isEmpty() && libraryCount > 0) {
            // Remaining library entries are seen (or deleted); offer rebrowse if any remain on disk.
            // libraryCount still reflects pre-delete snapshot; refresh flag conservatively.
            allPhotosSeen = true
        }
    }

    /** Call when user cancels system delete UI — restore staged items into the feed. */
    fun onBatchDeleteCancelled() {
        if (_pendingDelete.isEmpty()) return
        // Restore at the front of the remaining feed so user can re-review.
        val restored = _pendingDelete.toList()
        _pendingDelete.clear()
        _photos.addAll(0, restored)
        currentIndex = 0
        allPhotosSeen = false
    }

    fun getCurrentPhoto(): Photo? {
        return if (currentIndex in _photos.indices) {
            _photos[currentIndex]
        } else {
            null
        }
    }

    fun getNextPhoto(): Photo? {
        val nextIndex = currentIndex + 1
        return if (nextIndex in _photos.indices) {
            _photos[nextIndex]
        } else {
            null
        }
    }

    fun getPreviousPhoto(): Photo? {
        val prevIndex = currentIndex - 1
        return if (prevIndex in _photos.indices) {
            _photos[prevIndex]
        } else {
            null
        }
    }
}
