package com.quranhabit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.quranhabit.data.entity.LastReadPosition

@Dao
interface LastReadPositionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(position: LastReadPosition)

    @Query("SELECT * FROM last_read_position LIMIT 1")
    suspend fun getLastPosition(): LastReadPosition?

    @Query("SELECT scrollY FROM last_read_position WHERE page = :page LIMIT 1")
    suspend fun getScrollPosition(page: Int): Int?
}
