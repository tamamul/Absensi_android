package com.android.absensi

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject

class RegisterActivity : AppCompatActivity() {
    private lateinit var etNik: EditText
    private lateinit var etNip: EditText
    private lateinit var etNama: EditText
    private lateinit var spinnerLokasi: Spinner
    private lateinit var btnRegister: Button
    private lateinit var tvLogin: TextView
    
    private val locationList = ArrayList<LocationData>()
    private var selectedLocationId = 0

    private val URL_REGISTER by lazy { getString(R.string.ip_api) + "register.php" }
    private val URL_GET_LOCATIONS by lazy { getString(R.string.ip_api) + "get_lokasi.php" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Sembunyikan action bar
        supportActionBar?.hide()

        // Inisialisasi views
        etNik = findViewById(R.id.etNik)
        etNip = findViewById(R.id.etNip)
        etNama = findViewById(R.id.etNama)
        spinnerLokasi = findViewById(R.id.spinnerLokasi)
        btnRegister = findViewById(R.id.btnRegister)
        tvLogin = findViewById(R.id.tvLogin)

        // Ambil data lokasi
        getLocationData()

        btnRegister.setOnClickListener {
            registerUser()
        }

        tvLogin.setOnClickListener {
            val intent = Intent(this@RegisterActivity, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun getLocationData() {
        val stringRequest = StringRequest(
            Request.Method.GET, URL_GET_LOCATIONS,
            { response ->
                try {
                    val jsonResponse = JSONObject(response)
                    val success = jsonResponse.getBoolean("success")
                    
                    if (success) {
                        val data = jsonResponse.getJSONArray("data")
                        locationList.clear()
                        
                        // Tambahkan item default
                        locationList.add(LocationData(0, "-- Pilih Lokasi Kerja --", "", "", 0.0, 0.0, 0))
                        
                        // Tambahkan lokasi dari API
                        for (i in 0 until data.length()) {
                            val location = data.getJSONObject(i)
                            val id = location.getInt("id")
                            val name = location.getString("nama")
                            val ultg = location.getString("ultg")
                            val upt = location.getString("upt")
                            val latitude = location.getDouble("latitude")
                            val longitude = location.getDouble("longitude")
                            val radius = location.getInt("radius")
                            
                            locationList.add(LocationData(id, name, ultg, upt, latitude, longitude, radius))
                        }
                        
                        // Setup spinner adapter
                        val locationNames = locationList.map { "${it.name} - ${it.ultg}" }
                        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, locationNames)
                        spinnerLokasi.adapter = adapter
                        
                        // Setup spinner listener
                        spinnerLokasi.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                                selectedLocationId = locationList[position].id
                            }
                            
                            override fun onNothingSelected(parent: AdapterView<*>?) {
                                selectedLocationId = 0
                            }
                        }
                    } else {
                        Toast.makeText(this, "Gagal memuat data lokasi", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("LocationError", "Error parsing JSON: ${e.message}")
                    Toast.makeText(this, "Terjadi kesalahan sistem", Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                Log.e("LocationError", "Volley Error: ${error.message}")
                Toast.makeText(this, "Gagal terhubung ke server", Toast.LENGTH_SHORT).show()
            }
        )
        
        Volley.newRequestQueue(this).add(stringRequest)
    }

    private fun registerUser() {
        val nik = etNik.text.toString().trim()
        val nip = etNip.text.toString().trim()
        val nama = etNama.text.toString().trim()

        // Validasi input
        if (nik.isEmpty() || nip.isEmpty() || nama.isEmpty()) {
            Toast.makeText(this, "Mohon isi semua field", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (selectedLocationId == 0) {
            Toast.makeText(this, "Mohon pilih lokasi kerja", Toast.LENGTH_SHORT).show()
            return
        }

        // Membuat request ke server
        val stringRequest = object : StringRequest(
            Request.Method.POST, URL_REGISTER,
            Response.Listener { response ->
                try {
                    // Log response untuk debugging
                    Log.d("RegisterResponse", response)

                    val jsonResponse = JSONObject(response)
                    val success = jsonResponse.getBoolean("success")
                    val message = jsonResponse.getString("message")

                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

                    if (success) {
                        // Redirect ke login
                        val intent = Intent(this@RegisterActivity, LoginActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                } catch (e: Exception) {
                    Log.e("RegisterError", "Error parsing JSON: ${e.message}")
                    Toast.makeText(this, "Terjadi kesalahan sistem", Toast.LENGTH_SHORT).show()
                }
            },
            Response.ErrorListener { error ->
                Log.e("RegisterError", "Volley Error: ${error.message}")
                Toast.makeText(this, "Gagal terhubung ke server", Toast.LENGTH_SHORT).show()
            }) {
            override fun getParams(): Map<String, String> {
                val params = HashMap<String, String>()
                params["nik"] = nik
                params["nip"] = nip
                params["nama"] = nama
                params["lokasikerja_id"] = selectedLocationId.toString()
                return params
            }
        }

        // Set timeout yang lebih lama
        stringRequest.retryPolicy = DefaultRetryPolicy(
            30000, // 30 detik timeout
            0, // no retry
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )

        // Menambahkan request ke RequestQueue
        Volley.newRequestQueue(this).add(stringRequest)
    }
    
    // Data class untuk lokasi
    data class LocationData(
        val id: Int,
        val name: String,
        val ultg: String,
        val upt: String,
        val latitude: Double,
        val longitude: Double,
        val radius: Int
    )
}