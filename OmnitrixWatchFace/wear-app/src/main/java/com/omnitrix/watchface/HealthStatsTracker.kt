package com.omnitrix.watchface

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.health.services.client.HealthServices
import androidx.health.services.client.PassiveListenerCallback
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseTrackedStatus
import androidx.health.services.client.data.PassiveListenerConfig
import androidx.health.services.client.data.UserActivityInfo
import androidx.health.services.client.data.UserActivityState
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import java.util.Locale

// Live Health Services (PassiveMonitoring) readings for all seven center-stat
// values that cycle through the single centered stat line: heart rate,
// steps, calories, distance, floors, VO2 max, and pace. Shared between
// OmnitrixCanvasRenderer (the real watch face) and
// DebugPreviewActivity, same shared-code rule as DialPainter, since a
// Health Services read is just a data read -- not a complication bound
// through the WatchFaceService lifecycle -- so it works fine in a plain
// Activity too.
//
// Requires BODY_SENSORS + ACTIVITY_RECOGNITION (runtime permissions); see
// PermissionRequestActivity. Until granted, start() is a no-op and the text
// getters return placeholder strings instead of crashing.
object HealthStatsTracker {

    @Volatile private var latestHeartRateBpm: Double? = null
    @Volatile private var stepsToday: Long = 0
    @Volatile private var stepsTodayHasData: Boolean = false
    @Volatile private var accumulatedDate: LocalDate = LocalDate.now()
    @Volatile private var caloriesToday: Double = 0.0
    @Volatile private var caloriesTodayHasData: Boolean = false
    @Volatile private var distanceToday: Double = 0.0
    @Volatile private var distanceTodayHasData: Boolean = false
    @Volatile private var floorsToday: Double = 0.0
    @Volatile private var floorsTodayHasData: Boolean = false
    @Volatile private var latestVo2Max: Double? = null
    @Volatile private var latestPace: Double? = null

    // Reflects an exercise session another app (Samsung Health, etc.)
    // already has in progress -- this app never starts/owns a session
    // itself. Backed by PassiveListenerCallback#onUserActivityInfoReceived,
    // which is the only Health Services signal that reports exercise state
    // system-wide rather than only for sessions this client's own
    // ExerciseClient started; see report for the ExerciseClient
    // investigation that ruled that API out.
    @Volatile private var activeExerciseTypeName: String? = null
    @Volatile private var activeExerciseStateChangeTime: Instant? = null

    private val started = AtomicBoolean(false)

    private val callback = object : PassiveListenerCallback {
        override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
            dataPoints.getData(DataType.HEART_RATE_BPM).lastOrNull()?.let {
                latestHeartRateBpm = it.value
            }
            dataPoints.getData(DataType.VO2_MAX).lastOrNull()?.let {
                latestVo2Max = it.value
            }
            dataPoints.getData(DataType.PACE).lastOrNull()?.let {
                latestPace = it.value
            }

            val today = LocalDate.now()
            if (today != accumulatedDate) {
                accumulatedDate = today
                stepsToday = 0
                caloriesToday = 0.0
                distanceToday = 0.0
                floorsToday = 0.0
            }
            // STEPS_DAILY delivers interval deltas (like the other Health
            // Services delta types); summing them approximates a running
            // "steps so far today" count, reset locally at midnight. Exact
            // reset/accumulation semantics on the service side weren't
            // verifiable without extended multi-hour on-device observation,
            // so treat this as best-effort rather than a guaranteed exact
            // daily total -- see report.
            val deltas = dataPoints.getData(DataType.STEPS_DAILY)
            if (deltas.isNotEmpty()) {
                stepsToday += deltas.sumOf { it.value }
                stepsTodayHasData = true
            }
            val calorieDeltas = dataPoints.getData(DataType.CALORIES_DAILY)
            if (calorieDeltas.isNotEmpty()) {
                caloriesToday += calorieDeltas.sumOf { it.value }
                caloriesTodayHasData = true
            }
            val distanceDeltas = dataPoints.getData(DataType.DISTANCE_DAILY)
            if (distanceDeltas.isNotEmpty()) {
                distanceToday += distanceDeltas.sumOf { it.value }
                distanceTodayHasData = true
            }
            val floorDeltas = dataPoints.getData(DataType.FLOORS_DAILY)
            if (floorDeltas.isNotEmpty()) {
                floorsToday += floorDeltas.sumOf { it.value }
                floorsTodayHasData = true
            }
        }

        override fun onUserActivityInfoReceived(info: UserActivityInfo) {
            val exerciseInfo = info.exerciseInfo
            val isOtherAppExercise = exerciseInfo != null &&
                info.userActivityState == UserActivityState.USER_ACTIVITY_EXERCISE &&
                exerciseInfo.exerciseTrackedStatus == ExerciseTrackedStatus.OTHER_APP_IN_PROGRESS
            if (isOtherAppExercise) {
                if (activeExerciseTypeName == null) {
                    activeExerciseStateChangeTime = info.stateChangeTime
                }
                activeExerciseTypeName = exerciseInfo!!.exerciseType.name
            } else {
                activeExerciseTypeName = null
                activeExerciseStateChangeTime = null
            }
        }
    }

    fun hasRequiredPermissions(context: Context): Boolean {
        val bodySensors = ContextCompat.checkSelfPermission(
            context, Manifest.permission.BODY_SENSORS
        ) == PackageManager.PERMISSION_GRANTED
        val activityRecognition = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED
        return bodySensors && activityRecognition
    }

    fun start(context: Context) {
        if (!hasRequiredPermissions(context)) return
        if (!started.compareAndSet(false, true)) return

        // Stand up the lower tiers of the data chain -- raw SensorManager
        // readings and (samsung flavor only) the Samsung Health Tracking
        // Service -- regardless of what Health Services ends up delivering.
        SensorStatsTracker.init(context)
        SamsungHealthStats.start(context)

        val client = HealthServices.getClient(context.applicationContext).passiveMonitoringClient
        val config = PassiveListenerConfig(
            dataTypes = setOf(
                DataType.HEART_RATE_BPM, DataType.STEPS_DAILY,
                DataType.CALORIES_DAILY, DataType.DISTANCE_DAILY, DataType.FLOORS_DAILY,
                DataType.VO2_MAX, DataType.PACE
            ),
            shouldUserActivityInfoBeRequested = true,
            dailyGoals = emptySet(),
            healthEventTypes = emptySet()
        )
        client.setPassiveListenerCallback(config, callback)
    }

    fun stepsText(): String {
        if (stepsTodayHasData) {
            return "Steps: ${String.format(Locale.US, "%,d", stepsToday)}"
        }
        return if (SensorStatsTracker.isStepSensorAvailable()) {
            "Steps: ${String.format(Locale.US, "%,d", SensorStatsTracker.latestSteps())}"
        } else {
            "Steps: --"
        }
    }

    // Priority: Health Services value when present, then the SensorManager
    // baseline, then the Samsung Health Tracking Service tier.
    fun heartRateText(): String {
        val fallbackBpm = SensorStatsTracker.latestHeartRateBpm()
            ?: SamsungHealthStats.latestHeartRateBpm()
        val bpm = latestHeartRateBpm ?: fallbackBpm
        return bpm?.let { "Heartrate: ${it.toInt()} BPM" } ?: "Heartrate: -- BPM"
    }

    fun caloriesText(): String =
        if (caloriesTodayHasData) "Cal: ${String.format(Locale.US, "%,.0f", caloriesToday)} kcal" else "Cal: --"

    fun distanceText(): String =
        if (distanceTodayHasData) "Dist: ${String.format(Locale.US, "%.1f", distanceToday / 1000.0)} km" else "Dist: --"

    fun floorsText(): String =
        if (floorsTodayHasData) "Floors: ${String.format(Locale.US, "%.0f", floorsToday)}" else "Floors: --"

    fun vo2MaxText(): String =
        latestVo2Max?.let { "VO2: ${String.format(Locale.US, "%.1f", it)}" } ?: "VO2: --"

    fun paceText(): String =
        latestPace?.let {
            val totalSec = it.toLong()
            val min = totalSec / 60
            val sec = totalSec % 60
            "Pace: ${min}:${String.format(Locale.US, "%02d", sec)}/km"
        } ?: "Pace: --"

    fun isExternalExerciseActive(): Boolean = activeExerciseTypeName != null

    fun activeExerciseText(): String {
        val typeName = activeExerciseTypeName ?: return ""
        val startedAt = activeExerciseStateChangeTime ?: return typeName
        val elapsed = Duration.between(startedAt, Instant.now()).let { if (it.isNegative) Duration.ZERO else it }
        val totalMin = elapsed.toMinutes()
        val hours = totalMin / 60
        val minutes = totalMin % 60
        val elapsedText = if (hours > 0) "${hours}h ${minutes}m" else "${minutes} min"
        return "$typeName $elapsedText"
    }
}
