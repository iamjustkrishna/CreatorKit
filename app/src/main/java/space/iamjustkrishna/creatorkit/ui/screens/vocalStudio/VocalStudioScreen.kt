package space.iamjustkrishna.creatorkit.ui.screens.vocalStudio

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import space.iamjustkrishna.creatorkit.R
import space.iamjustkrishna.creatorkit.util.SimpleAudioPlayer

@Composable
fun VocalStudioScreen(viewModel: VocalStudioViewModel = viewModel()) {
    val library by viewModel.audioLibrary.collectAsState()
    val state = viewModel.uiState
    val context = LocalContext.current

    // State for the "Preview" player
    var previewUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(state) {
        if (state !is VocalStudioState.Success) {
            viewModel.loadAudioLibrary()
        }
    }

    // --- MAIN CONTENT ---
    if (state is VocalStudioState.Success) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.autoawesome),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(16.dp))

            Text("Enhancement Complete!", style = MaterialTheme.typography.headlineMedium)
            Text("Toggle below to hear the difference", style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(32.dp))

            var isOriginal by remember { mutableStateOf(false) }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Studio Enhanced",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (!isOriginal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Switch(
                    checked = !isOriginal,
                    onCheckedChange = { isOriginal = !it },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                Text(
                    text = "Original",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isOriginal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(Modifier.height(24.dp))

            val uriToPlay = if (isOriginal) state.originalUri else state.outputUri

            // Force player reload when switching files
            key(uriToPlay) {
                // Ensure you are using the Composable UI version of AudioPlayer here
                SimpleAudioPlayer(uri = uriToPlay)
            }

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = {
                    previewUri = null // Reset preview when going back
                    viewModel.reset()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("Enhance Another File")
            }
        }
    } else {
        // === LIBRARY MODE (Pick a File) ===
        Box(modifier = Modifier.fillMaxSize()) {

            if (library.isEmpty()) {
                // EMPTY STATE
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(painterResource(R.drawable.audiofile), contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.Gray)
                    Spacer(Modifier.height(16.dp))
                    Text("No audio files found.", style = MaterialTheme.typography.bodyLarge)
                    Text("Try recording some audio first.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            } else {
                // LIBRARY LIST
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // Add padding at bottom so the Player doesn't cover the last item
                    contentPadding = PaddingValues(bottom = 140.dp)
                ) {
                    item {
                        Text(
                            text = "Select Audio to Enhance",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(16.dp)
                        )
                    }

                    items(items = library, key = { it.id }) { file ->
                        val isPreviewing = previewUri == file.uri

                        // HIGHLIGHT LOGIC: Check if file is already processed
                        val isEnhanced = file.name.contains("CreatorKit", ignoreCase = true)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clickable {
                                    // Play/Pause on card click
                                    previewUri = if (isPreviewing) null else file.uri
                                },
                            colors = CardDefaults.cardColors(
                                // FIX: Use a SOLID color (SurfaceVariant) instead of alpha transparency
                                containerColor = if (isPreviewing) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                            ),
                            // Highlight border if enhanced
                            border = if (isEnhanced) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // A. Play/Pause Icon
                                IconButton(onClick = {
                                    previewUri = if (isPreviewing) null else file.uri
                                }) {
                                    Icon(
                                        imageVector = if (isPreviewing) ImageVector.vectorResource(R.drawable.pause) else Icons.Rounded.PlayArrow,
                                        contentDescription = "Preview",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // B. File Info (Middle Section)
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = file.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Text(
                                        text = file.formattedSize(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // C. Right Section (Badge + Button)
                                Row(verticalAlignment = Alignment.CenterVertically) {

                                    // Badge for Enhanced files
                                    if (isEnhanced) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = MaterialTheme.shapes.small,
                                            modifier = Modifier.padding(end = 8.dp)
                                        ) {
                                            Text(
                                                text = "Processed",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    // Enhance Action Button
                                    Button(
                                        onClick = {
                                            previewUri = null // Stop preview
                                            viewModel.processAudio(file.uri)
                                        },
                                        modifier = Modifier.height(36.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp)
                                    ) {
                                        Text("Enhance")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. BOTTOM MUSIC PLAYER (Visible UI)
            // This stays pinned to the bottom of the Box
            if (previewUri != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    shadowElevation = 16.dp,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Now Playing Preview",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Force reload when URI changes
                        key(previewUri) {
                            // This uses the Composable UI version with Seekbar we created earlier
                            SimpleAudioPlayer(uri = previewUri!!, toPlay = true)
                        }
                    }
                }
            }

            // 4. LOADING OVERLAY
            if (state is VocalStudioState.Processing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Applying Studio Magic...",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}
