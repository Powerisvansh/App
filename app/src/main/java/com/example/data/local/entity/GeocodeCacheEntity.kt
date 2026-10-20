package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Caches reverse-geocoded addresses to reduce network calls and battery drain.
 * Key is formatted as "lat_rounded,lon_rounded" (e.g. "37.7834,-122.4167").
 */
@Entity(tableName = "geocode_cache")
data class GeocodeCacheEntity(
    @PrimaryKey
    val coordinateKey: String,
    val placeName: String,
    val address: String,
    val cachedAtUtc: Long = System.currentTimeMillis()
)
