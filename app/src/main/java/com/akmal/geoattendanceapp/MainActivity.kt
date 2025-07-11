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

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                performValidation()
            } else {
                binding.tvResult.text = "Izin lokasi ditolak. Tidak bisa melakukan validasi."
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ## LANGKAH 1: PERBARUI INISIALISASI GEOVALIDATOR ##
        geoValidator = GeoValidator.Builder(this)
            .setTargetLocation(latitude = -5.3699466, longitude = 105.2720512)
            .setRadius(500.0)
            .enableMockLocationCheck(true)
            .enableAdvancedValidation(true) // Aktifkan validasi lanjutan

            // Definisikan aksi untuk setiap kategori error
            .setOnSecurityError { message ->
                // Aksi untuk MOCK_LOCATION dan UNNATURAL_LOCATION
                binding.tvResult.text = "GAGAL:\n$message"
            }
            .setOnOperationalError { message ->
                // Aksi untuk OUTSIDE_GEOFENCE dan LOCATION_UNAVAILABLE
                binding.tvResult.text = "Gagal: $message"
            }
            // Definisikan aksi spesifik jika perlu (opsional)
            .setOnFailureAction(ErrorType.PERMISSION_MISSING) {
                binding.tvResult.text = "Gagal: Izin lokasi tidak ada."
            }
            .build()

        binding.btnValidate.setOnClickListener {
            checkPermissionAndValidate()
        }
    }

    private fun checkPermissionAndValidate() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                performValidation()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    private fun performValidation() {
        binding.tvResult.text = "Memulai validasi..."

        geoValidator.validate { result ->
            runOnUiThread {
                when (result) {
                    is ValidationResult.Success -> {
                        val lat = result.location.latitude
                        val lng = result.location.longitude
                        binding.tvResult.text = "SUKSES!\nLokasi Anda valid di:\nLat: $lat\nLng: $lng"
                    }

                    // ## LANGKAH 2: SEDERHANAKAN PENANGANAN FAILURE ##
                    // Cukup panggil aksi yang sudah kita definisikan di atas
                    is ValidationResult.Failure -> {
                        result.action()
                    }
                }
            }
        }
    }
}