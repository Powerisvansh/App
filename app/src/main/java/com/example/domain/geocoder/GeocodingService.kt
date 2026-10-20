package com.example.domain.geocoder

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.example.data.local.dao.GeocodeCacheDao
import com.example.data.local.entity.GeocodeCacheEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt

data class GeocodeResult(
    val placeName: String,
    val address: String
)

class GeocodingService(
    private val context: Context,
    private val cacheDao: GeocodeCacheDao
) {

    suspend fun getPlaceAndAddress(latitude: Double, longitude: Double): GeocodeResult =
        withContext(Dispatchers.IO) {
            // Bucket coordinates to ~3-4 decimal places (~10-50m resolution) for cache hit efficiency
            val roundedLat = (latitude * 1000.0).roundToInt() / 1000.0
            val roundedLon = (longitude * 1000.0).roundToInt() / 1000.0
            val key = "$roundedLat,$roundedLon"

            // 1. Check local DB cache
            try {
                val cached = cacheDao.getCachedGeocode(key)
                if (cached != null) {
                    return@withContext GeocodeResult(
                        placeName = cached.placeName,
                        address = cached.address
                    )
                }
            } catch (_: Exception) {}

            // 2. Perform Geocoding using Android framework
            var placeName = "Place near ${String.format(Locale.US, "%.4f, %.4f", latitude, longitude)}"
            var addressText = "Coordinates: ${String.format(Locale.US, "%.5f, %.5f", latitude, longitude)}"

            if (Geocoder.isPresent()) {
                try {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    val addresses: List<Address> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        suspendCancellableCoroutine { continuation ->
                            try {
                                geocoder.getFromLocation(latitude, longitude, 1) { results ->
                                    continuation.resume(results)
                                }
                            } catch (e: Exception) {
                                continuation.resume(emptyList())
                            }
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocation(latitude, longitude, 1) ?: emptyList()
                    }

                    if (addresses.isNotEmpty()) {
                        val addr = addresses[0]
                        val feature = addr.featureName
                        val thoroughfare = addr.thoroughfare
                        val subThoroughfare = addr.subThoroughfare
                        val subLocality = addr.subLocality
                        val locality = addr.locality
                        val subAdminArea = addr.subAdminArea

                        val isFeatureCodeOrNumber = feature?.all { it.isDigit() || it == '-' || it == '/' || it == '+' } ?: false

                        placeName = when {
                            !subLocality.isNullOrBlank() && !locality.isNullOrBlank() && subLocality != locality -> "$subLocality, $locality"
                            !locality.isNullOrBlank() -> locality
                            !subLocality.isNullOrBlank() -> subLocality
                            !feature.isNullOrBlank() && !isFeatureCodeOrNumber && feature != thoroughfare -> {
                                if (!locality.isNullOrBlank()) "$feature, $locality" else feature
                            }
                            !thoroughfare.isNullOrBlank() -> {
                                val street = listOfNotNull(subThoroughfare, thoroughfare).joinToString(" ")
                                if (!locality.isNullOrBlank()) "$street, $locality" else street
                            }
                            !subAdminArea.isNullOrBlank() -> subAdminArea
                            else -> "Location (${String.format(Locale.US, "%.4f, %.4f", latitude, longitude)})"
                        }

                        val fullAddressLines = mutableListOf<String>()
                        for (i in 0..addr.maxAddressLineIndex) {
                            val line = addr.getAddressLine(i)
                            if (!line.isNullOrBlank()) {
                                fullAddressLines.add(line)
                            }
                        }
                        if (fullAddressLines.isNotEmpty()) {
                            addressText = fullAddressLines.joinToString(", ")
                        } else {
                            addressText = listOfNotNull(thoroughfare, subLocality, locality, addr.adminArea)
                                .joinToString(", ")
                        }
                    }
                } catch (_: Exception) {
                    // Network or Geocoder service unavailable, keep default
                }
            }

            val result = GeocodeResult(placeName = placeName, address = addressText)
            try {
                cacheDao.insertCache(
                    GeocodeCacheEntity(
                        coordinateKey = key,
                        placeName = result.placeName,
                        address = result.address
                    )
                )
            } catch (_: Exception) {}

            return@withContext result
        }
}
