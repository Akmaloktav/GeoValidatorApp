package com.akmal.geovalidator

import android.content.Context
import android.location.Location
import org.junit.Assert.*

import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class GeoValidatorTest {

    private lateinit var mockContext: Context
    private lateinit var mockLocation: Location

    @Before
    fun setUp() {
        mockContext = mock()
        mockLocation = mock()
        whenever(mockContext.applicationContext).thenReturn(mockContext)
    }

    @Test
    fun `isWithinGeofence returns true when location is inside radius`() {
        val targetLat = -6.1753924
        val targetLng = 106.8271528

        val userLat = -6.1753000
        val userLng = 106.8270000
        whenever(mockLocation.latitude).thenReturn(userLat)
        whenever(mockLocation.longitude).thenReturn(userLng)

        val geoValidator = GeoValidator.Builder(mockContext)
            .setTargetLocation(targetLat, targetLng)
            .setRadius(500.0) // Radius 500 meter
            .build()

        val result = geoValidator.isWithinGeofence(mockLocation)

        assertEquals(true, result)
    }

    @Test
    fun `isWithinGeofence returns false when location is outside radius`() {
        val targetLat = -6.1753924
        val targetLng = 106.8271528

        val userLat = -6.194726
        val userLng = 106.822695
        whenever(mockLocation.latitude).thenReturn(userLat)
        whenever(mockLocation.longitude).thenReturn(userLng)

        val geoValidator = GeoValidator.Builder(mockContext)
            .setTargetLocation(targetLat, targetLng)
            .setRadius(500.0)
            .build()

        val result = geoValidator.isWithinGeofence(mockLocation)

        assertEquals(false, result)
    }

    @Test
    fun `isMockLocation returns true when location is from mock provider`() {
        // 1. Arrange
        // Kita atur agar mockLocation dianggap sebagai lokasi palsu.
        // Kita gunakan isFromMockProvider karena ini yang paling umum.
        whenever(mockLocation.isFromMockProvider).thenReturn(true)

        // Kita hanya butuh instance GeoValidator, konfigurasinya tidak terlalu penting di sini
        val geoValidator = GeoValidator.Builder(mockContext)
            .setTargetLocation(0.0, 0.0)
            .build()

        // 2. Act
        val result = geoValidator.isMockLocation(mockLocation)

        // 3. Assert
        assertEquals(true, result)
    }

    @Test
    fun `isMockLocation returns false when location is not from mock provider`() {
        // 1. Arrange
        // Kita atur agar mockLocation dianggap sebagai lokasi asli.
        whenever(mockLocation.isFromMockProvider).thenReturn(false)

        val geoValidator = GeoValidator.Builder(mockContext)
            .setTargetLocation(0.0, 0.0)
            .build()

        // 2. Act
        val result = geoValidator.isMockLocation(mockLocation)

        // 3. Assert
        assertEquals(false, result)
    }

    @Test
    fun `builder correctly builds instance with given configuration`() {
        // 1. Arrange
        val testRadius = 150.5
        val testLat = -7.257472
        val testLng = 112.752088

        // 2. Act
        val geoValidator = GeoValidator.Builder(mockContext)
            .setTargetLocation(testLat, testLng)
            .setRadius(testRadius)
            .enableMockLocationCheck(true)
            .build()

        // 3. Assert
        // Kita gunakan refleksi untuk mengakses field private dan memverifikasi nilainya
        val radiusField = geoValidator::class.java.getDeclaredField("radius")
        radiusField.isAccessible = true
        val actualRadius = radiusField.getDouble(geoValidator)
        assertEquals(testRadius, actualRadius, 0.0)

        val latField = geoValidator::class.java.getDeclaredField("targetLatitude")
        latField.isAccessible = true
        val actualLat = latField.getDouble(geoValidator)
        assertEquals(testLat, actualLat, 0.0)
    }
}