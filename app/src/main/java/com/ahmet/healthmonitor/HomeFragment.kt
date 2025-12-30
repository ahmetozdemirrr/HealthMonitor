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
            "live_hr", "live_spo2", "live_temp", "live_steps" -> {
                updateDashboardUI(sharedPreferences)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
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

    private fun updateDashboardUI(sharedPref: SharedPreferences) {
        // UI güncellemesi Main Thread'de olmalı
        if (!isAdded) return

        activity?.runOnUiThread {
            val hr = sharedPref.getInt("live_hr", 0)
            val spo2 = sharedPref.getInt("live_spo2", 0)
            val temp = sharedPref.getFloat("live_temp", 0.0f)
            val steps = sharedPref.getInt("live_steps", 0)

            // Değerleri yazdır (0 gelirse varsayılan olarak -- gösterilebilir ama şimdilik direkt yazıyoruz)
            binding.tvHeartRate.text = if (hr > 0) hr.toString() else "--"
            binding.tvSpo2.text = if (spo2 > 0) "$spo2%" else "--%"
            binding.tvTemp.text = if (temp > 0) "${String.format("%.1f", temp)}°C" else "--°C"
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