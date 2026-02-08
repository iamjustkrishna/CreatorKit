package space.iamjustkrishna.creatorkit

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import space.iamjustkrishna.creatorkit.data.model.MediaFile
import space.iamjustkrishna.creatorkit.ui.components.CreatorKitAppBar
import space.iamjustkrishna.creatorkit.ui.screens.extractor.ExtractorScreen
import space.iamjustkrishna.creatorkit.ui.screens.home.CreatorKitHome
import space.iamjustkrishna.creatorkit.ui.screens.home.HomeViewModel
import space.iamjustkrishna.creatorkit.ui.screens.home.ToolType
import space.iamjustkrishna.creatorkit.ui.screens.vocalStudio.VocalStudioScreen
import space.iamjustkrishna.creatorkit.ui.theme.CreatorKitTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CreatorKitTheme {
                val navController = rememberNavController()
                val context = LocalContext.current

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                // 2. Setup Home ViewModel (fetches recent files)
                val homeViewModel: HomeViewModel = viewModel()

                // 3. Permission Handling (Essential for Android 13+)
                RequestStoragePermissions {
                    homeViewModel.loadRecentFiles()
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // 4. The Navigation Graph

                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)){
                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        // --- HOME SCREEN ---
                        composable("home") {

                            LaunchedEffect(Unit){
                                homeViewModel.loadRecentFiles()
                            }
                            val recentFiles by homeViewModel.recentFiles.collectAsState()

                            CreatorKitHome(
                                processedFiles = recentFiles,
                                onToolClick = { toolType ->
                                    when (toolType) {
                                        ToolType.AudioExtractor -> navController.navigate("extractor")
                                        ToolType.VocalStudio -> navController.navigate("vocal_studio")
                                    }
                                },
                                onShare = { file -> shareAudio(context, file)},
                                onDelete = {file -> homeViewModel.deleteFile(file)}
                            )
                        }

                        // --- TOOL 1: AUDIO EXTRACTOR ---
                        composable("extractor") {
                            // Assumes you created ExtractorScreen earlier
                            ExtractorScreen(
                                onBackClick = {navController.popBackStack()}
                            )
                        }

                        // --- TOOL 2: VOCAL STUDIO ---
                        composable("vocal_studio") {
                            VocalStudioScreen()
                        }
                    }

                    CreatorKitAppBar(
                        title = getScreenTitle(currentRoute),
                        canNavigateBack = navController.previousBackStackEntry != null,
                        navigateUp = { navController.navigateUp() },
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                    }
                }
            }
            }
        }
    }

fun shareAudio(context: Context, file: MediaFile) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "audio/*"
        putExtra(Intent.EXTRA_STREAM, file.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share Audio via"))
}


fun getScreenTitle(route: String?): String {
    return when (route) {
        "home" -> "CreatorKit"
        "extractor" -> "Audio Extract"
        "vocal_studio" -> "Vocal Studio"
        else -> "CreatorKit"
    }
}

@Composable
fun RequestStoragePermissions(onPermissionGranted: () -> Unit) {
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allGranted = result.values.all { it }
        if (allGranted) {
            onPermissionGranted()
        }
    }

    LaunchedEffect(Unit) {
        launcher.launch(permissions)
    }
}

