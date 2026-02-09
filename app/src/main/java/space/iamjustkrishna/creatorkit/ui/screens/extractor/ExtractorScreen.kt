package space.iamjustkrishna.creatorkit.ui.screens.extractor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import space.iamjustkrishna.creatorkit.R
import space.iamjustkrishna.creatorkit.util.SimpleAudioPlayer
@Composable
fun ExtractorScreen(
    viewModel: ExtractorViewModel = viewModel(),
    onBackClick: () -> Unit
) {
    val state = viewModel.extractionState

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.onVideoSelected(it) }
    }

    var selectedFormat by remember { mutableStateOf(AudioFormat.M4A) }
    if (state is ExtractionState.Selected) {
        var fileName by remember(state) { mutableStateOf(state.initialName) }


        AlertDialog(
            onDismissRequest = { viewModel.resetState() },
            icon = { Icon(painterResource(R.drawable.audiofile), contentDescription = null) },
            title = { Text("Extract Audio") },
            text = {
                Column {
                    Text("Enter a name for your audio file:")
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = fileName,
                        onValueChange = { fileName = it },
                        label = { Text("File Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. Format Selection (Chips)
                    Text("Format:", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AudioFormat.entries.forEach { format ->
                            FilterChip(
                                selected = selectedFormat == format,
                                onClick = { selectedFormat = format },
                                label = { Text(format.displayName) },
                                leadingIcon = if (selectedFormat == format) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Preview Output Name
                    Text(
                        text = "Output: Music/${fileName}.${selectedFormat.extension}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (fileName.isNotBlank()) {
                            viewModel.checkAndStartExtraction(state.uri, fileName, selectedFormat)
                        }
                    }
                ) {
                    Text("Extract")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.resetState() }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (state is ExtractionState.DuplicateFound) {
        AlertDialog(
            onDismissRequest = { viewModel.resetState() },
            title = { Text("File Already Exists") },
            text = { Text("The file '${state.fileName}' already exists. Do you want to overwrite it?") },
            confirmButton = {
                TextButton(onClick = { viewModel.forceStartExtraction(state.uri, state.fileName, selectedFormat) }) {
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
        Button(
            onClick = {
                launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
            },
            enabled = state !is ExtractionState.Processing,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Icon(imageVector = ImageVector.vectorResource(R.drawable.videofile), contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (state is ExtractionState.Success) "Select Another Video" else "Select Video to Extract Audio")
        }

        Spacer(modifier = Modifier.height(32.dp))

        when (state) {
            is ExtractionState.Idle, is ExtractionState.Selected -> {
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
            is ExtractionState.DuplicateFound -> {}
        }
    }
}