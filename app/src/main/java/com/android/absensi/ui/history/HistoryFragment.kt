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
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.absensi.R
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HistoryFragment : Fragment() {

    private lateinit var historyViewModel: HistoryViewModel
    private var _binding: com.android.absensi.databinding.FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private var satpamId: Int = 0
    private var bulan: Int = 0
    private var tahun: Int = 0
    
    private lateinit var historyAdapter: HistoryAdapter
    
    private val monthNames = arrayOf(
        "Januari", "Februari", "Maret", "April", "Mei", "Juni", 
        "Juli", "Agustus", "September", "Oktober", "November", "Desember"
    )
    
    private val yearsList = ArrayList<Int>()
    private val monthsList = ArrayList<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        historyViewModel = ViewModelProvider(this)[HistoryViewModel::class.java]
        _binding = com.android.absensi.databinding.FragmentHistoryBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Inisialisasi data user
        loadUserData()

        // Inisialisasi tahun dan bulan saat ini
        val calendar = Calendar.getInstance()
        bulan = calendar.get(Calendar.MONTH) + 1
        tahun = calendar.get(Calendar.YEAR)

        // Setup spinner bulan dan tahun
        setupSpinners()

        // Setup adapter riwayat
        historyAdapter = HistoryAdapter()
        binding.rvHistory.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = historyAdapter
        }

        // Load riwayat
        loadHistory()

        return root
    }

    private fun loadUserData() {
        val sharedPref = requireActivity().getSharedPreferences("login_data", Context.MODE_PRIVATE)
        satpamId = sharedPref.getInt("id", 0)
    }

    private fun setupSpinners() {
        // Buat daftar tahun (tahun sekarang, tahun depan, tahun lalu)
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        yearsList.add(currentYear - 1)
        yearsList.add(currentYear)
        yearsList.add(currentYear + 1)

        // Setup spinner tahun
        val yearAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, yearsList)
        yearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerYear.adapter = yearAdapter
        binding.spinnerYear.setSelection(yearsList.indexOf(tahun))

        // Setup bulan
        monthsList.addAll(monthNames)
        val monthAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, monthsList)
        monthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerMonth.adapter = monthAdapter
        binding.spinnerMonth.setSelection(bulan - 1)

        // Set listener spinner
        binding.spinnerMonth.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                bulan = position + 1
                loadHistory()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.spinnerYear.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                tahun = yearsList[position]
                loadHistory()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadHistory() {
        binding.progressBar.visibility = View.VISIBLE
        
        val url = getString(R.string.ip_api) + "get_absensi.php"
        
        val stringRequest = object : StringRequest(
            Request.Method.POST, url,
            Response.Listener { response ->
                binding.progressBar.visibility = View.GONE
                
                try {
                    val jsonResponse = JSONObject(response)
                    if (jsonResponse.getBoolean("success")) {
                        val data = jsonResponse.getJSONObject("data")
                        val absensi = data.getJSONArray("absensi")
                        val shiftInfo = data.getJSONObject("shift_info")
                        
                        val historyItems = ArrayList<HistoryItem>()
                        
                        // Group absensi by tanggal and shift
                        val absensiMap = mutableMapOf<String, JSONObject>()
                        
                        // Proses data absensi dan simpan data terbaru untuk setiap tanggal dan shift
                        for (i in 0 until absensi.length()) {
                            val item = absensi.getJSONObject(i)
                            val tanggal = item.getString("tanggal")
                            val shift = item.getString("shift")
                            val key = "$tanggal-$shift"
                            
                            // Ambil data yang sudah ada (jika ada)
                            val existingItem = absensiMap[key]
                            
                            if (existingItem == null) {
                                // Jika belum ada data untuk kombinasi tanggal-shift ini
                                absensiMap[key] = item
                            } else {
                                // Jika sudah ada, bandingkan timestamp untuk mengambil yang terbaru
                                val existingTimestamp = existingItem.optString("created_at", "")
                                val newTimestamp = item.optString("created_at", "")
                                
                                if (newTimestamp > existingTimestamp) {
                                    absensiMap[key] = item
                                }
                            }
                        }
                        
                        // Konversi map ke list history items
                        absensiMap.values.forEach { item ->
                            val tanggal = item.getString("tanggal")
                            val shift = item.getString("shift")
                            val id = item.getInt("id")
                            
                            // Format tanggal
                            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            val outputFormat = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID"))
                            val date = inputFormat.parse(tanggal)
                            val formattedDate = date?.let { outputFormat.format(it) } ?: tanggal
                            
                            // Get shift time information
                            val shiftTime = shiftInfo.optString(shift, "")
                            
                            // Buat shift label berdasarkan kode shift
                            val shiftLabel = when(shift) {
                                "P" -> "Shift Pagi"
                                "S" -> "Shift Siang"
                                "M" -> "Shift Malam"
                                "L" -> "Libur"
                                else -> "Shift: -"
                            }
                            
                            // Ambil jam masuk dan keluar
                            val jamMasuk = item.optString("jam_masuk", "-")
                            val jamKeluar = item.optString("jam_keluar", "-")
                            val status = item.getString("status")
                            
                            // Log untuk debugging
                            Log.d("HistoryFragment", "Processing item: tanggal=$tanggal, shift=$shift")
                            Log.d("HistoryFragment", "Jam masuk=$jamMasuk, jam keluar=$jamKeluar")
                            
                            historyItems.add(
                                HistoryItem(
                                    id,
                                    formattedDate,
                                    jamMasuk,
                                    jamKeluar,
                                    status,
                                    shift,
                                    "$shiftLabel ($shiftTime)"
                                )
                            )
                        }
                        
                        // Sort by date and shift priority
                        historyItems.sortWith(compareByDescending<HistoryItem> { 
                            val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID"))
                            dateFormat.parse(it.tanggal)
                        }.thenBy {
                            when(it.shift) {
                                "P" -> 1
                                "S" -> 2
                                "M" -> 3
                                "L" -> 4
                                else -> 5
                            }
                        })
                        
                        // Update adapter
                        historyAdapter.setData(historyItems)
                        
                        // Show/hide empty state
                        if (historyItems.isEmpty()) {
                            binding.layoutEmpty.visibility = View.VISIBLE
                            binding.rvHistory.visibility = View.GONE
                        } else {
                            binding.layoutEmpty.visibility = View.GONE
                            binding.rvHistory.visibility = View.VISIBLE
                        }
                    } else {
                        Toast.makeText(requireContext(), "Gagal memuat riwayat: ${jsonResponse.getString("message")}", Toast.LENGTH_SHORT).show()
                        binding.layoutEmpty.visibility = View.VISIBLE
                        binding.rvHistory.visibility = View.GONE
                    }
                } catch (e: Exception) {
                    Log.e("HistoryFragment", "Error parsing response", e)
                    Toast.makeText(requireContext(), "Terjadi kesalahan: ${e.message}", Toast.LENGTH_SHORT).show()
                    binding.layoutEmpty.visibility = View.VISIBLE
                    binding.rvHistory.visibility = View.GONE
                }
            },
            Response.ErrorListener { error ->
                binding.progressBar.visibility = View.GONE
                Log.e("HistoryFragment", "Error loading history", error)
                Toast.makeText(requireContext(), "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                binding.layoutEmpty.visibility = View.VISIBLE
                binding.rvHistory.visibility = View.GONE
            }) {
            override fun getParams(): Map<String, String> {
                val params = HashMap<String, String>()
                params["satpam_id"] = satpamId.toString()
                params["bulan"] = bulan.toString()
                params["tahun"] = tahun.toString()
                return params
            }
        }
        
        Volley.newRequestQueue(requireContext()).add(stringRequest)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}