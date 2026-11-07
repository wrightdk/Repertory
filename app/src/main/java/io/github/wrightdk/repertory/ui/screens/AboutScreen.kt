package io.github.wrightdk.repertory.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.wrightdk.repertory.BuildConfig
import io.github.wrightdk.repertory.R
import io.github.wrightdk.repertory.ui.theme.spacing

private const val SOURCE_URL = "https://github.com/wrightdk/Repertory"

// ListItem pads 16dp and can't be changed; the design aligns rows to the 20dp gutter.
private val ListItemGutterNudge = 4.dp

/** From design/about-*.png. The app icon and TMDB logo are still to come. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val spacing = MaterialTheme.spacing
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = { RepertoryTopBar(title = { Text("About") }, onBack = onBack) },
    ) { innerPadding ->
        BoxWithConstraints(Modifier.padding(innerPadding).fillMaxSize()) {
            // Fills the screen when short so the TMDB credit sits at the bottom; scrolls when not.
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight),
            ) {
                Column(
                    modifier = Modifier.padding(
                        start = spacing.gutter,
                        end = spacing.gutter,
                        top = spacing.xl,
                        bottom = 28.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
                    Text(
                        "Version ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "A notebook for working through filmographies. No accounts, no ads, no tracking. " +
                            "Your list stays on this phone.",
                        modifier = Modifier.padding(top = spacing.sm),
                        // 15sp prose sits between bodyMedium and bodyLarge in the mockup.
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 23.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
                ListItem(
                    leadingContent = { Icon(painterResource(R.drawable.ic_balance), contentDescription = null, Modifier.padding(start = ListItemGutterNudge)) },
                    headlineContent = { Text("Licence") },
                    supportingContent = { Text("GNU General Public License v3.0") },
                )
                ListItem(
                    modifier = Modifier.clickable { uriHandler.openUri(SOURCE_URL) },
                    leadingContent = { Icon(painterResource(R.drawable.ic_code), contentDescription = null, Modifier.padding(start = ListItemGutterNudge)) },
                    headlineContent = { Text("Source code") },
                    supportingContent = { Text(SOURCE_URL.removePrefix("https://")) },
                    trailingContent = {
                        Icon(
                            painterResource(R.drawable.ic_open_in_new),
                            contentDescription = stringResource(R.string.open_in_browser),
                            modifier = Modifier.padding(end = ListItemGutterNudge),
                        )
                    },
                )
                Spacer(Modifier.weight(1f))
                Column(
                    modifier = Modifier.padding(spacing.gutter),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HorizontalDivider(Modifier.padding(bottom = spacing.xs))
                    Text(
                        "Film data",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "This product uses the TMDB API but is not endorsed or certified by TMDB.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
