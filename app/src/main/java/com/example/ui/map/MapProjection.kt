package com.example.ui.map

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.tan

object MapProjection {
    const val TILE_SIZE = 256.0
    private const val MAX_LAT = 85.05112877980659
    private const val MIN_LAT = -85.05112877980659

    /**
     * Converts longitude [-180, 180] to world pixel X at integer zoom z.
     */
    fun lonToWorldPixelX(lon: Double, zoom: Int): Double {
        val clampedLon = lon.coerceIn(-180.0, 180.0)
        val tiles = (1 shl zoom).toDouble()
        return (clampedLon + 180.0) / 360.0 * tiles * TILE_SIZE
    }

    /**
     * Converts latitude [-85, 85] to world pixel Y at integer zoom z.
     */
    fun latToWorldPixelY(lat: Double, zoom: Int): Double {
        val clampedLat = lat.coerceIn(MIN_LAT, MAX_LAT)
        val latRad = Math.toRadians(clampedLat)
        val tiles = (1 shl zoom).toDouble()
        val sinLat = sin(latRad)
        val y = 0.5 - ln((1.0 + sinLat) / (1.0 - sinLat)) / (4.0 * PI)
        return y * tiles * TILE_SIZE
    }

    /**
     * Converts world pixel X back to longitude [-180, 180].
     */
    fun worldPixelXToLon(px: Double, zoom: Int): Double {
        val tiles = (1 shl zoom).toDouble()
        return (px / (tiles * TILE_SIZE)) * 360.0 - 180.0
    }

    /**
     * Converts world pixel Y back to latitude.
     */
    fun worldPixelYToLat(py: Double, zoom: Int): Double {
        val tiles = (1 shl zoom).toDouble()
        val y = 0.5 - (py / (tiles * TILE_SIZE))
        val latRad = 2.0 * atan(exp(y * 2.0 * PI)) - PI / 2.0
        return Math.toDegrees(latRad).coerceIn(MIN_LAT, MAX_LAT)
    }

    /**
     * Projects a (lat, lon) onto screen coordinates (Offset) given map center, zoom, and viewport dimensions.
     */
    fun latLonToScreen(
        lat: Double,
        lon: Double,
        centerLat: Double,
        centerLon: Double,
        zoom: Float,
        viewportWidth: Float,
        viewportHeight: Float
    ): Offset {
        val baseZoom = zoom.toInt().coerceIn(1, 19)
        val subScale = Math.pow(2.0, (zoom - baseZoom).toDouble())

        val ptX = lonToWorldPixelX(lon, baseZoom)
        val ptY = latToWorldPixelY(lat, baseZoom)
        val cX = lonToWorldPixelX(centerLon, baseZoom)
        val cY = latToWorldPixelY(centerLat, baseZoom)

        val screenX = (viewportWidth / 2.0) + (ptX - cX) * subScale
        val screenY = (viewportHeight / 2.0) + (ptY - cY) * subScale
        return Offset(screenX.toFloat(), screenY.toFloat())
    }

    /**
     * Unprojects screen coordinates (Offset) to (lat, lon) given map center, zoom, and viewport dimensions.
     */
    fun screenToLatLon(
        screenOffset: Offset,
        centerLat: Double,
        centerLon: Double,
        zoom: Float,
        viewportWidth: Float,
        viewportHeight: Float
    ): Pair<Double, Double> {
        val baseZoom = zoom.toInt().coerceIn(1, 19)
        val subScale = Math.pow(2.0, (zoom - baseZoom).toDouble())

        val cX = lonToWorldPixelX(centerLon, baseZoom)
        val cY = latToWorldPixelY(centerLat, baseZoom)

        val worldPxX = cX + (screenOffset.x - viewportWidth / 2.0) / subScale
        val worldPxY = cY + (screenOffset.y - viewportHeight / 2.0) / subScale

        val lon = worldPixelXToLon(worldPxX, baseZoom)
        val lat = worldPixelYToLat(worldPxY, baseZoom)
        return Pair(lat, lon)
    }

    /**
     * Determines the optimal center and zoom level to encompass a collection of points.
     */
    fun calculateFitBounds(
        lats: List<Double>,
        lons: List<Double>,
        viewportWidth: Float,
        viewportHeight: Float,
        paddingPx: Float = 100f
    ): Pair<Pair<Double, Double>, Float> {
        if (lats.isEmpty() || lons.isEmpty()) {
            return Pair(Pair(28.6139, 77.2090), 14f) // Default center (e.g., Delhi/NCR)
        }

        val minLat = lats.minOrNull() ?: 0.0
        val maxLat = lats.maxOrNull() ?: 0.0
        val minLon = lons.minOrNull() ?: 0.0
        val maxLon = lons.maxOrNull() ?: 0.0

        val centerLat = (minLat + maxLat) / 2.0
        val centerLon = (minLon + maxLon) / 2.0

        if (lats.size == 1 || (maxLat - minLat < 0.0001 && maxLon - minLon < 0.0001)) {
            return Pair(Pair(centerLat, centerLon), 16.5f) // High detail satellite view for single location
        }

        val usableW = max(50f, viewportWidth - paddingPx * 2f).toDouble()
        val usableH = max(50f, viewportHeight - paddingPx * 2f).toDouble()

        // Find zoom where bounding box fits in usable viewport
        var bestZoom = 17f
        for (z in 18 downTo 2) {
            val left = lonToWorldPixelX(minLon, z)
            val right = lonToWorldPixelX(maxLon, z)
            val top = latToWorldPixelY(maxLat, z)
            val bottom = latToWorldPixelY(minLat, z)

            val spanW = Math.abs(right - left)
            val spanH = Math.abs(bottom - top)

            if (spanW <= usableW && spanH <= usableH) {
                bestZoom = z.toFloat()
                break
            }
        }

        return Pair(Pair(centerLat, centerLon), bestZoom.coerceIn(3f, 17.5f))
    }
}
