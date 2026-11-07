package io.github.wrightdk.repertory.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.github.wrightdk.repertory.data.local.dao.CreditDao
import io.github.wrightdk.repertory.data.local.dao.FilmDao
import io.github.wrightdk.repertory.data.local.dao.PersonDao
import io.github.wrightdk.repertory.data.local.dao.TrackedPersonDao
import io.github.wrightdk.repertory.data.local.dao.ViewingDao
import io.github.wrightdk.repertory.data.local.entity.Credit
import io.github.wrightdk.repertory.data.local.entity.Film
import io.github.wrightdk.repertory.data.local.entity.Person
import io.github.wrightdk.repertory.data.local.entity.TrackedPerson
import io.github.wrightdk.repertory.data.local.entity.Viewing

@Database(
    entities = [Person::class, TrackedPerson::class, Film::class, Credit::class, Viewing::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class RepertoryDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun trackedPersonDao(): TrackedPersonDao
    abstract fun filmDao(): FilmDao
    abstract fun creditDao(): CreditDao
    abstract fun viewingDao(): ViewingDao

    companion object {
        fun build(context: Context): RepertoryDatabase =
            Room.databaseBuilder(context, RepertoryDatabase::class.java, "repertory.db").build()
    }
}
