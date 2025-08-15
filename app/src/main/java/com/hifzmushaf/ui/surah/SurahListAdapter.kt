package com.hifzmushaf.ui.surah

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hifzmushaf.R
import com.hifzmushaf.data.SurahRepository
import com.hifzmushaf.databinding.FragmentSurahListBinding
import com.hifzmushaf.databinding.ItemSurahBinding
import com.hifzmushaf.ui.reader.QuranReaderFragment
import com.hifzmushaf.ui.reader.QuranImageReaderFragment

class SurahListFragment : Fragment() {

    private var _binding: FragmentSurahListBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSurahListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (requireActivity() as AppCompatActivity).supportActionBar?.show()
        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        val surahList = SurahRepository.getSurahList()

        binding.surahRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())

            // Remove the dynamic padding calculation
            addItemDecoration(
                DividerItemDecoration(requireContext(), LinearLayoutManager.VERTICAL).apply {
                    setDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.spacer)!!)
                }
            )

            adapter = SurahAdapter(surahList) { surah ->
                try {
                    val bundle = Bundle().apply {
                        putInt("surahNumber", surah.number)
                    }
                    findNavController().navigate(
                        R.id.action_surahListFragment_to_quranImageReaderFragment,
                        bundle
                    )
                } catch (e: IllegalStateException) {
                    requireActivity().supportFragmentManager.beginTransaction()
                        .replace(R.id.nav_host_fragment, QuranImageReaderFragment().apply {
                            arguments = Bundle().apply {
                                putInt("surahNumber", surah.number)
                            }
                        })
                        .addToBackStack(null)
                        .commit()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class SurahAdapter(
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
}