package io.github.wrightdk.repertory.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The mockup's `border: Npx dashed` for unreleased films, drawn inside the
 * bounds like a CSS border. Dashes are spaced evenly so the pattern closes
 * without a stub where it meets itself.
 *
 * @param cornerRadius outer corner radius; half the size or more draws a circle.
 */
fun Modifier.dashedOutline(
    color: Color,
    width: Dp,
    cornerRadius: Dp,
    dash: Dp = 3.dp,
    gap: Dp = 2.dp,
): Modifier = drawBehind {
    val strokePx = width.toPx()
    val inset = strokePx / 2
    val w = size.width - strokePx
    val h = size.height - strokePx
    val radius = (cornerRadius.toPx() - inset).coerceIn(0f, minOf(w, h) / 2)
    val perimeter = 2 * (w + h) - (8 - 2 * PI.toFloat()) * radius
    val period = perimeter / max(1, (perimeter / (dash + gap).toPx()).roundToInt())
    val dashPx = period * dash.value / (dash + gap).value
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(w, h),
        cornerRadius = CornerRadius(radius),
        style = Stroke(width = strokePx, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashPx, period - dashPx))),
    )
}
