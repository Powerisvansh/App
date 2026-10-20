package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.VisitEntity
import com.example.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceDetailsScreen(
    visitId: Long,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        onNavigateBack()
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val visit by viewModel.getVisitById(visitId).collectAsState(initial = null)
    val daySummary by viewModel.selectedDaySummary.collectAsState()

    var showEditDialog by remember { mutableStateOf(false) }
    var editNameText by remember { mutableStateOf("") }
    var editAddressText by remember { mutableStateOf("") }

    var showMergeDialog by remember { mutableStateOf(false) }
    var selectedMergeCandidateId by remember { mutableStateOf<Long?>(null) }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Place Details") },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showDeleteConfirmDialog = true },
                        modifier = Modifier.testTag("delete_visit_button")
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Visit",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (visit == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Visit record not found or was deleted.")
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onNavigateBack) {
                    Text("Return")
                }
            }
            return@Scaffold
        }

        val v = visit!!
        val isOngoing = v.departureTimeUtc == null
        val timeFmt = SimpleDateFormat("EEEE, MMM d, yyyy 'at' h:mm:ss a", Locale.getDefault())

        val arrivalFormatted = timeFmt.format(Date(v.arrivalTimeUtc))
        val departureFormatted = if (v.departureTimeUtc != null) {
            timeFmt.format(Date(v.departureTimeUtc))
        } else "Still Here (Ongoing Visit)"

        val durMin = v.durationSeconds / 60
        val durHours = durMin / 60
        val durRemMin = durMin % 60
        val durationFormatted = if (durHours > 0) "${durHours}h ${durRemMin}m" else "${durRemMin} minutes"

        val otherVisitsOnDay = daySummary.visits.filter { it.id != v.id }

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Header Card with Place Name and Address
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = v.placeName,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = v.address,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = {
                                editNameText = v.placeName
                                editAddressText = v.address
                                showEditDialog = true
                            },
                            modifier = Modifier.testTag("edit_place_button")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Place Details")
                        }
                    }

                    if (v.isUserEdited) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Custom Label (User Edited)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // 2. Time & Duration Card
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Time & Duration",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    DetailItem(label = "Arrival Time", value = arrivalFormatted)
                    Spacer(modifier = Modifier.height(8.dp))
                    DetailItem(label = "Departure Time", value = departureFormatted)
                    Spacer(modifier = Modifier.height(8.dp))
                    DetailItem(label = "Total Duration", value = durationFormatted)
                }
            }

            // 3. Location Precision & Confidence Card
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.GpsFixed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Location & Uncertainty",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    DetailItem(
                        label = "GPS Coordinates",
                        value = String.format(Locale.US, "%.5f, %.5f", v.latitude, v.longitude)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DetailItem(
                        label = "GPS Accuracy",
                        value = "±${String.format(Locale.US, "%.1f", v.accuracy)} meters"
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val confidencePct = (v.confidence * 100).toInt()
                    val confidenceText = when {
                        v.confidence >= 0.85f -> "$confidencePct% (High Confidence)"
                        v.confidence >= 0.60f -> "$confidencePct% (Moderate Confidence)"
                        else -> "$confidencePct% (Low / GPS Jitter)"
                    }

                    DetailItem(label = "Visit Confidence", value = confidenceText)
                    Spacer(modifier = Modifier.height(8.dp))
                    DetailItem(label = "Clustered GPS Points", value = "${v.pointCount} readings")

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val mapUri = android.net.Uri.parse("geo:${v.latitude},${v.longitude}?q=${v.latitude},${v.longitude}(${android.net.Uri.encode(v.placeName)})")
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, mapUri)
                            try {
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.fillMaxWidth().testTag("open_place_in_google_maps_button")
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Place in Google Maps")
                    }
                }
            }

            // 4. Merge duplicate locations action
            if (otherVisitsOnDay.isNotEmpty()) {
                OutlinedButton(
                    onClick = {
                        selectedMergeCandidateId = otherVisitsOnDay.firstOrNull()?.id
                        showMergeDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("merge_visit_button")
                ) {
                    Icon(Icons.Default.CallMerge, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Merge With Duplicate Visit")
                }
            }
        }
    }

    // Edit Name & Address Dialog
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Correct Place Name") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editNameText,
                        onValueChange = { editNameText = it },
                        label = { Text("Place Name") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_place_name_input")
                    )
                    OutlinedTextField(
                        value = editAddressText,
                        onValueChange = { editAddressText = it },
                        label = { Text("Address / Description") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_place_address_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editNameText.isNotBlank()) {
                            viewModel.updateVisit(visitId, editNameText, editAddressText)
                        }
                        showEditDialog = false
                    },
                    modifier = Modifier.testTag("save_edit_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Merge Dialog
    if (showMergeDialog && visit != null) {
        val candidates = daySummary.visits.filter { it.id != visitId }
        AlertDialog(
            onDismissRequest = { showMergeDialog = false },
            title = { Text("Merge Duplicate Location") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Select another visit from today to merge with '${visit!!.placeName}'. Their durations and point clusters will be combined into a single continuous visit record.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    candidates.forEach { candidate ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = selectedMergeCandidateId == candidate.id,
                                onClick = { selectedMergeCandidateId = candidate.id }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(candidate.placeName, fontWeight = FontWeight.Bold)
                                Text(candidate.address, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedMergeCandidateId?.let { targetId ->
                            viewModel.mergeVisits(visitId, targetId)
                        }
                        showMergeDialog = false
                        onNavigateBack()
                    },
                    enabled = selectedMergeCandidateId != null
                ) {
                    Text("Merge Records")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMergeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete This Visit?") },
            text = { Text("Are you sure you want to remove this visit record? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteVisit(visitId)
                        showDeleteConfirmDialog = false
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DetailItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
