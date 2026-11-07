package io.github.wrightdk.repertory.ui.screens

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.wrightdk.repertory.AppContainer
import io.github.wrightdk.repertory.data.backup.Backup
import io.github.wrightdk.repertory.data.backup.BackupCodec
import io.github.wrightdk.repertory.data.backup.BackupError
import io.github.wrightdk.repertory.data.backup.BackupReadResult
import io.github.wrightdk.repertory.data.backup.RestoreMode
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface BackupUiState {
    data object Idle : BackupUiState

    /** Writing a backup to the file the user chose. */
    data object Saving : BackupUiState

    /** Reading and checking the file the user chose to restore. */
    data object Reading : BackupUiState

    /** The file is a good backup; asking whether to restore it, and how. */
    data class Confirm(val backup: Backup) : BackupUiState

    /** The file can't be restored, and why. Nothing has changed. */
    data class Invalid(val error: BackupError) : BackupUiState

    /** Putting the backup into the app. */
    data object Restoring : BackupUiState
}

/** One-off results for Settings' snackbar. */
sealed interface BackupMessage {
    data class Saved(val lists: Int, val seen: Int) : BackupMessage
    data object SaveFailed : BackupMessage
    data object Restored : BackupMessage
    data object RestoreFailed : BackupMessage
}

/**
 * "Back up my data" and "Restore from backup" in Settings. The file is picked
 * with the Storage Access Framework, so no storage permission is needed. All
 * reading, writing and restoring runs off the main thread, and once started
 * carries on if the user leaves Settings.
 */
class BackupViewModel(
    private val export: suspend () -> Backup,
    private val restore: suspend (Backup, RestoreMode) -> Unit,
    private val openOutput: (Uri) -> OutputStream?,
    private val openInput: (Uri) -> InputStream?,
    /** Removes the file a failed save left behind. */
    private val deleteDocument: (Uri) -> Unit,
    private val today: () -> LocalDate = LocalDate::now,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _state = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    private val _messages = Channel<BackupMessage>(Channel.BUFFERED)
    val messages: Flow<BackupMessage> = _messages.receiveAsFlow()

    /** The date for the suggested file name, "repertory-backup-2026-11-07.json". */
    fun fileDate(): String = today().toString()

    /** Writes a backup to [uri], the new file from the save picker; null if the user cancelled. */
    fun save(uri: Uri?) {
        if (uri == null || _state.value != BackupUiState.Idle) return
        _state.value = BackupUiState.Saving
        viewModelScope.launch {
            val message = withContext(io + NonCancellable) {
                try {
                    val backup = export()
                    val bytes = BackupCodec.encode(backup).toByteArray(Charsets.UTF_8)
                    (openOutput(uri) ?: error("No output stream")).use { it.write(bytes) }
                    BackupMessage.Saved(backup.lists.size, backup.viewings.size)
                } catch (_: Exception) {
                    runCatching { deleteDocument(uri) }
                    BackupMessage.SaveFailed
                }
            }
            _state.value = BackupUiState.Idle
            _messages.trySend(message)
        }
    }

    /** Reads and checks the file at [uri], from the open picker; null if the user cancelled. */
    fun open(uri: Uri?) {
        if (uri == null || _state.value != BackupUiState.Idle) return
        _state.value = BackupUiState.Reading
        viewModelScope.launch {
            val result = withContext(io) {
                try {
                    openInput(uri)?.use(BackupCodec::read) ?: BackupReadResult.Invalid(BackupError.Unreadable)
                } catch (_: Exception) {
                    // IOException, or SecurityException if access to the file has gone.
                    BackupReadResult.Invalid(BackupError.Unreadable)
                }
            }
            _state.value = when (result) {
                is BackupReadResult.Valid -> BackupUiState.Confirm(result.backup)
                is BackupReadResult.Invalid -> BackupUiState.Invalid(result.error)
            }
        }
    }

    /** Restores the backup being confirmed. */
    fun confirm(mode: RestoreMode) {
        val backup = (_state.value as? BackupUiState.Confirm)?.backup ?: return
        _state.value = BackupUiState.Restoring
        viewModelScope.launch {
            val message = withContext(io + NonCancellable) {
                try {
                    restore(backup, mode)
                    BackupMessage.Restored
                } catch (_: Exception) {
                    // The restore is one transaction, so nothing has changed.
                    BackupMessage.RestoreFailed
                }
            }
            _state.value = BackupUiState.Idle
            _messages.trySend(message)
        }
    }

    /** Closes the confirmation or the error without restoring. */
    fun dismiss() {
        val s = _state.value
        if (s is BackupUiState.Confirm || s is BackupUiState.Invalid) _state.value = BackupUiState.Idle
    }

    companion object {
        fun factory(container: AppContainer, contentResolver: ContentResolver): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                BackupViewModel(
                    export = container.backupManager::export,
                    restore = container::restoreBackup,
                    openOutput = { contentResolver.openOutputStream(it) },
                    openInput = { contentResolver.openInputStream(it) },
                    deleteDocument = { DocumentsContract.deleteDocument(contentResolver, it) },
                )
            }
        }
    }
}
