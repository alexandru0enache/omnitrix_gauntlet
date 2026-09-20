package com.omnitrix.watchface

import android.app.Activity
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.BatteryManager
import android.os.Bundle
import android.view.View
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

// Standalone on-device preview of the active-mode face, minus complications
// (those are bound through the WatchFaceService lifecycle and can't be
// replicated in a plain Activity). Shares DialPainter with the real renderer
// so this can't silently go stale the way the old placeholder version did.
// Health Services readings (all seven stats, cycling through the single
// centered stat line) ARE shown here since they're just a data read, not a
// complication -- same shared HealthStatsTracker the real renderer uses.
class DebugPreviewActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HealthStatsTracker.start(this)
        HealthConnectStatsTracker.start(this)
        setContentView(PreviewView(this))
    }
}

private class PreviewView(context: Context) : View(context) {
    private val ambientBitmap = BitmapFactory.decodeResource(resources, R.drawable.dial_ambient)
    private val activeBitmap = BitmapFactory.decodeResource(resources, R.drawable.dial_active)
    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val timePaint = Paint().apply {
        color = Color.parseColor("#1B1F14")
        textAlign = Paint.Align.LEFT
        isAntiAlias = true
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val statsPaint = Paint().apply {
        color = Color.parseColor("#00FF00")
        textAlign = Paint.Align.LEFT
        isAntiAlias = true
        typeface = android.graphics.Typeface.DEFAULT
    }
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    // Live "another app has an active exercise" state (Part B) isn't
    // meaningfully testable here -- it needs a real in-progress workout on
    // the device, and the real renderer already exercises that path -- so
    // this list stays plain cycling, matching the 7-stat list above plus
    // the new Health Connect stat.
    private val statList: List<() -> String> = listOf(
        { HealthStatsTracker.stepsText() },
        { HealthStatsTracker.heartRateText() },
        { HealthStatsTracker.caloriesText() },
        { HealthStatsTracker.distanceText() },
        { HealthStatsTracker.floorsText() },
        { HealthStatsTracker.vo2MaxText() },
        { HealthStatsTracker.paceText() },
        { HealthConnectStatsTracker.workoutDurationText() }
    )

    override fun onDraw(canvas: Canvas) {
        val bounds = Rect(0, 0, width, height)
        timePaint.textSize = width * 0.09f
        val batteryPct = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val currentIndex = (System.currentTimeMillis() / 5000L % statList.size).toInt()
        val currentStat = statList.map { it() }.filter { it.isNotEmpty() }
            .let { if (it.isEmpty()) "" else it[currentIndex % it.size] }
        DialPainter.drawActiveFace(
            canvas, bounds, ambientBitmap, activeBitmap,
            timeFormatter.format(ZonedDateTime.now()), "$batteryPct%", timePaint,
            statsPaint, currentStat
        )
        // Health data can arrive asynchronously after this first draw (or
        // permissions can still be pending) -- keep repainting periodically
        // so the plain preview Activity picks up live values.
        postInvalidateDelayed(2000)
    }
}
