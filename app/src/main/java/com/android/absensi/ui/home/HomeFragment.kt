package com.android.absensi.ui.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.android.absensi.R
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
    private var distanceToLocation: Float = 0f
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
        nama = sharedPref.getString("nama", "") ?: ""
        nik = sharedPref.getString("nik", "") ?: ""
        jabatan = sharedPref.getString("jabatan", "") ?: ""
        lokasiKerja = sharedPref.getString("lokasi_nama", "") ?: ""
        lokasiLatitude = sharedPref.getString("lokasi_latitude", "0.0")?.toDoubleOrNull() ?: 0.0
        lokasiLongitude = sharedPref.getString("lokasi_longitude", "0.0")?.toDoubleOrNull() ?: 0.0
        lokasiRadius = sharedPref.getInt("lokasi_radius", 0)
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
                requestPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
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
            return
        }

        val cancellationToken = CancellationTokenSource()
        
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationToken.token)
            .addOnSuccessListener { location ->
                location?.let {
                    currentLatitude = it.latitude
                    currentLongitude = it.longitude
                    
                    // Hitung jarak ke lokasi kerja
                    val results = FloatArray(1)
                    android.location.Location.distanceBetween(
                        currentLatitude, currentLongitude,
                        lokasiLatitude, lokasiLongitude,
                        results
                    )
                    
                    distanceToLocation = results[0]
                    insideRadius = distanceToLocation <= lokasiRadius
                    
                    // Update status lokasi
                    if (insideRadius) {
                        binding.tvStatusLokasi.text = getString(R.string.status_in_radius, distanceToLocation.toInt().toString())
                        binding.tvStatusLokasi.setTextColor(resources.getColor(R.color.green_text, null))
                    } else {
                        binding.tvStatusLokasi.text = getString(R.string.status_out_radius, distanceToLocation.toInt().toString())
                        binding.tvStatusLokasi.setTextColor(resources.getColor(R.color.red_text, null))
                    }
                    
                    // Update status button
                    updateButtonStatus()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Gagal mendapatkan lokasi: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("HomeFragment", "Error getting location", e)
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
        val stringRequest = object : StringRequest(
            Request.Method.POST, urlTodayStatus,
            Response.Listener { response ->
                try {
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
                    Toast.makeText(requireContext(), "Terjadi kesalahan: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e("HomeFragment", "Error parsing status response", e)
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
        
        Volley.newRequestQueue(requireContext()).add(stringRequest)
    }

    private fun updateButtonStatus() {
        // Check-in button
        binding.btnCheckIn.isEnabled = !isCheckIn && insideRadius
        
        // Check-out button
        binding.btnCheckOut.isEnabled = isCheckIn && !isCheckOut && insideRadius
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
        
        // Cek apakah dalam radius
        if (!insideRadius) {
            Toast.makeText(requireContext(), "Anda berada di luar area kerja", Toast.LENGTH_SHORT).show()
            return
        }
        
        val stringRequest = object : StringRequest(
            Request.Method.POST, urlCheckIn,
            Response.Listener { response ->
                try {
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
                    Toast.makeText(requireContext(), "Terjadi kesalahan: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e("HomeFragment", "Error parsing check-in response", e)
                }
            },
            Response.ErrorListener { error ->
                Toast.makeText(requireContext(), "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                Log.e("HomeFragment", "Error check-in", error)
            }) {
            override fun getParams(): Map<String, String> {
                val params = HashMap<String, String>()
                params["satpam_id"] = satpamId.toString()
                params["latitude"] = currentLatitude.toString()
                params["longitude"] = currentLongitude.toString()
                return params
            }
        }
        
        Volley.newRequestQueue(requireContext()).add(stringRequest)
    }

    private fun doCheckOut() {
        // Refresh lokasi terlebih dahulu
        getCurrentLocation()
        
        // Cek apakah dalam radius
        if (!insideRadius) {
            Toast.makeText(requireContext(), "Anda berada di luar area kerja", Toast.LENGTH_SHORT).show()
            return
        }
        
        val stringRequest = object : StringRequest(
            Request.Method.POST, urlCheckOut,
            Response.Listener { response ->
                try {
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
                    Toast.makeText(requireContext(), "Terjadi kesalahan: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e("HomeFragment", "Error parsing check-out response", e)
                }
            },
            Response.ErrorListener { error ->
                Toast.makeText(requireContext(), "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                Log.e("HomeFragment", "Error check-out", error)
            }) {
            override fun getParams(): Map<String, String> {
                val params = HashMap<String, String>()
                params["satpam_id"] = satpamId.toString()
                params["latitude"] = currentLatitude.toString()
                params["longitude"] = currentLongitude.toString()
                return params
            }
        }
        
        Volley.newRequestQueue(requireContext()).add(stringRequest)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(updateTimeRunnable)
        _binding = null
    }
}