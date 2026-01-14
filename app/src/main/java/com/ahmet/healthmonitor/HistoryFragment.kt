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
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        // Bu çağrı artık HealthDatabase'deki allowMainThreadQueries() sayesinde çalışacak
        prepareData()
        setupInfiniteCalendar()

        val sharedPref = requireActivity().getSharedPreferences("HealthApp", Context.MODE_PRIVATE)
        binding.chartActivity?.setTarget(sharedPref.getInt("daily_goal", 6000))
    }

    private fun prepareData() {
        realDataList.clear()

        // Veritabanından son 7 günü çek
        val dbHistoryList = DatabaseManager.getLast7Days(requireContext())
        val historyMap = dbHistoryList.associateBy { it.date }

        val sharedPref = requireActivity().getSharedPreferences("HealthApp", Context.MODE_PRIVATE)
        val liveHr = sharedPref.getInt("live_hr", 0)

        // AVG kartını ekle
        realDataList.add(DayData("AVG", "ALL", true))

        val chartHrList = mutableListOf<Int>()
        val chartStepList = mutableListOf<Int>()
        val chartDays = mutableListOf<String>()

        val calendar = Calendar.getInstance()
        val dayFormat = SimpleDateFormat("EEE", Locale.ENGLISH)
        val numFormat = SimpleDateFormat("dd", Locale.ENGLISH)
        val fullFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.ENGLISH)
        val dbDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        // Son 7 günü oluştur
        calendar.add(Calendar.DAY_OF_YEAR, -6)
        for (i in 0..6) {
            val isToday = (i == 6)
            val currentDateKey = dbDateFormat.format(calendar.time)

            var hr = 0
            var steps = 0
            var temp = 0.0

            if (isToday) {
                hr = liveHr
                steps = sharedPref.getInt("live_steps", 0)
                temp = sharedPref.getFloat("live_temp", 0f).toDouble()
            } else {
                val log = historyMap[currentDateKey]
                if (log != null) {
                    hr = log.avgHr
                    steps = log.steps
                    temp = log.avgTemp.toDouble()
                }
            }

            chartHrList.add(hr)
            chartStepList.add(steps)
            chartDays.add(dayFormat.format(calendar.time).uppercase())

            realDataList.add(DayData(
                dayName = dayFormat.format(calendar.time).uppercase(),
                dayNumber = numFormat.format(calendar.time),
                isAverage = false,
                fullDate = fullFormat.format(calendar.time),
                avgHr = hr,
                steps = steps,
                temp = temp
            ))
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        binding.chartHeartRate?.setChartData(chartHrList, chartDays)
        binding.chartActivity?.setChartData(chartStepList, chartDays)
    }

    private fun setupInfiniteCalendar() {
        val layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        binding.rvCalendar.layoutManager = layoutManager
        calendarAdapter = InfiniteCalendarAdapter(realDataList) { updateUI(it) }
        binding.rvCalendar.adapter = calendarAdapter

        // Listeyi ortala (Bugün veya AVG)
        val middle = (realDataList.size * LOOP_COUNT) / 2
        layoutManager.scrollToPosition(middle - (middle % realDataList.size) + (realDataList.size - 1)) // Bugüne git

        if (realDataList.isNotEmpty()) updateUI(realDataList.last()) // Bugünü göster
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
            val item = data[position % data.size]
            holder.tvName.text = item.dayName
            holder.tvNum.text = item.dayNumber

            if (position == selectedPos) {
                holder.tvNum.setBackgroundResource(R.drawable.bg_calendar_selected)
                holder.tvNum.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.white))
            } else {
                holder.tvNum.background = null
                holder.tvNum.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.black))
            }

            holder.itemView.setOnClickListener {
                val prev = selectedPos
                selectedPos = holder.layoutPosition
                notifyItemChanged(prev)
                notifyItemChanged(selectedPos)
                onItemClick(item)
            }
        }
        override fun getItemCount(): Int = data.size * LOOP_COUNT
    }
}