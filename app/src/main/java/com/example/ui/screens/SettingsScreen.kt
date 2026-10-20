package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isTrackingEnabled by viewModel.isTrackingEnabled.collectAsState()
    val isTrackingPaused by viewModel.isTrackingPaused.collectAsState()
    val intervalSec by viewModel.trackingIntervalSec.collectAsState()
    val stationaryRadius by viewModel.stationaryRadiusMeters.collectAsState()
    val dwellMinutes by viewModel.dwellTimeMinutes.collectAsState()
    val autoDeleteDays by viewModel.autoDeleteDays.collectAsState()
    val resumeOnBoot by viewModel.resumeOnBoot.collectAsState()
    val useMetric by viewModel.useMetricUnits.collectAsState()

    var showClearDataConfirm by remember { mutableStateOf(false) }
    var showBatteryGuidanceDialog by remember { mutableStateOf(false) }
    var showExportSuccessDialog by remember { mutableStateOf<String?>(null) }

    fun shareExport(data: String, mimeType: String, title: String) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, data)
            type = mimeType
        }
        val shareIntent = Intent.createChooser(sendIntent, title)
        context.startActivity(shareIntent)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Tracking Control Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Automatic Background Tracking",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Records visits & routes in the background via Foreground Service",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = isTrackingEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                viewModel.startTracking()
                            } else {
                                viewModel.stopTracking()
                            }
                        },
                        modifier = Modifier.testTag("tracking_toggle_switch")
                    )
                }

                if (isTrackingEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = DividerDefaults.color.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isTrackingPaused) "Tracking is Paused" else "Tracking is Active",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        FilledTonalButton(
                            onClick = {
                                if (isTrackingPaused) viewModel.resumeTracking() else viewModel.pauseTracking()
                            },
                            modifier = Modifier.testTag("settings_pause_resume_button")
                        ) {
                            Text(if (isTrackingPaused) "Resume" else "Pause")
                        }
                    }
                }
            }
        }

        // 2. Tracking Sensitivity & Adaptive Parameters
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Detection Sensitivity",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // GPS Update Interval
                Column {
                    Text("GPS Update Frequency", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text("Controls battery trade-off vs route granularity", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5 to "5s (High)", 10 to "10s (Balanced)", 30 to "30s (Eco)").forEach { (sec, label) ->
                            OutlinedButton(
                                onClick = { viewModel.updateTrackingInterval(sec) },
                                colors = if (intervalSec == sec) ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ) else ButtonDefaults.outlinedButtonColors()
                            ) {
                                Text(label)
                            }
                        }
                    }
                }

                // Stationary radius
                Column {
                    Text("Stationary Radius Threshold", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text("Max movement allowed before declaring arrival or departure", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(30f to "30m", 45f to "45m (Default)", 65f to "65m").forEach { (rad, label) ->
                            OutlinedButton(
                                onClick = { viewModel.updateStationaryRadius(rad) },
                                colors = if (stationaryRadius == rad) ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ) else ButtonDefaults.outlinedButtonColors()
                            ) {
                                Text(label)
                            }
                        }
                    }
                }

                // Dwell Time
                Column {
                    Text("Minimum Dwell Time", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text("Time staying stationary required to trigger a place visit", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(2 to "2 mins", 3 to "3 mins", 5 to "5 mins").forEach { (min, label) ->
                            OutlinedButton(
                                onClick = { viewModel.updateDwellTime(min) },
                                colors = if (dwellMinutes == min) ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ) else ButtonDefaults.outlinedButtonColors()
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            }
        }

        // 3. System & Lifecycle Controls
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.RestartAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "System Lifecycle & Battery",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Resume Tracking on Device Restart", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Automatically launches tracking service following boot completed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = resumeOnBoot,
                        onCheckedChange = { viewModel.updateResumeOnBoot(it) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Units of Measurement", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(if (useMetric) "Metric (Kilometers / Meters)" else "Imperial (Miles / Feet)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = { viewModel.updateUseMetricUnits(!useMetric) }) {
                        Text(if (useMetric) "km" else "mi")
                    }
                }

                OutlinedButton(
                    onClick = { showBatteryGuidanceDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.BatteryChargingFull, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Battery Optimization Guidance")
                }
            }
        }

        // 4. Data Retention & Local Storage
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Data Retention & Export",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column {
                    Text("Automatic History Cleanup", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text("Automatically purge records older than selected threshold via WorkManager", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to "Keep All", 7 to "7 Days", 30 to "30 Days", 90 to "90 Days").forEach { (days, label) ->
                            OutlinedButton(
                                onClick = { viewModel.updateAutoDeleteDays(days) },
                                colors = if (autoDeleteDays == days) ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ) else ButtonDefaults.outlinedButtonColors()
                            ) {
                                Text(label)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                val csv = viewModel.exportCsv()
                                shareExport(csv, "text/csv", "Export DailyTrack CSV")
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_csv_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export CSV")
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                val json = viewModel.exportJson()
                                shareExport(json, "application/json", "Export DailyTrack JSON")
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_json_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export JSON")
                    }
                }
            }
        }

        // 5. Danger Zone - Clear All Data
        OutlinedCard(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Clear Tracking History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Permanently deletes all visited places, GPS coordinates, geocode caches, and daily timelines from SQLite.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { showClearDataConfirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("clear_all_data_button")
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete All Stored History")
                }
            }
        }
    }

    // Battery Guidance Dialog
    if (showBatteryGuidanceDialog) {
        AlertDialog(
            onDismissRequest = { showBatteryGuidanceDialog = false },
            title = { Text("Android Battery & Doze Guidance") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Modern Android versions (and manufacturers like Samsung, Xiaomi, Pixel) impose aggressive background restrictions and Doze Mode.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "For uninterrupted background tracking:",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text("1. In Android App Settings, set Battery usage to 'Unrestricted'.")
                    Text("2. Keep the persistent notification enabled.")
                    Text("3. Avoid force-stopping the app, which revokes background services until reopened.")
                }
            },
            confirmButton = {
                Button(onClick = {
                    showBatteryGuidanceDialog = false
                    try {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                }) {
                    Text("Open App Info")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatteryGuidanceDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Clear Data Confirmation Dialog
    if (showClearDataConfirm) {
        AlertDialog(
            onDismissRequest = { showClearDataConfirm = false },
            title = { Text("Delete All History?") },
            text = { Text("This will permanently wipe all location records, visited places, and routine timelines. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllHistory()
                        showClearDataConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Confirm Wipe")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
