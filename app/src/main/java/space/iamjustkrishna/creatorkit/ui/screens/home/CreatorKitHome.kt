package space.iamjustkrishna.creatorkit.ui.screens.home
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import space.iamjustkrishna.creatorkit.R
import space.iamjustkrishna.creatorkit.data.model.MediaFile
import space.iamjustkrishna.creatorkit.ui.components.HistoryItem
import space.iamjustkrishna.creatorkit.ui.components.ToolCard
import space.iamjustkrishna.creatorkit.util.SimpleAudioPlayer

// 1. Define the Tool Data Class (if you haven't already)
data class Tool(
    val name: String,
    val icon: Int,
    val type: ToolType
)

enum class ToolType {
    AudioExtractor,
    VocalStudio
}

// 2. Define your static list of tools
val tools = listOf(
    Tool("Audio Extractor", R.drawable.audioextractor, ToolType.AudioExtractor),
    Tool("Vocal Studio", R.drawable.vocalstudio, ToolType.VocalStudio)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatorKitHome(
    modifier: Modifier = Modifier,
    processedFiles: List<MediaFile>,
    onToolClick: (ToolType) -> Unit,
    onDelete: (MediaFile) -> Unit, // Callback for deletion
    onShare: (MediaFile) -> Unit   // Callback for sharing
) {
    // --- State Management ---
    var fileToPlay by remember { mutableStateOf<MediaFile?>(null) }
    var showPlayerSheet by remember { mutableStateOf(false) }

    // State to track which file is being deleted (triggers the dialog)
    var fileToDelete by remember { mutableStateOf<MediaFile?>(null) }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        // --- Section 1: Tools ---
        item {
            Text(
                text = "Tools",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 16.dp)
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(tools) { tool ->
                    ToolCard(tool = tool, onClick = { onToolClick(tool.type) })
                }
            }
        }

        // --- Section 2: Recent Files Header ---
        item {
            Text(
                text = "Recently Processed",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp)
            )
        }

        // --- Section 3: The List with Swipe-to-Delete ---
        // We use 'key' so Compose knows exactly which item is being swiped
        items(processedFiles, key = { it.id }) { file ->

            // 1. Setup Swipe State
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    if (value == SwipeToDismissBoxValue.EndToStart) {
                        // User swiped left: Trigger Delete Dialog
                        fileToDelete = file
                        // Return false so it snaps back (we only remove it after confirmation)
                        false
                    } else {
                        false
                    }
                }
            )

            // 2. The Swipe Box
            SwipeToDismissBox(
                state = dismissState,
                enableDismissFromStartToEnd = false, // Disable right swipe
                backgroundContent = {
                    val color = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart)
                        MaterialTheme.colorScheme.errorContainer
                    else
                        Color.Transparent

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 6.dp) // Match Card padding
                            .background(color, RoundedCornerShape(12.dp))
                            .padding(end = 24.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            ) {
                // 3. The Content (HistoryItem)
                HistoryItem(
                    file = file,
                    onClick = {
                        fileToPlay = file
                        showPlayerSheet = true
                    },
                    onShareClick = { onShare(file) } // Connect Share Button
                )
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }

    // --- Delete Confirmation Dialog ---
    if (fileToDelete != null) {
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("Delete File?") },
            text = { Text("Are you sure you want to delete '${fileToDelete?.name}'?\nThis action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        fileToDelete?.let { onDelete(it) }
                        fileToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
                    Text("Cancel")
                }
            },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) }
        )
    }

    // --- The Player Bottom Sheet ---
    if (showPlayerSheet && fileToPlay != null) {
        ModalBottomSheet(
            onDismissRequest = {
                showPlayerSheet = false
                fileToPlay = null
            },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 48.dp)
            ) {
                Text(
                    text = fileToPlay!!.name,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = fileToPlay!!.formattedSize(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                // The Seekbar Player
                SimpleAudioPlayer(uri = fileToPlay!!.uri, toPlay = true)
            }
        }
    }
}