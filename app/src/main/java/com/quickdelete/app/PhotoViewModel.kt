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
import androidx.compose.runtime.mutableLongStateOf
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
    val dateAdded: Long,
    val sizeBytes: Long = 0L
)

enum class AppTab {
    Swipe, Album, Recycle, Me
}

class PhotoViewModel : ViewModel() {
    /** Swipe feed: library minus recycle bin minus seen (unless jumped from album). */
    private val _photos = mutableStateListOf<Photo>()
    val photos: List<Photo> = _photos

    /** Full album (library minus recycle bin), newest first. */
    private val _albumPhotos = mutableStateListOf<Photo>()
    val albumPhotos: List<Photo> = _albumPhotos

    /** Soft-deleted items still present in MediaStore. */
    private val _recyclePhotos = mutableStateListOf<Photo>()
    val recyclePhotos: List<Photo> = _recyclePhotos

    private var seenStore: SeenPhotosStore? = null
    private var recycleStore: RecycleBinStore? = null
    private var statsStore: StatsStore? = null

    /** Full library snapshot (may include bin IDs still on disk). */
    private var libraryAll: List<Photo> = emptyList()

    var currentIndex by mutableIntStateOf(0)
        private set

    var selectedTab by mutableStateOf(AppTab.Swipe)
        private set

    var deletedToday by mutableIntStateOf(0)
        private set

    var deletedTotal by mutableIntStateOf(0)
        private set

    var softDeletedToday by mutableIntStateOf(0)
        private set

    var softDeletedTotal by mutableIntStateOf(0)
        private set

    var bytesFreed by mutableLongStateOf(0L)
        private set

    val recycleCount: Int
        get() = _recyclePhotos.size

    var isLoading by mutableStateOf(true)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var allPhotosSeen by mutableStateOf(false)
        private set

    var libraryCount by mutableIntStateOf(0)
        private set

    /** Album multi-select mode. */
    var albumSelectMode by mutableStateOf(false)
        private set

    private val _albumSelected = mutableStateListOf<Long>()
    val albumSelectedIds: List<Long> = _albumSelected

    fun selectTab(tab: AppTab) {
        selectedTab = tab
        if (tab != AppTab.Album) {
            albumSelectMode = false
            _albumSelected.clear()
        }
    }

    private fun ensureStores(context: Context) {
        if (seenStore == null) seenStore = SeenPhotosStore(context)
        if (recycleStore == null) recycleStore = RecycleBinStore(context)
        if (statsStore == null) statsStore = StatsStore(context)
    }

    private fun refreshStats() {
        val stats = statsStore ?: return
        deletedToday = stats.deletedToday()
        deletedTotal = stats.deletedTotal()
        softDeletedToday = stats.softDeletedToday()
        softDeletedTotal = stats.softDeletedTotal()
        bytesFreed = stats.bytesFreed()
    }

    fun loadPhotos(context: Context) {
        ensureStores(context)
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                refreshStats()
                val store = seenStore!!
                val bin = recycleStore!!
                val seenIds = store.getSeenIds()
                val binIds = bin.getBinIds()
                val photoList = withContext(Dispatchers.IO) {
                    queryPhotos(context.contentResolver)
                }
                libraryAll = photoList
                libraryCount = photoList.size

                val album = photoList.filter { it.id !in binIds }
                val recycle = photoList.filter { it.id in binIds }
                // Drop stale bin IDs no longer in MediaStore
                val liveBinIds = recycle.map { it.id }.toSet()
                val stale = binIds - liveBinIds
                if (stale.isNotEmpty()) {
                    for (id in stale) bin.remove(id)
                }

                _albumPhotos.clear()
                _albumPhotos.addAll(album)
                _recyclePhotos.clear()
                _recyclePhotos.addAll(recycle)

                val filtered = album.filter { it.id !in seenIds }
                allPhotosSeen = album.isNotEmpty() && filtered.isEmpty()
                _photos.clear()
                _photos.addAll(filtered)
                currentIndex = 0
            } catch (e: Exception) {
                errorMessage = e.message ?: "加载失败"
            } finally {
                isLoading = false
            }
        }
    }

    fun clearSeenAndReload(context: Context) {
        ensureStores(context)
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
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE
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
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val dateAdded = cursor.getLong(dateColumn)
                val sizeBytes = cursor.getLong(sizeColumn)
                val uri = ContentUris.withAppendedId(collection, id)
                photoList.add(Photo(id, uri, dateAdded, sizeBytes))
            }
        }

        return photoList
    }

    fun moveToNext() {
        if (currentIndex < _photos.size - 1) {
            markCurrentSeen()
            currentIndex++
        } else if (currentIndex == _photos.size - 1 && _photos.isNotEmpty()) {
            markCurrentSeen()
        }
    }

    fun moveToPrevious() {
        if (currentIndex > 0) {
            currentIndex--
        }
    }

    /**
     * Soft-delete current swipe photo into the in-app recycle bin.
     * Does NOT call MediaStore.
     */
    fun softDeleteCurrent() {
        if (_photos.isEmpty() || currentIndex !in _photos.indices) return
        val photo = _photos[currentIndex]
        softDeletePhotos(listOf(photo))
    }

    /** @deprecated Use [softDeleteCurrent] — kept name for gesture call sites. */
    fun stageCurrentForDelete() = softDeleteCurrent()

    fun softDeletePhotos(photos: List<Photo>) {
        if (photos.isEmpty()) return
        val bin = recycleStore ?: return
        val stats = statsStore
        val ids = photos.map { it.id }.toSet()
        bin.addAll(ids)
        for (photo in photos) {
            seenStore?.markSeen(photo.id)
        }
        stats?.recordSoftDelete(photos.size)
        refreshStats()

        _photos.removeAll { it.id in ids }
        _albumPhotos.removeAll { it.id in ids }
        for (photo in photos) {
            if (_recyclePhotos.none { it.id == photo.id }) {
                _recyclePhotos.add(0, photo)
            }
        }
        _albumSelected.removeAll { it in ids }

        if (currentIndex >= _photos.size && currentIndex > 0) {
            currentIndex = _photos.size - 1
        }
        if (_photos.isEmpty()) {
            currentIndex = 0
            if (_albumPhotos.isNotEmpty()) {
                allPhotosSeen = true
            }
        }
    }

    fun restoreFromRecycle(photo: Photo) {
        val bin = recycleStore ?: return
        bin.remove(photo.id)
        _recyclePhotos.removeAll { it.id == photo.id }
        // Re-insert into album by date (newest first)
        val insertAt = _albumPhotos.indexOfFirst { it.dateAdded < photo.dateAdded }
            .let { if (it < 0) _albumPhotos.size else it }
        if (_albumPhotos.none { it.id == photo.id }) {
            _albumPhotos.add(insertAt, photo)
        }
        // Also surface in swipe feed if not marked seen — always add for immediate visibility
        if (_photos.none { it.id == photo.id }) {
            val feedAt = _photos.indexOfFirst { it.dateAdded < photo.dateAdded }
                .let { if (it < 0) _photos.size else it }
            _photos.add(feedAt, photo)
        }
        allPhotosSeen = false
    }

    fun restoreAllFromRecycle() {
        val copy = _recyclePhotos.toList()
        for (photo in copy) {
            restoreFromRecycle(photo)
        }
    }

    /**
     * Empty recycle bin: ONE system MediaStore delete for all URIs.
     * On cancel, items stay in the bin (unlike v0.1 pending restore).
     */
    fun requestEmptyRecycleBin(
        context: Context,
        deleteRequestLauncher: ActivityResultLauncher<IntentSenderRequest>
    ) {
        if (_recyclePhotos.isEmpty()) return
        viewModelScope.launch {
            try {
                val uris = _recyclePhotos.map { it.uri }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val pendingIntent = withContext(Dispatchers.IO) {
                        MediaStore.createDeleteRequest(context.contentResolver, uris)
                    }
                    deleteRequestLauncher.launch(
                        IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                    )
                } else {
                    val deleted = withContext(Dispatchers.IO) {
                        var count = 0
                        for (photo in _recyclePhotos.toList()) {
                            val rows = context.contentResolver.delete(photo.uri, null, null)
                            if (rows > 0) count++
                        }
                        count
                    }
                    onEmptyRecycleConfirmed(deleted)
                }
            } catch (e: Exception) {
                errorMessage = "删除失败: ${e.message}"
            }
        }
    }

    fun onEmptyRecycleConfirmed(count: Int = _recyclePhotos.size) {
        val bytes = _recyclePhotos.sumOf { it.sizeBytes }
        statsStore?.recordPermanentDelete(count.coerceAtLeast(0), bytes)
        recycleStore?.clear()
        _recyclePhotos.clear()
        refreshStats()
        if (_photos.isEmpty() && _albumPhotos.isNotEmpty()) {
            allPhotosSeen = true
        } else if (_photos.isEmpty() && _albumPhotos.isEmpty() && libraryCount > 0) {
            allPhotosSeen = false
        }
    }

    /** User cancelled system delete — leave recycle bin untouched. */
    fun onEmptyRecycleCancelled() {
        // no-op by design
    }

    /** Album tap: open 刷删 at that photo (full album stream, not seen-filtered). */
    fun jumpToPhotoFromAlbum(photoId: Long) {
        val indexInAlbum = _albumPhotos.indexOfFirst { it.id == photoId }
        if (indexInAlbum < 0) return
        _photos.clear()
        _photos.addAll(_albumPhotos)
        currentIndex = indexInAlbum
        allPhotosSeen = false
        albumSelectMode = false
        _albumSelected.clear()
        selectedTab = AppTab.Swipe
    }

    fun toggleAlbumSelectMode() {
        albumSelectMode = !albumSelectMode
        if (!albumSelectMode) _albumSelected.clear()
    }

    fun toggleAlbumSelection(id: Long) {
        if (id in _albumSelected) {
            _albumSelected.remove(id)
        } else {
            _albumSelected.add(id)
        }
    }

    fun softDeleteAlbumSelection() {
        if (_albumSelected.isEmpty()) return
        val selected = _albumPhotos.filter { it.id in _albumSelected }
        softDeletePhotos(selected)
        albumSelectMode = false
        _albumSelected.clear()
    }

    fun getCurrentPhoto(): Photo? {
        return if (currentIndex in _photos.indices) _photos[currentIndex] else null
    }

    fun getNextPhoto(): Photo? {
        val nextIndex = currentIndex + 1
        return if (nextIndex in _photos.indices) _photos[nextIndex] else null
    }

    fun getPreviousPhoto(): Photo? {
        val prevIndex = currentIndex - 1
        return if (prevIndex in _photos.indices) _photos[prevIndex] else null
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format("%.2f GB", gb)
            mb >= 1.0 -> String.format("%.1f MB", mb)
            kb >= 1.0 -> String.format("%.0f KB", kb)
            else -> "$bytes B"
        }
    }

    /** Approx bytes that would be freed if recycle bin were emptied. */
    fun recycleBinBytes(): Long = _recyclePhotos.sumOf { it.sizeBytes }
}
