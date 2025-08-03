package com.quranhabit.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "last_read_position")
data class LastReadPosition(
    @PrimaryKey val id: Int = 1, // Single row
    val surah: Int,
    val ayah: Int,
    val page: Int,
    val scrollY: Int,
    val timestamp: Long
)