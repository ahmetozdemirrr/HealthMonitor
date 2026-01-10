package com.ahmet.healthmonitor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.ahmet.healthmonitor.databinding.FragmentAnalysisBinding
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.launch

class AnalysisFragment : Fragment() {

    private lateinit var binding: FragmentAnalysisBinding

    // Gemini Modelini Tanımla (API KEY BURAYA)
    // Gerçek projede bunu local.properties'den çekmek daha güvenlidir.
    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentAnalysisBinding.inflate(inflater, container, false)

        binding.btnAnalyze.setOnClickListener {
            performAnalysis()
        }

        return binding.root
    }

    private fun performAnalysis() {
        // 1. UI'ı güncelle (Yükleniyor...)
        binding.loadingBar.visibility = View.VISIBLE
        binding.btnAnalyze.isEnabled = false
        binding.tvResult.text = "Consulting with AI..."

        // 2. Simüle edilmiş veya SharedPreferences'tan alınan veriler
        // (Burada gerçek verilerini çekeceksin)
        val heartRate = 78
        // spo2 kaldırıldı
        val temp = 36.5
        val steps = 4520

        // 3. Prompt Hazırla (Mühendislik Kısmı Burası)
        // SpO2 satırı prompt'tan çıkarıldı.
        val prompt = """
            Act as a professional health consultant. Analyze the following user data collected from a smart wristband:
            
            - Heart Rate: $heartRate BPM
            - Skin Temperature: $temp °C
            - Steps Today: $steps
            
            Please provide:
            1. A brief assessment of the current health status.
            2. Potential reasons if any value is abnormal.
            3. Three specific, actionable recommendations for the rest of the day.
            
            Keep the tone professional yet encouraging. Keep the response under 150 words.
        """.trimIndent()

        // 4. API İsteği (Asenkron)
        lifecycleScope.launch {
            try {
                val response = generativeModel.generateContent(prompt)

                // Cevabı yazdır
                binding.tvResult.text = response.text
            } catch (e: Exception) {
                binding.tvResult.text = "Error: ${e.localizedMessage}"
            } finally {
                binding.loadingBar.visibility = View.GONE
                binding.btnAnalyze.isEnabled = true
            }
        }
    }
}