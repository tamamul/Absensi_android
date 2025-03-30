package com.android.absensi.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class HomeViewModel : ViewModel() {
    
    // Status lokasi
    private val _isWithinRadius = MutableLiveData<Boolean>()
    val isWithinRadius: LiveData<Boolean> = _isWithinRadius
    
    // Status absensi
    private val _isCheckIn = MutableLiveData<Boolean>()
    val isCheckIn: LiveData<Boolean> = _isCheckIn
    
    private val _isCheckOut = MutableLiveData<Boolean>()
    val isCheckOut: LiveData<Boolean> = _isCheckOut
    
    // Status loading
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    // Error message
    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage
    
    init {
        _isWithinRadius.value = false
        _isCheckIn.value = false
        _isCheckOut.value = false
        _isLoading.value = false
        _errorMessage.value = ""
    }
    
    fun setWithinRadius(isWithin: Boolean) {
        _isWithinRadius.value = isWithin
    }
    
    fun setCheckIn(isCheckIn: Boolean) {
        _isCheckIn.value = isCheckIn
    }
    
    fun setCheckOut(isCheckOut: Boolean) {
        _isCheckOut.value = isCheckOut
    }
    
    fun setLoading(isLoading: Boolean) {
        _isLoading.value = isLoading
    }
    
    fun setErrorMessage(message: String) {
        _errorMessage.value = message
    }
}