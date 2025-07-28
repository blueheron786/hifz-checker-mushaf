package com.quranhabit.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pages_read_on_day")
data class PagesReadOnDay(
    @PrimaryKey val date: String, // "YYYY-MM-DD"
    val pagesRead: Int,
    val secondsSpendReading: Int
)