package com.example.data.repository

import android.location.Location
import com.example.data.local.dao.GeocodeCacheDao
import com.example.data.local.dao.LocationPointDao
import com.example.data.local.dao.VisitDao
import com.example.data.local.entity.LocationPointEntity
import com.example.data.local.entity.VisitEntity
import com.example.domain.detector.VisitDetector
import com.example.domain.geocoder.GeocodingService
import com.example.domain.model.DailySummary
import com.example.domain.model.TimelineItem
import com.example.domain.model.TripRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.min

class LocationRepository(
    private val locationPointDao: LocationPointDao,
    private val visitDao: VisitDao,
    private val geocodeCacheDao: GeocodeCacheDao,
    private val geocodingService: GeocodingService,
    private val visitDetector: VisitDetector
) {

    suspend fun recordLocation(
        location: Location,
        stationaryRadiusMeters: Float = 45f,
        dwellTimeMinutes: Int = 3
    ): Pair<LocationPointEntity, VisitEntity?> = withContext(Dispatchers.IO) {
        val lastPoint = locationPointDao.getLastPoint()
        var distanceFromLast = 0.0

        if (lastPoint != null) {
            val results = FloatArray(1)
            Location.distanceBetween(
                lastPoint.latitude,
                lastPoint.longitude,
                location.latitude,
                location.longitude,
                results
            )
            distanceFromLast = results[0].toDouble()
        }

        // Timestamp in UTC
        val pointTimestamp = if (location.time > 0) location.time else System.currentTimeMillis()

        val entity = LocationPointEntity(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = location.accuracy,
            speed = if (location.hasSpeed()) location.speed else 0f,
            altitude = if (location.hasAltitude()) location.altitude else 0.0,
            timestampUtc = pointTimestamp,
            distanceFromLastMeters = distanceFromLast,
            isVisitCandidate = false
        )

        val id = locationPointDao.insertPoint(entity)
        val savedPoint = entity.copy(id = id)

        // Process through Visit Detector
        val detectedVisit = visitDetector.processLocation(
            point = savedPoint,
            stationaryRadiusMeters = stationaryRadiusMeters,
            dwellTimeMinutes = dwellTimeMinutes
        )

        Pair(savedPoint, detectedVisit)
    }

    fun getPointsForDateRange(startUtc: Long, endUtc: Long): Flow<List<LocationPointEntity>> =
        locationPointDao.getPointsForDateRange(startUtc, endUtc)

    fun getAllTripTracksFlow(): Flow<List<TripRecord>> {
        val pointsFlow = locationPointDao.getAllPointsFlow()
        val visitsFlow = visitDao.getAllVisitsFlow()

        return combine(pointsFlow, visitsFlow) { allPoints, allVisits ->
            if (allPoints.isEmpty() && allVisits.isEmpty()) {
                return@combine emptyList()
            }

            val dayStartMap = mutableMapOf<Long, MutableList<LocationPointEntity>>()
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))

            for (p in allPoints) {
                cal.timeInMillis = p.timestampUtc
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val dayStart = cal.timeInMillis
                dayStartMap.getOrPut(dayStart) { mutableListOf() }.add(p)
            }

            val dayVisitsMap = mutableMapOf<Long, MutableList<VisitEntity>>()
            for (v in allVisits) {
                cal.timeInMillis = v.arrivalTimeUtc
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val dayStart = cal.timeInMillis
                dayVisitsMap.getOrPut(dayStart) { mutableListOf() }.add(v)
                if (!dayStartMap.containsKey(dayStart)) {
                    dayStartMap[dayStart] = mutableListOf()
                }
            }

            val dateFmt = SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault())
            val tripList = mutableListOf<TripRecord>()

            for ((dayStart, pts) in dayStartMap.entries) {
                val dayVisits = dayVisitsMap[dayStart] ?: emptyList()
                pts.sortBy { it.timestampUtc }

                val totalDist = pts.sumOf { it.distanceFromLastMeters }
                val startUtc = pts.firstOrNull()?.timestampUtc ?: dayVisits.firstOrNull()?.arrivalTimeUtc ?: dayStart
                val endUtc = pts.lastOrNull()?.timestampUtc ?: dayVisits.lastOrNull()?.departureTimeUtc ?: (dayStart + 86400000L - 1000L)

                val durationSec = if (pts.size >= 2) {
                    max(60L, (endUtc - startUtc) / 1000L)
                } else if (dayVisits.isNotEmpty()) {
                    dayVisits.sumOf { it.durationSeconds }
                } else {
                    0L
                }

                val startName = dayVisits.firstOrNull()?.placeName
                    ?: if (pts.isNotEmpty()) geocodingService.getFastSynchronousName(pts.first().latitude, pts.first().longitude, false)
                    else "Recorded Departure"

                val destName = dayVisits.lastOrNull()?.placeName
                    ?: if (pts.isNotEmpty()) geocodingService.getFastSynchronousName(pts.last().latitude, pts.last().longitude, pts.last().speed > 0.8f)
                    else "Recorded Stop"

                val maxSpeed = (pts.maxOfOrNull { it.speed.toDouble() } ?: 0.0) * 3.6
                val avgSpeed = if (durationSec > 0 && totalDist > 0) (totalDist / durationSec) * 3.6 else 0.0
                val isOngoing = pts.lastOrNull()?.let { it.timestampUtc > System.currentTimeMillis() - 10 * 60 * 1000L } ?: false

                tripList.add(
                    TripRecord(
                        id = "trip_$dayStart",
                        dateLabel = dateFmt.format(Date(dayStart)),
                        dateTimestampUtc = dayStart,
                        startTimeUtc = startUtc,
                        endTimeUtc = endUtc,
                        totalDistanceMeters = totalDist,
                        durationSeconds = durationSec,
                        startPlaceName = startName,
                        destinationPlaceName = destName,
                        pointCount = pts.size,
                        visitCount = dayVisits.size,
                        maxSpeedKmh = maxSpeed,
                        averageSpeedKmh = avgSpeed,
                        isOngoing = isOngoing
                    )
                )
            }

            tripList.sortedByDescending { it.dateTimestampUtc }
        }
    }

    fun getVisitsForDate(startUtc: Long, endUtc: Long): Flow<List<VisitEntity>> =
        visitDao.getVisitsForDate(startUtc, endUtc)

    fun getAllVisitsFlow(): Flow<List<VisitEntity>> =
        visitDao.getAllVisitsFlow()

    fun getVisitByIdFlow(id: Long): Flow<VisitEntity?> =
        visitDao.getVisitByIdFlow(id)

    suspend fun getVisitById(id: Long): VisitEntity? =
        visitDao.getVisitById(id)

    fun getOngoingVisitFlow(): Flow<VisitEntity?> =
        visitDao.getOngoingVisitFlow()

    fun searchVisits(query: String): Flow<List<VisitEntity>> =
        visitDao.searchVisits(query)

    suspend fun updateVisitDetails(id: Long, newName: String, newAddress: String) = withContext(Dispatchers.IO) {
        val visit = visitDao.getVisitById(id) ?: return@withContext
        visitDao.updateVisit(
            visit.copy(
                placeName = newName.trim(),
                address = newAddress.trim(),
                isUserEdited = true
            )
        )
    }

    suspend fun mergeVisits(primaryId: Long, secondaryId: Long) = withContext(Dispatchers.IO) {
        val primary = visitDao.getVisitById(primaryId) ?: return@withContext
        val secondary = visitDao.getVisitById(secondaryId) ?: return@withContext

        val earliestArrival = min(primary.arrivalTimeUtc, secondary.arrivalTimeUtc)
        val latestDeparture = when {
            primary.departureTimeUtc == null || secondary.departureTimeUtc == null -> null
            else -> max(primary.departureTimeUtc, secondary.departureTimeUtc)
        }

        val totalDuration = if (latestDeparture != null) {
            max(0L, (latestDeparture - earliestArrival) / 1000L)
        } else {
            max(0L, (System.currentTimeMillis() - earliestArrival) / 1000L)
        }

        val merged = primary.copy(
            arrivalTimeUtc = earliestArrival,
            departureTimeUtc = latestDeparture,
            durationSeconds = totalDuration,
            pointCount = primary.pointCount + secondary.pointCount,
            confidence = min(1.0f, max(primary.confidence, secondary.confidence) + 0.05f),
            isUserEdited = true
        )

        visitDao.updateVisit(merged)
        visitDao.deleteById(secondaryId)
    }

    suspend fun deleteVisit(id: Long) = withContext(Dispatchers.IO) {
        visitDao.deleteById(id)
    }

    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        locationPointDao.deleteAll()
        visitDao.deleteAll()
        geocodeCacheDao.deleteAll()
    }

    suspend fun deleteOlderThanDays(days: Int): Int = withContext(Dispatchers.IO) {
        if (days <= 0) return@withContext 0
        val cutoff = System.currentTimeMillis() - (days.toLong() * 24 * 60 * 60 * 1000L)
        val deletedPoints = locationPointDao.deleteOlderThan(cutoff)
        val deletedVisits = visitDao.deleteOlderThan(cutoff)
        geocodeCacheDao.deleteOlderThan(cutoff)
        deletedPoints + deletedVisits
    }

    suspend fun closeOngoingVisit() = withContext(Dispatchers.IO) {
        visitDetector.closeOngoingVisitIfAny()
    }

    fun getDailySummary(startOfDayUtc: Long, endOfDayUtc: Long): Flow<DailySummary> {
        val visitsFlow = visitDao.getVisitsForDate(startOfDayUtc, endOfDayUtc)
        val pointsFlow = locationPointDao.getPointsForDateRange(startOfDayUtc, endOfDayUtc)

        return combine(visitsFlow, pointsFlow) { visits, points ->
            var totalDistance = 0.0
            for (p in points) {
                totalDistance += p.distanceFromLastMeters
            }

            var stationarySeconds = 0L
            val currentTime = System.currentTimeMillis()
            for (v in visits) {
                val effectiveEnd = v.departureTimeUtc ?: min(currentTime, endOfDayUtc)
                val duration = max(0L, (effectiveEnd - v.arrivalTimeUtc) / 1000L)
                stationarySeconds += duration
            }

            val elapsedDaySeconds = if (currentTime < endOfDayUtc) {
                max(0L, (currentTime - startOfDayUtc) / 1000L)
            } else {
                24 * 3600L
            }

            val timeOutsideSeconds = max(0L, elapsedDaySeconds - stationarySeconds)

            val formatter = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
            val dateLabel = formatter.format(Date(startOfDayUtc))

            DailySummary(
                dateLabel = dateLabel,
                startOfDayUtc = startOfDayUtc,
                endOfDayUtc = endOfDayUtc,
                totalDistanceMeters = totalDistance,
                totalPlacesVisited = visits.size,
                timeOutsideSeconds = timeOutsideSeconds,
                stationaryTimeSeconds = stationarySeconds,
                visits = visits
            )
        }
    }

    fun getTimelineItems(startOfDayUtc: Long, endOfDayUtc: Long): Flow<List<TimelineItem>> {
        val visitsFlow = visitDao.getVisitsForDate(startOfDayUtc, endOfDayUtc)
        val pointsFlow = locationPointDao.getPointsForDateRange(startOfDayUtc, endOfDayUtc)

        return combine(visitsFlow, pointsFlow) { visits, points ->
            val items = mutableListOf<TimelineItem>()
            val sortedVisits = visits.sortedBy { it.arrivalTimeUtc }

            if (sortedVisits.isEmpty()) {
                // If there are points but no visits yet, show single journey with real location names
                if (points.size >= 2) {
                    val first = points.first()
                    val last = points.last()
                    val dist = points.sumOf { it.distanceFromLastMeters }
                    val durSec = max(1L, (last.timestampUtc - first.timestampUtc) / 1000L)
                    val avgSpeedKmh = if (durSec > 0) (dist / durSec) * 3.6 else 0.0

                    val fromName = geocodingService.getFastSynchronousName(first.latitude, first.longitude, isMovingOnRoad = false)
                    val toName = geocodingService.getFastSynchronousName(last.latitude, last.longitude, isMovingOnRoad = (last.speed > 0.8f))
                    val road = geocodingService.determineRoadForPath(points)

                    items.add(
                        TimelineItem.JourneyTimelineItem(
                            fromPlaceName = fromName,
                            toPlaceName = toName,
                            distanceMeters = dist,
                            durationSeconds = durSec,
                            startTimeUtc = first.timestampUtc,
                            endTimeUtc = last.timestampUtc,
                            averageSpeedKmh = avgSpeedKmh,
                            pointCount = points.size,
                            startLatitude = first.latitude,
                            startLongitude = first.longitude,
                            endLatitude = last.latitude,
                            endLongitude = last.longitude,
                            roadName = road,
                            waypoints = points.map { Pair(it.latitude, it.longitude) }
                        )
                    )
                }
                return@combine items
            }

            // Check if there was travel before the first visit
            val firstVisit = sortedVisits.first()
            val beforePoints = points.filter { it.timestampUtc < firstVisit.arrivalTimeUtc }
            if (beforePoints.size >= 2) {
                val dist = beforePoints.sumOf { it.distanceFromLastMeters }
                if (dist > 50.0) {
                    val pStart = beforePoints.first()
                    val pEnd = beforePoints.last()
                    val dur = max(1L, (pEnd.timestampUtc - pStart.timestampUtc) / 1000L)
                    val fromName = geocodingService.getFastSynchronousName(pStart.latitude, pStart.longitude, isMovingOnRoad = false)
                    val road = geocodingService.determineRoadForPath(beforePoints)
                    items.add(
                        TimelineItem.JourneyTimelineItem(
                            fromPlaceName = fromName,
                            toPlaceName = firstVisit.placeName,
                            distanceMeters = dist,
                            durationSeconds = dur,
                            startTimeUtc = pStart.timestampUtc,
                            endTimeUtc = pEnd.timestampUtc,
                            averageSpeedKmh = (dist / dur) * 3.6,
                            pointCount = beforePoints.size,
                            startLatitude = pStart.latitude,
                            startLongitude = pStart.longitude,
                            endLatitude = firstVisit.latitude,
                            endLongitude = firstVisit.longitude,
                            roadName = road,
                            waypoints = beforePoints.map { Pair(it.latitude, it.longitude) }
                        )
                    )
                }
            }

            for (i in sortedVisits.indices) {
                val current = sortedVisits[i]
                items.add(TimelineItem.VisitTimelineItem(current))

                // Check transition to next visit
                if (i < sortedVisits.size - 1) {
                    val next = sortedVisits[i + 1]
                    val departureTime = current.departureTimeUtc ?: current.arrivalTimeUtc
                    val transitPoints = points.filter {
                        it.timestampUtc in departureTime..next.arrivalTimeUtc
                    }

                    var journeyDistance = 0.0
                    if (transitPoints.isNotEmpty()) {
                        journeyDistance = transitPoints.sumOf { it.distanceFromLastMeters }
                    } else {
                        val results = FloatArray(1)
                        Location.distanceBetween(
                            current.latitude, current.longitude,
                            next.latitude, next.longitude,
                            results
                        )
                        journeyDistance = results[0].toDouble()
                    }

                    val journeyDuration = max(1L, (next.arrivalTimeUtc - departureTime) / 1000L)
                    val avgSpeedKmh = (journeyDistance / journeyDuration) * 3.6
                    val road = geocodingService.determineRoadForPath(transitPoints)

                    items.add(
                        TimelineItem.JourneyTimelineItem(
                            fromPlaceName = current.placeName,
                            toPlaceName = next.placeName,
                            distanceMeters = journeyDistance,
                            durationSeconds = journeyDuration,
                            startTimeUtc = departureTime,
                            endTimeUtc = next.arrivalTimeUtc,
                            averageSpeedKmh = avgSpeedKmh,
                            pointCount = transitPoints.size,
                            startLatitude = current.latitude,
                            startLongitude = current.longitude,
                            endLatitude = next.latitude,
                            endLongitude = next.longitude,
                            roadName = road,
                            waypoints = transitPoints.map { Pair(it.latitude, it.longitude) }
                        )
                    )
                }
            }

            // Check if there is travel after the last closed visit
            val lastVisit = sortedVisits.last()
            if (lastVisit.departureTimeUtc != null) {
                val afterPoints = points.filter { it.timestampUtc > lastVisit.departureTimeUtc }
                if (afterPoints.size >= 2) {
                    val dist = afterPoints.sumOf { it.distanceFromLastMeters }
                    if (dist > 50.0) {
                        val pStart = afterPoints.first()
                        val pEnd = afterPoints.last()
                        val dur = max(1L, (pEnd.timestampUtc - pStart.timestampUtc) / 1000L)
                        val toName = geocodingService.getFastSynchronousName(pEnd.latitude, pEnd.longitude, isMovingOnRoad = (pEnd.speed > 0.8f))
                        val road = geocodingService.determineRoadForPath(afterPoints)
                        items.add(
                            TimelineItem.JourneyTimelineItem(
                                fromPlaceName = lastVisit.placeName,
                                toPlaceName = toName,
                                distanceMeters = dist,
                                durationSeconds = dur,
                                startTimeUtc = pStart.timestampUtc,
                                endTimeUtc = pEnd.timestampUtc,
                                averageSpeedKmh = (dist / dur) * 3.6,
                                pointCount = afterPoints.size,
                                startLatitude = lastVisit.latitude,
                                startLongitude = lastVisit.longitude,
                                endLatitude = pEnd.latitude,
                                endLongitude = pEnd.longitude,
                                roadName = road,
                                waypoints = afterPoints.map { Pair(it.latitude, it.longitude) }
                            )
                        )
                    }
                }
            }

            return@combine items.sortedBy { it.timestampUtc }
        }
    }

    suspend fun exportAsJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("app", "vansh final")
        root.put("exportedAtUtc", System.currentTimeMillis())
        root.put("version", "1.0")

        val allVisits = visitDao.getAllVisitsSync()
        val visitsArray = JSONArray()
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        for (v in allVisits) {
            val vObj = JSONObject()
            vObj.put("id", v.id)
            vObj.put("placeName", v.placeName)
            vObj.put("address", v.address)
            vObj.put("latitude", v.latitude)
            vObj.put("longitude", v.longitude)
            vObj.put("arrivalUtc", v.arrivalTimeUtc)
            vObj.put("arrivalIso", isoFormat.format(Date(v.arrivalTimeUtc)))
            vObj.put("departureUtc", v.departureTimeUtc ?: JSONObject.NULL)
            if (v.departureTimeUtc != null) {
                vObj.put("departureIso", isoFormat.format(Date(v.departureTimeUtc)))
            }
            vObj.put("durationSeconds", v.durationSeconds)
            vObj.put("accuracyMeters", v.accuracy)
            vObj.put("confidence", v.confidence)
            visitsArray.put(vObj)
        }
        root.put("visits", visitsArray)
        return@withContext root.toString(2)
    }

    suspend fun exportAsCsv(): String = withContext(Dispatchers.IO) {
        val allVisits = visitDao.getAllVisitsSync()
        val sb = StringBuilder()
        sb.append("VisitID,PlaceName,Address,Latitude,Longitude,ArrivalTimeUTC,DepartureTimeUTC,DurationMinutes,AccuracyMeters,Confidence\n")
        val isoFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        for (v in allVisits) {
            val arrivalStr = isoFormat.format(Date(v.arrivalTimeUtc))
            val departureStr = if (v.departureTimeUtc != null) isoFormat.format(Date(v.departureTimeUtc)) else "ONGOING"
            val durationMin = v.durationSeconds / 60
            sb.append("${v.id},")
            sb.append("\"${v.placeName.replace("\"", "\"\"")}\",")
            sb.append("\"${v.address.replace("\"", "\"\"")}\",")
            sb.append("${v.latitude},")
            sb.append("${v.longitude},")
            sb.append("\"$arrivalStr\",")
            sb.append("\"$departureStr\",")
            sb.append("$durationMin,")
            sb.append("${v.accuracy},")
            sb.append("${v.confidence}\n")
        }
        return@withContext sb.toString()
    }
}
