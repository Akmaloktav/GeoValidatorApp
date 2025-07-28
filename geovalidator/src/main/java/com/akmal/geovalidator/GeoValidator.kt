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
 * Kelas utama untuk melakukan validasi lokasi secara komprehensif.
 * Dibuat melalui [Builder] untuk mengonfigurasi validasi dan aksi penanganan error.
 *
 * @property context Konteks aplikasi.
 * @property targetLatitude Garis lintang dari lokasi target.
 * @property targetLongitude Garis bujur dari lokasi target.
 * @property radius Jarak radius yang diizinkan dari lokasi target (dalam meter).
 * @property enableMockCheck Jika true, melakukan pengecekan lokasi palsu (isMock).
 * @property enableAdvancedValidation Jika true, mengaktifkan verifikasi dua langkah untuk anomali.
 * @property accuracyThreshold Ambang batas akurasi untuk memicu verifikasi dua langkah.
 * @property errorActions Peta yang berisi [ErrorType] dan aksi (lambda) yang sesuai untuk dieksekusi saat gagal.
 */
class GeoValidator private constructor(
    private val context: Context,
    private val targetLatitude: Double,
    private val targetLongitude: Double,
    private val radius: Double,
    private val enableMockCheck: Boolean,
    private val enableAdvancedValidation: Boolean,
    private val accuracyThreshold: Float,
    private val errorActions: Map<ErrorType, () -> Unit>
) {
//    private val fusedLocationClient: FusedLocationProviderClient =
//        LocationServices.getFusedLocationProviderClient(context)

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    /**
     * Menjalankan proses validasi lokasi secara asynchronous.
     * @param callback Fungsi yang akan dipanggil dengan hasil validasi,
     * baik [ValidationResult.Success] atau [ValidationResult.Failure].
     */
    @SuppressLint("MissingPermission")
    fun validate(callback: (ValidationResult) -> Unit) {
        if (!hasLocationPermission()) {
            handleFailure(ErrorType.PERMISSION_MISSING, callback)
            return
        }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { location: Location? ->
                if (location == null) {
                    handleFailure(ErrorType.LOCATION_UNAVAILABLE, callback)
                    return@addOnSuccessListener
                }

                if (enableMockCheck && isMockLocation(location)) {
                    handleFailure(ErrorType.MOCK_LOCATION_DETECTED, callback)
                    return@addOnSuccessListener
                }

                if (enableAdvancedValidation && location.accuracy < this.accuracyThreshold) {
                    Log.d("GeoValidator", "Akurasi mencurigakan (${location.accuracy}m). Memulai verifikasi 2 langkah.")
                    performSecondCheck(location, callback)
                } else {
                    performGeofenceCheck(location, callback)
                }
            }
            .addOnFailureListener {
                handleFailure(ErrorType.LOCATION_UNAVAILABLE, callback)
            }
    }

    /**
     * Helper internal untuk mencari dan memanggil aksi yang sesuai untuk sebuah [ErrorType].
     * @param errorType Tipe error yang terjadi.
     * @param callback Callback validasi untuk meneruskan hasilnya.
     */
    private fun handleFailure(errorType: ErrorType, callback: (ValidationResult) -> Unit) {
        errorActions[errorType]?.let { action ->
            callback(ValidationResult.Failure(action))
        } ?: Log.e("GeoValidator", "No action defined for ErrorType: $errorType")
    }

    @SuppressLint("MissingPermission")
    private fun performSecondCheck(firstLocation: Location, callback: (ValidationResult) -> Unit) {
        Handler(Looper.getMainLooper()).postDelayed({
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
                .addOnSuccessListener { secondLocation ->
                    if (secondLocation == null) {
                        handleFailure(ErrorType.LOCATION_UNAVAILABLE, callback)
                        return@addOnSuccessListener
                    }
                    val isStatic = firstLocation.accuracy == secondLocation.accuracy && firstLocation.latitude == secondLocation.latitude
                    if (isStatic) {
                        Log.w("GeoValidator", "Verifikasi gagal. Data lokasi statis.")
                        handleFailure(ErrorType.UNNATURAL_LOCATION_DETECTED, callback)
                    } else {
                        Log.d("GeoValidator", "Verifikasi berhasil. Terdeteksi fluktuasi alami.")
                        performGeofenceCheck(firstLocation, callback)
                    }
                }
                .addOnFailureListener { handleFailure(ErrorType.LOCATION_UNAVAILABLE, callback) }
        }, 2000)
    }

    private fun performGeofenceCheck(location: Location, callback: (ValidationResult) -> Unit) {
        if (!isWithinGeofence(location)) {
            handleFailure(ErrorType.OUTSIDE_GEOFENCE, callback)
            return
        }
        callback(ValidationResult.Success(location))
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val r = 6371 // Radius bumi dalam km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c * 1000 // Hasil dalam meter
    }

//    fun isWithinGeofence(location: Location): Boolean {
//        val distance = FloatArray(1)
//        Location.distanceBetween(location.latitude, location.longitude, targetLatitude, targetLongitude, distance)
//        return distance[0] <= radius
//    }

    internal fun isWithinGeofence(location: Location): Boolean {
        val distance = calculateDistance(
            lat1 = location.latitude,
            lon1 = location.longitude,
            lat2 = targetLatitude,
            lon2 = targetLongitude
        )
        return distance <= radius
    }

    internal fun isMockLocation(location: Location): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) location.isMock else @Suppress("DEPRECATION") location.isFromMockProvider
    }

    /**
     * Builder untuk mengonfigurasi dan membuat instance [GeoValidator].
     * Memungkinkan pengaturan parameter validasi dan aksi penanganan error secara fleksibel.
     */
    class Builder(private val context: Context) {
        private var targetLatitude: Double? = null
        private var targetLongitude: Double? = null
        private var radius: Double = 100.0
        private var enableMockCheck: Boolean = true
        private var enableAdvancedValidation: Boolean = false
        private var accuracyThreshold: Float = 5.0f

        private val specificActions = mutableMapOf<ErrorType, () -> Unit>()
        private val categoryActions = mutableMapOf<ErrorCategory, (message: String) -> Unit>()
        private val defaultMessages = mapOf(
            ErrorType.PERMISSION_MISSING to "Izin lokasi dibutuhkan untuk melanjutkan.",
            ErrorType.LOCATION_UNAVAILABLE to "Gagal mendapatkan lokasi Anda. Pastikan GPS aktif.",
            ErrorType.MOCK_LOCATION_DETECTED to "Penggunaan lokasi palsu tidak diizinkan.",
            ErrorType.OUTSIDE_GEOFENCE to "Anda berada di luar area yang ditentukan.",
            ErrorType.UNNATURAL_LOCATION_DETECTED to "Terdeteksi data lokasi yang tidak wajar."
        )

        fun setTargetLocation(latitude: Double, longitude: Double) = apply { this.targetLatitude = latitude; this.targetLongitude = longitude }
        fun setRadius(radius: Double) = apply { this.radius = radius }
        fun enableMockLocationCheck(enabled: Boolean) = apply { this.enableMockCheck = enabled }
        fun enableAdvancedValidation(enabled: Boolean) = apply { this.enableAdvancedValidation = enabled }
        fun setAccuracyThreshold(threshold: Float) = apply { this.accuracyThreshold = threshold }

        /**
         * Menetapkan aksi kustom untuk sebuah [ErrorType] spesifik.
         * Aksi ini akan menjadi prioritas utama, menimpa aksi kategori.
         * @param errorType Tipe error yang ingin ditangani secara khusus.
         * @param action Blok kode (lambda) yang akan dieksekusi saat error ini terjadi.
         * @return [Builder] untuk chaining.
         */
        fun setOnFailureAction(errorType: ErrorType, action: () -> Unit) = apply {
            this.specificActions[errorType] = action
        }

        /**
         * Menetapkan aksi default untuk semua error dalam kategori [ErrorCategory.OPERATIONAL].
         * @param action Blok kode (lambda) yang akan menerima pesan error default.
         * @return [Builder] untuk chaining.
         */
        fun setOnOperationalError(action: (message: String) -> Unit) = apply {
            this.categoryActions[ErrorCategory.OPERATIONAL] = action
        }

        /**
         * Menetapkan aksi default untuk semua error dalam kategori [ErrorCategory.SECURITY].
         * @param action Blok kode (lambda) yang akan menerima pesan error default.
         * @return [Builder] untuk chaining.
         */
        fun setOnSecurityError(action: (message: String) -> Unit) = apply {
            this.categoryActions[ErrorCategory.SECURITY] = action
        }

        /**
         * Menetapkan aksi default untuk semua error dalam kategori [ErrorCategory.SETUP].
         * @param action Blok kode (lambda) yang akan menerima pesan error default.
         * @return [Builder] untuk chaining.
         */
        fun setOnSetupError(action: (message: String) -> Unit) = apply {
            this.categoryActions[ErrorCategory.SETUP] = action
        }

        /**
         * Membangun instance [GeoValidator] dengan semua konfigurasi yang telah diatur.
         * Metode ini akan menyelesaikan semua aksi error berdasarkan prioritas (spesifik > kategori).
         * @return Instance [GeoValidator] yang siap digunakan.
         */
        fun build(): GeoValidator {
            requireNotNull(targetLatitude) { "Target latitude must be set." }
            requireNotNull(targetLongitude) { "Target longitude must be set." }

            val resolvedActions = mutableMapOf<ErrorType, () -> Unit>()
            ErrorType.values().forEach { errorType ->
                val finalAction: () -> Unit

                val specificAction = specificActions[errorType]
                val categoryAction = categoryActions[errorType.category]
                val message = defaultMessages[errorType] ?: "Terjadi error tidak dikenal."

                if (specificAction != null) {
                    // Prioritas 1: Gunakan aksi spesifik jika ada
                    finalAction = specificAction
                } else if (categoryAction != null) {
                    // Prioritas 2: Gunakan aksi kategori jika ada
                    finalAction = { categoryAction(message) }
                } else {
                    // Prioritas 3: Fallback jika tidak ada aksi yang didefinisikan
                    finalAction = { Log.e("GeoValidator", "No action defined for $errorType, using fallback.") }
                }

                resolvedActions[errorType] = finalAction
            }


            return GeoValidator(
                context = context.applicationContext,
                targetLatitude = targetLatitude!!,
                targetLongitude = targetLongitude!!,
                radius = radius,
                enableMockCheck = enableMockCheck,
                enableAdvancedValidation = enableAdvancedValidation,
                accuracyThreshold = accuracyThreshold,
                errorActions = resolvedActions
            )
        }
    }
}