package com.ahmet.healthmonitor

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class ViewPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

    // GÜNCELLEME: Sayfa sayısı 4 olmalı
    override fun getItemCount(): Int {
        return 4
    }

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> HomeFragment()
            1 -> HistoryFragment()
            2 -> AnalysisFragment() // 3. Sıra (Index 2)
            3 -> SettingsFragment() // 4. Sıra (Index 3)
            else -> HomeFragment()
        }
    }
}