package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.LocationPointEntity
import com.example.data.local.entity.VisitEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

@Composable
fun InteractiveRouteCanvas(
    points: List<LocationPointEntity>,
    visits: List<VisitEntity>,
    modifier: Modifier = Modifier,
    onVisitSelected: (VisitEntity) -> Unit = {}
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var selectedVisit by remember { mutableStateOf<VisitEntity?>(null) }

    // Map screen projection coordinates for visits to detect taps
    var visitScreenPositions by remember { mutableStateOf<List<Pair<VisitEntity, Offset>>>(emptyList()) }

    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.3f, 15f)
        offset += offsetChange
    }

    // Auto-fit bounds when data changes
    fun fitBounds(canvasWidth: Float, canvasHeight: Float) {
        if (points.isEmpty() && visits.isEmpty()) {
            scale = 1f
            offset = Offset.Zero
            return
        }

        val allLats = points.map { it.latitude } + visits.map { it.latitude }
        val allLons = points.map { it.longitude } + visits.map { it.longitude }

        val minLat = allLats.minOrNull() ?: 0.0
        val maxLat = allLats.maxOrNull() ?: 0.0
        val minLon = allLons.minOrNull() ?: 0.0
        val maxLon = allLons.maxOrNull() ?: 0.0

        val latSpan = max(0.0005, maxLat - minLat)
        val lonSpan = max(0.0005, maxLon - minLon)

        val padding = 120f
        val usableW = max(100f, canvasWidth - padding * 2)
        val usableH = max(100f, canvasHeight - padding * 2)

        val scaleX = (usableW / lonSpan).toFloat()
        val scaleY = (usableH / latSpan).toFloat()
        val optimalScale = min(scaleX, scaleY).coerceIn(500f, 400000f)

        scale = 1f
        offset = Offset.Zero
    }

    BoxWithConstraints(modifier = modifier) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        val allLats = points.map { it.latitude } + visits.map { it.latitude }
        val allLons = points.map { it.longitude } + visits.map { it.longitude }

        val minLat = allLats.minOrNull() ?: 37.7749
        val maxLat = allLats.maxOrNull() ?: 37.7894
        val minLon = allLons.minOrNull() ?: -122.4194
        val maxLon = allLons.maxOrNull() ?: -122.3952

        val centerLat = (minLat + maxLat) / 2.0
        val centerLon = (minLon + maxLon) / 2.0

        val latSpan = max(0.002, maxLat - minLat)
        val lonSpan = max(0.002, maxLon - minLon)

        val baseZoom = min(
            (widthPx * 0.7f) / lonSpan.toFloat(),
            (heightPx * 0.7f) / latSpan.toFloat()
        ).coerceIn(1000f, 600000f)

        // Helper to convert lat/lon to canvas x/y
        fun project(lat: Double, lon: Double): Offset {
            val cx = widthPx / 2f + offset.x
            val cy = heightPx / 2f + offset.y
            val x = cx + ((lon - centerLon) * baseZoom * scale).toFloat()
            // Inverted Y: latitude increases north (upwards)
            val y = cy - ((lat - centerLat) * baseZoom * scale).toFloat()
            return Offset(x, y)
        }

        val primaryColor = MaterialTheme.colorScheme.primary
        val secondaryColor = MaterialTheme.colorScheme.secondary
        val tertiaryColor = MaterialTheme.colorScheme.tertiary
        val surfaceColor = MaterialTheme.colorScheme.surface
        val onSurfaceColor = MaterialTheme.colorScheme.onSurface
        val outlineColor = MaterialTheme.colorScheme.outlineVariant

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .transformable(state = transformState)
                .pointerInput(Unit) {
                    detectTapGestures { tapOffset ->
                        // Check if tap hit any visit pin
                        val hit = visitScreenPositions.find { (_, pinOffset) ->
                            hypot((tapOffset.x - pinOffset.x).toDouble(), (tapOffset.y - pinOffset.y).toDouble()) <= 48.0
                        }
                        if (hit != null) {
                            selectedVisit = hit.first
                            onVisitSelected(hit.first)
                        } else {
                            selectedVisit = null
                        }
                    }
                }
                .testTag("interactive_route_canvas")
        ) {
            val canvasW = size.width
            val canvasH = size.height

            // 1. Draw subtle coordinate grid background
            val gridStep = 80f * (scale.coerceIn(0.5f, 2f))
            var gx = (offset.x % gridStep)
            while (gx < canvasW) {
                drawLine(
                    color = outlineColor.copy(alpha = 0.25f),
                    start = Offset(gx, 0f),
                    end = Offset(gx, canvasH),
                    strokeWidth = 1f
                )
                gx += gridStep
            }
            var gy = (offset.y % gridStep)
            while (gy < canvasH) {
                drawLine(
                    color = outlineColor.copy(alpha = 0.25f),
                    start = Offset(0f, gy),
                    end = Offset(canvasW, gy),
                    strokeWidth = 1f
                )
                gy += gridStep
            }

            // 2. Draw route polyline if points exist
            if (points.size >= 2) {
                val routePath = Path()
                val firstPt = project(points.first().latitude, points.first().longitude)
                routePath.moveTo(firstPt.x, firstPt.y)

                for (i in 1 until points.size) {
                    val pt = project(points[i].latitude, points[i].longitude)
                    routePath.lineTo(pt.x, pt.y)
                }

                // Route shadow / halo
                drawPath(
                    path = routePath,
                    color = primaryColor.copy(alpha = 0.2f),
                    style = Stroke(
                        width = 10f * scale.coerceIn(0.8f, 2f),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // Route main line
                drawPath(
                    path = routePath,
                    color = primaryColor,
                    style = Stroke(
                        width = 4f * scale.coerceIn(0.8f, 2f),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // Draw track point dots
                for (ptEntity in points) {
                    val p = project(ptEntity.latitude, ptEntity.longitude)
                    drawCircle(
                        color = Color.White,
                        radius = 3.5f * scale.coerceIn(0.6f, 1.5f),
                        center = p
                    )
                    drawCircle(
                        color = primaryColor,
                        radius = 2f * scale.coerceIn(0.6f, 1.5f),
                        center = p
                    )
                }
            }

            // 3. Draw visited place pins & compute screen positions
            val currentPositions = mutableListOf<Pair<VisitEntity, Offset>>()

            visits.forEachIndexed { index, visit ->
                val p = project(visit.latitude, visit.longitude)
                currentPositions.add(Pair(visit, p))

                val isOngoing = visit.departureTimeUtc == null
                val isSelected = selectedVisit?.id == visit.id
                val pinRadius = if (isSelected) 22f else 18f
                val pinColor = if (isOngoing) tertiaryColor else secondaryColor

                // Outer aura
                drawCircle(
                    color = pinColor.copy(alpha = if (isSelected) 0.35f else 0.18f),
                    radius = pinRadius * 2f,
                    center = p
                )

                // Pin circle background
                drawCircle(
                    color = Color.White,
                    radius = pinRadius + 3f,
                    center = p
                )
                drawCircle(
                    color = pinColor,
                    radius = pinRadius,
                    center = p
                )

                // Pin label number
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = (13f * 2.5f)
                    textAlign = android.graphics.Paint.Align.CENTER
                    isFakeBoldText = true
                    isAntiAlias = true
                }
                drawContext.canvas.nativeCanvas.drawText(
                    "${index + 1}",
                    p.x,
                    p.y + 11f,
                    paint
                )

                // Place name label under pin
                val labelPaint = android.graphics.Paint().apply {
                    color = if (isSelected) android.graphics.Color.BLACK else android.graphics.Color.DKGRAY
                    textSize = 28f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isFakeBoldText = isSelected
                    isAntiAlias = true
                }
                val shortName = if (visit.placeName.length > 18) {
                    visit.placeName.take(16) + "…"
                } else visit.placeName

                drawContext.canvas.nativeCanvas.drawText(
                    shortName,
                    p.x,
                    p.y + pinRadius + 26f,
                    labelPaint
                )
            }
            visitScreenPositions = currentPositions

            // 4. Draw latest user location dot if available
            if (points.isNotEmpty()) {
                val latest = points.last()
                val lp = project(latest.latitude, latest.longitude)

                drawCircle(
                    color = Color(0xFF38BDF8).copy(alpha = 0.25f),
                    radius = 32f,
                    center = lp
                )
                drawCircle(
                    color = Color.White,
                    radius = 10f,
                    center = lp
                )
                drawCircle(
                    color = Color(0xFF0284C7),
                    radius = 7f,
                    center = lp
                )
            }
        }

        // Map control overlays (Zoom in, Zoom out, Recenter)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                tonalElevation = 4.dp,
                shadowElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                IconButton(
                    onClick = { scale = (scale * 1.35f).coerceAtMost(15f) },
                    modifier = Modifier.testTag("zoom_in_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Zoom In")
                }
            }

            Surface(
                shape = CircleShape,
                tonalElevation = 4.dp,
                shadowElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                IconButton(
                    onClick = { scale = (scale / 1.35f).coerceAtLeast(0.3f) },
                    modifier = Modifier.testTag("zoom_out_button")
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
                }
            }

            Surface(
                shape = CircleShape,
                tonalElevation = 4.dp,
                shadowElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                IconButton(
                    onClick = {
                        scale = 1f
                        offset = Offset.Zero
                    },
                    modifier = Modifier.testTag("recenter_button")
                ) {
                    Icon(Icons.Default.CenterFocusStrong, contentDescription = "Recenter Map")
                }
            }
        }

        // Selected visit bottom popup card
        AnimatedVisibility(
            visible = selectedVisit != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            selectedVisit?.let { v ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("selected_visit_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = v.placeName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(onClick = { selectedVisit = null }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = v.address,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                            val arrival = timeFormat.format(Date(v.arrivalTimeUtc))
                            val departure = if (v.departureTimeUtc != null) {
                                timeFormat.format(Date(v.departureTimeUtc))
                            } else "Ongoing"

                            Text(
                                text = "Time: $arrival – $departure",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            val durMin = v.durationSeconds / 60
                            Text(
                                text = "Duration: ${durMin}m",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
