package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "daily_track_preferences")

class UserPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val IS_TRACKING_ENABLED = booleanPreferencesKey("is_tracking_enabled")
        val IS_TRACKING_PAUSED = booleanPreferencesKey("is_tracking_paused")
        val TRACKING_INTERVAL_SECONDS = intPreferencesKey("tracking_interval_seconds")
        val STATIONARY_RADIUS_METERS = floatPreferencesKey("stationary_radius_meters")
        val DWELL_TIME_MINUTES = intPreferencesKey("dwell_time_minutes")
        val AUTO_DELETE_DAYS = intPreferencesKey("auto_delete_days")
        val RESUME_ON_BOOT = booleanPreferencesKey("resume_on_boot")
        val USE_METRIC_UNITS = booleanPreferencesKey("use_metric_units")
        val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")

        val LAST_LATITUDE = doublePreferencesKey("last_latitude")
        val LAST_LONGITUDE = doublePreferencesKey("last_longitude")
        val LAST_ACCURACY = floatPreferencesKey("last_accuracy")
        val LAST_UPDATE_TIME_UTC = longPreferencesKey("last_update_time_utc")
        val LAST_PLACE_NAME = stringPreferencesKey("last_place_name")
        val LAST_ROAD_NAME = stringPreferencesKey("last_road_name")
        val STATUS_MESSAGE = stringPreferencesKey("status_message")
    }

    val isTrackingEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.IS_TRACKING_ENABLED] ?: false
    }

    val isTrackingPaused: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.IS_TRACKING_PAUSED] ?: false
    }

    val trackingIntervalSeconds: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.TRACKING_INTERVAL_SECONDS] ?: 10
    }

    val stationaryRadiusMeters: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.STATIONARY_RADIUS_METERS] ?: 45f
    }

    val dwellTimeMinutes: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.DWELL_TIME_MINUTES] ?: 3
    }

    val autoDeleteDays: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.AUTO_DELETE_DAYS] ?: 0
    }

    val resumeOnBoot: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.RESUME_ON_BOOT] ?: true
    }

    val useMetricUnits: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.USE_METRIC_UNITS] ?: true
    }

    val hasCompletedOnboarding: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.HAS_COMPLETED_ONBOARDING] ?: false
    }

    val lastLocationInfo: Flow<LastLocationInfo> = context.dataStore.data.map { prefs ->
        LastLocationInfo(
            latitude = prefs[PreferencesKeys.LAST_LATITUDE] ?: 0.0,
            longitude = prefs[PreferencesKeys.LAST_LONGITUDE] ?: 0.0,
            accuracy = prefs[PreferencesKeys.LAST_ACCURACY] ?: 0f,
            timestampUtc = prefs[PreferencesKeys.LAST_UPDATE_TIME_UTC] ?: 0L,
            placeName = prefs[PreferencesKeys.LAST_PLACE_NAME] ?: "",
            roadName = prefs[PreferencesKeys.LAST_ROAD_NAME] ?: "",
            statusMessage = prefs[PreferencesKeys.STATUS_MESSAGE] ?: "Tracking Idle"
        )
    }

    suspend fun setTrackingEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.IS_TRACKING_ENABLED] = enabled
            if (enabled) {
                prefs[PreferencesKeys.IS_TRACKING_PAUSED] = false
            }
        }
    }

    suspend fun setTrackingPaused(paused: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.IS_TRACKING_PAUSED] = paused
        }
    }

    suspend fun setTrackingIntervalSeconds(seconds: Int) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.TRACKING_INTERVAL_SECONDS] = seconds
        }
    }

    suspend fun setStationaryRadiusMeters(radius: Float) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.STATIONARY_RADIUS_METERS] = radius
        }
    }

    suspend fun setDwellTimeMinutes(minutes: Int) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.DWELL_TIME_MINUTES] = minutes
        }
    }

    suspend fun setAutoDeleteDays(days: Int) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.AUTO_DELETE_DAYS] = days
        }
    }

    suspend fun setResumeOnBoot(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.RESUME_ON_BOOT] = enabled
        }
    }

    suspend fun setUseMetricUnits(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.USE_METRIC_UNITS] = enabled
        }
    }

    suspend fun setCompletedOnboarding(completed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.HAS_COMPLETED_ONBOARDING] = completed
        }
    }

    suspend fun updateLastLocation(
        latitude: Double,
        longitude: Double,
        accuracy: Float,
        timestampUtc: Long,
        placeName: String,
        roadName: String = "",
        statusMessage: String
    ) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.LAST_LATITUDE] = latitude
            prefs[PreferencesKeys.LAST_LONGITUDE] = longitude
            prefs[PreferencesKeys.LAST_ACCURACY] = accuracy
            prefs[PreferencesKeys.LAST_UPDATE_TIME_UTC] = timestampUtc
            prefs[PreferencesKeys.LAST_PLACE_NAME] = placeName
            prefs[PreferencesKeys.LAST_ROAD_NAME] = roadName
            prefs[PreferencesKeys.STATUS_MESSAGE] = statusMessage
        }
    }
}

data class LastLocationInfo(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestampUtc: Long,
    val placeName: String,
    val roadName: String = "",
    val statusMessage: String
)
