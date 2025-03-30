package com.android.absensi.ui.history

import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.RecyclerView
import com.android.absensi.R

// Data class untuk item riwayat
data class HistoryItem(
    val id: Int,
    val tanggal: String,
    val jamMasuk: String,
    val jamKeluar: String,
    val status: String,
    val shift: String
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
        
        private val tvTanggal: TextView = itemView.findViewById(R.id.tvTanggal)
        private val tvShift: TextView = itemView.findViewById(R.id.tvShift)
        private val tvJamMasuk: TextView = itemView.findViewById(R.id.tvJamMasuk)
        private val tvJamKeluar: TextView = itemView.findViewById(R.id.tvJamKeluar)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        
        @RequiresApi(Build.VERSION_CODES.M)
        fun bind(item: HistoryItem) {
            tvTanggal.text = item.tanggal
            
            // Set shift info
            when (item.shift) {
                "P" -> tvShift.text = "Shift: Pagi"
                "S" -> tvShift.text = "Shift: Siang"
                "M" -> tvShift.text = "Shift: Malam"
                "L" -> tvShift.text = "Shift: Libur"
                else -> tvShift.text = "Shift: -"
            }
            
            tvJamMasuk.text = item.jamMasuk
            tvJamKeluar.text = item.jamKeluar
            
            // Set status dengan warna
            when (item.status) {
                "hadir" -> {
                    tvStatus.text = "Hadir"
                    tvStatus.setTextColor(itemView.context.getColor(R.color.green_text))
                }
                "terlambat" -> {
                    tvStatus.text = "Terlambat"
                    tvStatus.setTextColor(itemView.context.getColor(R.color.orange_text))
                }
                "alpha" -> {
                    tvStatus.text = "Alpha"
                    tvStatus.setTextColor(itemView.context.getColor(R.color.red_text))
                }
                else -> {
                    tvStatus.text = "Belum Absen"
                    tvStatus.setTextColor(itemView.context.getColor(R.color.gray_text))
                }
            }
        }
    }
}