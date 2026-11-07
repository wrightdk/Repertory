package io.github.wrightdk.repertory.data.backup

import kotlinx.serialization.Serializable

// The backup file's JSON, version BackupCodec.SCHEMA_VERSION. Kept apart from
// the Room entities so the database can change without changing the file.
// Anything with a default is optional when reading; the rest is required.
// Renaming or retyping a field here changes the format: bump the version and
// add a step to BackupCodec.migrate.

@Serializable
internal data class BackupFile(
    val schemaVersion: Int,
    /** ISO 8601 instant. */
    val exportedAt: String,
    val appVersion: String? = null,
    val data: BackupData,
)

@Serializable
internal data class BackupData(
    val lists: List<BackupListJson> = emptyList(),
    val viewings: List<BackupViewingJson> = emptyList(),
    val settings: BackupSettingsJson? = null,
)

@Serializable
internal data class BackupListJson(
    /** TMDB person id. */
    val personId: Int,
    /** TMDB department, e.g. "Directing". */
    val department: String,
    /** ISO 8601 instant. */
    val addedAt: String? = null,
    /** ISO 8601 instant. */
    val updatedAt: String? = null,
    /** The list's own filters; null follows the defaults. */
    val filters: BackupFiltersJson? = null,
)

@Serializable
internal data class BackupViewingJson(
    /** TMDB's name: "movie" or "tv". */
    val mediaType: String,
    /** TMDB movie or series id. */
    val filmId: Int,
    /** ISO 8601 date, or null when seen without a date. */
    val seenOn: String? = null,
)

@Serializable
internal data class BackupSettingsJson(
    val filterDefaults: BackupFiltersJson? = null,
    /** "light", "dark" or "system". */
    val theme: String? = null,
    /** "name", "progress" or "recent". */
    val homeSort: String? = null,
)

/** Each switch means "show". One left out takes its out-of-the-box value. */
@Serializable
internal data class BackupFiltersJson(
    val showShorts: Boolean? = null,
    val showTv: Boolean? = null,
    val showDocumentaries: Boolean? = null,
    val showSelf: Boolean? = null,
    val showMinorRoles: Boolean? = null,
)
