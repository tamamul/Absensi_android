package com.android.absensi

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {
    private lateinit var etNik: EditText
    private lateinit var etNip: EditText
    private lateinit var btnLogin: Button
    private lateinit var tvRegister: TextView

    // Ganti URL sesuai dengan alamat server Anda
//    private val URL = getString(R.string.ip_api) +"login.php"
    private val URL by lazy { getString(R.string.ip_api) + "login.php" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Sembunyikan action bar
        supportActionBar?.hide()

        // Cek apakah sudah login
        checkLoginStatus()

        // Inisialisasi views
        etNik = findViewById(R.id.etNik)
        etNip = findViewById(R.id.etNip)
        btnLogin = findViewById(R.id.btnLogin)
        tvRegister = findViewById(R.id.tvRegister)

        // Handle login button click
        btnLogin.setOnClickListener {
            loginUser()
        }

        // Handle register text click
        tvRegister.setOnClickListener {
            val intent = Intent(this@LoginActivity, RegisterActivity::class.java)
            startActivity(intent)
        }
    }

    private fun checkLoginStatus() {
        val sharedPref = getSharedPreferences("login_data", MODE_PRIVATE)
        val isLoggedIn = sharedPref.getBoolean("is_logged_in", false)
        
        if (isLoggedIn) {
            // Redirect ke MainActivity
            val intent = Intent(this@LoginActivity, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun loginUser() {
        val nik = etNik.text.toString().trim()
        val nip = etNip.text.toString().trim()

        // Validasi input
        if (nik.isEmpty() || nip.isEmpty()) {
            Toast.makeText(this, "Mohon isi NIK dan NIP", Toast.LENGTH_SHORT).show()
            return
        }

        // Membuat request ke server
        val stringRequest = object : StringRequest(
            Request.Method.POST, URL,
            Response.Listener { response ->
                try {
                    val jsonResponse = JSONObject(response)
                    val success = jsonResponse.getBoolean("success")
                    val message = jsonResponse.getString("message")

                    if (success) {
                        // Ambil data user
                        val userData = jsonResponse.getJSONObject("data")
                        val id = userData.getInt("id")
                        val nama = userData.getString("nama")
                        val nik = userData.getString("nik")
                        val nip = userData.getString("nip")
                        val jabatan = userData.getString("jabatan")
                        val foto = userData.optString("foto", "")
                        
                        // Ambil data lokasi kerja
                        val lokasiData = userData.getJSONObject("lokasikerja")
                        val lokasiId = lokasiData.getInt("id")
                        val lokasiNama = lokasiData.getString("nama")
                        val lokasiLatitude = lokasiData.getDouble("latitude")
                        val lokasiLongitude = lokasiData.getDouble("longitude")
                        val lokasiRadius = lokasiData.getInt("radius")

                        // Log data lokasi untuk debugging
                        Log.d("LoginActivity", "====== KOORDINAT LOKASI KERJA ======")
                        Log.d("LoginActivity", "Lokasi: $lokasiNama")
                        Log.d("LoginActivity", "Latitude: $lokasiLatitude")
                        Log.d("LoginActivity", "Longitude: $lokasiLongitude") 
                        Log.d("LoginActivity", "Radius: $lokasiRadius meter")
                        Log.d("LoginActivity", "====================================")

                        // Simpan data login ke SharedPreferences
                        val sharedPref = getSharedPreferences("login_data", MODE_PRIVATE)
                        with(sharedPref.edit()) {
                            putInt("id", id)
                            putString("nama", nama)
                            putString("nik", nik)
                            putString("nip", nip)
                            putString("jabatan", jabatan)
                            putString("foto", foto)
                            
                            // Lokasi kerja
                            putInt("lokasi_id", lokasiId)
                            putString("lokasi_nama", lokasiNama)
                            putString("lokasi_latitude", lokasiLatitude.toString())
                            putString("lokasi_longitude", lokasiLongitude.toString())
                            putInt("lokasi_radius", lokasiRadius)
                            
                            putBoolean("is_logged_in", true)
                            apply()
                        }

                        // Redirect ke MainActivity
                        val intent = Intent(this@LoginActivity, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()

                        Toast.makeText(this, "Selamat datang, $nama", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Terjadi kesalahan: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e("Login", "Error parsing: ${e.message}")
                    Log.e("Login", "Response: $response")
                }
            },
            Response.ErrorListener { error ->
                Toast.makeText(this, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                Log.e("Login", "Error network: ${error.message}")
            }) {
            override fun getParams(): Map<String, String> {
                val params = HashMap<String, String>()
                params["nik"] = nik
                params["nip"] = nip
                return params
            }
        }
        
        // Menambahkan request ke RequestQueue
        Volley.newRequestQueue(this).add(stringRequest)
    }
    
}