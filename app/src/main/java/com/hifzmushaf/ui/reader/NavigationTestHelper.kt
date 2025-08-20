package com.hifzmushaf.ui.reader

import android.util.Log
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.*

/**
 * Helper class for automated navigation testing to detect blank screen issues
 */
class NavigationTestHelper(
    private val viewPager: ViewPager2,
    private val fragment: QuranImageReaderFragment
) {
    companion object {
        private const val TAG = "NavigationTestHelper"
    }
    
    private var testJob: Job? = null
    
    /**
     * Test basic forward/backward navigation patterns
     */
    fun testBasicNavigation() {
        testJob = CoroutineScope(Dispatchers.Main).launch {
            Log.d(TAG, "🧪 Starting basic navigation test...")
            
            // Test 1: Forward navigation
            Log.d(TAG, "📈 Test 1: Forward navigation 1→2→3")
            navigateToPage(1, "Initial page")
            delay(2000)
            navigateToPage(2, "Forward to 2")
            delay(2000)
            navigateToPage(3, "Forward to 3")
            delay(2000)
            
            // Test 2: Backward navigation
            Log.d(TAG, "📉 Test 2: Backward navigation 3→2→1")
            navigateToPage(2, "Backward to 2")
            delay(2000)
            navigateToPage(1, "Backward to 1")
            delay(2000)
            
            Log.d(TAG, "✅ Basic navigation test completed")
        }
    }
    
    /**
     * Test problematic direction change scenarios
     */
    fun testDirectionChanges() {
        testJob = CoroutineScope(Dispatchers.Main).launch {
            Log.d(TAG, "🧪 Starting direction change test...")
            
            // Test 1: Forward then backward
            Log.d(TAG, "🔄 Test 1: Forward→Backward (1→2→1)")
            navigateToPage(1, "Start at 1")
            delay(2000)
            navigateToPage(2, "Forward to 2")
            delay(2000)
            navigateToPage(1, "CRITICAL: Backward to 1 (direction change)")
            delay(3000) // Give more time to observe
            
            // Test 2: Backward then forward
            Log.d(TAG, "🔄 Test 2: Backward→Forward (2→1→2)")
            navigateToPage(2, "Start at 2")
            delay(2000)
            navigateToPage(1, "Backward to 1")
            delay(2000)
            navigateToPage(2, "CRITICAL: Forward to 2 (direction change)")
            delay(3000)
            
            // Test 3: Multiple direction changes
            Log.d(TAG, "🔄 Test 3: Multiple changes (1→3→1→3)")
            navigateToPage(1, "Start at 1")
            delay(2000)
            navigateToPage(3, "Jump to 3")
            delay(2000)
            navigateToPage(1, "CRITICAL: Jump back to 1")
            delay(2000)
            navigateToPage(3, "CRITICAL: Jump to 3 again")
            delay(3000)
            
            Log.d(TAG, "✅ Direction change test completed")
        }
    }
    
    /**
     * Test rapid navigation to stress test the system
     */
    fun testRapidNavigation() {
        testJob = CoroutineScope(Dispatchers.Main).launch {
            Log.d(TAG, "🧪 Starting rapid navigation test...")
            
            val pages = listOf(1, 3, 1, 2, 3, 1, 2, 1, 3, 2)
            
            for ((index, page) in pages.withIndex()) {
                Log.d(TAG, "⚡ Rapid nav ${index + 1}/${pages.size}: → $page")
                navigateToPage(page, "Rapid nav to $page")
                delay(1000) // Shorter delay for rapid testing
            }
            
            Log.d(TAG, "✅ Rapid navigation test completed")
        }
    }
    
    /**
     * Comprehensive test that combines all scenarios
     */
    fun runComprehensiveTest() {
        testJob = CoroutineScope(Dispatchers.Main).launch {
            Log.d(TAG, "🚀 Starting comprehensive navigation test...")
            
            testBasicNavigation()
            delay(5000) // Pause between tests
            
            testDirectionChanges()
            delay(5000)
            
            testRapidNavigation()
            
            Log.d(TAG, "🏁 Comprehensive test completed!")
        }
    }
    
    private suspend fun navigateToPage(pageNumber: Int, description: String) {
        Log.d(TAG, "🧭 $description")
        Log.d(TAG, "📊 Current page: ${viewPager.currentItem + 1} → Target: $pageNumber")
        
        withContext(Dispatchers.Main) {
            viewPager.setCurrentItem(pageNumber - 1, true) // Convert to 0-based index
        }
        
        // Give some time for the navigation animation
        delay(500)
        
        // Verify the navigation succeeded
        val actualPage = viewPager.currentItem + 1
        if (actualPage == pageNumber) {
            Log.d(TAG, "✅ Successfully navigated to page $pageNumber")
        } else {
            Log.e(TAG, "❌ Navigation failed! Expected: $pageNumber, Actual: $actualPage")
        }
    }
    
    /**
     * Stop any running test
     */
    fun stopTest() {
        testJob?.cancel()
        Log.d(TAG, "🛑 Navigation test stopped")
    }
    
    /**
     * Check if test is currently running
     */
    fun isTestRunning(): Boolean = testJob?.isActive == true
}
