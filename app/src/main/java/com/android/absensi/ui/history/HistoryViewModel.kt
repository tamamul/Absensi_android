package com.android.absensi.ui.history

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class HistoryViewModel : ViewModel() {
    
    // Data riwayat absensi
    private val _historyItems = MutableLiveData<List<HistoryItem>>()
    val historyItems: LiveData<List<HistoryItem>> = _historyItems
    
    // Loading state
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    // Error message
    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage
    
    // Filter bulan dan tahun
    private val _selectedMonth = MutableLiveData<Int>()
    val selectedMonth: LiveData<Int> = _selectedMonth
    
    private val _selectedYear = MutableLiveData<Int>()
    val selectedYear: LiveData<Int> = _selectedYear
    
    // Statistics riwayat (total, hadir, terlambat, alpha)
    private val _statistics = MutableLiveData<Map<String, Int>>()
    val statistics: LiveData<Map<String, Int>> = _statistics
    
    // Empty state
    private val _isEmpty = MutableLiveData<Boolean>()
    val isEmpty: LiveData<Boolean> = _isEmpty
    
    // Initialize dengan bulan dan tahun saat ini
    init {
        val calendar = java.util.Calendar.getInstance()
        _selectedMonth.value = calendar.get(java.util.Calendar.MONTH) + 1
        _selectedYear.value = calendar.get(java.util.Calendar.YEAR)
        _isEmpty.value = true
    }
    
    // Fungsi untuk mengatur data riwayat
    fun setHistoryItems(items: List<HistoryItem>) {
        _historyItems.value = items
        _isEmpty.value = items.isEmpty()
        // Hitung statistik
        calculateStatistics(items)
    }
    
    // Fungsi untuk menghitung statistik
    private fun calculateStatistics(items: List<HistoryItem>) {
        val stats = mutableMapOf(
            "total" to items.size,
            "hadir" to 0,
            "terlambat" to 0,
            "alpha" to 0
        )
        
        items.forEach { item ->
            when (item.status) {
                "hadir" -> stats["hadir"] = stats["hadir"]!! + 1
                "terlambat" -> stats["terlambat"] = stats["terlambat"]!! + 1
                "alpha" -> stats["alpha"] = stats["alpha"]!! + 1
            }
        }
        
        _statistics.value = stats
    }
    
    // Fungsi untuk mengatur bulan
    fun setMonth(month: Int) {
        _selectedMonth.value = month
    }
    
    // Fungsi untuk mengatur tahun
    fun setYear(year: Int) {
        _selectedYear.value = year
    }
    
    // Fungsi untuk mengatur loading state
    fun setLoading(isLoading: Boolean) {
        _isLoading.value = isLoading
    }
    
    // Fungsi untuk mengatur error message
    fun setErrorMessage(message: String) {
        _errorMessage.value = message
    }
} 