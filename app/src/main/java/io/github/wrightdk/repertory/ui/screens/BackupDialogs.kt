package io.github.wrightdk.repertory.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.wrightdk.repertory.R
import io.github.wrightdk.repertory.data.backup.Backup
import io.github.wrightdk.repertory.data.backup.BackupError
import io.github.wrightdk.repertory.data.backup.RestoreMode
import io.github.wrightdk.repertory.ui.components.DialogBody
import io.github.wrightdk.repertory.ui.components.DialogButton
import io.github.wrightdk.repertory.ui.components.DialogTextButton
import io.github.wrightdk.repertory.ui.components.RepertoryDialog
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * "Restore this backup?": what the file holds and when it was made, then
 * Merge or Replace everything. Restore turns red for Replace, like Remove,
 * since it deletes what's on the phone.
 */
@Composable
fun RestoreBackupDialog(backup: Backup, onRestore: (RestoreMode) -> Unit, onDismiss: () -> Unit) {
    var mode by rememberSaveable { mutableStateOf(RestoreMode.MERGE) }
    val made = backup.exportedAt.atZone(ZoneId.systemDefault()).toLocalDate().format(DateFormat)
    RepertoryDialog(
        title = stringResource(R.string.restore_title),
        onDismiss = onDismiss,
        buttons = {
            DialogTextButton(stringResource(R.string.cancel), onClick = onDismiss)
            DialogButton(
                stringResource(R.string.restore_confirm),
                onClick = { onRestore(mode) },
                colors = if (mode == RestoreMode.REPLACE) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            )
        },
    ) {
        DialogBody(
            stringResource(
                R.string.restore_summary,
                made,
                pluralStringResource(R.plurals.backup_lists, backup.lists.size, backup.lists.size),
                pluralStringResource(R.plurals.backup_films_seen, backup.viewings.size, backup.viewings.size),
            ),
        )
        Column(Modifier.selectableGroup()) {
            ModeRow(
                label = stringResource(R.string.restore_merge),
                supporting = stringResource(R.string.restore_merge_body),
                selected = mode == RestoreMode.MERGE,
                onSelect = { mode = RestoreMode.MERGE },
            )
            ModeRow(
                label = stringResource(R.string.restore_replace),
                supporting = stringResource(R.string.restore_replace_body),
                selected = mode == RestoreMode.REPLACE,
                onSelect = { mode = RestoreMode.REPLACE },
            )
        }
    }
}

@Composable
private fun ModeRow(label: String, supporting: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 6.dp)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Why a file can't be restored, and that nothing has changed. */
@Composable
fun InvalidBackupDialog(error: BackupError, onDismiss: () -> Unit) {
    val reason = when (error) {
        BackupError.NotJson, BackupError.NotABackup -> R.string.restore_invalid_not_backup
        is BackupError.TooNew -> R.string.restore_invalid_too_new
        is BackupError.Damaged -> R.string.restore_invalid_damaged
        BackupError.Unreadable -> R.string.restore_invalid_unreadable
        BackupError.TooLarge -> R.string.restore_invalid_too_large
    }
    RepertoryDialog(
        title = stringResource(R.string.restore_invalid_title),
        onDismiss = onDismiss,
        buttons = { DialogButton(stringResource(R.string.ok), onClick = onDismiss) },
    ) {
        DialogBody(stringResource(reason) + " " + stringResource(R.string.restore_invalid_nothing_changed))
    }
}

/** As the filmography's "Seen 11 Mar 2022". */
private val DateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
