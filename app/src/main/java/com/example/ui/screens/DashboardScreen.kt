package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.navigation.Screen
import com.example.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val summary by viewModel.todaySummary.collectAsState()
    val ongoingVisit by viewModel.ongoingVisit.collectAsState()
    val isTrackingEnabled by viewModel.isTrackingEnabled.collectAsState()
    val isTrackingPaused by viewModel.isTrackingPaused.collectAsState()
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    val serviceStatus by viewModel.serviceTrackingStatus.collectAsState()
    val lastLocationInfo by viewModel.lastLocationInfo.collectAsState()
    val useMetric by viewModel.useMetricUnits.collectAsState()

    var showRenameCurrentDialog by remember { mutableStateOf(false) }
    var renameCurrentText by remember { mutableStateOf("") }

    // Pulsing animation for active tracking indicator
    val infiniteTransition = rememberInfiniteTransition(label = "tracking_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val scrollState = rememberScrollState()

    // Determine current logical status
    val isActuallyTracking = isTrackingEnabled && isServiceRunning && !isTrackingPaused
    val isActuallyPaused = isTrackingEnabled && (isTrackingPaused || (!isServiceRunning && isTrackingEnabled))

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // =========================================================================
        // 1. Current Tracking Status Header Card (Active / Paused / Inactive)
        // =========================================================================
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    !isTrackingEnabled -> MaterialTheme.colorScheme.surfaceVariant
                    isActuallyPaused -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f)
                    else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("status_banner_card")
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Status Badge with Live Pulsing Indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.testTag("tracking_status_chip")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isActuallyTracking) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .scale(pulseScale)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                                )
                            }
                            val indicatorColor = when {
                                !isTrackingEnabled -> Color.Gray
                                isActuallyPaused -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.primary
                            }
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(indicatorColor)
                            )
                        }

                        Column {
                            Text(
                                text = when {
                                    !isTrackingEnabled -> "Tracking Inactive"
                                    isActuallyPaused -> "Tracking Paused"
                                    else -> "Tracking Active"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = when {
                                    !isTrackingEnabled -> MaterialTheme.colorScheme.onSurfaceVariant
                                    isActuallyPaused -> MaterialTheme.colorScheme.onTertiaryContainer
                                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                                }
                            )
                            Text(
                                text = when {
                                    !isTrackingEnabled -> "GPS recording is off"
                                    isActuallyPaused -> "Standby • Battery saving"
                                    else -> "Live background recording"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    !isTrackingEnabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    isActuallyPaused -> MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                    else -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                }
                            )
                        }
                    }

                    // Quick Refresh GPS Fix
                    IconButton(
                        onClick = { viewModel.refreshRealLocation() },
                        modifier = Modifier.testTag("refresh_real_location_button")
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh GPS",
                            tint = when {
                                !isTrackingEnabled -> MaterialTheme.colorScheme.onSurfaceVariant
                                isActuallyPaused -> MaterialTheme.colorScheme.onTertiaryContainer
                                else -> MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Service Status or Real Location String
                Text(
                    text = if (isTrackingEnabled) serviceStatus else "Tap Start Tracking to automatically record your routes, visits, and timeline.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Real location & road display if known
                if (lastLocationInfo.placeName.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Real Location: ${lastLocationInfo.placeName}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (lastLocationInfo.roadName.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Route,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Road / Highway: ${lastLocationInfo.roadName}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                if (lastLocationInfo.timestampUtc > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val timeFmt = SimpleDateFormat("h:mm:ss a", Locale.getDefault())
                    val updateTime = timeFmt.format(Date(lastLocationInfo.timestampUtc))
                    Text(
                        text = "Last GPS fix: $updateTime (Accuracy ±${lastLocationInfo.accuracy.toInt()}m)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        // =========================================================================
        // 2. Prominent Start/Stop Tracking Hero Control Button
        // =========================================================================
        if (!isTrackingEnabled) {
            // Prominent START Tracking Button
            Button(
                onClick = {
                    val fineLocationGranted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

                    if (!fineLocationGranted) {
                        viewModel.showPermissionsDialog(true)
                    } else {
                        viewModel.startTracking()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .testTag("start_tracking_button")
                    .testTag("prominent_start_stop_button"),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 1.dp)
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Start Tracking",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }
        } else {
            // Prominent STOP & PAUSE / RESUME Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Prominent Stop Button
                Button(
                    onClick = { viewModel.stopTracking() },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .testTag("stop_tracking_button")
                        .testTag("prominent_start_stop_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp)
                ) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = "Stop Tracking",
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Stop",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                // Pause / Resume Button
                FilledTonalButton(
                    onClick = {
                        if (isActuallyPaused) {
                            viewModel.resumeTracking()
                        } else {
                            viewModel.pauseTracking()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .testTag("toggle_pause_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isActuallyPaused) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.tertiaryContainer
                        },
                        contentColor = if (isActuallyPaused) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        }
                    )
                ) {
                    Icon(
                        if (isActuallyPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isActuallyPaused) "Resume" else "Pause",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }

        // =========================================================================
        // 3. Prominent Summary Card of Today's Distance (Hero Metric)
        // =========================================================================
        val distanceDisplay = if (useMetric) {
            String.format(Locale.US, "%.1f km", summary.totalDistanceMeters / 1000.0)
        } else {
            String.format(Locale.US, "%.1f mi", summary.totalDistanceMeters / 1609.34)
        }

        val outsideHours = summary.timeOutsideSeconds / 3600
        val outsideMins = (summary.timeOutsideSeconds % 3600) / 60
        val outsideStr = if (outsideHours > 0) "${outsideHours}h ${outsideMins}m" else "${outsideMins}m"

        val dwellHours = summary.stationaryTimeSeconds / 3600
        val dwellMins = (summary.stationaryTimeSeconds % 3600) / 60
        val dwellStr = if (dwellHours > 0) "${dwellHours}h ${dwellMins}m" else "${dwellMins}m"

        val totalTrackedSec = (summary.timeOutsideSeconds + summary.stationaryTimeSeconds).coerceAtLeast(1L)
        val movingRatio = (summary.timeOutsideSeconds.toFloat() / totalTrackedSec).coerceIn(0f, 1f)

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(24.dp)
                )
                .testTag("distance_summary_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Route,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Today's Distance",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = "${summary.totalPlacesVisited} places visited",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Hero Distance Number
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = distanceDisplay,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("distance_value_text")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Movement vs Stationary Visual Breakdown
                Text(
                    text = "Travel activity: $outsideStr moving • $dwellStr stationary",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { movingRatio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }

        // =========================================================================
        // 4. Ongoing Visit Card (if currently dwelling at a location)
        // =========================================================================
        if (ongoingVisit != null) {
            val visit = ongoingVisit!!
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(Screen.PlaceDetails(visit.id)) }
                    .testTag("ongoing_visit_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CURRENT LOCATION",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                        Text(
                            text = visit.placeName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        val durationMin = visit.durationSeconds / 60
                        Text(
                            text = "Dwell: ${durationMin}m • ${visit.address}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                            maxLines = 1
                        )
                    }

                    IconButton(
                        onClick = {
                            renameCurrentText = visit.placeName
                            showRenameCurrentDialog = true
                        },
                        modifier = Modifier.testTag("rename_ongoing_place_button")
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit Place Name",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Details",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // =========================================================================
        // 5. Today's Key Metrics Grid
        // =========================================================================
        Text(
            text = "Today's Metrics",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                icon = Icons.Default.LocationOn,
                value = "${summary.totalPlacesVisited}",
                label = "Places Visited",
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.weight(1f)
            )

            MetricCard(
                icon = Icons.Default.DirectionsWalk,
                value = outsideStr,
                label = "Time Moving",
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                icon = Icons.Default.AccessTime,
                value = dwellStr,
                label = "Time Stationary",
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.weight(1f)
            )

            MetricCard(
                icon = Icons.Default.GpsFixed,
                value = if (isTrackingEnabled) "Active" else "Off",
                label = "GPS Tracking",
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }

        // =========================================================================
        // 6. Quick Action Navigation Cards
        // =========================================================================
        Text(
            text = "Explore & Visualize",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ElevatedCard(
                onClick = { onNavigate(Screen.Timeline) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("open_timeline_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Icon(
                        Icons.Default.Timeline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Daily Timeline",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "View arrivals & journeys",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            ElevatedCard(
                onClick = { onNavigate(Screen.MapView) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("open_map_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Icon(
                        Icons.Default.Map,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Satellite Map",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Satellite imagery & routes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // =========================================================================
        // 7. Privacy & Zero-Cloud Guarantee Badge
        // =========================================================================
        OutlinedCard(
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "100% Private On-Device Tracking",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "No AI, no analytics, no cloud transmission. Your routine stays strictly on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // =========================================================================
        // Rename Current Place Dialog
        // =========================================================================
        if (showRenameCurrentDialog && ongoingVisit != null) {
            val visit = ongoingVisit!!
            AlertDialog(
                onDismissRequest = { showRenameCurrentDialog = false },
                title = { Text("Name This Place") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Enter the real or custom name for this location (e.g., Nilokheri, Home, Office, Gym):",
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedTextField(
                            value = renameCurrentText,
                            onValueChange = { renameCurrentText = it },
                            label = { Text("Place Name") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("rename_place_input")
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (renameCurrentText.isNotBlank()) {
                                viewModel.updateVisit(visit.id, renameCurrentText.trim(), visit.address)
                            }
                            showRenameCurrentDialog = false
                        },
                        modifier = Modifier.testTag("confirm_rename_button")
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameCurrentDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun MetricCard(
    icon: ImageVector,
    value: String,
    label: String,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
