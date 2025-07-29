package com.akmal.geovalidator

/**
 * Enum yang merepresentasikan alasan spesifik mengapa validasi lokasi gagal.
 * Setiap error kini memiliki kategori untuk penanganan yang lebih fleksibel.
 */
enum class ErrorType(val category: ErrorCategory) {
    /**
     * Izin lokasi (ACCESS_FINE_LOCATION) belum diberikan oleh pengguna.
     */
    PERMISSION_MISSING(ErrorCategory.SETUP),

    /**
     * Gagal mendapatkan lokasi dari perangkat (misal: GPS mati atau tidak ada sinyal).
     */
    LOCATION_UNAVAILABLE(ErrorCategory.OPERATIONAL),

    /**
     * Terdeteksi bahwa pengguna menggunakan lokasi palsu (mock location).
     */
    MOCK_LOCATION_DETECTED(ErrorCategory.SECURITY),

    /**
     * Pengguna berada di lokasi yang valid tetapi di luar radius yang ditentukan.
     */
    OUTSIDE_GEOFENCE(ErrorCategory.OPERATIONAL),

    /**
     * Lokasi terdeteksi tidak wajar setelah melalui verifikasi dua langkah.
     */
    UNNATURAL_LOCATION_DETECTED(ErrorCategory.SECURITY),

    /**
    * Terdeteksi bahwa sebuah aplikasi telah ditetapkan sebagai "aplikasi lokasi palsu"
    * di dalam Opsi Pengembang.
    */
    MOCK_LOCATION_APP_SET(ErrorCategory.SECURITY)
}