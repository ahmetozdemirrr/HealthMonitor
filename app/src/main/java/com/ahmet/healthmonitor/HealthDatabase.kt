package com.ahmet.healthmonitor

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [DailyHealthLog::class], version = 1, exportSchema = false)
abstract class HealthDatabase : RoomDatabase() {

    abstract fun healthDao(): HealthDao

    companion object {
        @Volatile
        private var INSTANCE: HealthDatabase? = null

        fun getDatabase(context: Context): HealthDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HealthDatabase::class.java,
                    "health_monitor_db"
                )
                    // Ana thread'de sorgu yapmaya izin ver (Basitlik için şimdilik)
                    // Gerçek projede coroutine kullanılmalı ama BleManager içinde çağırmak için
                    // bu ayar işimizi kolaylaştırır.
                    .allowMainThreadQueries()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}