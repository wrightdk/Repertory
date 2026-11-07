package io.github.wrightdk.repertory.ui.screens

import io.github.wrightdk.repertory.data.local.dao.FilmCredit
import io.github.wrightdk.repertory.data.local.entity.Film
import io.github.wrightdk.repertory.data.local.entity.FilmographyFilters
import io.github.wrightdk.repertory.data.local.entity.ImageConfig
import io.github.wrightdk.repertory.data.local.entity.MediaType
import io.github.wrightdk.repertory.data.local.entity.Person
import io.github.wrightdk.repertory.data.local.entity.Viewing
import io.github.wrightdk.repertory.ui.roleLabel
import java.time.LocalDate

// Turns cached credits, viewings and filters into what the Filmography screen
// shows. Pure functions, so the counting rules can be tested without Room.

/** The kinds of film a list can hide, in the order the filter sheet lists them. */
enum class FilmCategory { SHORT, TV, DOCUMENTARY, SELF, MINOR_ROLE }

data class FilmographyHeader(
    val name: String,
    /** "Director, 1928–1999", "Actor, born 1933", or just "Director" with no dates. */
    val subtitle: String,
    val photoUrl: String?,
)

/** One film on the list, with all of this person's credits on it in this role folded together. */
data class FilmRow(
    val mediaType: MediaType,
    val tmdbId: Int,
    val title: String,
    /** Shown in the year column; null when TMDB has no date. */
    val year: Int?,
    val posterUrl: String?,
    /** Categories this film falls into, for the meta line prefix ("Short", "Documentary"). */
    val categories: List<FilmCategory>,
    /** Acting: the character(s) played, for "as Harry Palmer". Null for crew or when TMDB has none. */
    val character: String?,
    /** Minutes, for crew roles (and for acting when there's no character). Null until details are fetched. */
    val runtime: Int?,
    val seen: Boolean,
    /** When seen, the date if the user kept one. */
    val seenOn: LocalDate?,
    /** Not out yet: can't be ticked and doesn't count. */
    val unreleased: Boolean,
    /** For unreleased films, when TMDB says it's due. */
    val dueDate: LocalDate?,
) {
    val key: String get() = "${mediaType.name}:$tmdbId"
}

sealed interface FilmographyItem {
    val key: String

    /** "1950s", or null for the undated films at the end. */
    data class Decade(val decade: Int?) : FilmographyItem {
        override val key: String get() = "decade:$decade"
    }

    data class Film(val row: FilmRow) : FilmographyItem {
        override val key: String get() = row.key
    }
}

enum class LedgerCell { SEEN, UNSEEN, UNRELEASED }

/** What the list currently leaves out, for "Hiding shorts" or "Custom list: showing everything". */
data class FilterSummary(
    /** Hidden categories that actually have films in this list. */
    val hiding: List<FilmCategory>,
    /** The list overrides the defaults from Settings. */
    val custom: Boolean,
)

data class FilmographyContent(
    /** Released films shown that are seen. */
    val seen: Int,
    /** Released films shown. Unreleased and hidden films never count. */
    val total: Int,
    /** One cell per film shown, in list order, unreleased ones included. */
    val ledger: List<LedgerCell>,
    val filterSummary: FilterSummary,
    val items: List<FilmographyItem>,
) {
    /** Rounded percentage, or null when there's nothing to count. */
    val percent: Int? get() = if (total == 0) null else Math.round(seen * 100f / total)
}

internal fun filmographyHeader(person: Person, department: String, images: ImageConfig, photoMinWidthPx: Int) =
    FilmographyHeader(
        name = person.name,
        subtitle = listOfNotNull(roleLabel(department), lifeSpan(person.birthday, person.deathday)).joinToString(", "),
        photoUrl = images.profileUrl(person.profilePath, photoMinWidthPx),
    )

/** "1928–1999", "born 1933", "died 1999", or null. */
internal fun lifeSpan(birthday: LocalDate?, deathday: LocalDate?): String? = when {
    birthday != null && deathday != null -> "${birthday.year}–${deathday.year}"
    birthday != null -> "born ${birthday.year}"
    deathday != null -> "died ${deathday.year}"
    else -> null
}

/**
 * Builds the list from [credits] (chronological, one row per credit, as
 * [io.github.wrightdk.repertory.data.local.dao.FilmDao.observeFilmography]
 * returns them). Canceled films are left out entirely; films [filters] hide
 * are left out of the rows, the ledger and the count.
 */
internal fun filmographyContent(
    credits: List<FilmCredit>,
    viewings: Map<Pair<MediaType, Int>, Viewing>,
    filters: FilmographyFilters,
    custom: Boolean,
    today: LocalDate,
    images: ImageConfig,
    posterMinWidthPx: Int,
): FilmographyContent {
    val films = credits
        .groupBy { it.film.mediaType to it.film.tmdbId }
        .values
        .map { group -> toRow(group, viewings, today, images, posterMinWidthPx) }
        .filterNot { (film, _) -> film.isCanceled }

    val shown = films.filter { (_, row) -> row.categories.all { filters.shows(it) } }.map { it.second }

    val hiding = FilmCategory.entries.filter { category ->
        !filters.shows(category) && films.any { (_, row) -> category in row.categories }
    }

    val items = buildList {
        var decade: Int? = Int.MIN_VALUE
        for (row in shown) {
            val d = row.year?.let { it / 10 * 10 }
            if (d != decade) {
                add(FilmographyItem.Decade(d))
                decade = d
            }
            add(FilmographyItem.Film(row))
        }
    }

    val released = shown.filterNot { it.unreleased }
    return FilmographyContent(
        seen = released.count { it.seen },
        total = released.size,
        ledger = shown.map {
            when {
                it.unreleased -> LedgerCell.UNRELEASED
                it.seen -> LedgerCell.SEEN
                else -> LedgerCell.UNSEEN
            }
        },
        filterSummary = FilterSummary(hiding, custom),
        items = items,
    )
}

private fun toRow(
    group: List<FilmCredit>,
    viewings: Map<Pair<MediaType, Int>, Viewing>,
    today: LocalDate,
    images: ImageConfig,
    posterMinWidthPx: Int,
): Pair<Film, FilmRow> {
    val film = group.first().film
    val released = film.isReleased(today)
    val viewing = viewings[film.mediaType to film.tmdbId]
    val characters = group.mapNotNull { displayCharacter(it.credit.character) }.distinct()
    val isActing = group.any { it.credit.character != null || it.credit.order != null }
    val categories = buildList {
        if (film.isShort) add(FilmCategory.SHORT)
        if (film.isTv) add(FilmCategory.TV)
        if (film.isDocumentary) add(FilmCategory.DOCUMENTARY)
        if (group.all { it.credit.isSelf }) add(FilmCategory.SELF)
        if (isActing && group.all { isMinorRole(it.credit.character) }) add(FilmCategory.MINOR_ROLE)
    }
    return film to FilmRow(
        mediaType = film.mediaType,
        tmdbId = film.tmdbId,
        title = film.title,
        year = film.releaseDate?.year,
        posterUrl = images.posterUrl(film.posterPath, posterMinWidthPx),
        categories = categories,
        character = characters.takeIf { it.isNotEmpty() }?.joinToString(" / "),
        runtime = film.runtime,
        seen = released && viewing != null,
        seenOn = viewing?.seenOn?.takeIf { released },
        unreleased = !released,
        dueDate = film.releaseDate.takeIf { !released },
    )
}

internal fun FilmographyFilters.shows(category: FilmCategory): Boolean = when (category) {
    FilmCategory.SHORT -> showShorts
    FilmCategory.TV -> showTv
    FilmCategory.DOCUMENTARY -> showDocumentaries
    FilmCategory.SELF -> showSelf
    FilmCategory.MINOR_ROLE -> showMinorRoles
}

private val UNCREDITED = Regex("""\s*\(uncredited\)""", RegexOption.IGNORE_CASE)

/**
 * A minor role, for now, is an uncredited part: TMDB marks those in the character
 * name ("Nun (uncredited)"). Whether a cast-order cutoff should count too is
 * still an open question (see CLAUDE.md).
 */
internal fun isMinorRole(character: String?): Boolean = character != null && UNCREDITED.containsMatchIn(character)

/** The character name to show after "as": without TMDB's "(uncredited)" marker, null when blank. */
internal fun displayCharacter(character: String?): String? =
    character?.replace(UNCREDITED, "")?.trim()?.takeIf { it.isNotEmpty() }
