package com.quranhabit.ui.statistics.ui

import StatisticsViewModel
import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.quranhabit.MainActivity
import com.quranhabit.R
import com.quranhabit.data.QuranDatabase
import com.quranhabit.data.dao.StatisticsDao
import com.quranhabit.databinding.FragmentStatisticsBinding
import com.quranhabit.ui.statistics.DailyData
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class StatisticsFragment : Fragment() {

    private var _binding: FragmentStatisticsBinding? = null
    private val binding get() = _binding ?: throw IllegalStateException("Binding accessed after onDestroyView")

    private val viewModel: StatisticsViewModel by viewModels {
        ReadingProgressViewModelFactory(
            QuranDatabase.getDatabase(requireContext()).statisticsDao()
        )
    }

    private val dateFormatter = DateTimeFormatter.ISO_DATE

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStatisticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? MainActivity)?.setBottomNavVisibility(false)

        observeTextViewData()
        observeBarChartData()

        binding.resetButton.setOnClickListener {
            showResetConfirmationDialog()
        }
    }

    private fun observeTextViewData() {
        viewModel.pagesReadToday.observe(viewLifecycleOwner) { pages ->
            binding.pagesToday.text = getString(R.string.pages_today, pages)
        }

        viewModel.pagesReadMonth.observe(viewLifecycleOwner) { pages ->
            binding.pagesMonth.text = getString(R.string.pages_month, pages)
        }

        viewModel.totalPagesRead.observe(viewLifecycleOwner) { pages ->
            binding.totalPages.text = getString(R.string.total_pages, pages)
        }

        viewModel.timeReadToday.observe(viewLifecycleOwner) { seconds ->
            val minutes = seconds / 60
            binding.timeToday.text = getString(R.string.time_today, minutes)
        }

        viewModel.totalTimeRead.observe(viewLifecycleOwner) { seconds ->
            val minutes = seconds / 60
            val hours = minutes / 60
            val displayMinutes = minutes % 60
            binding.totalTime.text = getString(R.string.total_time, hours, displayMinutes)
        }

        viewModel.streakDays.observe(viewLifecycleOwner) { days ->
            binding.streakDays.text = getString(R.string.streak_days, days)
        }
    }

    private fun observeBarChartData() {
        observeMonthlyPagesData()
        observeWeeklyTimeData()
    }

    private fun observeMonthlyPagesData() {
        lifecycleScope.launch {
            viewModel.monthlyData.collectLatest { dailyDataList ->
                updateBarChart(
                    binding.monthlyPagesChart,
                    dailyDataList,
                    30,
                    20,
                    { it.pagesRead },
                    useNumericLabels = true,
                    roundBars = true,
                    labelInterval = 5,
                    tag = "MonthlyPages"
                )
            }
        }
    }

    private fun observeWeeklyTimeData() {
        lifecycleScope.launch {
            viewModel.weeklyTimeData.collectLatest { dailyDataList ->
                updateBarChart(
                    binding.weeklyTimeChart,
                    dailyDataList,
                    7,
                    30,
                    { it.secondsReading / 60 },
                    useNumericLabels = true,
                    barSpacingFactor = 2.5f,
                    tag = "WeeklyTime"
                )
            }
        }
    }

    private fun updateBarChart(
        barChartView: BarChartView,
        dailyDataList: List<DailyData>,
        displayDays: Int,
        goal: Int,
        valueSelector: (DailyData) -> Int,
        useNumericLabels: Boolean = false,
        roundBars: Boolean = false,
        labelInterval: Int = 5,
        barSpacingFactor: Float = 1f,
        tag: String
    ) {
        val latestDataDate = dailyDataList.maxByOrNull { LocalDate.parse(it.date, dateFormatter) }
            ?.let { LocalDate.parse(it.date, dateFormatter) } ?: LocalDate.now()

        val endDate: LocalDate = latestDataDate
        val startDate: LocalDate = endDate.minusDays(displayDays.toLong() - 1)

        val allDatesInRange = generateDateRange(startDate, endDate)
        val paddedDataMap = padDataWithZerosToMap(dailyDataList, valueSelector)

        val valueList = allDatesInRange.map { paddedDataMap[it] ?: 0 }
        val dateList = allDatesInRange

        Log.e("StatisticsFragment", "$tag - valueList size: ${valueList.size}, dateList size: ${dateList.size}")

        barChartView.goal = goal
        barChartView.useNumericLabels = useNumericLabels
        barChartView.roundBars = roundBars
        barChartView.labelInterval = labelInterval
        barChartView.barSpacingFactor = barSpacingFactor
        barChartView.displayDays = displayDays // Ensure displayDays is set correctly
        barChartView.setData(valueList, dateList)
    }

    private fun generateDateRange(startDate: LocalDate, endDate: LocalDate): List<LocalDate> {
        val dates = mutableListOf<LocalDate>()
        var currentDate = startDate
        while (!currentDate.isAfter(endDate)) {
            dates.add(currentDate)
            currentDate = currentDate.plusDays(1)
        }
        return dates
    }

    private fun padDataWithZerosToMap(
        actualData: List<DailyData>,
        valueSelector: (DailyData) -> Int
    ): Map<LocalDate, Int> {
        return actualData.associateBy(
            keySelector = { LocalDate.parse(it.date, dateFormatter) },
            valueTransform = valueSelector
        )
    }

    private fun showResetConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.reset_statistics_title)
            .setMessage(R.string.reset_statistics_message)
            .setPositiveButton(R.string.yes) { _, _ ->
                viewModel.resetStatistics()
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    override fun onDestroyView() {
        (activity as? MainActivity)?.setBottomNavVisibility(true)
        super.onDestroyView()
        _binding = null
    }
}

class ReadingProgressViewModelFactory(
    private val statisticsDao: StatisticsDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return StatisticsViewModel(statisticsDao) as T
    }
}