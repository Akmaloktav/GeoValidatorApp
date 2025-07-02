package com.akmal.geovalidator

import android.location.Location

/**
 * Sealed class yang merepresentasikan hasil dari proses validasi lokasi.
 * Bisa berupa Success (berhasil) atau Failure (gagal).
 */
sealed class ValidationResult {
    /**
     * Menandakan validasi berhasil. Mengandung data lokasi yang valid.
     * @param location Objek Lokasi yang telah terverifikasi.
     */
    data class Success(val location: Location) : ValidationResult()

    /**
     * Menandakan validasi gagal. Mengandung tipe error yang spesifik.
     * @param errorType Alasan mengapa validasi gagal.
     */
    data class Failure(val errorType: ErrorType) : ValidationResult()
}