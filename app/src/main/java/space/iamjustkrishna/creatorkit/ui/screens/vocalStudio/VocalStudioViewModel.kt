package space.iamjustkrishna.creatorkit.ui.screens.vocalStudio

import android.app.Application
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import space.iamjustkrishna.creatorkit.util.FileSaver // Use the FileSaver we wrote earlier
import kotlinx.coroutines.launch
import space.iamjustkrishna.creatorkit.data.model.MediaFile
import space.iamjustkrishna.creatorkit.processing.AudioEnhancer
import java.io.File

class VocalStudioViewModel(application: Application) : AndroidViewModel(application) {

    private val enhancer = AudioEnhancer(application)

    private val _audioLibrary = MutableStateFlow<List<MediaFile>>(emptyList())
    val audioLibrary = _audioLibrary.asStateFlow()

    var uiState by mutableStateOf<VocalStudioState>(VocalStudioState.Idle)
        private set

    fun processAudio(uri: Uri) {
        uiState = VocalStudioState.Processing

        val tempFile = File(getApplication<Application>().cacheDir, "temp_studio.m4a")

        enhancer.enhanceAudio(
            inputUri = uri,
            outputFile = tempFile,
            onSuccess = { path ->
                val savedUri = FileSaver.saveAudioToPublicMusic(
                    getApplication(),
                    File(path),
                    "CreatorKit_Studio_${System.currentTimeMillis()}"
                )
                // FIX: Pass both the input URI and the new Result URI
                uiState = VocalStudioState.Success(originalUri = uri, outputUri = savedUri ?: Uri.EMPTY)
            },
            onError = { error ->
                uiState = VocalStudioState.Error(error)
            }
        )
    }

    fun reset(){
        uiState = VocalStudioState.Idle
    }

    fun loadAudioLibrary() {
        viewModelScope.launch {
            val files = mutableListOf<MediaFile>()
            val context = getApplication<Application>()

            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.DURATION
            )

            // Query: Fetch ALL audio (Music + Recordings)
            // We removed the strict "IS_MUSIC" check because some voice recorders don't set it.
            val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

            try {
                context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null, // No strict selection (fetch everything)
                    null,
                    sortOrder
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                    val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                    val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                        val name = cursor.getString(nameCol) ?: "Unknown Audio"
                        val duration = cursor.getLong(durCol)

                        // Filter out very short audio (e.g., notification sounds < 1 sec)
                        if (duration > 1000) {
                            files.add(
                                MediaFile(
                                    id = id,
                                    uri = contentUri,
                                    name = name,
                                    mimeType = cursor.getString(mimeCol) ?: "audio/*",
                                    size = cursor.getLong(sizeCol),
                                    dateAdded = cursor.getLong(dateCol),
                                    duration = duration
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            _audioLibrary.value = files
        }
    }
}

sealed class VocalStudioState {
    object Idle : VocalStudioState()
    object Processing : VocalStudioState()

    data class Success(val originalUri: Uri, val outputUri: Uri) : VocalStudioState()
    data class Error(val message: String) : VocalStudioState()
}