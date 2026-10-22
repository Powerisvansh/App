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
    SATELLITE("Satellite", "Satellite aerial imagery"),
    HYBRID("Hybrid", "Satellite with roads and place names"),
    STREET("Streets", "High-contrast street map")
}

data class TileCoord(val z: Int, val x: Int, val y: Int)

class MapTileManager(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    // In-memory LRU cache (approx. 180 tiles ~ 256x256x4 bytes = 262KB each ~ 45MB total)
    private val maxMemoryCacheBytes = (Runtime.getRuntime().maxMemory() / 8).toInt().coerceIn(20 * 1024 * 1024, 60 * 1024 * 1024)
    private val memoryCache = object : LruCache<String, Bitmap>(maxMemoryCacheBytes) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }
    }

    // Disk cache folder
    private val diskCacheDir = File(context.cacheDir, "satellite_map_tiles").apply {
        if (!exists()) mkdirs()
    }

    // Set of currently running download jobs to avoid duplicate requests
    private val activeLoads = ConcurrentHashMap<String, Job>()

    private val scope = CoroutineScope(Dispatchers.IO)

    fun getCachedTile(coord: TileCoord, layerType: MapLayerType): Bitmap? {
        val key = "${layerType.name}_${coord.z}_${coord.x}_${coord.y}"
        memoryCache.get(key)?.let { return it }

        // Check disk cache in memory query (instant check)
        val file = File(diskCacheDir, "$key.png")
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

        // If already in memory cache, return immediately
        memoryCache.get(key)?.let {
            onTileLoaded(coord, it)
            return
        }

        // Avoid duplicate concurrent network fetches for same tile
        if (activeLoads.containsKey(key)) return

        val job = scope.launch {
            try {
                // Check disk cache first
                val file = File(diskCacheDir, "$key.png")
                var bitmap: Bitmap? = null
                if (file.exists() && file.length() > 0) {
                    try {
                        bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    } catch (_: Exception) {
                        file.delete()
                    }
                }

                // If not on disk, download from tile server
                if (bitmap == null) {
                    bitmap = downloadTileBitmap(coord, layerType)
                    if (bitmap != null) {
                        // Persist to disk cache
                        try {
                            FileOutputStream(file).use { out ->
                                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                            }
                        } catch (_: IOException) {
                            // Disk write failure is non-fatal
                        }
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
                    .header("User-Agent", "PersonalGPSTracker/1.0 (Android; MapViewer)")
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
            } catch (_: Exception) {
                // Try next fallback URL if any
            }
        }
        return null
    }

    private fun getTileUrls(coord: TileCoord, layerType: MapLayerType): List<String> {
        val (z, x, y) = coord
        // Max tile bounds at zoom z
        val maxTile = (1 shl z)
        if (x < 0 || x >= maxTile || y < 0 || y >= maxTile) {
            return emptyList()
        }

        return when (layerType) {
            MapLayerType.SATELLITE -> listOf(
                // Esri World Imagery (ArcGIS High-Res Global Satellite Photography)
                "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x",
                "https://services.arcgisonline.com/arcgis/rest/services/World_Imagery/MapServer/tile/$z/$y/$x"
            )
            MapLayerType.HYBRID -> listOf(
                // Esri World Imagery with Reference Labels (satellite aerial + street/place labels)
                "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x"
            )
            MapLayerType.STREET -> listOf(
                // CartoDB Voyager and OSM standard
                "https://basemaps.cartocdn.com/rastertiles/voyager/$z/$x/$y.png",
                "https://tile.openstreetmap.org/$z/$x/$y.png"
            )
        }
    }

    /**
     * For Hybrid view, fetch the transparent road/boundary label overlay tile to blend on top
     */
    fun requestOverlayTile(
        coord: TileCoord,
        onOverlayLoaded: (TileCoord, Bitmap) -> Unit
    ) {
        val (z, x, y) = coord
        val key = "OVERLAY_HYBRID_${z}_${x}_${y}"

        memoryCache.get(key)?.let {
            onOverlayLoaded(coord, it)
            return
        }

        if (activeLoads.containsKey(key)) return

        val job = scope.launch {
            try {
                val file = File(diskCacheDir, "$key.png")
                var bitmap: Bitmap? = null
                if (file.exists() && file.length() > 0) {
                    try {
                        bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    } catch (_: Exception) {
                        file.delete()
                    }
                }

                if (bitmap == null) {
                    // Esri World Boundaries and Places reference label overlay
                    val url = "https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/$z/$y/$x"
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "PersonalGPSTracker/1.0 (Android; MapViewer)")
                        .build()

                    val response = httpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        response.body?.byteStream()?.use { stream ->
                            bitmap = BitmapFactory.decodeStream(stream)
                        }
                    }

                    if (bitmap != null) {
                        try {
                            FileOutputStream(file).use { out ->
                                bitmap!!.compress(Bitmap.CompressFormat.PNG, 90, out)
                            }
                        } catch (_: Exception) { }
                    }
                }

                if (bitmap != null) {
                    memoryCache.put(key, bitmap!!)
                    withContext(Dispatchers.Main) {
                        onOverlayLoaded(coord, bitmap!!)
                    }
                }
            } finally {
                activeLoads.remove(key)
            }
        }
        activeLoads[key] = job
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
