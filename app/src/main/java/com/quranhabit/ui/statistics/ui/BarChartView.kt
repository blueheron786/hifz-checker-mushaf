package com.quranhabit.ui.statistics.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.quranhabit.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.max

class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Properties that can be parameterized
    var goal: Int = 10
        set(value) {
            field = value
            invalidate()
        }

    var displayDays: Int = 30
        set(value) {
            field = value
            updateDisplayedData()
            invalidate()
        }

    var useNumericLabels: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var roundBars: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var labelInterval: Int = 5 // Default interval for numeric labels
        set(value) {
            field = max(1, value) // Ensure interval is at least 1
            invalidate()
        }

    var barSpacingFactor: Float = 1f // Factor to control bar spacing (1f is default)
        set(value) {
            field = max(0.1f, value) // Ensure factor is positive
            invalidate()
        }

    // Constants
    private val MAX_TOTAL_DATA_POINTS = 365
    private val MIN_BAR_WIDTH = 10f
    private val BAR_START_OFFSET = 10f // Added space between y-axis and first bar
    private val Y_AXIS_LABEL_PADDING = 14f // Padding to prevent y-axis labels from touching the line
    private val DEFAULT_BAR_SPACING = 2f

    // Internal Properties
    private var barWidth: Float = 0f
    private var barSpacing: Float = 0f
    private var rawData: List<Int> = emptyList()
    private var rawLabels: List<LocalDate> = emptyList()

    private val displayedData: List<Int>
        get() = if (rawData.size > displayDays) rawData.takeLast(displayDays) else rawData

    private val displayedLabels: List<LocalDate>
        get() = if (rawLabels.size > displayDays) rawLabels.takeLast(displayDays) else rawLabels

    // Paints
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.accent)
    }

    private val goalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = ContextCompat.getColor(context, R.color.text_primary)
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f) // 10f is dash length, 10f is gap length
    }

    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = ContextCompat.getColor(context, R.color.text_primary)
        strokeWidth = 2f
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.text_primary)
        textSize = 36f
        textAlign = Paint.Align.CENTER
        isSubpixelText = true
    }

    private val goalLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.text_primary)
        textSize = 32f
        textAlign = Paint.Align.RIGHT
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (displayedData.isEmpty()) return
        val width = width.toFloat()
        val height = height.toFloat()
        val padding = 40f
        val axisPadding = 100f
        val availableWidth = width - padding * 2 - axisPadding - 10f - BAR_START_OFFSET // Added offset here
        val numBars = displayedData.size

        // Calculate bar width and spacing
        if (numBars > 0) {
            val totalSpacing = numBars - 1
            val availableBarSpace = availableWidth - totalSpacing * DEFAULT_BAR_SPACING * barSpacingFactor
            barWidth = max(MIN_BAR_WIDTH, availableBarSpace / numBars)
            barSpacing = DEFAULT_BAR_SPACING * barSpacingFactor
        } else {
            barWidth = 0f
            barSpacing = 0f
        }

        drawAxes(canvas, width, height, padding, axisPadding)
        drawBarsAndLabels(canvas, width, height, padding, axisPadding, height - padding - labelPaint.textSize * 1.5f)
        drawGoalLineAndLabel(canvas, width, height, padding, axisPadding, padding)
    }

    private fun drawAxes(canvas: Canvas, width: Float, height: Float, padding: Float, axisPadding: Float) {
        canvas.drawLine(padding + axisPadding, padding, padding + axisPadding, height - padding - labelPaint.textSize * 1.5f, axisPaint)
        canvas.drawLine(padding + axisPadding, height - padding - labelPaint.textSize * 1.5f, width - padding, height - padding - labelPaint.textSize * 1.5f, axisPaint)
    }

    private fun drawGoalLineAndLabel(canvas: Canvas, width: Float, height: Float, padding: Float,
                                     axisPadding: Float, yTop: Float) {
        val maxValue = max(goal, displayedData.maxOrNull() ?: goal).toFloat()
        val availableHeight = height - padding * 3 - labelPaint.textSize * 2
        val goalY = yTop + (availableHeight * (maxValue - goal) / maxValue) // Changed calculation to draw goal OVER bars

        canvas.drawLine(padding + axisPadding, goalY, width - padding, goalY, goalPaint)
        canvas.drawText("Goal", width - padding - 30f, goalY - 5f, goalLabelPaint)
    }

    private fun drawBarsAndLabels(canvas: Canvas, width: Float, height: Float, padding: Float,
                                  axisPadding: Float, yAxisBottom: Float) {
        val labelFormatter = DateTimeFormatter.ofPattern("d MMM")
        val maxValue = max(goal, displayedData.maxOrNull() ?: goal).toFloat()
        val availableHeight = yAxisBottom - padding
        val numBars = displayedData.size

        displayedData.forEachIndexed { index, value ->
            // Added BAR_START_OFFSET to the left position
            val left = padding + axisPadding + BAR_START_OFFSET + index * (barWidth + barSpacing)
            val barHeight = (availableHeight * value / maxValue)
            val top = yAxisBottom - barHeight
            val right = left + barWidth
            val bottom = yAxisBottom

            val barPath = Path().apply {
                moveTo(left, bottom)
                lineTo(left, top + if (roundBars && barWidth > 2) barWidth / 3 else 0f) // Adjusted rounding
                if (roundBars && barWidth > 2) {
                    val radius = barWidth / 3 // Adjusted rounding radius
                    arcTo(left, top, left + radius * 2, top + radius * 2, 180f, 90f, false)
                    lineTo(right - radius, top)
                    arcTo(right - radius * 2, top, right, top + radius * 2, 270f, 90f, false)
                } else {
                    lineTo(right, top)
                }
                lineTo(right, bottom)
                close()
            }
            canvas.drawPath(barPath, barPaint)

            // Draw x-axis labels conditionally
            val isLastBar = index == numBars - 1
            if (numBars <= 10 || (useNumericLabels && index % labelInterval == 0) || (!useNumericLabels && (numBars <= 10 || index % 5 == 0)) || isLastBar) {
                val labelText = if (isLastBar && displayedLabels.isNotEmpty()) {
                    "Today"
                } else if (useNumericLabels) {
                    (index + 1).toString()
                } else {
                    displayedLabels.getOrNull(index)?.format(labelFormatter)?.take(3) ?: ""
                }
                val labelX = left + barWidth / 2
                val labelY = height - padding - 10f
                canvas.drawText(labelText, (labelX + 0.5f).toInt() - 0.5f, (labelY + 0.5f).toInt() - 0.5f, labelPaint)
            }
        }

        // Draw Y-axis labels
        val yStep = if (maxValue > 0) maxValue / 4 else 1f
        for (i in 0..4) {
            val yValue = i * yStep
            val yPos = yAxisBottom - (availableHeight * yValue / maxValue)
            canvas.drawText(yValue.toInt().toString(), padding + axisPadding - 15f - Y_AXIS_LABEL_PADDING, yPos + labelPaint.textSize / 3, labelPaint)
        }
    }

    fun setData(values: List<Int>, dates: List<LocalDate>) {
        require(values.size == dates.size) { "Values and dates must have the same size" }
        this.rawData = values.takeLast(MAX_TOTAL_DATA_POINTS)
        this.rawLabels = dates.takeLast(MAX_TOTAL_DATA_POINTS)
        updateDisplayedData()
        invalidate()
    }

    private fun updateDisplayedData() {
        // 'displayedData' and 'displayedLabels' are already calculated based on 'displayDays'
    }
}