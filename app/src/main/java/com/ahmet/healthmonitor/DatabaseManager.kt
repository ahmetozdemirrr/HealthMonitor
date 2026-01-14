package com.ahmet.healthmonitor

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

object DatabaseManager {
    private const val TAG = "DB_MANAGER"
    private val executor = Executors.newSingleThreadExecutor()

    fun saveLiveReading(context: Context, hr: Int, temp: Float, steps: Int) {
        executor.execute {
            try {
                val db = HealthDatabase.getDatabase(context)
                val dao = db.healthDao()
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val existingLog = dao.getLogByDate(today)

                val newLog = if (existingLog == null) {
                    DailyHealthLog(today, steps, hr, temp, 1)
                } else {
                    val count = existingLog.dataCount
                    val newCount = count + 1
                    val updatedSteps = if (steps > existingLog.steps) steps else existingLog.steps
                    val newAvgHr = ((existingLog.avgHr * count) + hr) / newCount
                    val newAvgTemp = ((existingLog.avgTemp * count) + temp) / newCount
                    DailyHealthLog(today, updatedSteps, newAvgHr, newAvgTemp, newCount)
                }
                dao.insertLog(newLog)
            } catch (e: Exception) {
                Log.e(TAG, "Error saving data", e)
            }
        }
    }

    // HistoryFragment bu fonksiyonu çağırır
    fun getLast7Days(context: Context): List<DailyHealthLog> {
        return try {
            val db = HealthDatabase.getDatabase(context)
            // allowMainThreadQueries() sayesinde artık burası hata vermeyecek
            db.healthDao().getLast7DaysLogs().reversed()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching history", e)
            emptyList()
        }
    }

    // Sadece ilk açılışta çalışır
    fun populateDummyDataIfEmpty(context: Context) {
        executor.execute {
            try {
                val sharedPref = context.getSharedPreferences("HealthApp", Context.MODE_PRIVATE)
                // İsim değiştirildi (v3) ki önceki denemeyi unutup tekrar çalışsın
                val isAlreadyInjected = sharedPref.getBoolean("dummy_data_injected_v3", false)

                if (!isAlreadyInjected) {
                    Log.d(TAG, "Injecting dummy data...")
                    val db = HealthDatabase.getDatabase(context)
                    val dao = db.healthDao()
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

                    // Son 6 gün (Bugün hariç)
                    for (i in 6 downTo 1) {
                        val cal = Calendar.getInstance()
                        cal.add(Calendar.DAY_OF_YEAR, -i)
                        val dateStr = dateFormat.format(cal.time)

                        if (dao.getLogByDate(dateStr) == null) {
                            val dummySteps = (3000..9000).random()
                            val dummyHr = (65..95).random()
                            val dummyTemp = (36.0 + Math.random()).toFloat()

                            val log = DailyHealthLog(dateStr, dummySteps, dummyHr, dummyTemp, 100)
                            dao.insertLog(log)
                        }
                    }
                    sharedPref.edit().putBoolean("dummy_data_injected_v3", true).apply()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Dummy population failed", e)
            }
        }
    }
}