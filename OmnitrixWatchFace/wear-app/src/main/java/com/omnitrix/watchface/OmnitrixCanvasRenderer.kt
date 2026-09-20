package com.omnitrix.watchface

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.BatteryManager
import android.view.SurfaceHolder
import androidx.wear.watchface.CanvasType
import androidx.wear.watchface.ComplicationSlotsManager
import androidx.wear.watchface.DrawMode
import androidx.wear.watchface.Renderer
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.style.CurrentUserStyleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

// Static face redraws only need minute granularity — no seconds hand, no animation.
private const val FRAME_PERIOD_MILLIS = 60_000L

// Ambient-exit "pop up" animation: the active face's overlay content snaps up
// to full size/opacity in a handful of discrete steps. Evokes the Omnitrix
// dial mechanically rising into place (see design notes) rather than a flat
// cross-fade -- alpha still ramps 0->255, paired with a scale that grows from
// POP_IN_START_SCALE and overshoots past 1.0 before settling, applied via
// canvas.scale() around a center pivot in DialPainter and the complications
// loop below. Stepped/discretized rather than continuous-time-driven --
// see conversation/handoff notes for why.
private const val FADE_IN_STEPS = 6
private const val FADE_IN_STEP_MILLIS = 90L
private const val POP_IN_START_SCALE = 0.62f

private const val STAT_DISPLAY_MILLIS = 5000L
private val STAT_LIST: List<() -> String> = listOf(
    { HealthStatsTracker.stepsText() },
    { HealthStatsTracker.heartRateText() },
    { HealthStatsTracker.caloriesText() },
    { HealthStatsTracker.distanceText() },
    { HealthStatsTracker.floorsText() },
    { HealthStatsTracker.vo2MaxText() },
    { HealthStatsTracker.paceText() },
    { HealthConnectStatsTracker.workoutDurationText() }
)

// Robert Penner's easeOutBack: overshoots past 1.0 then settles back to
// exactly 1.0 at t=1, which reads as a mechanical "snap into place" rather
// than a soft ease. c1 is the overshoot amount.
private fun easeOutBack(t: Float): Float {
    val c1 = 3.2f
    val c3 = c1 + 1f
    val tm1 = t - 1f
    return 1f + c3 * tm1 * tm1 * tm1 + c1 * tm1 * tm1
}

class OmnitrixCanvasRenderer(
    private val context: Context,
    surfaceHolder: SurfaceHolder,
    private val watchState: WatchState,
    private val complicationSlotsManager: ComplicationSlotsManager,
    currentUserStyleRepository: CurrentUserStyleRepository
) : Renderer.CanvasRenderer2<Renderer.SharedAssets>(
    surfaceHolder = surfaceHolder,
    currentUserStyleRepository = currentUserStyleRepository,
    watchState = watchState,
    canvasType = CanvasType.HARDWARE,
    interactiveDrawModeUpdateDelayMillis = FRAME_PERIOD_MILLIS,
    clearWithBackgroundTintBeforeRenderingHighlightLayer = false
) {

    private val ambientBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.dial_ambient)
    private val activeBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.dial_active)
    private var scaledAmbientBitmap: Bitmap? = null
    private var scaledActiveBitmap: Bitmap? = null
    private var scaledForSize = -1

    private val ambientPaint = Paint().apply { alpha = 160 }
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
    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    // Drives the ambient->interactive fade-in. Alpha is read from render()
    // (called on the watch face's own render thread) and written from
    // fadeScope's coroutine (Dispatchers.Main) -- @Volatile keeps that write
    // visible without needing full synchronization for a value this simple.
    @Volatile
    private var activeFaceAlpha = 255
    @Volatile
    private var activeFaceScale = 1f
    private var fadeJob: Job? = null
    private var statCycleJob: Job? = null
    private var isFirstAmbientState = true
    private var isFirstVisibleState = true
    private val fadeScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    init {
        HealthStatsTracker.start(context)
        HealthConnectStatsTracker.start(context)
        fadeScope.launch {
            watchState.isAmbient.collect { isAmbientOrNull ->
                val isAmbient = isAmbientOrNull ?: false
                if (isFirstAmbientState) {
                    // Don't animate on the very first frame the face is
                    // created in interactive mode -- only real
                    // ambient->interactive transitions during a session
                    // should pop up.
                    isFirstAmbientState = false
                    activeFaceAlpha = if (isAmbient) 0 else 255
                    activeFaceScale = if (isAmbient) POP_IN_START_SCALE else 1f
                    return@collect
                }
                if (isAmbient) {
                    fadeJob?.cancel()
                    activeFaceAlpha = 0
                    activeFaceScale = POP_IN_START_SCALE
                } else {
                    startFadeIn()
                }
            }
        }
        // AOD-off screen wake never transitions isAmbient (the face stays in
        // interactive DrawMode the whole time; the screen is just physically
        // off), so it never reaches the isAmbient false-branch above.
        // WatchState.isVisible tracks screen on/off independent of ambient
        // and does fire in that case -- collect it too so the pop-in plays
        // on every interactive screen wake, AOD on or off.
        fadeScope.launch {
            watchState.isVisible.collect { isVisibleOrNull ->
                val isVisible = isVisibleOrNull ?: false
                if (isFirstVisibleState) {
                    isFirstVisibleState = false
                    return@collect
                }
                if (isVisible && watchState.isAmbient.value != true) {
                    startFadeIn()
                }
            }
        }
        statCycleJob = fadeScope.launch {
            while (isActive) {
                delay(STAT_DISPLAY_MILLIS)
                invalidate()
            }
        }
    }

    private fun startFadeIn() {
        fadeJob?.cancel()
        fadeJob = fadeScope.launch {
            for (step in 1..FADE_IN_STEPS) {
                val progress = step.toFloat() / FADE_IN_STEPS
                if (step == FADE_IN_STEPS) {
                    // Land on exact rest values -- easeOutBack asymptotically
                    // hits 1.0 at t=1 in theory, but pin it explicitly so no
                    // residual scale/alpha survives float rounding.
                    activeFaceAlpha = 255
                    activeFaceScale = 1f
                } else {
                    activeFaceAlpha = (progress * 255).toInt()
                    activeFaceScale = POP_IN_START_SCALE + (1f - POP_IN_START_SCALE) * easeOutBack(progress)
                }
                // Force an extra render() pass now instead of waiting for the
                // normal 60s cadence (no lasting change to the redraw rate).
                invalidate()
                if (step != FADE_IN_STEPS) delay(FADE_IN_STEP_MILLIS)
            }
        }
    }

    override fun onDestroy() {
        fadeJob?.cancel()
        statCycleJob?.cancel()
        fadeScope.cancel()
        super.onDestroy()
    }

    override suspend fun createSharedAssets(): SharedAssets =
        object : SharedAssets {
            override fun onDestroy() = Unit
        }

    private fun ensureScaledBitmaps(size: Int) {
        if (size == scaledForSize) return
        scaledAmbientBitmap = Bitmap.createScaledBitmap(ambientBitmap, size, size, true)
        scaledActiveBitmap = Bitmap.createScaledBitmap(activeBitmap, size, size, true)
        timePaint.textSize = size * 0.09f
        scaledForSize = size
    }

    override fun render(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime, sharedAssets: SharedAssets) {
        val isAmbient = renderParameters.drawMode == DrawMode.AMBIENT
        ensureScaledBitmaps(bounds.width())

        if (isAmbient) {
            canvas.drawBitmap(scaledAmbientBitmap!!, 0f, 0f, ambientPaint)
        } else {
            val batteryPct = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val currentStat = if (HealthStatsTracker.isExternalExerciseActive()) {
                HealthStatsTracker.activeExerciseText()
            } else {
                val stats = STAT_LIST.map { it() }.filter { it.isNotEmpty() }
                if (stats.isEmpty()) "" else {
                    val currentIndex = (System.currentTimeMillis() / STAT_DISPLAY_MILLIS % stats.size).toInt()
                    stats[currentIndex]
                }
            }
            DialPainter.drawActiveFace(
                canvas, bounds, scaledAmbientBitmap!!, scaledActiveBitmap!!,
                timeFormatter.format(zonedDateTime), "$batteryPct%", timePaint,
                statsPaint, currentStat,
                activeFaceAlpha, activeFaceScale
            )
            // ComplicationDrawable.setAlpha() -- present on the class per javap and
            // the obvious-looking direct API -- actually throws
            // UnsupportedOperationException at runtime on this AAR (confirmed live,
            // see crash log), so it can't drive the fade. Falling back to the same
            // saveLayerAlpha pattern DialPainter uses for its overlay: wrap the
            // whole loop in one offscreen layer so all complications fade+pop in
            // together as a group with the rest of the overlay content, using the
            // same center-pivot scale as DialPainter so the whole overlay reads as
            // one rising unit.
            val isAnimating = activeFaceAlpha < 255 || activeFaceScale != 1f
            val complicationLayerCount = when {
                !isAnimating -> -1
                activeFaceAlpha < 255 -> canvas.saveLayerAlpha(null, activeFaceAlpha)
                else -> canvas.save()
            }
            if (isAnimating) {
                canvas.scale(activeFaceScale, activeFaceScale, bounds.exactCenterX(), bounds.exactCenterY())
            }
            for ((_, complication) in complicationSlotsManager.complicationSlots) {
                if (complication.enabled) {
                    complication.render(canvas, zonedDateTime, renderParameters)
                }
            }
            if (isAnimating) canvas.restoreToCount(complicationLayerCount)
        }
    }

    override fun renderHighlightLayer(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime, sharedAssets: SharedAssets) {
    }
}
