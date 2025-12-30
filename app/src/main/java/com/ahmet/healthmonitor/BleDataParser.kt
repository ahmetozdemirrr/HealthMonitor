package com.ahmet.healthmonitor

import java.nio.ByteBuffer
import java.nio.ByteOrder

// Verileri tutacak model sınıfımız
data class HealthData(
    val isWorn: Boolean,      // Takılı mı?
    val isIdle: Boolean,      // Uyuyor mu?
    val stepCount: Int,       // Adım
    val temperature: Float,   // Vücut Isısı
    val spo2: Float,          // Oksijen
    val heartRateRaw: Int     // Nabız Sinyali (Grafik için)
)

object BleDataParser {
    fun parse(bytes: ByteArray): HealthData {
        // Paket boyutu kontrolü (Protokolümüz 17 byte)
        if (bytes.size < 17) {
            return HealthData(false, false, 0, 0f, 0f, 0)
        }

        // ESP32 "Little Endian" gönderir, Android "Big Endian" okur.
        // Bunu düzeltmek için order(ByteOrder.LITTLE_ENDIAN) şarttır.
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        // --- OKUMA SIRASI (ESP32 struct yapısıyla aynı olmalı) ---

        // 1. Byte: Bayraklar (Flags)
        val flags = buffer.get().toInt()
        val isWorn = (flags and 1) == 1
        val isIdle = (flags and 2) == 2

        // 2. Int (4 Byte): Adım
        val steps = buffer.int

        // 3. Float (4 Byte): Sıcaklık
        val temp = buffer.float

        // 4. Float (4 Byte): SpO2
        val spo2 = buffer.float

        // 5. UInt32 (4 Byte): IR / Ham Nabız
        val irValue = buffer.int

        return HealthData(
            isWorn = isWorn,
            isIdle = isIdle,
            stepCount = steps,
            temperature = temp,
            spo2 = spo2,
            heartRateRaw = irValue
        )
    }
}