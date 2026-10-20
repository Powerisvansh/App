package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val summary by viewModel.todaySummary.collectAsState()
    val ongoingVisit by viewModel.ongoingVisit.collectAsState()
    val isTrackingEnabled by viewModel.isTrackingEnabled.collectAsState()
    val isTrackingPaused by viewModel.isTrackingPaused.collectAsState()
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    val serviceStatus by viewModel.serviceTrackingStatus.collectAsState()
    val lastLocationInfo by viewModel.lastLocationInfo.collectAsState()
    val useMetric by viewModel.useMetricUnits.collectAsState()

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Status & Control Banner
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    !isTrackingEnabled -> MaterialTheme.colorScheme.surfaceVariant
                    isTrackingPaused -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.primaryContainer
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val beaconColor = when {
                            !isTrackingEnabled -> Color.Gray
                            isTrackingPaused -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        }

                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(beaconColor)
                        )

                        Text(
                            text = when {
                                !isTrackingEnabled -> "Tracking Inactive"
                                isTrackingPaused -> "Tracking Paused"
                                else -> "Tracking Active"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                !isTrackingEnabled -> MaterialTheme.colorScheme.onSurfaceVariant
                                isTrackingPaused -> MaterialTheme.colorScheme.onTertiaryContainer
                                else -> MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        )
                    }

                    if (isTrackingEnabled) {
                        FilledTonalButton(
                            onClick = {
                                if (isTrackingPaused) viewModel.resumeTracking() else viewModel.pauseTracking()
                            },
                            modifier = Modifier.testTag("toggle_pause_button")
                        ) {
                            Icon(
                                if (isTrackingPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isTrackingPaused) "Resume" else "Pause")
                        }
                    } else {
                        Button(
                            onClick = { viewModel.showPermissionsDialog(true) },
                            modifier = Modifier.testTag("enable_tracking_button")
                        ) {
                            Text("Setup Permissions")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (isTrackingEnabled) serviceStatus else "Grant location permissions to automatically record your visits and routes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (lastLocationInfo.placeName.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Real Location: ${lastLocationInfo.placeName}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
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

                if (isTrackingEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = { viewModel.refreshRealLocation() },
                        modifier = Modifier.fillMaxWidth().testTag("refresh_real_location_button")
                    ) {
                        Icon(Icons.Default.GpsFixed, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Detect Real Location Now")
                    }
                }
            }
        }

        // 2. Ongoing Visit Card (if currently dwelling)
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "CURRENT LOCATION",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                        }
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

                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Details",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // 3. Today's Key Metrics Grid
        Text(
            text = "Today's Metrics",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val distanceDisplay = if (useMetric) {
                String.format(Locale.US, "%.1f km", summary.totalDistanceMeters / 1000.0)
            } else {
                String.format(Locale.US, "%.1f mi", summary.totalDistanceMeters / 1609.34)
            }

            MetricCard(
                icon = Icons.Default.Route,
                value = distanceDisplay,
                label = "Distance",
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.weight(1f)
            )

            MetricCard(
                icon = Icons.Default.LocationOn,
                value = "${summary.totalPlacesVisited}",
                label = "Places Visited",
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val outsideHours = summary.timeOutsideSeconds / 3600
            val outsideMins = (summary.timeOutsideSeconds % 3600) / 60
            val outsideStr = if (outsideHours > 0) "${outsideHours}h ${outsideMins}m" else "${outsideMins}m"

            val dwellHours = summary.stationaryTimeSeconds / 3600
            val dwellMins = (summary.stationaryTimeSeconds % 3600) / 60
            val dwellStr = if (dwellHours > 0) "${dwellHours}h ${dwellMins}m" else "${dwellMins}m"

            MetricCard(
                icon = Icons.Default.DirectionsWalk,
                value = outsideStr,
                label = "Time Moving",
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.weight(1f)
            )

            MetricCard(
                icon = Icons.Default.AccessTime,
                value = dwellStr,
                label = "Time Stationary",
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }

        // 4. Quick Action Tiles
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
                        text = "Map View",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Route path & place pins",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 5. Privacy & Zero-Cloud Guarantee Badge
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
