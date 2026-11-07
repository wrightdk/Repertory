package io.github.wrightdk.repertory.data.backup

import androidx.room.withTransaction
import io.github.wrightdk.repertory.data.SettingsStore
import io.github.wrightdk.repertory.data.local.RepertoryDatabase
import io.github.wrightdk.repertory.data.local.entity.FilmographyFilters
import io.github.wrightdk.repertory.data.local.entity.TrackedPerson
import java.time.Instant
import java.time.LocalDate

/**
 * Takes the user's data out of the app as a [Backup] and puts one back.
 * Reading and writing the file itself is [BackupCodec]'s job; the file never
 * leaves the phone except where the user saves it.
 */
class BackupManager(
    private val database: RepertoryDatabase,
    private val settings: SettingsStore,
    /** `versionName`, recorded in the file. */
    private val appVersion: String,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Runs inside the restore's transaction after every write, before it commits. For tests. */
    private val beforeCommit: suspend () -> Unit = {},
) {
    private val lists = database.trackedPersonDao()
    private val viewings = database.viewingDao()

    /** A snapshot of every list, ticked film and setting, read in one transaction. */
    suspend fun export(): Backup {
        val (allLists, allViewings) = database.withTransaction { lists.getAll() to viewings.getAll() }
        return Backup(
            exportedAt = Instant.ofEpochMilli(clock()),
            appVersion = appVersion,
            lists = allLists.map { BackupList(it.personId, it.department, it.addedAt, it.updatedAt, it.filterOverride) },
            viewings = allViewings,
            settings = BackupSettings(
                filterDefaults = settings.filterDefaults.value,
                theme = settings.themeMode.value,
                homeSort = settings.homeSort.value,
                watchRegion = settings.watchRegion.value,
            ),
        )
    }

    /**
     * Puts [backup] into the app, all in one transaction: if anything fails,
     * nothing changes. Settings are applied only once the transaction has
     * committed.
     *
     * [RestoreMode.REPLACE] deletes every list and ticked film first and
     * takes the backup's settings. [RestoreMode.MERGE] keeps everything and
     * this phone's settings; where both sides have the same thing:
     * - **A list** (same person and role): the earlier `addedAt` and the later
     *   `updatedAt`. Filters come from whichever was updated later; on a tie,
     *   this phone's.
     * - **A ticked film**: stays ticked (a merge never unticks anything). A date
     *   beats no date; of two dates, the earlier wins, since ticking fills in
     *   today and the earlier is more likely the real one.
     *
     * Either way, a list whose filters end up equal to the defaults follows
     * the defaults again, as when it's changed on the phone.
     *
     * The TMDB cache is left alone: it's keyed by TMDB id, so it's still right.
     * Lists whose person isn't cached are fetched afterwards by
     * [io.github.wrightdk.repertory.data.FilmDetailsSync.backfill].
     */
    suspend fun restore(backup: Backup, mode: RestoreMode) {
        val now = clock()
        val defaults = when (mode) {
            RestoreMode.REPLACE -> backup.settings.filterDefaults ?: settings.filterDefaults.value
            RestoreMode.MERGE -> settings.filterDefaults.value
        }
        database.withTransaction {
            if (mode == RestoreMode.REPLACE) {
                lists.deleteAll()
                viewings.deleteAll()
            }
            for (l in backup.lists) {
                val existing = lists.find(l.personId, l.department)
                if (existing == null) {
                    val addedAt = l.addedAt ?: now
                    lists.insert(
                        TrackedPerson(
                            personId = l.personId,
                            department = l.department,
                            addedAt = addedAt,
                            updatedAt = l.updatedAt ?: addedAt,
                            filterOverride = l.filterOverride.unlessEqualTo(defaults),
                        ),
                    )
                } else {
                    lists.update(mergeList(existing, l).let { it.copy(filterOverride = it.filterOverride.unlessEqualTo(defaults)) })
                }
            }
            for (v in backup.viewings) {
                val existing = viewings.get(v.mediaType, v.filmId)
                viewings.upsert(if (existing == null) v else v.copy(seenOn = mergeSeenOn(existing.seenOn, v.seenOn)))
            }
            beforeCommit()
        }
        if (mode == RestoreMode.REPLACE) {
            backup.settings.filterDefaults?.let(settings::setFilterDefaults)
            backup.settings.theme?.let(settings::setThemeMode)
            backup.settings.homeSort?.let(settings::setHomeSort)
            backup.settings.watchRegion?.let(settings::setWatchRegion)
        }
    }

    private fun FilmographyFilters?.unlessEqualTo(defaults: FilmographyFilters): FilmographyFilters? =
        this?.takeUnless { it == defaults }
}

/** Merge rule for a list on both sides; see [BackupManager.restore]. */
internal fun mergeList(existing: TrackedPerson, incoming: BackupList): TrackedPerson {
    val incomingAdded = incoming.addedAt ?: existing.addedAt
    val incomingUpdated = incoming.updatedAt ?: incomingAdded
    return existing.copy(
        addedAt = minOf(existing.addedAt, incomingAdded),
        updatedAt = maxOf(existing.updatedAt, incomingUpdated),
        filterOverride = if (incomingUpdated > existing.updatedAt) incoming.filterOverride else existing.filterOverride,
    )
}

/** Merge rule for a film ticked on both sides; see [BackupManager.restore]. */
internal fun mergeSeenOn(existing: LocalDate?, incoming: LocalDate?): LocalDate? = when {
    existing == null -> incoming
    incoming == null -> existing
    else -> minOf(existing, incoming)
}
