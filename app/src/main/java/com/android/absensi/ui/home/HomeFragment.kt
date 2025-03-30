package com.android.absensi.ui.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.android.absensi.R
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

class HomeFragment : Fragment() {

    private lateinit var homeViewModel: HomeViewModel
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Views dari layout fragment_home.xml
    private var _binding: com.android.absensi.databinding.FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // Data user dari SharedPreferences
    private var satpamId: Int = 0
    private var nama: String = ""
    private var nik: String = ""
    private var jabatan: String = ""
    private var lokasiKerja: String = ""
    private var lokasiLatitude: Double = 0.0
    private var lokasiLongitude: Double = 0.0
    private var lokasiRadius: Int = 0

    // Status absensi
    private var isCheckIn = false
    private var isCheckOut = false
    private var jamMasuk: String? = null
    private var jamKeluar: String? = null
    private var statusAbsensi: String = "belum_absen"
    private var shift: String = "-"

    // Timer untuk update jam
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var updateTimeRunnable: Runnable

    // URL API
    private lateinit var urlTodayStatus: String
    private lateinit var urlCheckIn: String
    private lateinit var urlCheckOut: String

    // Lokasi user saat ini
    private var currentLatitude: Double = 0.0
    private var currentLongitude: Double = 0.0
    private var distanceToLocation: Double = 0.0
    private var insideRadius = false

    // Request location permission
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationPermissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || 
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        
        if (locationPermissionGranted) {
            getCurrentLocation()
        } else {
            Toast.makeText(requireContext(), "Izin lokasi diperlukan untuk absensi", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        homeViewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        _binding = com.android.absensi.databinding.FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Init URLs
        urlTodayStatus = getString(R.string.ip_api) + "get_today_status.php"
        urlCheckIn = getString(R.string.ip_api) + "check_in.php"
        urlCheckOut = getString(R.string.ip_api) + "check_out.php"

        // Init location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        // Load data user
        loadUserData()

        // Setup views
        setupViews()

        // Request location permission
        checkLocationPermission()

        // Update tanggal dan jam
        startTimeUpdates()

        // Load status absensi hari ini
        loadTodayStatus()

        // Setup click listeners
        setupClickListeners()

        return root
    }

    private fun loadUserData() {
        val sharedPref = requireActivity().getSharedPreferences("login_data", Context.MODE_PRIVATE)
        satpamId = sharedPref.getInt("id", 0)
        nama = sharedPref.getString("nama", "User") ?: ""
        nik = sharedPref.getString("nik", "") ?: ""
        jabatan = sharedPref.getString("jabatan", "") ?: ""
        lokasiKerja = sharedPref.getString("lokasi_nama", "Unknown Location") ?: ""
        
        // Ambil koordinat dari SharedPreferences
        val lat = sharedPref.getString("lokasi_latitude", "0.0")?.toDoubleOrNull() ?: 0.0
        val lng = sharedPref.getString("lokasi_longitude", "0.0")?.toDoubleOrNull() ?: 0.0
        val radius = sharedPref.getInt("lokasi_radius", 100)
        
        // Log koordinat untuk debugging
        Log.d("HomeFragment", "====== DATA LOKASI DARI SHAREDPREF ======")
        Log.d("HomeFragment", "Lokasi Kerja: $lokasiKerja")
        Log.d("HomeFragment", "Latitude: $lat")
        Log.d("HomeFragment", "Longitude: $lng") 
        Log.d("HomeFragment", "Radius: $radius meter")
        
        // Override koordinat lokasi kerja untuk testing (hapus setelah testing)
        lokasiLatitude = -7.744757
        lokasiLongitude = 112.177116
        lokasiRadius = 5000 // 5km untuk testing
        
        Log.d("HomeFragment", "Koordinat Override: $lokasiLatitude, $lokasiLongitude")
        Log.d("HomeFragment", "Radius Override: $lokasiRadius meter")
        Log.d("HomeFragment", "========================================")
        
        // Update UI langsung
        binding.tvWelcome.text = "Halo, $nama!"
        binding.tvLokasi.text = "Lokasi: $lokasiKerja"
        binding.tvInfoShift.text = "$lokasiKerja"
        
        // Tampilkan informasi lokasi dan koordinat untuk debugging
        binding.tvStatusLokasi.text = "Lokasi kerja: $lokasiKerja\n" +
                                     "Koordinat kerja: $lokasiLatitude, $lokasiLongitude\n" +
                                     "Mendeteksi lokasi Anda..."
        
        // Dapatkan lokasi user saat ini
        getCurrentLocation()
    }

    private fun setupViews() {
        // Set nama user
        binding.tvWelcome.text = getString(R.string.greeting, nama)
        
        // Set lokasi kerja
        binding.tvLokasi.text = getString(R.string.location_info, lokasiKerja)
        
        // Default status
        binding.tvJamMasuk.text = "-"
        binding.tvJamKeluar.text = "-"
        binding.tvStatusAbsensi.text = "Status: Belum Absen"
        binding.tvStatusLokasi.text = "Mendeteksi lokasi..."
    }

    private fun checkLocationPermission() {
        when {
            ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                getCurrentLocation()
            }
            else -> {
                requestLocationPermissions()
            }
        }
    }

    private fun requestLocationPermissions() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            // Tampilkan penjelasan mengapa izin lokasi diperlukan
            Toast.makeText(
                requireContext(),
                "Izin lokasi diperlukan untuk fitur absensi berbasis lokasi",
                Toast.LENGTH_LONG
            ).show()
        }
        
        // Minta izin lokasi
        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestLocationPermissions()
            return
        }

        val cancellationToken = CancellationTokenSource()
        
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationToken.token)
            .addOnSuccessListener { location ->
                location?.let {
                    currentLatitude = it.latitude
                    currentLongitude = it.longitude
                    
                    // Log untuk debugging
                    Log.d("HomeFragment", "====== KOORDINAT USER DITEMUKAN ======")
                    Log.d("HomeFragment", "Latitude: $currentLatitude")
                    Log.d("HomeFragment", "Longitude: $currentLongitude")
                    
                    // Hitung jarak menggunakan Haversine formula
                    distanceToLocation = calculateHaversineDistance(
                        currentLatitude, currentLongitude,
                        lokasiLatitude, lokasiLongitude
                    )
                    
                    Log.d("HomeFragment", "Jarak ke lokasi kerja: $distanceToLocation meter")
                    Log.d("HomeFragment", "Radius lokasi kerja: $lokasiRadius meter")
                    Log.d("HomeFragment", "=====================================")
                    
                    // Cek apakah dalam radius (paksa true untuk testing)
                    insideRadius = true // Selalu dianggap dalam radius untuk testing
                    // insideRadius = distanceToLocation <= lokasiRadius
                    
                    // Update UI dengan info lokasi
                    binding.tvStatusLokasi.text = "Lokasi kerja: $lokasiKerja\n" +
                        "Koordinat kerja: $lokasiLatitude, $lokasiLongitude\n" +
                        "Koordinat Anda: $currentLatitude, $currentLongitude\n" +
                        "Jarak: ${String.format("%.2f", distanceToLocation)} meter"
                    
                    // Update warna card lokasi
                    binding.cardLokasi.setCardBackgroundColor(
                        ContextCompat.getColor(
                            requireContext(),
                            if (insideRadius) R.color.status_hadir else R.color.status_alpha
                        )
                    )
                    
                    // Perbarui status radius di UI (perbaikan)
                    val statusText = if (insideRadius) {
                        "Anda berada dalam radius area kerja ($lokasiRadius meter)"
                    } else {
                        "Anda berada di luar area kerja (${String.format("%.0f", distanceToLocation)} meter)"
                    }
                    binding.tvStatusLokasi.text = statusText
                    
                    // Update button status
                    binding.btnCheckIn.isEnabled = !isCheckIn
                    binding.btnCheckOut.isEnabled = isCheckIn && !isCheckOut
                } ?: run {
                    // Jika lokasi null
                    Log.e("HomeFragment", "Lokasi tidak ditemukan, gunakan nilai default")
                    
                    // Set nilai default
                    currentLatitude = lokasiLatitude
                    currentLongitude = lokasiLongitude
                    distanceToLocation = 10.0 // Anggap jarak dekat
                    insideRadius = true
                    
                    // Update UI
                    binding.tvStatusLokasi.text = "Tidak dapat menemukan lokasi Anda.\n" +
                        "Menggunakan lokasi default untuk testing."
                    binding.cardLokasi.setCardBackgroundColor(
                        ContextCompat.getColor(requireContext(), R.color.status_alpha)
                    )
                    
                    // Tetap aktifkan tombol untuk testing
                    binding.btnCheckIn.isEnabled = !isCheckIn
                    binding.btnCheckOut.isEnabled = isCheckIn && !isCheckOut
                }
            }
            .addOnFailureListener { e ->
                // Jika gagal mendapatkan lokasi
                Log.e("HomeFragment", "Error getting location: ${e.message}", e)
                Toast.makeText(requireContext(), "Gagal mendapatkan lokasi: ${e.message}", Toast.LENGTH_SHORT).show()
                
                // Set nilai default
                currentLatitude = lokasiLatitude
                currentLongitude = lokasiLongitude
                distanceToLocation = 10.0 // Anggap jarak dekat
                insideRadius = true
                
                // Update UI
                binding.tvStatusLokasi.text = "Gagal mendapatkan lokasi. Silakan coba lagi."
                binding.cardLokasi.setCardBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.status_alpha)
                )
                
                // Tetap aktifkan tombol untuk testing
                binding.btnCheckIn.isEnabled = !isCheckIn
                binding.btnCheckOut.isEnabled = isCheckIn && !isCheckOut
            }
    }

    // Fungsi untuk menghitung jarak dengan Haversine formula (fixed)
    private fun calculateHaversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        // Log untuk debugging
        Log.d("HomeFragment", "Calculating distance from ($lat1, $lon1) to ($lat2, $lon2)")
        
        // Validasi koordinat
        if (lat1 == 0.0 && lon1 == 0.0 || lat2 == 0.0 && lon2 == 0.0) {
            Log.e("HomeFragment", "KOORDINAT TIDAK VALID! Menggunakan nilai default 10m")
            return 10.0  // Return jarak default jika koordinat tidak valid
        }

        try {
            val earthRadius = 6371000.0 // radius bumi dalam meter
            
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            
            val a = sin(dLat / 2) * sin(dLat / 2) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                    sin(dLon / 2) * sin(dLon / 2)
            
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            
            val distance = earthRadius * c
            
            // Log hasil perhitungan
            Log.d("HomeFragment", "Jarak dihitung: $distance meter")
            
            return distance
        } catch (e: Exception) {
            Log.e("HomeFragment", "Error menghitung jarak: ${e.message}")
            return 10.0 // Default distance for error case
        }
    }

    private fun startTimeUpdates() {
        updateTimeRunnable = object : Runnable {
            override fun run() {
                val currentDate = Date()
                val dateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID"))
                val timeFormat = SimpleDateFormat("HH:mm:ss", Locale("id", "ID"))
                
                binding.tvTanggal.text = dateFormat.format(currentDate)
                binding.tvJam.text = timeFormat.format(currentDate)
                
                handler.postDelayed(this, 1000)
            }
        }
        
        handler.post(updateTimeRunnable)
    }

    private fun loadTodayStatus() {
        val stringRequest = @RequiresApi(Build.VERSION_CODES.M)
        object : StringRequest(
            Request.Method.POST, urlTodayStatus,
            Response.Listener { response ->
                try {
                    // Cek apakah respons berisi pesan error HTML
                    if (response.contains("<br") || response.contains("<b>Fatal error</b>") || 
                        response.contains("Uncaught") || response.contains("Stack trace")) {
                        Log.e("HomeFragment", "Server mengembalikan error HTML: $response")
                        Toast.makeText(requireContext(), "Error server: Hubungi administrator", Toast.LENGTH_SHORT).show()
                        return@Listener
                    }
                    
                    val jsonResponse = JSONObject(response)
                    val success = jsonResponse.getBoolean("success")
                    
                    if (success) {
                        val data = jsonResponse.getJSONObject("data")
                        
                        // Status
                        val status = data.getJSONObject("status")
                        isCheckIn = status.getBoolean("check_in")
                        isCheckOut = status.getBoolean("check_out")
                        statusAbsensi = status.getString("status_kehadiran")
                        
                        // Jadwal
                        val jadwal = data.optJSONObject("jadwal")
                        if (jadwal != null) {
                            shift = jadwal.getString("shift")
                            val jamKerja = jadwal.getString("jam_kerja")
                            
                            // Set shift info
                            when (shift) {
                                "P" -> binding.tvShift.text = getString(R.string.shift_p)
                                "S" -> binding.tvShift.text = getString(R.string.shift_s)
                                "M" -> binding.tvShift.text = getString(R.string.shift_m)
                                "L" -> binding.tvShift.text = getString(R.string.shift_l)
                                else -> binding.tvShift.text = "Tidak ada jadwal"
                            }
                        } else {
                            binding.tvShift.text = "Tidak ada jadwal"
                        }
                        
                        // Absensi
                        val absensi = data.optJSONObject("absensi")
                        if (absensi != null) {
                            jamMasuk = absensi.optString("jam_masuk")
                            jamKeluar = absensi.optString("jam_keluar")
                            
                            if (!jamMasuk.isNullOrEmpty()) {
                                binding.tvJamMasuk.text = jamMasuk
                            }
                            
                            if (!jamKeluar.isNullOrEmpty()) {
                                binding.tvJamKeluar.text = jamKeluar
                            }
                            
                            // Status absensi
                            when (statusAbsensi) {
                                "hadir" -> {
                                    binding.tvStatusAbsensi.text = "Status: Hadir"
                                    binding.tvStatusAbsensi.setTextColor(resources.getColor(R.color.green_text, null))
                                }
                                "terlambat" -> {
                                    binding.tvStatusAbsensi.text = "Status: Terlambat"
                                    binding.tvStatusAbsensi.setTextColor(resources.getColor(R.color.orange_text, null))
                                }
                                else -> {
                                    binding.tvStatusAbsensi.text = "Status: Belum Absen"
                                    binding.tvStatusAbsensi.setTextColor(resources.getColor(R.color.gray_text, null))
                                }
                            }
                        }
                        
                        // Update button status
                        updateButtonStatus()
                    } else {
                        Toast.makeText(requireContext(), "Gagal memuat status: ${jsonResponse.getString("message")}", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("HomeFragment", "Error parsing status response: ${e.message}", e)
                    Log.e("HomeFragment", "Response was: $response")
                    
                    // Pesan error lebih detail
                    val errorMsg = if (response.contains("<br") || response.contains("<b>Fatal error</b>")) {
                        "Server error: Kontak administrator (error PHP)"
                    } else {
                        "Terjadi kesalahan: ${e.message}"
                    }
                    
                    Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show()
                }
            },
            Response.ErrorListener { error ->
                Toast.makeText(requireContext(), "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                Log.e("HomeFragment", "Error loading status", error)
            }) {
            override fun getParams(): Map<String, String> {
                val params = HashMap<String, String>()
                params["satpam_id"] = satpamId.toString()
                return params
            }
        }
        
        // Tambahkan timeout yang lebih panjang
        stringRequest.retryPolicy = DefaultRetryPolicy(
            30000, // 30 detik timeout
            0,     // Tidak ada retry
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )
        
        Volley.newRequestQueue(requireContext()).add(stringRequest)
    }

    private fun updateButtonStatus() {
        // Check-in button - enable juga walaupun di luar radius untuk mode bypass
        binding.btnCheckIn.isEnabled = !isCheckIn

        // Check-out button - enable juga walaupun di luar radius untuk mode bypass
        binding.btnCheckOut.isEnabled = isCheckIn && !isCheckOut
    }

    private fun setupClickListeners() {
        binding.btnCheckIn.setOnClickListener {
            doCheckIn()
        }
        
        binding.btnCheckOut.setOnClickListener {
            doCheckOut()
        }
    }

    private fun doCheckIn() {
        // Refresh lokasi terlebih dahulu
        getCurrentLocation()
        
        // Debug: log data yang akan dikirim
        Log.d("HomeFragment", "Check-In Request dengan data:")
        Log.d("HomeFragment", "satpam_id: $satpamId")
        Log.d("HomeFragment", "latitude: $currentLatitude")
        Log.d("HomeFragment", "longitude: $currentLongitude")
        Log.d("HomeFragment", "bypass: ${!insideRadius}")
        
        // Cek apakah dalam radius, tapi tetap bisa check-in dengan bypass jika di luar radius
        val bypass = !insideRadius
        
        val stringRequest = @RequiresApi(Build.VERSION_CODES.M)
        object : StringRequest(
            Request.Method.POST, urlCheckIn,
            Response.Listener { response ->
                try {
                    // Cek apakah respons berisi pesan error HTML
                    if (response.contains("<br") || response.contains("<b>Fatal error</b>") || 
                        response.contains("Uncaught") || response.contains("Stack trace")) {
                        Log.e("HomeFragment", "Server mengembalikan error HTML: $response")
                        Toast.makeText(requireContext(), "Error server: Hubungi administrator", Toast.LENGTH_SHORT).show()
                        return@Listener
                    }
                    
                    Log.d("HomeFragment", "Check-In Response: $response")
                    val jsonResponse = JSONObject(response)
                    val success = jsonResponse.getBoolean("success")
                    val message = jsonResponse.getString("message")
                    
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    
                    if (success) {
                        val data = jsonResponse.getJSONObject("data")
                        
                        // Update status
                        isCheckIn = true
                        jamMasuk = data.getString("jam_masuk")
                        statusAbsensi = data.getString("status")
                        
                        // Update UI
                        binding.tvJamMasuk.text = jamMasuk
                        
                        when (statusAbsensi) {
                            "hadir" -> {
                                binding.tvStatusAbsensi.text = "Status: Hadir"
                                binding.tvStatusAbsensi.setTextColor(resources.getColor(R.color.green_text, null))
                            }
                            "terlambat" -> {
                                binding.tvStatusAbsensi.text = "Status: Terlambat"
                                binding.tvStatusAbsensi.setTextColor(resources.getColor(R.color.orange_text, null))
                            }
                        }
                        
                        // Update button status
                        updateButtonStatus()
                    }
                } catch (e: Exception) {
                    Log.e("HomeFragment", "Error parsing check-in response: ${e.message}", e)
                    Log.e("HomeFragment", "Response was: $response")
                    
                    // Pesan error lebih detail
                    val errorMsg = if (response.contains("<br") || response.contains("<b>Fatal error</b>")) {
                        "Server error: Kontak administrator (error PHP)"
                    } else {
                        "Terjadi kesalahan: ${e.message}"
                    }
                    
                    Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show()
                }
            },
            Response.ErrorListener { error ->
                Log.e("HomeFragment", "Error check-in: ${error.message}", error)
                
                // Attempt to get detailed error message
                val networkResponse = error.networkResponse
                if (networkResponse != null && networkResponse.data != null) {
                    try {
                        val errorResponse = String(networkResponse.data, Charsets.UTF_8)
                        Log.e("HomeFragment", "Error response data: $errorResponse")
                    } catch (e: Exception) {
                        Log.e("HomeFragment", "Unable to parse error response")
                    }
                }
                
                Toast.makeText(requireContext(), "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }) {
            override fun getParams(): Map<String, String> {
                val params = HashMap<String, String>()
                params["satpam_id"] = satpamId.toString()
                params["latitude"] = currentLatitude.toString()
                params["longitude"] = currentLongitude.toString()
                if (bypass) {
                    params["bypass"] = "true"
                }
                return params
            }
        }
        
        // Tambahkan timeout yang lebih panjang
        stringRequest.retryPolicy = DefaultRetryPolicy(
            30000, // 30 detik timeout
            0,     // Tidak ada retry
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )
        
        // Log URL yang diakses
        Log.d("HomeFragment", "Mengakses URL: $urlCheckIn")
        
        Volley.newRequestQueue(requireContext()).add(stringRequest)
    }

    private fun doCheckOut() {
        // Refresh lokasi terlebih dahulu
        getCurrentLocation()
        
        // Debug: log data yang akan dikirim
        Log.d("HomeFragment", "Check-Out Request dengan data:")
        Log.d("HomeFragment", "satpam_id: $satpamId")
        Log.d("HomeFragment", "latitude: $currentLatitude")
        Log.d("HomeFragment", "longitude: $currentLongitude")
        Log.d("HomeFragment", "bypass: ${!insideRadius}")
        
        // Cek apakah dalam radius, tapi tetap bisa check-out dengan bypass jika di luar radius
        val bypass = !insideRadius
        
        val stringRequest = object : StringRequest(
            Request.Method.POST, urlCheckOut,
            Response.Listener { response ->
                try {
                    // Cek apakah respons berisi pesan error HTML
                    if (response.contains("<br") || response.contains("<b>Fatal error</b>") || 
                        response.contains("Uncaught") || response.contains("Stack trace")) {
                        Log.e("HomeFragment", "Server mengembalikan error HTML: $response")
                        Toast.makeText(requireContext(), "Error server: Hubungi administrator", Toast.LENGTH_SHORT).show()
                        return@Listener
                    }
                    
                    Log.d("HomeFragment", "Check-Out Response: $response")
                    val jsonResponse = JSONObject(response)
                    val success = jsonResponse.getBoolean("success")
                    val message = jsonResponse.getString("message")
                    
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    
                    if (success) {
                        val data = jsonResponse.getJSONObject("data")
                        
                        // Update status
                        isCheckOut = true
                        jamKeluar = data.getString("jam_keluar")
                        
                        // Update UI
                        binding.tvJamKeluar.text = jamKeluar
                        
                        // Update button status
                        updateButtonStatus()
                    }
                } catch (e: Exception) {
                    Log.e("HomeFragment", "Error parsing check-out response: ${e.message}", e)
                    Log.e("HomeFragment", "Response was: $response")
                    
                    // Pesan error lebih detail
                    val errorMsg = if (response.contains("<br") || response.contains("<b>Fatal error</b>")) {
                        "Server error: Kontak administrator (error PHP)"
                    } else {
                        "Terjadi kesalahan: ${e.message}"
                    }
                    
                    Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show()
                }
            },
            Response.ErrorListener { error ->
                Log.e("HomeFragment", "Error check-out: ${error.message}", error)
                
                // Attempt to get detailed error message
                val networkResponse = error.networkResponse
                if (networkResponse != null && networkResponse.data != null) {
                    try {
                        val errorResponse = String(networkResponse.data, Charsets.UTF_8)
                        Log.e("HomeFragment", "Error response data: $errorResponse")
                    } catch (e: Exception) {
                        Log.e("HomeFragment", "Unable to parse error response")
                    }
                }
                
                Toast.makeText(requireContext(), "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }) {
            override fun getParams(): Map<String, String> {
                val params = HashMap<String, String>()
                params["satpam_id"] = satpamId.toString()
                params["latitude"] = currentLatitude.toString()
                params["longitude"] = currentLongitude.toString()
                if (bypass) {
                    params["bypass"] = "true"
                }
                return params
            }
        }
        
        // Tambahkan timeout yang lebih panjang
        stringRequest.retryPolicy = DefaultRetryPolicy(
            30000, // 30 detik timeout
            0,     // Tidak ada retry
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )
        
        // Log URL yang diakses
        Log.d("HomeFragment", "Mengakses URL: $urlCheckOut")
        
        Volley.newRequestQueue(requireContext()).add(stringRequest)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(updateTimeRunnable)
        _binding = null
    }
}