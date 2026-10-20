package com.example.domain.geocoder

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import com.example.data.local.dao.GeocodeCacheDao
import com.example.data.local.entity.GeocodeCacheEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
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

    // Offline spatial gazetteer for fallback when offline or in emulator
    private data class TownEntry(val name: String, val districtState: String, val lat: Double, val lon: Double)

    private val offlineTowns = listOf(
        TownEntry("Nilokheri", "Karnal, Haryana", 29.9324, 76.9180),
        TownEntry("Taraori", "Karnal, Haryana", 29.8037, 76.9317),
        TownEntry("Karnal", "Haryana", 29.6857, 76.9905),
        TownEntry("Kurukshetra", "Haryana", 29.9695, 76.8783),
        TownEntry("Panipat", "Haryana", 29.3909, 76.9635),
        TownEntry("Ambala", "Haryana", 30.3782, 76.7767),
        TownEntry("Kaithal", "Haryana", 29.8015, 76.3996),
        TownEntry("Chandigarh", "India", 30.7333, 76.7794),
        TownEntry("New Delhi", "Delhi, India", 28.6139, 77.2090),
        TownEntry("Gurugram", "Haryana", 28.4595, 77.0266),
        TownEntry("Noida", "Uttar Pradesh", 28.5355, 77.3910),
        TownEntry("Jaipur", "Rajasthan", 26.9124, 75.7873),
        TownEntry("Mumbai", "Maharashtra", 19.0760, 72.8777),
        TownEntry("Bengaluru", "Karnataka", 12.9716, 77.5946),
        TownEntry("San Francisco", "California, USA", 37.7749, -122.4194),
        TownEntry("New York", "New York, USA", 40.7128, -74.0060),
        TownEntry("London", "UK", 51.5074, -0.1278)
    )

    suspend fun getPlaceAndAddress(latitude: Double, longitude: Double): GeocodeResult =
        withContext(Dispatchers.IO) {
            // Bucket coordinates to ~3-4 decimal places (~30-50m resolution) for cache hit efficiency
            val roundedLat = (latitude * 1000.0).roundToInt() / 1000.0
            val roundedLon = (longitude * 1000.0).roundToInt() / 1000.0
            val key = "$roundedLat,$roundedLon"

            // 1. Check local DB cache
            try {
                val cached = cacheDao.getCachedGeocode(key)
                if (cached != null && cached.placeName.isNotBlank() && !cached.placeName.startsWith("Location (")) {
                    return@withContext GeocodeResult(
                        placeName = cached.placeName,
                        address = cached.address
                    )
                }
            } catch (_: Exception) {}

            var detectedPlaceName: String? = null
            var detectedAddress: String? = null

            // 2. Primary: Android System Geocoder
            if (Geocoder.isPresent()) {
                try {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    val addresses: List<Address> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        suspendCancellableCoroutine { continuation ->
                            try {
                                geocoder.getFromLocation(latitude, longitude, 1) { results ->
                                    continuation.resume(results)
                                }
                            } catch (_: Exception) {
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

                        val isCodeOrNum = feature?.all { it.isDigit() || it == '-' || it == '/' || it == '+' } ?: false

                        detectedPlaceName = when {
                            !subLocality.isNullOrBlank() && !locality.isNullOrBlank() && subLocality != locality -> "$subLocality, $locality"
                            !locality.isNullOrBlank() -> locality
                            !subLocality.isNullOrBlank() -> subLocality
                            !feature.isNullOrBlank() && !isCodeOrNum && feature != thoroughfare -> {
                                if (!locality.isNullOrBlank()) "$feature, $locality" else feature
                            }
                            !thoroughfare.isNullOrBlank() -> {
                                val st = listOfNotNull(subThoroughfare, thoroughfare).joinToString(" ")
                                if (!locality.isNullOrBlank()) "$st, $locality" else st
                            }
                            !subAdminArea.isNullOrBlank() -> subAdminArea
                            else -> null
                        }

                        val fullAddressLines = mutableListOf<String>()
                        for (i in 0..addr.maxAddressLineIndex) {
                            val line = addr.getAddressLine(i)
                            if (!line.isNullOrBlank()) {
                                fullAddressLines.add(line)
                            }
                        }
                        if (fullAddressLines.isNotEmpty()) {
                            detectedAddress = fullAddressLines.joinToString(", ")
                        } else {
                            detectedAddress = listOfNotNull(thoroughfare, subLocality, locality, addr.adminArea, addr.countryName)
                                .joinToString(", ")
                        }
                    }
                } catch (_: Exception) {
                    // System geocoder failed or offline
                }
            }

            // 3. Secondary: HTTP Reverse Geocoding (OpenStreetMap Nominatim)
            if (detectedPlaceName.isNullOrBlank()) {
                try {
                    val urlStr = "https://nominatim.openstreetmap.org/reverse?format=json&lat=$latitude&lon=$longitude&zoom=18&addressdetails=1"
                    val url = URL(urlStr)
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 3000
                        readTimeout = 3000
                        setRequestProperty("User-Agent", "DailyTrackAndroidApp/1.0")
                    }

                    if (conn.responseCode == 200) {
                        val reader = BufferedReader(InputStreamReader(conn.inputStream))
                        val response = reader.readText()
                        reader.close()

                        val json = JSONObject(response)
                        val addressObj = json.optJSONObject("address")
                        if (addressObj != null) {
                            val town = addressObj.optString("town", "")
                            val city = addressObj.optString("city", "")
                            val village = addressObj.optString("village", "")
                            val suburb = addressObj.optString("suburb", "")
                            val neighbourhood = addressObj.optString("neighbourhood", "")
                            val district = addressObj.optString("county", addressObj.optString("state_district", ""))
                            val state = addressObj.optString("state", "")

                            val locality = when {
                                town.isNotBlank() -> town
                                city.isNotBlank() -> city
                                village.isNotBlank() -> village
                                else -> ""
                            }
                            val subLoc = when {
                                neighbourhood.isNotBlank() -> neighbourhood
                                suburb.isNotBlank() -> suburb
                                else -> ""
                            }

                            detectedPlaceName = when {
                                subLoc.isNotBlank() && locality.isNotBlank() && subLoc != locality -> "$subLoc, $locality"
                                locality.isNotBlank() -> locality
                                subLoc.isNotBlank() -> subLoc
                                district.isNotBlank() -> district
                                else -> null
                            }
                            detectedAddress = json.optString("display_name", "")
                        }
                    }
                    conn.disconnect()
                } catch (_: Exception) {}
            }

            // 4. Tertiary: Offline Nearest Town Gazetteer Fallback
            if (detectedPlaceName.isNullOrBlank()) {
                var closestTown: TownEntry? = null
                var minDistanceMeters = Float.MAX_VALUE
                val distanceResult = FloatArray(1)

                for (town in offlineTowns) {
                    Location.distanceBetween(latitude, longitude, town.lat, town.lon, distanceResult)
                    if (distanceResult[0] < minDistanceMeters) {
                        minDistanceMeters = distanceResult[0]
                        closestTown = town
                    }
                }

                if (closestTown != null && minDistanceMeters < 25000f) { // Within 25 km
                    detectedPlaceName = closestTown.name
                    val distKm = (minDistanceMeters / 1000f).roundToInt()
                    detectedAddress = if (distKm <= 2) {
                        "${closestTown.name}, ${closestTown.districtState}"
                    } else {
                        "Near ${closestTown.name} (~${distKm} km), ${closestTown.districtState}"
                    }
                } else {
                    detectedPlaceName = "Visited Location"
                    detectedAddress = String.format(Locale.US, "Coordinates: %.4f, %.4f", latitude, longitude)
                }
            }

            val finalPlaceName = detectedPlaceName ?: "Visited Location"
            val finalAddress = detectedAddress ?: String.format(Locale.US, "Location: %.4f, %.4f", latitude, longitude)
            val result = GeocodeResult(placeName = finalPlaceName, address = finalAddress)

            // Cache result in SQLite
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
