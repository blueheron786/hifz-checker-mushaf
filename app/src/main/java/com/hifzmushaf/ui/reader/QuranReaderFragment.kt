package com.hifzmushaf.ui.reader

import android.content.Context
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.util.SparseIntArray
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
        var currentSurah = 1
        
        pages.forEachIndexed { pageIndex, lines ->
            // Look for surah changes by detecting basmala (except for first page)
            val hasNewSurahBasmala = lines.any { line ->
                line.contains("بِسْمِ ٱللَّهِ ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ") && 
                !line.contains("١") // Not the first ayah of Fatiha
            }
            
            if (pageIndex > 0 && hasNewSurahBasmala) {
                currentSurah++
            }
            
            pageToSurahMap[pageIndex] = currentSurah
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
                currentPagePosition = position
                val newPage = position
                
                updateHeader(pageToSurahMap[newPage] ?: 1, newPage)
                
                // Page read tracking
                startPageReadTracking()
            }
        })
    }

    private fun updateHeader(surahNumber: Int, pageNumber: Int) {
        val surah = SurahRepository.getSurahByNumber(surahNumber)
        binding.surahInfoTextView.text = surah?.arabicName ?: "Unknown"
        binding.pageInfoTextView.text = "صفحة ${pageNumber + 1}"
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
        
        // Save to database
        databaseHelper.saveReadingProgress(
            surahNumber = surahNumber,
            ayahNumber = 1, // Simplified since we don't track precise ayahs
            pageNumber = pageNumber,
            scrollY = 0
        )
        
        lastPageMarkedAsRead = pageNumber
        
        // Update preferences
        requireContext().getSharedPreferences("QuranPrefs", Context.MODE_PRIVATE).edit()
            .putInt("lastPageMarked", pageNumber)
            .putInt("lastSurah", surahNumber)
            .putInt("lastPage", pageNumber)
            .apply()
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

            holder.binding.pageContent.removeAllViews()

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
                        // Add regular line
                        addLineToPage(holder.binding.pageContent, line)
                    }
                } else {
                    // Add minimal space for blank lines
                    addLineToPage(holder.binding.pageContent, " ")
                }
            }

            // Restore scroll position
            holder.binding.pageScrollView.post {
                holder.binding.pageScrollView.scrollTo(0, scrollPositions.get(position, 0))
            }
        }

        // Helper function to create and add TextViews for each line
        private fun addLineToPage(container: ViewGroup, text: String) {
            val textView = TextView(container.context).apply {
                setTextAppearance(R.style.AyahTextAppearance)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                fontFeatureSettings = "'liga' on, 'clig' on"
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                textDirection = View.TEXT_DIRECTION_RTL
                includeFontPadding = false
                this.text = text
            }
            container.addView(textView)
        }

        override fun onViewRecycled(holder: PageViewHolder) {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                // Save scroll position
                holder.binding.pageScrollView.scrollY.let { scrollY ->
                    scrollPositions.put(position, scrollY)
                }
            }
            super.onViewRecycled(holder)
        }

        override fun getItemCount() = allPages.size
    }

    // Simplified database helper
    private inner class DatabaseHelper {
        fun saveReadingProgress(surahNumber: Int, ayahNumber: Int, pageNumber: Int, scrollY: Int) {
            // Implementation for saving progress
            // This would save to your Room database
        }

        fun cleanup() {
            // Any cleanup needed
        }
    }
}
