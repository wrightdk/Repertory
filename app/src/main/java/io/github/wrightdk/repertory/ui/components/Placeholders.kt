package io.github.wrightdk.repertory.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt

/**
 * Diagonal stripes drawn where a poster or photo is missing or still loading:
 * the mockup's `repeating-linear-gradient(135deg, ph1 0 5px, ph2 5px 10px)`.
 */
fun Modifier.placeholderStripes(stripe: Color, stripeAlt: Color, stripeWidth: Dp = 5.dp): Modifier =
    drawWithCache {
        // One period (two stripes) measured along the 135° axis.
        val d = stripeWidth.toPx() * sqrt(2f)
        val brush = Brush.linearGradient(
            0f to stripe, 0.5f to stripe, 0.5f to stripeAlt, 1f to stripeAlt,
            start = Offset.Zero,
            end = Offset(d, d),
            tileMode = TileMode.Repeated,
        )
        onDrawBehind { drawRect(brush) }
    }

/** The mockup's skeleton pulse: opacity .55 ↔ 1 over 1.4s. */
@Composable
fun Modifier.skeletonPulse(): Modifier {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    return graphicsLayer { this.alpha = alpha }
}
