package io.github.wrightdk.repertory.ui.screens

import io.github.wrightdk.repertory.data.displayCharacter
import io.github.wrightdk.repertory.data.local.dao.FilmCredit
import io.github.wrightdk.repertory.data.local.entity.Film
import io.github.wrightdk.repertory.data.local.entity.FilmographyFilters
import io.github.wrightdk.repertory.data.local.entity.ImageConfig
import io.github.wrightdk.repertory.data.local.entity.MediaType
import io.github.wrightdk.repertory.data.local.entity.Person
import io.github.wrightdk.repertory.data.local.entity.TrackedPerson
import io.github.wrightdk.repertory.data.local.entity.Viewing
import io.github.wrightdk.repertory.ui.roleLabel
import java.time.LocalDate

// Turns cached data into what the Film detail screen shows. Pure functions, so
// the credit line and the per-list counts can be tested without Room.

/** Where the film stands: the Seen toggle with its date, or "Not out yet" instead. */
sealed interface SeenState {
    data object NotSeen : SeenState

    /** [seenOn] is null when seen but the user cleared (or never set) the date. */
    data class Seen(val seenOn: LocalDate?) : SeenState

    /** Can't be ticked until it's out. [due] is null when TMDB has no date. */
    data class Unreleased(val due: LocalDate?) : SeenState
}

data class FilmDetail(
    val title: String,
    /** With [runtime], the "1968, 149 min" line. Either can be missing. */
    val year: Int?,
    /** Minutes. Null until details are fetched, or when TMDB has none. */
    val runtime: Int?,
    val posterUrl: String?,
    val seen: SeenState,
)

/**
 * The credit line under the title, for the list the film was opened from:
 * "Directed by Stanley Kubrick", "Michael Caine as Harry Palmer". [job] is the
 * crew job for departments the design doesn't name ("Production Design").
 */
sealed interface CreditLine {
    val name: String

    data class Directed(override val name: String) : CreditLine
    data class Written(override val name: String) : CreditLine
    data class Cinematography(override val name: String) : CreditLine
    data class Music(override val name: String) : CreditLine
    data class Produced(override val name: String) : CreditLine
    data class Edited(override val name: String) : CreditLine

    /** Acting: "Michael Caine as Harry Palmer", or just the name when TMDB has no character. */
    data class Acted(override val name: String, val character: String?) : CreditLine

    /** Any other department: "Ken Adam, Production Design". */
    data class Other(override val name: String, val job: String) : CreditLine
}

/** One row of "Counts toward": a list the film counts on, with that list's progress. */
data class TowardEntry(
    val trackedPersonId: Long,
    val personName: String,
    /** "Director", "Actor". */
    val role: String,
    /** Released films the list shows that are seen, and how many it shows. Null while the count isn't final. */
    val seen: Int?,
    val total: Int?,
)

/** What one list makes of this film: whether it shows it, its progress, and this person's credits on it. */
internal data class ListView(
    val list: TrackedPerson,
    val person: Person?,
    /** The list's own count is final (every film's details are in). */
    val detailsComplete: Boolean,
    /** The list's filters show this film. */
    val shows: Boolean,
    val seen: Int,
    val total: Int,
    val creditsOnFilm: List<FilmCredit>,
)

/**
 * Runs one list's credits through the same rules as its Filmography screen
 * ([filmographyContent]), so "9 of 13" here always matches the list.
 */
internal fun listView(
    list: TrackedPerson,
    person: Person?,
    credits: List<FilmCredit>,
    missingDetails: Int,
    viewings: Map<Pair<MediaType, Int>, Viewing>,
    defaults: FilmographyFilters,
    mediaType: MediaType,
    filmId: Int,
    today: LocalDate,
): ListView {
    val content = filmographyContent(
        credits = credits,
        personName = person?.name,
        viewings = viewings,
        filters = list.filters(defaults),
        custom = list.filterOverride != null,
        today = today,
        images = ImageConfig.DEFAULT,
        posterMinWidthPx = 0,
    )
    val shows = content.items.any { it is FilmographyItem.Film && it.row.mediaType == mediaType && it.row.tmdbId == filmId }
    return ListView(
        list = list,
        person = person,
        detailsComplete = missingDetails == 0,
        shows = shows,
        seen = content.seen,
        total = content.total,
        creditsOnFilm = credits.filter { it.film.mediaType == mediaType && it.film.tmdbId == filmId },
    )
}

/**
 * "Counts toward": the lists that show this film (a list that hides it doesn't
 * count it), the one it was opened from first. Lists whose person isn't cached
 * yet are left out rather than shown nameless.
 */
internal fun towardEntries(views: List<ListView>, openedFrom: Long): List<TowardEntry> =
    views
        .filter { it.shows && it.person != null }
        .sortedByDescending { it.list.id == openedFrom }
        .map { v ->
            TowardEntry(
                trackedPersonId = v.list.id,
                personName = v.person!!.name,
                role = roleLabel(v.list.department),
                seen = v.seen.takeIf { v.detailsComplete },
                total = v.total.takeIf { v.detailsComplete },
            )
        }

internal fun creditLine(department: String, personName: String, credits: List<FilmCredit>): CreditLine = when (department) {
    "Directing" -> CreditLine.Directed(personName)
    "Writing" -> CreditLine.Written(personName)
    "Camera" -> CreditLine.Cinematography(personName)
    "Sound" -> CreditLine.Music(personName)
    "Production" -> CreditLine.Produced(personName)
    "Editing" -> CreditLine.Edited(personName)
    "Acting" -> CreditLine.Acted(
        personName,
        credits.mapNotNull { displayCharacter(it.credit.character) }.distinct()
            .takeIf { it.isNotEmpty() }?.joinToString(" / "),
    )
    else -> CreditLine.Other(
        personName,
        credits.mapNotNull { it.credit.job?.takeIf(String::isNotBlank) }.distinct()
            .takeIf { it.isNotEmpty() }?.joinToString(", ") ?: department,
    )
}

internal fun filmDetail(film: Film, viewing: Viewing?, today: LocalDate, images: ImageConfig, posterMinWidthPx: Int): FilmDetail {
    val released = film.isReleased(today)
    return FilmDetail(
        title = film.title,
        year = film.releaseDate?.year,
        runtime = film.runtime?.takeIf { it > 0 },
        posterUrl = images.posterUrl(film.posterPath, posterMinWidthPx),
        seen = when {
            !released -> SeenState.Unreleased(film.releaseDate)
            viewing != null -> SeenState.Seen(viewing.seenOn)
            else -> SeenState.NotSeen
        },
    )
}
