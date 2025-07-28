package com.quranhabit

import android.app.Application
import com.quranhabit.data.QuranDatabase

class QuranApplication : Application() {

    val database by lazy { QuranDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        // Database initialization will happen when 'database' property is accessed
    }
}