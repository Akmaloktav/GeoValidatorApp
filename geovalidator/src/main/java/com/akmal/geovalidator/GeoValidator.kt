package com.akmal.geovalidator

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
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
 * @property enableMockAppCheck Jika true, melakukan pengecekan apakah ada 'aplikasi lokasi palsu' yang diatur di Opsi Pengembang.
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
    private val enableMockAppCheck: Boolean,
    private val errorActions: Map<ErrorType, () -> Unit>
) {

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
        if (enableMockAppCheck && isMockLocationAppSet()) {
            handleFailure(ErrorType.MOCK_LOCATION_APP_SET, callback)
            return
        }

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

                if (enableAdvancedValidation) {
                    // Selalu jalankan verifikasi 2 langkah jika mock check dasar gagal
                    Log.d("GeoValidator", "Mock check dasar lolos. Memulai verifikasi 2 langkah (analisis statis).")
                    performSecondCheck(location, callback)
                } else {
                    // Jika validasi lanjutan tidak aktif, langsung ke geofence
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

    /**
     * Melakukan verifikasi dua langkah yang cerdas untuk mendeteksi anomali lokasi.
     * Fungsi ini mengambil lokasi kedua setelah jeda waktu, lalu membandingkannya dengan lokasi pertama.
     * Validasi akan gagal jika data lokasi (lat, lon, akurasi) identik tetapi timestamp-nya berbeda,
     * yang merupakan indikator kuat dari mock location yang aktif.
     *
     * @param firstLocation Lokasi pertama yang akan dijadikan acuan perbandingan.
     * @param callback Fungsi yang akan dipanggil dengan hasil akhir validasi.
     */
    @SuppressLint("MissingPermission")
    private fun performSecondCheck(firstLocation: Location, callback: (ValidationResult) -> Unit) {
        Handler(Looper.getMainLooper()).postDelayed({
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
                .addOnSuccessListener { secondLocation ->
                    if (secondLocation == null) {
                        handleFailure(ErrorType.LOCATION_UNAVAILABLE, callback)
                        return@addOnSuccessListener
                    }

                    // Memeriksa apakah semua data lokasi (lat, lon, akurasi) sama persis.
                    val isDataIdentical = firstLocation.latitude == secondLocation.latitude &&
                            firstLocation.longitude == secondLocation.longitude &&
                            firstLocation.accuracy == secondLocation.accuracy

                    // Memeriksa apakah waktu (timestamp) dari lokasi juga sama.
                    val isTimestampIdentical = firstLocation.time == secondLocation.time

                    // GAGAL HANYA JIKA: Data lokasi sama persis TETAPI timestamp-nya berbeda.
                    // Ini adalah indikator kuat dari aplikasi mock yang terus menghasilkan data statis.
                    if (isDataIdentical && !isTimestampIdentical) {
                        Log.w("GeoValidator", "Verifikasi gagal. Terdeteksi data lokasi statis dengan timestamp baru.")
                        handleFailure(ErrorType.UNNATURAL_LOCATION_DETECTED, callback)
                    } else {
                        // Lolos jika:
                        // 1. Data lokasi berfluktuasi secara alami.
                        // 2. Data dan timestamp sama (kemungkinan besar lokasi cache dari pengguna asli yang diam).
                        Log.d("GeoValidator", "Verifikasi berhasil. Terdeteksi fluktuasi alami atau lokasi cache yang valid.")
                        performGeofenceCheck(firstLocation, callback)
                    }
                }
                .addOnFailureListener { handleFailure(ErrorType.LOCATION_UNAVAILABLE, callback) }
        }, 2000) // Jeda 2 detik
    }


    /**
     * Memeriksa apakah lokasi pengguna berada di dalam area geofence yang telah ditentukan.
     * Jika lokasi berada di luar radius, validasi akan gagal dengan [ErrorType.OUTSIDE_GEOFENCE].
     *
     * @param location Lokasi pengguna saat ini yang akan divalidasi.
     * @param callback Fungsi yang akan dipanggil dengan hasil akhir validasi.
     */
    private fun performGeofenceCheck(location: Location, callback: (ValidationResult) -> Unit) {
        if (!isWithinGeofence(location)) {
            handleFailure(ErrorType.OUTSIDE_GEOFENCE, callback)
            return
        }
        callback(ValidationResult.Success(location))
    }

    /**
     * Fungsi helper untuk memeriksa apakah izin [android.Manifest.permission.ACCESS_FINE_LOCATION]
     * telah diberikan oleh pengguna.
     *
     * @return `true` jika izin diberikan, `false` jika sebaliknya.
     */
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

    /**
     * Menghitung jarak dari lokasi saat ini ke lokasi target menggunakan formula Haversine
     * dan membandingkannya dengan radius yang diizinkan.
     * Dibuat `internal` untuk kemudahan pengujian.
     *
     * @param location Lokasi yang akan diperiksa.
     * @return `true` jika jarak kurang dari atau sama dengan radius, `false` jika sebaliknya.
     */
    internal fun isWithinGeofence(location: Location): Boolean {
        val distance = calculateDistance(
            lat1 = location.latitude,
            lon1 = location.longitude,
            lat2 = targetLatitude,
            lon2 = targetLongitude
        )
        return distance <= radius
    }

    /**
     * Memeriksa apakah sebuah [Location] berasal dari penyedia lokasi palsu (mock provider).
     * Menangani perbedaan API untuk kompatibilitas ke belakang (sebelum dan sesudah Android S).
     * Dibuat `internal` untuk kemudahan pengujian.
     *
     * @param location Objek Lokasi yang akan diperiksa.
     * @return `true` jika lokasi ditandai sebagai mock, `false` jika sebaliknya.
     */
    internal fun isMockLocation(location: Location): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) location.isMock else @Suppress("DEPRECATION") location.isFromMockProvider
    }

    /**
     * Memeriksa apakah ada aplikasi yang telah ditetapkan sebagai "aplikasi lokasi palsu"
     * di dalam Opsi Pengembang. Ini adalah indikator yang jauh lebih kuat daripada
     * hanya memeriksa apakah Opsi Pengembang aktif.
     */
    private fun isMockLocationAppSet(): Boolean {
        return try {
            val mockLocation = Settings.Secure.getString(context.contentResolver, Settings.Secure.ALLOW_MOCK_LOCATION)
            // Mengembalikan true jika ada nama paket aplikasi yang diatur (tidak null dan bukan "0")
            mockLocation != null && mockLocation != "0"
        } catch (e: Exception) {
            // Jika terjadi error saat membaca pengaturan, anggap tidak aktif demi keamanan.
            false
        }
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
        private var enableMockAppCheck: Boolean = true

        private val specificActions = mutableMapOf<ErrorType, () -> Unit>()
        private val categoryActions = mutableMapOf<ErrorCategory, (message: String) -> Unit>()
        private val defaultMessages = mapOf(
            ErrorType.PERMISSION_MISSING to "Izin lokasi dibutuhkan untuk melanjutkan.",
            ErrorType.LOCATION_UNAVAILABLE to "Gagal mendapatkan lokasi Anda. Pastikan GPS aktif.",
            ErrorType.MOCK_LOCATION_DETECTED to "Penggunaan lokasi palsu tidak diizinkan.",
            ErrorType.OUTSIDE_GEOFENCE to "Anda berada di luar area yang ditentukan.",
            ErrorType.UNNATURAL_LOCATION_DETECTED to "Terdeteksi data lokasi yang tidak wajar.",
            ErrorType.MOCK_LOCATION_APP_SET to "Aplikasi lokasi palsu terdeteksi di pengaturan perangkat Anda."
        )

        fun setTargetLocation(latitude: Double, longitude: Double) = apply { this.targetLatitude = latitude; this.targetLongitude = longitude }
        fun setRadius(radius: Double) = apply { this.radius = radius }
        fun enableMockLocationCheck(enabled: Boolean) = apply { this.enableMockCheck = enabled }
        fun enableAdvancedValidation(enabled: Boolean) = apply { this.enableAdvancedValidation = enabled }
        fun enableMockAppCheck(enabled: Boolean) = apply { this.enableMockAppCheck = enabled }

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
                enableMockAppCheck = enableMockAppCheck,
                errorActions = resolvedActions
            )
        }
    }
}