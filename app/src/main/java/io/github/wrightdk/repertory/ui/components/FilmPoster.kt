package io.github.wrightdk.repertory.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.wrightdk.repertory.ui.theme.repertoryColors

/** A film poster. Striped while loading, and where TMDB has none or it fails to load. */
@Composable
fun FilmPoster(url: String?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.repertoryColors
    Box(
        modifier
            .clip(PosterShape)
            .placeholderStripes(colors.placeholderStripe, colors.placeholderStripeAlt),
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private val PosterShape = RoundedCornerShape(3.dp)
