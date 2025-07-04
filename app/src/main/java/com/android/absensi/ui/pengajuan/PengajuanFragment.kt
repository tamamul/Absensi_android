package com.android.absensi.ui.pengajuan

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.absensi.R
import com.android.absensi.databinding.FragmentPengajuanBinding
import com.android.absensi.databinding.ItemPengajuanBinding
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.bumptech.glide.Glide
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import okhttp3.OkHttpClient
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request as OkHttpRequest
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response as OkHttpResponse
import java.io.IOException

class PengajuanFragment : Fragment() {

    private var _binding: FragmentPengajuanBinding? = null
    private val binding get() = _binding!!
    
    private var satpamId: Int = 0
    private var selectedImageUri: Uri? = null
    private val PICK_IMAGE_REQUEST = 1
    
    private val jenisPengajuan = arrayOf("Izin", "Sakit", "Cuti", "Pulang Cepat")
    private lateinit var pengajuanAdapter: PengajuanAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPengajuanBinding.inflate(inflater, container, false)
        
        // Load user data
        loadUserData()
        
        // Setup spinner jenis pengajuan
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, jenisPengajuan)
        binding.spinnerJenisPengajuan.setAdapter(adapter)
        
        // Setup date picker
        binding.edtTanggalMulai.setOnClickListener { showDatePicker(true) }
        binding.edtTanggalSelesai.setOnClickListener { showDatePicker(false) }
        
        // Setup image picker
        binding.btnUpload.setOnClickListener { pickImage() }
        
        // Setup submit button
        binding.btnSubmit.setOnClickListener { submitPengajuan() }
        
        // Setup recycler view
        binding.rvRiwayatPengajuan.layoutManager = LinearLayoutManager(context)
        pengajuanAdapter = PengajuanAdapter()
        binding.rvRiwayatPengajuan.adapter = pengajuanAdapter
        
        // Load riwayat pengajuan
        loadRiwayatPengajuan()
        
        return binding.root
    }

    private fun loadUserData() {
        val sharedPref = requireActivity().getSharedPreferences("login_data", Context.MODE_PRIVATE)
        satpamId = sharedPref.getInt("id", 0)
    }

    private fun showDatePicker(isStartDate: Boolean) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                val date = Calendar.getInstance()
                date.set(year, month, day)
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val formattedDate = dateFormat.format(date.time)
                if (isStartDate) {
                    binding.edtTanggalMulai.setText(formattedDate)
                } else {
                    binding.edtTanggalSelesai.setText(formattedDate)
                }
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            selectedImageUri = data.data
            binding.imgBukti.setImageURI(selectedImageUri)
        }
    }

    private fun submitPengajuan() {
        val jenis = binding.spinnerJenisPengajuan.text.toString()
        val tanggalMulai = binding.edtTanggalMulai.text.toString()
        val tanggalSelesai = binding.edtTanggalSelesai.text.toString()
        val alasan = binding.edtAlasan.text.toString()

        if (jenis.isEmpty() || tanggalMulai.isEmpty() || tanggalSelesai.isEmpty() || alasan.isEmpty()) {
            Toast.makeText(context, "Semua field harus diisi", Toast.LENGTH_SHORT).show()
            return
        }

        // Convert jenis to enum value
        val jenisPengajuanValue = when(jenis) {
            "Izin" -> "izin"
            "Sakit" -> "sakit"
            "Cuti" -> "cuti"
            "Pulang Cepat" -> "pulang_cepat"
            else -> "izin"
        }

        val client = OkHttpClient()
        val url = getString(R.string.ip_api) + "submit_pengajuan.php"

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("satpam_id", satpamId.toString())
            .addFormDataPart("jenis_pengajuan", jenisPengajuanValue)
            .addFormDataPart("tanggal_mulai", tanggalMulai)
            .addFormDataPart("tanggal_selesai", tanggalSelesai)
            .addFormDataPart("alasan", alasan)

        // Add image if selected
        selectedImageUri?.let { uri ->
            val file = File(getRealPathFromURI(uri))
            val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
            requestBody.addFormDataPart("bukti_foto", file.name, requestFile)
        }

        val request = OkHttpRequest.Builder()
            .url(url)
            .post(requestBody.build())
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: OkHttpResponse) {
                val responseData = response.body?.string()
                activity?.runOnUiThread {
                    try {
                        val jsonObject = JSONObject(responseData)
                        if (jsonObject.getBoolean("success")) {
                            Toast.makeText(context, "Pengajuan berhasil dikirim", Toast.LENGTH_SHORT).show()
                            clearForm()
                            loadRiwayatPengajuan()
                        } else {
                            Toast.makeText(context, "Gagal mengirim pengajuan", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun getRealPathFromURI(uri: Uri): String {
        val cursor = requireActivity().contentResolver.query(uri, null, null, null, null)
        cursor?.moveToFirst()
        val idx = cursor?.getColumnIndex(MediaStore.Images.ImageColumns.DATA)
        val result = cursor?.getString(idx ?: 0) ?: ""
        cursor?.close()
        return result
    }

    private fun clearForm() {
        binding.spinnerJenisPengajuan.setText("")
        binding.edtTanggalMulai.setText("")
        binding.edtTanggalSelesai.setText("")
        binding.edtAlasan.setText("")
        binding.imgBukti.setImageResource(R.drawable.ic_add_photo)
        selectedImageUri = null
    }

    private fun loadRiwayatPengajuan() {
        val url = getString(R.string.ip_api) + "get_riwayat_pengajuan.php"
        
        val stringRequest = object : StringRequest(
            Request.Method.POST, url,
            Response.Listener { response ->
                try {
                    val jsonObject = JSONObject(response)
                    if (jsonObject.getBoolean("success")) {
                        val pengajuanArray = jsonObject.getJSONArray("data")
                        val pengajuanList = mutableListOf<PengajuanItem>()
                        
                        for (i in 0 until pengajuanArray.length()) {
                            val item = pengajuanArray.getJSONObject(i)
                            pengajuanList.add(
                                PengajuanItem(
                                    id = item.getInt("id"),
                                    jenisPengajuan = item.getString("jenis_pengajuan"),
                                    tanggalMulai = item.getString("tanggal_mulai"),
                                    tanggalSelesai = item.getString("tanggal_selesai"),
                                    alasan = item.getString("alasan"),
                                    status = item.getString("status"),
                                    buktiFoto = item.getString("bukti_foto"),
                                    catatanAdmin = item.getString("catatan_admin")
                                )
                            )
                        }
                        
                        pengajuanAdapter.updateData(pengajuanList)
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            },
            Response.ErrorListener { error ->
                Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }) {
            override fun getParams(): Map<String, String> {
                return hashMapOf("satpam_id" to satpamId.toString())
            }
        }
        
        Volley.newRequestQueue(context).add(stringRequest)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

data class PengajuanItem(
    val id: Int,
    val jenisPengajuan: String,
    val tanggalMulai: String,
    val tanggalSelesai: String,
    val alasan: String,
    val status: String,
    val buktiFoto: String,
    val catatanAdmin: String
)

class PengajuanAdapter : RecyclerView.Adapter<PengajuanAdapter.ViewHolder>() {
    private var items = mutableListOf<PengajuanItem>()

    fun updateData(newItems: List<PengajuanItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPengajuanBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    class ViewHolder(private val binding: ItemPengajuanBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: PengajuanItem) {
            // Format jenis pengajuan
            binding.tvJenisPengajuan.text = when(item.jenisPengajuan) {
                "izin" -> "Izin"
                "sakit" -> "Sakit"
                "cuti" -> "Cuti"
                "pulang_cepat" -> "Pulang Cepat"
                else -> item.jenisPengajuan
            }

            // Format tanggal
            val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale("id"))
            val startDate = SimpleDateFormat("yyyy-MM-dd").parse(item.tanggalMulai)
            val endDate = SimpleDateFormat("yyyy-MM-dd").parse(item.tanggalSelesai)
            binding.tvTanggal.text = "${dateFormat.format(startDate)} - ${dateFormat.format(endDate)}"

            // Set alasan
            binding.tvAlasan.text = item.alasan

            // Set status dengan warna yang sesuai
            binding.tvStatus.text = when(item.status) {
                "pending" -> "Pending"
                "disetujui" -> "Disetujui"
                "ditolak" -> "Ditolak"
                else -> item.status
            }
            binding.tvStatus.setBackgroundResource(when(item.status) {
                "pending" -> R.drawable.bg_status_pending
                "disetujui" -> R.drawable.bg_status_approved
                "ditolak" -> R.drawable.bg_status_rejected
                else -> R.drawable.bg_status_pending
            })

            // Set catatan admin jika ada
            if (item.catatanAdmin.isNotEmpty()) {
                binding.layoutCatatan.visibility = View.VISIBLE
                binding.tvCatatan.text = "Catatan: ${item.catatanAdmin}"
            } else {
                binding.layoutCatatan.visibility = View.GONE
            }

            // Load bukti foto jika ada
            if (item.buktiFoto.isNotEmpty()) {
                binding.imgBukti.visibility = View.VISIBLE
                // Ambil filename saja dari URL lengkap
                val fileName = item.buktiFoto.substringAfterLast("/")
                val baseUrl = binding.root.context.getString(R.string.ip_api).replace("api_android/", "")
                val imageUrl = "${baseUrl}uploads_bukti_pengajuan/$fileName"
                
                Glide.with(binding.root.context)
                    .load(imageUrl)
                    .into(binding.imgBukti)
            } else {
                binding.imgBukti.visibility = View.GONE
            }
        }
    }
} 