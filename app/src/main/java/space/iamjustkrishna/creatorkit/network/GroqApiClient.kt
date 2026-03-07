package space.iamjustkrishna.creatorkit.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class GroqApiClient(private val apiKey: String) {

    // Groq's API is very fast, but uploading the audio file might take a moment
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Uploads an audio file to Groq and returns the SRT (Subtitle) string.
     */
    suspend fun generateCaptions(audioFile: File): String? = withContext(Dispatchers.IO) {
        if (!audioFile.exists()) return@withContext null

        // 1. Create the Multipart Request Body
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                audioFile.name,
                audioFile.asRequestBody("audio/m4a".toMediaTypeOrNull()) // Adjust MIME if using MP3
            )
            // Groq's fastest Whisper model
            .addFormDataPart("model", "whisper-large-v3-turbo")
            // Crucial: Tell Groq to return an SRT subtitle file, not plain JSON
            .addFormDataPart("response_format", "verbose_json")
            .addFormDataPart("language", "en")
            .build()

        // 2. Build the Request
        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/audio/transcriptions")
            .post(requestBody)
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        // 3. Execute and Return
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    println("Groq Error: ${response.code} - ${response.body?.string()}")
                    return@withContext null
                }
                // This returns the raw SRT text!
                return@withContext response.body?.string()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            return@withContext null
        }
    }
}