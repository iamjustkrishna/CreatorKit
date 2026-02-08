package space.iamjustkrishna.creatorkit.ui.screens.extractor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import space.iamjustkrishna.creatorkit.R
import space.iamjustkrishna.creatorkit.util.SimpleAudioPlayer

@Composable
fun ExtractorScreen(viewModel: ExtractorViewModel = viewModel(),
                    onBackClick: () -> Unit) {
    // 1. Observe the new State (Idle, Processing, Success, Error)
    val state = viewModel.extractionState

    // 2. Setup the Media Picker
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.checkAndStartExtraction(it) }
    }

    if (state is ExtractionState.DuplicateFound) {
        AlertDialog(
            onDismissRequest = { viewModel.resetState() }, // Create a reset function
            title = { Text("File Already Exists") },
            text = { Text("You have already extracted audio from this video. Do you want to do it again?") },
            confirmButton = {
                TextButton(onClick = { viewModel.forceStartExtraction(state.uri) }) {
                    Text("Yes, Overwrite")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.resetState() }) {
                    Text("Cancel")
                }
            },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- The Button ---
        Button(
            onClick = {
                launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
            },
            enabled = state !is ExtractionState.Processing,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            // Using a default icon for safety, you can swap back to R.drawable.videofile
            Icon(imageVector = ImageVector.vectorResource(R.drawable.videofile), contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (state is ExtractionState.Success) "Select Another Video" else "Select Video to Extract Audio")
        }

        Spacer(modifier = Modifier.height(32.dp))

        // --- Dynamic State Content ---
        when (state) {
            is ExtractionState.Idle -> {
                Text(
                    text = "Supports MP4, MKV, AVI, and more.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is ExtractionState.Processing -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Extracting audio... Please wait.")
            }
            is ExtractionState.Success -> {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Extraction Complete!",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Audio saved to Music/CreatorKit",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        SimpleAudioPlayer(uri = state.outputUri)
                    }
                }
            }
            is ExtractionState.Error -> {
                Text(
                    text = "Error: ${state.message}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            // DuplicateFound is handled by the AlertDialog above,
            is ExtractionState.DuplicateFound -> {}
        }
    }
}