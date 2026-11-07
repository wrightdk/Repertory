package io.github.wrightdk.repertory.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.wrightdk.repertory.R
import io.github.wrightdk.repertory.ui.theme.spacing

/** App bar height in the mockups; Material's default is 64dp. */
val AppBarHeight = 56.dp

/** Top app bar used across screens: optional back arrow, title, actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepertoryTopBar(
    title: @Composable () -> Unit,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {},
    height: Dp = AppBarHeight,
    backDescription: String = stringResource(R.string.back),
) {
    TopAppBar(
        // Without a back arrow Material insets the title 16dp; the design uses the 20dp gutter.
        title = { Box(Modifier.padding(start = if (onBack == null) 4.dp else 0.dp)) { title() } },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = backDescription,
                    )
                }
            }
        },
        actions = actions,
        expandedHeight = height,
        // Action icons are ink in the design, not Material's onSurfaceVariant.
        colors = TopAppBarDefaults.topAppBarColors(
            actionIconContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

/** Shared frame for the skeleton screens: title bar, optional back arrow, a column of content. */
@Composable
fun PlaceholderScaffold(
    title: String,
    onBack: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        topBar = { RepertoryTopBar(title = { Text(title) }, onBack = onBack) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = MaterialTheme.spacing.gutter, vertical = MaterialTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
        ) {
            Text(
                text = "Placeholder screen",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}
