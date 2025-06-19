package com.android.absensi.ui.history

import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.android.absensi.R

// Data class untuk item riwayat
data class HistoryItem(
    val id: Int,
    val tanggal: String,
    val jamMasuk: String,
    val jamKeluar: String,
    val status: String,
    val shift: String,
    val shiftTime: String = "" // Tambahan untuk waktu shift
)

// Adapter untuk recycler view riwayat
class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {
    
    private val historyList = ArrayList<HistoryItem>()
    
    fun setData(newList: List<HistoryItem>) {
        historyList.clear()
        historyList.addAll(newList)
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false)
        return HistoryViewHolder(view)
    }
    
    @RequiresApi(Build.VERSION_CODES.M)
    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val item = historyList[position]
        holder.bind(item)
    }
    
    override fun getItemCount(): Int = historyList.size
    
    inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        
        private val cardView: CardView = itemView.findViewById(R.id.cardView)
        private val tvTanggal: TextView = itemView.findViewById(R.id.tvTanggal)
        private val tvShift: TextView = itemView.findViewById(R.id.tvShift)
        private val tvJamMasuk: TextView = itemView.findViewById(R.id.tvJamMasuk)
        private val tvJamKeluar: TextView = itemView.findViewById(R.id.tvJamKeluar)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        
        @RequiresApi(Build.VERSION_CODES.M)
        fun bind(item: HistoryItem) {
            tvTanggal.text = item.tanggal
            
            // Set shift info dengan waktu shift
            val shiftText = when (item.shift) {
                "P" -> "Shift Pagi"
                "S" -> "Shift Siang"
                "M" -> "Shift Malam"
                "L" -> "Libur"
                else -> "Shift: -"
            }
            tvShift.text = if (item.shiftTime.isNotEmpty()) {
                "$shiftText (${item.shiftTime})"
            } else {
                shiftText
            }
            
            // Set background color berdasarkan shift
            val backgroundColor = when (item.shift) {
                "P" -> R.color.shift_pagi
                "S" -> R.color.shift_siang
                "M" -> R.color.shift_malam
                "L" -> R.color.shift_libur
                else -> R.color.light_gray
            }
            cardView.setCardBackgroundColor(ContextCompat.getColor(itemView.context, backgroundColor))
            
            // Format jam masuk/keluar
            tvJamMasuk.text = if (item.jamMasuk != "null" && item.jamMasuk != "-") item.jamMasuk else "-"
            tvJamKeluar.text = if (item.jamKeluar != "null" && item.jamKeluar != "-") item.jamKeluar else "-"
            
            // Set status dengan warna
            when (item.status) {
                "hadir" -> {
                    tvStatus.text = "Hadir"
                    tvStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.green_text))
                }
                "terlambat" -> {
                    tvStatus.text = "Terlambat"
                    tvStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.orange_text))
                }
                "alpha" -> {
                    tvStatus.text = "Alpha"
                    tvStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.red_text))
                }
                else -> {
                    tvStatus.text = "Belum Absen"
                    tvStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.gray_text))
                }
            }
        }
    }
}