package space.iamjustkrishna.creatorkit.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryDark, // Used for "Active" cards
    onPrimaryContainer = TextWhite,

    secondary = SecondaryTeal,
    onSecondary = OnSecondary,

    background = DarkBackground,
    onBackground = TextWhite,

    surface = DarkSurface,
    onSurface = TextWhite,

    surfaceVariant = DarkSurfaceVariant, // Your "Card" background
    onSurfaceVariant = TextGray,

    error = Color(0xFFFF5252)
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = OnPrimary,
    primaryContainer = Color(0xFFE0E0FF),
    onPrimaryContainer = PrimaryDark,

    background = LightBackground,
    onBackground = TextBlack,

    surface = LightSurface,
    onSurface = TextBlack,

    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF444444)
)

@Composable
fun CreatorKitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // REMOVED: dynamicColor argument. We don't want it anymore.
    content: @Composable () -> Unit
) {
    // 3. Pick the scheme based solely on Dark/Light mode
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    // 4. Set the Status Bar Color to match
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Make status bar match background for seamless look
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()

            // Set icons to light or dark
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}