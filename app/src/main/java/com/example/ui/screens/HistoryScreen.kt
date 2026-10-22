package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.TripRecord
import com.example.ui.navigation.Screen
import com.example.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: MainViewModel,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val allTrips by viewModel.allTripTracks.collectAsState()
    val useMetric by viewModel.useMetricUnits.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) } // 0: Trips & Tracks, 1: Visited Places

    val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())

    // Filter trips by search query
    val filteredTrips = remember(allTrips, searchQuery) {
        if (searchQuery.isBlank()) {
            allTrips
        } else {
            val q = searchQuery.trim().lowercase(Locale.getDefault())
            allTrips.filter { trip ->
                trip.dateLabel.lowercase(Locale.getDefault()).contains(q) ||
                trip.startPlaceName.lowercase(Locale.getDefault()).contains(q) ||
                trip.destinationPlaceName.lowercase(Locale.getDefault()).contains(q)
            }
        }
    }

    // Lifetime summary stats
    val totalAllDistanceMeters = allTrips.sumOf { it.totalDistanceMeters }
    val totalAllDurationSec = allTrips.sumOf { it.durationSeconds }
    val totalAllHours = totalAllDurationSec / 3600
    val totalAllMins = (totalAllDurationSec % 3600) / 60
    val totalDurationStr = if (totalAllHours > 0) "${totalAllHours}h ${totalAllMins}m" else "${totalAllMins}m"

    val totalDistanceDisplay = if (useMetric) {
        String.format(Locale.US, "%.1f km", totalAllDistanceMeters / 1000.0)
    } else {
        String.format(Locale.US, "%.1f mi", totalAllDistanceMeters / 1609.34)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear Search")
                    }
                }
            },
            placeholder = { Text("Search trips, dates, places, routes...") },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("history_search_input")
        )

        // All-Time Summary Header Card
        ElevatedCard(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("history_summary_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TOTAL RECORDED TRACKS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${allTrips.size} Trips",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Distance",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                        )
                        Text(
                            text = totalDistanceDisplay,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Duration",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                        )
                        Text(
                            text = totalDurationStr,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // View Tabs: Previous Trips & Tracks vs Visited Places
        TabRow(
            selectedTabIndex = selectedTabIndex,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
        ) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 },
                text = { Text("Previous Trips (${filteredTrips.size})", fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Default.Route, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.testTag("tab_previous_trips")
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                text = { Text("Places Visited (${searchResults.size})", fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.testTag("tab_visited_places")
            )
        }

        // Tab Content
        if (selectedTabIndex == 0) {
            // =========================================================================
            // Tab 0: PREVIOUS TRIPS & RECORDED TRACKS LIST
            // =========================================================================
            if (filteredTrips.isEmpty()) {
                EmptyTripsState(searchQuery = searchQuery, onStartTracking = { viewModel.startTracking() })
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(filteredTrips, key = { it.id }) { trip ->
                        TripTrackCard(
                            trip = trip,
                            useMetric = useMetric,
                            onViewOnMap = {
                                viewModel.setSelectedDate(trip.dateTimestampUtc)
                                onNavigate(Screen.MapView)
                            },
                            onViewTimeline = {
                                viewModel.setSelectedDate(trip.dateTimestampUtc)
                                onNavigate(Screen.Timeline)
                            }
                        )
                    }
                }
            }
        } else {
            // =========================================================================
            // Tab 1: VISITED PLACES SEARCH & LIST
            // =========================================================================
            if (searchResults.isEmpty()) {
                EmptyPlacesState(searchQuery = searchQuery)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(searchResults, key = { it.id }) { visit ->
                        val visitDate = dateFmt.format(Date(visit.arrivalTimeUtc))
                        val arrival = timeFmt.format(Date(visit.arrivalTimeUtc))
                        val departure = if (visit.departureTimeUtc != null) {
                            timeFmt.format(Date(visit.departureTimeUtc))
                        } else "Ongoing"

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigate(Screen.PlaceDetails(visit.id)) }
                                .testTag("visit_history_item_${visit.id}")
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Default.LocationOn,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = visit.placeName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface
                                    ) {
                                        Text(
                                            text = visitDate,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = visit.address,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val durMin = visit.durationSeconds / 60
                                    val durHours = durMin / 60
                                    val durRemMin = durMin % 60
                                    val durationStr = if (durHours > 0) "${durHours}h ${durRemMin}m" else "${durRemMin}m"

                                    Text(
                                        text = "$arrival – $departure",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Duration: $durationStr",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dedicated Card displaying a Previous Recorded Track / Trip
 * Showing: Date, Total Distance, Duration, Journey Endpoints, and Quick Actions
 */
@Composable
fun TripTrackCard(
    trip: TripRecord,
    useMetric: Boolean,
    onViewOnMap: () -> Unit,
    onViewTimeline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val distanceDisplay = if (useMetric) {
        String.format(Locale.US, "%.1f km", trip.totalDistanceMeters / 1000.0)
    } else {
        String.format(Locale.US, "%.1f mi", trip.totalDistanceMeters / 1609.34)
    }

    val hours = trip.durationSeconds / 3600
    val mins = (trip.durationSeconds % 3600) / 60
    val durationDisplay = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"

    val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
    val startFormatted = timeFmt.format(Date(trip.startTimeUtc))
    val endFormatted = timeFmt.format(Date(trip.endTimeUtc))

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(20.dp)
            )
            .testTag("trip_track_card_${trip.id}")
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header Row: Date & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = trip.dateLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (trip.isOngoing) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "Active Trip",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "${trip.pointCount} GPS Points",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Primary Metrics: Total Distance & Duration Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Distance Metric
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
                    Column {
                        Text(
                            text = "Total Distance",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = distanceDisplay,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("trip_distance_text")
                        )
                    }
                }

                // Vertical Divider
                Box(
                    modifier = Modifier
                        .height(30.dp)
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )

                // Duration Metric
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Duration",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = durationDisplay,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.testTag("trip_duration_text")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Journey Path Endpoints: Start ➔ Destination
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = trip.startPlaceName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )

                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .size(16.dp)
                )

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = trip.destinationPlaceName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Timing and stats subtitle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$startFormatted – $endFormatted",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (trip.visitCount > 0) {
                        Text(
                            text = "${trip.visitCount} places visited",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (trip.maxSpeedKmh > 0) {
                        Text(
                            text = "Max ${trip.maxSpeedKmh.toInt()} km/h",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons: View on Map & View Timeline
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = onViewOnMap,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("trip_view_map_button")
                ) {
                    Icon(
                        Icons.Default.Map,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("View on Map", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onViewTimeline,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("trip_view_timeline_button")
                ) {
                    Icon(
                        Icons.Default.Timeline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Timeline", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun EmptyTripsState(
    searchQuery: String,
    onStartTracking: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Route,
                        contentDescription = null,
                        modifier = Modifier.size(34.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (searchQuery.isNotBlank()) "No Trips Match '$searchQuery'" else "No Recorded Trips Yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (searchQuery.isNotBlank()) {
                    "Try checking the spelling or search by date or place name."
                } else {
                    "As you travel, daily routes, total distances, and travel durations will be automatically recorded here."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onStartTracking,
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Start Tracking Now")
            }
        }
    }
}

@Composable
private fun EmptyPlacesState(searchQuery: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.LocationOn,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (searchQuery.isNotBlank()) "No Places Match '$searchQuery'" else "No Visited Places Yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Visited places will appear here once you dwell at a location for a few minutes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
