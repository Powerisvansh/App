package com.example.domain.routing

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs

data class RoadCoordinate(
    val latitude: Double,
    val longitude: Double
)

data class RoadRouteResult(
    val coordinates: List<RoadCoordinate>,
    val totalDistanceMeters: Double,
    val durationSeconds: Double,
    val summaryRoadName: String,
    val isSnappedToRoads: Boolean
)

object RoadRoutingService {
    private const val TAG = "RoadRoutingService"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // In-memory LRU-like cache to avoid re-querying identical routes
    private val routeCache = ConcurrentHashMap<String, RoadRouteResult>()

    /**
     * Snap a set of waypoints (GPS tracking points or visit locations)
     * onto real streets and roads using real road network geometries.
     */
    suspend fun getStreetRoute(
        waypoints: List<RoadCoordinate>,
        maxWaypointsToSend: Int = 25
    ): RoadRouteResult = withContext(Dispatchers.IO) {
        if (waypoints.size < 2) {
            return@withContext RoadRouteResult(
                coordinates = waypoints,
                totalDistanceMeters = 0.0,
                durationSeconds = 0.0,
                summaryRoadName = "",
                isSnappedToRoads = false
            )
        }

        // Sub-sample waypoints if list is large to respect OSRM URL limits and preserve key turns
        val sampled = sampleWaypoints(waypoints, maxWaypointsToSend)
        val cacheKey = buildCacheKey(sampled)

        routeCache[cacheKey]?.let {
            return@withContext it
        }

        // OSRM coordinates format: {lon},{lat};{lon},{lat}...
        val coordsParam = sampled.joinToString(";") {
            String.format(java.util.Locale.US, "%.6f,%.6f", it.longitude, it.latitude)
        }

        val url = "https://router.project-osrm.org/route/v1/driving/$coordsParam?overview=full&geometries=geojson&steps=false"

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "VanshFinalGPSApp/1.0")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "OSRM routing response failed: ${response.code}")
                return@withContext fallbackDirect(waypoints)
            }

            val body = response.body?.string() ?: return@withContext fallbackDirect(waypoints)
            val json = JSONObject(body)

            if (json.optString("code") != "Ok") {
                Log.w(TAG, "OSRM code not Ok: ${json.optString("code")}")
                return@withContext fallbackDirect(waypoints)
            }

            val routes = json.optJSONArray("routes")
            if (routes == null || routes.length() == 0) {
                return@withContext fallbackDirect(waypoints)
            }

            val firstRoute = routes.getJSONObject(0)
            val distance = firstRoute.optDouble("distance", 0.0)
            val duration = firstRoute.optDouble("duration", 0.0)

            // Legs to extract road names
            val summaryNames = mutableListOf<String>()
            val legs = firstRoute.optJSONArray("legs")
            if (legs != null) {
                for (i in 0 until legs.length()) {
                    val leg = legs.getJSONObject(i)
                    val summary = leg.optString("summary")
                    if (summary.isNotBlank() && !summaryNames.contains(summary)) {
                        summaryNames.add(summary)
                    }
                }
            }
            val roadSummary = if (summaryNames.isNotEmpty()) {
                summaryNames.joinToString(", ")
            } else "Real Streets & Roads"

            val geometry = firstRoute.getJSONObject("geometry")
            val coordsArray = geometry.getJSONArray("coordinates")
            val roadCoordinates = ArrayList<RoadCoordinate>(coordsArray.length())

            for (i in 0 until coordsArray.length()) {
                val pt = coordsArray.getJSONArray(i)
                // GeoJSON format is [lon, lat]
                val lon = pt.getDouble(0)
                val lat = pt.getDouble(1)
                roadCoordinates.add(RoadCoordinate(latitude = lat, longitude = lon))
            }

            val result = RoadRouteResult(
                coordinates = roadCoordinates,
                totalDistanceMeters = distance,
                durationSeconds = duration,
                summaryRoadName = roadSummary,
                isSnappedToRoads = true
            )

            // Cache result (keep cache size bounded)
            if (routeCache.size > 200) {
                routeCache.clear()
            }
            routeCache[cacheKey] = result

            return@withContext result
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching road route from OSRM", e)
            return@withContext fallbackDirect(waypoints)
        }
    }

    /**
     * Calculate street route between two single points (e.g. from current GPS location to place).
     */
    suspend fun getRouteBetween(
        fromLat: Double,
        fromLng: Double,
        toLat: Double,
        toLng: Double
    ): RoadRouteResult {
        return getStreetRoute(
            listOf(
                RoadCoordinate(fromLat, fromLng),
                RoadCoordinate(toLat, toLng)
            )
        )
    }

    /**
     * Evenly downsample GPS waypoints while preserving start, end, and significant turns.
     */
    private fun sampleWaypoints(points: List<RoadCoordinate>, maxPoints: Int): List<RoadCoordinate> {
        if (points.size <= maxPoints) return points

        val result = ArrayList<RoadCoordinate>(maxPoints)
        result.add(points.first())

        val step = (points.size - 2).toDouble() / (maxPoints - 2).toDouble()
        for (i in 1 until maxPoints - 1) {
            val idx = (i * step).toInt().coerceIn(1, points.size - 2)
            if (idx !in result.indices && idx < points.size - 1) {
                result.add(points[idx])
            }
        }

        result.add(points.last())
        return result
    }

    private fun buildCacheKey(points: List<RoadCoordinate>): String {
        return points.joinToString("|") {
            String.format(java.util.Locale.US, "%.5f,%.5f", it.latitude, it.longitude)
        }
    }

    private fun fallbackDirect(waypoints: List<RoadCoordinate>): RoadRouteResult {
        return RoadRouteResult(
            coordinates = waypoints,
            totalDistanceMeters = 0.0,
            durationSeconds = 0.0,
            summaryRoadName = "Direct GPS Path",
            isSnappedToRoads = false
        )
    }
}
