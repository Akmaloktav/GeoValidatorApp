# GeoValidator 🛡️📍

Sebuah library Android (Kotlin) yang menyediakan fungsionalitas validasi lokasi secara komprehensif untuk mengamankan sistem absensi berbasis Geolocation.

---

## Deskripsi

**GeoValidator** membungkus logika validasi lokasi yang kompleks ke dalam komponen yang sederhana dan mudah digunakan. Tujuannya adalah untuk memisahkan logika keamanan dari logika bisnis aplikasi, sehingga developer bisa dengan cepat mengimplementasikan sistem absensi yang aman tanpa harus menangani seluk-beluk API lokasi dan celah keamanannya.

Library ini menyediakan verifikasi berlapis, mulai dari pengecekan dasar hingga analisis perilaku data GPS untuk mendeteksi anomali.

---

## Fitur Utama

* **Validasi Geofence:** Memastikan lokasi pengguna berada di dalam radius area yang ditentukan.
* **Deteksi Lokasi Palsu (Dasar):** Menggunakan flag `isMock` bawaan sistem Android.
* **Deteksi Lokasi Palsu (Lanjutan):** Melakukan verifikasi 2 langkah untuk mendeteksi data GPS yang bersifat statis dan tidak wajar.
* **Penanganan Error Terkategori:** Mengelompokkan error ke dalam kategori (`SECURITY`, `OPERATIONAL`, `SETUP`) untuk penanganan yang lebih bersih.
* **Konfigurasi Fleksibel:** Menggunakan Builder Pattern untuk setup yang mudah dibaca dan diatur.

---

## Instalasi

### Langkah 1: Tambahkan JitPack ke Repositori Proyek
Tambahkan URL JitPack di file setting gradle Anda:

**settings.gradle.kts**
```kotlin
dependencyResolutionManagement {
    repositories {
        //...
        maven { url = uri("https://jitpack.io") }
    }
}
```

**gradle**
```kotlin
dependencyResolutionManagement {
    repositories {
        //...
        maven { url 'https://jitpack.io' }
    }
}
```

### Langkah 2: Tambahkan Dependensi
Tambahkan baris berikut ke file build gradle pada level modul aplikasi Anda(misal: 2.0.0).

**build.gradle.kts**
```kotlin
dependencies {
    implementation("com.github.Akmaloktav:GeoValidatorApp:Tag")
}
```

**gradle**
```kotlin
dependencies {
    implementation 'com.github.Akmaloktav:GeoValidatorApp:Tag'
}
```

---

## Contoh Penggunaan Cepat
Berikut adalah contoh lengkap cara menginisialisasi dan menggunakan GeoValidator di dalam sebuah Activity.

### Inisialisasi GeoValidator
Di sini Anda mengatur semua konfigurasi dan aksi penanganan error.

```kotlin
// Inisialisasi validator dan konfigurasikan aksi penanganan error
val geoValidator = GeoValidator.Builder(this)
    .setTargetLocation(latitude = -6.1753, longitude = 106.8271) // Contoh: Monas
    .setRadius(500.0) // Radius 500 meter
    .enableAdvancedValidation(true) // Aktifkan keamanan lapis kedua
    // Atur aksi untuk setiap kategori error
    .setOnSecurityError { message -> 
        Toast.makeText(this, "KEAMANAN: $message", Toast.LENGTH_LONG).show() 
    }
    .setOnOperationalError { message -> 
        Toast.makeText(this, "OPERASIONAL: $message", Toast.LENGTH_SHORT).show() 
    }
    .build()
```
### Menjalankan Validasi
Setelah diinisialisasi, panggil metode .validate() untuk memulai proses pengecekan. Metode ini bersifat asynchronous dan akan mengembalikan hasilnya melalui callback.

```kotlin
// Contoh membungkusnya dalam fungsi
fun startValidation() {
    geoValidator.validate { result ->
        runOnUiThread {
            when (result) {
                // Jika sukses, lanjutkan logika bisnis Anda
                is ValidationResult.Success -> {
                    val location = result.location
                    Toast.makeText(this, "VALIDASI SUKSES di ${location.latitude}", Toast.LENGTH_SHORT).show()
                    // Lakukan absensi...
                }
                // Jika gagal, cukup jalankan aksi yang sudah disiapkan
                is ValidationResult.Failure -> {
                    result.action()
                }
            }
        }
    }
}
```
PENTING: Fungsi geoValidator.validate() bergantung pada akses lokasi dari perangkat. Oleh karena itu, fungsi ini hanya boleh dipanggil SETELAH Anda memastikan izin ACCESS_FINE_LOCATION telah diberikan oleh pengguna. Memanggil .validate() sebelum izin dikonfirmasi akan menyebabkan library mengembalikan error PERMISSION_MISSING. Pola di bawah ini adalah cara yang aman dan direkomendasikan untuk mencegah hal tersebut.
```kotlin
contoh eksekusi di dalam launcher
private val requestPermissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            // Izin baru saja diberikan, sekarang aman untuk memulai validasi
            startValidation() 
        } else {
            // Tangani kasus jika pengguna menolak izin
            ...
        }
    }

contoh eksekusi ketika pengecekan lokasi
fun checkLocationPermission() {
    // 1. cek izin terlebih dahulu
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
        // izin SUDAH ADA panggil fungsi
        startValidation()
    } else {
        requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}
```

---

## Opsi Konfigurasi (Builder)
| Metode | Deskripsi |
| --- | --- |
| `setTargetLocation(lat, lon)` | **Wajib.** Mengatur titik pusat geofence. |
| `setRadius(meter)` | Mengatur radius toleransi dalam meter. Default: `100.0`. |
| `enableMockCheck(bool)` | Mengaktifkan/menonaktifkan deteksi `isMock`. Default: `true`. |
| `enableAdvancedValidation(bool)` | Mengaktifkan/menonaktifkan verifikasi 2 langkah. Default: `false`. |
| `setAccuracyThreshold(float)` | Mengatur ambang batas akurasi untuk memicu verifikasi lanjutan. Default: `5.0f`. |
| `setOnFailureAction(type, action)` | Menetapkan aksi spesifik untuk satu `ErrorType`, menjadi prioritas utama. |
| `setOnSecurityError(action)` | Menetapkan aksi untuk semua error kategori `SECURITY`. |
| `setOnOperationalError(action)` | Menetapkan aksi untuk semua error kategori `OPERATIONAL`. |
| `setOnSetupError(action)`| Menetapkan aksi untuk semua error kategori `SETUP`. |

---

## Lisensi
Proyek ini dilisensikan di bawah MIT License. Lihat file LICENSE untuk detailnya.
