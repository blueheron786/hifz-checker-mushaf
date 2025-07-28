import android.util.Log

class ReadingTimeTracker {
    private var startTime: Long = 0
    private var totalSeconds: Int = 0
    private var isPaused: Boolean = false

    @Synchronized
    fun start() {
        if (isPaused || startTime == 0L) {
            startTime = System.currentTimeMillis()
            isPaused = false
        }
    }

    @Synchronized
    fun pause(): Int {
        if (!isPaused && startTime > 0) {
            val elapsed = ((System.currentTimeMillis() - startTime) / 1000).toInt()
            totalSeconds += elapsed
            isPaused = true
            return totalSeconds
        }
        return totalSeconds
    }

    @Synchronized
    fun getCurrentSessionSeconds(): Int {
        return if (!isPaused && startTime > 0) {
            ((System.currentTimeMillis() - startTime) / 1000).toInt()
        } else {
            0
        }
    }

    @Synchronized
    fun getTotalSeconds(): Int {
        return totalSeconds + getCurrentSessionSeconds()
    }

    @Synchronized
    fun reset() {
        startTime = 0
        totalSeconds = 0
        isPaused = false
    }
}