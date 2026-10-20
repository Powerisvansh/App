package com.example

import android.app.Application
import com.example.data.local.DailyTrackDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.LocationRepository
import com.example.data.worker.DataRetentionWorker
import com.example.domain.detector.VisitDetector
import com.example.domain.geocoder.GeocodingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DailyTrackApplication : Application() {

    val applicationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val database by lazy { DailyTrackDatabase.getDatabase(this) }
    val preferencesRepository by lazy { UserPreferencesRepository(this) }
    val geocodingService by lazy { GeocodingService(this, database.geocodeCacheDao()) }
    val visitDetector by lazy {
        VisitDetector(
            visitDao = database.visitDao(),
            locationPointDao = database.locationPointDao(),
            geocodingService = geocodingService
        )
    }
    val locationRepository by lazy {
        LocationRepository(
            locationPointDao = database.locationPointDao(),
            visitDao = database.visitDao(),
            geocodeCacheDao = database.geocodeCacheDao(),
            geocodingService = geocodingService,
            visitDetector = visitDetector
        )
    }

    override fun onCreate() {
        super.onCreate()
        try {
            DataRetentionWorker.schedule(this)
        } catch (_: Exception) {}

        // Ensure database has no fake/mock sample data
        applicationScope.launch {
            cleanAnyMockData()
        }
    }

    private suspend fun cleanAnyMockData() {
        try {
            // Remove any leftover sample data from previous template runs
            val allVisits = database.visitDao().getAllVisitsSync()
            val mockNames = listOf(
                "Sweet Home Residence",
                "Blue Bottle Artisan Cafe",
                "Metro Innovation Campus",
                "Salesforce Rooftop Park"
            )
            for (v in allVisits) {
                if (mockNames.any { v.placeName.contains(it, ignoreCase = true) }) {
                    database.visitDao().deleteById(v.id)
                }
            }
        } catch (_: Exception) {}
    }
}
