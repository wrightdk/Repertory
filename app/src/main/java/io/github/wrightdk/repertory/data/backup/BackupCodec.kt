package io.github.wrightdk.repertory.data.backup

import io.github.wrightdk.repertory.data.HomeSort
import io.github.wrightdk.repertory.data.ThemeMode
import io.github.wrightdk.repertory.data.local.entity.FilmographyFilters
import io.github.wrightdk.repertory.data.local.entity.MediaType
import io.github.wrightdk.repertory.data.local.entity.Viewing
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** Why a file can't be restored. The UI turns each into a sentence; nothing has been changed. */
sealed interface BackupError {
    /** Bigger than any real backup ([BackupCodec.MAX_BYTES]); not read in full. */
    data object TooLarge : BackupError

    /** The file couldn't be opened or read. */
    data object Unreadable : BackupError

    /** Not JSON at all. */
    data object NotJson : BackupError

    /** JSON, but not a Repertory backup: no usable `schemaVersion`. */
    data object NotABackup : BackupError

    /** Written by a newer version of the app, in a format this one can't read. */
    data class TooNew(val schemaVersion: Int) : BackupError

    /** A Repertory backup, but something required is missing or the wrong type. [detail] is for tests and debugging, not the UI. */
    data class Damaged(val detail: String) : BackupError
}

sealed interface BackupReadResult {
    data class Valid(val backup: Backup) : BackupReadResult
    data class Invalid(val error: BackupError) : BackupReadResult
}

/**
 * Turns a [Backup] into the backup file's JSON and back. Reading checks the
 * whole file (JSON, version, every required field and its type) before
 * anything is returned, so a bad file is rejected without touching the
 * database.
 *
 * The file:
 * ```
 * {
 *   "schemaVersion": 1,
 *   "exportedAt": "2026-11-07T10:15:30.123Z",
 *   "appVersion": "1.0",
 *   "data": {
 *     "lists": [{ "personId": 240, "department": "Directing", "addedAt": "…", "updatedAt": "…", "filters": null }],
 *     "viewings": [{ "mediaType": "movie", "filmId": 62, "seenOn": "2022-03-11" }],
 *     "settings": { "filterDefaults": { … }, "theme": "system", "homeSort": "name" }
 *   }
 * }
 * ```
 * Unknown fields are ignored and optional ones fall back to defaults, so files
 * from slightly older or newer versions still read. A change that older
 * versions can't read safely bumps [SCHEMA_VERSION]; reading an older version
 * then goes through [migrate].
 */
object BackupCodec {
    /** The format this version writes, and the newest it reads. */
    const val SCHEMA_VERSION = 1

    /** Far beyond any real backup (a viewing is under 100 bytes); stops a wrong file filling memory. */
    const val MAX_BYTES = 10 * 1024 * 1024

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    fun encode(backup: Backup): String = json.encodeToString(BackupFile.serializer(), backup.toFile())

    /** Reads at most [MAX_BYTES] from [input] and decodes it. Doesn't close [input]. */
    fun read(input: InputStream): BackupReadResult {
        val bytes = try {
            readLimited(input, MAX_BYTES) ?: return BackupReadResult.Invalid(BackupError.TooLarge)
        } catch (_: IOException) {
            return BackupReadResult.Invalid(BackupError.Unreadable)
        }
        return decode(bytes.toString(Charsets.UTF_8))
    }

    fun decode(text: String): BackupReadResult {
        val root = try {
            json.parseToJsonElement(text.removePrefix(BYTE_ORDER_MARK))
        } catch (_: SerializationException) {
            return invalid(BackupError.NotJson)
        }
        val obj = root as? JsonObject ?: return invalid(BackupError.NotABackup)
        val version = (obj["schemaVersion"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
            ?: return invalid(BackupError.NotABackup)
        if (version < 1) return invalid(BackupError.NotABackup)
        if (version > SCHEMA_VERSION) return invalid(BackupError.TooNew(version))

        return try {
            val file = json.decodeFromJsonElement(BackupFile.serializer(), migrate(obj, version))
            BackupReadResult.Valid(file.toBackup())
        } catch (e: SerializationException) {
            invalid(BackupError.Damaged(e.message ?: "Couldn't decode"))
        } catch (e: IllegalArgumentException) {
            // kotlinx.serialization reports some type mismatches this way.
            invalid(BackupError.Damaged(e.message ?: "Couldn't decode"))
        } catch (e: InvalidBackupException) {
            invalid(BackupError.Damaged(e.message.orEmpty()))
        }
    }

    /**
     * Brings a file written in [fromVersion] up to [SCHEMA_VERSION]'s shape. Only
     * version 1 exists so far; when version 2 arrives, rewrite the JSON here
     * one step at a time (1 → 2, 2 → 3…).
     */
    private fun migrate(file: JsonObject, fromVersion: Int): JsonElement {
        check(fromVersion == SCHEMA_VERSION) { "No migration from backup version $fromVersion" }
        return file
    }

    private fun invalid(error: BackupError) = BackupReadResult.Invalid(error)

    /** All of [input], or null if it's longer than [max] bytes. */
    private fun readLimited(input: InputStream, max: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) return out.toByteArray()
            if (out.size() + n > max) return null
            out.write(buffer, 0, n)
        }
    }

    private const val BYTE_ORDER_MARK = "﻿"
}

private class InvalidBackupException(message: String) : Exception(message)

private fun fail(message: String): Nothing = throw InvalidBackupException(message)

// ---- Domain to file ----

private fun Backup.toFile() = BackupFile(
    schemaVersion = BackupCodec.SCHEMA_VERSION,
    exportedAt = exportedAt.toString(),
    appVersion = appVersion,
    data = BackupData(
        lists = lists.map { l ->
            BackupListJson(
                personId = l.personId,
                department = l.department,
                addedAt = l.addedAt?.let { Instant.ofEpochMilli(it).toString() },
                updatedAt = l.updatedAt?.let { Instant.ofEpochMilli(it).toString() },
                filters = l.filterOverride?.toJson(),
            )
        },
        viewings = viewings.map { v -> BackupViewingJson(v.mediaType.tmdbName, v.filmId, v.seenOn?.toString()) },
        settings = BackupSettingsJson(
            filterDefaults = settings.filterDefaults?.toJson(),
            theme = settings.theme?.name?.lowercase(),
            homeSort = settings.homeSort?.name?.lowercase(),
        ),
    ),
)

private fun FilmographyFilters.toJson() = BackupFiltersJson(showShorts, showTv, showDocumentaries, showSelf, showMinorRoles)

// ---- File to domain, checking as it goes ----

private fun BackupFile.toBackup(): Backup {
    val lists = data.lists.mapIndexed { i, l ->
        val where = "lists[$i]"
        if (l.personId <= 0) fail("$where.personId must be a TMDB id")
        if (l.department.isBlank()) fail("$where.department is blank")
        BackupList(
            personId = l.personId,
            department = l.department,
            addedAt = l.addedAt?.let { parseInstant(it, "$where.addedAt").toEpochMilli() },
            updatedAt = l.updatedAt?.let { parseInstant(it, "$where.updatedAt").toEpochMilli() },
            filterOverride = l.filters?.toFilters(),
        )
    }
    lists.groupingBy { it.personId to it.department }.eachCount().entries.firstOrNull { it.value > 1 }?.let {
        fail("Person ${it.key.first} is in ${it.key.second} more than once")
    }

    val viewings = data.viewings.mapIndexed { i, v ->
        val where = "viewings[$i]"
        val type = MediaType.fromTmdb(v.mediaType) ?: fail("$where.mediaType \"${v.mediaType}\" isn't movie or tv")
        if (v.filmId <= 0) fail("$where.filmId must be a TMDB id")
        Viewing(type, v.filmId, v.seenOn?.let { parseDate(it, "$where.seenOn") })
    }
    viewings.groupingBy { it.mediaType to it.filmId }.eachCount().entries.firstOrNull { it.value > 1 }?.let {
        fail("Film ${it.key.second} is ticked more than once")
    }

    return Backup(
        exportedAt = parseInstant(exportedAt, "exportedAt"),
        appVersion = appVersion,
        lists = lists,
        viewings = viewings,
        settings = data.settings.toSettings(),
    )
}

/** Unknown theme or sort names (from a newer version) are left out rather than rejected. */
private fun BackupSettingsJson?.toSettings(): BackupSettings = if (this == null) {
    BackupSettings()
} else {
    BackupSettings(
        filterDefaults = filterDefaults?.toFilters(),
        theme = ThemeMode.entries.firstOrNull { it.name.equals(theme, ignoreCase = true) },
        homeSort = HomeSort.entries.firstOrNull { it.name.equals(homeSort, ignoreCase = true) },
    )
}

/** A switch the file leaves out takes its out-of-the-box value. */
private fun BackupFiltersJson.toFilters(): FilmographyFilters {
    val d = FilmographyFilters.DEFAULTS
    return FilmographyFilters(
        showShorts = showShorts ?: d.showShorts,
        showTv = showTv ?: d.showTv,
        showDocumentaries = showDocumentaries ?: d.showDocumentaries,
        showSelf = showSelf ?: d.showSelf,
        showMinorRoles = showMinorRoles ?: d.showMinorRoles,
    )
}

/** ISO 8601 with "Z" or an offset ("2026-11-07T10:15:30Z", "2026-11-07T11:15:30+01:00"). */
private fun parseInstant(value: String, where: String): Instant = try {
    OffsetDateTime.parse(value).toInstant()
} catch (_: DateTimeException) {
    fail("$where \"$value\" isn't an ISO 8601 date and time")
}

private fun parseDate(value: String, where: String): LocalDate = try {
    LocalDate.parse(value)
} catch (_: DateTimeException) {
    fail("$where \"$value\" isn't an ISO 8601 date")
}
