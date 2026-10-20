package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.service.LocationTrackingService
import com.example.ui.navigation.Screen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.MapViewScreen
import com.example.ui.screens.PermissionsPromptDialog
import com.example.ui.screens.PlaceDetailsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TimelineScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val currentScreen by viewModel.currentScreen.collectAsState()
                val showPermissionsDialog by viewModel.showPermissionsDialog.collectAsState()
                val isTrackingEnabled by viewModel.isTrackingEnabled.collectAsState()
                val hasCompletedOnboarding by viewModel.hasCompletedOnboarding.collectAsState()
                val isServiceRunning by viewModel.isServiceRunning.collectAsState()
                val isTrackingPaused by viewModel.isTrackingPaused.collectAsState()

                // Check permissions on startup
                LaunchedEffect(Unit) {
                    val fineLocationGranted = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

                    if (!fineLocationGranted && !hasCompletedOnboarding) {
                        viewModel.showPermissionsDialog(true)
                    } else if (fineLocationGranted && isTrackingEnabled && !isServiceRunning && !isTrackingPaused) {
                        // Automatically resume ongoing tracking service
                        LocationTrackingService.startService(this@MainActivity)
                    }
                }

                if (showPermissionsDialog) {
                    PermissionsPromptDialog(
                        viewModel = viewModel,
                        onDismiss = { viewModel.showPermissionsDialog(false) }
                    )
                }

                val showBottomNav = currentScreen !is Screen.PlaceDetails

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        if (showBottomNav) {
                            CenterAlignedTopAppBar(
                                title = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "DailyTrack",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleLarge
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        val beaconColor = when {
                                            !isTrackingEnabled -> Color.Gray
                                            isTrackingPaused -> MaterialTheme.colorScheme.tertiary
                                            else -> MaterialTheme.colorScheme.primary
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(9.dp)
                                                .clip(CircleShape)
                                                .background(beaconColor)
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            )
                        }
                    },
                    bottomBar = {
                        if (showBottomNav) {
                            DailyTrackBottomNavigation(
                                currentScreen = currentScreen,
                                onSelectScreen = { screen ->
                                    viewModel.navigateTo(screen)
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    Crossfade(
                        targetState = currentScreen,
                        label = "screen_transition",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) { screen ->
                        when (screen) {
                            is Screen.Dashboard -> DashboardScreen(
                                viewModel = viewModel,
                                onNavigate = { viewModel.navigateTo(it) }
                            )
                            is Screen.Timeline -> TimelineScreen(
                                viewModel = viewModel,
                                onNavigate = { viewModel.navigateTo(it) }
                            )
                            is Screen.MapView -> MapViewScreen(
                                viewModel = viewModel,
                                onNavigate = { viewModel.navigateTo(it) }
                            )
                            is Screen.History -> HistoryScreen(
                                viewModel = viewModel,
                                onNavigate = { viewModel.navigateTo(it) }
                            )
                            is Screen.Settings -> SettingsScreen(
                                viewModel = viewModel
                            )
                            is Screen.PlaceDetails -> PlaceDetailsScreen(
                                visitId = screen.visitId,
                                viewModel = viewModel,
                                onNavigateBack = { viewModel.navigateBack() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyTrackBottomNavigation(
    currentScreen: Screen,
    onSelectScreen: (Screen) -> Unit
) {
    NavigationBar(modifier = Modifier.testTag("bottom_nav_bar")) {
        NavigationBarItem(
            selected = currentScreen is Screen.Dashboard,
            onClick = { onSelectScreen(Screen.Dashboard) },
            icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
            label = { Text("Dashboard") },
            modifier = Modifier.testTag("nav_item_dashboard")
        )
        NavigationBarItem(
            selected = currentScreen is Screen.Timeline,
            onClick = { onSelectScreen(Screen.Timeline) },
            icon = { Icon(Icons.Default.Timeline, contentDescription = "Timeline") },
            label = { Text("Timeline") },
            modifier = Modifier.testTag("nav_item_timeline")
        )
        NavigationBarItem(
            selected = currentScreen is Screen.MapView,
            onClick = { onSelectScreen(Screen.MapView) },
            icon = { Icon(Icons.Default.Map, contentDescription = "Map") },
            label = { Text("Map") },
            modifier = Modifier.testTag("nav_item_map")
        )
        NavigationBarItem(
            selected = currentScreen is Screen.History,
            onClick = { onSelectScreen(Screen.History) },
            icon = { Icon(Icons.Default.History, contentDescription = "History") },
            label = { Text("History") },
            modifier = Modifier.testTag("nav_item_history")
        )
        NavigationBarItem(
            selected = currentScreen is Screen.Settings,
            onClick = { onSelectScreen(Screen.Settings) },
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text("Settings") },
            modifier = Modifier.testTag("nav_item_settings")
        )
    }
}
