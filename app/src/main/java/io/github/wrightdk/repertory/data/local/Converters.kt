package io.github.wrightdk.repertory.data.local

import androidx.room.TypeConverter
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun localDateToEpochDay(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter
    fun epochDayToLocalDate(epochDay: Long?): LocalDate? = epochDay?.let(LocalDate::ofEpochDay)

    /** For short lists of simple tokens (TMDB image sizes). Items must not contain commas. */
    @TypeConverter
    fun stringListToString(list: List<String>): String = list.joinToString(",")

    @TypeConverter
    fun stringToStringList(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split(",")

    @TypeConverter
    fun intListToString(list: List<Int>): String = list.joinToString(",")

    @TypeConverter
    fun stringToIntList(value: String): List<Int> =
        if (value.isEmpty()) emptyList() else value.split(",").map(String::toInt)
}
