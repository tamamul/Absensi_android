package com.android.absensi.ui.history

data class PermissionItem(
    val id: Int,
    val jenisPengajuan: String,
    val tanggalPengajuan: String,
    val tanggalMulai: String,
    val tanggalSelesai: String,
    val alasan: String,
    val status: String,
    val buktiFoto: String,
    val catatanAdmin: String
)
