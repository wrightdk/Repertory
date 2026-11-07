package io.github.wrightdk.repertory.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import io.github.wrightdk.repertory.ui.theme.repertoryColors

/**
 * A person's portrait photo. Striped while loading; the person's initials on
 * the stripes when TMDB has no photo or it fails to load.
 */
@Composable
fun PersonPhoto(
    name: String,
    url: String?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.repertoryColors
    var failed by remember(url) { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(PhotoShape)
            .placeholderStripes(colors.placeholderStripe, colors.placeholderStripeAlt),
        contentAlignment = Alignment.Center,
    ) {
        if (url == null || failed) {
            Text(
                text = initials(name),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onState = { failed = it is AsyncImagePainter.State.Error },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Posters and photos in rows have 3dp corners in the mockup. */
private val PhotoShape = RoundedCornerShape(3.dp)

/** "Stanley Kubrick" -> "SK", "Madonna" -> "M", "Jean-Luc Godard" -> "JG". */
internal fun initials(name: String): String {
    val words = name.split(' ', ' ').map { w -> w.firstOrNull(Char::isLetterOrDigit) }.filterNotNull()
    val letters = when (words.size) {
        0 -> return ""
        1 -> listOf(words.first())
        else -> listOf(words.first(), words.last())
    }
    return letters.joinToString("") { it.uppercase() }
}
