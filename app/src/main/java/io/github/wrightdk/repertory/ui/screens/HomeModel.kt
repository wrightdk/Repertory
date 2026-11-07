package io.github.wrightdk.repertory.ui.screens

import io.github.wrightdk.repertory.data.HomeSort
import io.github.wrightdk.repertory.data.local.dao.FilmCredit
import io.github.wrightdk.repertory.data.local.entity.FilmographyFilters
import io.github.wrightdk.repertory.data.local.entity.ImageConfig
import io.github.wrightdk.repertory.data.local.entity.MediaType
import io.github.wrightdk.repertory.data.local.entity.Person
import io.github.wrightdk.repertory.data.local.entity.TrackedPerson
import io.github.wrightdk.repertory.data.local.entity.Viewing
import io.github.wrightdk.repertory.ui.roleLabel
import java.text.Collator
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

// Turns tracked lists into Home's rows: counting, sorting, searching. Pure
// functions, so the rules can be tested without Room.

/** "9 of 40 seen" for one list, counted by the same rules as its filmography. */
data class HomeProgress(val seen: Int, val total: Int) {
    val done: Boolean get() = total > 0 && seen == total

    /** 0 for a list with nothing to count. */
    val fraction: Float get() = if (total == 0) 0f else seen.toFloat() / total
}

/** One row on Home: one person in one role. */
data class HomeRow(
    val trackedPersonId: Long,
    /** Null until the person is cached. */
    val name: String?,
    val photoUrl: String?,
    val role: String,
    /** Null while the list's film details are still loading, so the count isn't final. */
    val progress: HomeProgress?,
    /** The background details fetch stopped; the row stays in its loading state, without the pulse. */
    val detailsFailed: Boolean,
    /** Epoch millis of the last tick or change, for the Recent sort. */
    val updatedAt: Long,
)

/** How long ago a list was last updated, for "Director, 2 days ago". */
sealed interface Recency {
    data object Today : Recency
    data object Yesterday : Recency
    data class Days(val n: Int) : Recency
    data class Weeks(val n: Int) : Recency
    data class Months(val n: Int) : Recency
    data class Years(val n: Int) : Recency
}

internal fun homeRow(
    list: TrackedPerson,
    person: Person?,
    credits: List<FilmCredit>,
    detailsComplete: Boolean,
    detailsFailed: Boolean,
    viewings: Map<Pair<MediaType, Int>, Viewing>,
    defaults: FilmographyFilters,
    today: LocalDate,
    images: ImageConfig,
    photoMinWidthPx: Int,
): HomeRow {
    // Without the person (a list just restored from a backup) there are no credits yet either.
    val progress = if (detailsComplete && person != null) {
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
        HomeProgress(content.seen, content.total)
    } else {
        null
    }
    return HomeRow(
        trackedPersonId = list.id,
        name = person?.name,
        photoUrl = person?.let { images.profileUrl(it.profilePath, photoMinWidthPx) },
        role = roleLabel(list.department),
        progress = progress,
        detailsFailed = progress == null && detailsFailed,
        updatedAt = list.updatedAt,
    )
}

/**
 * Name: by surname (the last word of the name), then role. Progress: furthest
 * along first, lists still loading last. Recent: last updated first.
 * Ties fall back to name order.
 */
internal fun sortRows(rows: List<HomeRow>, sort: HomeSort, locale: Locale = Locale.getDefault()): List<HomeRow> {
    // Primary strength: "Agnès" sorts with "Agnes", case is ignored.
    val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    val byName = compareBy<HomeRow, String>(collator) { surname(it.name.orEmpty()) }
        .thenBy(collator) { it.name.orEmpty() }
        .thenBy(collator) { it.role }
        .thenBy { it.trackedPersonId }
    val comparator = when (sort) {
        HomeSort.NAME -> byName
        HomeSort.PROGRESS -> compareBy<HomeRow> { it.progress == null }
            .thenByDescending { it.progress?.fraction ?: 0f }
            .then(byName)
        HomeSort.RECENT -> compareByDescending<HomeRow> { it.updatedAt }.then(byName)
    }
    return rows.sortedWith(comparator)
}

internal fun surname(name: String): String = name.trim().substringAfterLast(' ')

/** Rows whose name or role contains [query], ignoring case and accents. Blank matches everything. */
internal fun searchRows(rows: List<HomeRow>, query: String): List<HomeRow> {
    val q = fold(query.trim())
    if (q.isEmpty()) return rows
    return rows.filter { fold(it.name.orEmpty()).contains(q) || fold(it.role).contains(q) }
}

private val COMBINING_MARKS = Regex("""\p{Mn}+""")

private fun fold(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(COMBINING_MARKS, "").lowercase(Locale.ROOT)

/** Calendar days between [updatedAt] and [today] in [zone], rounded the way people say it. */
internal fun recency(updatedAt: Long, today: LocalDate, zone: ZoneId): Recency {
    val day = Instant.ofEpochMilli(updatedAt).atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(day, today).coerceAtLeast(0).toInt()
    return when {
        days == 0 -> Recency.Today
        days == 1 -> Recency.Yesterday
        days < 7 -> Recency.Days(days)
        days < 30 -> Recency.Weeks((days / 7f).roundToInt())
        days < 365 -> Recency.Months((days / 30f).roundToInt().coerceIn(1, 12))
        else -> Recency.Years(days / 365)
    }
}
