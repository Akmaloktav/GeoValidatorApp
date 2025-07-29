package com.akmal.geoattendanceapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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

        // Konfigurasi GeoValidator dengan error handler yang menggunakan AlertDialog
        geoValidator = GeoValidator.Builder(this)
            .setTargetLocation(latitude = -5.3699466, longitude = 105.2720512)
            .setRadius(500.0) // Radius 500 meter
            .enableMockLocationCheck(true)
            .enableAdvancedValidation(true)
            .enableMockAppCheck(true)
            .setOnSecurityError { message ->
                // Peringatan keamanan (Mock & Unnatural) akan menampilkan dialog
                showSecurityAlert(message)
            }
            .setOnOperationalError { message ->
                // Error operasional (di luar radius, dll) akan menampilkan teks
                binding.tvResult.text = "Gagal: $message"
            }
            .setOnFailureAction(ErrorType.PERMISSION_MISSING) {
                binding.tvResult.text = "Gagal: Izin lokasi tidak ada."
            }
            .build()

        // Listener untuk tombol validasi manual
        binding.btnValidate.setOnClickListener {
            checkPermissionAndValidate()
        }

        // Lakukan validasi pertama kali secara otomatis saat Activity dibuat
        checkPermissionAndValidate()
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
                    is ValidationResult.Failure -> {
                        // Cukup panggil aksi yang sudah didefinisikan di Builder
                        result.action()
                    }
                }
            }
        }
    }

    /**
     * Fungsi helper untuk menampilkan AlertDialog peringatan keamanan.
     * Mirip dengan yang ada di AttendanceDetailActivity Anda.
     */
    private fun showSecurityAlert(message: String) {
        // Jika ada dialog lain yang sedang tampil, jangan tampilkan lagi
        if (isFinishing || isDestroyed) return

        AlertDialog.Builder(this)
            .setTitle("Peringatan Keamanan")
            .setMessage(message)
            .setPositiveButton("Mengerti") { dialog, _ ->
                dialog.dismiss()
                // Setelah dialog ditutup, tampilkan status terakhir
                binding.tvResult.text = "Siap untuk validasi berikutnya..."
            }
            .setCancelable(false)
            .show()
    }
}