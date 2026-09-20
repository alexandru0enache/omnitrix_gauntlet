package com.omnitrix.watchface

import android.content.Context
import android.util.Log
import com.samsung.android.service.health.tracking.ConnectionListener
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.ValueKey
import java.util.concurrent.atomic.AtomicBoolean

// Samsung Health Sensor SDK path for heart rate (samsung flavor only --
// this object is replaced by a no-op stub in src/generic, so the shared
// code in HealthStatsTracker compiles against a stable surface either way).
// Binds the on-watch Health Tracking Service (com.samsung.android.service.
// health) and streams HEART_RATE_CONTINUOUS, the SDK's battery-efficient
// always-on tracker. Third tier in the face's chain; Google Health Services
// and the SensorManager fallback run ahead of it.
//
// Testing note: without Samsung partner registration the Health Platform
// reports SDK_POLICY_ERROR unless its developer mode is enabled (Settings ->
// Apps -> Health Platform -> tap the title ~10x). A missing service package
// just fails the connect and is logged -- no crash, same "--" placeholders.
object SamsungHealthStats {

    private const val TAG = "SamsungHealthStats"
    private const val HEALTH_SERVICE_PACKAGE = "com.samsung.android.service.health"
    private const val MIN_VALID_BPM = 30
    private const val MAX_VALID_BPM = 250

    @Volatile private var latestHeartRateBpm: Double? = null

    private val started = AtomicBoolean(false)
    private var healthTrackingService: HealthTrackingService? = null
    private var heartRateTracker: HealthTracker? = null

    fun latestHeartRateBpm(): Double? = latestHeartRateBpm

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        if (!isHealthServiceInstalled(context)) {
            Log.i(TAG, "health service package not installed; skipping")
            return
        }
        Log.i(TAG, "connecting to Samsung Health Tracking Service")
        healthTrackingService = HealthTrackingService(connectionListener, context.applicationContext)
            .also { it.connectService() }
    }

    fun stop() {
        heartRateTracker?.unsetEventListener()
        healthTrackingService?.disconnectService()
        healthTrackingService = null
        heartRateTracker = null
        started.set(false)
    }

    private fun isHealthServiceInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(HEALTH_SERVICE_PACKAGE, 0)
        true
    } catch (e: Exception) {
        false
    }

    private val connectionListener = object : ConnectionListener {
        override fun onConnectionSuccess() {
            val service = healthTrackingService ?: return
            val supported = service.getTrackingCapability().getSupportHealthTrackerTypes()
            if (HealthTrackerType.HEART_RATE_CONTINUOUS in supported) {
                val tracker = service.getHealthTracker(HealthTrackerType.HEART_RATE_CONTINUOUS)
                tracker.setEventListener(trackerEventListener)
                heartRateTracker = tracker
                Log.i(TAG, "HEART_RATE_CONTINUOUS tracker active")
            } else {
                Log.i(TAG, "HEART_RATE_CONTINUOUS not supported on this device")
            }
        }

        override fun onConnectionFailed(exception: HealthTrackerException) {
            Log.i(TAG, "connection failed code=${exception.errorCode} hasResolution=${exception.hasResolution()}")
            heartRateTracker = null
        }

        override fun onConnectionEnded() {
            heartRateTracker = null
        }
    }

    private val trackerEventListener = object : HealthTracker.TrackerEventListener {
        override fun onDataReceived(dataPoints: List<DataPoint>) {
            dataPoints.lastOrNull()?.let { dp ->
                val status = dp.getValue(ValueKey.HeartRateSet.HEART_RATE_STATUS)
                val hr = dp.getValue(ValueKey.HeartRateSet.HEART_RATE)
                // HEART_RATE_STATUS > 0 means the reading is backed by skin
                // contact; REJECT the 0-flagged samples the sensor emits off
                // the wrist, and range-guard against junk.
                if (status != null && status > 0 && hr != null && hr in MIN_VALID_BPM..MAX_VALID_BPM) {
                    latestHeartRateBpm = hr.toDouble()
                }
            }
        }

        override fun onFlushCompleted() = Unit

        override fun onError(error: HealthTracker.TrackerError) {
            Log.i(TAG, "tracker error=${error.name}")
        }
    }
}