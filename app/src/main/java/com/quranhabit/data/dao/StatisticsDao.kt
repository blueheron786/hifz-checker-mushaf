package com.quranhabit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.quranhabit.data.entity.PagesReadOnDay
import com.quranhabit.ui.statistics.DailyData

@Dao
interface StatisticsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: PagesReadOnDay)

    @Query("SELECT * FROM pages_read_on_day WHERE date = :date")
    suspend fun getByDate(date: String): PagesReadOnDay?

    @Query("SELECT COALESCE(SUM(pagesRead), 0) FROM pages_read_on_day")
    suspend fun getTotalPagesRead(): Int

    @Query("SELECT SUM(secondsSpendReading) FROM pages_read_on_day WHERE date = :date")
    suspend fun getTimeReadToday(date: String): Int?

    @Query("SELECT COALESCE(SUM(secondsSpendReading), 0) FROM pages_read_on_day")
    suspend fun getTotalTimeRead(): Int

    @Query("SELECT COALESCE(SUM(secondsSpendReading), 0) FROM pages_read_on_day WHERE date = :date")
    suspend fun getTimeSpentToday(date: String): Int

    @Query("SELECT COALESCE(SUM(secondsSpendReading), 0) FROM pages_read_on_day")
    suspend fun getTotalTimeSpent(): Int

    @Query("DELETE FROM pages_read_on_day")
    suspend fun resetAllStatistics()

    @Query("""
    SELECT strftime('%Y-%m-%d', date('now', '-' || (CAST(:daysParam AS INTEGER) - 1) || ' days')) AS date, 0 AS pagesRead, 0 AS secondsReading
    UNION ALL
    SELECT strftime('%Y-%m-%d', date) AS date, pagesRead, secondsSpendReading
        FROM pages_read_on_day
        WHERE date >= strftime('%Y-%m-%d', date('now', '-' || (CAST(:daysParam AS INTEGER) - 1) || ' days'))
    ORDER BY date ASC
""")
    suspend fun getTimeRangeStatsData(daysParam: Int): List<DailyData>

    @Query("""
    WITH RECURSIVE streak_days AS (
        -- Base case: today's reading if it meets the criteria
        SELECT date, 
               CASE WHEN pagesRead >= 1 OR secondsSpendReading >= 60 THEN 1 ELSE 0 END AS hasRead,
               1 AS dayNumber
        FROM pages_read_on_day
        WHERE date = strftime('%Y-%m-%d', 'now')
        
        UNION ALL
        
        -- Recursive case: previous days in the streak
        SELECT p.date, 
               CASE WHEN p.pagesRead >= 1 OR p.secondsSpendReading >= 60 THEN 1 ELSE 0 END AS hasRead,
               sd.dayNumber + 1
        FROM pages_read_on_day p
        JOIN streak_days sd ON p.date = strftime('%Y-%m-%d', date(sd.date, '-1 day'))
        WHERE sd.hasRead = 1
    )
    SELECT MAX(dayNumber) 
    FROM streak_days 
    WHERE hasRead = 1
""")
    suspend fun getCurrentStreakDays(): Int
}


