package com.ahmet.healthmonitor

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// 1. VERSİYONU ARTTIRDIK (Örn: 1 ise 2 yaptık)
@Database(entities = [DailyHealthLog::class], version = 2, exportSchema = false)
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
                    "health_database"
                )
                    .fallbackToDestructiveMigration() // <--- BU SATIRI EKLE (Eski verileri silip yenisini kurar)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}