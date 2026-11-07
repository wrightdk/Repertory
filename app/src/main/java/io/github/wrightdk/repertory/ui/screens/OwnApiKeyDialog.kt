package io.github.wrightdk.repertory.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.wrightdk.repertory.R
import io.github.wrightdk.repertory.data.remote.TmdbError
import io.github.wrightdk.repertory.data.remote.TmdbResult
import io.github.wrightdk.repertory.ui.components.DialogBody
import io.github.wrightdk.repertory.ui.components.DialogButton
import io.github.wrightdk.repertory.ui.components.DialogTextButton
import io.github.wrightdk.repertory.ui.components.RepertoryDialog
import kotlinx.coroutines.launch

/**
 * Enter, change or remove the user's own TMDB key. The key is checked with
 * TMDB before it's saved, so a typo can't silently break every screen.
 */
@Composable
internal fun OwnApiKeyDialog(
    current: String?,
    check: suspend (String) -> TmdbResult<Unit>,
    onSave: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(current.orEmpty()) }
    var error by rememberSaveable { mutableStateOf<Int?>(null) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (current == null) focus.requestFocus() }
    // The field is disabled while checking, which drops focus: give it back so a rejected key can be fixed.
    LaunchedEffect(checking) { if (!checking && error != null) focus.requestFocus() }

    val canSave = !checking && text.isNotBlank() && text.trim() != current
    val save = save@{
        if (!canSave) return@save
        val key = text.trim()
        error = apiKeyFormatError(key)
        if (error != null) return@save
        checking = true
        scope.launch {
            when (val result = check(key)) {
                is TmdbResult.Success -> onSave(key)
                is TmdbResult.Failure -> error = when (result.error) {
                    TmdbError.InvalidApiKey -> R.string.own_key_error_rejected
                    TmdbError.NoNetwork -> R.string.own_key_error_offline
                    else -> R.string.own_key_error_other
                }
            }
            checking = false
        }
    }

    RepertoryDialog(
        title = stringResource(R.string.own_key_dialog_title),
        onDismiss = onDismiss,
        buttons = {
            if (current != null) {
                DialogTextButton(stringResource(R.string.own_key_remove), onClick = onRemove, enabled = !checking)
                Spacer(Modifier.weight(1f))
            }
            DialogTextButton(stringResource(R.string.cancel), onClick = onDismiss)
            DialogButton(
                stringResource(R.string.own_key_save),
                onClick = save,
                enabled = canSave,
            )
        },
    ) {
        DialogBody(stringResource(R.string.own_key_dialog_body))
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                error = null
            },
            label = { Text(stringResource(R.string.own_key_field_label)) },
            singleLine = true,
            enabled = !checking,
            isError = error != null,
            supportingText = when {
                checking -> { { Text(stringResource(R.string.own_key_checking)) } }
                error != null -> { { Text(stringResource(error!!)) } }
                else -> null
            },
            trailingIcon = if (checking) {
                { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { save() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
    }
}

/**
 * Catches the likely mistakes before asking TMDB: the app sends a v3 API key
 * (32 letters and digits), not the much longer v4 read access token that TMDB
 * shows on the same page.
 */
@StringRes
internal fun apiKeyFormatError(key: String): Int? = when {
    key.startsWith("eyJ") -> R.string.own_key_error_token
    !ApiKeyPattern.matches(key) -> R.string.own_key_error_format
    else -> null
}

private val ApiKeyPattern = Regex("[A-Za-z0-9]{32}")
