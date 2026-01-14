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
    private var emptyDataCounter = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // --- BU SATIR ÇOK ÖNEMLİ (EKSİK OLAN KISIM) ---
        // Uygulama her açıldığında kontrol eder: Eğer veritabanı boşsa dummy verilerle doldurur.
        // Bunu yapmazsak History ekranı boş kalır.
        DatabaseManager.populateDummyDataIfEmpty(this)

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
                val data = BleDataParser.parse(bytes)

                // Önce verileri kaydet
                val sharedPref = getSharedPreferences("HealthApp", Context.MODE_PRIVATE)
                with(sharedPref.edit()) {
                    putInt("live_hr", data.heartRateBpm)
                    putFloat("live_temp", data.temperature)
                    putInt("live_steps", data.stepCount)

                    // Veri geliyorsa sistem aktiftir
                    if (data.heartRateRaw > 50000) {
                        putBoolean("monitoring_active", true)
                    }
                    apply()
                }

                // Canlı veriyi veritabanına kaydet (History grafikleri için)
                if (data.heartRateBpm > 0) {
                    DatabaseManager.saveLiveReading(
                        this,
                        data.heartRateBpm,
                        data.temperature,
                        data.stepCount
                    )
                }

                // --- OTOMATİK DURDURMA MANTIĞI ---
                // Eğer sensörden gelen ham veri çok düşükse (Saat takılı değilse)
                if (data.heartRateRaw < 50000) {
                    emptyDataCounter++

                    // Yaklaşık 4 saniye (40 paket) boyunca veri gelmezse durdur
                    if (emptyDataCounter > 40) {
                        runOnUiThread {
                            // 1. Veri akışını fiziksel olarak kes
                            BleManager.setMonitoring(false)

                            // 2. Sistemin durduğunu hafızaya yaz (Kritik Nokta)
                            sharedPref.edit().putBoolean("monitoring_active", false).apply()

                            // 3. Kullanıcıya bilgi ver
                            android.widget.Toast.makeText(this, "Saat çıkarıldı. Tasarruf modu aktif.", android.widget.Toast.LENGTH_SHORT).show()

                            emptyDataCounter = 0
                        }
                    }
                } else {
                    emptyDataCounter = 0
                }

            } catch (e: Exception) {
                Log.e("BLE_DATA", "Veri işleme hatası: ${e.message}")
            }
        }
    }
}