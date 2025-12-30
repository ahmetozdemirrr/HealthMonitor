package com.ahmet.healthmonitor

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HealthDao {
    // Belirli bir günün kaydını getir
    @Query("SELECT * FROM daily_health_logs WHERE date = :date")
    fun getLogByDate(date: String): DailyHealthLog?

    // Geçmiş grafikleri için son 7 günü getir
    @Query("SELECT * FROM daily_health_logs ORDER BY date DESC LIMIT 7")
    fun getLast7DaysLogs(): List<DailyHealthLog>

    // Kayıt ekle veya varsa üzerine yaz
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertLog(log: DailyHealthLog)

    // 1 haftadan eski verileri sil (Clean-up)
    @Query("SELECT * FROM daily_health_logs WHERE date < :thresholdDate")
    fun getOldLogs(thresholdDate: String): List<DailyHealthLog>

    @Query("DELETE FROM daily_health_logs WHERE date < :thresholdDate")
    fun deleteOldLogs(thresholdDate: String)
}