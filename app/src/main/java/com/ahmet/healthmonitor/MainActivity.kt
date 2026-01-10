package com.ahmet.healthmonitor

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.viewpager2.widget.ViewPager2
import com.ahmet.healthmonitor.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Adapter Kurulumu
        val adapter = ViewPagerAdapter(this)
        binding.viewPager.adapter = adapter
        binding.viewPager.offscreenPageLimit = 3

        // 1. KAYDIRMA OLAYI
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                when (position) {
                    0 -> binding.bottomNavigation.selectedItemId = R.id.nav_home
                    1 -> binding.bottomNavigation.selectedItemId = R.id.nav_history
                    2 -> binding.bottomNavigation.selectedItemId = R.id.nav_analysis
                    3 -> binding.bottomNavigation.selectedItemId = R.id.nav_settings
                }
            }
        })

        // 2. BUTON OLAYI
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> binding.viewPager.currentItem = 0
                R.id.nav_history -> binding.viewPager.currentItem = 1
                R.id.nav_analysis -> binding.viewPager.currentItem = 2
                R.id.nav_settings -> binding.viewPager.currentItem = 3
            }
            true
        }

        // --- BLE VERİ MERKEZİ ---
        // Uygulama açılınca BLE yöneticisini başlat
        BleManager.init(this)

        // Veri geldiğinde ne yapılacağını tanımla
        BleManager.onDataReceived = { bytes ->
            try {
                // 1. Binary veriyi anlamlı modele çevir
                val data = BleDataParser.parse(bytes)

                // 2. Verileri kaydet (HomeFragment buradan okuyacak)
                saveDataToPreferences(data)

                // SpO2 logu kaldırıldı
                Log.d("BLE_DATA", "Adım: ${data.stepCount}, Temp: ${data.temperature}")
            } catch (e: Exception) {
                Log.e("BLE_DATA", "Veri işleme hatası: ${e.message}")
            }
        }
    }

    private fun saveDataToPreferences(data: HealthData) {
        val sharedPref = getSharedPreferences("HealthApp", Context.MODE_PRIVATE)

        with(sharedPref.edit()) {
            // Canlı değerleri güncelle
            putInt("live_hr", data.heartRateRaw) // Ham sinyal veya nabız
            // live_spo2 satırı silindi
            putFloat("live_temp", data.temperature)
            putInt("live_steps", data.stepCount)

            // Durum bilgilerini de ekleyebiliriz
            putBoolean("is_worn", data.isWorn)
            putBoolean("is_idle", data.isIdle)

            apply() // Asenkron kaydet (UI'ı kilitlemez)
        }
    }
}