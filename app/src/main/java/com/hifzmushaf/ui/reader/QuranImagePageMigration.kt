package com.hifzmushaf.ui.reader

import android.content.Context
import android.util.Log

/**
 * Helper class to manage migration from the old QuranImagePageAdapter to the new QuranImagePageAdapterV2
 */
object QuranImagePageMigration {
    
    private const val TAG = "QuranImagePageMigration"
    private const val PREFS_NAME = "QuranImagePageMigration"
    private const val KEY_USE_V2_ADAPTER = "use_v2_adapter"
    private const val KEY_MIGRATION_COMPLETED = "migration_completed"
    
    /**
     * Checks if the V2 adapter should be used
     */
    fun shouldUseV2Adapter(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_USE_V2_ADAPTER, true) // Default to V2
    }
    
    /**
     * Enables the V2 adapter
     */
    fun enableV2Adapter(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_USE_V2_ADAPTER, true)
            .apply()
        Log.d(TAG, "V2 adapter enabled")
    }
    
    /**
     * Falls back to the V1 adapter (for debugging or compatibility)
     */
    fun fallbackToV1Adapter(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_USE_V2_ADAPTER, false)
            .apply()
        Log.d(TAG, "Fallback to V1 adapter")
    }
    
    /**
     * Marks migration as completed
     */
    fun markMigrationCompleted(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_MIGRATION_COMPLETED, true)
            .apply()
        Log.d(TAG, "Migration marked as completed")
    }
    
    /**
     * Checks if migration has been completed
     */
    fun isMigrationCompleted(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_MIGRATION_COMPLETED, false)
    }
    
    /**
     * Migrates existing cache data from V1 to V2 format
     */
    fun migrateExistingCache(context: Context) {
        if (isMigrationCompleted(context)) {
            Log.d(TAG, "Migration already completed, skipping...")
            return
        }
        
        try {
            Log.d(TAG, "Starting cache migration from V1 to V2...")
            
            // Initialize V2 cache
            val imageCache = QuranImageCache(context)
            
            // Check for old cache format and migrate if needed
            val oldCacheDir = java.io.File(context.cacheDir, "quran_pages")
            if (oldCacheDir.exists()) {
                val migratedFiles = oldCacheDir.listFiles()?.count() ?: 0
                Log.d(TAG, "Found $migratedFiles existing cache files, keeping them for V2")
                // The V2 cache uses the same directory structure, so no migration needed
            }
            
            markMigrationCompleted(context)
            Log.d(TAG, "Cache migration completed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during cache migration", e)
        }
    }
    
    /**
     * Clears all cache data (useful for troubleshooting)
     */
    fun clearAllCache(context: Context) {
        try {
            val imageCache = QuranImageCache(context)
            imageCache.clearAllCaches()
            
            // Reset migration status
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean(KEY_MIGRATION_COMPLETED, false)
                .apply()
                
            Log.d(TAG, "All cache cleared and migration status reset")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache", e)
        }
    }
}
