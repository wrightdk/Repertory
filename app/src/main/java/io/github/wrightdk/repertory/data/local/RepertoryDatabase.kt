package io.github.wrightdk.repertory.data.local

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.github.wrightdk.repertory.data.local.dao.CreditDao
import io.github.wrightdk.repertory.data.local.dao.FilmDao
import io.github.wrightdk.repertory.data.local.dao.ImageConfigDao
import io.github.wrightdk.repertory.data.local.dao.PersonDao
import io.github.wrightdk.repertory.data.local.dao.TrackedPersonDao
import io.github.wrightdk.repertory.data.local.dao.ViewingDao
import io.github.wrightdk.repertory.data.local.dao.WatchProvidersDao
import io.github.wrightdk.repertory.data.local.dao.WatchRegionDao
import io.github.wrightdk.repertory.data.local.entity.Credit
import io.github.wrightdk.repertory.data.local.entity.Film
import io.github.wrightdk.repertory.data.local.entity.ImageConfig
import io.github.wrightdk.repertory.data.local.entity.Person
import io.github.wrightdk.repertory.data.local.entity.TrackedPerson
import io.github.wrightdk.repertory.data.local.entity.Viewing
import io.github.wrightdk.repertory.data.local.entity.WatchProvidersCache
import io.github.wrightdk.repertory.data.local.entity.WatchRegion

@Database(
    entities = [
        Person::class,
        TrackedPerson::class,
        Film::class,
        Credit::class,
        Viewing::class,
        ImageConfig::class,
        WatchProvidersCache::class,
        WatchRegion::class,
    ],
    // Unreleased: schema changes bump the version and the app is reinstalled.
    // No migrations until the first public release; from then on every bump needs one.
    // 3 -> 4 and 4 -> 5 only add tables, which Room migrates by itself, so test devices keep their data.
    version = 5,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5)],
)
@TypeConverters(Converters::class)
abstract class RepertoryDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun trackedPersonDao(): TrackedPersonDao
    abstract fun filmDao(): FilmDao
    abstract fun creditDao(): CreditDao
    abstract fun viewingDao(): ViewingDao
    abstract fun imageConfigDao(): ImageConfigDao
    abstract fun watchProvidersDao(): WatchProvidersDao
    abstract fun watchRegionDao(): WatchRegionDao

    companion object {
        fun build(context: Context): RepertoryDatabase =
            Room.databaseBuilder(context, RepertoryDatabase::class.java, "repertory.db").build()
    }
}
