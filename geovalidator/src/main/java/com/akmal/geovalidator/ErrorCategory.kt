package com.akmal.geovalidator

/**
 * Enum untuk mengelompokkan tipe error ke dalam kategori yang lebih umum.
 * Ini memungkinkan penanganan error secara berkelompok.
 */
enum class ErrorCategory {
    /**
     * Error terkait keamanan dan potensi kecurangan (misal: fake GPS).
     */
    SECURITY,

    /**
     * Error operasional biasa (misal: di luar area, lokasi tidak tersedia).
     */
    OPERATIONAL,

    /**
     * Error yang membutuhkan penanganan khusus oleh pengguna (misal: izin belum diberikan).
     */
    SETUP
}