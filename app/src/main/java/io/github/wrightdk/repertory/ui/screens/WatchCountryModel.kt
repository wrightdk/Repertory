package io.github.wrightdk.repertory.ui.screens

import io.github.wrightdk.repertory.data.local.entity.WatchRegion
import io.github.wrightdk.repertory.data.regionDisplayName
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

// The country picker's list. Pure, so sorting and search can be tested without a screen.

data class CountryOption(val code: String, val name: String)

/**
 * Every country in [regions] by its name in [locale], in that language's
 * alphabetical order, keeping those whose name, English name or code matches
 * [query] (ignoring case and accents, so "cote" finds Côte d'Ivoire).
 */
fun countryOptions(regions: List<WatchRegion>, query: String, locale: Locale): List<CountryOption> {
    val needle = fold(query.trim())
    val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    return regions
        .map { it to CountryOption(it.code, regionDisplayName(it.code, locale, fallback = it.englishName)) }
        .filter { (region, option) ->
            needle.isEmpty() || fold(option.name).contains(needle) || fold(region.englishName).contains(needle) ||
                region.code.equals(needle, ignoreCase = true)
        }
        .map { it.second }
        .sortedWith { a, b -> collator.compare(a.name, b.name) }
}

private val Marks = Regex("\\p{Mn}+")

private fun fold(text: String): String = Marks.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase(Locale.ROOT)
