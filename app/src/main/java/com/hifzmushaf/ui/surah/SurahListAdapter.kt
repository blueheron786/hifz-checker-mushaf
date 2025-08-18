package com.hifzmushaf.ui.surah

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.hifzmushaf.R
import com.hifzmushaf.data.SurahRepository
import com.hifzmushaf.databinding.ItemSurahBinding
import com.hifzmushaf.ui.reader.QuranImageReaderFragment

class SurahListFragment : Fragment() {

    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_surah_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (requireActivity() as AppCompatActivity).supportActionBar?.show()
        
        tabLayout = view.findViewById(R.id.tabLayout)
        viewPager = view.findViewById(R.id.viewPager)
        
        setupViewPager()
    }

    private fun setupViewPager() {
        val adapter = TableOfContentsPagerAdapter(requireActivity())
        viewPager.adapter = adapter
        
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            when (position) {
                0 -> tab.text = "Surahs"
                1 -> tab.text = "Pages"
            }
        }.attach()
    }

    private class TableOfContentsPagerAdapter(fragmentActivity: FragmentActivity) : 
        FragmentStateAdapter(fragmentActivity) {
        
        override fun getItemCount(): Int = 2
        
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> SurahTabFragment()
                1 -> PageTabFragment()
                else -> SurahTabFragment()
            }
        }
    }
}
class SurahAdapter(
    private val surahList: List<Surah>,
    private val onItemClick: (Surah) -> Unit
) : RecyclerView.Adapter<SurahAdapter.SurahViewHolder>() {

    inner class SurahViewHolder(private val binding: ItemSurahBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(surah: Surah) {
            binding.surahNumberTextView.text = surah.number.toString() + "."
            binding.englishNameTextView.text = surah.englishName
            binding.arabicNameTextView.text = surah.arabicName

            binding.root.setOnClickListener {
                onItemClick(surah)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SurahViewHolder {
        val binding = ItemSurahBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SurahViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SurahViewHolder, position: Int) {
        holder.bind(surahList[position])
    }

    override fun getItemCount() = surahList.size
}