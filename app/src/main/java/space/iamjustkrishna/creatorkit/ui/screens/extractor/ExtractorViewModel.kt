package space.iamjustkrishna.creatorkit.ui.screens.extractor

import android.app.Application
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import space.iamjustkrishna.creatorkit.data.AudioExtractor
import space.iamjustkrishna.creatorkit.util.FileSaver
import java.io.File


sealed class ExtractionState {
    object Idle : ExtractionState()
    data class Selected(val uri: Uri, val initialName: String) : ExtractionState() // New state for Rename Dialog
    object Processing : ExtractionState()
    data class Success(val outputUri: Uri) : ExtractionState()
    data class Error(val message: String) : ExtractionState()
    data class DuplicateFound(val uri: Uri, val fileName: String, val format: AudioFormat) : ExtractionState()
}

enum class AudioFormat(val extension: String, val mimeType: String, val displayName: String) {
    M4A("m4a", androidx.media3.common.MimeTypes.AUDIO_AAC, "M4A (AAC)"),
    MP3("mp3", androidx.media3.common.MimeTypes.AUDIO_MPEG, "MP3")
}

class ExtractorViewModel(application: Application) : AndroidViewModel(application) {
    private val extractor = AudioExtractor(application)
    private val context = application.applicationContext

    var extractionState by mutableStateOf<ExtractionState>(ExtractionState.Idle)
        private set

    // 1. Called when user clicks a video item. Sets state to Selected to trigger Dialog.
    fun onVideoSelected(videoUri: Uri) {
        val originalName = getFileName(videoUri)
        extractionState = ExtractionState.Selected(videoUri, originalName)
    }

    // 2. Called when user clicks "Extract" in the Dialog.
    fun checkAndStartExtraction(videoUri: Uri, customName: String, format: AudioFormat) {
        val safeName = "${customName}.${format.extension}" // e.g. "MySong.mp3"

        if (fileExistsInMediaStore(safeName)) {
            // Pass the format to the duplicate state so we remember it
            extractionState = ExtractionState.DuplicateFound(videoUri, safeName, format)
        } else {
            forceStartExtraction(videoUri, safeName, format)
        }
    }

    private fun fileExistsInMediaStore(fileNameWithExtension: String): Boolean {
        val projection = arrayOf(MediaStore.Audio.Media._ID)
        // Check for Display Name AND Relative Path (to be safe)
        val selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf(fileNameWithExtension, "%CreatorKit%")

        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )
        val exists = (cursor?.count ?: 0) > 0
        cursor?.close()
        return exists
    }

    private fun getFileName(uri: Uri): String {
        var result = "Audio"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    result = cursor.getString(nameIndex).substringBeforeLast(".")
                }
            }
        }
        return result
    }

    @OptIn(UnstableApi::class)
    fun forceStartExtraction(videoUri: Uri, finalFileName: String, format: AudioFormat) {
        extractionState = ExtractionState.Processing

        // 1. Prepare Output Path in Cache
        // Ensure filename doesn't have extension for the temp file creation logic if needed,
        // but here we just need a unique temp path.
        val safeFileName = finalFileName.substringBeforeLast(".")
        val tempOutputPath = "${context.cacheDir}/$safeFileName${format.extension}"

        val tempFile = File(tempOutputPath)

        // 2. Start Extraction
        extractor.extract(videoUri, tempFile.absolutePath, mimeType = format.mimeType, object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                // 3. Save to Public Music Folder with the user's chosen name
                val savedUri = FileSaver.saveAudioToPublicMusic(
                    context = context,
                    tempFile = tempFile,
                    fileName = safeFileName // FileSaver likely handles adding extension or expects name without it
                )

                // Update UI to show the Player
                extractionState = if (savedUri != null) {
                    ExtractionState.Success(savedUri)
                } else {
                    ExtractionState.Error("Failed to save file")
                }

                // Cleanup temp file
                if (tempFile.exists()) tempFile.delete()
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException
            ) {
                extractionState = ExtractionState.Error(exportException.message ?: "Unknown Error")
            }
        })
    }

    fun resetState() {
        extractionState = ExtractionState.Idle
    }
}