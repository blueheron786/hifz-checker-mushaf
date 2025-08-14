package com.hifzmushaf

import android.app.Application
import com.hifzmushaf.data.QuranDatabase

class QuranApplication : Application() {

    val database by lazy { QuranDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        // Database initialization will happen when 'database' property is accessed
    }
}