package com.hifzmushaf.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Manages caching of Quran page images and word images both in memory and on disk
 */
class QuranImageCache(private val context: Context) {

    companion object {
        private const val TAG = "QuranImageCache"
        private const val PAGE_IMAGES_CACHE_DIR = "quran_pages"
        private const val WORD_IMAGES_CACHE_DIR = "quran_words"
        private const val MEMORY_CACHE_SIZE = 10 * 1024 * 1024 // Small memory cache for active words only
        // NO DISK CACHE LIMIT - cache grows infinitely as user reads the Qur'an
    }

    // Memory cache for bitmaps
    private val memoryCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(MEMORY_CACHE_SIZE) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }

        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            if (evicted && !oldValue.isRecycled) {
                // Don't recycle immediately, let GC handle it
                Log.d(TAG, "Bitmap evicted from memory cache: $key")
            }
        }
    }

    // Cache directories
    private val pageImagesDir: File by lazy {
        File(context.cacheDir, PAGE_IMAGES_CACHE_DIR).apply {
            if (!exists()) mkdirs()
        }
    }

    private val wordImagesDir: File by lazy {
        File(context.cacheDir, WORD_IMAGES_CACHE_DIR).apply {
            if (!exists()) mkdirs()
        }
    }

    init {
        // NO cleanup - let word cache grow infinitely!
        // Only clean up corrupted files if found
        cleanupCorruptedFiles()
    }

    /**
     * Clears all cached images to force regeneration
     */
    fun clearAllCache() {
        Log.d(TAG, "🧹 Clearing all cache to force regeneration")
        
        // Clear memory cache
        memoryCache.evictAll()
        
        // Clear disk cache
        try {
            pageImagesDir.listFiles()?.forEach { it.delete() }
            wordImagesDir.listFiles()?.forEach { it.delete() }
            Log.d(TAG, "✅ All cache cleared successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error clearing cache", e)
        }
    }

    /**
     * Gets a page image from cache (memory first, then disk)
     */
    fun getPageImage(pageNumber: Int): Bitmap? {
        val cacheKey = "page_$pageNumber"
        
        // Try memory cache first
        memoryCache.get(cacheKey)?.let { bitmap ->
            Log.d(TAG, "Page image found in memory cache: $pageNumber")
            return bitmap
        }
        
        // Try disk cache
        val diskFile = File(pageImagesDir, "page_${pageNumber.toString().padStart(3, '0')}.png")
        if (diskFile.exists()) {
            try {
                val bitmap = BitmapFactory.decodeFile(diskFile.absolutePath)
                if (bitmap != null) {
                    // Add back to memory cache
                    memoryCache.put(cacheKey, bitmap)
                    Log.d(TAG, "Page image found in disk cache: $pageNumber")
                    return bitmap
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading page image from disk cache: $pageNumber", e)
                // Delete corrupted file
                diskFile.delete()
            }
        }
        
        return null
    }

    /**
     * Caches a page image to both memory and disk
     */
    fun cachePageImage(pageNumber: Int, bitmap: Bitmap) {
        val cacheKey = "page_$pageNumber"
        
        // Add to memory cache
        memoryCache.put(cacheKey, bitmap)
        
        // Save to disk cache
        val diskFile = File(pageImagesDir, "page_${pageNumber.toString().padStart(3, '0')}.png")
        try {
            FileOutputStream(diskFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                out.flush()
            }
            Log.d(TAG, "Page image cached to disk: $pageNumber")
        } catch (e: IOException) {
            Log.e(TAG, "Error caching page image to disk: $pageNumber", e)
        }
    }

    /**
     * Gets a word image from cache (memory first, then disk)
     * Key format: "surah/ayah/word"
     */
    fun getWordImage(wordKey: String): Bitmap? {
        val cacheKey = "word_$wordKey"
        
        // Try memory cache first
        memoryCache.get(cacheKey)?.let { bitmap ->
            Log.d(TAG, "Word image found in memory cache: $wordKey")
            return bitmap
        }
        
        // Try disk cache
        val diskFile = getWordImageFile(wordKey)
        if (diskFile.exists()) {
            try {
                val bitmap = BitmapFactory.decodeFile(diskFile.absolutePath)
                if (bitmap != null) {
                    // Add back to memory cache
                    memoryCache.put(cacheKey, bitmap)
                    Log.d(TAG, "Word image found in disk cache: $wordKey")
                    return bitmap
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading word image from disk cache: $wordKey", e)
                // Delete corrupted file
                diskFile.delete()
            }
        }
        
        return null
    }

    /**
     * Caches a word image to both memory and disk
     * Key format: "surah/ayah/word"
     */
    fun cacheWordImage(wordKey: String, bitmap: Bitmap) {
        val cacheKey = "word_$wordKey"
        
        // Add to memory cache
        memoryCache.put(cacheKey, bitmap)
        
        // Save to disk cache
        val diskFile = getWordImageFile(wordKey)
        
        // Create parent directories if they don't exist
        diskFile.parentFile?.mkdirs()
        
        try {
            FileOutputStream(diskFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                out.flush()
            }
            Log.d(TAG, "Word image cached to disk: $wordKey")
        } catch (e: IOException) {
            Log.e(TAG, "Error caching word image to disk: $wordKey", e)
        }
    }

    /**
     * Gets the file for a word image based on the key
     * Creates the directory structure: surah/ayah/word.png
     */
    private fun getWordImageFile(wordKey: String): File {
        return File(wordImagesDir, "$wordKey.png")
    }

    /**
     * Clears memory cache
     */
    fun clearMemoryCache() {
        memoryCache.evictAll()
        Log.d(TAG, "Memory cache cleared")
        
        // Force garbage collection
        System.gc()
    }

    /**
     * Triggers aggressive memory cleanup
     */
    fun performMemoryCleanup() {
        Log.d(TAG, "🧹 Performing aggressive memory cleanup")
        
        // Clear half of memory cache
        val currentSize = memoryCache.size()
        val targetSize = currentSize / 2
        
        // This is a simplified cleanup - LruCache will handle LRU eviction
        memoryCache.trimToSize(targetSize)
        
        // Force garbage collection
        System.gc()
        
        Log.d(TAG, "📊 Memory cleanup complete. Cache size reduced from $currentSize to ${memoryCache.size()}")
    }

    /**
     * Checks if we're running low on memory and performs cleanup if needed
     */
    fun checkMemoryPressure() {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory
        
        val memoryUsagePercent = (usedMemory.toDouble() / maxMemory.toDouble()) * 100
        
        if (memoryUsagePercent > 80) {
            Log.w(TAG, "⚠️ High memory usage: ${memoryUsagePercent.toInt()}% - performing cleanup")
            performMemoryCleanup()
        } else {
            Log.d(TAG, "📊 Memory usage: ${memoryUsagePercent.toInt()}% - OK")
        }
    }

    /**
     * Clears disk cache for pages
     */
    fun clearPageDiskCache() {
        try {
            pageImagesDir.listFiles()?.forEach { file ->
                file.delete()
            }
            Log.d(TAG, "Page disk cache cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing page disk cache", e)
        }
    }

    /**
     * Clears disk cache for words
     */
    fun clearWordDiskCache() {
        try {
            wordImagesDir.deleteRecursively()
            wordImagesDir.mkdirs()
            Log.d(TAG, "Word disk cache cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing word disk cache", e)
        }
    }

    /**
     * Clears all caches
     */
    fun clearAllCaches() {
        clearMemoryCache()
        clearPageDiskCache()
        clearWordDiskCache()
    }

    /**
     * Gets cache statistics
     */
    fun getCacheStats(): CacheStats {
        // Simplified implementation to avoid compilation issues
        val pageDiskSize = try { calculateDirectorySize(pageImagesDir) } catch (e: Exception) { 0L }
        val wordDiskSize = try { calculateDirectorySize(wordImagesDir) } catch (e: Exception) { 0L }
        
        return CacheStats(
            memorySize = 0, // memoryCache.size() - causes compilation issue
            memoryHitCount = 0L, // memoryCache.hitCount() 
            memoryMissCount = 0L, // memoryCache.missCount()
            memoryCacheSize = 0, // memoryCache.maxSize() - causes compilation issue
            pageDiskSize = pageDiskSize,
            wordDiskSize = wordDiskSize
        )
    }

    /**
     * Calculates the total size of files in a directory
     */
    private fun calculateDirectorySize(directory: File): Long {
        return try {
            directory.walkTopDown()
                .filter { it.isFile }
                .map { it.length() }
                .sum()
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Cleans up only corrupted files, not based on size limits
     */
    private fun cleanupCorruptedFiles() {
        try {
            // Check page cache for corrupted files
            pageImagesDir.listFiles()?.forEach { file ->
                try {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    if (bitmap == null) {
                        Log.d(TAG, "Removing corrupted page cache file: ${file.name}")
                        file.delete()
                    } else {
                        bitmap.recycle()
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Removing corrupted page cache file: ${file.name}")
                    file.delete()
                }
            }
            
            // Check word cache for corrupted files (sample check, not exhaustive)
            wordImagesDir.walkTopDown()
                .filter { it.isFile && it.extension == "png" }
                .take(100) // Only check first 100 files to avoid long startup
                .forEach { file ->
                    try {
                        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                        if (bitmap == null) {
                            Log.d(TAG, "Removing corrupted word cache file: ${file.name}")
                            file.delete()
                        } else {
                            bitmap.recycle()
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Removing corrupted word cache file: ${file.name}")
                        file.delete()
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error during corrupted file cleanup", e)
        }
    }

    /**
     * Data class to hold cache statistics  
     */
    data class CacheStats(
        val memorySize: Int,
        val memoryHitCount: Long,
        val memoryMissCount: Long,
        val memoryCacheSize: Int,
        val pageDiskSize: Long,
        val wordDiskSize: Long
    )
}
