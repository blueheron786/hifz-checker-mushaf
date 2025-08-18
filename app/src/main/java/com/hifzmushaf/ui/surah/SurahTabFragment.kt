package com.hifzmushaf.ui.surah

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hifzmushaf.R
import com.hifzmushaf.data.SurahRepository
import com.hifzmushaf.ui.reader.QuranImageReaderFragment

class SurahTabFragment : Fragment() {

    private lateinit var surahRecyclerView: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_surah_tab, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        surahRecyclerView = view.findViewById(R.id.surahRecyclerView)
        setupRecyclerView()
        loadSurahs()
    }

    private fun setupRecyclerView() {
        surahRecyclerView.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun loadSurahs() {
        val surahs = SurahRepository.getSurahList()
        val adapter = SurahAdapter(surahs) { surah ->
            openSurah(surah)
        }
        surahRecyclerView.adapter = adapter
    }

    private fun openSurah(surah: Surah) {
        val args = Bundle().apply {
            putInt("surahNumber", surah.number)
            putInt("ayahNumber", 1)
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
