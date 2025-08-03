package com.quranhabit.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.quranhabit.data.dao.LastReadPositionDao
import com.quranhabit.data.dao.StatisticsDao
import com.quranhabit.data.entity.LastReadPosition
import com.quranhabit.data.entity.PagesReadOnDay

@Database(
    entities = [PagesReadOnDay::class, LastReadPosition::class],
    version = 4,
    exportSchema = false
)
abstract class QuranDatabase : RoomDatabase() {
    abstract fun statisticsDao(): StatisticsDao
    abstract fun lastReadPositionDao(): LastReadPositionDao

    companion object {
        @Volatile
        private var INSTANCE: QuranDatabase? = null
        fun getDatabase(context: Context): QuranDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    QuranDatabase::class.java,
                    "quran_database"
                )
                    .fallbackToDestructiveMigration() // For development only
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}