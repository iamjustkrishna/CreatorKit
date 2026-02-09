package space.iamjustkrishna.creatorkit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import space.iamjustkrishna.creatorkit.ui.theme.FontPlayPhen

@Composable
fun CreatorKitAppBar(
    title: String,
    canNavigateBack: Boolean,
    navigateUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 1. The Container
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp) // Float it slightly
            .height(48.dp) // COMPACT HEIGHT (Adjust this to make it thinner/thicker)
            .clip(RoundedCornerShape(24.dp)) // ROUNDED CORNERS (The Pill Shape)
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0f)
            )
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 2. Back Button (Left)
            if (canNavigateBack) {
                IconButton(onClick = navigateUp) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(48.dp)) // Balance the layout if no back button
            }

            // 3. Title (Centered)
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontPlayPhen,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).wrapContentWidth(Alignment.CenterHorizontally)
            )

            // 4. Ghost Icon (Right) - Balances the Back Button so title stays perfectly centered
            Spacer(modifier = Modifier.width(48.dp))
        }
    }
}