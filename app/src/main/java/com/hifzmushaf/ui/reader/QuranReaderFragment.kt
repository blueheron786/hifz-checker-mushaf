package com.hifzmushaf.ui.reader

import android.content.Context
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.util.SparseIntArray
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
import com.hifzmushaf.databinding.FragmentQuranReaderBinding
import com.hifzmushaf.databinding.ItemPageBinding
import com.hifzmushaf.ui.surah.Surah
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class QuranReaderFragment : Fragment() {

    // Data
    private lateinit var allPages: List<List<String>>
    private var currentSurahNumber = 1
    
    // Simplified page mapping for navigation
    private val pageToSurahMap = mutableMapOf<Int, Int>()

    // Database
    private lateinit var lastReadRepo: LastReadRepository
    private lateinit var databaseHelper: DatabaseHelper

    // Page read tracking
    private var pageReadCheckJob: Job? = null
    private var lastPageMarkedAsRead = -1
    private var startPageReadTime = 0L

    // View components
    private var _binding: FragmentQuranReaderBinding? = null
    private val binding get() = _binding!!
    private val scrollPositions = SparseIntArray()
    private var currentPagePosition = 0
    private lateinit var pageAdapter: QuranPageAdapter

    companion object {
        private const val PAGE_READ_DELAY_MS = 3000L
        private const val PAGE_READ_CHECK_INTERVAL = 1000L
    }

    private val cachedPages by lazy {
        val json = loadTextFromRaw(R.raw.text)
        val pages = Gson().fromJson<List<List<String>>>(
            json,
            object : TypeToken<List<List<String>>>() {}.type
        )
        
        // Build surah mapping while loading
        buildSurahMapping(pages)
        
        pages
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQuranReaderBinding.inflate(inflater, container, false)
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

        // Initialize data
        allPages = cachedPages

        // Get the selected surah number from arguments
        currentSurahNumber = arguments?.getInt("surahNumber") ?: 1
        val ayahNumber = arguments?.getInt("ayahNumber")?.takeIf { it != 0 } ?: 1
        val page = arguments?.getInt("pageNumber")?.takeIf { it != 0 } ?: findFirstPageForSurah(currentSurahNumber)
        val scrollY = arguments?.getInt("scrollY") ?: 0

        setupViewPager(page)
        updateHeader(currentSurahNumber, page)

        binding.quranPager.postDelayed({
            val fromContinue = arguments?.getBoolean("fromContinue") == true

            if (fromContinue) {
                // Always restore exact scrollY — even if 0
                restoreScrollPosition(binding.quranPager.currentItem, scrollY)
            } else {
                // New session → scroll to ayah (simplified - just go to page)
                // Since we're using pre-formatted text, precise ayah scrolling is complex
                // For now, we just navigate to the correct page
            }
        }, 100)

        setupPageChangeListener()
        
        // Initialize database helper
        databaseHelper = DatabaseHelper()
    }

    private fun buildSurahMapping(pages: List<List<String>>) {
        pageToSurahMap.clear()
        
        // Standard 604-page Mushaf Uthmani mapping - page where each surah starts (0-indexed)
        val surahStartPages = mapOf(
            1 to 0,    // Al-Fatiha
            2 to 1,    // Al-Baqarah  
            3 to 49,   // Ali Imran
            4 to 76,   // An-Nisa
            5 to 105,  // Al-Ma'idah
            6 to 127,  // Al-An'am
            7 to 150,  // Al-A'raf
            8 to 176,  // Al-Anfal
            9 to 186,  // At-Tawbah
            10 to 207, // Yunus
            11 to 220, // Hud
            12 to 234, // Yusuf
            13 to 248, // Ar-Ra'd
            14 to 254, // Ibrahim
            15 to 261, // Al-Hijr
            16 to 266, // An-Nahl
            17 to 281, // Al-Isra
            18 to 292, // Al-Kahf
            19 to 304, // Maryam
            20 to 311, // Ta-Ha
            21 to 321, // Al-Anbiya
            22 to 331, // Al-Hajj
            23 to 341, // Al-Mu'minun
            24 to 349, // An-Nur
            25 to 359, // Al-Furqan
            26 to 366, // Ash-Shu'ara
            27 to 376, // An-Naml
            28 to 385, // Al-Qasas
            29 to 396, // Al-Ankabut
            30 to 404, // Ar-Rum
            31 to 410, // Luqman
            32 to 414, // As-Sajdah
            33 to 417, // Al-Ahzab
            34 to 427, // Saba
            35 to 433, // Fatir
            36 to 440, // Ya-Sin
            37 to 446, // As-Saffat
            38 to 452, // Sad
            39 to 457, // Az-Zumar
            40 to 467, // Ghafir
            41 to 477, // Fussilat
            42 to 482, // Ash-Shura
            43 to 489, // Az-Zukhruf
            44 to 496, // Ad-Dukhan
            45 to 498, // Al-Jathiyah
            46 to 501, // Al-Ahqaf
            47 to 507, // Muhammad
            48 to 511, // Al-Fath
            49 to 515, // Al-Hujurat
            50 to 518, // Qaf
            51 to 520, // Adh-Dhariyat
            52 to 523, // At-Tur
            53 to 526, // An-Najm
            54 to 528, // Al-Qamar
            55 to 531, // Ar-Rahman
            56 to 534, // Al-Waqi'ah
            57 to 537, // Al-Hadid
            58 to 542, // Al-Mujadilah
            59 to 545, // Al-Hashr
            60 to 549, // Al-Mumtahanah
            61 to 551, // As-Saff
            62 to 553, // Al-Jumu'ah
            63 to 554, // Al-Munafiqun
            64 to 556, // At-Taghabun
            65 to 558, // At-Talaq
            66 to 560, // At-Tahrim
            67 to 562, // Al-Mulk
            68 to 564, // Al-Qalam
            69 to 566, // Al-Haqqah
            70 to 568, // Al-Ma'arij
            71 to 570, // Nuh
            72 to 572, // Al-Jinn
            73 to 574, // Al-Muzzammil
            74 to 575, // Al-Muddaththir
            75 to 577, // Al-Qiyamah
            76 to 578, // Al-Insan
            77 to 580, // Al-Mursalat
            78 to 582, // An-Naba
            79 to 583, // An-Nazi'at
            80 to 585, // Abasa
            81 to 586, // At-Takwir
            82 to 587, // Al-Infitar
            83 to 587, // Al-Mutaffifin
            84 to 589, // Al-Inshiqaq
            85 to 590, // Al-Buruj
            86 to 591, // At-Tariq
            87 to 591, // Al-A'la
            88 to 592, // Al-Ghashiyah
            89 to 593, // Al-Fajr
            90 to 594, // Al-Balad
            91 to 595, // Ash-Shams
            92 to 595, // Al-Layl
            93 to 596, // Ad-Duha
            94 to 596, // Ash-Sharh
            95 to 597, // At-Tin
            96 to 597, // Al-Alaq
            97 to 598, // Al-Qadr
            98 to 598, // Al-Bayyinah
            99 to 599, // Az-Zalzalah
            100 to 599, // Al-Adiyat
            101 to 600, // Al-Qari'ah
            102 to 600, // At-Takathur
            103 to 601, // Al-Asr
            104 to 601, // Al-Humazah
            105 to 601, // Al-Fil
            106 to 602, // Quraysh
            107 to 602, // Al-Ma'un
            108 to 602, // Al-Kawthar
            109 to 603, // Al-Kafirun
            110 to 603, // An-Nasr
            111 to 603, // Al-Masad
            112 to 604, // Al-Ikhlas
            113 to 604, // Al-Falaq
            114 to 604  // An-Nas
        )
        
        // Build reverse mapping: for each page, find which surah it belongs to
        for (pageIndex in 0 until pages.size) {
            var surahForThisPage = 1 // default fallback
            
            // Find the highest surah number that starts on or before this page
            for ((surah, startPage) in surahStartPages) {
                if (startPage <= pageIndex) {
                    surahForThisPage = surah
                }
            }
            
            pageToSurahMap[pageIndex] = surahForThisPage
        }
    }

    private fun findFirstPageForSurah(surahNumber: Int): Int {
        return pageToSurahMap.entries.firstOrNull { it.value == surahNumber }?.key ?: 0
    }

    private fun setupViewPager(initialPage: Int) {
        pageAdapter = QuranPageAdapter(
            fragment = this,
            allPages = allPages
        )

        binding.quranPager.adapter = pageAdapter
        binding.quranPager.layoutDirection = View.LAYOUT_DIRECTION_RTL
        binding.quranPager.setCurrentItem(initialPage, false)

        binding.quranPager.post {
            binding.quranPager.setCurrentItem(initialPage, false)
            currentPagePosition = initialPage
            updateHeader(currentSurahNumber, initialPage)
        }
    }

    private fun setupPageChangeListener() {
        binding.quranPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                Log.d("PageChange", "Page selected: $position")
                
                currentPagePosition = position
                val newPage = position
                
                updateHeader(pageToSurahMap[newPage] ?: 1, newPage)
                
                // Save position immediately when page changes
                saveCurrentPosition(newPage)
                
                // Page read tracking
                startPageReadTracking()
            }
        })
    }

    private fun updateHeader(surahNumber: Int, pageNumber: Int) {
        val surah = SurahRepository.getSurahByNumber(surahNumber)
        val surahName = surah?.arabicName ?: "Unknown"
        
        // Convert numbers to fancy Arabic-Indic numerals
        val fancyPageNumber = convertToArabicNumerals(pageNumber + 1)
        val fancySurahNumber = convertToArabicNumerals(surahNumber)
        
        // Format with fancy numerals: "صفحة [page number] / سورة [surah number] - [surah name]"
        binding.pageInfoTextView.text = "صفحة $fancyPageNumber \\ سورة $fancySurahNumber - $surahName"
        
        // Hide the separate surah info text view since we're combining them
        binding.surahInfoTextView.text = ""
    }
    
    // Helper function to convert regular numbers to Arabic-Indic numerals
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
            
            // Simple check - if user spent time on page, mark as read
            val currentTime = System.currentTimeMillis()
            if (currentTime - startPageReadTime >= PAGE_READ_DELAY_MS) {
                markPageAsRead(currentPagePosition)
            }
        }
    }

    private fun markPageAsRead(pageNumber: Int) {
        if (pageNumber == lastPageMarkedAsRead) return
        
        val surahNumber = pageToSurahMap[pageNumber] ?: 1
        
        // Get the current scroll position
        val currentScrollY = getCurrentScrollPosition(pageNumber)
        
        // Save to database
        databaseHelper.saveReadingProgress(
            surahNumber = surahNumber,
            ayahNumber = 1, // Simplified since we don't track precise ayahs
            pageNumber = pageNumber,
            scrollY = currentScrollY
        )
        
        lastPageMarkedAsRead = pageNumber
        
        // Update preferences
        requireContext().getSharedPreferences("QuranPrefs", Context.MODE_PRIVATE).edit()
            .putInt("lastPageMarked", pageNumber)
            .putInt("lastSurah", surahNumber)
            .putInt("lastPage", pageNumber)
            .apply()
    }

    private fun saveCurrentPosition(pageNumber: Int) {
        val surahNumber = pageToSurahMap[pageNumber] ?: 1
        
        // Use a small delay to ensure the view is ready
        binding.quranPager.post {
            val currentScrollY = getCurrentScrollPosition(pageNumber)
            
            Log.d("SavePosition", "Attempting to save position: page=$pageNumber, surah=$surahNumber, scrollY=$currentScrollY")
            
            // Save immediately to database
            databaseHelper.saveReadingProgress(
                surahNumber = surahNumber,
                ayahNumber = 1, // Simplified since we don't track precise ayahs
                pageNumber = pageNumber,
                scrollY = currentScrollY
            )
        }
    }

    private fun getCurrentScrollPosition(pageNumber: Int): Int {
        try {
            // Get the RecyclerView from ViewPager2
            val recyclerView = binding.quranPager.getChildAt(0) as? RecyclerView
            if (recyclerView == null) {
                Log.w("ScrollPosition", "RecyclerView not found")
                return 0
            }
            
            // Find the view holder for the current page
            val viewHolder = recyclerView.findViewHolderForAdapterPosition(pageNumber) as? QuranPageAdapter.PageViewHolder
            if (viewHolder == null) {
                Log.w("ScrollPosition", "ViewHolder not found for page $pageNumber")
                return 0
            }
            
            val scrollY = viewHolder.binding.pageScrollView.scrollY
            Log.d("ScrollPosition", "Got scroll position for page $pageNumber: $scrollY")
            return scrollY
            
        } catch (e: Exception) {
            Log.e("ScrollPosition", "Error getting scroll position for page $pageNumber", e)
            return 0
        }
    }

    private fun restoreScrollPosition(pageNumber: Int, scrollY: Int) {
        binding.quranPager.post {
            val viewHolder = (binding.quranPager.getChildAt(0) as? RecyclerView)
                ?.findViewHolderForAdapterPosition(pageNumber) as? QuranPageAdapter.PageViewHolder
            
            viewHolder?.binding?.pageScrollView?.post {
                viewHolder.binding.pageScrollView.scrollTo(0, scrollY)
            }
        }
    }

    private fun loadTextFromRaw(resourceId: Int): String {
        return resources.openRawResource(resourceId).bufferedReader().use { it.readText() }
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as MainActivity).setBottomNavVisibility(false)
    }

    override fun onPause() {
        super.onPause()
        pageReadCheckJob?.cancel()
        
        // Save current position when pausing to ensure we don't lose progress
        saveCurrentPosition(currentPagePosition)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pageReadCheckJob?.cancel()
        if (::databaseHelper.isInitialized) {
            databaseHelper.cleanup()
        }
        _binding = null
    }

    // Simplified page adapter for pre-formatted text
    class QuranPageAdapter(
        private val fragment: QuranReaderFragment,
        private val allPages: List<List<String>>
    ) : RecyclerView.Adapter<QuranPageAdapter.PageViewHolder>() {

        private val scrollPositions = SparseIntArray()

        inner class PageViewHolder(val binding: ItemPageBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val binding = ItemPageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return PageViewHolder(binding)
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            if (position !in 0 until allPages.size) return

            // Completely clear and reset the page content
            holder.binding.pageContent.removeAllViews()
            holder.binding.pageContent.invalidate()

            val pageLines = allPages[position]
            
            pageLines.forEach { line ->
                if (line.isNotEmpty()) {
                    // Check if line contains basmala
                    if (line.contains("بِسْمِ ٱللَّهِ ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ") && !line.contains("١")) {
                        // Add basmala layout
                        val basmalaView = LayoutInflater.from(holder.binding.root.context)
                            .inflate(R.layout.item_basmala, holder.binding.pageContent, false)
                        holder.binding.pageContent.addView(basmalaView)
                        
                        // Add remaining text if any
                        val remaining = line.trim().removePrefix("بِسْمِ ٱللَّهِ ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ").trim()
                        if (remaining.isNotEmpty()) {
                            addLineToPage(holder.binding.pageContent, remaining)
                        }
                    } else {
                        // Add regular line with auto-sizing
                        addLineToPage(holder.binding.pageContent, line)
                    }
                }
                // Skip empty lines completely - don't add any spacing for them
            }

            // Restore scroll position
            holder.binding.pageScrollView.post {
                holder.binding.pageScrollView.scrollTo(0, scrollPositions.get(position, 0))
            }
        }

        // Helper function to create and add TextViews for each line with auto-sizing per line
        private fun addLineToPage(container: ViewGroup, text: String) {
            val textView = TextView(container.context).apply {
                setTextAppearance(R.style.AyahTextAppearance)
                // Use simple layout params without margins - control spacing differently
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
                
                fontFeatureSettings = "'liga' on, 'clig' on"
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                textDirection = View.TEXT_DIRECTION_RTL
                // Disable font padding completely
                includeFontPadding = false
                // Allow multiple lines but with tight spacing
                maxLines = Int.MAX_VALUE
                isSingleLine = false
                // Very tight line spacing - negative extra spacing
                setLineSpacing(-2f, 0.85f)
                
                // No padding at all to eliminate spacing
                setPadding(0, 0, 0, 0)
                
                this.text = text
                
                // Use fixed text size instead of auto-sizing to ensure consistency
                textSize = 17.5f // Fixed size in sp (25% bigger than 14sp)
                
                // Force layout recalculation after text is set
                post {
                    requestLayout()
                    invalidate()
                }
            }
            container.addView(textView)
            
            // Add a minimal spacer view after each text view for controlled spacing
            val spacer = View(container.context).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    1 // Just 1dp spacing between ayaat
                )
            }
            container.addView(spacer)
        }
        
        // Helper for empty lines
        private fun addEmptyLine(container: ViewGroup) {
            val emptyView = View(container.context).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    3 // Very small height for minimal spacing between sections
                )
            }
            container.addView(emptyView)
        }

        override fun onViewRecycled(holder: PageViewHolder) {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                // Save scroll position
                holder.binding.pageScrollView.scrollY.let { scrollY ->
                    scrollPositions.put(position, scrollY)
                }
            }
            // Completely clear the page content to ensure clean recycling
            holder.binding.pageContent.removeAllViews()
            super.onViewRecycled(holder)
        }

        override fun getItemCount() = allPages.size
    }

    // Simplified database helper
    private inner class DatabaseHelper {
        fun saveReadingProgress(surahNumber: Int, ayahNumber: Int, pageNumber: Int, scrollY: Int) {
            // Save to Room database using lifecycleScope
            lifecycleScope.launch {
                try {
                    lastReadRepo.savePosition(
                        surah = surahNumber,
                        ayah = ayahNumber,
                        page = pageNumber,
                        scrollY = scrollY
                    )
                    Log.d("DatabaseHelper", "Saved reading progress: page=$pageNumber, surah=$surahNumber, scrollY=$scrollY")
                } catch (e: Exception) {
                    Log.e("DatabaseHelper", "Failed to save reading progress", e)
                }
            }
        }

        fun cleanup() {
            // Any cleanup needed
        }
    }
}
