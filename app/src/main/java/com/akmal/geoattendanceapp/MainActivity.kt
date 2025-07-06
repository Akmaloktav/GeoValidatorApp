package com.akmal.geoattendanceapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.akmal.geoattendanceapp.databinding.ActivityMainBinding
import com.akmal.geovalidator.ErrorType
import com.akmal.geovalidator.GeoValidator
import com.akmal.geovalidator.ValidationResult

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var geoValidator: GeoValidator

    // Menyiapkan launcher untuk meminta izin lokasi
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // Jika izin diberikan, langsung jalankan validasi
                performValidation()
            } else {
                // Jika izin ditolak, tampilkan pesan di TextView
                binding.tvResult.text = "Izin lokasi ditolak. Tidak bisa melakukan validasi."
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inisialisasi GeoValidator menggunakan Builder dari library kita
        // Ganti koordinat ini dengan lokasi target Anda (contoh: Monas, Jakarta)
        geoValidator = GeoValidator.Builder(this)
            .setTargetLocation(latitude = -5.3699466, longitude = 105.2720512)
            .setRadius(500.0) // Radius 500 meter
            .enableMockLocationCheck(true)
            .build()

        // Atur listener untuk tombol
        binding.btnValidate.setOnClickListener {
            checkPermissionAndValidate()
        }
    }

    private fun checkPermissionAndValidate() {
        when {
            // Cek apakah izin sudah diberikan
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Jika sudah, langsung validasi
                performValidation()
            }
            else -> {
                // Jika belum, minta izin
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    private fun performValidation() {
        binding.tvResult.text = "Memulai validasi..."

        // Panggil fungsi validate dari library kita!
        geoValidator.validate { result ->
            // Penting: Update UI harus berjalan di Main Thread
            runOnUiThread {
                when (result) {
                    // Kasus jika validasi SUKSES
                    is ValidationResult.Success -> {
                        val lat = result.location.latitude
                        val lng = result.location.longitude
                        binding.tvResult.text = "SUKSES!\nLokasi Anda valid di:\nLat: $lat\nLng: $lng"
                    }
                    // Kasus jika validasi GAGAL
                    is ValidationResult.Failure -> {
                        val errorText = when (result.errorType) {
                            ErrorType.PERMISSION_MISSING -> "Gagal: Izin lokasi tidak ada."
                            ErrorType.LOCATION_UNAVAILABLE -> "Gagal: Tidak bisa mendapatkan lokasi. Pastikan GPS aktif."
                            ErrorType.MOCK_LOCATION_DETECTED -> "GAGAL:\nTERDETEKSI LOKASI PALSU!"
                            ErrorType.OUTSIDE_GEOFENCE -> "Gagal: Anda berada di luar area yang ditentukan."
                            ErrorType.UNNATURAL_LOCATION_DETECTED -> "Verifikasi gagal, data lokasi tidak wajar."
                        }
                        binding.tvResult.text = errorText
                    }
                }
            }
        }
    }
}