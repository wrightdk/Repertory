package io.github.wrightdk.repertory.data.backup

import io.github.wrightdk.repertory.data.HomeSort
import io.github.wrightdk.repertory.data.ThemeMode
import io.github.wrightdk.repertory.data.local.entity.FilmographyFilters
import io.github.wrightdk.repertory.data.local.entity.MediaType
import io.github.wrightdk.repertory.data.local.entity.Viewing
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    private val backup = Backup(
        exportedAt = Instant.parse("2026-11-07T10:15:30.123Z"),
        appVersion = "1.0",
        lists = listOf(
            BackupList(240, "Directing", addedAt = 1_700_000_000_000, updatedAt = 1_700_000_500_000, filterOverride = null),
            BackupList(
                3895, "Acting", addedAt = 1_700_000_100_000, updatedAt = 1_700_000_100_000,
                filterOverride = FilmographyFilters(showShorts = true, showTv = false, showDocumentaries = false, showSelf = true, showMinorRoles = false),
            ),
        ),
        viewings = listOf(
            Viewing(MediaType.MOVIE, 62, LocalDate.of(2022, 3, 11)),
            Viewing(MediaType.MOVIE, 975, null),
            Viewing(MediaType.TV, 1396, LocalDate.of(2020, 1, 2)),
        ),
        settings = BackupSettings(FilmographyFilters.DEFAULTS.copy(showTv = true), ThemeMode.DARK, HomeSort.RECENT),
    )

    private fun decode(text: String) = BackupCodec.decode(text)

    private fun error(text: String): BackupError =
        (decode(text) as BackupReadResult.Invalid).error

    private fun valid(text: String): Backup = when (val r = decode(text)) {
        is BackupReadResult.Valid -> r.backup
        is BackupReadResult.Invalid -> throw AssertionError("Expected a valid backup, got ${r.error}")
    }

    private fun file(data: String = "{}", extra: String = "") =
        """{"schemaVersion": 1, "exportedAt": "2026-11-07T10:15:30Z", $extra "data": $data}"""

    // ---- Round trip and format ----

    @Test fun `encode then decode gives the same backup`() {
        assertEquals(backup, valid(BackupCodec.encode(backup)))
    }

    @Test fun `the file has the envelope and uses TMDB ids and ISO dates`() {
        val root = Json.parseToJsonElement(BackupCodec.encode(backup)).jsonObject
        assertEquals("1", root["schemaVersion"]!!.jsonPrimitive.content)
        assertEquals("2026-11-07T10:15:30.123Z", root["exportedAt"]!!.jsonPrimitive.content)
        assertEquals("1.0", root["appVersion"]!!.jsonPrimitive.content)
        val data = root["data"]!!.jsonObject
        val list = data["lists"]!!.jsonArray[0].jsonObject
        assertEquals(setOf("personId", "department", "addedAt", "updatedAt", "filters"), list.keys)
        assertEquals("2023-11-14T22:13:20Z", list["addedAt"]!!.jsonPrimitive.content)
        val viewing = data["viewings"]!!.jsonArray[0].jsonObject
        assertEquals("movie", viewing["mediaType"]!!.jsonPrimitive.content)
        assertEquals("2022-03-11", viewing["seenOn"]!!.jsonPrimitive.content)
        val settings = data["settings"]!!.jsonObject
        assertEquals("dark", settings["theme"]!!.jsonPrimitive.content)
        assertEquals("recent", settings["homeSort"]!!.jsonPrimitive.content)
    }

    // ---- Rejected files ----

    @Test fun `not JSON`() {
        assertEquals(BackupError.NotJson, error("this is not json"))
        assertEquals(BackupError.NotJson, error("""{"schemaVersion": 1, "data": {"""))
        assertEquals(BackupError.NotJson, error(""))
    }

    @Test fun `JSON that isn't a backup`() {
        assertEquals(BackupError.NotABackup, error("[1, 2, 3]"))
        assertEquals(BackupError.NotABackup, error("""{"name": "something else"}"""))
        assertEquals(BackupError.NotABackup, error("""{"schemaVersion": "1", "exportedAt": "2026-11-07T10:15:30Z", "data": {}}"""))
        assertEquals(BackupError.NotABackup, error("""{"schemaVersion": 0, "exportedAt": "2026-11-07T10:15:30Z", "data": {}}"""))
        assertEquals(BackupError.NotABackup, error("""{"schemaVersion": 1.5, "exportedAt": "2026-11-07T10:15:30Z", "data": {}}"""))
    }

    @Test fun `a newer schema version is refused, not guessed at`() {
        assertEquals(BackupError.TooNew(2), error("""{"schemaVersion": 2, "exportedAt": "2026-11-07T10:15:30Z", "data": {}}"""))
        assertEquals(BackupError.TooNew(99), error("""{"schemaVersion": 99}"""))
    }

    @Test fun `missing required fields`() {
        assertTrue(error("""{"schemaVersion": 1, "data": {}}""") is BackupError.Damaged)
        assertTrue(error("""{"schemaVersion": 1, "exportedAt": "2026-11-07T10:15:30Z"}""") is BackupError.Damaged)
        assertTrue(error(file("""{"lists": [{"department": "Directing"}]}""")) is BackupError.Damaged)
        assertTrue(error(file("""{"lists": [{"personId": 240}]}""")) is BackupError.Damaged)
        assertTrue(error(file("""{"viewings": [{"mediaType": "movie"}]}""")) is BackupError.Damaged)
        assertTrue(error(file("""{"viewings": [{"filmId": 62}]}""")) is BackupError.Damaged)
        assertTrue(error(file("""{"lists": [{"personId": null, "department": "Directing"}]}""")) is BackupError.Damaged)
    }

    @Test fun `wrong types`() {
        assertTrue(error(file("""{"viewings": [{"mediaType": "movie", "filmId": "abc"}]}""")) is BackupError.Damaged)
        assertTrue(error(file("""{"viewings": {"mediaType": "movie", "filmId": 62}}""")) is BackupError.Damaged)
        assertTrue(error(file("""{"lists": [{"personId": 240, "department": ["Directing"]}]}""")) is BackupError.Damaged)
        assertTrue(error(file("""{"lists": [{"personId": 240, "department": "Directing", "filters": {"showTv": "yes"}}]}""")) is BackupError.Damaged)
        assertTrue(error(file("\"data\"")) is BackupError.Damaged)
    }

    @Test fun `values that can't be right`() {
        assertTrue(error(file("""{"viewings": [{"mediaType": "book", "filmId": 62}]}""")) is BackupError.Damaged)
        assertTrue(error(file("""{"viewings": [{"mediaType": "movie", "filmId": 0}]}""")) is BackupError.Damaged)
        assertTrue(error(file("""{"viewings": [{"mediaType": "movie", "filmId": 62, "seenOn": "11/03/2022"}]}""")) is BackupError.Damaged)
        assertTrue(error(file("""{"lists": [{"personId": -1, "department": "Directing"}]}""")) is BackupError.Damaged)
        assertTrue(error(file("""{"lists": [{"personId": 240, "department": " "}]}""")) is BackupError.Damaged)
        assertTrue(error(file("""{"lists": [{"personId": 240, "department": "Directing", "addedAt": "yesterday"}]}""")) is BackupError.Damaged)
        assertTrue(error("""{"schemaVersion": 1, "exportedAt": "not a date", "data": {}}""") is BackupError.Damaged)
    }

    @Test fun `duplicates are refused`() {
        val lists = """{"lists": [{"personId": 240, "department": "Directing"}, {"personId": 240, "department": "Directing"}]}"""
        assertTrue(error(file(lists)) is BackupError.Damaged)
        val viewings = """{"viewings": [{"mediaType": "movie", "filmId": 62}, {"mediaType": "movie", "filmId": 62, "seenOn": "2022-03-11"}]}"""
        assertTrue(error(file(viewings)) is BackupError.Damaged)
    }

    @Test fun `the same person in two roles, and the same id as a movie and a series, are fine`() {
        val b = valid(
            file(
                """{
                    "lists": [{"personId": 240, "department": "Directing"}, {"personId": 240, "department": "Writing"}],
                    "viewings": [{"mediaType": "movie", "filmId": 62}, {"mediaType": "tv", "filmId": 62}]
                }""",
            ),
        )
        assertEquals(2, b.lists.size)
        assertEquals(2, b.viewings.size)
    }

    // ---- Old and new files ----

    @Test fun `missing optional fields take their defaults`() {
        val b = valid(
            file(
                """{
                    "lists": [{"personId": 240, "department": "Directing"}],
                    "viewings": [{"mediaType": "movie", "filmId": 62}]
                }""",
            ),
        )
        assertNull(b.appVersion)
        assertEquals(BackupList(240, "Directing", addedAt = null, updatedAt = null, filterOverride = null), b.lists.single())
        assertEquals(Viewing(MediaType.MOVIE, 62, null), b.viewings.single())
        assertEquals(BackupSettings(), b.settings)

        val empty = valid(file())
        assertEquals(emptyList<BackupList>(), empty.lists)
        assertEquals(emptyList<Viewing>(), empty.viewings)
    }

    @Test fun `a filter switch left out takes its out-of-the-box value`() {
        val b = valid(file("""{"lists": [{"personId": 240, "department": "Directing", "filters": {"showShorts": true}}]}"""))
        assertEquals(FilmographyFilters.DEFAULTS.copy(showShorts = true), b.lists.single().filterOverride)
    }

    @Test fun `unknown fields are ignored`() {
        val b = valid(
            file(
                extra = """"device": "Pixel 9", "future": {"nested": [1, 2]},""",
                data = """{
                    "lists": [{"personId": 240, "department": "Directing", "name": "Stanley Kubrick", "priority": 3}],
                    "viewings": [{"mediaType": "movie", "filmId": 62, "rating": 5}],
                    "settings": {"theme": "dark", "fontSize": "large"},
                    "books": []
                }""",
            ),
        )
        assertEquals(240, b.lists.single().personId)
        assertEquals(62, b.viewings.single().filmId)
        assertEquals(ThemeMode.DARK, b.settings.theme)
    }

    @Test fun `an unknown theme or sort from a newer version is left out, not refused`() {
        val b = valid(file("""{"settings": {"theme": "sepia", "homeSort": "rating"}}"""))
        assertNull(b.settings.theme)
        assertNull(b.settings.homeSort)
    }

    @Test fun `times with an offset are accepted`() {
        val b = valid("""{"schemaVersion": 1, "exportedAt": "2026-11-07T11:15:30+01:00", "data": {}}""")
        assertEquals(Instant.parse("2026-11-07T10:15:30Z"), b.exportedAt)
    }

    @Test fun `a byte order mark is skipped`() {
        assertEquals(backup, valid("﻿" + BackupCodec.encode(backup)))
    }

    // ---- Reading a stream ----

    @Test fun `reads a file from a stream`() {
        val r = BackupCodec.read(ByteArrayInputStream(BackupCodec.encode(backup).toByteArray()))
        assertEquals(BackupReadResult.Valid(backup), r)
    }

    @Test fun `a file too big to be a backup isn't read in full`() {
        var served = 0L
        val endless = object : InputStream() {
            override fun read(): Int = 'a'.code.also { served++ }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                b.fill('a'.code.toByte(), off, off + len)
                served += len
                return len
            }
        }
        assertEquals(BackupReadResult.Invalid(BackupError.TooLarge), BackupCodec.read(endless))
        assertTrue(served <= BackupCodec.MAX_BYTES + 8 * 1024)
    }

    @Test fun `a read error is unreadable`() {
        val broken = object : InputStream() {
            override fun read(): Int = throw IOException("gone")
        }
        assertEquals(BackupReadResult.Invalid(BackupError.Unreadable), BackupCodec.read(broken))
    }
}
