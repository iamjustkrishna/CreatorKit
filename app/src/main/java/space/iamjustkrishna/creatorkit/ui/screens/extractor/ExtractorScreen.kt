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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current

    // --- 1. The "Modern" Launcher (Photo Picker) ---
    // Best for Android 13+ and devices with Play Services
    val modernLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.onVideoSelected(it) }
    }

    // --- 2. The "Classic" Launcher (System File Picker) ---
    // Works on EVERYTHING (Android 5.0+, Emulators, etc.)
    val legacyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.onVideoSelected(it) }
    }

    // --- 3. The Robust Launch Logic ---
    fun launchVideoPicker() {
        try {
            // First, try the modern, permission-less picker
            modernLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
            )
        } catch (e: Exception) {
            // If it crashes (e.g. "ActivityNotFound" on Emulator),
            // silently fall back to the classic picker.
            legacyLauncher.launch("video/*")
        }
    }

    // --- 4. State & UI ---

    // Hold the format selection state here so it persists across dialog recompositions
    var selectedFormat by remember { mutableStateOf(AudioFormat.M4A) }

    // RENAME DIALOG (Triggered when a video is selected)
    if (state is ExtractionState.Selected) {
        // Initialize with the original file name
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

                    // Format Chips
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

    // DUPLICATE WARNING DIALOG
    if (state is ExtractionState.DuplicateFound) {
        // Note: We need to remember the format from the previous step or pass it in the state.
        // Assuming your DuplicateFound state now holds the format (as we discussed before).
        // If not, it uses the current 'selectedFormat' variable.
        AlertDialog(
            onDismissRequest = { viewModel.resetState() },
            title = { Text("File Already Exists") },
            text = { Text("The file '${state.fileName}' already exists. Do you want to overwrite it?") },
            confirmButton = {
                TextButton(onClick = {
                    // Pass the format cleanly to the force function
                    viewModel.forceStartExtraction(state.uri, state.fileName, selectedFormat)
                }) {
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

    // MAIN UI CONTENT
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()), // Add scrolling for small screens
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Spacer to push content down if you have a Glassy Top Bar
        Spacer(modifier = Modifier.height(60.dp))

        Button(
            onClick = { launchVideoPicker() }, // <--- Calls our robust helper
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