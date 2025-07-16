package com.android.absensi.ui.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.android.absensi.R
import java.text.SimpleDateFormat
import java.util.Locale

class PermissionsAdapter(private var permissions: List<PermissionItem>) :
    RecyclerView.Adapter<PermissionsAdapter.PermissionViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PermissionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_permission, parent, false)
        return PermissionViewHolder(view)
    }

    override fun onBindViewHolder(holder: PermissionViewHolder, position: Int) {
        val permission = permissions[position]
        holder.bind(permission)
    }

    override fun getItemCount(): Int = permissions.size

    fun setData(newPermissions: List<PermissionItem>) {
        this.permissions = newPermissions
        notifyDataSetChanged()
    }

    class PermissionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvPermissionType: TextView = itemView.findViewById(R.id.tvPermissionType)
        private val tvPermissionDate: TextView = itemView.findViewById(R.id.tvPermissionDate)
        private val tvPermissionReason: TextView = itemView.findViewById(R.id.tvPermissionReason)

        fun bind(permission: PermissionItem) {
            // Mengubah jenis pengajuan menjadi format yang lebih rapi (huruf kapital di awal)
            tvPermissionType.text = permission.jenisPengajuan.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }

            // Format tanggal pengajuan
            try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val outputFormat = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID"))
                val date = inputFormat.parse(permission.tanggalPengajuan)
                tvPermissionDate.text = date?.let { outputFormat.format(it) } ?: permission.tanggalPengajuan
            } catch (e: Exception) {
                tvPermissionDate.text = permission.tanggalPengajuan // Fallback jika format salah
            }

            tvPermissionReason.text = "Keterangan: ${permission.alasan}"
        }
    }
}
