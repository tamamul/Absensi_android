package com.android.absensi.ui.schedule

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.GridView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.android.absensi.R
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ScheduleFragment : Fragment() {

    private lateinit var scheduleViewModel: ScheduleViewModel
    private var _binding: com.android.absensi.databinding.FragmentScheduleBinding? = null
    private val binding get() = _binding!!

    private var satpamId: Int = 0
    private var bulan: Int = 0
    private var tahun: Int = 0
    
    private lateinit var scheduleAdapter: ScheduleAdapter
    private val scheduleList = ArrayList<ScheduleItem>()
    
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
        scheduleViewModel = ViewModelProvider(this)[ScheduleViewModel::class.java]
        _binding = com.android.absensi.databinding.FragmentScheduleBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Inisialisasi data user
        loadUserData()

        // Inisialisasi tahun dan bulan saat ini
        val calendar = Calendar.getInstance()
        bulan = calendar.get(Calendar.MONTH) + 1 // Calendar.MONTH dimulai dari 0
        tahun = calendar.get(Calendar.YEAR)

        // Setup spinner bulan dan tahun
        setupSpinners()

        // Setup adapter jadwal
        scheduleAdapter = ScheduleAdapter(requireContext(), scheduleList)
        binding.gridSchedule.adapter = scheduleAdapter

        // Load jadwal
        loadSchedule()

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
                loadSchedule()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.spinnerYear.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                tahun = yearsList[position]
                loadSchedule()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadSchedule() {
        val url = getString(R.string.ip_api) + "get_jadwal.php"
        
        val stringRequest = object : StringRequest(
            Request.Method.POST, url,
            Response.Listener { response ->
                try {
                    val jsonResponse = JSONObject(response)
                    if (jsonResponse.getBoolean("success")) {
                        val data = jsonResponse.getJSONObject("data")
                        val jadwal = data.getJSONArray("jadwal")
                        val shiftInfo = data.getJSONObject("shift_info")
                        
                        // Clear previous data
                        scheduleList.clear()
                        
                        // Fill schedule list
                        for (i in 0 until jadwal.length()) {
                            val item = jadwal.getJSONObject(i)
                            val tanggal = item.getString("tanggal")
                            val shift = item.getString("shift")
                            val keterangan = item.getString("keterangan")
                            
                            // Parse tanggal
                            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            val date = dateFormat.parse(tanggal)
                            val dayFormat = SimpleDateFormat("d", Locale.getDefault())
                            val day = dayFormat.format(date ?: Date())
                            
                            // Get shift information
                            val shiftTime = shiftInfo.optString(shift, "-")
                            
                            scheduleList.add(
                                ScheduleItem(
                                    day.toInt(),
                                    shift,
                                    shiftTime,
                                    keterangan
                                )
                            )
                        }
                        
                        // Notify adapter
                        scheduleAdapter.notifyDataSetChanged()
                    } else {
                        Toast.makeText(requireContext(), "Gagal memuat jadwal", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("ScheduleFragment", "Error parsing response", e)
                    Toast.makeText(requireContext(), "Terjadi kesalahan: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            },
            Response.ErrorListener { error ->
                Log.e("ScheduleFragment", "Error loading schedule", error)
                Toast.makeText(requireContext(), "Error: ${error.message}", Toast.LENGTH_SHORT).show()
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

// Data class untuk item jadwal
data class ScheduleItem(
    val day: Int,
    val shift: String,
    val shiftTime: String,
    val keterangan: String
)

// Adapter untuk grid jadwal
class ScheduleAdapter(private val context: Context, private val items: List<ScheduleItem>) : 
    ArrayAdapter<ScheduleItem>(context, 0, items) {
    
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var itemView = convertView
        if (itemView == null) {
            itemView = LayoutInflater.from(context).inflate(R.layout.item_schedule, parent, false)
        }
        
        val item = items[position]
        
        val tvDate = itemView?.findViewById<TextView>(R.id.tvDate)
        val tvShift = itemView?.findViewById<TextView>(R.id.tvShift)
        val tvShiftTime = itemView?.findViewById<TextView>(R.id.tvShiftTime)
        
        tvDate?.text = item.day.toString()
        tvShift?.text = "Shift ${item.shift}"
        tvShiftTime?.text = item.shiftTime
        
        // Set background color berdasarkan shift
        val backgroundColor = when (item.shift) {
            "P" -> R.color.shift_pagi
            "S" -> R.color.shift_siang
            "M" -> R.color.shift_malam
            "L" -> R.color.shift_libur
            else -> R.color.shift_libur
        }
        
        itemView?.setBackgroundResource(backgroundColor)
        
        return itemView!!
    }
} 