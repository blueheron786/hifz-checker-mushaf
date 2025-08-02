package com.quranhabit.utils

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.ZoneOffset

class DateConverter {
    @TypeConverter
    fun fromTimestamp(value: Long?): LocalDate? {
        return value?.let {
            LocalDate.ofEpochDay(it)
        }
    }

    @TypeConverter
    fun dateToTimestamp(date: LocalDate?): Long? {
        return date?.toEpochDay()
    }
}