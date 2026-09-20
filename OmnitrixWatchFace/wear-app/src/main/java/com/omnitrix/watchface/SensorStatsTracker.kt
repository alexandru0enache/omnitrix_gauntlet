package com.omnitrix.watchface

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

// Raw SensorManager fallback for heart rate and steps, used when Google
// Health Services isn't delivering values (no passive-monitoring service on
// the device, permissions missing, or just nothing flowing yet). Runs on
// every flavor -- this is the vendor-agnostic tier that makes the face work
// on non-Samsung Wear OS watches. Samsung Health Services are a superset of
// what a plain SensorManager read gives the face: HR via TYPE_HEART_RATE and
// steps via TYPE_STEP_COUNTER (cumulative since the last boot), so this is
// the portable baseline the Samsung SDK builds on top of.
//
// Requires BODY_SENSORS for the HR sensor (step counter needs no runtime
// permission); GateSensorStatsTracker.init gates registration, mirroring
// HealthStatsTracker.start's own permission check.
object SensorStatsTracker {

    private const val TAG = "SensorStatsTracker"

    // Plausible resting/exertion range for a wear HR reading. Rejects the
    // garbage sample the HR sensor occasionally emits on contact loss.
    private const val MIN_VALID_BPM = 30
    private const val MAX_VALID_BPM = 250

    // UI cadence keeps the HR sensor cheap on battery while still refreshing
    // the face's value; the face only needs a per-5s-updated reading anyway.
    private val SAMPLE_DELAY_US = SensorManager.SENSOR_DELAY_UI

    @Volatile private var latestHeartRateBpm: Double? = null
    @Volatile private var latestStepCount: Long = 0
    @Volatile private var stepSensorAvailable = false

    private var sensorManager: SensorManager? = null
    private var heartRateSensor: Sensor? = null
    private var stepCounterSensor: Sensor? = null
    private val heartRateListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val bpm = event.values.firstOrNull()?.toDouble()
            if (bpm != null && bpm >= MIN_VALID_BPM && bpm <= MAX_VALID_BPM) {
                latestHeartRateBpm = bpm
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }
    private val stepCounterListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val steps = event.values.firstOrNull()?.toLong()
            if (steps != null && steps >= 0) {
                latestStepCount = steps
                stepSensorAvailable = true
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }

    // Called from HealthStatsTracker.start() after its own permission gate.
    fun init(context: Context) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        sensorManager = manager
        heartRateSensor = manager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        stepCounterSensor = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (heartRateSensor == null && stepCounterSensor == null) return
        Log.i(TAG, "hrSensor=${heartRateSensor != null} stepSensor=${stepCounterSensor != null}")
        heartRateSensor?.let { manager.registerListener(heartRateListener, it, SAMPLE_DELAY_US) }
        stepCounterSensor?.let { manager.registerListener(stepCounterListener, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    fun latestHeartRateBpm(): Double? = latestHeartRateBpm

    fun isStepSensorAvailable(): Boolean = stepSensorAvailable

    fun latestSteps(): Long = latestStepCount
}