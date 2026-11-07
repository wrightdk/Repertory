package io.github.wrightdk.repertory.data.local

import androidx.room.TypeConverter
import io.github.wrightdk.repertory.data.WatchAvailability
import java.time.LocalDate
import kotlinx.serialization.json.Json

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

    /** Watch providers cache. Throws on a row that won't parse; the repository treats that as no row. */
    @TypeConverter
    fun watchAvailabilityToJson(availability: WatchAvailability): String = json.encodeToString(WatchAvailability.serializer(), availability)

    @TypeConverter
    fun jsonToWatchAvailability(value: String): WatchAvailability = json.decodeFromString(WatchAvailability.serializer(), value)

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
