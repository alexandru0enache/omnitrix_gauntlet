package com.omnitrix.watchface

import android.content.Context

// Generic-flavor stub for the Samsung Health Sensor SDK tier. This variant
// does NOT link the .aar, so there is no Health Tracking Service to bind;
// keep the exact same object name and surface as the samsung-flavor
// implementation so shared code (HealthStatsTracker) compiles against a
// stable contract. Heart rate on this flavor comes from Health Services and
// SensorStatsTracker only.
object SamsungHealthStats {

    fun latestHeartRateBpm(): Double? = null

    fun start(context: Context) = Unit

    fun stop() = Unit
}