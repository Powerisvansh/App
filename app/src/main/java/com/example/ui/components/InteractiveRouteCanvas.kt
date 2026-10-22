package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.LocationPointEntity
import com.example.data.local.entity.VisitEntity
import com.example.data.preferences.LastLocationInfo
import com.example.ui.map.MapLayerType
import com.example.ui.map.MapProjection
import com.example.ui.map.MapTileManager
import com.example.ui.map.TileCoord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun InteractiveRouteCanvas(
    points: List<LocationPointEntity>,
    visits: List<VisitEntity>,
    modifier: Modifier = Modifier,
    currentLocation: LastLocationInfo? = null,
    onVisitSelected: (VisitEntity) -> Unit = {}
) {
    val context = LocalContext.current
    val tileManager = remember { MapTileManager(context) }

    // Map state: center (lat, lon), zoom (Float), and layer
    var centerLat by remember { mutableStateOf(28.6139) } // Default fallback (e.g. Karnal/Delhi region or user position)
    var centerLon by remember { mutableStateOf(77.2090) }
    var zoomLevel by remember { mutableFloatStateOf(14.5f) }
    var mapLayer by remember { mutableStateOf(MapLayerType.SATELLITE) }
    var showLayerMenu by remember { mutableStateOf(false) }

    var selectedVisit by remember { mutableStateOf<VisitEntity?>(null) }
    var hasAutoCentered by remember { mutableStateOf(false) }

    // Version tick to trigger redraw when new tiles arrive
    var tileRefreshTrigger by remember { mutableIntStateOf(0) }
    val loadedTiles = remember { mutableStateMapOf<String, Bitmap>() }
    val loadedOverlays = remember { mutableStateMapOf<String, Bitmap>() }

    // Pulsing transition for live user GPS pin
    val infiniteTransition = rememberInfiniteTransition(label = "gps_beacon")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // Calculate initial center and bounds
    LaunchedEffect(points, visits, currentLocation) {
        if (!hasAutoCentered) {
            val allLats = points.map { it.latitude } + visits.map { it.latitude }
            val allLons = points.map { it.longitude } + visits.map { it.longitude }

            if (allLats.isNotEmpty()) {
                val (newCenter, optimalZoom) = MapProjection.calculateFitBounds(
                    lats = allLats,
                    lons = allLons,
                    viewportWidth = 800f,
                    viewportHeight = 1000f,
                    paddingPx = 140f
                )
                centerLat = newCenter.first
                centerLon = newCenter.second
                zoomLevel = optimalZoom
                hasAutoCentered = true
            } else if (currentLocation != null && currentLocation.latitude != 0.0 && currentLocation.longitude != 0.0) {
                centerLat = currentLocation.latitude
                centerLon = currentLocation.longitude
                zoomLevel = 16.5f
                hasAutoCentered = true
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("interactive_route_canvas")
    ) {
        val viewportW = constraints.maxWidth.toFloat()
        val viewportH = constraints.maxHeight.toFloat()

        // Visit positions on screen for tap detection
        var visitScreenPositions by remember { mutableStateOf<List<Pair<VisitEntity, Offset>>>(emptyList()) }

        // Combined gestures: Drag to pan, Pinch to zoom
        val gestureModifier = Modifier.pointerInput(centerLat, centerLon, zoomLevel) {
            detectTransformGestures { _, pan, zoomChange, _ ->
                // Apply zoom
                if (zoomChange != 1.0f) {
                    zoomLevel = (zoomLevel * zoomChange).coerceIn(2.5f, 18.5f)
                }

                // Apply pan
                if (pan != Offset.Zero) {
                    val (newLat, newLon) = MapProjection.screenToLatLon(
                        screenOffset = Offset(viewportW / 2f - pan.x, viewportH / 2f - pan.y),
                        centerLat = centerLat,
                        centerLon = centerLon,
                        zoom = zoomLevel,
                        viewportWidth = viewportW,
                        viewportHeight = viewportH
                    )
                    centerLat = newLat
                    centerLon = newLon
                }
            }
        }

        // Tap gestures for selecting visit pins and double-tap to zoom
        val tapModifier = Modifier.pointerInput(centerLat, centerLon, zoomLevel, visitScreenPositions) {
            detectTapGestures(
                onDoubleTap = { tapOffset ->
                    val (tapLat, tapLon) = MapProjection.screenToLatLon(
                        screenOffset = tapOffset,
                        centerLat = centerLat,
                        centerLon = centerLon,
                        zoom = zoomLevel,
                        viewportWidth = viewportW,
                        viewportHeight = viewportH
                    )
                    centerLat = tapLat
                    centerLon = tapLon
                    zoomLevel = (zoomLevel + 1.2f).coerceAtMost(18.5f)
                },
                onTap = { tapOffset ->
                    val hit = visitScreenPositions.find { (_, pinOffset) ->
                        hypot((tapOffset.x - pinOffset.x).toDouble(), (tapOffset.y - pinOffset.y).toDouble()) <= 45.0
                    }
                    if (hit != null) {
                        selectedVisit = hit.first
                        onVisitSelected(hit.first)
                    } else {
                        selectedVisit = null
                    }
                }
            )
        }

        // Map Canvas: renders Satellite tiles, polylines, visited places, and live user location
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(gestureModifier)
                .then(tapModifier)
        ) {
            // Read trigger to recompose when tiles load
            val currentTrigger = tileRefreshTrigger
            if (currentTrigger < 0) return@Canvas

            val baseZoom = zoomLevel.toInt().coerceIn(1, 18)
            val subScale = Math.pow(2.0, (zoomLevel - baseZoom).toDouble()).toFloat()
            val tileSizeOnScreen = (MapProjection.TILE_SIZE * subScale).toFloat()

            // 1. Draw Satellite Terrain Base / Realistic aerial background
            drawRect(
                brush = Brush.verticalGradient(
                    colors = when (mapLayer) {
                        MapLayerType.SATELLITE, MapLayerType.HYBRID -> listOf(
                            Color(0xFF0F1E26),
                            Color(0xFF132A38),
                            Color(0xFF193444)
                        )
                        MapLayerType.STREET -> listOf(
                            Color(0xFFE8ECEF),
                            Color(0xFFDCE2E6)
                        )
                    }
                ),
                size = Size(viewportW, viewportH)
            )

            // 2. Compute visible tile bounds
            val topLeftCoord = MapProjection.screenToLatLon(
                screenOffset = Offset(-tileSizeOnScreen, -tileSizeOnScreen),
                centerLat = centerLat,
                centerLon = centerLon,
                zoom = zoomLevel,
                viewportWidth = viewportW,
                viewportHeight = viewportH
            )
            val bottomRightCoord = MapProjection.screenToLatLon(
                screenOffset = Offset(viewportW + tileSizeOnScreen, viewportH + tileSizeOnScreen),
                centerLat = centerLat,
                centerLon = centerLon,
                zoom = zoomLevel,
                viewportWidth = viewportW,
                viewportHeight = viewportH
            )

            val maxTiles = (1 shl baseZoom) - 1
            val minTileX = ((topLeftCoord.second + 180.0) / 360.0 * (1 shl baseZoom)).toInt().coerceIn(0, maxTiles)
            val maxTileX = ((bottomRightCoord.second + 180.0) / 360.0 * (1 shl baseZoom)).toInt().coerceIn(0, maxTiles)

            // Web Mercator tile Y increases downwards
            val topPxY = MapProjection.latToWorldPixelY(topLeftCoord.first, baseZoom)
            val bottomPxY = MapProjection.latToWorldPixelY(bottomRightCoord.first, baseZoom)
            val minTileY = (topPxY / MapProjection.TILE_SIZE).toInt().coerceIn(0, maxTiles)
            val maxTileY = (bottomPxY / MapProjection.TILE_SIZE).toInt().coerceIn(0, maxTiles)

            // Draw Satellite / Aerial Tiles
            for (ty in min(minTileY, maxTileY)..max(minTileY, maxTileY)) {
                for (tx in min(minTileX, maxTileX)..max(minTileX, maxTileX)) {
                    val tile = TileCoord(baseZoom, tx, ty)
                    val tileKey = "${mapLayer.name}_${baseZoom}_${tx}_${ty}"

                    // Convert tile NW corner to screen coordinates
                    val nwLat = MapProjection.worldPixelYToLat(ty * MapProjection.TILE_SIZE, baseZoom)
                    val nwLon = MapProjection.worldPixelXToLon(tx * MapProjection.TILE_SIZE, baseZoom)
                    val screenNW = MapProjection.latLonToScreen(
                        lat = nwLat,
                        lon = nwLon,
                        centerLat = centerLat,
                        centerLon = centerLon,
                        zoom = zoomLevel,
                        viewportWidth = viewportW,
                        viewportHeight = viewportH
                    )

                    var bitmap = loadedTiles[tileKey]
                    if (bitmap == null) {
                        bitmap = tileManager.getCachedTile(tile, mapLayer)
                        if (bitmap != null) {
                            loadedTiles[tileKey] = bitmap
                        } else {
                            tileManager.requestTile(tile, mapLayer) { loadedCoord, loadedBmp ->
                                val key = "${mapLayer.name}_${loadedCoord.z}_${loadedCoord.x}_${loadedCoord.y}"
                                loadedTiles[key] = loadedBmp
                                tileRefreshTrigger++
                            }
                        }
                    }

                    if (bitmap != null && !bitmap.isRecycled) {
                        // Draw high-resolution satellite imagery tile
                        drawImage(
                            image = bitmap.asImageBitmap(),
                            dstOffset = IntOffset(screenNW.x.roundToInt(), screenNW.y.roundToInt()),
                            dstSize = IntSize((tileSizeOnScreen + 1f).roundToInt(), (tileSizeOnScreen + 1f).roundToInt())
                        )
                    } else {
                        // Drawing realistic satellite terrain placeholder while loading
                        drawRect(
                            color = if (mapLayer == MapLayerType.STREET) Color(0xFFE4E9EC) else Color(0xFF142733),
                            topLeft = screenNW,
                            size = Size(tileSizeOnScreen, tileSizeOnScreen)
                        )
                        drawRect(
                            color = Color.White.copy(alpha = 0.05f),
                            topLeft = screenNW,
                            size = Size(tileSizeOnScreen, tileSizeOnScreen),
                            style = Stroke(width = 1f)
                        )
                    }

                    // For HYBRID layer, draw overlay roads & place labels
                    if (mapLayer == MapLayerType.HYBRID) {
                        val overlayKey = "HYBRID_${baseZoom}_${tx}_${ty}"
                        var overlayBmp = loadedOverlays[overlayKey]
                        if (overlayBmp == null) {
                            tileManager.requestOverlayTile(tile) { coord, bmp ->
                                val key = "HYBRID_${coord.z}_${coord.x}_${coord.y}"
                                loadedOverlays[key] = bmp
                                tileRefreshTrigger++
                            }
                        } else if (!overlayBmp.isRecycled) {
                            drawImage(
                                image = overlayBmp.asImageBitmap(),
                                dstOffset = IntOffset(screenNW.x.roundToInt(), screenNW.y.roundToInt()),
                                dstSize = IntSize((tileSizeOnScreen + 1f).roundToInt(), (tileSizeOnScreen + 1f).roundToInt())
                            )
                        }
                    }
                }
            }

            // 3. Draw Travel Route Polyline with Neon Contrast Halo
            if (points.size >= 2) {
                val routePath = Path()
                val firstScr = MapProjection.latLonToScreen(
                    lat = points.first().latitude,
                    lon = points.first().longitude,
                    centerLat = centerLat,
                    centerLon = centerLon,
                    zoom = zoomLevel,
                    viewportWidth = viewportW,
                    viewportHeight = viewportH
                )
                routePath.moveTo(firstScr.x, firstScr.y)

                for (i in 1 until points.size) {
                    val p = points[i]
                    val scr = MapProjection.latLonToScreen(
                        lat = p.latitude,
                        lon = p.longitude,
                        centerLat = centerLat,
                        centerLon = centerLon,
                        zoom = zoomLevel,
                        viewportWidth = viewportW,
                        viewportHeight = viewportH
                    )
                    routePath.lineTo(scr.x, scr.y)
                }

                // Outer high-contrast shadow/border (makes route visible over both dark satellite forests and bright deserts/cities)
                drawPath(
                    path = routePath,
                    color = Color.Black.copy(alpha = 0.65f),
                    style = Stroke(
                        width = 12f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // Neon glowing accent border
                drawPath(
                    path = routePath,
                    color = Color(0xFF00E5FF).copy(alpha = 0.5f),
                    style = Stroke(
                        width = 8f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // Main vibrant route polyline (Electric Cyan / Google Maps Style)
                drawPath(
                    path = routePath,
                    color = Color(0xFF00E5FF),
                    style = Stroke(
                        width = 4.5f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // GPS breadcrumb dots along the route
                for (pt in points) {
                    val p = MapProjection.latLonToScreen(
                        lat = pt.latitude,
                        lon = pt.longitude,
                        centerLat = centerLat,
                        centerLon = centerLon,
                        zoom = zoomLevel,
                        viewportWidth = viewportW,
                        viewportHeight = viewportH
                    )
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.6f),
                        radius = 4f,
                        center = p
                    )
                    drawCircle(
                        color = Color(0xFFE0F7FA),
                        radius = 2.5f,
                        center = p
                    )
                }
            }

            // 4. Draw Visited Place Pins with Names & Numbers
            val calculatedPositions = mutableListOf<Pair<VisitEntity, Offset>>()
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }

            visits.forEachIndexed { index, visit ->
                val p = MapProjection.latLonToScreen(
                    lat = visit.latitude,
                    lon = visit.longitude,
                    centerLat = centerLat,
                    centerLon = centerLon,
                    zoom = zoomLevel,
                    viewportWidth = viewportW,
                    viewportHeight = viewportH
                )
                calculatedPositions.add(Pair(visit, p))

                val isOngoing = visit.departureTimeUtc == null
                val isSelected = selectedVisit?.id == visit.id

                val pinColor = when {
                    isSelected -> Color(0xFFFF5722)
                    isOngoing -> Color(0xFF00E676)
                    else -> Color(0xFFFF9100)
                }
                val pinRadius = if (isSelected) 22f else 18f

                // Outer pulsing aura
                drawCircle(
                    color = pinColor.copy(alpha = if (isSelected) 0.45f else 0.25f),
                    radius = pinRadius * 1.8f,
                    center = p
                )

                // White pin halo
                drawCircle(
                    color = Color.White,
                    radius = pinRadius + 3.5f,
                    center = p
                )

                // Pin circle background
                drawCircle(
                    color = pinColor,
                    radius = pinRadius,
                    center = p
                )

                // Number badge inside pin
                paint.color = android.graphics.Color.WHITE
                paint.textSize = 26f
                paint.isFakeBoldText = true
                drawContext.canvas.nativeCanvas.drawText(
                    "${index + 1}",
                    p.x,
                    p.y + 9f,
                    paint
                )

                // Place name label banner below pin with dark pill background for readability on satellite images
                val shortName = if (visit.placeName.length > 20) {
                    visit.placeName.take(18) + "…"
                } else visit.placeName

                paint.textSize = 24f
                val textW = paint.measureText(shortName)
                val bannerH = 34f
                val bannerW = textW + 28f
                val bannerTop = p.y + pinRadius + 10f

                // Banner dark background
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.85f),
                    topLeft = Offset(p.x - bannerW / 2f, bannerTop),
                    size = Size(bannerW, bannerH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f)
                )
                drawRoundRect(
                    color = pinColor.copy(alpha = 0.6f),
                    topLeft = Offset(p.x - bannerW / 2f, bannerTop),
                    size = Size(bannerW, bannerH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f),
                    style = Stroke(width = 1.5f)
                )

                // Banner text
                paint.color = android.graphics.Color.WHITE
                paint.isFakeBoldText = true
                drawContext.canvas.nativeCanvas.drawText(
                    shortName,
                    p.x,
                    bannerTop + 24f,
                    paint
                )
            }
            visitScreenPositions = calculatedPositions

            // 5. Draw Live User Location Blue GPS Dot (Google Maps style)
            val liveLat = currentLocation?.latitude ?: points.lastOrNull()?.latitude
            val liveLon = currentLocation?.longitude ?: points.lastOrNull()?.longitude
            if (liveLat != null && liveLon != null && liveLat != 0.0 && liveLon != 0.0) {
                val liveScreen = MapProjection.latLonToScreen(
                    lat = liveLat,
                    lon = liveLon,
                    centerLat = centerLat,
                    centerLon = centerLon,
                    zoom = zoomLevel,
                    viewportWidth = viewportW,
                    viewportHeight = viewportH
                )

                // Accuracy circle / GPS pulsing wave
                drawCircle(
                    color = Color(0xFF29B6F6).copy(alpha = 0.22f),
                    radius = 36f * pulseScale,
                    center = liveScreen
                )

                // White ring
                drawCircle(
                    color = Color.White,
                    radius = 12f,
                    center = liveScreen
                )

                // Core Google Maps blue dot
                drawCircle(
                    color = Color(0xFF0288D1),
                    radius = 9f,
                    center = liveScreen
                )
            }
        }

        // =========================================================================
        // Floating Top-Left Layer Indicator & HUD Info Chip
        // =========================================================================
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.82f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color(0xFF00E5FF), CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "${mapLayer.label} View",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Zoom ${(zoomLevel * 10).roundToInt() / 10f} • High-Res Aerial",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                }
            }
        }

        // =========================================================================
        // Floating Map Controls: Layers, My Location, Fit Route, Zoom In, Zoom Out
        // =========================================================================
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Layer Switcher Button
            Box {
                Surface(
                    shape = CircleShape,
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                ) {
                    IconButton(
                        onClick = { showLayerMenu = true },
                        modifier = Modifier
                            .size(46.dp)
                            .testTag("map_layers_button")
                    ) {
                        Icon(
                            Icons.Default.Layers,
                            contentDescription = "Map Layers",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                DropdownMenu(
                    expanded = showLayerMenu,
                    onDismissRequest = { showLayerMenu = false }
                ) {
                    MapLayerType.values().forEach { layer ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = layer.label,
                                    fontWeight = if (mapLayer == layer) FontWeight.Bold else FontWeight.Normal,
                                    color = if (mapLayer == layer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                mapLayer = layer
                                showLayerMenu = false
                            }
                        )
                    }
                }
            }

            // My Location Button (Jump directly to live GPS location)
            Surface(
                shape = CircleShape,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            ) {
                IconButton(
                    onClick = {
                        val lat = currentLocation?.latitude ?: points.lastOrNull()?.latitude
                        val lon = currentLocation?.longitude ?: points.lastOrNull()?.longitude
                        if (lat != null && lon != null && lat != 0.0 && lon != 0.0) {
                            centerLat = lat
                            centerLon = lon
                            zoomLevel = 17f
                        }
                    },
                    modifier = Modifier
                        .size(46.dp)
                        .testTag("my_location_button")
                ) {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = "Center on My Location",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Fit Route Button (Zoom to encompass all tracked points)
            Surface(
                shape = CircleShape,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            ) {
                IconButton(
                    onClick = {
                        val allLats = points.map { it.latitude } + visits.map { it.latitude }
                        val allLons = points.map { it.longitude } + visits.map { it.longitude }
                        if (allLats.isNotEmpty()) {
                            val (newCenter, optimalZoom) = MapProjection.calculateFitBounds(
                                lats = allLats,
                                lons = allLons,
                                viewportWidth = viewportW,
                                viewportHeight = viewportH,
                                paddingPx = 140f
                            )
                            centerLat = newCenter.first
                            centerLon = newCenter.second
                            zoomLevel = optimalZoom
                        }
                    },
                    modifier = Modifier
                        .size(46.dp)
                        .testTag("recenter_button")
                ) {
                    Icon(
                        Icons.Default.CenterFocusStrong,
                        contentDescription = "Fit Route",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Zoom In (+)
            Surface(
                shape = CircleShape,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            ) {
                IconButton(
                    onClick = { zoomLevel = (zoomLevel + 1f).coerceAtMost(18.5f) },
                    modifier = Modifier
                        .size(46.dp)
                        .testTag("zoom_in_button")
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Zoom In",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Zoom Out (-)
            Surface(
                shape = CircleShape,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            ) {
                IconButton(
                    onClick = { zoomLevel = (zoomLevel - 1f).coerceAtLeast(2.5f) },
                    modifier = Modifier
                        .size(46.dp)
                        .testTag("zoom_out_button")
                ) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = "Zoom Out",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // =========================================================================
        // Live Road / Location Banner at Bottom-Start
        // =========================================================================
        if (currentLocation != null && (currentLocation.placeName.isNotBlank() || currentLocation.roadName.isNotBlank())) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.85f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, bottom = if (selectedVisit != null) 170.dp else 16.dp)
                    .fillMaxWidth(0.72f)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Route,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        val textRoad = if (currentLocation.roadName.isNotBlank()) currentLocation.roadName else currentLocation.placeName
                        Text(
                            text = textRoad,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1
                        )
                        if (currentLocation.placeName.isNotBlank() && currentLocation.placeName != textRoad) {
                            Text(
                                text = currentLocation.placeName,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.75f),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // =========================================================================
        // Selected Visited Place Popup Card (Interactive Pin Details)
        // =========================================================================
        AnimatedVisibility(
            visible = selectedVisit != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(14.dp),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            selectedVisit?.let { v ->
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .testTag("selected_visit_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = v.placeName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = v.address,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }

                            IconButton(
                                onClick = { selectedVisit = null },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                            val arrival = timeFormat.format(Date(v.arrivalTimeUtc))
                            val departure = if (v.departureTimeUtc != null) {
                                timeFormat.format(Date(v.departureTimeUtc))
                            } else "Ongoing"

                            val durMin = v.durationSeconds / 60
                            val durHours = durMin / 60
                            val durRemMin = durMin % 60
                            val durationStr = if (durHours > 0) "${durHours}h ${durRemMin}m" else "${durRemMin}m"

                            Column {
                                Text(
                                    text = "$arrival – $departure",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Stayed for $durationStr",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            FilledTonalButton(
                                onClick = { onVisitSelected(v) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("View Details")
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
