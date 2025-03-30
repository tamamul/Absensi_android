package com.android.absensi.ui.schedule

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ScheduleViewModel : ViewModel() {
    private val _selectedMonth = MutableLiveData<Int>()
    val selectedMonth: LiveData<Int> = _selectedMonth
    
    private val _selectedYear = MutableLiveData<Int>()
    val selectedYear: LiveData<Int> = _selectedYear
    
    fun setMonth(month: Int) {
        _selectedMonth.value = month
    }
    
    fun setYear(year: Int) {
        _selectedYear.value = year
    }
} 