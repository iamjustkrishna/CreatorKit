package space.iamjustkrishna.creatorkit.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File

object FileSaver{
    fun saveAudioToPublicMusic(context: Context, tempFile: File, fileName: String): Uri? {

        val contentResolver = context.contentResolver
        val audioDetails = ContentValues().apply{
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$fileName.m4a")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")

            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/CreatorKit")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }

        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        val uri = contentResolver.insert(collection, audioDetails) ?: return null

        uri.let{ targetUri ->
            contentResolver.openOutputStream(targetUri)?.use{ outputStream ->
                tempFile.inputStream().use{ inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            audioDetails.clear()
            audioDetails.put(MediaStore.Audio.Media.IS_PENDING, 0)
            contentResolver.update(targetUri, audioDetails, null, null)

            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "CreatorKit/${fileName}.m4a").absolutePath),
                null,
                null
            )
            return uri

        }
    }
}