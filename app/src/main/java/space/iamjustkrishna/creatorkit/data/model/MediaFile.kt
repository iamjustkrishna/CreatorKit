package space.iamjustkrishna.creatorkit.data.model

import android.net.Uri

data class MediaFile(
    val id: Long,
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val size: Long,
    val dateAdded: Long,
    val duration: Long = 0L // Duration in milliseconds
) {
    // Helper to format size (e.g., "5.2 MB")
    fun formattedSize(): String {
        val kb = size / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1 -> String.format("%.1f MB", mb)
            kb >= 1 -> String.format("%.1f KB", kb)
            else -> "$size B"
        }
    }
}
