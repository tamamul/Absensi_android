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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.android.absensi.R
import com.android.volley.AuthFailureError
import com.android.volley.DefaultRetryPolicy
import com.android.volley.NetworkError
import com.android.volley.NoConnectionError
import com.android.volley.ParseError
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.ServerError
import com.android.volley.TimeoutError
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

class HomeFragment : Fragment() {

    // Enum untuk status lokasi
    enum class tvLocationStatus {
        WITHIN_RADIUS,
        OUTSIDE_RADIUS,
        LOCATION_ERROR,
        DETECTING
    }

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
    
    // Timer untuk update lokasi dan status dari server
    private lateinit var locationUpdateRunnable: Runnable
    private var locationUpdateInterval: Long = 30000 // 30 detik

    // URL API
    private lateinit var urlTodayStatus: String
    private lateinit var urlCheckIn: String
    private lateinit var urlCheckOut: String

    // Lokasi user saat ini
    private var currentLatitude: Double = 0.0
    private var currentLongitude: Double = 0.0
    private var distanceToLocation: Double = 0.0
    private var insideRadius = false
    private var hasJadwalToday = false // Tambahan: status ada jadwal hari ini

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

        // Setup views
        setupViews()

        // Load data user
        loadUserData()

        // Request location permission
        checkLocationPermission()

        // Update tanggal dan jam
        startTimeUpdates()
        
        // Mulai pembaruan lokasi berkala
        startLocationUpdates()

        // Load status absensi hari ini
        loadTodayStatus()

        // Setup click listeners
        setupClickListeners()
        
        // Tambahkan swipe refresh
        binding.swipeRefresh.setOnRefreshListener {
            refreshAllData()
        }

        return root
    }
    
    private fun refreshAllData() {
        // Ambil lokasi terbaru
        getCurrentLocation()
        
        // Ambil status dari server
        loadTodayStatus()
        
        // Selesai refresh
        binding.swipeRefresh.isRefreshing = false
    }

    private fun loadUserData() {
        val sharedPref = requireActivity().getSharedPreferences("login_data", Context.MODE_PRIVATE)
        satpamId = sharedPref.getInt("id", 0)
        nama = sharedPref.getString("nama", "User") ?: ""
        nik = sharedPref.getString("nik", "") ?: ""
        jabatan = sharedPref.getString("jabatan", "") ?: ""
        lokasiKerja = sharedPref.getString("lokasi_nama", "Unknown Location") ?: ""
        
        // Ambil koordinat asli dari SharedPreferences
        lokasiLatitude = sharedPref.getString("lokasi_latitude", "0.0")?.toDoubleOrNull() ?: 0.0
        lokasiLongitude = sharedPref.getString("lokasi_longitude", "0.0")?.toDoubleOrNull() ?: 0.0
        lokasiRadius = sharedPref.getInt("lokasi_radius", 100)
        
        // Log koordinat untuk debugging
        Log.d("HomeFragment", "====== DATA LOKASI ASLI DARI SHAREDPREF ======")
        Log.d("HomeFragment", "Lokasi Kerja: $lokasiKerja")
        Log.d("HomeFragment", "Latitude: $lokasiLatitude")
        Log.d("HomeFragment", "Longitude: $lokasiLongitude") 
        Log.d("HomeFragment", "Radius: $lokasiRadius meter")
        Log.d("HomeFragment", "========================================")
        
        // Update UI langsung
        binding.tvWelcome.text = "Halo, $nama!"
        binding.tvLokasi.text = "Lokasi: $lokasiKerja"
        binding.tvInfoShift.text = "$lokasiKerja"
        
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
        // Cek apakah aplikasi memiliki izin lokasi
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Jika belum ada izin, minta izin
            requestLocationPermissions()
            return
        }

        try {
            // Dapatkan lokasi terakhir
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    // Simpan lokasi saat ini
                    currentLatitude = location.latitude
                    currentLongitude = location.longitude
                    
                    // Log lokasi
                    Log.d("HomeFragment", "Lokasi GPS Saat Ini: Lat=$currentLatitude, Long=$currentLongitude")
                    
                    // Juga simpan ke SharedPreferences
                    val editor = requireActivity().getSharedPreferences("login_data", Context.MODE_PRIVATE).edit()
                    editor.putString("user_latitude", currentLatitude.toString())
                    editor.putString("user_longitude", currentLongitude.toString())
                    editor.apply()
                    
                    // Ambil lokasi kerja dari data yang sudah dimuat
                    val workLatitude = lokasiLatitude
                    val workLongitude = lokasiLongitude
                    val workRadius = lokasiRadius.toDouble()
                    
                    Log.d("HomeFragment", "Lokasi Kerja: Lat=$workLatitude, Long=$workLongitude, Radius=$workRadius")
                    
                    // Hitung jarak
                    val distance = calculateHaversineDistance(
                        currentLatitude, currentLongitude,
                        workLatitude, workLongitude
                    )
                    
                    // Update tampilan lokasi
                    updateLocationStatus(distance, workRadius)
                } else {
                    // Jika lokasi null, coba ambil dari SharedPreferences
                    val userLat = requireActivity().getSharedPreferences("login_data", Context.MODE_PRIVATE).getString("user_latitude", "0.0")?.toDoubleOrNull() ?: 0.0
                    val userLong = requireActivity().getSharedPreferences("login_data", Context.MODE_PRIVATE).getString("user_longitude", "0.0")?.toDoubleOrNull() ?: 0.0
                    
                    if (userLat != 0.0 && userLong != 0.0) {
                        currentLatitude = userLat
                        currentLongitude = userLong
                        
                        Log.d("HomeFragment", "Menggunakan lokasi dari SharedPreferences: Lat=$userLat, Long=$userLong")
                        
                        // Ambil lokasi kerja
                        val workLatitude = lokasiLatitude
                        val workLongitude = lokasiLongitude
                        val workRadius = lokasiRadius.toDouble()
                        
                        Log.d("HomeFragment", "Lokasi Kerja: Lat=$workLatitude, Long=$workLongitude, Radius=$workRadius")
                        
                        // Hitung jarak
                        val distance = calculateHaversineDistance(
                            currentLatitude, currentLongitude,
                            workLatitude, workLongitude
                        )
                        
                        // Update tampilan lokasi
                        updateLocationStatus(distance, workRadius)
                    } else {
                        // Lokasi tidak tersedia
                        binding.tvStatusLokasi.text = "Tidak dapat memperoleh lokasi"
                        binding.tvStatusLokasi.setTextColor(ContextCompat.getColor(requireContext(), R.color.red_text))
                        binding.cardLokasi.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.light_red))
                        
                        Log.e("HomeFragment", "Tidak dapat memperoleh lokasi GPS")
                        Toast.makeText(requireContext(), "Tidak dapat memperoleh lokasi. Aktifkan GPS Anda.", Toast.LENGTH_SHORT).show()
                    }
                }
            }.addOnFailureListener { e ->
                Log.e("HomeFragment", "Gagal mendapatkan lokasi: ${e.message}", e)
                binding.tvStatusLokasi.text = "Error: Gagal mendapatkan lokasi"
                binding.tvStatusLokasi.setTextColor(ContextCompat.getColor(requireContext(), R.color.red_text))
                binding.cardLokasi.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.light_red))
                Toast.makeText(requireContext(), "Error mendapatkan lokasi: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("HomeFragment", "Error dalam getCurrentLocation: ${e.message}", e)
            binding.tvStatusLokasi.text = "Error: ${e.message}"
            binding.tvStatusLokasi.setTextColor(ContextCompat.getColor(requireContext(), R.color.red_text))
            binding.cardLokasi.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.light_red))
        }
    }

    // Fungsi untuk update tampilan status lokasi
    private fun updateLocationStatus(distance: Double, radius: Double) {
        insideRadius = distance <= radius
        if (insideRadius) {
            binding.tvStatusLokasi.text = "Dalam Radius: ${distance.roundToInt()} meter"
            binding.tvStatusLokasi.setTextColor(ContextCompat.getColor(requireContext(), R.color.green_text))
            binding.cardLokasi.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.light_green))
        } else {
            binding.tvStatusLokasi.text = "Di Luar Radius: ${distance.roundToInt()} meter"
            binding.tvStatusLokasi.setTextColor(ContextCompat.getColor(requireContext(), R.color.red_text))
            binding.cardLokasi.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.light_red))
        }
        updateButtonStatus()
    }

    // Fungsi menghitung jarak Haversine
    private fun calculateHaversineDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        // Pastikan koordinat valid
        if (lat1 == 0.0 || lon1 == 0.0 || lat2 == 0.0 || lon2 == 0.0) {
            Log.e("HomeFragment", "Koordinat tidak valid: ($lat1, $lon1) atau ($lat2, $lon2)")
            return 99999.0
        }
        
        try {
            Log.d("HomeFragment", "Menghitung jarak antara ($lat1, $lon1) dan ($lat2, $lon2)")
            
            val earthRadius = 6371.0 // Radius bumi dalam kilometer
            
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            
            val a = sin(dLat / 2) * sin(dLat / 2) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                    sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            
            // Jarak dalam kilometer, dikonversi ke meter
            val distance = earthRadius * c * 1000
            
            Log.d("HomeFragment", "Jarak terhitung: $distance meter")
            return distance
        } catch (e: Exception) {
            Log.e("HomeFragment", "Error saat menghitung jarak: ${e.message}", e)
            return 99999.0
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
    
    private fun startLocationUpdates() {
        locationUpdateRunnable = object : Runnable {
            override fun run() {
                // Ambil lokasi terbaru
                getCurrentLocation()
                
                // Perbarui status dari server (data lokasi kerja bisa berubah)
                loadTodayStatus()
                
                // Jadwalkan pembaruan berikutnya
                handler.postDelayed(this, locationUpdateInterval)
                
                Log.d("HomeFragment", "Pembaruan lokasi dan status otomatis dijalankan")
            }
        }
        
        // Mulai pembaruan setelah delay awal
        handler.postDelayed(locationUpdateRunnable, locationUpdateInterval)
    }

    private fun loadTodayStatus() {
        // Tampilkan loading
        binding.tvStatusAbsensi.text = "Memuat status absensi..."

        val stringRequest = object : StringRequest(
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
                    
                    Log.d("HomeFragment", "Today Status Response: $response")
                    val jsonResponse = JSONObject(response)
                    val success = jsonResponse.getBoolean("success")
                    
                    if (success) {
                        val data = jsonResponse.getJSONObject("data")
                        
                        // Status
                        val status = data.getJSONObject("status")
                        isCheckIn = status.getBoolean("check_in")
                        isCheckOut = status.getBoolean("check_out")
                        statusAbsensi = status.getString("status_kehadiran")
                        
                        // PENTING: Ambil data lokasi kerja terbaru dari server
                        val lokasiKerjaData = data.getJSONObject("lokasi_kerja")
                        lokasiKerja = lokasiKerjaData.getString("nama")
                        lokasiLatitude = lokasiKerjaData.getDouble("latitude")
                        lokasiLongitude = lokasiKerjaData.getDouble("longitude")
                        lokasiRadius = lokasiKerjaData.getInt("radius")
                        
                        // Log data lokasi kerja terbaru
                        Log.d("HomeFragment", "Data lokasi kerja terbaru dari server:")
                        Log.d("HomeFragment", "Nama: $lokasiKerja")
                        Log.d("HomeFragment", "Latitude: $lokasiLatitude")
                        Log.d("HomeFragment", "Longitude: $lokasiLongitude")
                        Log.d("HomeFragment", "Radius: $lokasiRadius")
                        
                        // Perbarui UI dengan data lokasi kerja terbaru
                        binding.tvLokasi.text = "Lokasi: $lokasiKerja"
                        binding.tvInfoShift.text = "$lokasiKerja"
                        
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
                            hasJadwalToday = shift != "-" && shift != "L" && shift.isNotEmpty()
                        } else {
                            binding.tvShift.text = "Tidak ada jadwal"
                            hasJadwalToday = false
                        }
                        
                        // Absensi
                        val absensi = data.optJSONObject("absensi")
                        if (absensi != null) {
                            jamMasuk = absensi.optString("jam_masuk", null)
                            jamKeluar = absensi.optString("jam_keluar", null)
                            
                            if (!jamMasuk.isNullOrEmpty()) {
                                binding.tvJamMasuk.text = jamMasuk
                            } else {
                                binding.tvJamMasuk.text = "-"
                            }
                            
                            if (!jamKeluar.isNullOrEmpty()) {
                                binding.tvJamKeluar.text = jamKeluar
                            } else {
                                binding.tvJamKeluar.text = "-"
                            }
                            
                            // Status absensi
                            when (statusAbsensi) {
                                "hadir" -> {
                                    binding.tvStatusAbsensi.text = "Status: Hadir"
                                    binding.tvStatusAbsensi.setTextColor(ContextCompat.getColor(requireContext(), R.color.green_text))
                                }
                                "terlambat" -> {
                                    binding.tvStatusAbsensi.text = "Status: Terlambat"
                                    binding.tvStatusAbsensi.setTextColor(ContextCompat.getColor(requireContext(), R.color.orange_text))
                                }
                                else -> {
                                    binding.tvStatusAbsensi.text = "Status: Belum Absen"
                                    binding.tvStatusAbsensi.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_text))
                                }
                            }
                        } else {
                            binding.tvJamMasuk.text = "-"
                            binding.tvJamKeluar.text = "-"
                            binding.tvStatusAbsensi.text = "Status: Belum Absen"
                            binding.tvStatusAbsensi.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_text))
                        }
                        
                        // Update button status
                        updateButtonStatus()
                        
                        // Perbarui status lokasi karena data lokasi kerja bisa berubah
                        if (currentLatitude != 0.0 && currentLongitude != 0.0) {
                            val distance = calculateHaversineDistance(
                                currentLatitude, currentLongitude,
                                lokasiLatitude, lokasiLongitude
                            )
                            updateLocationStatus(distance, lokasiRadius.toDouble())
                        }
                    } else {
                        Toast.makeText(requireContext(), "Gagal memuat status: ${jsonResponse.getString("message")}", Toast.LENGTH_SHORT).show()
                        binding.tvStatusAbsensi.text = "Status: Gagal memuat data"
                        binding.tvJamMasuk.text = "-"
                        binding.tvJamKeluar.text = "-"
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
                    binding.tvStatusAbsensi.text = "Status: Error"
                    binding.tvJamMasuk.text = "-"
                    binding.tvJamKeluar.text = "-"
                }
            },
            Response.ErrorListener { error ->
                Log.e("HomeFragment", "Error loading status: ${error.message}", error)
                Toast.makeText(requireContext(), "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                binding.tvStatusAbsensi.text = "Status: Error jaringan"
                binding.tvJamMasuk.text = "-"
                binding.tvJamKeluar.text = "-"
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
        // Tombol check-in aktif jika belum check-in, dalam radius, dan ada jadwal
        val enableCheckIn = !isCheckIn && insideRadius && hasJadwalToday
        // Tombol check-out aktif HANYA jika sudah check-in, belum check-out, dalam radius, dan ada jadwal
        val enableCheckOut = isCheckIn && !isCheckOut && insideRadius && hasJadwalToday
        binding.btnCheckIn.isEnabled = enableCheckIn
        binding.btnCheckOut.isEnabled = enableCheckOut
        // Update warna tombol
        binding.btnCheckIn.alpha = if (enableCheckIn) 1.0f else 0.5f
        binding.btnCheckOut.alpha = if (enableCheckOut) 1.0f else 0.5f
    }

    private fun setupClickListeners() {
        // Tombol check-in
        binding.btnCheckIn.setOnClickListener {
            if (!insideRadius) {
                Toast.makeText(requireContext(), "Anda di luar radius lokasi kerja!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!hasJadwalToday) {
                Toast.makeText(requireContext(), "Tidak ada jadwal hari ini!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Ambil lokasi terbaru sebelum check-in
            getCurrentLocation()
            // Tampilkan loading dialog
            val loadingDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Memproses")
                .setMessage("Memproses Check-In...")
                .setCancelable(false)
                .create()
            loadingDialog.show()
            // Pura-pura loading sebentar untuk UX
            Handler(Looper.getMainLooper()).postDelayed({
                loadingDialog.dismiss()
                // Proses check-in
                proceedCheckIn()
            }, 1000)
        }
        // Tombol check-out
        binding.btnCheckOut.setOnClickListener {
            if (!insideRadius) {
                Toast.makeText(requireContext(), "Anda di luar radius lokasi kerja!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!hasJadwalToday) {
                Toast.makeText(requireContext(), "Tidak ada jadwal hari ini!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Ambil lokasi terbaru sebelum check-out
            getCurrentLocation()
            // Tampilkan loading dialog
            val loadingDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Memproses")
                .setMessage("Memproses Check-Out...")
                .setCancelable(false)
                .create()
            loadingDialog.show()
            // Pura-pura loading sebentar untuk UX
            Handler(Looper.getMainLooper()).postDelayed({
                loadingDialog.dismiss()
                // Proses check-out
                proceedCheckOut()
            }, 1000)
        }
    }

    private fun proceedCheckIn() {
        // Validasi lokasi
        if (currentLatitude == 0.0 || currentLongitude == 0.0) {
            Toast.makeText(requireContext(), "Lokasi tidak valid! Pastikan GPS aktif.", Toast.LENGTH_SHORT).show()
            getCurrentLocation() // Coba dapatkan lokasi lagi
            return
        }
        // Tampilkan dialog loading
        val loadingDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Memproses")
            .setMessage("Memproses check-in...")
            .setCancelable(false)
            .create()
        loadingDialog.show()
        // Log parameter yang dikirim
        Log.d("HomeFragment", "Check-in Request - satpam_id: $satpamId, lat: $currentLatitude, long: $currentLongitude")
        val stringRequest = object : StringRequest(
            Request.Method.POST, urlCheckIn,
            Response.Listener { response ->
                loadingDialog.dismiss()
                try {
                    // Cek apakah respons berisi HTML error
                    if (response.contains("<br") || response.contains("<b>Fatal error</b>") ||
                        response.contains("Uncaught") || response.contains("Stack trace")) {
                        Log.e("HomeFragment", "Server mengembalikan HTML error: "+response.take(500))
                        Toast.makeText(requireContext(), "Error server: Hubungi administrator", Toast.LENGTH_SHORT).show()
                        return@Listener
                    }
                    Log.d("HomeFragment", "Check-in Response: $response")
                    val jsonResponse = JSONObject(response)
                    val success = jsonResponse.getBoolean("success")
                    if (success) {
                        // Check-in berhasil
                        Toast.makeText(requireContext(), "Check-in berhasil!", Toast.LENGTH_SHORT).show()
                        // Update UI
                        isCheckIn = true
                        updateButtonStatus() // Langsung enable tombol check-out
                        // Ambil data waktu check-in dari response
                        val data = jsonResponse.getJSONObject("data")
                        jamMasuk = data.getString("jam_masuk")
                        // Update tampilan jam masuk
                        binding.tvJamMasuk.text = jamMasuk
                        // Update status absensi jika ada
                        if (data.has("status")) {
                            statusAbsensi = data.getString("status")
                            when (statusAbsensi) {
                                "hadir" -> {
                                    binding.tvStatusAbsensi.text = "Status: Hadir"
                                    binding.tvStatusAbsensi.setTextColor(ContextCompat.getColor(requireContext(), R.color.green_text))
                                }
                                "terlambat" -> {
                                    binding.tvStatusAbsensi.text = "Status: Terlambat"
                                    binding.tvStatusAbsensi.setTextColor(ContextCompat.getColor(requireContext(), R.color.orange_text))
                                }
                            }
                        }
                        // Reload status untuk memastikan data terbaru
                        Handler(Looper.getMainLooper()).postDelayed({
                            loadTodayStatus()
                        }, 1000)
                    } else {
                        // Check-in gagal
                        val message = jsonResponse.getString("message")
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                        // Log pesan error
                        Log.e("HomeFragment", "Check-in gagal: $message")
                        // Jika ada data tambahan, tampilkan untuk debugging
                        if (jsonResponse.has("data")) {
                            val data = jsonResponse.getJSONObject("data")
                            Log.d("HomeFragment", "Distance data: ${data.toString(2)}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("HomeFragment", "Error parsing check-in response: ${e.message}", e)
                    Log.e("HomeFragment", "Raw response: $response")
                    Toast.makeText(requireContext(), "Terjadi kesalahan: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            },
            Response.ErrorListener { error ->
                loadingDialog.dismiss()
                Log.e("HomeFragment", "Error check-in request: ${error.message}", error)
                val errorMessage = when (error) {
                    is TimeoutError -> "Timeout: Server tidak merespon"
                    is NoConnectionError -> "Tidak ada koneksi internet"
                    is AuthFailureError -> "Autentikasi gagal"
                    is ServerError -> "Error server (${error.networkResponse?.statusCode ?: "Unknown"})"
                    is NetworkError -> "Koneksi jaringan bermasalah"
                    is ParseError -> "Gagal parsing data"
                    else -> "Error: ${error.message}"
                }
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
            }) {
            override fun getParams(): Map<String, String> {
                val params = HashMap<String, String>()
                params["satpam_id"] = satpamId.toString()
                params["latitude"] = currentLatitude.toString()
                params["longitude"] = currentLongitude.toString()
                params["keterangan"] = ""
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

    private fun proceedCheckOut() {
        // Validasi lokasi
        if (currentLatitude == 0.0 || currentLongitude == 0.0) {
            Toast.makeText(requireContext(), "Lokasi tidak valid! Pastikan GPS aktif.", Toast.LENGTH_SHORT).show()
            getCurrentLocation() // Coba dapatkan lokasi lagi
            return
        }
        
        // Tampilkan dialog loading
        val loadingDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Memproses")
            .setMessage("Memproses check-out...")
            .setCancelable(false)
            .create()
        loadingDialog.show()
        
        // Log parameter yang dikirim
        Log.d("HomeFragment", "Check-out Request - satpam_id: $satpamId, lat: $currentLatitude, long: $currentLongitude")
        
        val stringRequest = object : StringRequest(
            Request.Method.POST, urlCheckOut,
            Response.Listener { response ->
                loadingDialog.dismiss()
                
                try {
                    // Cek apakah respons berisi HTML error
                    if (response.contains("<br") || response.contains("<b>Fatal error</b>") ||
                        response.contains("Uncaught") || response.contains("Stack trace")) {
                        Log.e("HomeFragment", "Server mengembalikan HTML error: ${response.take(500)}")
                        Toast.makeText(requireContext(), "Error server: Hubungi administrator", Toast.LENGTH_SHORT).show()
                        return@Listener
                    }
                    
                    Log.d("HomeFragment", "Check-out Response: $response")
                    val jsonResponse = JSONObject(response)
                    val success = jsonResponse.getBoolean("success")
                    
                    if (success) {
                        // Check-out berhasil
                        Toast.makeText(requireContext(), "Check-out berhasil!", Toast.LENGTH_SHORT).show()
                        
                        // Update UI
                        isCheckOut = true
                        updateButtonStatus()
                        
                        // Ambil data waktu check-out dari response
                        val data = jsonResponse.getJSONObject("data")
                        jamKeluar = data.getString("jam_keluar")
                        
                        // Update tampilan jam keluar
                        binding.tvJamKeluar.text = jamKeluar
                        
                        // Reload status untuk memastikan data terbaru
                        Handler(Looper.getMainLooper()).postDelayed({
                            loadTodayStatus()
                        }, 1000)
                    } else {
                        // Check-out gagal
                        val message = jsonResponse.getString("message")
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                        
                        // Log pesan error
                        Log.e("HomeFragment", "Check-out gagal: $message")
                        
                        // Jika ada data tambahan, tampilkan untuk debugging
                        if (jsonResponse.has("data")) {
                            val data = jsonResponse.getJSONObject("data")
                            Log.d("HomeFragment", "Distance data: ${data.toString(2)}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("HomeFragment", "Error parsing check-out response: ${e.message}", e)
                    Log.e("HomeFragment", "Raw response: $response")
                    Toast.makeText(requireContext(), "Terjadi kesalahan: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            },
            Response.ErrorListener { error ->
                loadingDialog.dismiss()
                Log.e("HomeFragment", "Error check-out request: ${error.message}", error)
                
                val errorMessage = when (error) {
                    is TimeoutError -> "Timeout: Server tidak merespon"
                    is NoConnectionError -> "Tidak ada koneksi internet"
                    is AuthFailureError -> "Autentikasi gagal"
                    is ServerError -> "Error server (${error.networkResponse?.statusCode ?: "Unknown"})"
                    is NetworkError -> "Koneksi jaringan bermasalah"
                    is ParseError -> "Gagal parsing data"
                    else -> "Error: ${error.message}"
                }
                
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
            }) {
            override fun getParams(): Map<String, String> {
                val params = HashMap<String, String>()
                params["satpam_id"] = satpamId.toString()
                params["latitude"] = currentLatitude.toString()
                params["longitude"] = currentLongitude.toString()
                params["keterangan"] = ""
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

    override fun onDestroyView() {
        super.onDestroyView()
        // Hentikan semua timer
        handler.removeCallbacks(updateTimeRunnable)
        handler.removeCallbacks(locationUpdateRunnable)
        _binding = null
    }
}