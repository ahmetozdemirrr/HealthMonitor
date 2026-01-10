package com.ahmet.healthmonitor

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.ahmet.healthmonitor.databinding.FragmentHistoryBinding
import java.text.SimpleDateFormat
import java.util.*

class HistoryFragment : Fragment() {
    private lateinit var binding: FragmentHistoryBinding
    private lateinit var calendarAdapter: InfiniteCalendarAdapter

    private val realDataList = ArrayList<DayData>()
    private val LOOP_COUNT = 1000

    data class DayData(
        val dayName: String,
        val dayNumber: String,
        val isAverage: Boolean,
        val fullDate: String = "",
        val avgHr: Int = 0,
        val steps: Int = 0,
        val temp: Double = 0.0
        // spo2 alanı silindi
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        updateChartTarget()
        prepareData()
        setupInfiniteCalendar()
    }

    private fun updateChartTarget() {
        val sharedPref = requireActivity().getSharedPreferences("HealthApp", Context.MODE_PRIVATE)
        val goal = sharedPref.getInt("daily_goal", 6000)
        binding.tvTargetDisplay?.text = "Target: $goal"
        binding.chartActivity?.setTarget(goal)
    }

    private fun prepareData() {
        realDataList.clear()

        // 1. Canlı verileri çek
        val sharedPref = requireActivity().getSharedPreferences("HealthApp", Context.MODE_PRIVATE)
        val liveHr = sharedPref.getInt("live_hr", 0)
        val liveSteps = sharedPref.getInt("live_steps", 0)
        // liveSpo2 silindi
        val liveTemp = sharedPref.getFloat("live_temp", 0f).toDouble()

        // 2. Veritabanından Geçmişi Çek
        val dbHistoryList = DatabaseManager.getLast7Days(requireContext())
        val historyMap = dbHistoryList.associateBy { it.date }

        // 3. Listenin başına AVG ekle
        realDataList.add(DayData("AVG", "ALL", true))

        // 4. Günleri Oluştur
        val calendar = Calendar.getInstance()
        val dayFormat = SimpleDateFormat("EEE", Locale.ENGLISH)
        val numFormat = SimpleDateFormat("dd", Locale.ENGLISH)
        val fullFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.ENGLISH)
        val dbDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        calendar.add(Calendar.DAY_OF_YEAR, -6)

        for (i in 0..6) {
            val isToday = (i == 6)
            val currentDateKey = dbDateFormat.format(calendar.time)

            var hr = 0
            var steps = 0
            var temp = 0.0

            if (isToday) {
                hr = liveHr
                steps = liveSteps
                temp = liveTemp
            } else {
                val historyLog = historyMap[currentDateKey]
                if (historyLog != null) {
                    hr = historyLog.avgHr
                    steps = historyLog.steps
                    temp = historyLog.avgTemp.toDouble()
                    // spo2 okuması silindi
                }
            }

            realDataList.add(DayData(
                dayName = dayFormat.format(calendar.time).uppercase(),
                dayNumber = numFormat.format(calendar.time),
                isAverage = false,
                fullDate = fullFormat.format(calendar.time),
                avgHr = hr,
                steps = steps,
                temp = temp
                // spo2 ataması silindi
            ))
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
    }

    private fun setupInfiniteCalendar() {
        if (::calendarAdapter.isInitialized && binding.rvCalendar.adapter != null) {
            // Mevcut durumu koru
        }

        val layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        binding.rvCalendar.layoutManager = layoutManager

        calendarAdapter = InfiniteCalendarAdapter(realDataList) { selectedData ->
            updateUI(selectedData)
        }
        binding.rvCalendar.adapter = calendarAdapter

        binding.rvCalendar.onFlingListener = null
        val snapHelper = LinearSnapHelper()
        snapHelper.attachToRecyclerView(binding.rvCalendar)

        val totalItems = realDataList.size * LOOP_COUNT
        val startPos = (totalItems / 2) - ((totalItems / 2) % realDataList.size)
        layoutManager.scrollToPosition(startPos)

        if (realDataList.isNotEmpty()) {
            updateUI(realDataList[0])
        }
    }

    private fun updateUI(data: DayData) {
        if (data.isAverage) {
            binding.layoutChartsMode.visibility = View.VISIBLE
            binding.layoutSummaryMode.visibility = View.GONE
        } else {
            binding.layoutChartsMode.visibility = View.GONE
            binding.layoutSummaryMode.visibility = View.VISIBLE

            binding.tvSummaryTitle.text = data.fullDate
            binding.tvDailyHr.text = if (data.avgHr > 0) data.avgHr.toString() else "--"
            binding.tvDailySteps.text = if (data.steps > 0) data.steps.toString() else "--"
            binding.tvDailyTemp.text = if (data.temp > 0) String.format("%.1f", data.temp) else "--"
            // tvDailySpo2 ataması silindi (XML'den de silmeyi unutma veya görünmez yap)
        }
    }

    inner class InfiniteCalendarAdapter(
        private val data: List<DayData>,
        private val onItemClick: (DayData) -> Unit
    ) : RecyclerView.Adapter<InfiniteCalendarAdapter.DayViewHolder>() {

        private var selectedPos = RecyclerView.NO_POSITION

        inner class DayViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tv_day_name)
            val tvNum: TextView = view.findViewById(R.id.tv_day_number)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_calendar_day, parent, false)
            return DayViewHolder(view)
        }

        override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
            val realPosition = position % data.size
            val item = data[realPosition]

            holder.tvName.text = item.dayName
            holder.tvNum.text = item.dayNumber

            val isSelected = (position == selectedPos)
            if (isSelected) {
                holder.tvNum.background = ContextCompat.getDrawable(holder.itemView.context, R.drawable.bg_calendar_selected)
                holder.tvNum.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.white))
                holder.tvName.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.black))
            } else {
                holder.tvNum.background = null
                if (item.isAverage) {
                    val highlightColor = ContextCompat.getColor(holder.itemView.context, R.color.sage_green_dark)
                    holder.tvNum.setTextColor(highlightColor)
                    holder.tvName.setTextColor(highlightColor)
                } else {
                    val normalColor = ContextCompat.getColor(holder.itemView.context, android.R.color.black)
                    holder.tvNum.setTextColor(normalColor)
                    holder.tvName.setTextColor(normalColor)
                }
            }

            holder.itemView.setOnClickListener {
                val previousPos = selectedPos
                selectedPos = holder.layoutPosition
                if (previousPos != RecyclerView.NO_POSITION) notifyItemChanged(previousPos)
                notifyItemChanged(selectedPos)
                binding.rvCalendar.smoothScrollToPosition(selectedPos)
                onItemClick(item)
            }
        }
        override fun getItemCount(): Int = data.size * LOOP_COUNT
    }
}