package com.akmal.geovalidator

/**
 * Enum yang merepresentasikan alasan spesifik mengapa validasi lokasi gagal.
 */
enum class ErrorType {
    /**
     * Izin lokasi (ACCESS_FINE_LOCATION) belum diberikan oleh pengguna.
     */
    PERMISSION_MISSING,

    /**
     * Gagal mendapatkan lokasi dari perangkat (misal: GPS mati atau tidak ada sinyal).
     */
    LOCATION_UNAVAILABLE,

    /**
     * Terdeteksi bahwa pengguna menggunakan lokasi palsu (mock location).
     */
    MOCK_LOCATION_DETECTED,

    /**
     * Pengguna berada di lokasi yang valid tetapi di luar radius yang ditentukan.
     */
    OUTSIDE_GEOFENCE,

    /**
     * Lokasi terdeteksi tidak wajar setelah melalui verifikasi dua langkah
     * (misal: akurasi dan koordinat statis sempurna, mengindikasikan data sintetis).
     */
    UNNATURAL_LOCATION_DETECTED
}