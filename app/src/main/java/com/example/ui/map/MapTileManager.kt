package com.example.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class MapLayerType(val label: String, val iconDescription: String) {
    HYBRID("Google Satellite", "Satellite photography with road names & labels"),
    SATELLITE("Pure Satellite", "Pure satellite aerial imagery without labels"),
    STREET("Google Streets", "High-contrast standard road map")
}

data class TileCoord(val z: Int, val x: Int, val y: Int)

class MapTileManager(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // In-memory LRU cache (approx. 200 tiles)
    private val maxMemoryCacheBytes = (Runtime.getRuntime().maxMemory() / 6).toInt().coerceIn(24 * 1024 * 1024, 75 * 1024 * 1024)
    private val memoryCache = object : LruCache<String, Bitmap>(maxMemoryCacheBytes) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }
    }

    // Disk cache folder
    private val diskCacheDir = File(context.cacheDir, "google_satellite_tiles").apply {
        if (!exists()) mkdirs()
    }

    // Set of currently active network jobs
    private val activeLoads = ConcurrentHashMap<String, Job>()

    private val scope = CoroutineScope(Dispatchers.IO)

    fun getCachedTile(coord: TileCoord, layerType: MapLayerType): Bitmap? {
        val key = "${layerType.name}_${coord.z}_${coord.x}_${coord.y}"
        memoryCache.get(key)?.let { return it }

        // Check disk cache
        val file = File(diskCacheDir, "$key.img")
        if (file.exists() && file.length() > 0) {
            try {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) {
                    memoryCache.put(key, bitmap)
                    return bitmap
                }
            } catch (_: Exception) {
                file.delete()
            }
        }
        return null
    }

    fun requestTile(
        coord: TileCoord,
        layerType: MapLayerType,
        onTileLoaded: (TileCoord, Bitmap) -> Unit
    ) {
        val key = "${layerType.name}_${coord.z}_${coord.x}_${coord.y}"

        // Memory cache check
        memoryCache.get(key)?.let {
            onTileLoaded(coord, it)
            return
        }

        if (activeLoads.containsKey(key)) return

        val job = scope.launch {
            try {
                val file = File(diskCacheDir, "$key.img")
                var bitmap: Bitmap? = null
                if (file.exists() && file.length() > 0) {
                    try {
                        bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    } catch (_: Exception) {
                        file.delete()
                    }
                }

                if (bitmap == null) {
                    bitmap = downloadTileBitmap(coord, layerType)
                    if (bitmap != null) {
                        try {
                            FileOutputStream(file).use { out ->
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
                            }
                        } catch (_: IOException) { }
                    }
                }

                if (bitmap != null) {
                    memoryCache.put(key, bitmap)
                    withContext(Dispatchers.Main) {
                        onTileLoaded(coord, bitmap)
                    }
                }
            } finally {
                activeLoads.remove(key)
            }
        }

        activeLoads[key] = job
    }

    private fun downloadTileBitmap(coord: TileCoord, layerType: MapLayerType): Bitmap? {
        val urls = getTileUrls(coord, layerType)
        for (url in urls) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                    .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                    .build()

                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    response.body?.byteStream()?.use { stream ->
                        val bmp = BitmapFactory.decodeStream(stream)
                        if (bmp != null) {
                            return bmp
                        }
                    }
                }
            } catch (_: Exception) { }
        }
        return null
    }

    private fun getTileUrls(coord: TileCoord, layerType: MapLayerType): List<String> {
        val (z, x, y) = coord
        val maxTile = (1 shl z)
        if (x < 0 || x >= maxTile || y < 0 || y >= maxTile) {
            return emptyList()
        }

        return when (layerType) {
            MapLayerType.HYBRID -> listOf(
                // Google Maps Hybrid: Real satellite photography + yellow/white roads, street names & city labels
                "https://mt1.google.com/vt/lyrs=y&x=$x&y=$y&z=$z",
                "https://mt2.google.com/vt/lyrs=y&x=$x&y=$y&z=$z",
                "https://mt3.google.com/vt/lyrs=y&x=$x&y=$y&z=$z",
                "https://mt0.google.com/vt/lyrs=y&x=$x&y=$y&z=$z",
                // Fallback to ArcGIS World Imagery
                "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x"
            )
            MapLayerType.SATELLITE -> listOf(
                // Google Maps Pure Satellite: Aerial photographic imagery
                "https://mt1.google.com/vt/lyrs=s&x=$x&y=$y&z=$z",
                "https://mt2.google.com/vt/lyrs=s&x=$x&y=$y&z=$z",
                "https://mt3.google.com/vt/lyrs=s&x=$x&y=$y&z=$z",
                "https://mt0.google.com/vt/lyrs=s&x=$x&y=$y&z=$z",
                "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x"
            )
            MapLayerType.STREET -> listOf(
                // Google Maps Standard Roadmap
                "https://mt1.google.com/vt/lyrs=m&x=$x&y=$y&z=$z",
                "https://mt2.google.com/vt/lyrs=m&x=$x&y=$y&z=$z",
                "https://mt3.google.com/vt/lyrs=m&x=$x&y=$y&z=$z",
                "https://basemaps.cartocdn.com/rastertiles/voyager/$z/$x/$y.png"
            )
        }
    }

    fun clearCache() {
        memoryCache.evictAll()
        scope.launch {
            try {
                diskCacheDir.deleteRecursively()
                diskCacheDir.mkdirs()
            } catch (_: Exception) { }
        }
    }
}
