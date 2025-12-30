package com.ahmet.healthmonitor

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

class HeartRateChart @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val linePaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.sage_green_dark)
        strokeWidth = 8f
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }

    private val fillPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.text_grey)
        textSize = 30f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    // Dummy Veriler (Son 7 günün nabız değerleri)
    private val dataPoints = listOf(65, 60, 90, 75, 85, 98, 68)
    private val days = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat") // Dinamik güncellenecek

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        val padding = 60f
        val chartHeight = height - padding * 2
        val stepX = (width - padding * 2) / (dataPoints.size - 1)

        // Y Eksenindeki çizgiler ve yazılar (60-100 arası)
        val ySteps = 5 // 60, 70, 80, 90, 100
        for (i in 0 until ySteps) {
            val yVal = 60 + (i * 10)
            val yPos = height - padding - (i * (chartHeight / (ySteps - 1)))

            // Yatay çizgiler
            linePaint.strokeWidth = 2f
            linePaint.color = Color.parseColor("#E0E0E0") // Çok açık gri
            canvas.drawLine(padding, yPos, width - padding, yPos, linePaint)

            // Yazılar
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(yVal.toString(), padding - 10, yPos + 10, textPaint)
        }

        // Grafiği Çizme (Path)
        val path = Path()
        val fillPath = Path() // Altını doldurmak için kapalı şekil

        // Başlangıç noktası
        val startY = mapValueToY(dataPoints[0], chartHeight, padding, height)
        path.moveTo(padding, startY)
        fillPath.moveTo(padding, height - padding) // Alt sol köşe
        fillPath.lineTo(padding, startY)

        linePaint.strokeWidth = 8f
        linePaint.color = ContextCompat.getColor(context, R.color.sage_green_dark)

        for (i in 0 until dataPoints.size - 1) {
            val thisX = padding + i * stepX
            val thisY = mapValueToY(dataPoints[i], chartHeight, padding, height)
            val nextX = padding + (i + 1) * stepX
            val nextY = mapValueToY(dataPoints[i + 1], chartHeight, padding, height)

            // Bezier Curve (Yumuşak geçiş)
            val controlX1 = (thisX + nextX) / 2
            val controlY1 = thisY
            val controlX2 = (thisX + nextX) / 2
            val controlY2 = nextY

            path.cubicTo(controlX1, controlY1, controlX2, controlY2, nextX, nextY)
            fillPath.cubicTo(controlX1, controlY1, controlX2, controlY2, nextX, nextY)
        }

        // Fill Path'i kapat
        fillPath.lineTo(width - padding, height - padding)
        fillPath.close()

        // Dolgu Rengi (Gradient)
        val gradient = LinearGradient(0f, 0f, 0f, height,
            ContextCompat.getColor(context, R.color.sage_green_dark),
            Color.TRANSPARENT, Shader.TileMode.CLAMP)
        fillPaint.shader = gradient
        fillPaint.alpha = 50 // Şeffaflık
        canvas.drawPath(fillPath, fillPaint)

        // Çizgiyi çiz
        canvas.drawPath(path, linePaint)

        // Gün isimlerini yaz
        textPaint.textAlign = Paint.Align.CENTER
        for (i in days.indices) {
            val x = padding + i * stepX
            canvas.drawText(days[i], x, height - 10, textPaint)
        }
    }

    private fun mapValueToY(value: Int, chartHeight: Float, padding: Float, totalHeight: Float): Float {
        // 60 ile 100 arasına map ediyoruz
        val range = 100 - 60
        val normalized = (value - 60).toFloat() / range
        return totalHeight - padding - (normalized * chartHeight)
    }

    // Dışarıdan günleri güncellemek için
    fun setDays(newDays: List<String>) {
        // Burada days listesi val olduğu için basitlik adına elle çiziyoruz,
        // gerçek projede bu listeyi var yapıp güncellersin.
        // Şimdilik sistemden günleri MainActivity'de hesaplayıp buraya paslamak gerekirdi
        // ama görsel odaklı olduğumuz için onDraw içinde dummy kullandım.
    }
}