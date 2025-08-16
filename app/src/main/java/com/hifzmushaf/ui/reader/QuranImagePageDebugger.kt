package com.hifzmushaf.ui.reader

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Debug utilities for testing QuranImagePage functionality
 */
object QuranImagePageDebugger {
    
    private const val TAG = "QuranImagePageDebugger"
    
    /**
     * Tests connectivity to Qpc14LinesV2 image source
     */
    fun testQpc14LinesV2Connectivity(context: Context, pageNumber: Int = 1) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val pageNumberPadded = pageNumber.toString().padStart(3, '0')
                val testUrl = "https://qpc.ksu.edu.sa/pages/qpc14linesv2/page$pageNumberPadded.png"
                
                Log.d(TAG, "Testing Qpc14LinesV2 connectivity: $testUrl")
                
                val url = URL(testUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "HEAD"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.setRequestProperty("User-Agent", "QuranImageReader/2.0")
                
                val responseCode = connection.responseCode
                connection.disconnect()
                
                withContext(Dispatchers.Main) {
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        Log.i(TAG, "✅ Qpc14LinesV2 connectivity test PASSED (HTTP $responseCode)")
                    } else {
                        Log.w(TAG, "⚠️ Qpc14LinesV2 connectivity test returned HTTP $responseCode")
                    }
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "❌ Qpc14LinesV2 connectivity test FAILED", e)
                }
            }
        }
    }
    
    /**
     * Tests connectivity to BlackImagesWordByWord source
     */
    fun testBlackImagesWordByWordConnectivity(context: Context, surah: Int = 1, ayah: Int = 1, word: Int = 1) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val testUrl = "https://api.qurancdn.com/api/qdc/words/$surah/$ayah/$word.png"
                
                Log.d(TAG, "Testing BlackImagesWordByWord connectivity: $testUrl")
                
                val url = URL(testUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "HEAD"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.setRequestProperty("User-Agent", "QuranImageReader/2.0")
                
                val responseCode = connection.responseCode
                connection.disconnect()
                
                withContext(Dispatchers.Main) {
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        Log.i(TAG, "✅ BlackImagesWordByWord connectivity test PASSED (HTTP $responseCode)")
                    } else {
                        Log.w(TAG, "⚠️ BlackImagesWordByWord connectivity test returned HTTP $responseCode")
                    }
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "❌ BlackImagesWordByWord connectivity test FAILED", e)
                }
            }
        }
    }
    
    /**
     * Tests both image sources
     */
    fun runFullConnectivityTest(context: Context) {
        Log.d(TAG, "🧪 Running full connectivity test for QuranImagePage sources...")
        testQpc14LinesV2Connectivity(context, 1)
        testBlackImagesWordByWordConnectivity(context, 1, 1, 1)
    }
    
    /**
     * Prints cache statistics
     */
    fun printCacheStats(context: Context) {
        try {
            val imageCache = QuranImageCache(context)
            val stats = imageCache.getCacheStats()
            
            Log.i(TAG, "📊 QuranImageCache Statistics:")
            Log.i(TAG, "   Memory Cache Size: ${stats.memorySize} bytes")
            Log.i(TAG, "   Memory Cache Hits: ${stats.memoryHitCount}")
            Log.i(TAG, "   Memory Cache Misses: ${stats.memoryMissCount}")
            Log.i(TAG, "   Memory Cache Max Size: ${stats.memoryCacheSize} bytes")
            Log.i(TAG, "   Page Disk Cache Size: ${stats.pageDiskSize} bytes")
            Log.i(TAG, "   Word Disk Cache Size: ${stats.wordDiskSize} bytes")
            Log.i(TAG, "   Total Disk Cache Size: ${stats.pageDiskSize + stats.wordDiskSize} bytes")
            
            val hitRate = if (stats.memoryHitCount + stats.memoryMissCount > 0) {
                (stats.memoryHitCount.toFloat() / (stats.memoryHitCount + stats.memoryMissCount) * 100)
            } else {
                0f
            }
            Log.i(TAG, "   Memory Cache Hit Rate: %.1f%%".format(hitRate))
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting cache statistics", e)
        }
    }
    
    /**
     * Forces a specific adapter version for testing
     */
    fun forceAdapterVersion(context: Context, useV2: Boolean) {
        if (useV2) {
            QuranImagePageMigration.enableV2Adapter(context)
            Log.i(TAG, "🔄 Forced to use QuranImagePageAdapterV2")
        } else {
            QuranImagePageMigration.fallbackToV1Adapter(context)
            Log.i(TAG, "🔄 Forced to use original QuranImagePageAdapter")
        }
        Log.i(TAG, "📝 Please restart the fragment to apply changes")
    }
    
    /**
     * Clears all caches for testing
     */
    fun clearAllCachesForTesting(context: Context) {
        QuranImagePageMigration.clearAllCache(context)
        Log.i(TAG, "🧹 All caches cleared for testing")
    }
}
