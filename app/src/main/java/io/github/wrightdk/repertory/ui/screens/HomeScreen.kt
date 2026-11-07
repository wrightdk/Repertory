package io.github.wrightdk.repertory.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wrightdk.repertory.R
import io.github.wrightdk.repertory.appContainer
import io.github.wrightdk.repertory.ui.theme.spacing

@Composable
fun HomeScreen(
    onSearch: () -> Unit,
    onOpenFilmography: () -> Unit,
    onSettings: () -> Unit,
) {
    // Skeleton check that the Room database opens through the manual wiring.
    val context = LocalContext.current
    val countFlow = remember { context.appContainer.database.trackedPersonDao().observeCount() }
    val trackedCount by countFlow.collectAsStateWithLifecycle(initialValue = null)
    val hasEntries = (trackedCount ?: 0) > 0

    Scaffold(
        topBar = {
            RepertoryTopBar(
                // The wordmark is set in Newsreader, unlike other screen titles.
                title = { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleSmall) },
                onBack = null,
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.settings))
                    }
                },
            )
        },
        floatingActionButton = {
            if (hasEntries) {
                ExtendedFloatingActionButton(
                    onClick = onSearch,
                    icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                    text = { Text("Add person") },
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            if (hasEntries) {
                YourListPlaceholder(trackedCount ?: 0)
            } else {
                EmptyHome(onSearch = onSearch)
            }
            SkeletonLinks(onOpenFilmography = onOpenFilmography)
        }
    }
}

/** First launch, from design/home-firstlaunch-*.png. */
@Composable
private fun EmptyHome(onSearch: () -> Unit) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier = Modifier.padding(start = spacing.xl, end = spacing.xl, top = 72.dp),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        Text("Nobody on your list yet.", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Pick a director, actor, writer or composer and work through their films one at a time.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onSearch,
            modifier = Modifier
                .padding(top = spacing.sm)
                .heightIn(min = 48.dp),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        ) {
            Icon(painterResource(R.drawable.ic_search), contentDescription = null, Modifier.size(20.dp))
            Spacer(Modifier.size(spacing.sm))
            Text("Find someone")
        }
    }
}

@Composable
private fun YourListPlaceholder(count: Int) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier = Modifier.padding(start = spacing.gutter, end = spacing.gutter, top = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text("Your list", style = MaterialTheme.typography.displaySmall)
        Text(
            "$count tracked. The list itself isn't built yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Shortcuts through the skeleton, sitting where the design has starter suggestions. */
@Composable
private fun SkeletonLinks(onOpenFilmography: () -> Unit) {
    val spacing = MaterialTheme.spacing
    Column(
        // 16dp column gap + 40dp margin in the mockup.
        modifier = Modifier.padding(start = spacing.xl, end = spacing.xl, top = spacing.lg + spacing.xxxl),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        HorizontalDivider(Modifier.padding(bottom = spacing.gutter - spacing.md))
        Text(
            "Skeleton",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SuggestionChip(
            onClick = onOpenFilmography,
            label = { Text("Sample filmography", style = MaterialTheme.typography.bodyMedium) },
            border = SuggestionChipDefaults.suggestionChipBorder(
                enabled = true,
                borderColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}
