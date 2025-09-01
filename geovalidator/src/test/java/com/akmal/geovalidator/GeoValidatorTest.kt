package com.akmal.geovalidator

import android.content.Context
import android.location.Location
import android.os.Build
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
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
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.R])
fun `isMockLocation on old SDK returns true when from mock provider`() {
    // Arrange
    val geoValidator = GeoValidator.Builder(mockContext).setTargetLocation(0.0, 0.0).build()
    whenever(mockLocation.isFromMockProvider).thenReturn(true) // Cukup ini saja

    // Act
    val result = geoValidator.isMockLocation(mockLocation)

    // Assert
    assertEquals(true, result)
}

    @Test
    @Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.R]) // Mensimulasikan Android 11 (sebelum S)
    fun `isMockLocation on old SDK returns false when not from mock provider`() {
        // Arrange
        val geoValidator = GeoValidator.Builder(mockContext).setTargetLocation(0.0, 0.0).build()
        whenever(mockLocation.isFromMockProvider).thenReturn(false)

        // Act
        val result = geoValidator.isMockLocation(mockLocation)

        // Assert
        assertEquals(false, result)
    }

    @Test
    @Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.S])
    fun `isMockLocation on new SDK returns true when isMock is true`() {
        // Arrange
        val geoValidator = GeoValidator.Builder(mockContext).setTargetLocation(0.0, 0.0).build()
        whenever(mockLocation.isMock).thenReturn(true) // Cukup ini saja

        // Act
        val result = geoValidator.isMockLocation(mockLocation)

        // Assert
        assertEquals(true, result)
    }

    @Test
    @Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.S]) // Mensimulasikan Android 12 (S)
    fun `isMockLocation on new SDK returns false when isMock is false`() {
        // Arrange
        val geoValidator = GeoValidator.Builder(mockContext).setTargetLocation(0.0, 0.0).build()
        whenever(mockLocation.isMock).thenReturn(false)

        // Act
        val result = geoValidator.isMockLocation(mockLocation)

        // Assert
        assertEquals(false, result)
    }

    @Test
    fun `builder correctly builds instance with all configurations`() {
        // 1. Arrange
        val testRadius = 150.5
        val testLat = -7.257472
        val testLng = 112.752088
        val mockCheck = true
        val advancedValidation = true
        val mockAppCheck = true

        // 2. Act
        val geoValidator = GeoValidator.Builder(mockContext)
            .setTargetLocation(testLat, testLng)
            .setRadius(testRadius)
            .enableMockLocationCheck(mockCheck)
            .enableAdvancedValidation(advancedValidation)
            .enableMockAppCheck(mockAppCheck)
            .build()

        // 3. Assert
        // Gunakan refleksi untuk mengakses field private dan memverifikasi nilainya
        val radiusField = geoValidator::class.java.getDeclaredField("radius")
        radiusField.isAccessible = true
        assertEquals(testRadius, radiusField.getDouble(geoValidator), 0.0)

        val latField = geoValidator::class.java.getDeclaredField("targetLatitude")
        latField.isAccessible = true
        assertEquals(testLat, latField.getDouble(geoValidator), 0.0)

        val mockCheckField = geoValidator::class.java.getDeclaredField("enableMockCheck")
        mockCheckField.isAccessible = true
        assertEquals(mockCheck, mockCheckField.getBoolean(geoValidator))

        val advancedValidationField = geoValidator::class.java.getDeclaredField("enableAdvancedValidation")
        advancedValidationField.isAccessible = true
        assertEquals(advancedValidation, advancedValidationField.getBoolean(geoValidator))

        // Verifikasi untuk properti baru yang kita tambahkan
        val mockAppCheckField = geoValidator::class.java.getDeclaredField("enableMockAppCheck")
        mockAppCheckField.isAccessible = true
        assertEquals(mockAppCheck, mockAppCheckField.getBoolean(geoValidator))
    }
}