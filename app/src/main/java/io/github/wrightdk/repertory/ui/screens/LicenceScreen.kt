package io.github.wrightdk.repertory.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.wrightdk.repertory.R
import io.github.wrightdk.repertory.ui.theme.spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Which licence text About opens. Each file is bundled at build time from the repo (see `copyLicenceTexts`). */
enum class LicenceKind(@StringRes val title: Int, val sections: List<LicenceSection>) {
    APP(R.string.licence_title_app, listOf(LicenceSection(heading = null, asset = "licences/gpl-3.0.txt"))),
    FONTS(
        R.string.licence_title_fonts,
        listOf(
            LicenceSection(heading = "Newsreader", asset = "licences/ofl-newsreader.txt"),
            LicenceSection(heading = "IBM Plex Sans", asset = "licences/ofl-ibm-plex-sans.txt"),
        ),
    ),
}

data class LicenceSection(val heading: String?, val asset: String)

private sealed interface LicenceBlock {
    data class Heading(val text: String) : LicenceBlock
    data class Paragraph(val text: String) : LicenceBlock
}

/** The full text of a licence, reflowed to the screen width. */
@Composable
fun LicenceScreen(kind: LicenceKind, onBack: () -> Unit) {
    val assets = LocalContext.current.assets
    val blocks by produceState(emptyList<LicenceBlock>(), kind) {
        value = withContext(Dispatchers.IO) {
            kind.sections.flatMap { section ->
                val text = assets.open(section.asset).bufferedReader().use { it.readText() }
                listOfNotNull(section.heading?.let(LicenceBlock::Heading)) +
                    reflowLicenceText(text).map(LicenceBlock::Paragraph)
            }
        }
    }

    Scaffold(
        topBar = { RepertoryTopBar(title = { Text(stringResource(kind.title)) }, onBack = onBack) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(
                start = MaterialTheme.spacing.gutter,
                end = MaterialTheme.spacing.gutter,
                top = innerPadding.calculateTopPadding() + MaterialTheme.spacing.sm,
                bottom = innerPadding.calculateBottomPadding() + MaterialTheme.spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
        ) {
            itemsIndexed(blocks) { index, block ->
                when (block) {
                    is LicenceBlock.Heading -> Text(
                        block.text,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(top = if (index == 0) 0.dp else MaterialTheme.spacing.xl),
                    )
                    is LicenceBlock.Paragraph -> Text(
                        block.text,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Splits a plain-text licence into paragraphs at blank lines and joins each
 * paragraph's hard-wrapped lines, so it wraps to the screen instead of at 72
 * columns. All-caps headings ("PREAMBLE") stand alone; rules of dashes go.
 */
internal fun reflowLicenceText(text: String): List<String> {
    val paragraphs = mutableListOf<String>()
    val current = mutableListOf<String>()
    fun flush() {
        if (current.isNotEmpty()) paragraphs += current.joinToString(" ").replace(Regex("\\s+"), " ")
        current.clear()
    }
    for (line in text.lines().map { it.trim() }) {
        when {
            line.isEmpty() -> flush()
            line.length >= 3 && line.all { it == '-' } -> flush()
            line.isHeading() -> {
                flush()
                current += line
                flush()
            }
            else -> current += line
        }
    }
    flush()
    return paragraphs
}

private fun String.isHeading(): Boolean = length < 60 && any { it.isLetter() } && none { it.isLowerCase() }
