package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.DailyTrackApplication
import com.example.data.local.entity.LocationPointEntity
import com.example.data.local.entity.VisitEntity
import com.example.data.preferences.LastLocationInfo
import com.example.data.service.LocationTrackingService
import com.example.domain.model.DailySummary
import com.example.domain.model.TimelineItem
import com.example.ui.navigation.Screen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DailyTrackApplication
    private val repository = app.locationRepository
    private val prefs = app.preferencesRepository

    // Current navigation state
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Dashboard)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    // Navigation back stack for sub-screens
    private val navigationStack = mutableListOf<Screen>()

    // Selected Date in UTC milliseconds (start of day)
    private val _selectedDateUtc = MutableStateFlow(getStartOfDayUtc(System.currentTimeMillis()))
    val selectedDateUtc: StateFlow<Long> = _selectedDateUtc.asStateFlow()

    // Search query for History
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Permissions banner visibility
    private val _showPermissionsDialog = MutableStateFlow(false)
    val showPermissionsDialog: StateFlow<Boolean> = _showPermissionsDialog.asStateFlow()

    // Preferences states
    val isTrackingEnabled: StateFlow<Boolean> = prefs.isTrackingEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isTrackingPaused: StateFlow<Boolean> = prefs.isTrackingPaused
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val trackingIntervalSec: StateFlow<Int> = prefs.trackingIntervalSeconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 10)

    val stationaryRadiusMeters: StateFlow<Float> = prefs.stationaryRadiusMeters
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 45f)

    val dwellTimeMinutes: StateFlow<Int> = prefs.dwellTimeMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 3)

    val autoDeleteDays: StateFlow<Int> = prefs.autoDeleteDays
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val resumeOnBoot: StateFlow<Boolean> = prefs.resumeOnBoot
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val useMetricUnits: StateFlow<Boolean> = prefs.useMetricUnits
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val hasCompletedOnboarding: StateFlow<Boolean> = prefs.hasCompletedOnboarding
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val lastLocationInfo: StateFlow<LastLocationInfo> = prefs.lastLocationInfo
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            LastLocationInfo(0.0, 0.0, 0f, 0L, "", "Idle")
        )

    // Service tracking status
    val isServiceRunning = LocationTrackingService.isServiceRunning
    val serviceTrackingStatus = LocationTrackingService.trackingStatus

    // Today's summary
    val todaySummary: StateFlow<DailySummary> = repository.getDailySummary(
        startOfDayUtc = getStartOfDayUtc(System.currentTimeMillis()),
        endOfDayUtc = getEndOfDayUtc(System.currentTimeMillis())
    ).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        DailySummary("Today", getStartOfDayUtc(System.currentTimeMillis()), getEndOfDayUtc(System.currentTimeMillis()))
    )

    // Ongoing visit if any
    val ongoingVisit: StateFlow<VisitEntity?> = repository.getOngoingVisitFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Selected day summary
    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedDaySummary: StateFlow<DailySummary> = _selectedDateUtc.flatMapLatest { startUtc ->
        val endUtc = startUtc + 86400000L - 1L
        repository.getDailySummary(startUtc, endUtc)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        DailySummary("", 0L, 0L)
    )

    // Selected day timeline items
    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedDayTimeline: StateFlow<List<TimelineItem>> = _selectedDateUtc.flatMapLatest { startUtc ->
        val endUtc = startUtc + 86400000L - 1L
        repository.getTimelineItems(startUtc, endUtc)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Selected day route points
    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedDayRoutePoints: StateFlow<List<LocationPointEntity>> = _selectedDateUtc.flatMapLatest { startUtc ->
        val endUtc = startUtc + 86400000L - 1L
        repository.getPointsForDateRange(startUtc, endUtc)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Search results in History
    @OptIn(ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<VisitEntity>> = _searchQuery.flatMapLatest { query ->
        if (query.isBlank()) {
            repository.getAllVisitsFlow()
        } else {
            repository.searchVisits(query.trim())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun navigateTo(screen: Screen) {
        if (_currentScreen.value != screen) {
            navigationStack.add(_currentScreen.value)
            _currentScreen.value = screen
        }
    }

    fun navigateBack(): Boolean {
        if (navigationStack.isNotEmpty()) {
            _currentScreen.value = navigationStack.removeAt(navigationStack.size - 1)
            return true
        }
        if (_currentScreen.value != Screen.Dashboard) {
            _currentScreen.value = Screen.Dashboard
            return true
        }
        return false
    }

    fun shiftSelectedDate(days: Int) {
        val cal = Calendar.getInstance().apply {
            timeInMillis = _selectedDateUtc.value
            add(Calendar.DAY_OF_YEAR, days)
        }
        _selectedDateUtc.value = getStartOfDayUtc(cal.timeInMillis)
    }

    fun setSelectedDate(timeMillis: Long) {
        _selectedDateUtc.value = getStartOfDayUtc(timeMillis)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun showPermissionsDialog(show: Boolean) {
        _showPermissionsDialog.value = show
    }

    fun startTracking() {
        viewModelScope.launch {
            prefs.setTrackingEnabled(true)
            prefs.setCompletedOnboarding(true)
            LocationTrackingService.startService(getApplication())
        }
    }

    fun pauseTracking() {
        viewModelScope.launch {
            LocationTrackingService.pauseService(getApplication())
        }
    }

    fun resumeTracking() {
        viewModelScope.launch {
            LocationTrackingService.resumeService(getApplication())
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun refreshRealLocation() {
        viewModelScope.launch {
            try {
                val fused = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(app)
                fused.getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { loc ->
                        if (loc != null) {
                            viewModelScope.launch {
                                repository.recordLocation(loc)
                            }
                        }
                    }
            } catch (_: Exception) {}
        }
    }

    fun stopTracking() {
        viewModelScope.launch {
            LocationTrackingService.stopService(getApplication())
        }
    }

    fun updateTrackingInterval(seconds: Int) {
        viewModelScope.launch {
            prefs.setTrackingIntervalSeconds(seconds)
            if (isServiceRunning.value && !isTrackingPaused.value) {
                // Restart service to apply new rate
                LocationTrackingService.startService(getApplication())
            }
        }
    }

    fun updateStationaryRadius(meters: Float) {
        viewModelScope.launch {
            prefs.setStationaryRadiusMeters(meters)
        }
    }

    fun updateDwellTime(minutes: Int) {
        viewModelScope.launch {
            prefs.setDwellTimeMinutes(minutes)
        }
    }

    fun updateAutoDeleteDays(days: Int) {
        viewModelScope.launch {
            prefs.setAutoDeleteDays(days)
        }
    }

    fun updateResumeOnBoot(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setResumeOnBoot(enabled)
        }
    }

    fun updateUseMetricUnits(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setUseMetricUnits(enabled)
        }
    }

    fun updateVisit(id: Long, newName: String, newAddress: String) {
        viewModelScope.launch {
            repository.updateVisitDetails(id, newName, newAddress)
        }
    }

    fun mergeVisits(primaryId: Long, secondaryId: Long) {
        viewModelScope.launch {
            repository.mergeVisits(primaryId, secondaryId)
        }
    }

    fun deleteVisit(id: Long) {
        viewModelScope.launch {
            repository.deleteVisit(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAllData()
        }
    }

    suspend fun exportJson(): String {
        return repository.exportAsJson()
    }

    suspend fun exportCsv(): String {
        return repository.exportAsCsv()
    }

    fun getVisitById(id: Long): Flow<VisitEntity?> {
        return repository.getVisitByIdFlow(id)
    }

    companion object {
        fun getStartOfDayUtc(timeMillis: Long): Long {
            val cal = Calendar.getInstance().apply {
                this.timeInMillis = timeMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return cal.timeInMillis
        }

        fun getEndOfDayUtc(timeMillis: Long): Long {
            val cal = Calendar.getInstance().apply {
                this.timeInMillis = timeMillis
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            return cal.timeInMillis
        }
    }
}
