package com.android.absensi.ui.monthlyHistory

// Definisikan enum untuk mempermudah logika
enum class StatusType {
    HADIR,
    SAKIT,
    TERLAMBAT,
    IZIN,
    CUTI,
    PULANG_CEPAT,
    ALPHA,
    PENDING // Untuk status di masa depan atau yang belum ada datanya
}

data class DailyStatusItem(
    val fullDate: java.util.Date,
    val dateText: String,
    val status: StatusType,
    val statusText: String,
    val sortPriority: Int
)
