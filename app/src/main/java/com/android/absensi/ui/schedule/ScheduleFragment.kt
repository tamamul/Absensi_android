package com.android.absensi.ui.schedule

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.android.absensi.R
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.google.android.material.button.MaterialButtonToggleGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.ImageView

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

    private lateinit var recyclerViewAdapter: ScheduleListAdapter

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

        // Setup adapter jadwal (grid)
        scheduleAdapter = ScheduleAdapter(requireContext(), scheduleList)
        binding.gridSchedule.adapter = scheduleAdapter

        // Setup adapter jadwal (list)
        recyclerViewAdapter = ScheduleListAdapter(scheduleList)
        binding.recyclerViewSchedule.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewSchedule.adapter = recyclerViewAdapter

        // Toggle mode
        binding.toggleMode.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                if (checkedId == R.id.btnModeCalendar) {
                    binding.gridSchedule.visibility = View.VISIBLE
                    binding.recyclerViewSchedule.visibility = View.GONE
                } else if (checkedId == R.id.btnModeList) {
                    binding.gridSchedule.visibility = View.GONE
                    binding.recyclerViewSchedule.visibility = View.VISIBLE
                }
            }
        }
        // Set default mode
        binding.toggleMode.check(R.id.btnModeCalendar)

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
                        
                        // Temporary map untuk mengelompokkan shift berdasarkan tanggal
                        val tempScheduleMap = mutableMapOf<String, MutableList<Pair<String, String>>>()
                        
                        // Group shifts by date
                        for (i in 0 until jadwal.length()) {
                            val item = jadwal.getJSONObject(i)
                            val tanggal = item.getString("tanggal")
                            val shift = item.getString("shift")
                            val shiftTime = shiftInfo.optString(shift, "-")
                            
                            if (!tempScheduleMap.containsKey(tanggal)) {
                                tempScheduleMap[tanggal] = mutableListOf()
                            }
                            tempScheduleMap[tanggal]?.add(Pair(shift, shiftTime))
                        }
                        
                        // Convert grouped data to schedule items
                        tempScheduleMap.forEach { (tanggal, shifts) ->
                            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            val date = dateFormat.parse(tanggal)
                            val dayFormat = SimpleDateFormat("d", Locale.getDefault())
                            val day = dayFormat.format(date ?: Date())
                            
                            // Sort shifts by priority (P -> S -> M -> L)
                            val sortedShifts = shifts.sortedBy { 
                                when(it.first) {
                                    "P" -> 1
                                    "S" -> 2
                                    "M" -> 3
                                    "L" -> 4
                                    else -> 5
                                }
                            }
                            
                            // Combine shift information
                            val combinedShifts = sortedShifts.joinToString("\n") { 
                                "Shift ${it.first} (${it.second})" 
                            }
                            
                            scheduleList.add(
                                ScheduleItem(
                                    day.toInt(),
                                    sortedShifts.map { it.first }.joinToString(","),
                                    combinedShifts,
                                    ""
                                )
                            )
                        }
                        
                        // Sort by day
                        scheduleList.sortBy { it.day }
                        
                        // Notify adapter
                        scheduleAdapter.notifyDataSetChanged()
                        recyclerViewAdapter.notifyDataSetChanged()
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
        val tvDay = itemView?.findViewById<TextView>(R.id.tvDay)
        val layoutShiftChips = itemView?.findViewById<LinearLayout>(R.id.layoutShiftChips)
        // Tanggal
        tvDate?.text = item.day.toString()
        // Hari
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, item.day)
        val hari = SimpleDateFormat("EEEE", Locale("id", "ID")).format(calendar.time)
        tvDay?.text = hari
        // Hari Minggu warna merah
        if (hari.lowercase().contains("minggu")) {
            tvDay?.setTextColor(android.graphics.Color.RED)
            tvDate?.setBackgroundResource(R.drawable.circle_date_bg_merah)
        } else {
            tvDay?.setTextColor(android.graphics.Color.parseColor("#3F51B5"))
            tvDate?.setBackgroundResource(R.drawable.circle_date_bg)
        }
        // Bersihkan chip shift
        layoutShiftChips?.removeAllViews()
        val shifts = item.shift.split(",")
        val shiftTimes = item.shiftTime.split("\n")
        for ((i, shift) in shifts.withIndex()) {
            val chip = TextView(context)
            chip.text = if (shift == "L") "Libur" else shiftTimes.getOrNull(i) ?: shift
            chip.setPadding(0, 0, 0, 0)
            chip.textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            chip.gravity = android.view.Gravity.CENTER
            chip.textSize = if (shift == "L") 16f else 14f
            chip.setTypeface(null, if (shift == "L") android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            chip.setTextColor(android.graphics.Color.WHITE)
            chip.setBackgroundResource(when (shift) {
                "P" -> R.drawable.chip_shift_pagi
                "S" -> R.drawable.chip_shift_siang
                "M" -> R.drawable.chip_shift_malam
                "L" -> R.drawable.chip_shift_libur
                else -> R.drawable.chip_shift_libur
            })
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, 0)
            chip.layoutParams = params
            layoutShiftChips?.addView(chip)
        }
        return itemView!!
    }
}

// Adapter untuk mode list (RecyclerView)
class ScheduleListAdapter(private val items: List<ScheduleItem>) : RecyclerView.Adapter<ScheduleListAdapter.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_schedule_list, parent, false)
        return ViewHolder(view)
    }
    override fun getItemCount(): Int = items.size
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(item: ScheduleItem) {
            val tvDate = itemView.findViewById<TextView>(R.id.tvDate)
            val tvDay = itemView.findViewById<TextView>(R.id.tvDay)
            val layoutShiftChips = itemView.findViewById<LinearLayout>(R.id.layoutShiftChips)
            val tvKeterangan = itemView.findViewById<TextView>(R.id.tvKeterangan)
            val icInfo = itemView.findViewById<ImageView>(R.id.icInfo)
            val bgDateCircle = itemView.findViewById<View>(R.id.bgDateCircle)
            // Tanggal
            tvDate?.text = item.day.toString()
            // Hari
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.DAY_OF_MONTH, item.day)
            val hari = SimpleDateFormat("EEEE", Locale("id", "ID")).format(calendar.time)
            tvDay?.text = hari
            // Hari Minggu warna merah, lingkaran tanggal juga merah
            if (hari.lowercase().contains("minggu")) {
                tvDay?.setTextColor(android.graphics.Color.RED)
                tvDate?.setTextColor(android.graphics.Color.WHITE)
                bgDateCircle?.setBackgroundResource(R.drawable.circle_date_bg_merah)
            } else {
                tvDay?.setTextColor(android.graphics.Color.parseColor("#3F51B5"))
                tvDate?.setTextColor(android.graphics.Color.WHITE)
                bgDateCircle?.setBackgroundResource(R.drawable.circle_date_bg)
            }
            // Bersihkan chip shift
            layoutShiftChips?.removeAllViews()
            val shifts = item.shift.split(",")
            val shiftTimes = item.shiftTime.split("\n")
            for ((i, shift) in shifts.withIndex()) {
                val chip = TextView(itemView.context)
                // Icon shift
                val icon = when (shift) {
                    "P" -> "\u2600 " // Matahari
                    "S" -> "\u26C5 " // Matahari berawan
                    "M" -> "\uD83C\uDF19 " // Bulan
                    "L" -> "\u274C " // Silang
                    else -> ""
                }
                chip.text = icon + if (shift == "L") "Libur" else shiftTimes.getOrNull(i) ?: shift
                chip.setPadding(28, 10, 28, 10)
                chip.setTextColor(android.graphics.Color.WHITE)
                chip.textSize = 16f
                chip.setTypeface(null, if (shift == "L") android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                chip.setBackgroundResource(when (shift) {
                    "P" -> R.drawable.chip_shift_pagi
                    "S" -> R.drawable.chip_shift_siang
                    "M" -> R.drawable.chip_shift_malam
                    "L" -> R.drawable.chip_shift_libur
                    else -> R.drawable.chip_shift_libur
                })
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(12, 0, 12, 0)
                chip.layoutParams = params
                layoutShiftChips?.addView(chip)
            }
            // Keterangan
            if (!item.keterangan.isNullOrEmpty()) {
                tvKeterangan?.visibility = View.VISIBLE
                icInfo?.visibility = View.VISIBLE
                tvKeterangan?.text = "Keterangan: ${item.keterangan}"
            } else {
                tvKeterangan?.visibility = View.GONE
                icInfo?.visibility = View.GONE
            }
        }
    }
} 