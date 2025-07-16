package com.android.absensi.ui.history

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class HistoryPagerAdapter(fragmentActivity: FragmentActivity) :
    FragmentStateAdapter(fragmentActivity) {
    override fun createFragment(position: Int): Fragment {
        when (position) {
            0 -> return AttendanceHistoryFragment() // Tab untuk Riwayat Absensi
            1 -> return PermissionsHistoryFragment() // Tab untuk Izin & Sakit
            else -> return AttendanceHistoryFragment()
        }
    }

    override fun getItemCount(): Int {
        return 2 // Kita punya 2 tab
    }
}
