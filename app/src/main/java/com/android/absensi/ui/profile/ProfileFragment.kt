package com.android.absensi.ui.profile

import android.app.Activity
import android.content.Context
import android.content.Intent
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
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.bumptech.glide.Glide
import org.json.JSONObject
import java.io.File

class ProfileFragment : Fragment() {

    private lateinit var profileViewModel: ProfileViewModel
    private var _binding: com.android.absensi.databinding.FragmentProfileBinding? = null
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
    
    // Image picker launcher
    private val pickImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
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
        _binding = com.android.absensi.databinding.FragmentProfileBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Load user data
        loadUserData()

        // Setup profile image
        setupProfileImage()

        // Setup buttons
        setupButtons()

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
            val imageUrl = getString(R.string.ip_api).replace("api_android/", "") + "uploads/profile/" + foto
            Glide.with(this)
                .load(imageUrl)
                .placeholder(R.drawable.profile_placeholder)
                .error(R.drawable.profile_placeholder)
                .into(binding.ivProfile)
        } else {
            binding.ivProfile.setImageResource(R.drawable.profile_placeholder)
        }
    }

    private fun setupButtons() {
        // Setup save button
        binding.btnSave.setOnClickListener {
            saveProfileData()
        }

        // Setup logout button
        binding.btnLogout.setOnClickListener {
            logout()
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
                        
                        // Set data to UI
                        binding.etNik.setText(data.getString("nik"))
                        binding.etNip.setText(data.getString("nip"))
                        binding.etNama.setText(data.getString("nama"))
                        binding.tvJabatan.text = "Satpam"
                        binding.tvLokasi.text = lokasiKerja
                        
                        // Optional fields
                        noHp = data.optString("no_hp", "")
                        email = data.optString("email", "")
                        alamat = data.optString("alamat", "")
                        
                        if (noHp.isNotEmpty()) binding.etNoHp.setText(noHp)
                        if (email.isNotEmpty()) binding.etEmail.setText(email)
                        if (alamat.isNotEmpty()) binding.etAlamat.setText(alamat)
                        
                        // Load profile image if exists
                        foto = data.optString("foto", "")
                        if (foto.isNotEmpty()) {
                            val imageUrl = getString(R.string.ip_api).replace("api_android/", "") + "uploads/profile/" + foto
                            Glide.with(this)
                                .load(imageUrl)
                                .placeholder(R.drawable.profile_placeholder)
                                .error(R.drawable.profile_placeholder)
                                .into(binding.ivProfile)
                        }
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
        
        binding.progressBar.visibility = View.VISIBLE
        binding.btnSave.isEnabled = false
        
        val url = getString(R.string.ip_api) + "update_profile.php"
        
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
                        // Update SharedPreferences
                        val sharedPref = requireActivity().getSharedPreferences("login_data", Context.MODE_PRIVATE)
                        with(sharedPref.edit()) {
                            putString("nama", newNama)
                            apply()
                        }
                        
                        // Reset save button visibility
                        binding.btnSave.visibility = View.GONE
                        
                        // Refresh profile data
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
}