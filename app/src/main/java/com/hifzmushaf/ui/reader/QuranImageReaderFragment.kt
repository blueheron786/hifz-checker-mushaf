package com.hifzmushaf.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hifzmushaf.MainActivity
import com.hifzmushaf.R
import com.hifzmushaf.data.repository.LastReadRepository
import com.hifzmushaf.data.QuranDatabase
import com.hifzmushaf.data.SurahRepository
import com.hifzmushaf.databinding.FragmentQuranImageReaderBinding
import com.hifzmushaf.databinding.ItemQuranImagePageBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.InputStream

data class WordBoundary(
    val page: Int,
    val line: Int,
    val word: Int,
    val surah: Int,
    val ayah: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val text: String? = null
)

data class PageInfo(
    val pageNumber: Int,
    val surahNumber: Int,
    val words: List<WordBoundary>
)

class QuranImageReaderFragment : Fragment() {

    // Data
    private var wordBoundaries: List<WordBoundary> = emptyList()
    private var pageInfoMap: Map<Int, PageInfo> = emptyMap()
    private var currentSurahNumber = 1
    private var totalPages = 604 // Standard Mushaf pages

    // Database
    private lateinit var lastReadRepo: LastReadRepository
    private lateinit var databaseHelper: DatabaseHelper
    private lateinit var qpcDataManager: QpcDataManager

    // Page read tracking
    private var pageReadCheckJob: Job? = null
    private var lastPageMarkedAsRead = -1
    private var startPageReadTime = 0L

    // View components
    private var _binding: FragmentQuranImageReaderBinding? = null
    private val binding get() = _binding!!
    private var currentPagePosition = 0
    private lateinit var pageAdapter: QuranImagePageAdapter
    private lateinit var pageAdapterV2: QuranImagePageAdapterV2
    private var usingV2Adapter = false
    private var maskedModeEnabled = true // Re-enabled masking with placeholders

    companion object {
        private const val PAGE_READ_DELAY_MS = 3000L
        private const val TAG = "QuranImageReader"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQuranImageReaderBinding.inflate(inflater, container, false)
        return binding.root
    }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (requireActivity() as AppCompatActivity).supportActionBar?.hide()
        super.onViewCreated(view, savedInstanceState)

        // Load the last marked page so we don't double-mark as read
        val prefs = requireContext().getSharedPreferences("QuranPrefs", Context.MODE_PRIVATE)
        lastPageMarkedAsRead = prefs.getInt("lastPageMarkedAsRead", -1)

        // Initialize database helpers
        val database = QuranDatabase.getDatabase(requireContext())
        lastReadRepo = LastReadRepository(database.lastReadPositionDao())
        databaseHelper = DatabaseHelper()
        qpcDataManager = QpcDataManager(requireContext())

        // Load word boundaries and page info
        loadWordBoundaries()

        // Set up fragment UI
        setupModeToggle()
        
        // Debug: Test connectivity to new image sources and ensure V2 adapter is used
        Log.d(TAG, "QuranImageReaderFragment initialized")
        Log.d(TAG, "Using V2 adapter: ${QuranImagePageMigration.shouldUseV2Adapter(requireContext())}")
        
        // Force enable V2 adapter for testing
        QuranImagePageMigration.enableV2Adapter(requireContext())
        Log.d(TAG, "Forced V2 adapter enabled")
        
        // Initialize ViewPager with current page
        val initialPage = arguments?.getInt("pageNumber", 1) ?: 1
        Log.d(TAG, "Setting up ViewPager with initial page: $initialPage")
        setupViewPager(initialPage)
        
        // Enable this for debugging: QuranImagePageDebugger.runFullConnectivityTest(requireContext())
    }

    private fun loadWordBoundaries() {
        try {
            // First try to load from JSON
            wordBoundaries = qpcDataManager.loadWordBoundaries()
            
            // If JSON is empty, try database
            if (wordBoundaries.isEmpty()) {
                Log.d(TAG, "JSON empty, trying database...")
                wordBoundaries = qpcDataManager.loadWordBoundariesFromDatabase()
            }
            
            // Build page info map
            if (wordBoundaries.isNotEmpty()) {
                buildPageInfoMap()
                Log.d(TAG, "Loaded ${wordBoundaries.size} word boundaries")
            } else {
                Log.w(TAG, "No word boundaries found, using fallback")
                // Log database schema for debugging
                Log.d(TAG, "Database schema:\n${qpcDataManager.getDatabaseSchema()}")
                createFallbackPageInfo()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading word boundaries", e)
            createFallbackPageInfo()
        }
    }

    private fun buildPageInfoMap() {
        // First, build map for pages with word boundaries
        val pagesWithBoundaries = wordBoundaries.groupBy { it.page }.mapValues { (pageNum, words) ->
            val surahNumber = words.firstOrNull()?.surah ?: 1
            Log.d(TAG, "📄 Page $pageNum: ${words.size} words, surah: $surahNumber")
            PageInfo(pageNum, surahNumber, words.sortedBy { it.line * 1000 + it.word })
        }
        
        // Then, create complete map with fallback for pages without boundaries
        val completePageMap = (1..totalPages).map { pageNum ->
            if (pagesWithBoundaries.containsKey(pageNum)) {
                pageNum to pagesWithBoundaries[pageNum]!!
            } else {
                val surahNumber = findSurahForPage(pageNum)
                pageNum to PageInfo(pageNum, surahNumber, emptyList())
            }
        }.toMap()
        
        pageInfoMap = completePageMap
        
        // Log summary of what we built
        val pagesWithData = pagesWithBoundaries.keys.sorted()
        val availableSurahs = pageInfoMap.values.map { it.surahNumber }.distinct().sorted()
        Log.d(TAG, "📊 PageInfoMap built: ${pageInfoMap.size} total pages, ${pagesWithBoundaries.size} with word data")
        Log.d(TAG, "📊 Pages with word boundaries: $pagesWithData")
        Log.d(TAG, "📊 Available surahs: $availableSurahs")
        
        // Log details for first few pages to verify
        (1..10).forEach { pageNum ->
            val pageInfo = pageInfoMap[pageNum]
            if (pageInfo != null) {
                Log.d(TAG, "   Page $pageNum -> Surah ${pageInfo.surahNumber} (${pageInfo.words.size} words)")
            }
        }
    }

    private fun createFallbackPageInfo() {
        // Create basic page info without word boundaries
        val fallbackPages = (1..totalPages).map { pageNum ->
            val surahNumber = findSurahForPage(pageNum)
            pageNum to PageInfo(pageNum, surahNumber, emptyList())
        }.toMap()
        pageInfoMap = fallbackPages
    }

    private fun findSurahForPage(pageNumber: Int): Int {
        // Use the same mapping as in QuranReaderFragment
        val surahStartPages = mapOf(
            1 to 1, 2 to 2, 3 to 50, 4 to 77, 5 to 106, 6 to 128, 7 to 151, 8 to 177,
            9 to 187, 10 to 208, 11 to 221, 12 to 235, 13 to 249, 14 to 255, 15 to 262,
            16 to 267, 17 to 282, 18 to 293, 19 to 305, 20 to 312, 21 to 322, 22 to 332,
            23 to 342, 24 to 350, 25 to 360, 26 to 367, 27 to 377, 28 to 386, 29 to 397,
            30 to 405, 31 to 411, 32 to 415, 33 to 418, 34 to 428, 35 to 434, 36 to 441,
            37 to 447, 38 to 453, 39 to 458, 40 to 468, 41 to 478, 42 to 483, 43 to 490,
            44 to 497, 45 to 499, 46 to 502, 47 to 508, 48 to 512, 49 to 516, 50 to 519,
            51 to 521, 52 to 524, 53 to 527, 54 to 529, 55 to 532, 56 to 535, 57 to 538,
            58 to 543, 59 to 546, 60 to 550, 61 to 552, 62 to 554, 63 to 555, 64 to 557,
            65 to 559, 66 to 561, 67 to 563, 68 to 565, 69 to 567, 70 to 569, 71 to 571,
            72 to 573, 73 to 575, 74 to 576, 75 to 578, 76 to 579, 77 to 581, 78 to 583,
            79 to 584, 80 to 586, 81 to 587, 82 to 588, 83 to 588, 84 to 590, 85 to 591,
            86 to 592, 87 to 592, 88 to 593, 89 to 594, 90 to 595, 91 to 596, 92 to 596,
            93 to 597, 94 to 597, 95 to 598, 96 to 598, 97 to 599, 98 to 599, 99 to 600,
            100 to 600, 101 to 601, 102 to 601, 103 to 602, 104 to 602, 105 to 602,
            106 to 603, 107 to 603, 108 to 603, 109 to 604, 110 to 604, 111 to 604,
            112 to 605, 113 to 605, 114 to 605
        )

        return surahStartPages.entries.lastOrNull { it.value <= pageNumber }?.key ?: 1
    }

    private fun findFirstPageForSurah(surahNumber: Int): Int {
        // Use the standard Madani Mushaf page mapping from SurahRepository
        val surah = SurahRepository.getSurahByNumber(surahNumber)
        if (surah != null) {
            Log.d(TAG, "✅ Found surah $surahNumber (${surah.englishName}) starts on page ${surah.startPage}")
            return surah.startPage
        } else {
            Log.w(TAG, "⚠️ Surah $surahNumber not found in repository, defaulting to page 1")
            return 1
        }
    }

    private fun setupViewPager(initialPage: Int) {
        // Handle surah navigation - check if we need to navigate to a specific surah
        val surahNumber = arguments?.getInt("surahNumber")
        val effectiveInitialPage = if (surahNumber != null && surahNumber > 0) {
            val pageForSurah = findFirstPageForSurah(surahNumber)
            Log.d(TAG, "🕌 Navigating to surah $surahNumber, standard page: $pageForSurah")
            
            // Check if we have content for this page in our limited database
            if (pageInfoMap.containsKey(pageForSurah)) {
                // We have actual content for this page
                Log.d(TAG, "✅ Page $pageForSurah has content available")
                currentSurahNumber = surahNumber
                pageForSurah
            } else {
                // We don't have content for this page, show message and redirect to available content
                Log.w(TAG, "⚠️ Page $pageForSurah for surah $surahNumber not available in database")
                
                val surah = SurahRepository.getSurahByNumber(surahNumber)
                val surahName = surah?.englishName ?: "Surah $surahNumber"
                
                // Show a toast to inform user
                view?.post {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "$surahName not available. Showing Al-Baqarah instead.",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                
                // Default to page 2 where we have actual content
                2
            }
        } else {
            initialPage
        }
        
        // Force use of V2 adapter for debugging
        QuranImagePageMigration.enableV2Adapter(requireContext())
        usingV2Adapter = true // Force V2 for debugging
        
        Log.d(TAG, "Setting up ViewPager with V2 adapter forced ON")
        Log.d(TAG, "Total pages: $totalPages")
        Log.d(TAG, "PageInfoMap size: ${pageInfoMap.size}")
        Log.d(TAG, "Effective initial page: $effectiveInitialPage (requested: $initialPage, surah: $surahNumber)")
        
        if (usingV2Adapter) {
            // Use the new V2 adapter with enhanced features
            pageAdapterV2 = QuranImagePageAdapterV2(
                fragment = this,
                totalPages = totalPages,
                pageInfoMap = pageInfoMap
            )
            
            Log.d(TAG, "📋 Created V2 adapter with $totalPages pages")
            
            binding.quranImagePager.adapter = pageAdapterV2
            
            Log.d(TAG, "📎 Adapter attached to ViewPager2")
            
            // Initialize masking mode immediately after adapter creation
            pageAdapterV2.updateMaskedMode(maskedModeEnabled)
            Log.d(TAG, "🔒 Initialized adapter with masking mode: $maskedModeEnabled")
            
            // Perform migration if needed
            QuranImagePageMigration.migrateExistingCache(requireContext())
            
            Log.d(TAG, "✅ Using QuranImagePageAdapterV2 with enhanced features")
        } else {
            // Use the original adapter for compatibility
            pageAdapter = QuranImagePageAdapter(
                fragment = this,
                totalPages = totalPages,
                pageInfoMap = pageInfoMap
            )
            binding.quranImagePager.adapter = pageAdapter
            
            Log.d(TAG, "⚠️ Using original QuranImagePageAdapter")
        }

        binding.quranImagePager.layoutDirection = View.LAYOUT_DIRECTION_RTL
        binding.quranImagePager.setCurrentItem(effectiveInitialPage - 1, false) // Convert to 0-based

        // Set up page change listener to handle navigation properly
        setupPageChangeListener()

        binding.quranImagePager.post {
            binding.quranImagePager.setCurrentItem(effectiveInitialPage - 1, false)
            currentPagePosition = effectiveInitialPage - 1
            updateHeader(currentSurahNumber, effectiveInitialPage)
        }
    }

    private fun setupPageChangeListener() {
        binding.quranImagePager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                Log.d(TAG, "Page selected: ${position + 1}")
                
                currentPagePosition = position
                val pageNumber = position + 1
                val pageInfo = pageInfoMap[pageNumber]
                val surahNumber = pageInfo?.surahNumber ?: currentSurahNumber
                
                updateHeader(surahNumber, pageNumber)
                
                // Save position immediately when page changes
                saveCurrentPosition(pageNumber)
                
                // Page read tracking
                startPageReadTracking()
            }
        })
    }

    private fun setupModeToggle() {
        // Set up the masking toggle button
        binding.maskingToggleButton.setOnClickListener {
            toggleMaskedMode()
        }
        updateMaskingButton()
    }
    
    private fun toggleMaskedMode() {
        maskedModeEnabled = !maskedModeEnabled
        
        if (usingV2Adapter) {
            // Update V2 adapter
            if (::pageAdapterV2.isInitialized) {
                pageAdapterV2.updateMaskedMode(maskedModeEnabled)
            }
        } else {
            // Clear V1 adapter cache to force reload
            if (::pageAdapter.isInitialized) {
                pageAdapter.clearCache()
                
                // Refresh current page
                pageAdapter.notifyItemChanged(currentPagePosition)
            }
        }
        
        updateMaskingButton()
        
        Log.d(TAG, "Toggled to ${if (maskedModeEnabled) "masked" else "normal"} mode")
    }
    
    private fun updateModeIndicator() {
        // Remove the mode indicator from the page info text
        // Mode indicator will be shown separately
    }
    
    private fun updateMaskingButton() {
        val buttonText = if (maskedModeEnabled) "🔒 Masking ON" else "👁️ Masking OFF"
        binding.maskingToggleButton.text = buttonText
    }
    
    private fun updateHeader(surahNumber: Int, pageNumber: Int) {
        val surah = SurahRepository.getSurahByNumber(surahNumber)
        val surahName = surah?.englishName ?: "Unknown"
        
        // Use regular English numerals
        val baseText = "Page $pageNumber - Surah $surahNumber: $surahName"
        
        binding.pageInfoTextView.text = baseText
    }

    fun isMaskedMode(): Boolean = maskedModeEnabled
    
    fun onWordClicked(word: WordBoundary) {
        Log.d(TAG, "Word clicked: ${word.text} - Surah ${word.surah}, Ayah ${word.ayah}, Word ${word.word}")
        // Word reveal is now handled automatically in QuranImagePage
    }

    private fun convertToArabicNumerals(number: Int): String {
        val arabicNumerals = arrayOf("٠", "١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩")
        return number.toString().map { digit ->
            if (digit.isDigit()) {
                arabicNumerals[digit.digitToInt()]
            } else {
                digit.toString()
            }
        }.joinToString("")
    }

    private fun startPageReadTracking() {
        pageReadCheckJob?.cancel()
        startPageReadTime = System.currentTimeMillis()
        
        pageReadCheckJob = lifecycleScope.launch {
            delay(PAGE_READ_DELAY_MS)
            
            val currentTime = System.currentTimeMillis()
            if (currentTime - startPageReadTime >= PAGE_READ_DELAY_MS) {
                markPageAsRead(currentPagePosition + 1) // Convert to 1-based
            }
        }
    }

    private fun markPageAsRead(pageNumber: Int) {
        if (pageNumber == lastPageMarkedAsRead) return
        
        val pageInfo = pageInfoMap[pageNumber]
        val surahNumber = pageInfo?.surahNumber ?: 1
        
        databaseHelper.saveReadingProgress(
            surahNumber = surahNumber,
            ayahNumber = 1,
            pageNumber = pageNumber,
            scrollY = 0 // No scrolling in image-based view
        )
        
        lastPageMarkedAsRead = pageNumber
        
        requireContext().getSharedPreferences("QuranPrefs", Context.MODE_PRIVATE).edit()
            .putInt("lastPageMarked", pageNumber)
            .putInt("lastSurah", surahNumber)
            .putInt("lastPage", pageNumber)
            .apply()
    }

    private fun saveCurrentPosition(pageNumber: Int) {
        val pageInfo = pageInfoMap[pageNumber]
        val surahNumber = pageInfo?.surahNumber ?: 1
        
        databaseHelper.saveReadingProgress(
            surahNumber = surahNumber,
            ayahNumber = 1,
            pageNumber = pageNumber,
            scrollY = 0
        )
    }

    private fun loadTextFromRaw(fileName: String): String {
        return try {
            val resourceId = resources.getIdentifier(
                fileName.substringBeforeLast('.'), 
                "raw", 
                requireContext().packageName
            )
            if (resourceId != 0) {
                resources.openRawResource(resourceId).bufferedReader().use { it.readText() }
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading $fileName", e)
            ""
        }
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as MainActivity).setBottomNavVisibility(false)
    }

    override fun onPause() {
        super.onPause()
        pageReadCheckJob?.cancel()
        saveCurrentPosition(currentPagePosition + 1)
        (requireActivity() as? MainActivity)?.setBottomNavVisibility(true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pageReadCheckJob?.cancel()
        (requireActivity() as? MainActivity)?.setBottomNavVisibility(true)
        
        // Clear bitmap cache to prevent memory leaks
        if (usingV2Adapter && ::pageAdapterV2.isInitialized) {
            pageAdapterV2.clearCache()
        } else if (::pageAdapter.isInitialized) {
            pageAdapter.clearCache()
        }
        
        if (::databaseHelper.isInitialized) {
            databaseHelper.cleanup()
        }
        _binding = null
    }

    private inner class DatabaseHelper {
        fun saveReadingProgress(surahNumber: Int, ayahNumber: Int, pageNumber: Int, scrollY: Int) {
            lifecycleScope.launch {
                try {
                    lastReadRepo.savePosition(
                        surah = surahNumber,
                        ayah = ayahNumber,
                        page = pageNumber,
                        scrollY = scrollY
                    )
                    Log.d(TAG, "Saved reading progress: page=$pageNumber, surah=$surahNumber")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save reading progress", e)
                }
            }
        }

        fun cleanup() {
            // Any cleanup needed
        }
    }
}
