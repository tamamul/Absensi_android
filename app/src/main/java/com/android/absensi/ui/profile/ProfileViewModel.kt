package com.android.absensi.ui.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ProfileViewModel : ViewModel() {
    private val _profileData = MutableLiveData<Map<String, String>>()
    val profileData: LiveData<Map<String, String>> = _profileData
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage
    
    fun setProfileData(data: Map<String, String>) {
        _profileData.value = data
    }
    
    fun setLoading(isLoading: Boolean) {
        _isLoading.value = isLoading
    }
    
    fun setErrorMessage(message: String) {
        _errorMessage.value = message
    }
}