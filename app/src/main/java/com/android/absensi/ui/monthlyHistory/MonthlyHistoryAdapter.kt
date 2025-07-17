package com.android.absensi.ui.monthlyHistory

import android.R.color.black
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.android.absensi.R
import com.android.absensi.databinding.ItemDailyStatusBinding

class MonthlyHistoryAdapter(private var items: List<DailyStatusItem>) :
    RecyclerView.Adapter<MonthlyHistoryAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDailyStatusBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun setData(newItems: List<DailyStatusItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemDailyStatusBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DailyStatusItem) {
            binding.tvDate.text = item.dateText
            binding.tvStatus.text = item.statusText

            val context = binding.root.context
            val (bgColor, textColor) = getStatusColors(context, item.status)
            binding.tvStatus.setBackgroundColor(bgColor)
            binding.tvStatus.setTextColor(textColor)
        }

        private fun getStatusColors(context: Context, status: StatusType): Pair<Int, Int> {
            val white = ContextCompat.getColor(context, android.R.color.white)
            return when (status) {
                StatusType.HADIR -> Pair(R.color.green, white)
                StatusType.TERLAMBAT -> Pair(R.color.yellow, white)
                StatusType.SAKIT -> Pair(R.color.orange, white)
                StatusType.IZIN -> Pair(R.color.blue, white)
                StatusType.CUTI -> Pair(R.color.purple_500, white)
                StatusType.PULANG_CEPAT -> Pair(R.color.teal_700, white)
                StatusType.ALPHA -> Pair(R.color.red, white)
                StatusType.PENDING -> Pair(R.color.gray, white)
            }
        }
    }
}
