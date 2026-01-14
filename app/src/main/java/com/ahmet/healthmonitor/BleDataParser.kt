package com.ahmet.healthmonitor

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class HealthData(
    val isWorn: Boolean,
    val isIdle: Boolean,
    val stepCount: Int,
    val temperature: Float,
    val heartRateRaw: Int,
    val heartRateBpm: Int
)

object BleDataParser {
    // --- DEMO HIZ AYARI ---
    // Eskiden 50'ydi. 20 yaptık.
    // Artık veri toplamak için 3 saniye değil, sadece 0.5 saniye bekleyecek.
    private const val SAMPLE_SIZE = 20

    private val irBuffer = LongArray(SAMPLE_SIZE)
    private var bufferIndex = 0
    private var lastBeatTime: Long = 0
    private var currentBpm: Int = 0
    private var lastFiltered: Long = 0

    fun parse(bytes: ByteArray): HealthData {
        if (bytes.size < 13) {
            return HealthData(false, false, 0, 0f, 0, 0)
        }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        val flags = buffer.get().toInt()
        val isWorn = (flags and 1) == 1
        val isIdle = (flags and 2) == 2
        val steps = buffer.int
        val temp = buffer.float
        val irValueRaw = buffer.int
        val irValue = if (irValueRaw < 0) irValueRaw + 4294967296L else irValueRaw.toLong()

        val calculatedBpm = calculateBPM(irValue)

        return HealthData(
            isWorn = isWorn,
            isIdle = isIdle,
            stepCount = steps,
            temperature = temp,
            heartRateRaw = irValueRaw,
            heartRateBpm = calculatedBpm
        )
    }

    private fun calculateBPM(irValue: Long): Int {
        // Parmak yoksa 0 (Eşik: 50.000)
        if (irValue < 50000) {
            currentBpm = 0
            lastBeatTime = 0
            // Buffer'ı temizle
            for (i in irBuffer.indices) irBuffer[i] = 0
            return 0
        }

        // Filtreleme
        val filtered = (lastFiltered * 0.7 + irValue * 0.3).toLong()
        lastFiltered = filtered

        // Ortalama hesapla
        var sum: Long = 0
        for (v in irBuffer) sum += v
        val average = sum / SAMPLE_SIZE

        // Buffer güncelle
        irBuffer[bufferIndex] = filtered
        bufferIndex = (bufferIndex + 1) % SAMPLE_SIZE

        // --- HIZLANDIRILMIŞ EŞİK ---
        // Eskiden 8000'di. Şimdi 3000 yaptık.
        // Daha ufak nabızları da hemen yakalayacak.
        val threshold = 3000
        val now = System.currentTimeMillis()

        // Zaman kısıtlaması (400ms = Max 150 BPM)
        if ((filtered > average + threshold) && (now - lastBeatTime > 400)) {
            if (lastBeatTime != 0L) {
                val timeDiff = now - lastBeatTime
                var instantBpm = (60000 / timeDiff).toInt()

                // Çift sayma koruması (Demo Hack)
                if (instantBpm > 110) {
                    instantBpm /= 2
                }

                // Mantıklı sınırlar
                if (instantBpm in 45..130) {
                    if (currentBpm == 0) {
                        currentBpm = instantBpm
                    } else {
                        // Ortalamayı daha hızlı güncelle (0.7 -> 0.5 yaptık)
                        // Değer ekrana daha çabuk yansır
                        currentBpm = (currentBpm * 0.5 + instantBpm * 0.5).toInt()
                    }
                }
            }
            lastBeatTime = now
        }
        return currentBpm
    }
}