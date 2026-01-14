package com.ahmet.healthmonitor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

class ActivityBarChart @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var stepsData: List<Int> = listOf()
    private var days: List<String> = listOf()

    private var target = 6000

    private val barPaint = Paint().apply { isAntiAlias = true }

    // Yandaki rakamlar için text paint (Sağa yaslı)
    private val textPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.text_grey)
        textSize = 30f
        isAntiAlias = true
        textAlign = Paint.Align.RIGHT
    }

    // Alttaki gün isimleri için text paint (Ortalı)
    private val dayTextPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.text_grey)
        textSize = 30f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    fun setTarget(newTarget: Int) {
        this.target = newTarget
        invalidate()
    }

    // Veriyi güncellemek için metod
    fun setChartData(newSteps: List<Int>, newDays: List<String>) {
        this.stepsData = newSteps
        this.days = newDays
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (stepsData.isEmpty()) return

        val width = width.toFloat()
        val height = height.toFloat()

        val paddingLeft = 110f
        val paddingBottom = 60f
        val paddingTop = 60f
        val paddingRight = 40f

        val chartHeight = height - paddingTop - paddingBottom
        val chartWidth = width - paddingLeft - paddingRight

        val barWidth = 40f
        // Bölen 0 olmasın
        val divCount = if (stepsData.isNotEmpty()) stepsData.size else 1
        val stepX = chartWidth / divCount

        // Y Ekseni (Rakamlar)
        val maxVal = 15000
        val steps = 5
        val stepValue = 3000

        for (i in 0..steps) {
            val value = i * stepValue
            val yPos = height - paddingBottom - (i * (chartHeight / steps))
            canvas.drawText(value.toString(), paddingLeft - 20, yPos + 10, textPaint)
        }

        // Barlar ve Gün İsimleri
        for (i in stepsData.indices) {
            val value = stepsData[i]
            val barHeight = (value.toFloat().coerceAtMost(maxVal.toFloat()) / maxVal.toFloat()) * chartHeight

            // Barın merkezi x konumu
            val centerX = paddingLeft + (i * stepX) + (stepX/2)

            val left = centerX - (barWidth/2)
            val top = height - paddingBottom - barHeight
            val right = left + barWidth
            val bottom = height - paddingBottom

            if (value >= target) {
                barPaint.color = ContextCompat.getColor(context, R.color.sage_green_light)
            } else {
                barPaint.color = ContextCompat.getColor(context, R.color.red_accent)
            }

            val rect = RectF(left, top, right, bottom)
            canvas.drawRoundRect(rect, 10f, 10f, barPaint)

            // Gün ismini barın altına yaz
            if (i < days.size) {
                canvas.drawText(days[i], centerX, height - paddingBottom + 40f, dayTextPaint)
            }
        }
    }
}