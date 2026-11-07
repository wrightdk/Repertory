package io.github.wrightdk.repertory.data.backup

import io.github.wrightdk.repertory.data.HomeSort
import io.github.wrightdk.repertory.data.ThemeMode
import io.github.wrightdk.repertory.data.local.entity.FilmographyFilters
import io.github.wrightdk.repertory.data.local.entity.Viewing
import java.time.Instant

/**
 * Everything the user has made, as read from or written to a backup file:
 * their lists, ticked films and settings. Nothing that TMDB can give back
 * (people, films, credits) and not the own TMDB key, which stays on the phone.
 *
 * Everything is keyed by TMDB ids, never local row ids, so a backup restores
 * on any install.
 */
data class Backup(
    val exportedAt: Instant,
    /** `versionName` of the app that wrote it; null if the file doesn't say. */
    val appVersion: String?,
    val lists: List<BackupList>,
    val viewings: List<Viewing>,
    val settings: BackupSettings,
)

/** One list: [personId] (TMDB) tracked in [department]. */
data class BackupList(
    val personId: Int,
    val department: String,
    /** Epoch millis. Null if the file leaves it out; restoring then uses the time of the restore. */
    val addedAt: Long?,
    /** Epoch millis. Null if the file leaves it out; restoring then uses [addedAt]. */
    val updatedAt: Long?,
    /** The list's own filters, or null to follow the defaults. */
    val filterOverride: FilmographyFilters?,
)

/** App settings from the backup. Null for any the file leaves out or that this version doesn't know. */
data class BackupSettings(
    val filterDefaults: FilmographyFilters? = null,
    val theme: ThemeMode? = null,
    val homeSort: HomeSort? = null,
)

/** How a restore treats what's already on the phone. */
enum class RestoreMode {
    /**
     * Keeps everything on the phone and adds the backup's lists and ticked
     * films. Settings stay as they are. Where both have the same list or
     * film, see [BackupManager.restore] for which wins.
     */
    MERGE,

    /** Swaps the phone's lists, ticked films and settings for the backup's. */
    REPLACE,
}
