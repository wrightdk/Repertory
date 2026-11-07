package io.github.wrightdk.repertory.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import io.github.wrightdk.repertory.ui.theme.Scrim
import io.github.wrightdk.repertory.ui.theme.spacing

/**
 * The app's dialog, from design/remove-person-*: Newsreader title, body,
 * buttons on the right, over the mockup's warm scrim rather than the
 * platform's black dim. Lifts above the keyboard when the body has a field.
 */
@Composable
fun RepertoryDialog(
    title: String,
    onDismiss: () -> Unit,
    buttons: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // The window is edge to edge, so it also sets the status bar icons: keep them as the screen has them.
        val view = LocalView.current
        val window = (view.parent as? DialogWindowProvider)?.window
        val lightBars = MaterialTheme.colorScheme.surface.luminance() > 0.5f
        SideEffect {
            window?.setDimAmount(0f)
            window?.let { WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = lightBars }
        }
        Box(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Scrim.copy(alpha = DialogScrimAlpha))
                    .clickable(interactionSource = null, indication = null, onClick = onDismiss)
                    .clearAndSetSemantics {},
            )
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .imePadding()
                    .padding(horizontal = 28.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(start = MaterialTheme.spacing.xl, end = MaterialTheme.spacing.xl, top = MaterialTheme.spacing.xl, bottom = MaterialTheme.spacing.lg)
                    .semantics { paneTitle = title },
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg),
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                content()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = MaterialTheme.spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    content = buttons,
                )
            }
        }
    }
}

/** Dialog body copy: 15sp prose in ink2. */
@Composable
fun DialogBody(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.5.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The dialog's quiet button (Cancel). */
@Composable
fun DialogTextButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.lg),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        modifier = Modifier.height(40.dp),
    ) { Text(text, style = DialogButtonText) }
}

/** The dialog's main button (Remove, Save). */
@Composable
fun DialogButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 20.dp),
        colors = colors,
        modifier = Modifier.height(40.dp),
    ) { Text(text, style = DialogButtonText) }
}

private val DialogButtonText: TextStyle
    @Composable get() = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, lineHeight = 14.sp)

private const val DialogScrimAlpha = 0.4f
