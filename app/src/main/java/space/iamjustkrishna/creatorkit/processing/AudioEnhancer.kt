package space.iamjustkrishna.creatorkit.processing

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import java.io.File

class AudioEnhancer(private val context: Context) {

    @OptIn(UnstableApi::class)
    fun enhanceAudio(
        inputUri: Uri,
        outputFile: File,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        // 1. Create our Custom Studio Chain
        val processors = ImmutableList.of<AudioProcessor>(
            StudioAudioProcessor(volumeMultiplier = 1.8f) // Boost volume by 1.8x
        )

        // 2. Setup the Media Item with Effects
        val editedMediaItem = EditedMediaItem.Builder(MediaItem.fromUri(inputUri))
            .setRemoveVideo(true) // EXTRACT AUDIO ONLY
            .setEffects(Effects(processors, ImmutableList.of()))
            .build()

        // 3. Configure Transformer
        val transformer = Transformer.Builder(context)
            .setAudioMimeType(MimeTypes.AUDIO_AAC) // High quality AAC
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    onSuccess(outputFile.absolutePath)
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exception: ExportException) {
                    onError(exception.message ?: "Unknown Error")
                }
            })
            .build()

        // 4. Start!
        transformer.start(editedMediaItem, outputFile.absolutePath)
    }
}