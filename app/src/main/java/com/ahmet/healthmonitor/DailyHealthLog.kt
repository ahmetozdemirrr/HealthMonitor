package com.ahmet.healthmonitor

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_health_logs")
data class DailyHealthLog(
    @PrimaryKey
    val date: String, // Format: "yyyy-MM-dd" (Benzersiz Anahtar)

    val steps: Int,
    val avgHr: Int,
    val avgSpo2: Int,
    val avgTemp: Float,

    // Ortalamayı doğru hesaplamak için o gün kaç veri geldiğini tutuyoruz
    val dataCount: Int
)