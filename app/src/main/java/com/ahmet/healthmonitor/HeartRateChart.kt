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

    private var dataPoints: List<Int> = listOf()
    private var days: List<String> = listOf()

    // Veriyi güncellemek için metod
    fun setChartData(newData: List<Int>, newDays: List<String>) {
        this.dataPoints = newData
        this.days = newDays
        invalidate() // Tekrar çiz
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (dataPoints.isEmpty()) return

        val width = width.toFloat()
        val height = height.toFloat()
        val padding = 60f
        val chartHeight = height - padding * 2

        // Veri sayısına göre stepX belirle (en az 1 olmalı)
        val divCount = if (dataPoints.size > 1) dataPoints.size - 1 else 1
        val stepX = (width - padding * 2) / divCount

        // Y Eksenindeki çizgiler ve yazılar (60-100 arası)
        val ySteps = 5 // 60, 70, 80, 90, 100
        for (i in 0 until ySteps) {
            val yVal = 60 + (i * 10)
            val yPos = height - padding - (i * (chartHeight / (ySteps - 1)))

            // Yatay çizgiler
            linePaint.strokeWidth = 2f
            linePaint.color = Color.parseColor("#E0E0E0")
            canvas.drawLine(padding, yPos, width - padding, yPos, linePaint)

            // Yazılar
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(yVal.toString(), padding - 10, yPos + 10, textPaint)
        }

        // Grafiği Çizme (Path)
        val path = Path()
        val fillPath = Path() // Altını doldurmak için kapalı şekil

        linePaint.strokeWidth = 8f
        linePaint.color = ContextCompat.getColor(context, R.color.sage_green_dark)

        var isFirstPoint = true
        var pathStarted = false

        for (i in dataPoints.indices) {
            val value = dataPoints[i]
            // Sadece değeri 0'dan büyük olanları çiz (Boş günler atlanır)
            if (value > 0) {
                val thisX = padding + i * stepX
                val thisY = mapValueToY(value, chartHeight, padding, height)

                if (isFirstPoint) {
                    path.moveTo(thisX, thisY)
                    fillPath.moveTo(thisX, height - padding)
                    fillPath.lineTo(thisX, thisY)
                    isFirstPoint = false
                    pathStarted = true
                } else {
                    // Önceki nokta ile bezier curve yapalım mı?
                    // Eğer önceki nokta 0 ise (atlandıysa), moveTo yapmalıyız.
                    // Bu basit mantıkta, önceki noktayı tutmadığımız için direkt lineTo yaparsak,
                    // aradaki 0 olan günlerin üzerinden çizgi geçer.
                    // Ancak "çizmeyebilir" dendiği için, eğer önceki gün 0 ise path kopuk olmalı.

                    val prevValue = if(i > 0) dataPoints[i-1] else 0
                    if (prevValue == 0) {
                        // Yeni bir parça başlat
                        path.moveTo(thisX, thisY)

                        // Fill path için önceki parçayı kapatıp yenisini açmak karmaşık olabilir,
                        // basitçe fill'i kapatıp yeniden başlatıyoruz.
                        fillPath.lineTo(thisX, height - padding)
                        fillPath.close() // Önceki bloğu kapat

                        fillPath.moveTo(thisX, height - padding)
                        fillPath.lineTo(thisX, thisY)
                    } else {
                        // Devam et
                        val prevX = padding + (i - 1) * stepX
                        val prevY = mapValueToY(prevValue, chartHeight, padding, height)

                        val controlX1 = (prevX + thisX) / 2
                        val controlY1 = prevY
                        val controlX2 = (prevX + thisX) / 2
                        val controlY2 = thisY

                        path.cubicTo(controlX1, controlY1, controlX2, controlY2, thisX, thisY)
                        fillPath.cubicTo(controlX1, controlY1, controlX2, controlY2, thisX, thisY)
                    }
                }

                // Son nokta ise fill path'i aşağı indir
                if (i == dataPoints.indices.lastOrNull { dataPoints[it] > 0 }) {
                    fillPath.lineTo(thisX, height - padding)
                }
            }
        }

        fillPath.close()

        if (pathStarted) {
            // Dolgu Rengi (Gradient)
            val gradient = LinearGradient(0f, 0f, 0f, height,
                ContextCompat.getColor(context, R.color.sage_green_dark),
                Color.TRANSPARENT, Shader.TileMode.CLAMP)
            fillPaint.shader = gradient
            fillPaint.alpha = 50 // Şeffaflık
            canvas.drawPath(fillPath, fillPaint)

            // Çizgiyi çiz
            canvas.drawPath(path, linePaint)
        }

        // Gün isimlerini yaz (Veri olmasa da günleri yazıyoruz)
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
        // Clamp (Sınırla)
        val clamped = normalized.coerceIn(0f, 1f)
        return totalHeight - padding - (clamped * chartHeight)
    }
}