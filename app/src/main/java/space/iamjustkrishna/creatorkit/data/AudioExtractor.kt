package space.iamjustkrishna.creatorkit.data

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Transformer

class AudioExtractor(private val context: Context) {
    @OptIn(UnstableApi::class)
    fun extract(inputUri: Uri, outputFilePath: String, mimeType: String, listener: Transformer.Listener){
        val transformer = Transformer.Builder(context)
            .setAudioMimeType(mimeType)
            .addListener(listener)
            .build()

        val mediaItem = MediaItem.fromUri(inputUri)

        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setRemoveVideo(true)
            .build()

        transformer.start(editedMediaItem, outputFilePath)
    }
}