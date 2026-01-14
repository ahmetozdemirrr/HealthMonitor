package com.ahmet.healthmonitor

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.ahmet.healthmonitor.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {
    private lateinit var binding: FragmentHomeBinding

    // Veri değişimini dinleyecek listener
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        when (key) {
            "live_hr", "live_temp", "live_steps", "monitoring_active" -> {
                updateDashboardUI(sharedPreferences)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentHomeBinding.inflate(inflater, container, false)

        // --- UYANDIRMA BUTONU (GÜNCELLENMİŞ HALİ) ---
        // Karta veya yazıya tıklayınca sistemi uyandır
        val wakeUpListener = View.OnClickListener {
            // 1. Hafızada 'Aktif' olarak işaretle
            val sharedPref = requireActivity().getSharedPreferences("HealthApp", Context.MODE_PRIVATE)
            sharedPref.edit().putBoolean("monitoring_active", true).apply()

            // 2. BLE komutunu gönder (ESP32'ye "Gönder" de)
            BleManager.setMonitoring(true)

            // 3. UI'ı hemen güncelle (Kullanıcı tepki görsün)
            binding.tvHeartRate.text = "..."
            binding.tvHeartRate.textSize = 42f
            binding.tvHeartRate.setTextColor(Color.BLACK)

            android.widget.Toast.makeText(requireContext(), "Veri akışı başlatılıyor...", android.widget.Toast.LENGTH_SHORT).show()
        }

        // Hem karta hem de yazıya tıklama özelliği verelim
        binding.cardHeartRate.setOnClickListener(wakeUpListener)
        binding.tvHeartRate.setOnClickListener(wakeUpListener)

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        calculateAndDisplayBMI()

        // Listener'ı kaydet ve mevcut veriyi hemen göster
        val sharedPref = requireActivity().getSharedPreferences("HealthApp", Context.MODE_PRIVATE)
        sharedPref.registerOnSharedPreferenceChangeListener(preferenceListener)
        updateDashboardUI(sharedPref)
    }

    override fun onPause() {
        super.onPause()
        // Hafıza sızıntısını önlemek için listener'ı durdur
        val sharedPref = requireActivity().getSharedPreferences("HealthApp", Context.MODE_PRIVATE)
        sharedPref.unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }

    // UI Güncelleme Fonksiyonu
    private fun updateDashboardUI(sharedPref: SharedPreferences) {
        if (!isAdded) return

        activity?.runOnUiThread {
            val hr = sharedPref.getInt("live_hr", 0)
            val temp = sharedPref.getFloat("live_temp", 0.0f)
            val steps = sharedPref.getInt("live_steps", 0)

            // Sistemin aktif olup olmadığını kontrol et
            val isActive = sharedPref.getBoolean("monitoring_active", true)

            // --- DURUM KONTROLÜ ---
            if (!isActive) {
                // DURUM 1: SİSTEM DURDU (Kırmızı ve Yazı)
                binding.tvHeartRate.text = "BAŞLAT"
                binding.tvHeartRate.textSize = 24f // Yazı sığsın diye küçültüyoruz
                binding.tvHeartRate.setTextColor(Color.RED)
            }
            else if (hr == 0) {
                // DURUM 2: ÖLÇÜLÜYOR (Siyah ve Noktalar)
                binding.tvHeartRate.text = "..."
                binding.tvHeartRate.textSize = 42f
                binding.tvHeartRate.setTextColor(Color.BLACK)
            }
            else {
                // DURUM 3: VERİ VAR (Siyah ve Değer)
                binding.tvHeartRate.text = hr.toString()
                binding.tvHeartRate.textSize = 42f
                binding.tvHeartRate.setTextColor(Color.BLACK)
            }

            // Diğer veriler her zaman görünür
            binding.tvTemp.text = if (temp > 0) "${String.format("%.1f", temp)}°C" else "--"
            binding.tvSteps.text = if (steps > 0) steps.toString() else "0"
        }
    }

    private fun calculateAndDisplayBMI() {
        val sharedPref = requireActivity().getSharedPreferences("HealthApp", Context.MODE_PRIVATE)

        val weight = sharedPref.getInt("user_weight", 75).toDouble()
        val heightCm = sharedPref.getInt("user_height", 175).toDouble()

        val heightM = heightCm / 100.0
        val bmi = weight / (heightM * heightM)

        binding.tvBmiValue.text = String.format("%.1f", bmi)

        val statusText: String
        val color: Int

        when {
            bmi < 18.5 -> {
                statusText = "Underweight"
                color = Color.parseColor("#FFB74D")
            }
            bmi < 25.0 -> {
                statusText = "Normal Weight"
                color = ContextCompat.getColor(requireContext(), R.color.sage_green_dark)
            }
            bmi < 30.0 -> {
                statusText = "Overweight"
                color = Color.parseColor("#FF7043")
            }
            else -> {
                statusText = "Obese"
                color = Color.parseColor("#E53935")
            }
        }

        binding.tvBmiStatus.text = statusText
        binding.tvBmiStatus.setTextColor(color)

        val maxBmiLimit = 40.0
        var progressValue = ((bmi / maxBmiLimit) * 100).toInt()

        if (progressValue > 100) progressValue = 100

        binding.progressBmi.progress = progressValue
        binding.progressBmi.progressTintList = ColorStateList.valueOf(color)
    }
}