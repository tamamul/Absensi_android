package com.android.absensi.ui.monthlyHistory

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
import com.android.absensi.databinding.FragmentMonthlyHistoryBinding
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MonthlyHistoryFragment : Fragment() {

    private var _binding: FragmentMonthlyHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var monthlyAdapter: MonthlyHistoryAdapter
    private var satpamId: Int = 0
    private var bulan: Int = 0
    private var tahun: Int = 0

    private val monthNames = arrayOf(
        "Januari", "Februari", "Maret", "April", "Mei", "Juni",
        "Juli", "Agustus", "September", "Oktober", "November", "Desember"
    )
    private val yearsList = ArrayList<Int>()

    private val TAG = "MonthlyHistoryFragment"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMonthlyHistoryBinding.inflate(inflater, container, false)

        loadUserData()
        val calendar = Calendar.getInstance()
        bulan = calendar.get(Calendar.MONTH) + 1
        tahun = calendar.get(Calendar.YEAR)

        setupSpinners()
        setupRecyclerView()
        loadCombinedHistory()

        return binding.root
    }

    private fun loadUserData() {
        val sharedPref = requireActivity().getSharedPreferences("login_data", Context.MODE_PRIVATE)
        satpamId = sharedPref.getInt("id", 0)
    }

    private fun setupRecyclerView() {
        monthlyAdapter = MonthlyHistoryAdapter(emptyList())
        binding.rvMonthlyHistory.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = monthlyAdapter
        }
    }

    private fun setupSpinners() {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        yearsList.add(currentYear - 1)
        yearsList.add(currentYear)
        yearsList.add(currentYear + 1)
        val yearAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, yearsList)
        yearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerYear.adapter = yearAdapter
        binding.spinnerYear.setSelection(yearsList.indexOf(tahun))

        val monthAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, monthNames)
        monthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerMonth.adapter = monthAdapter
        binding.spinnerMonth.setSelection(bulan - 1)

        val listener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                bulan = binding.spinnerMonth.selectedItemPosition + 1
                tahun = binding.spinnerYear.selectedItem as Int
                loadCombinedHistory()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        binding.spinnerMonth.onItemSelectedListener = listener
        binding.spinnerYear.onItemSelectedListener = listener
    }

    private fun loadCombinedHistory() {
        binding.progressBar.visibility = View.VISIBLE
        binding.layoutEmpty.visibility = View.GONE
        binding.rvMonthlyHistory.visibility = View.GONE

        // Data holders
        var attendanceData: JSONArray? = null
        var permissionsData: JSONArray? = null
        var attendanceError: String? = null
        var permissionsError: String? = null

        var completedRequests = 0
        val totalRequests = 2

        val onBothRequestsComplete = {
            binding.progressBar.visibility = View.GONE
            if (attendanceData != null && permissionsData != null) {
                processAndDisplayData(attendanceData!!, permissionsData!!)
            } else {
                val errorMsg = attendanceError ?: permissionsError ?: "Gagal memuat data."
                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                binding.layoutEmpty.visibility = View.VISIBLE
            }
        }

        // 1. Fetch Attendance Data
        fetchData("get_absensi.php") { response, error ->
            completedRequests++
            if (response != null) attendanceData = response else attendanceError = error
            if (completedRequests == totalRequests) onBothRequestsComplete()
        }

        // 2. Fetch Permissions Data
        fetchData("get_permissions.php") { response, error ->
            completedRequests++
            if (response != null) permissionsData = response else permissionsError = error
            if (completedRequests == totalRequests) onBothRequestsComplete()
        }
    }

    private fun fetchData(endpoint: String, callback: (JSONArray?, String?) -> Unit) {
        val url = getString(R.string.ip_api) + endpoint
        val queue = Volley.newRequestQueue(requireContext())

        val stringRequest = object : StringRequest(Method.POST, url,
            { response ->
                try {
                    val jsonResponse = JSONObject(response)
                    if (jsonResponse.getBoolean("success")) {
                        // get_absensi.php has a nested data object
                        val data = if (endpoint == "get_absensi.php") {
                            jsonResponse.getJSONObject("data").getJSONArray("absensi")
                        } else {
                            jsonResponse.getJSONArray("data")
                        }
                        callback(data, null)
                    } else {
                        // Jika success false tapi ada data kosong, anggap berhasil
                        if (jsonResponse.has("data")) {
                            callback(JSONArray(), null)
                        } else {
                            callback(null, jsonResponse.getString("message"))
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing $endpoint: ", e)
                    callback(null, "Error parsing data.")
                }
            },
            { error ->
                Log.e(TAG, "Volley error on $endpoint: ", error)
                callback(null, "Gagal terhubung ke server.")
            }) {
            override fun getParams(): Map<String, String> {
                return mapOf(
                    "satpam_id" to satpamId.toString(),
                    "bulan" to bulan.toString(),
                    "tahun" to tahun.toString()
                )
            }
        }
        queue.add(stringRequest)
    }

    private fun isLate(jamMasuk: String, shiftCode: String): Boolean {
        // Fungsi sederhana untuk cek keterlambatan.
        // Format jamMasuk diasumsikan "HH:mm:ss"
        if (jamMasuk.isBlank() || jamMasuk == "-") return false

        val jamMasukTime = jamMasuk.substring(0, 5) // Ambil "HH:mm"

        val batasWaktu = when(shiftCode.uppercase()) {
            "P" -> "07:00"
            "S" -> "15:00"
            "M" -> "23:00"
            else -> return false
        }
        // Perbandingan string "HH:mm" sudah cukup untuk kasus ini
        return jamMasukTime > batasWaktu
    }

    private fun processAndDisplayData(attendanceArray: JSONArray, permissionsArray: JSONArray) {
        val allEvents = ArrayList<DailyStatusItem>()
        val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val displayDateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID"))
        val datesWithEvents = HashSet<String>()

        // 1. Proses semua data absensi (per shift)
        for (i in 0 until attendanceArray.length()) {
            val item = attendanceArray.getJSONObject(i)
            val tanggalStr = item.getString("tanggal")
            val tanggalDate = apiDateFormat.parse(tanggalStr) ?: continue

            val shift = item.getString("shift").uppercase()
            val jamMasuk = item.getString("jam_masuk")
            val shiftLabel = when(shift) {
                "P" -> "Shift Pagi"
                "S" -> "Shift Siang"
                "M" -> "Shift Malam"
                else -> "Lainnya"
            }

            val statusType = StatusType.HADIR
            val statusText = if (isLate(jamMasuk, shift)) {
                "Hadir (Terlambat)" // Beri keterangan tambahan
            } else {
                "Hadir"
            }

            val sortPriority = when(shift) {
                "P" -> 1
                "S" -> 2
                "M" -> 3
                else -> 4
            }

            allEvents.add(
                DailyStatusItem(
                    fullDate = tanggalDate,
                    dateText = "${displayDateFormat.format(tanggalDate)} - $shiftLabel",
                    status = statusType,
                    statusText = statusText,
                    sortPriority = sortPriority
                )
            )
            datesWithEvents.add(tanggalStr)
        }

        // 2. Proses semua data pengajuan (izin, sakit, cuti)
        for (i in 0 until permissionsArray.length()) {
            val item = permissionsArray.getJSONObject(i)
            if (item.getString("status").equals("Disetujui", ignoreCase = true)) {
                val startDate = apiDateFormat.parse(item.getString("tanggal_mulai"))
                val endDate = apiDateFormat.parse(item.getString("tanggal_selesai"))
                if(startDate != null && endDate != null) {
                    val cal = Calendar.getInstance().apply { time = startDate }
                    val jenisPengajuan = item.getString("jenis_pengajuan").replaceFirstChar { it.titlecase(Locale.getDefault()) }
                    val statusType = when (jenisPengajuan.lowercase()) {
                        "sakit" -> StatusType.SAKIT
                        "izin" -> StatusType.IZIN
                        "cuti" -> StatusType.CUTI
                        "pulang cepat" -> StatusType.PULANG_CEPAT
                        else -> StatusType.IZIN
                    }

                    while (!cal.time.after(endDate)) {
                        val currentDayStr = apiDateFormat.format(cal.time)
                        if (!datesWithEvents.contains(currentDayStr)) { // Hanya tambah jika belum ada absensi di hari itu
                            allEvents.add(
                                DailyStatusItem(
                                    fullDate = cal.time,
                                    dateText = displayDateFormat.format(cal.time),
                                    status = statusType,
                                    statusText = jenisPengajuan,
                                    sortPriority = 5 // Prioritas setelah shift
                                )
                            )
                            datesWithEvents.add(currentDayStr)
                        }
                        cal.add(Calendar.DATE, 1)
                    }
                }
            }
        }

        // 3. Cari hari tanpa event untuk ditandai sebagai 'Alpha'
        val calendar = Calendar.getInstance().apply { set(tahun, bulan - 1, 1) }
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val today = Calendar.getInstance()

        for (day in 1..daysInMonth) {
            calendar.set(Calendar.DAY_OF_MONTH, day)
            val currentDayStr = apiDateFormat.format(calendar.time)

            // Tandai Alpha hanya untuk hari kemarin dan sebelumnya yang tidak ada event sama sekali
            if (!datesWithEvents.contains(currentDayStr) && calendar.before(today) && !isSameDay(calendar, today)) {
                allEvents.add(
                    DailyStatusItem(
                        fullDate = calendar.time,
                        dateText = displayDateFormat.format(calendar.time),
                        status = StatusType.ALPHA,
                        statusText = "Alpha",
                        sortPriority = 6 // Prioritas paling akhir
                    )
                )
            }
        }

        // 4. Urutkan semua event berdasarkan tanggal (terbaru dulu) lalu prioritas shift
        allEvents.sortWith(compareByDescending<DailyStatusItem> { it.fullDate }.thenBy { it.sortPriority })

        // 5. Tampilkan data dan rekapitulasi total
        if (allEvents.isEmpty()) {
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.rvMonthlyHistory.visibility = View.GONE
            binding.summaryCard.visibility = View.GONE
        } else {
            var totalHadir = 0
            var totalSakit = 0
            var totalIzinCuti = 0
            var totalAlpha = 0

            for (item in allEvents) {
                when (item.status) {
                    StatusType.HADIR -> totalHadir++
                    StatusType.SAKIT -> totalSakit++
                    StatusType.IZIN, StatusType.CUTI, StatusType.PULANG_CEPAT -> totalIzinCuti++
                    StatusType.ALPHA -> totalAlpha++
                    else -> { /* Abaikan PENDING */ }
                }
            }

            // Update UI Rekapitulasi (Anda mungkin perlu menyesuaikan layout rekapitulasi untuk 'Terlambat')
            binding.tvTotalHadir.text = totalHadir.toString()
            binding.tvTotalSakit.text = totalSakit.toString()
            binding.tvTotalIzin.text = totalIzinCuti.toString()
            binding.tvTotalAlpha.text = totalAlpha.toString()
            // Contoh jika Anda menambahkan TextView untuk total terlambat: binding.tvTotalTerlambat.text = totalTerlambat.toString()

            binding.layoutEmpty.visibility = View.GONE
            binding.rvMonthlyHistory.visibility = View.VISIBLE
            binding.summaryCard.visibility = View.VISIBLE
            monthlyAdapter.setData(allEvents)
        }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
