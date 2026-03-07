package space.iamjustkrishna.creatorkit.data

import org.json.JSONObject

// 1. The Data Model
data class CaptionSegment(
    val id: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    var text: String // 'var' because the user will edit this!
)

// 2. The Parser Helper
fun parseGroqCaptions(jsonString: String): List<CaptionSegment> {
    val segments = mutableListOf<CaptionSegment>()
    try {
        val jsonObject = JSONObject(jsonString)
        val jsonArray = jsonObject.getJSONArray("segments")

        for (i in 0 until jsonArray.length()) {
            val segment = jsonArray.getJSONObject(i)

            // Convert Groq's seconds (e.g., 1.5) to ExoPlayer's milliseconds (1500)
            val startMs = (segment.getDouble("start") * 1000).toLong()
            val endMs = (segment.getDouble("end") * 1000).toLong()
            val text = segment.getString("text").trim()

            segments.add(CaptionSegment(id = i, startTimeMs = startMs, endTimeMs = endMs, text = text))
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return segments
}