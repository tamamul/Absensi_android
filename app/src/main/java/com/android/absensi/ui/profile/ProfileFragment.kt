package com.android.absensi.ui.profile

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.android.absensi.LoginActivity
import com.android.absensi.R
import com.android.absensi.databinding.FragmentProfileBinding
import com.android.volley.DefaultRetryPolicy
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.HttpHeaderParser
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.bumptech.glide.Glide
import org.json.JSONObject
import java.io.*

class ProfileFragment : Fragment() {

    private lateinit var profileViewModel: ProfileViewModel
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    // Data user
    private var satpamId: Int = 0
    private var nik: String = ""
    private var nip: String = ""
    private var nama: String = ""
    private var lokasiKerja: String = ""
    private var jabatan: String = "Satpam"
    private var noHp: String = ""
    private var email: String = ""
    private var alamat: String = ""
    private var foto: String = ""

    // Selected image
    private var selectedImageUri: Uri? = null
    private var selectedImagePath: String? = null
    
    // Image picker launcher
    private val pickImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                selectedImagePath = getRealPathFromURI(uri)
                binding.ivProfile.setImageURI(uri)
                binding.btnSave.visibility = View.VISIBLE
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        profileViewModel = ViewModelProvider(this)[ProfileViewModel::class.java]
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Load user data
        loadUserData()

        // Setup profile image
        setupProfileImage()

        // Setup buttons
        setupClickListeners()

        // Setup field change listeners untuk menampilkan tombol save
        setupFieldChangeListeners()

        // Load profile data from API
        loadProfileData()

        return root
    }

    private fun loadUserData() {
        val sharedPref = requireActivity().getSharedPreferences("login_data", Context.MODE_PRIVATE)
        satpamId = sharedPref.getInt("id", 0)
        nik = sharedPref.getString("nik", "") ?: ""
        nip = sharedPref.getString("nip", "") ?: ""
        nama = sharedPref.getString("nama", "") ?: ""
        jabatan = sharedPref.getString("jabatan", "Satpam") ?: "Satpam"
        lokasiKerja = sharedPref.getString("lokasi_nama", "") ?: ""
        foto = sharedPref.getString("foto", "") ?: ""
    }

    private fun setupProfileImage() {
        // Setup click on profile image
        binding.ivProfile.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pickImage.launch(intent)
        }

        // Load profile image if exists
        if (foto.isNotEmpty()) {
            val imageUrl = getString(R.string.ip_api).replace("api_android/", "") + "uploads/" + foto
            Glide.with(this)
                .load(imageUrl)
                .placeholder(R.drawable.profile_placeholder)
                .error(R.drawable.profile_placeholder)
                .into(binding.ivProfile)
        } else {
            binding.ivProfile.setImageResource(R.drawable.profile_placeholder)
        }
    }

    private fun setupClickListeners() {
        // Setup save button
        binding.btnSave.setOnClickListener {
            saveProfileData()
        }

        // Setup logout button
        binding.btnLogout.setOnClickListener {
            logout()
        }
    }

    private fun setupFieldChangeListeners() {
        // Tambahkan text change listener untuk semua field yang bisa diedit
        binding.etNama.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && binding.etNama.text.toString() != nama) {
                binding.btnSave.visibility = View.VISIBLE
            }
        }
        
        binding.etNoHp.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && binding.etNoHp.text.toString() != noHp) {
                binding.btnSave.visibility = View.VISIBLE
            }
        }
        
        binding.etEmail.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && binding.etEmail.text.toString() != email) {
                binding.btnSave.visibility = View.VISIBLE
            }
        }
        
        binding.etAlamat.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && binding.etAlamat.text.toString() != alamat) {
                binding.btnSave.visibility = View.VISIBLE
            }
        }
    }

    private fun loadProfileData() {
        binding.progressBar.visibility = View.VISIBLE
        
        val url = getString(R.string.ip_api) + "get_profile.php"
        
        val stringRequest = object : StringRequest(
            Request.Method.POST, url,
            Response.Listener { response ->
                binding.progressBar.visibility = View.GONE
                
                try {
                    val jsonResponse = JSONObject(response)
                    if (jsonResponse.getBoolean("success")) {
                        val data = jsonResponse.getJSONObject("data")
                        
                        // Simpan data profil
                        nik = data.getString("nik")
                        nip = data.getString("nip")
                        nama = data.getString("nama")
                        jabatan = data.optString("jabatan", "Satpam")
                        
                        // Data lokasi
                        val lokasiData = data.getJSONObject("lokasikerja")
                        lokasiKerja = lokasiData.getString("nama")
                        
                        // Optional fields
                        noHp = data.optString("no_hp", "")
                        email = data.optString("email", "")
                        alamat = data.optString("alamat", "")
                        foto = data.optString("foto", "")
                        
                        // Set data ke UI
                        binding.etNik.setText(nik)
                        binding.etNip.setText(nip)
                        binding.etNama.setText(nama)
                        binding.tvJabatan.text = jabatan
                        binding.tvLokasi.text = lokasiKerja
                        
                        // Set field dengan "Belum diisi" jika kosong
                        binding.etNoHp.setText(if (noHp.isNotEmpty()) noHp else "Belum diisi")
                        binding.etEmail.setText(if (email.isNotEmpty()) email else "Belum diisi")
                        binding.etAlamat.setText(if (alamat.isNotEmpty()) alamat else "Belum diisi")
                        
                        // Load foto profil jika ada
                        if (foto.isNotEmpty()) {
                            val imageUrl = getString(R.string.ip_api).replace("api_android/", "") + "uploads/" + foto
                            Glide.with(this)
                                .load(imageUrl)
                                .placeholder(R.drawable.profile_placeholder)
                                .error(R.drawable.profile_placeholder)
                                .into(binding.ivProfile)
                        }
                        
                        // Sembunyikan tombol save
                        binding.btnSave.visibility = View.GONE
                    } else {
                        Toast.makeText(requireContext(), "Gagal memuat profil: ${jsonResponse.getString("message")}", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("ProfileFragment", "Error parsing response", e)
                    Toast.makeText(requireContext(), "Terjadi kesalahan: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            },
            Response.ErrorListener { error ->
                binding.progressBar.visibility = View.GONE
                Log.e("ProfileFragment", "Error loading profile", error)
                Toast.makeText(requireContext(), "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }) {
            override fun getParams(): Map<String, String> {
                val params = HashMap<String, String>()
                params["satpam_id"] = satpamId.toString()
                return params
            }
        }
        
        Volley.newRequestQueue(requireContext()).add(stringRequest)
    }

    private fun saveProfileData() {
        val newNama = binding.etNama.text.toString().trim()
        val newNoHp = binding.etNoHp.text.toString().trim()
        val newEmail = binding.etEmail.text.toString().trim()
        val newAlamat = binding.etAlamat.text.toString().trim()
        
        // Validasi input
        if (newNama.isEmpty()) {
            Toast.makeText(requireContext(), "Nama tidak boleh kosong", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Jangan kirim "Belum diisi" ke server
        val finalNoHp = if (newNoHp == "Belum diisi") "" else newNoHp
        val finalEmail = if (newEmail == "Belum diisi") "" else newEmail
        val finalAlamat = if (newAlamat == "Belum diisi") "" else newAlamat
        
        binding.progressBar.visibility = View.VISIBLE
        binding.btnSave.isEnabled = false
        
        // Jika ada foto yang dipilih, gunakan MultipartRequest
        if (selectedImagePath != null) {
            uploadProfileWithImage(newNama, finalNoHp, finalEmail, finalAlamat)
        } else {
            // Tidak ada foto, gunakan StringRequest biasa
            uploadProfileWithoutImage(newNama, finalNoHp, finalEmail, finalAlamat)
        }
    }
    
    private fun uploadProfileWithImage(newNama: String, newNoHp: String, newEmail: String, newAlamat: String) {
        val url = getString(R.string.ip_api) + "edit_profile.php"
        
        try {
            val imageData = selectedImagePath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    val inputStream = FileInputStream(file)
                    val byteArrayOutputStream = ByteArrayOutputStream()
                    val buffer = ByteArray(1024)
                    var len: Int
                    while (inputStream.read(buffer).also { len = it } != -1) {
                        byteArrayOutputStream.write(buffer, 0, len)
                    }
                    byteArrayOutputStream.toByteArray()
                } else null
            }
            
            val request = object : VolleyMultipartRequest(
                url,
                { response ->
                    binding.progressBar.visibility = View.GONE
                    binding.btnSave.isEnabled = true
                    
                    try {
                        val jsonResponse = JSONObject(String(response.data, Charsets.UTF_8))
                        val success = jsonResponse.getBoolean("success")
                        val message = jsonResponse.getString("message")
                        
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                        
                        if (success) {
                            // Update nilai lokal
                            nama = newNama
                            noHp = newNoHp
                            email = newEmail
                            alamat = newAlamat
                            
                            // Reset foto yang dipilih
                            selectedImageUri = null
                            selectedImagePath = null
                            
                            // Update SharedPreferences
                            val sharedPref = requireActivity().getSharedPreferences("login_data", Context.MODE_PRIVATE)
                            with(sharedPref.edit()) {
                                putString("nama", newNama)
                                if (jsonResponse.has("foto")) {
                                    putString("foto", jsonResponse.getString("foto"))
                                }
                                apply()
                            }
                            
                            // Reset save button visibility
                            binding.btnSave.visibility = View.GONE
                            
                            // Reload profile data
                            loadProfileData()
                        }
                    } catch (e: Exception) {
                        Log.e("ProfileFragment", "Error parsing response", e)
                        Toast.makeText(requireContext(), "Terjadi kesalahan: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                },
                { error ->
                    binding.progressBar.visibility = View.GONE
                    binding.btnSave.isEnabled = true
                    Log.e("ProfileFragment", "Error updating profile", error)
                    Toast.makeText(requireContext(), "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            ) {
                override fun getParams(): Map<String, String> {
                    val params = HashMap<String, String>()
                    params["satpam_id"] = satpamId.toString()
                    params["nama"] = newNama
                    params["no_hp"] = newNoHp
                    params["email"] = newEmail
                    params["alamat"] = newAlamat
                    return params
                }
                
                override fun getByteData(): Map<String, DataPart>? {
                    val params = HashMap<String, DataPart>()
                    imageData?.let {
                        params["foto"] = DataPart("profile_${System.currentTimeMillis()}.jpg", it, "image/jpeg")
                    }
                    return params
                }
            }
            
            // Set timeout lebih lama untuk upload file
            request.retryPolicy = DefaultRetryPolicy(
                60000, // 60 detik timeout
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
            )
            
            Volley.newRequestQueue(requireContext()).add(request)
            
        } catch (e: Exception) {
            binding.progressBar.visibility = View.GONE
            binding.btnSave.isEnabled = true
            Log.e("ProfileFragment", "Error preparing image", e)
            Toast.makeText(requireContext(), "Gagal memproses foto: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun uploadProfileWithoutImage(newNama: String, newNoHp: String, newEmail: String, newAlamat: String) {
        val url = getString(R.string.ip_api) + "edit_profile.php"
        
        val stringRequest = object : StringRequest(
            Request.Method.POST, url,
            Response.Listener { response ->
                binding.progressBar.visibility = View.GONE
                binding.btnSave.isEnabled = true
                
                try {
                    val jsonResponse = JSONObject(response)
                    val success = jsonResponse.getBoolean("success")
                    val message = jsonResponse.getString("message")
                    
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    
                    if (success) {
                        // Update nilai lokal
                        nama = newNama
                        noHp = newNoHp
                        email = newEmail
                        alamat = newAlamat
                        
                        // Update SharedPreferences
                        val sharedPref = requireActivity().getSharedPreferences("login_data", Context.MODE_PRIVATE)
                        with(sharedPref.edit()) {
                            putString("nama", newNama)
                            apply()
                        }
                        
                        // Reset save button visibility
                        binding.btnSave.visibility = View.GONE
                        
                        // Reload data untuk menampilkan perubahan
                        loadProfileData()
                    }
                } catch (e: Exception) {
                    Log.e("ProfileFragment", "Error parsing update response", e)
                    Toast.makeText(requireContext(), "Terjadi kesalahan: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            },
            Response.ErrorListener { error ->
                binding.progressBar.visibility = View.GONE
                binding.btnSave.isEnabled = true
                Log.e("ProfileFragment", "Error updating profile", error)
                Toast.makeText(requireContext(), "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }) {
            override fun getParams(): Map<String, String> {
                val params = HashMap<String, String>()
                params["satpam_id"] = satpamId.toString()
                params["nama"] = newNama
                params["no_hp"] = newNoHp
                params["email"] = newEmail
                params["alamat"] = newAlamat
                return params
            }
        }
        
        Volley.newRequestQueue(requireContext()).add(stringRequest)
    }

    private fun getRealPathFromURI(uri: Uri): String? {
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        var path: String? = null
        try {
            val cursor: Cursor? = requireActivity().contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val columnIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                    path = it.getString(columnIndex)
                }
            }
        } catch (e: Exception) {
            Log.e("ProfileFragment", "Error getting file path", e)
        }
        return path
    }

    private fun logout() {
        // Clear SharedPreferences
        val sharedPref = requireActivity().getSharedPreferences("login_data", Context.MODE_PRIVATE)
        sharedPref.edit().clear().apply()
        
        // Redirect to login
        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    // Class untuk multipart request tanpa extend kelas final
    open class VolleyMultipartRequest(
        private val url: String,
        private val listener: Response.Listener<MultipartResponse>,
        private val errorListener: Response.ErrorListener
    ) : com.android.volley.Request<MultipartResponse>(Method.POST, url, errorListener) {
        
        companion object {
            private const val BOUNDARY = "----WebKitFormBoundary7MA4YWxkTrZu0gW"
            private const val LINE_FEED = "\r\n"
        }
        
        override fun getHeaders(): Map<String, String> {
            val headers = HashMap<String, String>()
            headers["Accept"] = "application/json"
            return headers
        }
        
        override fun getBodyContentType(): String {
            return "multipart/form-data; boundary=$BOUNDARY"
        }
        
        override fun getBody(): ByteArray {
            val byteArrayOutputStream = ByteArrayOutputStream()
            val dataOutputStream = DataOutputStream(byteArrayOutputStream)
            
            try {
                // Add string params
                val params = getParams()
                if (params != null && params.isNotEmpty()) {
                    for ((key, value) in params) {
                        dataOutputStream.writeBytes("--$BOUNDARY$LINE_FEED")
                        dataOutputStream.writeBytes("Content-Disposition: form-data; name=\"$key\"$LINE_FEED")
                        dataOutputStream.writeBytes(LINE_FEED)
                        dataOutputStream.writeBytes(value)
                        dataOutputStream.writeBytes(LINE_FEED)
                    }
                }
                
                // Add file data
                val fileData = getByteData()
                if (fileData != null && fileData.isNotEmpty()) {
                    for ((key, filePart) in fileData) {
                        dataOutputStream.writeBytes("--$BOUNDARY$LINE_FEED")
                        dataOutputStream.writeBytes("Content-Disposition: form-data; name=\"$key\"; filename=\"${filePart.fileName}\"$LINE_FEED")
                        if (filePart.type.isNotEmpty()) {
                            dataOutputStream.writeBytes("Content-Type: ${filePart.type}$LINE_FEED")
                        }
                        dataOutputStream.writeBytes(LINE_FEED)
                        
                        dataOutputStream.write(filePart.data)
                        
                        dataOutputStream.writeBytes(LINE_FEED)
                    }
                }
                
                // Close multipart form data
                dataOutputStream.writeBytes("--$BOUNDARY--$LINE_FEED")
                
                return byteArrayOutputStream.toByteArray()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            
            return ByteArray(0)
        }
        
        override fun parseNetworkResponse(response: NetworkResponse): Response<MultipartResponse> {
            return Response.success(
                MultipartResponse(response.data, response.statusCode),
                HttpHeaderParser.parseCacheHeaders(response)
            )
        }
        
        override fun deliverResponse(response: MultipartResponse) {
            listener.onResponse(response)
        }
        
        open fun getByteData(): Map<String, DataPart>? = null
    }
    
    data class MultipartResponse(
        val data: ByteArray,
        val statusCode: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            
            other as MultipartResponse
            
            if (!data.contentEquals(other.data)) return false
            if (statusCode != other.statusCode) return false
            
            return true
        }
        
        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + statusCode
            return result
        }
    }
    
    class DataPart(
        val fileName: String,
        val data: ByteArray,
        val type: String = ""
    )
}
