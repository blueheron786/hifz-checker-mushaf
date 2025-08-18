package com.hifzmushaf.ui.surah

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hifzmushaf.R
import com.hifzmushaf.ui.reader.QuranImageReaderFragment

class PageTabFragment : Fragment() {

    private lateinit var pageRecyclerView: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_page_tab, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        pageRecyclerView = view.findViewById(R.id.pageRecyclerView)
        setupRecyclerView()
        loadPages()
    }

    private fun setupRecyclerView() {
        pageRecyclerView.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun loadPages() {
        // Create a list of pages (1-604 for standard Quran)
        val pages = (1..604).map { pageNumber ->
            Page(
                pageNumber = pageNumber,
                surahInfo = getSurahInfoForPage(pageNumber)
            )
        }
        
        val adapter = PageListAdapter(pages) { page ->
            openPage(page)
        }
        pageRecyclerView.adapter = adapter
    }

    private fun getSurahInfoForPage(pageNumber: Int): String {
        // Use the actual Madani Mushaf page mapping from SurahRepository
        val pageForSuraArray = intArrayOf(
            /*   1 -  10 */ 1, 2, 50, 77, 106, 128, 151, 177, 187, 208,
            /*  11 -  20 */ 221, 235, 249, 255, 262, 267, 282, 293, 305, 312,
            /*  21 -  30 */ 322, 332, 342, 350, 359, 367, 377, 385, 396, 404,
            /*  31 -  40 */ 411, 415, 418, 428, 434, 440, 446, 453, 458, 467,
            /*  41 -  50 */ 477, 483, 489, 496, 499, 502, 507, 511, 515, 518,
            /*  51 -  60 */ 520, 523, 526, 528, 531, 534, 537, 542, 545, 549,
            /*  61 -  70 */ 551, 553, 554, 556, 558, 560, 562, 564, 566, 568,
            /*  71 -  80 */ 570, 572, 574, 575, 577, 578, 580, 582, 583, 585,
            /*  81 -  90 */ 586, 587, 589, 590, 591, 593, 594, 595, 596, 597,
            /*  91 - 100 */ 598, 599, 600, 601, 601, 602, 602, 603, 603, 604,
            /* 101 - 110 */ 604, 604, 604, 604, 604, 604, 604, 604, 604, 604,
            /* 111 - 114 */ 604, 604, 604, 604
        )
        
        val surahNames = arrayOf(
            "Al-Fatiha", "Al-Baqarah", "Al-Imran", "An-Nisa", "Al-Ma'idah", "Al-An'am", "Al-A'raf",
            "Al-Anfal", "At-Tawbah", "Yunus", "Hud", "Yusuf", "Ar-Ra'd", "Ibrahim", "Al-Hijr", 
            "An-Nahl", "Al-Isra", "Al-Kahf", "Maryam", "Ta-Ha", "Al-Anbiya", "Al-Hajj", "Al-Mu'minun",
            "An-Nur", "Al-Furqan", "Ash-Shu'ara", "An-Naml", "Al-Qasas", "Al-Ankabut", "Ar-Rum",
            "Luqman", "As-Sajdah", "Al-Ahzab", "Saba", "Fatir", "Ya-Sin", "As-Saffat", "Sad", 
            "Az-Zumar", "Ghafir", "Fussilat", "Ash-Shura", "Az-Zukhruf", "Ad-Dukhan", "Al-Jathiyah",
            "Al-Ahqaf", "Muhammad", "Al-Fath", "Al-Hujurat", "Qaf", "Adh-Dhariyat", "At-Tur", 
            "An-Najm", "Al-Qamar", "Ar-Rahman", "Al-Waqi'ah", "Al-Hadid", "Al-Mujadila", "Al-Hashr",
            "Al-Mumtahanah", "As-Saff", "Al-Jumu'ah", "Al-Munafiqun", "At-Taghabun", "At-Talaq",
            "At-Tahrim", "Al-Mulk", "Al-Qalam", "Al-Haqqah", "Al-Ma'arij", "Nuh", "Al-Jinn",
            "Al-Muzzammil", "Al-Muddaththir", "Al-Qiyamah", "Al-Insan", "Al-Mursalat", "An-Naba",
            "An-Nazi'at", "Abasa", "At-Takwir", "Al-Infitar", "Al-Mutaffifin", "Al-Inshiqaq",
            "Al-Buruj", "At-Tariq", "Al-A'la", "Al-Ghashiyah", "Al-Fajr", "Al-Balad", "Ash-Shams",
            "Al-Layl", "Ad-Duha", "Ash-Sharh", "At-Tin", "Al-Alaq", "Al-Qadr", "Al-Bayyinah",
            "Az-Zalzalah", "Al-Adiyat", "Al-Qari'ah", "At-Takathur", "Al-Asr", "Al-Humazah",
            "Al-Fil", "Quraysh", "Al-Ma'un", "Al-Kawthar", "Al-Kafirun", "An-Nasr", "Al-Masad",
            "Al-Ikhlas", "Al-Falaq", "An-Nas"
        )
        
        // Find which surah this page belongs to
        for (i in pageForSuraArray.indices.reversed()) {
            if (pageNumber >= pageForSuraArray[i]) {
                return surahNames[i]
            }
        }
        
        return "Al-Fatiha" // fallback
    }

    private fun openPage(page: Page) {
        val args = Bundle().apply {
            putInt("pageNumber", page.pageNumber)
        }
        
        val fragment = QuranImageReaderFragment().apply {
            arguments = args
        }
        
        parentFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .addToBackStack("reader")
            .commit()
    }
}
