package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // Activity-level launcher for programmatic requests
    private var pendingPermissionCallback: ((Boolean) -> Unit)? = null
    private val activityPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val isGranted = fineGranted || coarseGranted

        if (isGranted) {
            viewModel.refreshRealLocation()
            viewModel.startTracking()
            LocationTrackingService.startService(this)
        }
        pendingPermissionCallback?.invoke(isGranted)
        pendingPermissionCallback = null
    }

    /**
     * Public method to request location permissions programmatically
     */
    fun requestLocationPermissions(onResult: ((Boolean) -> Unit)? = null) {
        pendingPermissionCallback = onResult
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        activityPermissionLauncher.launch(perms.toTypedArray())
    }

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

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

                val snackbarHostState = remember { SnackbarHostState() }
                val coroutineScope = rememberCoroutineScope()
                var hasPermissionDeniedBanner by remember { mutableStateOf(false) }

                // Compose Runtime Permission Launcher
                val locationPermissionsLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissionsMap ->
                    val fineGranted = permissionsMap[Manifest.permission.ACCESS_FINE_LOCATION] == true
                    val coarseGranted = permissionsMap[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                    val granted = fineGranted || coarseGranted

                    if (granted) {
                        hasPermissionDeniedBanner = false
                        viewModel.showPermissionsDialog(false)
                        viewModel.refreshRealLocation()
                        viewModel.startTracking()
                        LocationTrackingService.startService(this@MainActivity)
                    } else {
                        hasPermissionDeniedBanner = true
                        viewModel.showPermissionsDialog(false)
                        coroutineScope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = "Location permission is required to access GPS data for travel tracking.",
                                actionLabel = "Settings",
                                duration = SnackbarDuration.Long
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                openAppSettings()
                            }
                        }
                    }
                }

                // Check and request runtime location permissions on startup
                LaunchedEffect(Unit) {
                    val fineLocationGranted = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

                    if (!fineLocationGranted) {
                        val permsToRequest = mutableListOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        locationPermissionsLauncher.launch(permsToRequest.toTypedArray())
                    } else {
                        viewModel.refreshRealLocation()
                        if (isTrackingEnabled && !isServiceRunning && !isTrackingPaused) {
                            LocationTrackingService.startService(this@MainActivity)
                        }
                    }
                }

                // Secondary Permissions Prompt Dialog
                if (showPermissionsDialog) {
                    PermissionsPromptDialog(
                        viewModel = viewModel,
                        onDismiss = { viewModel.showPermissionsDialog(false) }
                    )
                }

                val showBottomNav = currentScreen !is Screen.PlaceDetails

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    topBar = {
                        if (showBottomNav) {
                            CenterAlignedTopAppBar(
                                title = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "vansh final",
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
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        // Permission Denied Warning Banner
                        AnimatedVisibility(visible = hasPermissionDeniedBanner) {
                            Card(
                                shape = RoundedCornerShape(0.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("permission_denied_banner")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Default.LocationOff,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "Location Access Needed",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                            Text(
                                                text = "Grant location to record GPS routes & places.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        TextButton(
                                            onClick = {
                                                val perms = mutableListOf(
                                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                                )
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                    perms.add(Manifest.permission.POST_NOTIFICATIONS)
                                                }
                                                locationPermissionsLauncher.launch(perms.toTypedArray())
                                            }
                                        ) {
                                            Text("Retry", fontWeight = FontWeight.Bold)
                                        }
                                        TextButton(onClick = { openAppSettings() }) {
                                            Text("Settings")
                                        }
                                    }
                                }
                            }
                        }

                        Crossfade(
                            targetState = currentScreen,
                            label = "screen_transition",
                            modifier = Modifier.fillMaxSize()
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
