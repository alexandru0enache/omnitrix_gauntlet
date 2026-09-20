package com.omnitrix.watchface

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean

// Today's total workout duration (ExerciseSessionRecord.EXERCISE_DURATION_TOTAL,
// aggregated via Health Connect) as the 8th cycling stat. Health Connect
// queries are suspend/async and noticeably heavier than the passive
// callback-driven reads in HealthStatsTracker, so this polls on its own
// slower interval and caches the last result for synchronous reads from the
// text formatter, same caching shape as HealthStatsTracker's fields.
//
// android.permission.health.READ_EXERCISE is a Health Connect permission,
// not a standard runtime permission -- granted via
// PermissionController.createRequestPermissionResultContract() in
// PermissionRequestActivity, not ActivityCompat.requestPermissions.
object HealthConnectStatsTracker {

    // Only consulted on API 28-33; on 34+ the library ignores it (platform service).
    const val PROVIDER_PACKAGE = "com.android.healthconnect.controller"

    private val READ_EXERCISE_PERMISSION = HealthPermission.getReadPermission(ExerciseSessionRecord::class)

    private val REFRESH_INTERVAL_MILLIS = Duration.ofMinutes(5).toMillis()

    @Volatile private var lastDuration: Duration? = null
    @Volatile private var sdkAvailable: Boolean = false

    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun requiredPermissions(): Set<String> = setOf(READ_EXERCISE_PERMISSION)

    fun isSdkAvailable(context: Context): Boolean =
        HealthConnectClient.getSdkStatus(context.applicationContext, PROVIDER_PACKAGE) == HealthConnectClient.SDK_AVAILABLE

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        scope.launch {
            while (isActive) {
                refreshOnce(appContext)
                delay(REFRESH_INTERVAL_MILLIS)
            }
        }
    }

    private suspend fun refreshOnce(context: Context) {
        if (HealthConnectClient.getSdkStatus(context, PROVIDER_PACKAGE) != HealthConnectClient.SDK_AVAILABLE) {
            sdkAvailable = false
            return
        }
        sdkAvailable = true
        val client = HealthConnectClient.getOrCreate(context, PROVIDER_PACKAGE)
        val granted = try {
            client.permissionController.getGrantedPermissions()
        } catch (e: Exception) {
            emptySet()
        }
        if (READ_EXERCISE_PERMISSION !in granted) {
            lastDuration = null
            return
        }
        val today = LocalDate.now(ZoneId.systemDefault())
        val startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val now = java.time.Instant.now()
        try {
            val result = client.aggregate(
                AggregateRequest(
                    metrics = setOf(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, now)
                )
            )
            lastDuration = result[ExerciseSessionRecord.EXERCISE_DURATION_TOTAL]
        } catch (e: Exception) {
            lastDuration = null
        }
    }

    fun workoutDurationText(): String {
        if (!sdkAvailable) return ""
        val duration = lastDuration ?: return "Workout: --"
        val totalMinutes = duration.toMinutes()
        if (totalMinutes <= 0) return "Workout: --"
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "Workout: ${hours}h ${minutes}m" else "Workout: ${minutes} min"
    }
}
