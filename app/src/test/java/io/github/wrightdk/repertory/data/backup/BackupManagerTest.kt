package io.github.wrightdk.repertory.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.wrightdk.repertory.data.HomeSort
import io.github.wrightdk.repertory.data.SettingsStore
import io.github.wrightdk.repertory.data.ThemeMode
import io.github.wrightdk.repertory.data.local.RepertoryDatabase
import io.github.wrightdk.repertory.data.local.entity.FilmographyFilters
import io.github.wrightdk.repertory.data.local.entity.MediaType
import io.github.wrightdk.repertory.data.local.entity.Person
import io.github.wrightdk.repertory.data.local.entity.TrackedPerson
import io.github.wrightdk.repertory.data.local.entity.Viewing
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Export and restore against in-memory Room databases, one per "phone". */
@RunWith(RobolectricTestRunner::class)
class BackupManagerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now = 1_800_000_000_000L
    private val databases = mutableListOf<RepertoryDatabase>()

    /** One install: its own database and settings. */
    private inner class Phone(name: String, beforeCommit: suspend () -> Unit = {}) {
        val db: RepertoryDatabase = Room.inMemoryDatabaseBuilder(context, RepertoryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { databases += it }
        val settings = SettingsStore(
            prefs = context.getSharedPreferences("settings-$name", Context.MODE_PRIVATE),
            keyPrefs = context.getSharedPreferences("key-$name", Context.MODE_PRIVATE),
        )
        val manager = BackupManager(db, settings, appVersion = "1.0", clock = { now }, beforeCommit = beforeCommit)

        suspend fun lists() = db.trackedPersonDao().getAll().map { it.copy(id = 0) }
        suspend fun viewings() = db.viewingDao().getAll()
        suspend fun list(personId: Int, department: String) = db.trackedPersonDao().find(personId, department)
        suspend fun viewing(type: MediaType, id: Int) = db.viewingDao().get(type, id)

        suspend fun track(
            personId: Int,
            department: String,
            addedAt: Long,
            updatedAt: Long = addedAt,
            filters: FilmographyFilters? = null,
        ) {
            db.trackedPersonDao().insert(TrackedPerson(personId = personId, department = department, addedAt = addedAt, updatedAt = updatedAt, filterOverride = filters))
        }

        suspend fun see(id: Int, on: LocalDate?, type: MediaType = MediaType.MOVIE) {
            db.viewingDao().upsert(Viewing(type, id, on))
        }
    }

    @After fun tearDown() = databases.forEach { it.close() }

    private val custom = FilmographyFilters(showShorts = true, showTv = true, showDocumentaries = false, showSelf = false, showMinorRoles = true)
    private val other = FilmographyFilters(showShorts = false, showTv = false, showDocumentaries = false, showSelf = true, showMinorRoles = true)

    private fun backup(
        lists: List<BackupList> = emptyList(),
        viewings: List<Viewing> = emptyList(),
        settings: BackupSettings = BackupSettings(),
    ) = Backup(Instant.ofEpochMilli(now), "1.0", lists, viewings, settings)

    /** Through the file, as on a real phone. */
    private fun throughFile(b: Backup): Backup =
        (BackupCodec.decode(BackupCodec.encode(b)) as BackupReadResult.Valid).backup

    // ---- Round trip ----

    @Test fun `export then restore on a new phone gives the same data`() = runTest {
        val old = Phone("old")
        old.track(240, "Directing", addedAt = 1_000, updatedAt = 5_000)
        old.track(240, "Writing", addedAt = 2_000, filters = custom)
        old.track(3895, "Acting", addedAt = 3_000, updatedAt = 9_000)
        old.see(62, LocalDate.of(2022, 3, 11))
        old.see(975, null)
        old.see(1396, LocalDate.of(2020, 1, 2), MediaType.TV)
        old.settings.setFilterDefaults(FilmographyFilters.DEFAULTS.copy(showTv = true))
        old.settings.setThemeMode(ThemeMode.DARK)
        old.settings.setHomeSort(HomeSort.PROGRESS)
        old.settings.setOwnTmdbApiKey("0123456789abcdef0123456789abcdef")

        val new = Phone("new")
        val exported = old.manager.export()
        new.manager.restore(throughFile(exported), RestoreMode.REPLACE)

        assertEquals(old.lists(), new.lists())
        assertEquals(old.viewings(), new.viewings())
        assertEquals(FilmographyFilters.DEFAULTS.copy(showTv = true), new.settings.filterDefaults.value)
        assertEquals(ThemeMode.DARK, new.settings.themeMode.value)
        assertEquals(HomeSort.PROGRESS, new.settings.homeSort.value)
        assertNull("the own TMDB key stays on the old phone", new.settings.ownTmdbApiKey.value)
        assertEquals(exported, new.manager.export())
    }

    @Test fun `restoring onto an empty phone with merge gives the same data too`() = runTest {
        val old = Phone("old")
        old.track(240, "Directing", addedAt = 1_000, filters = custom)
        old.see(62, LocalDate.of(2022, 3, 11))
        val new = Phone("new")

        new.manager.restore(throughFile(old.manager.export()), RestoreMode.MERGE)

        assertEquals(old.lists(), new.lists())
        assertEquals(old.viewings(), new.viewings())
    }

    @Test fun `the export leaves out the TMDB cache`() = runTest {
        val phone = Phone("a")
        phone.db.personDao().upsert(Person(240, "Stanley Kubrick", "/k.jpg", "Directing", null, null, 0))
        phone.track(240, "Directing", addedAt = 1_000)

        val text = BackupCodec.encode(phone.manager.export())
        assertTrue(!text.contains("Kubrick") && !text.contains("/k.jpg"))
    }

    // ---- Replace ----

    @Test fun `replace swaps lists, ticked films and settings for the backup's`() = runTest {
        val phone = Phone("a")
        phone.track(240, "Directing", addedAt = 1_000)
        phone.see(62, LocalDate.of(2022, 3, 11))
        phone.db.personDao().upsert(Person(240, "Stanley Kubrick", null, "Directing", null, null, 0))

        phone.manager.restore(
            backup(
                lists = listOf(BackupList(3895, "Acting", 2_000, 2_000, null)),
                viewings = listOf(Viewing(MediaType.MOVIE, 975, null)),
                settings = BackupSettings(theme = ThemeMode.LIGHT),
            ),
            RestoreMode.REPLACE,
        )

        assertEquals(listOf(TrackedPerson(personId = 3895, department = "Acting", addedAt = 2_000)), phone.lists())
        assertEquals(listOf(Viewing(MediaType.MOVIE, 975, null)), phone.viewings())
        assertEquals(ThemeMode.LIGHT, phone.settings.themeMode.value)
        assertEquals("settings the backup leaves out are kept", HomeSort.NAME, phone.settings.homeSort.value)
        assertNotNull("the TMDB cache is kept", phone.db.personDao().get(240))
    }

    // ---- Merge ----

    @Test fun `merge keeps this phone's lists and adds the backup's`() = runTest {
        val phone = Phone("a")
        phone.track(240, "Directing", addedAt = 1_000)

        phone.manager.restore(backup(lists = listOf(BackupList(3895, "Acting", 2_000, 2_000, null))), RestoreMode.MERGE)

        assertEquals(listOf(240 to "Directing", 3895 to "Acting"), phone.lists().map { it.personId to it.department })
    }

    @Test fun `merge keeps this phone's settings`() = runTest {
        val phone = Phone("a")
        phone.settings.setThemeMode(ThemeMode.DARK)

        phone.manager.restore(
            backup(settings = BackupSettings(FilmographyFilters.DEFAULTS.copy(showShorts = true), ThemeMode.LIGHT, HomeSort.RECENT)),
            RestoreMode.MERGE,
        )

        assertEquals(ThemeMode.DARK, phone.settings.themeMode.value)
        assertEquals(FilmographyFilters.DEFAULTS, phone.settings.filterDefaults.value)
        assertEquals(HomeSort.NAME, phone.settings.homeSort.value)
    }

    @Test fun `a list on both sides keeps the earlier added and later updated, and the newer side's filters`() = runTest {
        val phone = Phone("a")
        phone.track(240, "Directing", addedAt = 3_000, updatedAt = 4_000, filters = custom)

        phone.manager.restore(backup(lists = listOf(BackupList(240, "Directing", 1_000, 9_000, other))), RestoreMode.MERGE)

        val merged = phone.list(240, "Directing")!!
        assertEquals(1_000, merged.addedAt)
        assertEquals(9_000, merged.updatedAt)
        assertEquals(other, merged.filterOverride)
        assertEquals(1, phone.lists().size)
    }

    @Test fun `a list updated later on this phone keeps its own filters`() = runTest {
        val phone = Phone("a")
        phone.track(240, "Directing", addedAt = 1_000, updatedAt = 9_000, filters = custom)

        phone.manager.restore(backup(lists = listOf(BackupList(240, "Directing", 500, 2_000, null))), RestoreMode.MERGE)

        val merged = phone.list(240, "Directing")!!
        assertEquals(500, merged.addedAt)
        assertEquals(9_000, merged.updatedAt)
        assertEquals(custom, merged.filterOverride)
    }

    @Test fun `on a tie this phone's filters win`() = runTest {
        val phone = Phone("a")
        phone.track(240, "Directing", addedAt = 1_000, updatedAt = 5_000, filters = custom)

        phone.manager.restore(backup(lists = listOf(BackupList(240, "Directing", 1_000, 5_000, other))), RestoreMode.MERGE)

        assertEquals(custom, phone.list(240, "Directing")!!.filterOverride)
    }

    @Test fun `filters equal to the defaults go back to following them`() = runTest {
        val phone = Phone("a")

        phone.manager.restore(
            backup(lists = listOf(BackupList(240, "Directing", 1_000, 1_000, FilmographyFilters.DEFAULTS))),
            RestoreMode.MERGE,
        )

        assertNull(phone.list(240, "Directing")!!.filterOverride)
    }

    @Test fun `with replace, filters are compared with the backup's defaults`() = runTest {
        val phone = Phone("a")
        val backupDefaults = FilmographyFilters.DEFAULTS.copy(showTv = true)

        phone.manager.restore(
            backup(
                lists = listOf(
                    BackupList(240, "Directing", 1_000, 1_000, backupDefaults),
                    BackupList(240, "Writing", 1_000, 1_000, FilmographyFilters.DEFAULTS),
                ),
                settings = BackupSettings(filterDefaults = backupDefaults),
            ),
            RestoreMode.REPLACE,
        )

        assertNull(phone.list(240, "Directing")!!.filterOverride)
        assertEquals(FilmographyFilters.DEFAULTS, phone.list(240, "Writing")!!.filterOverride)
    }

    @Test fun `a ticked film stays ticked, a date beats no date, and the earlier date wins`() = runTest {
        val phone = Phone("a")
        phone.see(1, LocalDate.of(2024, 5, 1))
        phone.see(2, null)
        phone.see(3, LocalDate.of(2019, 1, 1))
        phone.see(4, LocalDate.of(2021, 6, 6))
        phone.see(5, null)

        phone.manager.restore(
            backup(
                viewings = listOf(
                    Viewing(MediaType.MOVIE, 1, LocalDate.of(2020, 2, 2)),
                    Viewing(MediaType.MOVIE, 2, LocalDate.of(2023, 3, 3)),
                    Viewing(MediaType.MOVIE, 3, LocalDate.of(2025, 1, 1)),
                    Viewing(MediaType.MOVIE, 4, null),
                    Viewing(MediaType.MOVIE, 6, LocalDate.of(2018, 8, 8)),
                ),
            ),
            RestoreMode.MERGE,
        )

        assertEquals(LocalDate.of(2020, 2, 2), phone.viewing(MediaType.MOVIE, 1)!!.seenOn)
        assertEquals(LocalDate.of(2023, 3, 3), phone.viewing(MediaType.MOVIE, 2)!!.seenOn)
        assertEquals(LocalDate.of(2019, 1, 1), phone.viewing(MediaType.MOVIE, 3)!!.seenOn)
        assertEquals(LocalDate.of(2021, 6, 6), phone.viewing(MediaType.MOVIE, 4)!!.seenOn)
        assertNotNull("ticked only on this phone, still ticked", phone.viewing(MediaType.MOVIE, 5))
        assertNull(phone.viewing(MediaType.MOVIE, 5)!!.seenOn)
        assertEquals(LocalDate.of(2018, 8, 8), phone.viewing(MediaType.MOVIE, 6)!!.seenOn)
    }

    @Test fun `a movie and a series with the same id are different films`() = runTest {
        val phone = Phone("a")
        phone.see(62, LocalDate.of(2022, 1, 1), MediaType.MOVIE)

        phone.manager.restore(backup(viewings = listOf(Viewing(MediaType.TV, 62, null))), RestoreMode.MERGE)

        assertEquals(2, phone.viewings().size)
    }

    // ---- Missing optional fields ----

    @Test fun `a list without dates is added now, and one without updatedAt uses addedAt`() = runTest {
        val phone = Phone("a")

        phone.manager.restore(
            backup(lists = listOf(BackupList(240, "Directing", null, null, null), BackupList(3895, "Acting", 1_000, null, null))),
            RestoreMode.MERGE,
        )

        val undated = phone.list(240, "Directing")!!
        assertEquals(now, undated.addedAt)
        assertEquals(now, undated.updatedAt)
        assertEquals(1_000, phone.list(3895, "Acting")!!.updatedAt)
    }

    @Test fun `a minimal file restores`() = runTest {
        val phone = Phone("a")
        val minimal = (
            BackupCodec.decode(
                """{"schemaVersion": 1, "exportedAt": "2026-11-07T10:15:30Z",
                    "data": {"lists": [{"personId": 240, "department": "Directing"}], "viewings": [{"mediaType": "movie", "filmId": 62}]}}""",
            ) as BackupReadResult.Valid
            ).backup

        phone.manager.restore(minimal, RestoreMode.REPLACE)

        assertEquals(listOf(TrackedPerson(personId = 240, department = "Directing", addedAt = now)), phone.lists())
        assertEquals(listOf(Viewing(MediaType.MOVIE, 62, null)), phone.viewings())
        assertEquals(ThemeMode.SYSTEM, phone.settings.themeMode.value)
    }

    // ---- Rollback ----

    @Test fun `a failure part-way through leaves everything as it was`() = runTest {
        for (mode in RestoreMode.entries) {
            val phone = Phone("rollback-$mode", beforeCommit = { throw IllegalStateException("disk full") })
            phone.track(240, "Directing", addedAt = 1_000, filters = custom)
            phone.see(62, LocalDate.of(2022, 3, 11))
            phone.settings.setThemeMode(ThemeMode.DARK)
            val lists = phone.lists()
            val viewings = phone.viewings()

            try {
                phone.manager.restore(
                    backup(
                        lists = listOf(BackupList(240, "Directing", 500, 9_000, other), BackupList(3895, "Acting", 2_000, 2_000, null)),
                        viewings = listOf(Viewing(MediaType.MOVIE, 62, LocalDate.of(2001, 1, 1)), Viewing(MediaType.MOVIE, 975, null)),
                        settings = BackupSettings(theme = ThemeMode.LIGHT),
                    ),
                    mode,
                )
                fail("Expected the restore to fail")
            } catch (_: IllegalStateException) {
            }

            assertEquals("$mode lists", lists, phone.lists())
            assertEquals("$mode viewings", viewings, phone.viewings())
            assertEquals("$mode settings", ThemeMode.DARK, phone.settings.themeMode.value)
        }
    }
}
