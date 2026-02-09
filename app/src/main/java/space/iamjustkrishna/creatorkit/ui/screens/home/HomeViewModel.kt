package space.iamjustkrishna.creatorkit.ui.screens.home

import android.app.Application
import android.content.ContentUris
import android.os.Build
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

            // 1. Build Projection: We need path info to check the folder
            val projection = mutableListOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.DATA // Deprecated but needed for path check on old Android
            )

            // Add RELATIVE_PATH for Android 10+ (API 29)
            projection.add(MediaStore.Audio.Media.RELATIVE_PATH)

            val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

            try {
                getApplication<Application>().contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection.toTypedArray(),
                    null,
                    null,
                    sortOrder
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                    val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                    val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol) ?: "Unknown"
                        val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                        // 2. Folder Check Logic
                        var isInCreatorKitFolder = false

                        // API 29+: Check RELATIVE_PATH (e.g., "Music/CreatorKit/")
                        val relPathIndex = cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                        if (relPathIndex != -1) {
                            val relPath = cursor.getString(relPathIndex)
                            if (relPath != null && relPath.contains("CreatorKit")) {
                                isInCreatorKitFolder = true
                            }
                        }

                        // 3. Add only if it matches the folder
                        if (isInCreatorKitFolder) {
                            files.add(
                                MediaFile(
                                    id = id,
                                    uri = contentUri,
                                    name = name,
                                    mimeType = cursor.getString(mimeCol) ?: "audio/*",
                                    size = cursor.getLong(sizeCol),
                                    dateAdded = cursor.getLong(dateCol)
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            _recentFiles.value = files
        }
    }

    fun deleteFile(file: MediaFile) {
        viewModelScope.launch {
            try {
                getApplication<Application>().contentResolver.delete(file.uri, null, null)
                loadRecentFiles() // Refresh list
            } catch (e: SecurityException) {
                // On Android 10+, if we don't own the file, we need to request permission via RecoverableSecurityException
                e.printStackTrace()
            }
        }
    }
}