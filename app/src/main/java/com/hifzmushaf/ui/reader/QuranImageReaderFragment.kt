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
    private var maskedModeEnabled = false // Start in normal mode to show images immediately

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
        lastPageMarkedAsRead = prefs.getInt("lastPageMarked", -1)

        // Initialize database
        lastReadRepo = LastReadRepository(QuranDatabase.getDatabase(requireContext()).lastReadPositionDao())
        
        // Initialize QPC data manager
        qpcDataManager = QpcDataManager(requireContext())

        // Initialize data
        loadWordBoundaries()

        // Get arguments
        currentSurahNumber = arguments?.getInt("surahNumber") ?: 1
        val page = arguments?.getInt("pageNumber")?.takeIf { it != 0 } ?: findFirstPageForSurah(currentSurahNumber)

        setupViewPager(page)
        updateHeader(currentSurahNumber, page)
        setupPageChangeListener()
        setupModeToggle()

        // Initialize database helper
        databaseHelper = DatabaseHelper()
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
        pageInfoMap = wordBoundaries.groupBy { it.page }.mapValues { (pageNum, words) ->
            val surahNumber = words.firstOrNull()?.surah ?: 1
            PageInfo(pageNum, surahNumber, words.sortedBy { it.line * 1000 + it.word })
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
        return pageInfoMap.entries.firstOrNull { it.value.surahNumber == surahNumber }?.key ?: 1
    }

    private fun setupViewPager(initialPage: Int) {
        pageAdapter = QuranImagePageAdapter(
            fragment = this,
            totalPages = totalPages,
            pageInfoMap = pageInfoMap
        )

        binding.quranImagePager.adapter = pageAdapter
        binding.quranImagePager.layoutDirection = View.LAYOUT_DIRECTION_RTL
        binding.quranImagePager.setCurrentItem(initialPage - 1, false) // Convert to 0-based

        binding.quranImagePager.post {
            binding.quranImagePager.setCurrentItem(initialPage - 1, false)
            currentPagePosition = initialPage - 1
            updateHeader(currentSurahNumber, initialPage)
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
        // Add click listener to header to toggle between masked and normal mode
        binding.pageInfoTextView.setOnClickListener {
            toggleMaskedMode()
        }
        
        // Update header to show current mode
        updateModeIndicator()
    }
    
    private fun toggleMaskedMode() {
        maskedModeEnabled = !maskedModeEnabled
        
        // Clear adapter cache to force reload
        if (::pageAdapter.isInitialized) {
            pageAdapter.clearCache()
            
            // Refresh current page
            pageAdapter.notifyItemChanged(currentPagePosition)
        }
        
        updateModeIndicator()
        
        Log.d(TAG, "Toggled to ${if (maskedModeEnabled) "masked" else "normal"} mode")
    }
    
    private fun updateModeIndicator() {
        val mode = if (maskedModeEnabled) "🔒" else "👁️"
        binding.pageInfoTextView.text = "${binding.pageInfoTextView.text} $mode"
    }
    
    private fun updateHeader(surahNumber: Int, pageNumber: Int) {
        val surah = SurahRepository.getSurahByNumber(surahNumber)
        val surahName = surah?.arabicName ?: "Unknown"
        
        // Convert numbers to fancy Arabic-Indic numerals
        val fancyPageNumber = convertToArabicNumerals(pageNumber)
        val fancySurahNumber = convertToArabicNumerals(surahNumber)
        
        val baseText = "صفحة $fancyPageNumber \\ سورة $fancySurahNumber - $surahName"
        val mode = if (maskedModeEnabled) "🔒" else "👁️"
        
        binding.pageInfoTextView.text = "$baseText $mode"
    }

    fun isMaskedMode(): Boolean = maskedModeEnabled

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
        if (::pageAdapter.isInitialized) {
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
