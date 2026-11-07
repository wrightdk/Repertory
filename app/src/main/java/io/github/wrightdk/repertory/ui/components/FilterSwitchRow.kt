package io.github.wrightdk.repertory.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.wrightdk.repertory.R
import io.github.wrightdk.repertory.data.FilmCategory

@get:StringRes
val FilmCategory.label: Int
    get() = when (this) {
        FilmCategory.SHORT -> R.string.filter_shorts
        FilmCategory.TV -> R.string.filter_tv
        FilmCategory.DOCUMENTARY -> R.string.filter_documentaries
        FilmCategory.SELF -> R.string.filter_self
        FilmCategory.MINOR_ROLE -> R.string.filter_minor_roles
    }

@get:StringRes
val FilmCategory.description: Int
    get() = when (this) {
        FilmCategory.SHORT -> R.string.filter_shorts_description
        FilmCategory.TV -> R.string.filter_tv_description
        FilmCategory.DOCUMENTARY -> R.string.filter_documentaries_description
        FilmCategory.SELF -> R.string.filter_self_description
        FilmCategory.MINOR_ROLE -> R.string.filter_minor_roles_description
    }

/**
 * A filter row from Settings and the "Show in this list" sheet: label, a
 * supporting line, and a switch meaning "show". The whole row toggles.
 */
@Composable
fun FilterSwitchRow(
    label: String,
    supporting: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    horizontalPadding: Dp,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            // The mockup's 56dp minimum is inside the padding, so rows are 76dp.
            .padding(horizontal = horizontalPadding, vertical = 10.dp)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = MaterialTheme.typography.bodyLarge.fontSize * 1.3f))
            Text(
                supporting,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = MaterialTheme.typography.bodySmall.fontSize * 1.3f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RepertorySwitch(checked = checked)
    }
}

/**
 * The mockup's switch: green track with a check in the thumb when on; when
 * off, an ink2 outline on sf2 with a small ink2 thumb. Not focusable itself,
 * as its row handles the toggle.
 */
@Composable
private fun RepertorySwitch(checked: Boolean) {
    val c = MaterialTheme.colorScheme
    Switch(
        checked = checked,
        onCheckedChange = null,
        thumbContent = if (checked) {
            { Icon(painterResource(R.drawable.ic_check), contentDescription = null, Modifier.size(16.dp)) }
        } else {
            null
        },
        colors = SwitchDefaults.colors(
            checkedThumbColor = c.onTertiary,
            checkedTrackColor = c.tertiary,
            checkedBorderColor = c.tertiary,
            checkedIconColor = c.tertiary,
            uncheckedThumbColor = c.onSurfaceVariant,
            uncheckedTrackColor = c.surfaceVariant,
            uncheckedBorderColor = c.onSurfaceVariant,
        ),
    )
}
