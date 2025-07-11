package com.akmal.geovalidator

import android.location.Location

/**
 * Sealed class yang merepresentasikan hasil dari proses validasi lokasi.
 */
sealed class ValidationResult {
    /**
     * Menandakan validasi berhasil. Mengandung data lokasi yang valid.
     */
    data class Success(val location: Location) : ValidationResult()

    /**
     * Menandakan validasi gagal. Mengandung aksi (lambda) yang harus dieksekusi.
     */
    data class Failure(val action: () -> Unit) : ValidationResult()
}