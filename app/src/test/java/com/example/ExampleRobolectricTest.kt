package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.DailyTrackDatabase
import com.example.data.local.entity.LocationPointEntity
import com.example.data.local.entity.VisitEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var database: DailyTrackDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, DailyTrackDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun readStringFromContext_matchesAppName() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("vansh final", appName)
    }

    @Test
    fun insertAndRetrieveVisit_isSuccessful() = runBlocking {
        val visitDao = database.visitDao()
        val now = System.currentTimeMillis()

        val visit = VisitEntity(
            placeName = "Downtown Cafe",
            address = "123 Market St",
            latitude = 37.7749,
            longitude = -122.4194,
            arrivalTimeUtc = now - 3600000L,
            departureTimeUtc = now,
            durationSeconds = 3600L,
            accuracy = 8.0f,
            confidence = 0.95f
        )

        val id = visitDao.insertVisit(visit)
        assertTrue(id > 0)

        val retrieved = visitDao.getVisitById(id)
        assertNotNull(retrieved)
        assertEquals("Downtown Cafe", retrieved?.placeName)
        assertEquals(3600L, retrieved?.durationSeconds)
    }

    @Test
    fun insertAndQueryLocationPoints_isSuccessful() = runBlocking {
        val pointDao = database.locationPointDao()
        val now = System.currentTimeMillis()

        val point1 = LocationPointEntity(
            latitude = 37.7749,
            longitude = -122.4194,
            accuracy = 5f,
            timestampUtc = now - 10000L,
            distanceFromLastMeters = 0.0
        )
        val point2 = LocationPointEntity(
            latitude = 37.7755,
            longitude = -122.4188,
            accuracy = 6f,
            timestampUtc = now,
            distanceFromLastMeters = 85.0
        )

        pointDao.insertPoint(point1)
        pointDao.insertPoint(point2)

        val points = pointDao.getPointsBetweenSync(now - 20000L, now + 1000L)
        assertEquals(2, points.size)
        assertEquals(85.0, points[1].distanceFromLastMeters, 0.1)
    }
}
