package io.github.wrightdk.repertory.data.local

import android.content.Context
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
import io.github.wrightdk.repertory.data.local.entity.Credit
import io.github.wrightdk.repertory.data.local.entity.Film
import io.github.wrightdk.repertory.data.local.entity.ImageConfig
import io.github.wrightdk.repertory.data.local.entity.Person
import io.github.wrightdk.repertory.data.local.entity.TrackedPerson
import io.github.wrightdk.repertory.data.local.entity.Viewing

@Database(
    entities = [
        Person::class,
        TrackedPerson::class,
        Film::class,
        Credit::class,
        Viewing::class,
        ImageConfig::class,
    ],
    // Unreleased: schema changes bump the version and the app is reinstalled.
    // No migrations until the first public release; from then on every bump needs one.
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class RepertoryDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun trackedPersonDao(): TrackedPersonDao
    abstract fun filmDao(): FilmDao
    abstract fun creditDao(): CreditDao
    abstract fun viewingDao(): ViewingDao
    abstract fun imageConfigDao(): ImageConfigDao

    companion object {
        fun build(context: Context): RepertoryDatabase =
            Room.databaseBuilder(context, RepertoryDatabase::class.java, "repertory.db").build()
    }
}
