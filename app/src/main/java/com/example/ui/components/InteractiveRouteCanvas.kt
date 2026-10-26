package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.local.entity.LocationPointEntity
import com.example.data.local.entity.VisitEntity
import com.example.data.preferences.LastLocationInfo
import com.example.domain.routing.RoadCoordinate
import com.example.domain.routing.RoadRouteResult
import com.example.domain.routing.RoadRoutingService
import com.example.ui.map.MapLayerType
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * JavaScript interface bridge between Leaflet real Google Maps and Android Kotlin.
 */
class RealMapJsBridge(
    private val onMapLoaded: () -> Unit,
    private val onVisitClick: (Long) -> Unit,
    private val onRouteToPlaceRequest: (Double, Double, Long) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onMapReady() {
        mainHandler.post {
            onMapLoaded()
        }
    }

    @JavascriptInterface
    fun onVisitClicked(visitId: Long) {
        mainHandler.post {
            onVisitClick(visitId)
        }
    }

    @JavascriptInterface
    fun onRouteToPlace(lat: Double, lng: Double, visitId: Long) {
        mainHandler.post {
            onRouteToPlaceRequest(lat, lng, visitId)
        }
    }
}

/**
 * Real interactive map view powered by Leaflet and Google Maps Satellite/Hybrid tiles,
 * complete with pinch-to-zoom, momentum panning, live pulsing GPS dot, and routes snapped
 * to real streets and roads.
 */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun InteractiveRouteCanvas(
    points: List<LocationPointEntity>,
    visits: List<VisitEntity>,
    modifier: Modifier = Modifier,
    currentLocation: LastLocationInfo? = null,
    onVisitSelected: (VisitEntity) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isMapLoaded by remember { mutableStateOf(false) }
    var selectedVisit by remember { mutableStateOf<VisitEntity?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var currentLayer by remember { mutableStateOf(MapLayerType.HYBRID) }
    var showLayerMenu by remember { mutableStateOf(false) }
    var hasInitialFitted by remember { mutableStateOf(false) }

    // Real street road snapping state
    var snapToRealRoads by remember { mutableStateOf(true) }
    var isCalculatingRoute by remember { mutableStateOf(false) }
    var activeRoadRoute by remember { mutableStateOf<RoadRouteResult?>(null) }

    fun evaluateJs(script: String) {
        webViewRef?.post {
            webViewRef?.evaluateJavascript(script, null)
        }
    }

    // Push real street route to Leaflet
    fun pushStreetRouteToMap(result: RoadRouteResult) {
        val jsonArray = JSONArray()
        for (c in result.coordinates) {
            val obj = JSONObject()
            obj.put("latitude", c.latitude)
            obj.put("longitude", c.longitude)
            jsonArray.put(obj)
        }
        val safeSummary = result.summaryRoadName.replace("'", "\\'")
        evaluateJs("window.setStreetRoute(${jsonArray}, ${result.totalDistanceMeters}, ${result.durationSeconds}, '${safeSummary}');")
    }

    // Calculate and apply real street route when points/visits change
    LaunchedEffect(isMapLoaded, points, visits, snapToRealRoads) {
        if (!isMapLoaded) return@LaunchedEffect

        if (!snapToRealRoads) {
            // User disabled real road snapping: fallback to direct points
            val jsonArray = JSONArray()
            for (p in points) {
                val obj = JSONObject()
                obj.put("latitude", p.latitude)
                obj.put("longitude", p.longitude)
                jsonArray.put(obj)
            }
            evaluateJs("window.setRoutePoints(${jsonArray});")
            activeRoadRoute = null
            return@LaunchedEffect
        }

        // Determine waypoints for street road routing
        val waypoints = when {
            points.size >= 2 -> {
                points.map { RoadCoordinate(it.latitude, it.longitude) }
            }
            visits.size >= 2 -> {
                visits.sortedBy { it.arrivalTimeUtc }.map { RoadCoordinate(it.latitude, it.longitude) }
            }
            currentLocation != null && visits.isNotEmpty() -> {
                val targetVisit = visits.first()
                listOf(
                    RoadCoordinate(currentLocation.latitude, currentLocation.longitude),
                    RoadCoordinate(targetVisit.latitude, targetVisit.longitude)
                )
            }
            else -> emptyList()
        }

        if (waypoints.size >= 2) {
            isCalculatingRoute = true
            val result = RoadRoutingService.getStreetRoute(waypoints)
            activeRoadRoute = result
            isCalculatingRoute = false
            pushStreetRouteToMap(result)
        } else {
            // Fewer than 2 points: clear route
            evaluateJs("window.setRoutePoints('[]');")
            activeRoadRoute = null
        }
    }

    // Sync visited place markers to Leaflet whenever visits change
    LaunchedEffect(isMapLoaded, visits) {
        if (isMapLoaded) {
            val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
            val jsonArray = JSONArray()
            for (v in visits) {
                val obj = JSONObject()
                obj.put("id", v.id)
                obj.put("placeName", v.placeName)
                obj.put("address", v.address)
                obj.put("latitude", v.latitude)
                obj.put("longitude", v.longitude)
                obj.put("arrivalTimeUtc", v.arrivalTimeUtc)
                obj.put("arrivalFormatted", timeFmt.format(Date(v.arrivalTimeUtc)))
                if (v.departureTimeUtc != null) {
                    obj.put("departureTimeUtc", v.departureTimeUtc)
                    obj.put("departureFormatted", timeFmt.format(Date(v.departureTimeUtc)))
                } else {
                    obj.put("departureTimeUtc", JSONObject.NULL)
                    obj.put("departureFormatted", "Ongoing")
                }
                obj.put("durationSeconds", v.durationSeconds)
                jsonArray.put(obj)
            }
            evaluateJs("window.setVisitedPlaces(${jsonArray});")
        }
    }

    // Sync live user location puck whenever location updates
    LaunchedEffect(isMapLoaded, currentLocation) {
        if (isMapLoaded && currentLocation != null) {
            val acc = currentLocation.accuracy ?: 0f
            evaluateJs("window.updateUserLocation(${currentLocation.latitude}, ${currentLocation.longitude}, $acc);")
        }
    }

    // Auto-fit to route/visits on initial load
    LaunchedEffect(isMapLoaded, points.size, visits.size) {
        if (isMapLoaded && !hasInitialFitted) {
            if (points.isNotEmpty() || visits.isNotEmpty()) {
                evaluateJs("window.fitAllBounds();")
                hasInitialFitted = true
            } else if (currentLocation != null) {
                evaluateJs("window.flyToLocation(${currentLocation.latitude}, ${currentLocation.longitude}, 16);")
                hasInitialFitted = true
            }
        }
    }

    // Function to calculate road route to a specific destination
    fun routeToDestination(destLat: Double, destLng: Double) {
        coroutineScope.launch {
            val origin = if (currentLocation != null) {
                RoadCoordinate(currentLocation.latitude, currentLocation.longitude)
            } else if (points.isNotEmpty()) {
                RoadCoordinate(points.last().latitude, points.last().longitude)
            } else if (visits.isNotEmpty()) {
                RoadCoordinate(visits.first().latitude, visits.first().longitude)
            } else null

            if (origin != null) {
                isCalculatingRoute = true
                val result = RoadRoutingService.getRouteBetween(
                    origin.latitude,
                    origin.longitude,
                    destLat,
                    destLng
                )
                activeRoadRoute = result
                isCalculatingRoute = false
                pushStreetRouteToMap(result)
                evaluateJs("window.fitAllBounds();")
            }
        }
    }

    Box(
        modifier = modifier
            .background(Color(0xFF121C24))
            .testTag("interactive_real_map_container")
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(0xFF121C24.toInt())
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        loadsImagesAutomatically = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        displayZoomControls = false
                        builtInZoomControls = false
                    }

                    // Prevent parent Compose scrollables from stealing gestures
                    setOnTouchListener { v, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                                v.parent?.requestDisallowInterceptTouchEvent(true)
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                v.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                        }
                        false
                    }

                    val bridge = RealMapJsBridge(
                        onMapLoaded = {
                            isMapLoaded = true
                        },
                        onVisitClick = { visitId ->
                            val clickedVisit = visits.find { it.id == visitId }
                            if (clickedVisit != null) {
                                selectedVisit = clickedVisit
                            }
                        },
                        onRouteToPlaceRequest = { lat, lng, _ ->
                            routeToDestination(lat, lng)
                        }
                    )
                    addJavascriptInterface(bridge, "AndroidBridge")

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isMapLoaded = true
                        }
                    }

                    webChromeClient = WebChromeClient()

                    loadUrl("file:///android_asset/map/map.html")
                    webViewRef = this
                }
            },
            update = { wv ->
                webViewRef = wv
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top Controls: Layer Selector & Real Road Mode Toggle
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 14.dp, top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Layer Selection Dropdown
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF1E293B).copy(alpha = 0.94f),
                shadowElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.35f))
            ) {
                Box {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(Color(0xFF10B981), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (currentLayer) {
                                MapLayerType.HYBRID -> "🛰️ Satellite"
                                MapLayerType.SATELLITE -> "📷 Pure Aerial"
                                MapLayerType.STREET -> "🗺️ Streets"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = { showLayerMenu = true },
                            modifier = Modifier
                                .size(26.dp)
                                .testTag("map_layer_selector_button")
                        ) {
                            Icon(
                                Icons.Default.Layers,
                                contentDescription = "Switch Map Layer",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showLayerMenu,
                        onDismissRequest = { showLayerMenu = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        MapLayerType.values().forEach { layer ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(layer.label, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                layer.iconDescription,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (currentLayer == layer) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    currentLayer = layer
                                    showLayerMenu = false
                                    evaluateJs("window.setLayerType('${layer.name}');")
                                }
                            )
                        }
                    }
                }
            }

            // Real Streets Roads Toggle Chip
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = if (snapToRealRoads) Color(0xFF0F766E).copy(alpha = 0.94f) else Color(0xFF334155).copy(alpha = 0.94f),
                shadowElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (snapToRealRoads) Color(0xFF2DD4BF) else Color.Gray.copy(alpha = 0.4f)
                ),
                modifier = Modifier
                    .clickable { snapToRealRoads = !snapToRealRoads }
                    .testTag("toggle_real_roads_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isCalculatingRoute) {
                        CircularProgressIndicator(
                            color = Color(0xFF2DD4BF),
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp)
                        )
                    } else {
                        Icon(
                            Icons.Default.DirectionsCar,
                            contentDescription = null,
                            tint = if (snapToRealRoads) Color(0xFF2DD4BF) else Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (snapToRealRoads) "🛣️ Real Roads: ON" else "📍 Direct GPS",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // Floating Action Buttons on the Right (My Location, Fit Route)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 14.dp, top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Fit All / Recenter Button
            FloatingActionButton(
                onClick = {
                    evaluateJs("window.fitAllBounds();")
                },
                modifier = Modifier
                    .size(44.dp)
                    .testTag("map_fit_bounds_button"),
                shape = CircleShape,
                containerColor = Color(0xFF1E293B).copy(alpha = 0.92f),
                contentColor = Color(0xFF38BDF8),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
            ) {
                Icon(
                    Icons.Default.CenterFocusStrong,
                    contentDescription = "Fit All Points",
                    modifier = Modifier.size(20.dp)
                )
            }

            // My Location Button
            FloatingActionButton(
                onClick = {
                    if (currentLocation != null) {
                        evaluateJs("window.flyToLocation(${currentLocation.latitude}, ${currentLocation.longitude}, 17);")
                    } else {
                        evaluateJs("window.fitAllBounds();")
                    }
                },
                modifier = Modifier
                    .size(44.dp)
                    .testTag("map_my_location_button"),
                shape = CircleShape,
                containerColor = Color(0xFF1E293B).copy(alpha = 0.92f),
                contentColor = Color(0xFF00E5FF),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
            ) {
                Icon(
                    Icons.Default.MyLocation,
                    contentDescription = "My Live Location",
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Bottom Selected Visit Card Popup with Real Street Route Action
        AnimatedVisibility(
            visible = selectedVisit != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            selectedVisit?.let { visit ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("visit_preview_bottom_sheet"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (visit.departureTimeUtc == null) Color(0xFF10B981) else Color(0xFFF97316),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = visit.placeName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = visit.address,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                            }

                            IconButton(
                                onClick = { selectedVisit = null },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close preview",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val durMin = visit.durationSeconds / 60
                        val durStr = if (durMin >= 60) "${durMin / 60}h ${durMin % 60}m" else "$durMin min"
                        val isOngoing = visit.departureTimeUtc == null

                        Text(
                            text = if (isOngoing) "⏱️ Ongoing • ${durStr} so far" else "⏱️ Visited for $durStr",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isOngoing) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Button to Route on Real Roads
                            FilledTonalButton(
                                onClick = {
                                    routeToDestination(visit.latitude, visit.longitude)
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("route_to_place_button")
                            ) {
                                Icon(
                                    Icons.Default.DirectionsCar,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Real Road Route", maxLines = 1)
                            }

                            // Button to View Place Details Screen
                            FilledTonalButton(
                                onClick = {
                                    onVisitSelected(visit)
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("visit_details_button")
                            ) {
                                Text("Details")
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
