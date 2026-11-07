package io.github.wrightdk.repertory.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wrightdk.repertory.R
import io.github.wrightdk.repertory.appContainer
import io.github.wrightdk.repertory.data.remote.TmdbError
import io.github.wrightdk.repertory.ui.theme.spacing

/*
 * What every TMDB screen says when the data service turns the app away: a
 * rejected key (401) or rate limiting that outlasted the retries (429). Both
 * point to Settings' own-key option, and say "your key" once one is set.
 */

/** Whether the user's own TMDB key from Settings is the one in use. */
@Composable
internal fun usingOwnTmdbKey(): Boolean {
    val key by LocalContext.current.appContainer.settings.ownTmdbApiKey.collectAsStateWithLifecycle()
    return key != null
}

/** The own-key setting can fix this one. */
internal val TmdbError.pointsToApiKeySetting: Boolean
    get() = this == TmdbError.InvalidApiKey || this == TmdbError.RateLimited

/** Title and body for the failures every screen words the same way; null for the rest, which each screen words itself. */
@Composable
internal fun commonTmdbErrorCopy(error: TmdbError): Pair<Int, Int>? {
    val own = usingOwnTmdbKey()
    return when (error) {
        TmdbError.NoNetwork -> R.string.search_error_offline_title to R.string.search_error_offline_body
        TmdbError.InvalidApiKey -> R.string.search_error_key_title to keyErrorBody(error, own)
        TmdbError.RateLimited -> R.string.search_error_busy_title to keyErrorBody(error, own)
        else -> null
    }
}

/** The body line alone, for places with room for one line only (the filmography's details status). */
@StringRes
internal fun keyErrorBody(error: TmdbError, usingOwnKey: Boolean): Int = when (error) {
    TmdbError.InvalidApiKey -> if (usingOwnKey) R.string.tmdb_error_own_key_body else R.string.search_error_key_body
    else -> if (usingOwnKey) R.string.tmdb_error_busy_own_key_body else R.string.search_error_busy_body
}

/** "Use your own key", or "Change your key" once one is set: opens Settings with the key dialog up. */
@Composable
internal fun apiKeyActionLabel(): String =
    stringResource(if (usingOwnTmdbKey()) R.string.tmdb_error_change_key else R.string.tmdb_error_use_own_key)

/** Under a full-screen error: "Try again" (when [onRetry] is given) and, for key trouble, the way to the setting. */
@Composable
internal fun TmdbErrorActions(error: TmdbError, onRetry: (() -> Unit)?, onOpenApiKeySetting: () -> Unit) {
    FlowRow(
        modifier = Modifier.padding(top = MaterialTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
    ) {
        if (onRetry != null) {
            OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.try_again)) }
        }
        if (error.pointsToApiKeySetting) {
            OutlinedButton(onClick = onOpenApiKeySetting) { Text(apiKeyActionLabel()) }
        }
    }
}
