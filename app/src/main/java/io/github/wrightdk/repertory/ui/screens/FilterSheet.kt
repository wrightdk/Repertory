package io.github.wrightdk.repertory.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.wrightdk.repertory.R
import io.github.wrightdk.repertory.data.FilmCategory
import io.github.wrightdk.repertory.data.shows
import io.github.wrightdk.repertory.ui.components.FilterSwitchRow
import io.github.wrightdk.repertory.ui.components.label
import io.github.wrightdk.repertory.ui.theme.Scrim
import kotlinx.coroutines.launch

private val SheetGutter = 24.dp

/**
 * "Show in this list", from design/local-filter-*.png: this list's own
 * filters, each with how many of its films fall into that category. Changes
 * save as they're made; "Use defaults" (only when customised) drops the
 * list's own filters, and "Done" closes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    content: FilmographyContent,
    onSetShown: (FilmCategory, Boolean) -> Unit,
    onUseDefaults: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        scrimColor = Scrim.copy(alpha = 0.36f),
        dragHandle = { DragHandle() },
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            Text(
                stringResource(R.string.filter_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = SheetGutter, end = SheetGutter, bottom = 6.dp),
            )
            Text(
                stringResource(R.string.filter_sheet_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = SheetGutter, end = SheetGutter, bottom = 12.dp),
            )
            for (category in FilmCategory.entries) {
                val count = content.categoryCounts[category] ?: 0
                FilterSwitchRow(
                    label = stringResource(category.label),
                    supporting = if (count == 0) {
                        stringResource(R.string.filter_sheet_none)
                    } else {
                        pluralStringResource(R.plurals.filter_sheet_count, count, count)
                    },
                    checked = content.filters.shows(category),
                    onCheckedChange = { onSetShown(category, it) },
                    horizontalPadding = SheetGutter,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                val buttonText = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, lineHeight = 14.sp)
                if (content.filterSummary.custom) {
                    TextButton(
                        onClick = onUseDefaults,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier.height(40.dp),
                    ) { Text(stringResource(R.string.use_defaults), style = buttonText) }
                }
                Button(
                    onClick = close,
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    modifier = Modifier.height(40.dp),
                ) { Text(stringResource(R.string.done), style = buttonText) }
            }
        }
    }
}

@Composable
private fun DragHandle() {
    Box(
        Modifier
            .padding(top = 10.dp, bottom = 18.dp)
            .size(width = 32.dp, height = 4.dp)
            .alpha(0.5f)
            .background(MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(2.dp)),
    )
}
