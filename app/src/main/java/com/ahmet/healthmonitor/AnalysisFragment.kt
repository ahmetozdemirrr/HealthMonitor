package com.ahmet.healthmonitor

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.ahmet.healthmonitor.databinding.FragmentAnalysisBinding
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AnalysisFragment : Fragment() {

    private lateinit var binding: FragmentAnalysisBinding
    private val TAG = "GEMINI_TEST"

    private val modelPriorities = listOf(
        "gemini-2.0-flash",
        "gemini-2.5-flash",
        "gemini-flash-latest",
        "gemini-1.5-flash"
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentAnalysisBinding.inflate(inflater, container, false)
        binding.btnAnalyze.setOnClickListener { performAnalysisWithFallback() }
        return binding.root
    }

    private fun performAnalysisWithFallback() {
        binding.loadingBar.visibility = View.VISIBLE
        binding.btnAnalyze.isEnabled = false
        binding.tvResult.text = "Analyzing data..."

        lifecycleScope.launch {
            // 1. Veriyi Hazırla (Canlı yoksa geçmişe bak)
            val data = withContext(Dispatchers.IO) { gatherSmartData() }

            if (BuildConfig.GEMINI_API_KEY.isBlank()) {
                binding.tvResult.text = "Error: API Key missing."
                binding.loadingBar.visibility = View.GONE
                binding.btnAnalyze.isEnabled = true
                return@launch
            }

            val prompt = """
                Act as a professional health consultant. Analyze this biometric data (${data.source}):
                
                - Avg Heart Rate: ${data.hr} BPM
                - Avg Temp: ${String.format("%.1f", data.temp)} °C
                - Steps: ${data.steps}
                
                Provide 3 clear sections:
                1. **Status**: Is this within healthy range?
                2. **Observations**: Any anomalies? (If HR is 0, warn sensor issue).
                3. **Recommendations**: 3 specific health tips.
                
                Keep under 150 words.
            """.trimIndent()

            var success = false
            for (modelName in modelPriorities) {
                if (success) break
                try {
                    withContext(Dispatchers.Main) { binding.tvResult.text = "Consulting $modelName..." }
                    val model = GenerativeModel(modelName, BuildConfig.GEMINI_API_KEY)
                    val response = model.generateContent(prompt)
                    response.text?.let {
                        withContext(Dispatchers.Main) { binding.tvResult.text = it }
                        success = true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Fail $modelName: ${e.message}")
                }
            }

            withContext(Dispatchers.Main) {
                binding.loadingBar.visibility = View.GONE
                binding.btnAnalyze.isEnabled = true
                if (!success) binding.tvResult.text = "Analysis failed. Check internet."
            }
        }
    }

    private fun gatherSmartData(): AnalysisData {
        val sharedPref = requireActivity().getSharedPreferences("HealthApp", Context.MODE_PRIVATE)
        val liveHr = sharedPref.getInt("live_hr", 0)

        // 1. Canlı veri varsa onu kullan
        if (liveHr > 0) {
            return AnalysisData(
                liveHr,
                sharedPref.getInt("live_steps", 0),
                sharedPref.getFloat("live_temp", 0f).toDouble(),
                "Live Data"
            )
        }

        // 2. Canlı veri yoksa, son 7 günün ortalamasını al
        try {
            val history = DatabaseManager.getLast7Days(requireContext())
            if (history.isNotEmpty()) {
                val avgHr = history.map { it.avgHr }.average().toInt()
                val avgSteps = history.map { it.steps }.average().toInt()
                val avgTemp = history.map { it.avgTemp }.average()
                return AnalysisData(avgHr, avgSteps, avgTemp, "Last 7 Days Average")
            }
        } catch (e: Exception) {
            Log.e(TAG, "DB Error", e)
        }

        return AnalysisData(0, 0, 0.0, "No Data")
    }

    data class AnalysisData(val hr: Int, val steps: Int, val temp: Double, val source: String)
}