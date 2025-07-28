import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quranhabit.data.dao.StatisticsDao
import com.quranhabit.ui.statistics.DailyData
import com.quranhabit.utils.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StatisticsViewModel(private val statisticsDao: StatisticsDao) : ViewModel() {
    private val _pagesReadToday = MutableLiveData<Int>()
    val pagesReadToday: LiveData<Int> = _pagesReadToday

    private val _pagesReadMonth = MutableLiveData<Int>()
    val pagesReadMonth: LiveData<Int> = _pagesReadMonth

    private val _totalPagesRead = MutableLiveData<Int>()
    val totalPagesRead: LiveData<Int> = _totalPagesRead

    private val _streakDays = MutableLiveData<Int>();
    val streakDays: LiveData<Int> = _streakDays;

    private val _timeReadToday = MutableLiveData<Int>()
    val timeReadToday: LiveData<Int> = _timeReadToday

    private val _totalTimeRead = MutableLiveData<Int>()
    val totalTimeRead: LiveData<Int> = _totalTimeRead

    private val _monthlyData = MutableStateFlow<List<DailyData>>(emptyList())
    val monthlyData: StateFlow<List<DailyData>> = _monthlyData

    private val _weeklyTimeData = MutableStateFlow<List<DailyData>>(emptyList()) // Add this
    val weeklyTimeData: StateFlow<List<DailyData>> = _weeklyTimeData // Add this

    init {
        loadProgress()
    }

    private fun loadProgress() {
        viewModelScope.launch {
            // Text data
            val todayDate = DateUtils.getTodayDate()

            val dataForToday = withContext(Dispatchers.IO) {
                statisticsDao.getByDate(todayDate)
            }

            val dataForMonth = withContext(Dispatchers.IO) {
                statisticsDao.getTimeRangeStatsData(30)
            }

            val dataForWeekTime = withContext(Dispatchers.IO) { // Fetch data for the time chart
                statisticsDao.getTimeRangeStatsData(7)
            }

            val totalPagesRead = withContext(Dispatchers.IO) {
                statisticsDao.getTotalPagesRead()
            }

            val streakDays = withContext(Dispatchers.IO) {
                statisticsDao.getCurrentStreakDays()
            }

            val totalTimeRead = withContext(Dispatchers.IO) {
                statisticsDao.getTotalTimeRead()
            }

            _pagesReadToday.value = dataForToday?.pagesRead ?: 0
            _pagesReadMonth.value = dataForMonth.sumOf { it.pagesRead }
            _timeReadToday.value = dataForToday?.secondsSpendReading ?: 0
            _totalPagesRead.value = totalPagesRead
            _streakDays.value = streakDays
            _totalTimeRead.value = totalTimeRead

            // Graph data
            _monthlyData.value = dataForMonth
            _weeklyTimeData.value = dataForWeekTime // Emit data for the time chart
        }
    }

    fun resetStatistics() {
        viewModelScope.launch {
            statisticsDao.resetAllStatistics()
            _pagesReadToday.value = 0
            _totalPagesRead.value = 0
            _timeReadToday.value = 0
            _totalTimeRead.value = 0
            _streakDays.value = 0
            _pagesReadMonth.value = 0
            _monthlyData.value = emptyList()
            _weeklyTimeData.value = emptyList() // Reset weekly time data
        }
    }
}