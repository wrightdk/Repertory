package io.github.wrightdk.repertory.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.wrightdk.repertory.R
import io.github.wrightdk.repertory.appContainer
import io.github.wrightdk.repertory.data.remote.TmdbError
import io.github.wrightdk.repertory.ui.components.PersonPhoto
import io.github.wrightdk.repertory.ui.components.skeletonPulse
import io.github.wrightdk.repertory.ui.theme.spacing

/** Add to your list: choose which roles to track, from design/add-people-*.png. */
@Composable
fun AddPersonScreen(
    personId: Int,
    onBack: () -> Unit,
    onAdded: (trackedPersonId: Long) -> Unit,
    onOpenApiKeySetting: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: AddPersonViewModel = viewModel(
        key = "add-person-$personId",
        factory = AddPersonViewModel.factory(context.appContainer, personId),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val addedListId by viewModel.addedListId.collectAsStateWithLifecycle()
    val currentOnAdded by rememberUpdatedState(onAdded)
    LaunchedEffect(addedListId) { addedListId?.let(currentOnAdded) }

    AddPersonScreen(
        state = state,
        onBack = onBack,
        onToggle = viewModel::toggle,
        onAdd = viewModel::add,
        onRetry = viewModel::retry,
        onOpenApiKeySetting = onOpenApiKeySetting,
    )
}

@Composable
private fun AddPersonScreen(
    state: AddPersonUiState,
    onBack: () -> Unit,
    onToggle: (String) -> Unit,
    onAdd: () -> Unit,
    onRetry: () -> Unit,
    onOpenApiKeySetting: () -> Unit,
) {
    val content = state.content
    Scaffold(
        topBar = {
            RepertoryTopBar(
                title = { Text(stringResource(R.string.add_person_title)) },
                onBack = onBack,
                height = 64.dp,
            )
        },
        bottomBar = {
            if (content is AddPersonContent.Loaded && content.roles.isNotEmpty()) {
                AddBar(count = content.selectedCount, saving = state.saving, onAdd = onAdd)
            }
        },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            when (content) {
                AddPersonContent.Loading -> Loading()
                is AddPersonContent.Error -> LoadError(content.error, onRetry, onOpenApiKeySetting)
                is AddPersonContent.Loaded -> Loaded(content, onToggle)
            }
        }
    }
}

@Composable
private fun Loaded(content: AddPersonContent.Loaded, onToggle: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "header") { Header(content.header) }
        if (content.roles.isEmpty()) {
            item(key = "empty") {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Message(
                    title = stringResource(R.string.add_person_no_credits_title),
                    body = stringResource(R.string.add_person_no_credits_body),
                )
            }
        } else {
            item(key = "track-as") { TrackAsLabel() }
            items(content.roles, key = { it.department }) { role ->
                RoleRowItem(role, onToggle = { onToggle(role.department) })
            }
        }
    }
}

/** Photo, name and life dates, bottom-aligned. */
@Composable
private fun Header(header: AddPersonHeader) {
    Row(
        modifier = Modifier.padding(
            start = MaterialTheme.spacing.gutter,
            end = MaterialTheme.spacing.gutter,
            top = MaterialTheme.spacing.sm,
            bottom = MaterialTheme.spacing.xl,
        ),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        PersonPhoto(name = header.name, url = header.photoUrl, modifier = Modifier.size(PhotoWidth, PhotoHeight))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(header.name, style = NameStyle)
            if (header.lifeDates != null) {
                Text(
                    header.lifeDates,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 18.2.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Hairline, "Track as" and the line explaining that each role is its own list. */
@Composable
private fun TrackAsLabel() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Column(
        modifier = Modifier.padding(
            start = MaterialTheme.spacing.gutter,
            end = MaterialTheme.spacing.gutter,
            top = MaterialTheme.spacing.gutter,
            bottom = MaterialTheme.spacing.sm,
        ),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(R.string.track_as),
            style = MaterialTheme.typography.labelMedium.copy(lineHeight = 15.6.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.track_as_body),
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RoleRowItem(role: RoleRow, onToggle: () -> Unit) {
    val alreadyOnList = stringResource(R.string.already_on_list)
    val rowModifier = if (role.tracked) {
        Modifier.semantics(mergeDescendants = true) { stateDescription = alreadyOnList }
    } else {
        Modifier.toggleable(value = role.checked, role = Role.Checkbox, onValueChange = { onToggle() })
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(rowModifier)
            .padding(horizontal = MaterialTheme.spacing.gutter, vertical = 10.dp)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        when {
            role.tracked -> Icon(
                painter = painterResource(R.drawable.ic_bookmark_added),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(BoxSize),
            )
            else -> TickBox(checked = role.checked)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                role.department,
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 20.8.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (role.tracked) {
                    alreadyOnList
                } else {
                    pluralStringResource(R.plurals.credits_as, role.creditCount, role.creditCount, role.jobLabel)
                },
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The mockup's checkbox: a 20dp square, green with a tick when checked, an ink2 outline when not. */
@Composable
private fun TickBox(checked: Boolean) {
    val shape = RoundedCornerShape(3.dp)
    if (checked) {
        Box(
            Modifier
                .size(BoxSize)
                .background(MaterialTheme.colorScheme.tertiary, shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
    } else {
        Box(
            Modifier
                .size(BoxSize)
                .border(2.dp, MaterialTheme.colorScheme.onSurfaceVariant, shape),
        )
    }
}

/** Hairline over a full-width pill: "Add N lists", or a disabled "Choose a role". */
@Composable
private fun AddBar(count: Int, saving: Boolean, onAdd: () -> Unit) {
    Column(Modifier.background(MaterialTheme.colorScheme.surface).navigationBarsPadding()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Button(
            onClick = { if (!saving) onAdd() },
            enabled = count > 0,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = MaterialTheme.spacing.gutter,
                    end = MaterialTheme.spacing.gutter,
                    top = MaterialTheme.spacing.md,
                    bottom = MaterialTheme.spacing.lg,
                )
                .height(52.dp),
        ) {
            Text(
                text = if (count > 0) {
                    pluralStringResource(R.plurals.add_lists, count, count)
                } else {
                    stringResource(R.string.choose_a_role)
                },
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** First fetch: the header and a few rows as skeletons. */
@Composable
private fun Loading() {
    val block = MaterialTheme.colorScheme.surfaceVariant
    Column(Modifier.fillMaxSize().skeletonPulse()) {
        Row(
            modifier = Modifier.padding(
                start = MaterialTheme.spacing.gutter,
                end = MaterialTheme.spacing.gutter,
                top = MaterialTheme.spacing.sm,
                bottom = MaterialTheme.spacing.xl,
            ),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(Modifier.size(PhotoWidth, PhotoHeight).background(block, RoundedCornerShape(3.dp)))
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
                Box(Modifier.size(width = 200.dp, height = 26.dp).background(block, SkeletonBarShape))
                Box(Modifier.size(width = 80.dp, height = 12.dp).background(block, SkeletonBarShape))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(MaterialTheme.spacing.gutter))
        repeat(SKELETON_ROWS) {
            Row(
                modifier = Modifier
                    .padding(horizontal = MaterialTheme.spacing.gutter, vertical = 10.dp)
                    .heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Box(Modifier.size(BoxSize).background(block, RoundedCornerShape(3.dp)))
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                    Box(Modifier.size(width = 96.dp, height = 14.dp).background(block, SkeletonBarShape))
                    Box(Modifier.size(width = 150.dp, height = 10.dp).background(block, SkeletonBarShape))
                }
            }
        }
    }
}

@Composable
private fun LoadError(error: TmdbError, onRetry: () -> Unit, onOpenApiKeySetting: () -> Unit) {
    val (title, body) = commonTmdbErrorCopy(error) ?: when (error) {
        TmdbError.NotFound -> R.string.add_person_error_missing_title to R.string.add_person_error_missing_body
        else -> R.string.add_person_error_other_title to R.string.add_person_error_other_body
    }
    Message(title = stringResource(title), body = stringResource(body)) {
        // Nothing to retry once TMDB has dropped the person.
        TmdbErrorActions(error, onRetry.takeIf { error != TmdbError.NotFound }, onOpenApiKeySetting)
    }
}

// Mockup values that fall between the theme's steps.
private val NameStyle: TextStyle
    @Composable get() = MaterialTheme.typography.headlineMedium.copy(lineHeight = 32.4.sp)

private val PhotoWidth = 88.dp
private val PhotoHeight = 112.dp
private val BoxSize = 20.dp
private val SkeletonBarShape = RoundedCornerShape(2.dp)
private const val SKELETON_ROWS = 4
