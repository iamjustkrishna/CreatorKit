package space.iamjustkrishna.creatorkit.ui.screens.editor


import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import java.io.File

/**
 * Shared ViewModel scoped to the Activity.
 * Passes the picked video URI and (optionally) the extracted audio file
 * to VideoEditorScreen without needing navigation arguments.
 */class SharedEditorViewModel : ViewModel() {
    var selectedVideoUri: Uri? by mutableStateOf(null)
        private set

    fun setVideoUri(uri: Uri) {
        selectedVideoUri = uri
    }

    fun clear() {
        selectedVideoUri = null
    }
}