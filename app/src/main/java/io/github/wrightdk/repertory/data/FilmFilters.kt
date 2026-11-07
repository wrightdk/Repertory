package io.github.wrightdk.repertory.data

import io.github.wrightdk.repertory.data.local.dao.CreditInfo
import io.github.wrightdk.repertory.data.local.entity.Film
import io.github.wrightdk.repertory.data.local.entity.FilmographyFilters

// The filter rules (see "Filter rules" in CLAUDE.md), in one place so every
// list, count and progress figure agrees. Pure functions.

/** The kinds of film a list can hide, in the order the filter sheet and Settings list them. */
enum class FilmCategory { SHORT, TV, DOCUMENTARY, SELF, MINOR_ROLE }

/** TMDB cast order (0 = top billed) from which a part counts as minor: 21st billed or lower. */
internal const val MINOR_ROLE_MIN_ORDER = 20

/**
 * The categories [film] falls into for one list, from the film and [credits]:
 * this person's credits on it in the list's department. [personName] lets a
 * character credited under their own name count as self.
 */
internal fun filmCategories(film: Film, credits: List<CreditInfo>, personName: String?): Set<FilmCategory> = buildSet {
    if (film.isShort) add(FilmCategory.SHORT)
    if (film.isTv) add(FilmCategory.TV)
    if (film.isDocumentary) add(FilmCategory.DOCUMENTARY)
    val cast = credits.isNotEmpty() && credits.all { it.isCast }
    if (cast && credits.all { isSelfCredit(it, personName) }) add(FilmCategory.SELF)
    if (cast && credits.all { isMinorCredit(it, personName) }) add(FilmCategory.MINOR_ROLE)
}

/** Shown unless one of its categories is switched off. */
internal fun FilmographyFilters.showsAll(categories: Collection<FilmCategory>): Boolean = categories.all { shows(it) }

internal fun FilmographyFilters.shows(category: FilmCategory): Boolean = when (category) {
    FilmCategory.SHORT -> showShorts
    FilmCategory.TV -> showTv
    FilmCategory.DOCUMENTARY -> showDocumentaries
    FilmCategory.SELF -> showSelf
    FilmCategory.MINOR_ROLE -> showMinorRoles
}

internal fun FilmographyFilters.with(category: FilmCategory, show: Boolean): FilmographyFilters = when (category) {
    FilmCategory.SHORT -> copy(showShorts = show)
    FilmCategory.TV -> copy(showTv = show)
    FilmCategory.DOCUMENTARY -> copy(showDocumentaries = show)
    FilmCategory.SELF -> copy(showSelf = show)
    FilmCategory.MINOR_ROLE -> copy(showMinorRoles = show)
}

/** A list's override after a change: null (follow the defaults) when it ends up matching them. */
internal fun overrideFor(filters: FilmographyFilters, defaults: FilmographyFilters): FilmographyFilters? =
    filters.takeIf { it != defaults }

/** Cast credits carry a character or a billing order; crew credits have neither. */
private val CreditInfo.isCast: Boolean get() = character != null || order != null

private val UNCREDITED = Regex("""\(\s*uncredited\s*\)""", RegexOption.IGNORE_CASE)
private val ARCHIVE_FOOTAGE = Regex("""\(\s*(also\s+)?archive footage\s*\)""", RegexOption.IGNORE_CASE)
private val TRAILING_PARENTHETICALS = Regex("""(\s*\([^)]*\))+\s*$""")

/** Playing themselves: TMDB's "Self"-style characters (already in [CreditInfo.isSelf]), or their own name. */
internal fun isSelfCredit(credit: CreditInfo, personName: String?): Boolean {
    if (!credit.isCast) return false
    if (credit.isSelf) return true
    val name = personName?.trim()?.takeIf { it.isNotEmpty() } ?: return false
    val character = credit.character?.replace(TRAILING_PARENTHETICALS, "")?.trim() ?: return false
    return character.equals(name, ignoreCase = true)
}

/** Uncredited, archive footage only, or billed 21st or lower. A self credit is never minor. */
internal fun isMinorCredit(credit: CreditInfo, personName: String?): Boolean {
    if (!credit.isCast || isSelfCredit(credit, personName)) return false
    val character = credit.character.orEmpty()
    return UNCREDITED.containsMatchIn(character) ||
        ARCHIVE_FOOTAGE.containsMatchIn(character) ||
        (credit.order != null && credit.order >= MINOR_ROLE_MIN_ORDER)
}

/** The character name to show after "as": without TMDB's "(uncredited)" marker, null when blank. */
internal fun displayCharacter(character: String?): String? =
    character?.replace(UNCREDITED, "")?.replace(Regex("""\s{2,}"""), " ")?.trim()?.takeIf { it.isNotEmpty() }
