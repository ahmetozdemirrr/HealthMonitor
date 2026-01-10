package com.ahmet.healthmonitor

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

object DatabaseManager {
    private const val TAG = "DB_MANAGER"
    // Arka plan işlemleri için Executor (UI donmasın diye)
    private val executor = Executors.newSingleThreadExecutor()

    fun saveLiveReading(context: Context, hr: Int, temp: Float, steps: Int) {
        executor.execute {
            try {
                val db = HealthDatabase.getDatabase(context)
                val dao = db.healthDao()

                // Bugünün tarihini al (yyyy-MM-dd)
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                // Bugün için kayıt var mı?
                val existingLog = dao.getLogByDate(today)

                val newLog = if (existingLog == null) {
                    // İlk kayıt
                    DailyHealthLog(
                        date = today,
                        steps = steps,
                        avgHr = hr,
                        // avgSpo2 silindi
                        avgTemp = temp,
                        dataCount = 1
                    )
                } else {
                    // Mevcut kaydı güncelle (Kümülatif Ortalama Formülü)
                    val count = existingLog.dataCount
                    val newCount = count + 1

                    val updatedSteps = if (steps > existingLog.steps) steps else existingLog.steps

                    val newAvgHr = ((existingLog.avgHr * count) + hr) / newCount
                    // newAvgSpo2 silindi
                    val newAvgTemp = ((existingLog.avgTemp * count) + temp) / newCount

                    DailyHealthLog(
                        date = today,
                        steps = updatedSteps,
                        avgHr = newAvgHr,
                        // avgSpo2 silindi
                        avgTemp = newAvgTemp,
                        dataCount = newCount
                    )
                }

                dao.insertLog(newLog)
                Log.d(TAG, "Data saved to DB: $newLog")

                // Temizlik işlemini her kayıtta değil, %5 ihtimalle yap
                if ((0..20).random() == 0) {
                    cleanOldData(context)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error saving data", e)
            }
        }
    }

    private fun cleanOldData(context: Context) {
        try {
            val db = HealthDatabase.getDatabase(context)
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, -7) // 7 gün öncesi

            val thresholdDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)

            db.healthDao().deleteOldLogs(thresholdDate)
            Log.d(TAG, "Cleaned logs older than: $thresholdDate")
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup failed", e)
        }
    }

    // HistoryFragment için verileri getiren yardımcı fonksiyon
    fun getLast7Days(context: Context): List<DailyHealthLog> {
        return try {
            val db = HealthDatabase.getDatabase(context)
            db.healthDao().getLast7DaysLogs().reversed() // Eskiden yeniye sırala
        } catch (e: Exception) {
            emptyList()
        }
    }
}