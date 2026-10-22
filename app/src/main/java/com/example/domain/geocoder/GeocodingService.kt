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
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.math.roundToInt

data class GeocodeResult(
    val placeName: String,
    val address: String,
    val roadName: String = ""
)

class GeocodingService(
    private val context: Context,
    private val cacheDao: GeocodeCacheDao
) {

    // Fast in-memory cache to ensure timeline never falls back to placeholder strings
    private val memoryCache = ConcurrentHashMap<String, GeocodeResult>()

    private data class TownEntry(
        val name: String,
        val districtState: String,
        val primaryRoad: String,
        val lat: Double,
        val lon: Double
    )

    private val offlineTowns = listOf(
        TownEntry("Nilokheri", "Karnal, Haryana", "GT Road (NH 44)", 29.9324, 76.9180),
        TownEntry("Taraori", "Karnal, Haryana", "GT Road (NH 44)", 29.8037, 76.9317),
        TownEntry("Karnal", "Haryana", "Mall Road / GT Road (NH 44)", 29.6857, 76.9905),
        TownEntry("Kurukshetra", "Haryana", "Pipli Road / GT Road (NH 44)", 29.9695, 76.8783),
        TownEntry("Pipli", "Kurukshetra, Haryana", "GT Road (NH 44)", 29.9745, 76.9186),
        TownEntry("Shahbad", "Kurukshetra, Haryana", "GT Road (NH 44)", 30.1683, 76.8703),
        TownEntry("Gharaunda", "Karnal, Haryana", "GT Road (NH 44)", 29.5410, 76.9715),
        TownEntry("Panipat", "Haryana", "GT Road (NH 44)", 29.3909, 76.9635),
        TownEntry("Samalkha", "Panipat, Haryana", "GT Road (NH 44)", 29.2384, 77.0135),
        TownEntry("Sonipat", "Haryana", "GT Road / Murthal Road", 28.9931, 77.0151),
        TownEntry("Indri", "Karnal, Haryana", "Indri-Karnal Road", 29.8804, 77.0600),
        TownEntry("Assandh", "Karnal, Haryana", "Karnal-Assandh Road", 29.5242, 76.6025),
        TownEntry("Nissing", "Karnal, Haryana", "Kaithal-Karnal Road", 29.6582, 76.8252),
        TownEntry("Ladwa", "Kurukshetra, Haryana", "Ladwa-Karnal Road", 29.9967, 77.0457),
        TownEntry("Pehowa", "Kurukshetra, Haryana", "Ambala-Hisar Road", 29.9806, 76.5815),
        TownEntry("Ambala Cantt", "Haryana", "GT Road (NH 44)", 30.3547, 76.8378),
        TownEntry("Ambala", "Haryana", "Ambala-Delhi Highway", 30.3782, 76.7767),
        TownEntry("Kaithal", "Haryana", "Kaithal-Karnal Road", 29.8015, 76.3996),
        TownEntry("Chandigarh", "India", "Madhya Marg / Himalayan Expressway", 30.7333, 76.7794),
        TownEntry("New Delhi", "Delhi, India", "Ring Road / NH 44", 28.6139, 77.2090),
        TownEntry("Gurugram", "Haryana", "Delhi-Jaipur Expressway (NH 48)", 28.4595, 77.0266),
        TownEntry("Noida", "Uttar Pradesh", "Noida Expressway", 28.5355, 77.3910),
        TownEntry("Jaipur", "Rajasthan", "Tonk Road / NH 48", 26.9124, 75.7873),
        TownEntry("Mumbai", "Maharashtra", "Western Express Highway", 19.0760, 72.8777),
        TownEntry("Bengaluru", "Karnataka", "Outer Ring Road", 12.9716, 77.5946),
        TownEntry("San Francisco", "California, USA", "Market Street", 37.7749, -122.4194),
        TownEntry("New York", "New York, USA", "Broadway", 40.7128, -74.0060),
        TownEntry("London", "UK", "Oxford Street", 51.5074, -0.1278)
    )

    fun getFastSynchronousName(latitude: Double, longitude: Double, isMovingOnRoad: Boolean = false): String {
        val roundedLat = (latitude * 1000.0).roundToInt() / 1000.0
        val roundedLon = (longitude * 1000.0).roundToInt() / 1000.0
        val key = "$roundedLat,$roundedLon"

        val mem = memoryCache[key]
        if (mem != null && mem.placeName.isNotBlank() && !mem.placeName.startsWith("Location (")) {
            return if (isMovingOnRoad && mem.roadName.isNotBlank()) {
                "On ${mem.roadName}, ${mem.placeName}"
            } else {
                mem.placeName
            }
        }

        // Fast spatial match from gazetteer
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

        if (closestTown != null && minDistanceMeters < 50000f) {
            return if (isMovingOnRoad && closestTown.primaryRoad.isNotBlank()) {
                "On ${closestTown.primaryRoad}, ${closestTown.name}"
            } else {
                closestTown.name
            }
        }

        return String.format(Locale.US, "Location (%.3f, %.3f)", latitude, longitude)
    }

    fun getFastRoadName(latitude: Double, longitude: Double): String {
        val roundedLat = (latitude * 1000.0).roundToInt() / 1000.0
        val roundedLon = (longitude * 1000.0).roundToInt() / 1000.0
        val key = "$roundedLat,$roundedLon"

        val mem = memoryCache[key]
        if (mem != null && mem.roadName.isNotBlank()) {
            return mem.roadName
        }

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

        if (closestTown != null && minDistanceMeters < 45000f) {
            return closestTown.primaryRoad
        }
        return ""
    }

    fun determineRoadForPath(points: List<com.example.data.local.entity.LocationPointEntity>): String {
        if (points.isEmpty()) return ""

        // Check if any point has a cached road in memoryCache
        for (p in points) {
            val roundedLat = (p.latitude * 1000.0).roundToInt() / 1000.0
            val roundedLon = (p.longitude * 1000.0).roundToInt() / 1000.0
            val key = "$roundedLat,$roundedLon"
            val mem = memoryCache[key]
            if (mem != null && mem.roadName.isNotBlank()) {
                return mem.roadName
            }
        }

        // Spatial voting along path
        val samplePoints = if (points.size <= 5) points else listOf(
            points.first(),
            points[points.size / 4],
            points[points.size / 2],
            points[points.size * 3 / 4],
            points.last()
        )

        val roadVotes = mutableMapOf<String, Int>()
        val distanceResult = FloatArray(1)

        for (p in samplePoints) {
            for (town in offlineTowns) {
                if (town.primaryRoad.isBlank()) continue
                Location.distanceBetween(p.latitude, p.longitude, town.lat, town.lon, distanceResult)
                if (distanceResult[0] < 35000f) {
                    roadVotes[town.primaryRoad] = (roadVotes[town.primaryRoad] ?: 0) + 1
                }
            }
        }

        val topRoad = roadVotes.maxByOrNull { it.value }
        return topRoad?.key ?: ""
    }

    suspend fun getPlaceAndAddress(latitude: Double, longitude: Double): GeocodeResult =
        withContext(Dispatchers.IO) {
            val roundedLat = (latitude * 1000.0).roundToInt() / 1000.0
            val roundedLon = (longitude * 1000.0).roundToInt() / 1000.0
            val key = "$roundedLat,$roundedLon"

            // 1. Check in-memory & SQLite cache
            memoryCache[key]?.let { return@withContext it }

            try {
                val cached = cacheDao.getCachedGeocode(key)
                if (cached != null && cached.placeName.isNotBlank() && !cached.placeName.startsWith("Location (")) {
                    val res = GeocodeResult(placeName = cached.placeName, address = cached.address)
                    memoryCache[key] = res
                    return@withContext res
                }
            } catch (_: Exception) {}

            var detectedPlaceName: String? = null
            var detectedAddress: String? = null
            var detectedRoadName: String = ""

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

                        if (!thoroughfare.isNullOrBlank()) {
                            detectedRoadName = listOfNotNull(subThoroughfare, thoroughfare).joinToString(" ")
                        }
                        if (detectedRoadName.isBlank() && !feature.isNullOrBlank()) {
                            val roadKeywords = listOf("Road", "Marg", "NH", "Highway", "Expressway", "Bypass", "Street", "Lane", "Gali", "Chowk")
                            if (roadKeywords.any { feature.contains(it, ignoreCase = true) }) {
                                detectedRoadName = feature
                            }
                        }

                        val isCodeOrNum = feature?.all { it.isDigit() || it == '-' || it == '/' || it == '+' } ?: false

                        detectedPlaceName = when {
                            !subLocality.isNullOrBlank() && !locality.isNullOrBlank() && subLocality != locality -> "$subLocality, $locality"
                            !locality.isNullOrBlank() -> locality
                            !subLocality.isNullOrBlank() -> subLocality
                            !thoroughfare.isNullOrBlank() && !locality.isNullOrBlank() -> "$thoroughfare, $locality"
                            !feature.isNullOrBlank() && !isCodeOrNum && feature != thoroughfare -> {
                                if (!locality.isNullOrBlank()) "$feature, $locality" else feature
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
                } catch (_: Exception) {}
            }

            // 3. Secondary: HTTP Reverse Geocoding (OpenStreetMap Nominatim)
            if (detectedPlaceName.isNullOrBlank() || detectedRoadName.isBlank()) {
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
                            val road = addressObj.optString("road", "")
                            val highway = addressObj.optString("highway", "")
                            val street = addressObj.optString("street", "")
                            val residential = addressObj.optString("residential", "")
                            val motorway = addressObj.optString("motorway", "")
                            val trunk = addressObj.optString("trunk", "")
                            val primary = addressObj.optString("primary", "")
                            val district = addressObj.optString("county", addressObj.optString("state_district", ""))

                            val detectedOsmRoad = listOf(road, highway, street, residential, motorway, trunk, primary).firstOrNull { it.isNotBlank() } ?: ""
                            if (detectedOsmRoad.isNotBlank()) {
                                detectedRoadName = detectedOsmRoad
                            }

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

                            if (detectedPlaceName.isNullOrBlank()) {
                                detectedPlaceName = when {
                                    subLoc.isNotBlank() && locality.isNotBlank() && subLoc != locality -> "$subLoc, $locality"
                                    locality.isNotBlank() -> locality
                                    subLoc.isNotBlank() -> subLoc
                                    district.isNotBlank() -> district
                                    else -> null
                                }
                            }
                            if (detectedAddress.isNullOrBlank()) {
                                detectedAddress = json.optString("display_name", "")
                            }
                        }
                    }
                    conn.disconnect()
                } catch (_: Exception) {}
            }

            // 4. Tertiary: Offline Nearest Town Gazetteer Fallback
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

            if (detectedRoadName.isBlank() && closestTown != null && minDistanceMeters < 50000f) {
                detectedRoadName = closestTown.primaryRoad
            }

            if (detectedPlaceName.isNullOrBlank()) {
                if (closestTown != null && minDistanceMeters < 55000f) {
                    detectedPlaceName = closestTown.name
                    if (detectedRoadName.isBlank()) {
                        detectedRoadName = closestTown.primaryRoad
                    }
                    val distKm = (minDistanceMeters / 1000f).roundToInt()
                    detectedAddress = if (distKm <= 2) {
                        "${closestTown.name}, ${closestTown.districtState}"
                    } else {
                        "${closestTown.name} Area (~${distKm} km), ${closestTown.districtState}"
                    }
                } else {
                    detectedPlaceName = String.format(Locale.US, "Location (%.3f, %.3f)", latitude, longitude)
                    detectedAddress = String.format(Locale.US, "Coordinates: %.4f, %.4f", latitude, longitude)
                }
            }

            val finalPlaceName = detectedPlaceName ?: "Visited Place"
            val finalAddress = detectedAddress ?: String.format(Locale.US, "Location: %.4f, %.4f", latitude, longitude)
            val result = GeocodeResult(placeName = finalPlaceName, address = finalAddress, roadName = detectedRoadName)

            memoryCache[key] = result

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
