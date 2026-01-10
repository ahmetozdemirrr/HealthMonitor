package com.ahmet.healthmonitor

import java.nio.ByteBuffer
import java.nio.ByteOrder

// Verileri tutacak model sınıfımız (SpO2 Kaldırıldı)
data class HealthData(
    val isWorn: Boolean,      // Takılı mı?
    val isIdle: Boolean,      // Uyuyor mu?
    val stepCount: Int,       // Adım
    val temperature: Float,   // Vücut Isısı
    val heartRateRaw: Int     // Nabız Sinyali
)

object BleDataParser {
    fun parse(bytes: ByteArray): HealthData {
        // Paket boyutu kontrolü (SpO2 gittiği için boyut 4 byte azalabilir)
        // Güvenlik için minimum boyutu kontrol ediyoruz
        if (bytes.size < 13) {
            return HealthData(false, false, 0, 0f, 0)
        }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        // --- OKUMA SIRASI ---

        // 1. Byte: Bayraklar (Flags)
        val flags = buffer.get().toInt()
        val isWorn = (flags and 1) == 1
        val isIdle = (flags and 2) == 2

        // 2. Int (4 Byte): Adım
        val steps = buffer.int

        // 3. Float (4 Byte): Sıcaklık
        val temp = buffer.float

        // (SpO2 okuma satırı buradan silindi)

        // 4. UInt32 (4 Byte): IR / Ham Nabız
        val irValue = buffer.int

        return HealthData(
            isWorn = isWorn,
            isIdle = isIdle,
            stepCount = steps,
            temperature = temp,
            heartRateRaw = irValue
        )
    }
}