package space.iamjustkrishna.creatorkit.ui.screens.home

import android.app.Application
import android.content.ContentUris
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import space.iamjustkrishna.creatorkit.data.model.MediaFile

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val _recentFiles = MutableStateFlow<List<MediaFile>>(emptyList())
    val recentFiles = _recentFiles.asStateFlow()

    fun loadRecentFiles() {
        viewModelScope.launch {
            val files = mutableListOf<MediaFile>()
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.MIME_TYPE
            )

            // Query only files in "Music/CreatorKit" (Android 10+)
            // Note: On older Androids, this query finds *all* audio, so we filter by path/name usually.
            // For simplicity in this learning project, we fetch recent audio.
            val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

            getApplication<Application>().contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol)
                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                    // Simple Filter: Only show files that look like our app's output
                    // In a real app, you'd filter strictly by folder path
                    if (name.contains("CreatorKit") || name.contains("Extracted") || name.contains("Studio")) {
                        files.add(
                            MediaFile(
                                id = id,
                                uri = contentUri,
                                name = name,
                                mimeType = cursor.getString(mimeCol),
                                size = cursor.getLong(sizeCol),
                                dateAdded = cursor.getLong(dateCol)
                            )
                        )
                    }
                }
            }
            _recentFiles.value = files
        }
    }

    // Add this function inside HomeViewModel
    fun deleteFile(file: MediaFile) {
        viewModelScope.launch {
            try {
                getApplication<Application>().contentResolver.delete(file.uri, null, null)
                loadRecentFiles() // Refresh list
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }
}