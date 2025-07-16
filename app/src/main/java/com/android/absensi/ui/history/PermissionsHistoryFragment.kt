package com.android.absensi.ui.history

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.absensi.R
import com.android.absensi.databinding.FragmentPermissionsHistoryBinding // Pastikan Anda membuat binding untuk layout ini
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONArray
import java.util.Calendar

class PermissionsHistoryFragment : Fragment() {

    private var _binding: FragmentPermissionsHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var permissionsAdapter: PermissionsAdapter
    private var satpamId: Int = 0
    private var bulan: Int = 0
    private var tahun: Int = 0

    private val monthNames = arrayOf(
        "Januari", "Februari", "Maret", "April", "Mei", "Juni",
        "Juli", "Agustus", "September", "Oktober", "November", "Desember"
    )
    private val yearsList = ArrayList<Int>()

    // Definisikan TAG untuk logging agar mudah difilter di Logcat
    private val TAG = "PermissionsHistory"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPermissionsHistoryBinding.inflate(inflater, container, false)
        Log.d(TAG, "onCreateView: Fragment view sedang dibuat.")

        // Inisialisasi data user dan periode
        loadUserData()
        val calendar = Calendar.getInstance()
        bulan = calendar.get(Calendar.MONTH) + 1
        tahun = calendar.get(Calendar.YEAR)
        Log.d(TAG, "onCreateView: Periode default diatur ke Bulan: $bulan, Tahun: $tahun")

        // Setup UI
        setupSpinners()
        setupRecyclerView()

        // Muat data awal
        loadPermissionsHistory()

        return binding.root
    }

    private fun loadUserData() {
        val sharedPref = requireActivity().getSharedPreferences("login_data", Context.MODE_PRIVATE)
        satpamId = sharedPref.getInt("id", 0)
        Log.d(TAG, "loadUserData: Berhasil memuat satpamId: $satpamId")
    }

    private fun setupRecyclerView() {
        permissionsAdapter = PermissionsAdapter(emptyList())
        binding.rvPermissions.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = permissionsAdapter
        }
        Log.d(TAG, "setupRecyclerView: RecyclerView dan Adapter berhasil diinisialisasi.")
    }

    private fun setupSpinners() {
        // Setup tahun
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        yearsList.add(currentYear - 1)
        yearsList.add(currentYear)
        yearsList.add(currentYear + 1)
        val yearAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, yearsList)
        yearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerYear.adapter = yearAdapter
        binding.spinnerYear.setSelection(yearsList.indexOf(tahun))

        // Setup bulan
        val monthAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, monthNames)
        monthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerMonth.adapter = monthAdapter
        binding.spinnerMonth.setSelection(bulan - 1)

        // Listener untuk memuat ulang data saat filter diubah
        val listener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                bulan = binding.spinnerMonth.selectedItemPosition + 1
                tahun = binding.spinnerYear.selectedItem as Int
                Log.i(TAG, "onItemSelected: Filter diubah. Periode baru -> Bulan: $bulan, Tahun: $tahun. Memuat ulang data...")
                loadPermissionsHistory()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        binding.spinnerMonth.onItemSelectedListener = listener
        binding.spinnerYear.onItemSelectedListener = listener
        Log.d(TAG, "setupSpinners: Spinner berhasil diatur dengan listener.")
    }

    private fun loadPermissionsHistory() {
        Log.d(TAG, "loadPermissionsHistory: Memulai proses memuat data...")
        binding.progressBar.visibility = View.VISIBLE
        binding.rvPermissions.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE

        val url = getString(R.string.ip_api) + "get_permissions.php"

        val stringRequest = object : StringRequest(
            Request.Method.POST, url,
            Response.Listener { response ->
                Log.d(TAG, "onResponse: Respons dari server: $response")
                binding.progressBar.visibility = View.GONE
                try {
                    val jsonResponse = org.json.JSONObject(response)
                    if (jsonResponse.getBoolean("success")) {
                        val dataArray: JSONArray = jsonResponse.getJSONArray("data")
                        val permissionsList = ArrayList<PermissionItem>()
                        Log.d(TAG, "onResponse: Sukses. Ditemukan ${dataArray.length()} item.")

                        for (i in 0 until dataArray.length()) {
                            val itemObj = dataArray.getJSONObject(i)
                            permissionsList.add(
                                PermissionItem(
                                    id = itemObj.getInt("id"),
                                    jenisPengajuan = itemObj.getString("jenis_pengajuan"),
                                    tanggalPengajuan = itemObj.getString("tanggal_pengajuan"),
                                    tanggalMulai = itemObj.getString("tanggal_mulai"),
                                    tanggalSelesai = itemObj.getString("tanggal_selesai"),
                                    alasan = itemObj.getString("alasan"),
                                    status = itemObj.getString("status"),
                                    buktiFoto = itemObj.getString("bukti_foto"),
                                    catatanAdmin = itemObj.getString("catatan_admin")
                                )
                            )
                        }

                        if (permissionsList.isEmpty()) {
                            Log.d(TAG, "onResponse: Daftar kosong, menampilkan layout kosong.")
                            binding.layoutEmpty.visibility = View.VISIBLE
                        } else {
                            Log.d(TAG, "onResponse: Daftar berisi data, memperbarui adapter.")
                            binding.rvPermissions.visibility = View.VISIBLE
                            permissionsAdapter.setData(permissionsList)
                        }

                    } else {
                        val message = jsonResponse.getString("message")
                        Log.w(TAG, "onResponse: Permintaan gagal dengan pesan: $message")
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        binding.layoutEmpty.visibility = View.VISIBLE
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "onResponse: Error saat parsing JSON: ${e.message}", e)
                    Toast.makeText(context, "Gagal memproses data dari server.", Toast.LENGTH_SHORT).show()
                    binding.layoutEmpty.visibility = View.VISIBLE
                }
            },
            Response.ErrorListener { error ->
                binding.progressBar.visibility = View.GONE
                binding.layoutEmpty.visibility = View.VISIBLE
                Log.e(TAG, "onErrorResponse: Volley error: ${error.message}", error)
                Toast.makeText(context, "Gagal terhubung ke server.", Toast.LENGTH_SHORT).show()
            }) {
            override fun getParams(): Map<String, String> {
                val params = mapOf(
                    "satpam_id" to satpamId.toString(),
                    "bulan" to bulan.toString(),
                    "tahun" to tahun.toString()
                )
                Log.d(TAG, "getParams: Mengirim parameter: $params ke URL: $url")
                return params
            }
        }
        Volley.newRequestQueue(requireContext()).add(stringRequest)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView: Fragment view dihancurkan.")
        _binding = null
    }
}
