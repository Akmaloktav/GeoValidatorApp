package com.akmal.geovalidator

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

/**
 * Kelas utama untuk melakukan validasi lokasi.
 * Objek kelas ini dibuat melalui Builder.
 *
 * @property context Konteks aplikasi, dibutuhkan untuk mengakses layanan lokasi.
 * @property targetLatitude Garis lintang dari lokasi target (misal: kantor).
 * @property targetLongitude Garis bujur dari lokasi target.
 * @property radius Jarak radius yang diizinkan dari lokasi target (dalam meter).
 * @property enableMockCheck Jika true, akan melakukan pengecekan lokasi palsu.
 */
class GeoValidator private constructor(
    private val context: Context,
    private val targetLatitude: Double,
    private val targetLongitude: Double,
    private val radius: Double,
    private val enableMockCheck: Boolean
) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    /**
     * Menjalankan proses validasi lokasi secara asynchronous.
     * @param callback Fungsi yang akan dipanggil dengan hasil validasi (Success atau Failure).
     */
    @SuppressLint("MissingPermission")
    fun validate(callback: (ValidationResult) -> Unit) {
        // 1. Cek Izin Lokasi
        if (!hasLocationPermission()) {
            callback(ValidationResult.Failure(ErrorType.PERMISSION_MISSING))
            return
        }

        // 2. Dapatkan Lokasi Saat Ini
        // Anotasi SuppressLint diperlukan karena kita sudah melakukan pengecekan izin secara manual di atas.
        fusedLocationClient.getCurrentLocation(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
            null
        ).addOnSuccessListener { location: android.location.Location? ->
            // 3. Lakukan Rangkaian Validasi
            if (location == null) {
                callback(ValidationResult.Failure(ErrorType.LOCATION_UNAVAILABLE))
                return@addOnSuccessListener
            }

            // 3a. Cek Lokasi Palsu (jika diaktifkan)
            if (enableMockCheck && isMockLocation(location)) {
                callback(ValidationResult.Failure(ErrorType.MOCK_LOCATION_DETECTED))
                return@addOnSuccessListener
            }

            // 3b. Cek Jarak/Radius
            if (!isWithinGeofence(location)) {
                callback(ValidationResult.Failure(ErrorType.OUTSIDE_GEOFENCE))
                return@addOnSuccessListener
            }

            // 4. Jika semua validasi lolos, kirim hasil Success
            callback(ValidationResult.Success(location))

        }.addOnFailureListener {
            // Jika gagal mendapatkan lokasi dari FusedLocationProviderClient
            callback(ValidationResult.Failure(ErrorType.LOCATION_UNAVAILABLE))
        }
    }

    private fun hasLocationPermission(): Boolean {
        return android.content.pm.PackageManager.PERMISSION_GRANTED ==
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                )
    }

    private fun isWithinGeofence(location: android.location.Location): Boolean {
        val distance = FloatArray(1)
        android.location.Location.distanceBetween(
            location.latitude,
            location.longitude,
            targetLatitude,
            targetLongitude,
            distance
        )
        return distance[0] <= radius
    }

    private fun isMockLocation(location: android.location.Location): Boolean {
        // Menggunakan isFromMockProvider yang deprecated untuk cakupan API level yang lebih luas.
        // Untuk API 31+ bisa menggunakan location.isMock.
        @Suppress("DEPRECATION")
        return location.isFromMockProvider
    }


    /**
     * Builder untuk mengonfigurasi dan membuat instance GeoValidator.
     */
    class Builder(private val context: Context) {
        private var targetLatitude: Double? = null
        private var targetLongitude: Double? = null
        private var radius: Double = 100.0
        private var enableMockCheck: Boolean = true

        /**
         * Mengatur lokasi target (tengah dari geofence).
         * @param latitude Garis lintang.
         * @param longitude Garis bujur.
         */
        fun setTargetLocation(latitude: Double, longitude: Double) = apply {
            this.targetLatitude = latitude
            this.targetLongitude = longitude
        }

        /**
         * Mengatur radius yang diizinkan dari lokasi target.
         * @param radius Jarak dalam meter.
         */
        fun setRadius(radius: Double) = apply {
            this.radius = radius
        }

        /**
         * Mengaktifkan atau menonaktifkan pengecekan lokasi palsu.
         * @param enabled Jika true, pengecekan akan dilakukan.
         */
        fun enableMockLocationCheck(enabled: Boolean) = apply {
            this.enableMockCheck = enabled
        }

        /**
         * Membuat instance GeoValidator dengan konfigurasi yang telah diatur.
         * @throws IllegalArgumentException jika lokasi target belum diatur.
         */
        fun build(): GeoValidator {
            // Pastikan lokasi target sudah diatur sebelum membuat objek
            requireNotNull(targetLatitude) { "Target latitude must be set." }
            requireNotNull(targetLongitude) { "Target longitude must be set." }

            return GeoValidator(
                context = context,
                targetLatitude = targetLatitude!!,
                targetLongitude = targetLongitude!!,
                radius = radius,
                enableMockCheck = enableMockCheck
            )
        }
    }
}