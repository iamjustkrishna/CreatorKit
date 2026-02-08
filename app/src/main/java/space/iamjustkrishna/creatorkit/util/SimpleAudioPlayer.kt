package space.iamjustkrishna.creatorkit.util

import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import space.iamjustkrishna.creatorkit.R

@Composable
fun SimpleAudioPlayer(
    uri: Uri,
    modifier: Modifier = Modifier,
    toPlay: Boolean = false // This parameter controls auto-play
) {
    val context = LocalContext.current

    // We initialize isPlaying based on toPlay so the UI state matches immediately
    var isPlaying by remember { mutableStateOf(toPlay) }

    // Use mutableStateOf for Float values to avoid boxing overhead
    var currentPosition by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableFloatStateOf(1f) }

    // The MediaPlayer instance
    val mediaPlayer = remember {
        MediaPlayer().apply {
            setDataSource(context, uri)
            prepare() // Synchronous prepare is okay for local files, but consider prepareAsync for streams
            setOnCompletionListener {
                isPlaying = false
                currentPosition = 0f
                seekTo(0) // Reset to start
            }
        }
    }

    // 1. AUTO-PLAY LOGIC (The Fix)
    // This effect runs once when the component is created.
    // If 'toPlay' is true, it starts playback immediately.
    LaunchedEffect(Unit) {
        duration = mediaPlayer.duration.toFloat()
        if (toPlay) {
            mediaPlayer.start()
            isPlaying = true
        }
    }

    // Cleanup when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
            }
            mediaPlayer.release()
        }
    }

    // Progress Updater (The "Tick" logic)
    DisposableEffect(isPlaying) {
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                if (isPlaying) {
                    try {
                        currentPosition = mediaPlayer.currentPosition.toFloat()
                        handler.postDelayed(this, 100) // Update every 100ms
                    } catch (e: Exception) {
                        // Handle potential race condition if player is released
                    }
                }
            }
        }
        if (isPlaying) handler.post(runnable)
        onDispose { handler.removeCallbacks(runnable) }
    }

    Card(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Slider (Seekbar)
            Slider(
                value = currentPosition,
                onValueChange = { newPos ->
                    currentPosition = newPos // Update slider immediately for smoothness
                },
                onValueChangeFinished = {
                    // Only seek when user lifts finger to avoid audio stutter
                    mediaPlayer.seekTo(currentPosition.toInt())
                },
                valueRange = 0f..duration,
                modifier = Modifier.fillMaxWidth()
            )

            // Time Labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatTime(currentPosition.toLong()), style = MaterialTheme.typography.bodySmall)
                Text(formatTime(duration.toLong()), style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Play/Pause Button
            IconButton(
                onClick = {
                    if (isPlaying) {
                        mediaPlayer.pause()
                    } else {
                        mediaPlayer.start()
                    }
                    isPlaying = !isPlaying
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) ImageVector.vectorResource(R.drawable.pause) else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// Helper to format MM:SS
fun formatTime(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / (1000 * 60)) % 60
    return String.format("%02d:%02d", minutes, seconds)
}