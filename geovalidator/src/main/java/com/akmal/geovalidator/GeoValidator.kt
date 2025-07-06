package com.akmal.geovalidator

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

/**
 * Kelas utama untuk melakukan validasi lokasi.
 * Objek kelas ini dibuat melalui Builder.
 *
 * @property context Konteks aplikasi, dibutuhkan untuk mengakses layanan lokasi.
 * @property targetLatitude Garis lintang dari lokasi target (misal: kantor).
 * @property targetLongitude Garis bujur dari lokasi target.
 * @property radius Jarak radius yang diizinkan dari lokasi target (dalam meter).
 * @property enableMockCheck Jika true, akan melakukan pengecekan lokasi palsu (isMock).
 * @property enableAdvancedValidation Jika true, akan mengaktifkan verifikasi dua langkah untuk mendeteksi anomali akurasi.
 * @property accuracyThreshold Ambang batas akurasi (dalam meter) yang digunakan untuk memicu verifikasi dua langkah.
 */
class GeoValidator private constructor(
    private val context: Context,
    private val targetLatitude: Double,
    private val targetLongitude: Double,
    private val radius: Double,
    private val enableMockCheck: Boolean,
    private val enableAdvancedValidation: Boolean,
    private val accuracyThreshold: Float
) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    /**
     * Menjalankan proses validasi lokasi secara asynchronous.
     * @param callback Fungsi yang akan dipanggil dengan hasil validasi (Success atau Failure).
     */
    @SuppressLint("MissingPermission")
    fun validate(callback: (ValidationResult) -> Unit) {
        if (!hasLocationPermission()) {
            callback(ValidationResult.Failure(ErrorType.PERMISSION_MISSING))
            return
        }

        val cancellationTokenSource = CancellationTokenSource()
        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            cancellationTokenSource.token
        ).addOnSuccessListener { location: Location? ->
            if (location == null) {
                callback(ValidationResult.Failure(ErrorType.LOCATION_UNAVAILABLE))
                return@addOnSuccessListener
            }

            // Validasi #1: Lokasi Palsu (isMock)
            if (enableMockCheck && isMockLocation(location)) {
                callback(ValidationResult.Failure(ErrorType.MOCK_LOCATION_DETECTED))
                return@addOnSuccessListener
            }

            // Validasi #2: Validasi Lanjutan (Akurasi & Konsistensi) - jika diaktifkan
            if (enableAdvancedValidation && location.accuracy < this.accuracyThreshold) {
                Log.d("GeoValidator", "Akurasi mencurigakan (${location.accuracy}m). Memulai verifikasi 2 langkah.")
                performSecondCheck(location, callback)
            } else {
                // Langsung ke pengecekan geofence jika validasi lanjutan tidak aktif atau akurasi tidak mencurigakan.
                performGeofenceCheck(location, callback)
            }
        }.addOnFailureListener {
            callback(ValidationResult.Failure(ErrorType.LOCATION_UNAVAILABLE))
        }
    }

    @SuppressLint("MissingPermission")
    private fun performSecondCheck(firstLocation: Location, callback: (ValidationResult) -> Unit) {
        Handler(Looper.getMainLooper()).postDelayed({
            val cancellationTokenSource = CancellationTokenSource()
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token)
                .addOnSuccessListener { secondLocation ->
                    if (secondLocation == null) {
                        callback(ValidationResult.Failure(ErrorType.LOCATION_UNAVAILABLE))
                        return@addOnSuccessListener
                    }

                    val isAccuracyStatic = firstLocation.accuracy == secondLocation.accuracy
                    val areCoordinatesStatic = firstLocation.latitude == secondLocation.latitude && firstLocation.longitude == secondLocation.longitude

                    if (isAccuracyStatic && areCoordinatesStatic) {
                        Log.w("GeoValidator", "Verifikasi gagal. Data lokasi statis.")
                        callback(ValidationResult.Failure(ErrorType.UNNATURAL_LOCATION_DETECTED))
                    } else {
                        Log.d("GeoValidator", "Verifikasi berhasil. Terdeteksi fluktuasi alami.")
                        performGeofenceCheck(firstLocation, callback)
                    }
                }
                .addOnFailureListener {
                    callback(ValidationResult.Failure(ErrorType.LOCATION_UNAVAILABLE))
                }
        }, 2000) // Jeda 2 detik
    }

    private fun performGeofenceCheck(location: Location, callback: (ValidationResult) -> Unit) {
        if (!isWithinGeofence(location)) {
            callback(ValidationResult.Failure(ErrorType.OUTSIDE_GEOFENCE))
            return
        }
        callback(ValidationResult.Success(location))
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isWithinGeofence(location: Location): Boolean {
        val distance = FloatArray(1)
        Location.distanceBetween(
            location.latitude,
            location.longitude,
            targetLatitude,
            targetLongitude,
            distance
        )
        return distance[0] <= radius
    }

    private fun isMockLocation(location: Location): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            location.isMock
        } else {
            @Suppress("DEPRECATION")
            location.isFromMockProvider
        }
    }

    /**
     * Builder untuk mengonfigurasi dan membuat instance GeoValidator.
     */
    class Builder(private val context: Context) {
        private var targetLatitude: Double? = null
        private var targetLongitude: Double? = null
        private var radius: Double = 100.0
        private var enableMockCheck: Boolean = true
        private var enableAdvancedValidation: Boolean = false
        private var accuracyThreshold: Float = 5.0f

        fun setTargetLocation(latitude: Double, longitude: Double) = apply {
            this.targetLatitude = latitude
            this.targetLongitude = longitude
        }

        fun setRadius(radius: Double) = apply {
            this.radius = radius
        }

        fun enableMockLocationCheck(enabled: Boolean) = apply {
            this.enableMockCheck = enabled
        }

        /**
         * Mengaktifkan validasi lanjutan untuk mendeteksi anomali akurasi dan data statis.
         * Sangat direkomendasikan untuk meningkatkan keamanan terhadap GPS palsu yang canggih.
         *
         * @param enabled Jika true, validasi lanjutan akan dijalankan. Defaultnya adalah `false`.
         */
        fun enableAdvancedValidation(enabled: Boolean) = apply {
            this.enableAdvancedValidation = enabled
        }

        /**
         * Mengatur nilai ambang batas akurasi untuk memicu verifikasi dua langkah.
         * Hanya berpengaruh jika `enableAdvancedValidation(true)` dipanggil.
         *
         * @param threshold Akurasi dalam meter. Jika lokasi yang diterima memiliki akurasi di bawah
         * nilai ini, verifikasi kedua akan dijalankan. Defaultnya adalah `5.0f`.
         */
        fun setAccuracyThreshold(threshold: Float) = apply {
            this.accuracyThreshold = threshold
        }

        fun build(): GeoValidator {
            requireNotNull(targetLatitude) { "Target latitude must be set." }
            requireNotNull(targetLongitude) { "Target longitude must be set." }

            return GeoValidator(
                context = context.applicationContext,
                targetLatitude = targetLatitude!!,
                targetLongitude = targetLongitude!!,
                radius = radius,
                enableMockCheck = enableMockCheck,
                enableAdvancedValidation = this.enableAdvancedValidation,
                accuracyThreshold = this.accuracyThreshold
            )
        }
    }
}