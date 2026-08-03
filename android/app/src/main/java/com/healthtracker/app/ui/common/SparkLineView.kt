package com.healthtracker.app.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.healthtracker.app.R

/**
 * Minimal line chart for a metric over time.
 *
 * Written by hand rather than pulling in a charting library: the app needs one
 * chart shape, and a dependency would also mean adding a JitPack repository.
 * Nulls are gaps, not zeroes — a day with no reading must not draw a dip to the
 * floor, which would read as a real measurement.
 */
class SparkLineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    private var values: List<Float?> = emptyList()

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = ContextCompat.getColor(context, R.color.accent)
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.accent)
        alpha = 38
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.accent)
    }

    private val baselinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = ContextCompat.getColor(context, R.color.outline)
    }

    fun setValues(newValues: List<Float?>) {
        values = newValues
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val padding = 12f
        val w = width - padding * 2
        val h = height - padding * 2
        canvas.drawLine(padding, height - padding, width - padding, height - padding, baselinePaint)

        val present = values.filterNotNull()
        if (present.size < 2 || w <= 0 || h <= 0) return

        val min = present.min()
        val max = present.max()
        // A flat series would divide by zero; centre it instead.
        val span = (max - min).takeIf { it > 0f }
        val stepX = if (values.size > 1) w / (values.size - 1) else w

        fun xAt(i: Int) = padding + stepX * i
        fun yAt(v: Float) = if (span == null) {
            padding + h / 2f
        } else {
            padding + h - ((v - min) / span) * h
        }

        // Draw each unbroken run separately so gaps stay gaps
        var i = 0
        while (i < values.size) {
            if (values[i] == null) { i++; continue }
            var j = i
            while (j + 1 < values.size && values[j + 1] != null) j++
            if (j > i) {
                val line = Path()
                val fill = Path()
                line.moveTo(xAt(i), yAt(values[i]!!))
                fill.moveTo(xAt(i), height - padding)
                fill.lineTo(xAt(i), yAt(values[i]!!))
                for (k in i + 1..j) {
                    line.lineTo(xAt(k), yAt(values[k]!!))
                    fill.lineTo(xAt(k), yAt(values[k]!!))
                }
                fill.lineTo(xAt(j), height - padding)
                fill.close()
                canvas.drawPath(fill, fillPaint)
                canvas.drawPath(line, linePaint)
            } else {
                // Lone reading between gaps still deserves to be visible
                canvas.drawCircle(xAt(i), yAt(values[i]!!), 5f, dotPaint)
            }
            i = j + 1
        }

        values.indexOfLast { it != null }.takeIf { it >= 0 }?.let { last ->
            canvas.drawCircle(xAt(last), yAt(values[last]!!), 7f, dotPaint)
        }
    }
}
