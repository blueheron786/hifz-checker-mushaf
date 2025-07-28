package com.quranhabit.ui.reader

import ReadingTimeTracker
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.CharacterStyle
import android.text.style.ClickableSpan
import android.text.style.StyleSpan
import android.util.Log
import android.util.SparseIntArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.quranhabit.MainActivity
import com.quranhabit.R
import com.quranhabit.data.QuranDatabase
import com.quranhabit.databinding.FragmentQuranReaderBinding
import com.quranhabit.databinding.ItemAyahBinding
import com.quranhabit.databinding.ItemPageBinding
import com.quranhabit.ui.surah.Ayah
import com.quranhabit.ui.surah.Surah
import com.quranhabit.data.SurahRepository
import com.quranhabit.data.entity.PagesReadOnDay
import com.quranhabit.data.repository.LastReadRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.core.content.edit
import com.quranhabit.ui.clearBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import java.time.LocalDate
import kotlin.math.max

class QuranReaderFragment : Fragment() {

    private var _binding: FragmentQuranReaderBinding? = null
    private val binding get() = _binding!!

    // What page are we on?
    private lateinit var pageAdapter: QuranPageAdapter
    private lateinit var allPages: List<List<PageAyahRange>>
    private lateinit var quranLines: List<String>
    private var currentSurahNumber = 1

    // Database
    private val database by lazy { QuranDatabase.getDatabase(requireContext()) }
    private val statisticsDao by lazy { database.statisticsDao() }
    // The cool new kid repository
    private val lastReadRepo by lazy {
        LastReadRepository(QuranDatabase.getDatabase(requireContext()).lastReadPositionDao())
    }
    private lateinit var databaseHelper: DatabaseHelper

    // Checking if we read a page
    private var pageTimer: CountDownTimer? = null
    private var currentPagePosition = -1
    private var pageReadStates = mutableMapOf<Int, Boolean>() // Track which pages have been marked as read
    private var pageMarked = false
    private var isAtBottom = false
    private var bottomTimer: CountDownTimer? = null
    // Don't mark as read again if we read to  the bottom of a page and reopen and continue
    private var lastPageMarkedAsRead = -1

    // Total time read
    private val readingTimeTracker = ReadingTimeTracker()

    companion object {
        private const val PAGE_READ_DELAY_MS = 3000L
        private const val PAGE_READ_CHECK_INTERVAL = 1000L
        private const val BASMALA_HEADER_HEIGHT = 111 // scroll up to show it properly
    }

    private val cachedPages by lazy {
        val json = loadTextFromRaw(R.raw.pages_absolute)
        Gson().fromJson<List<List<List<PageAyahRange>>>>(
            json,
            object : TypeToken<List<List<List<PageAyahRange>>>>() {}.type
        )
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
        readingTimeTracker.start()

        // Load the last marked page so we don't double-mark as read
        val prefs = requireContext().getSharedPreferences("QuranPrefs", Context.MODE_PRIVATE)
        lastPageMarkedAsRead = prefs.getInt("lastPageMarked", -1)

        // Initialize data
        allPages = cachedPages.flatten()
        quranLines = loadTextFromRaw(R.raw.quran_text).lines()

        // Get the selected surah number from arguments
        currentSurahNumber = arguments?.getInt("surahNumber") ?: 1
        // Argument missing -> defaults to zero, but we want 1 instead
        val ayahNumber = arguments?.getInt("ayahNumber")?.takeIf { it != 0 } ?: 1
        val page = arguments?.getInt("pageNumber")?.takeIf { it != 0 } ?: findFirstPageForSurah(currentSurahNumber)
        val scrollY = arguments?.getInt("scrollY") ?: 0

        setupViewPager(page)
        updateHeader(currentSurahNumber, page)

        binding.quranPager.postDelayed({
            val fromContinue = arguments?.getBoolean("fromContinue") == true

            if (fromContinue) {
                // Always restore exact scrollY — even if 0
                scrollToLastRead(page, scrollY)
            } else {
                // New session → scroll to ayah
                scrollToAyah(currentSurahNumber, ayahNumber)
            }
        }, 300)
    }

    private fun logReadingTime(seconds: Int) {
        if (seconds <= 0) return

        val currentDate = LocalDate.now().toString()
        Log.d("ReadingTime", "Logging $seconds seconds for $currentDate")

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val existingRecord = statisticsDao.getByDate(currentDate)
                if (existingRecord != null) {
                    statisticsDao.upsert(existingRecord.copy(
                        secondsSpendReading = existingRecord.secondsSpendReading + seconds
                    ))
                } else {
                    statisticsDao.upsert(PagesReadOnDay(
                        date = currentDate,
                        pagesRead = 0,
                        secondsSpendReading = seconds
                    ))
                }
            }
        }
    }

    private fun scrollToLastRead(page: Int, scrollY: Int) {
        try {
            if (binding.quranPager.currentItem != page) {
                binding.quranPager.setCurrentItem(page, false)
            }

            binding.quranPager.post {
                try {
                    val recyclerView = binding.quranPager.getChildAt(0) as? RecyclerView
                    val viewHolder = recyclerView?.findViewHolderForAdapterPosition(page) as? QuranPageAdapter.PageViewHolder
                    val scrollView = viewHolder?.binding?.pageScrollView

                    scrollView?.post {
                        try {
                            Log.d("SCROLL_DEBUG", "Scrolling to saved pos $scrollY")
                            // Prevent marking as read during restoration
                            viewHolder.scrollTracker?.isProgrammaticScroll = true
                            scrollView.scrollTo(0, scrollY)
                            // Reset the flag after a delay
                            scrollView.postDelayed({
                                viewHolder.scrollTracker?.isProgrammaticScroll = false
                            }, 500)
                        } catch (e: Exception) {
                            Log.e("ScrollError", "Failed to scroll", e)
                            scrollView.scrollTo(0, 0)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("QuranReader", "Page setup failed", e)
                }
            }
        } catch (e: Exception) {
            Log.e("QuranReader", "Scroll failed", e)
        }
    }

    private fun scrollToAyah(surah: Int, ayah: Int) {
        try {
            var pageToShow = arguments?.getInt("pageNumber") ?: 0
            if (pageToShow == 0) {
                pageToShow = findPageForAyah(surah, ayah).coerceIn(0, allPages.size - 1)
            }
            val targetPage = pageToShow

            // Always change page if surah changed or page is different
            if (binding.quranPager.currentItem != targetPage || surah != currentSurahNumber) {
                binding.quranPager.setCurrentItem(targetPage, false)
                currentSurahNumber = surah
            }

            // Wait for the page to be rendered and the ViewHolder to be available
            binding.quranPager.post {
                try {
                    val recyclerView = binding.quranPager.getChildAt(0) as? RecyclerView
                    val viewHolder = recyclerView?.findViewHolderForAdapterPosition(targetPage) as? QuranPageAdapter.PageViewHolder
                    val scrollView = viewHolder?.binding?.pageScrollView

                    lifecycleScope.launch {
                        val savedPosition = if (arguments?.containsKey("ayahNumber") == true) {
                            withContext(Dispatchers.IO) {
                                lastReadRepo.getScrollPosition(targetPage) ?: 0
                            }
                        } else {
                            null
                        }

                        scrollView?.post {
                            try {
                                if (savedPosition != null && savedPosition > 0) {
                                    Log.d("SCROLL_DEBUG", "Scrolling to saved pos $savedPosition")
                                    scrollView.scrollTo(0, savedPosition)
                                } else {
                                    val tag = "ayah_${surah}_$ayah"
                                    Log.d("SCROLL_DEBUG", "looking for tag: $tag")
                                    val ayahView = scrollView.findViewWithTag<View?>(tag)
                                    Log.d("SCROLL_DEBUG", "Scrolling to AYAH $ayah. got it? $ayahView")

                                    ayahView?.let {
                                        val backup = if (ayah == 1) {
                                            (BASMALA_HEADER_HEIGHT * resources.displayMetrics.density).toInt()
                                        } else {
                                            0
                                        }
                                        val scrollPosition = max(0, it.top - backup)

                                        scrollView.smoothScrollTo(0, scrollPosition)
                                    } ?: scrollView.smoothScrollTo(0, 0)
                                }
                            } catch (e: Exception) {
                                scrollView.scrollTo(0, 0)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("QuranReader", "Page setup failed: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("QuranReader", "Scroll failed: ${e.message}")
            binding.quranPager.setCurrentItem(findFirstPageForSurah(surah), false)
        }
    }

    private fun findPageForAyah(surah: Int, ayah: Int): Int {
        if (!::allPages.isInitialized) return 0

        return allPages.indexOfFirst { page ->
            page.any { range ->
                if (range.surah == surah) {
                    val firstAyah = range.start - getFirstLineNumberForSurah(surah) + 1
                    val lastAyah = range.end - getFirstLineNumberForSurah(surah) + 1
                    ayah in firstAyah..lastAyah
                } else false
            }
        }.takeIf { it != -1 } ?: 0
    }

    private fun setupViewPager(initialPage: Int) {
        pageAdapter = QuranPageAdapter(
            fragment = this,  // Pass fragment reference
            allPages = allPages,
            quranLines = quranLines
        )

        binding.quranPager.adapter = pageAdapter
        binding.quranPager.layoutDirection = View.LAYOUT_DIRECTION_RTL
        binding.quranPager.setCurrentItem(initialPage, false)

        binding.quranPager.post {
            binding.quranPager.setCurrentItem(initialPage, false)
            currentPagePosition = initialPage
            updateHeader(getSurahForPage(initialPage).number, initialPage)
        }

        binding.quranPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(newPage: Int) {
                saveCurrentPosition()

                // 1. Pause and log time from previous page
                val secondsRead = readingTimeTracker.pause()
                if (secondsRead > 0) {
                    logReadingTime(secondsRead)
                }

                // 2. Reset and start fresh for new page
                readingTimeTracker.reset()
                readingTimeTracker.start()

                // Cancel any existing timers on page change
                pageTimer?.cancel()
                pageTimer = null
                bottomTimer?.cancel()
                bottomTimer = null

                // Update page position and header
                currentPagePosition = newPage
                updateHeader(getSurahForPage(newPage).number, newPage)

                // Reset states for new page
                pageMarked = false
                isAtBottom = false

                // Check if we should mark this page as read
                if (!pageReadStates.getOrDefault(newPage, false)) {
                    binding.quranPager.postDelayed({
                        checkPageReadConditions()
                    }, 1000) // Short delay to allow page to settle
                }
            }

            // Required for Mark As Read when you freshly open a new surah
            override fun onPageScrollStateChanged(state: Int) {
                val stateName = when(state) {
                    ViewPager2.SCROLL_STATE_IDLE -> "IDLE"
                    ViewPager2.SCROLL_STATE_DRAGGING -> "DRAGGING"
                    ViewPager2.SCROLL_STATE_SETTLING -> "SETTLING"
                    else -> "UNKNOWN"
                }
                super.onPageScrollStateChanged(state)
            }

            private fun logReadingTime(secondsSpendReading: Int) {
                val currentDate = LocalDate.now().toString();

                lifecycleScope.launch {
                    val existingRecord = statisticsDao.getByDate(currentDate)
                    if (existingRecord != null) {
                        statisticsDao.upsert(existingRecord.copy(secondsSpendReading = existingRecord.secondsSpendReading + secondsSpendReading))
                    } else {
                        statisticsDao.upsert(PagesReadOnDay(date = currentDate, pagesRead = 0, secondsSpendReading = secondsSpendReading))
                    }
                }
            }
        })
    }

    private fun checkPageReadConditions() {
        val recyclerView = binding.quranPager.getChildAt(0) as? RecyclerView
        val viewHolder = recyclerView?.findViewHolderForAdapterPosition(currentPagePosition) as? QuranPageAdapter.PageViewHolder
        val isAtBottom = viewHolder?.scrollTracker?.isBottomReached() ?: false

        if (!pageMarked &&
            !pageReadStates.getOrDefault(currentPagePosition, false) &&
            isAtBottom &&
            currentPagePosition != lastPageMarkedAsRead) {

            markPageAsRead(currentPagePosition)
            pageMarked = true
            pageReadStates[currentPagePosition] = true
            lastPageMarkedAsRead = currentPagePosition
        }
    }

    private fun startPageReadTimer(pageNumber: Int) {
        pageTimer?.cancel()
        pageTimer = object : CountDownTimer(PAGE_READ_DELAY_MS, PAGE_READ_CHECK_INTERVAL) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                pageTimer = null
                Log.d("PageTimer", "Page timer completed")
                checkPageReadConditions()
            }
        }.start()
        Log.d("PageFlow", "Page $pageNumber - Timer started")
    }

    private fun updateHeader(currentSurahNumber: Int, pageNumber: Int) {
        val currentSurah = SurahRepository.getSurahByNumber(currentSurahNumber)
        binding.surahInfoTextView.text = "${currentSurah?.number}. ${currentSurah?.englishName}"
        binding.pageInfoTextView.text = "page ${pageNumber + 1}"
    }

    private fun getSurahForPage(position: Int): Surah {
        val surahNumber = allPages[position].first().surah
        return SurahRepository.getSurahByNumber(surahNumber) ?: Surah(0, "Unknown", "???")
    }

    private fun findFirstPageForSurah(surahNumber: Int): Int {
        return allPages.indexOfFirst { page -> page.any { it.surah == surahNumber } }
            .takeIf { it != -1 } ?: 0
    }

    private fun getFirstLineNumberForSurah(surahNumber: Int): Int {
        return try {
            cachedPages.flatten().flatten().first { it.surah == surahNumber }.start
        } catch (e: Exception) {
            Log.e("QuranReader", "Error finding first line for surah $surahNumber", e)
            0
        }
    }

    private fun saveLastReadAyah(surahNumber: Int, ayahNumber: Int) {
        requireContext().getSharedPreferences("QuranPrefs", Context.MODE_PRIVATE).edit()
            .putInt("lastSurah", surahNumber)
            .putInt("lastAyah", ayahNumber)
            .putInt("lastPage", binding.quranPager.currentItem)
            .apply()
    }

    private fun loadTextFromRaw(resourceId: Int): String {
        return resources.openRawResource(resourceId).bufferedReader().use { it.readText() }
    }


    private fun handleBottomPositionChange(atBottom: Boolean) {
        if (atBottom && binding.quranPager.currentItem == currentPagePosition) {
            checkPageReadConditions()
        }
    }

    override fun onResume() {
        super.onResume()
        readingTimeTracker.start()
        (requireActivity() as MainActivity).setBottomNavVisibility(false)
    }

    override fun onStop() {
        (requireActivity() as MainActivity).setBottomNavVisibility(true)
        super.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // 1. Cancel all timers safely
        pageTimer?.cancel()
        bottomTimer?.cancel()
        readingTimeTracker.pause()

        // 2. Clean up database helper
        databaseHelper.cleanup()

        // 3. Clear binding safely with extension
        clearBinding(_binding) {
            // Optional: Add any view-specific cleanup here
            quranPager.adapter = null
        }

        _binding = null
    }

    private fun showPageReadFeedback() {
        val snack = Snackbar.make(binding.root, "✔ Page read", Snackbar.LENGTH_SHORT)
        snack.setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.accent))
        snack.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
        snack.show()
    }

    class QuranPageAdapter(
        private val fragment: QuranReaderFragment,
        private val allPages: List<List<PageAyahRange>>,
        private val quranLines: List<String>
    ) : RecyclerView.Adapter<QuranPageAdapter.PageViewHolder>() {

        private val scrollPositions = SparseIntArray()
        private val scrollTrackers = mutableMapOf<Int, ScrollTracker>()

        inner class PageViewHolder(val binding: ItemPageBinding) : RecyclerView.ViewHolder(binding.root) {
            val scrollTracker = ScrollTracker().apply {
                onScrollStateChanged = { isScrolling ->
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                    }
                }

                onScrollPositionChanged = { atBottom ->
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {  // Add this check
                        Log.d("PageAdapter", "Bottom state for $pos: $atBottom")
                        if (pos == fragment.currentPagePosition) {
                            fragment.handleBottomPositionChange(atBottom)
                        }
                    }
                }

                onScrollPositionSaved = { scrollY ->
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        fragment.lifecycleScope.launch {
                            val page = pos
                            // page 114, ayah 1, whatchu gonna do? three matches...
                            val surah = fragment.getSurahForPage(page).number
                            val ayahRanges = fragment.allPages[page]
                            val lastAyahRange = ayahRanges.last()
                            val lastAyahNumber = lastAyahRange.end - fragment.getFirstLineNumberForSurah(lastAyahRange.surah) + 1

                            fragment.lastReadRepo.savePosition(
                                surah = surah,
                                ayah = lastAyahNumber,
                                page = page,
                                scrollY = scrollY
                            )
                        }
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val binding = ItemPageBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return PageViewHolder(binding)
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            if (position !in 0 until allPages.size) return

            holder.binding.pageContent.removeAllViews()

            var currentPageTextBuilder = SpannableStringBuilder()

            allPages[position].forEach { range ->
                val firstLine = fragment.getFirstLineNumberForSurah(range.surah)
                (range.start..range.end).forEach { lineNumber ->
                    val ayah = Ayah(
                        surahNumber = range.surah,
                        ayahNumber = lineNumber - firstLine + 1,
                        text = quranLines[lineNumber - 1]
                    )

                    if (isBasmala(ayah)) {
                        // If there's accumulated text, add it as a TextView before the Basmala
                        if (currentPageTextBuilder.isNotEmpty()) {
                            addTextToPage(holder.binding.pageContent, currentPageTextBuilder)
                            currentPageTextBuilder = SpannableStringBuilder() // Start a new builder
                        }

                        // Inflate and add the basmala layout
                        val basmalaView = LayoutInflater.from(holder.binding.root.context)
                            .inflate(R.layout.item_basmala, holder.binding.pageContent, false)
                        holder.binding.pageContent.addView(basmalaView)

                        val remaining = ayah.text.trim().removePrefix("بِسْمِ ٱللَّهِ ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ").trim()
                        if (remaining.isNotEmpty()) {
                            currentPageTextBuilder.append("${fixMissingSmallStops(remaining)} \u06DD${ayah.ayahNumber} ")
                        }

                    } else {
                        // Append regular ayah text to the current builder
                        currentPageTextBuilder.append("${fixMissingSmallStops(ayah.text)} \u06DD${ayah.ayahNumber} ")
                    }
                }
            }

            // Add any remaining accumulated text after the loop finishes
            if (currentPageTextBuilder.isNotEmpty()) {
                addTextToPage(holder.binding.pageContent, currentPageTextBuilder)
            }

            // Restore scroll position
            holder.binding.pageScrollView.post {
                holder.binding.pageScrollView.scrollTo(0, scrollPositions.get(position, 0))
            }

            // Setup tracking
            holder.scrollTracker.attach(holder.binding.pageScrollView)
        }

        // Add this helper function to create and add TextViews from SpannableStringBuilder
        private fun addTextToPage(container: ViewGroup, textBuilder: SpannableStringBuilder) {
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
                text = textBuilder
            }
            container.addView(textView)
        }

        override fun onViewRecycled(holder: PageViewHolder) {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                scrollTrackers[position]?.let {
                    scrollPositions.put(position, it.getScrollY())
                    it.detach()
                }
            }
            super.onViewRecycled(holder)
        }

        override fun getItemCount() = allPages.size

        private fun isBasmala(ayah: Ayah): Boolean {
            return ayah.ayahNumber == 1 &&
                    ayah.surahNumber != 1 &&
                    ayah.surahNumber != 9
        }

        private fun addAyahText(
            container: ViewGroup,
            ayahText: String,
            ayahNumber: Int,
            surahNumber: Int
        ) {
            val tag = "ayah_${surahNumber}_${ayahNumber}"
            val fixedText = fixMissingSmallStops(ayahText)

            val ayahView = TextView(container.context).apply {
                this.tag = tag
                this.text = "$fixedText \u06DD$ayahNumber"
                setTextAppearance(R.style.AyahTextAppearance)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                fontFeatureSettings = "'liga' on, 'clig' on"
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                textDirection = View.TEXT_DIRECTION_RTL
                includeFontPadding = false
            }

            container.addView(ayahView)
        }

        private fun fixMissingSmallStops(quranText: String): String {
            var fixedText = quranText

            val salaa = "\u06D6" // U+06D6
            val qala = "\u06D7"  // U+06D7
            val smallJeem = "\u06DA" // U+06DA
            val smallLaa = "\u06D9" // U+06D9

            val smallStops = listOf(salaa, qala, smallJeem, smallLaa)

            for (smallStop in smallStops) {
                // Prepend word joiner so tiny stop doesn't appear at the start of a new line.
                // \u2060 is "Word Joiner" which forces stops to stay attached to the previous word,
                // but without adding visible space.
                fixedText = fixedText.replace(smallStop, "\u2060$smallStop")
            }

            return fixedText
        }
    }

    private inner class DatabaseHelper {
        private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        // Unified method for saving reading progress
        fun saveReadingProgress(
            surahNumber: Int,
            ayahNumber: Int,
            pageNumber: Int,
            scrollY: Int,
            secondsRead: Int,
            onComplete: (() -> Unit)? = null,
            onError: ((Exception) -> Unit)? = null
        ) {
            ioScope.launch {
                try {
                    // 1. Save last read position
                    lastReadRepo.savePosition(surahNumber, ayahNumber, pageNumber, scrollY)

                    // 2. Update statistics
                    val currentDate = LocalDate.now().toString()
                    val existingRecord = statisticsDao.getByDate(currentDate)

                    if (existingRecord != null) {
                        statisticsDao.upsert(
                            existingRecord.copy(
                                pagesRead = existingRecord.pagesRead + 1,
                                secondsSpendReading = existingRecord.secondsSpendReading + secondsRead
                            )
                        )
                    } else {
                        statisticsDao.upsert(
                            PagesReadOnDay(
                                date = currentDate,
                                pagesRead = 1,
                                secondsSpendReading = secondsRead
                            )
                        )
                    }

                    withContext(Dispatchers.Main) {
                        onComplete?.invoke()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        onError?.invoke(e)
                        Log.e("DatabaseHelper", "Save failed", e)
                    }
                }
            }
        }

        fun cleanup() {
            ioScope.coroutineContext.cancelChildren()
        }
    }

    private fun markPageAsRead(pageNumber: Int) {
        val secondsRead = readingTimeTracker.pause()
        if (secondsRead <= 0) {
            readingTimeTracker.reset()
            readingTimeTracker.start()
            return
        }

        val surahNumber = getSurahForPage(pageNumber).number
        val ayahRanges = allPages[pageNumber]
        val lastAyahRange = ayahRanges.last()
        val lastAyahNumber = lastAyahRange.end - getFirstLineNumberForSurah(lastAyahRange.surah) + 1

        val recyclerView = binding.quranPager.getChildAt(0) as? RecyclerView
        val viewHolder = recyclerView?.findViewHolderForAdapterPosition(pageNumber) as? QuranPageAdapter.PageViewHolder
        val scrollY = viewHolder?.scrollTracker?.getScrollY() ?: 0

        databaseHelper.saveReadingProgress(
            surahNumber = surahNumber,
            ayahNumber = lastAyahNumber,
            pageNumber = pageNumber,
            scrollY = scrollY,
            secondsRead = secondsRead,
            onComplete = {
                showPageReadFeedback()
                readingTimeTracker.reset()
                readingTimeTracker.start()
            },
            onError = { e ->
                // Optional: Show error to user
                readingTimeTracker.reset()
                readingTimeTracker.start()
            }
        )
    }

    override fun onPause() {
        super.onPause()
        readingTimeTracker.pause()
        saveCurrentPosition()
        // Save the last marked page
        requireContext().getSharedPreferences("QuranPrefs", Context.MODE_PRIVATE).edit() {
            putInt("lastPageMarked", lastPageMarkedAsRead)
        }
    }

    private fun saveCurrentPosition() {
        val currentPage = binding.quranPager.currentItem
        if (currentPage in allPages.indices) {
            val surahNumber = getSurahForPage(currentPage).number
            val ayahRanges = allPages[currentPage]

            // Find the last ayah on this page
            val lastAyahRange = ayahRanges.last()
            val lastAyahNumber = lastAyahRange.end - getFirstLineNumberForSurah(lastAyahRange.surah) + 1
            
            // Get current scroll position
            val recyclerView = binding.quranPager.getChildAt(0) as? RecyclerView
            val viewHolder = recyclerView?.findViewHolderForAdapterPosition(currentPage) as? QuranPageAdapter.PageViewHolder
            val scrollY = viewHolder?.scrollTracker?.getScrollY() ?: 0

            // Save to both SharedPreferences and database
            saveLastReadAyah(surahNumber, lastAyahNumber)

            lifecycleScope.launch {
                lastReadRepo.savePosition(
                    surah = surahNumber,
                    ayah = lastAyahNumber,
                    page = currentPage,
                    scrollY = scrollY
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        databaseHelper = DatabaseHelper()

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 1. Pause the timer (captures current reading session)
                readingTimeTracker.pause()

                // 2. Log the accumulated time
                val secondsRead = readingTimeTracker.getTotalSeconds()
                if (secondsRead > 0) {
                    logReadingTime(secondsRead)
                }

                // 3. Save position and navigate back
                saveCurrentPosition()
                isEnabled = false
                requireActivity().onBackPressed()

                // 4. Reset after logging (don't reset before logging!)
                readingTimeTracker.reset()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(this, callback)
    }
}

data class PageAyahRange(val surah: Int, val start: Int, val end: Int)