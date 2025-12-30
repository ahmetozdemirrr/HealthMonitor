package com.ahmet.healthmonitor

import android.content.Context
import android.os.Bundle
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

        // ViewPager ve BottomNav Ayarları
        val adapter = ViewPagerAdapter(this)
        binding.viewPager.adapter = adapter
        binding.viewPager.offscreenPageLimit = 3

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

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> binding.viewPager.currentItem = 0
                R.id.nav_history -> binding.viewPager.currentItem = 1
                R.id.nav_analysis -> binding.viewPager.currentItem = 2
                R.id.nav_settings -> binding.viewPager.currentItem = 3
            }
            true
        }

        // --- BLE VERİ İŞLEME MERKEZİ ---
        // BleManager başlatıldıktan sonra callback atanıyor
        BleManager.init(this)

        BleManager.onDataReceived = { bytes ->
            // 1. Gelen Binary veriyi model sınıfına çevir
            val data = BleDataParser.parse(bytes)

            // 2. Verileri SharedPreferences'a kaydet (Böylece HomeFragment görür)
            saveDataToPreferences(data)
        }
    }

    private fun saveDataToPreferences(data: HealthData) {
        val sharedPref = getSharedPreferences("HealthApp", Context.MODE_PRIVATE)

        // Verileri kaydet
        with(sharedPref.edit()) {
            putInt("live_hr", data.heartRateRaw) // Ham IR şimdilik HR gibi davranacak
            putInt("live_spo2", data.spo2.toInt())
            putFloat("live_temp", data.temperature)
            putInt("live_steps", data.stepCount)

            // Eğer isIdle (Uyku) durumu varsa bunu da kaydedebilirsin
            putBoolean("is_worn", data.isWorn)

            apply() // Asenkron kaydet
        }

        // Opsiyonel: Veritabanına kaydet (Sadece anlamlı veri varsa)
        /*
        if (data.isWorn && data.spo2 > 0) {
            DatabaseManager.saveLiveReading(this, data.heartRateRaw, data.spo2.toInt(), data.temperature, data.stepCount)
        }
        */
    }
}