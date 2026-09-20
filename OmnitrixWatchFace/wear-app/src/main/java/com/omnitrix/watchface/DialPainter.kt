package com.omnitrix.watchface

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF

// Arc geometry for the two bottom panels in dial_active.png, measured from the
// source artwork at 937x937. Angles follow Canvas.Path#addArc convention (0deg
// = 3 o'clock, clockwise-positive). Both panels read left-to-right as angle
// decreases, since the whole bottom band sweeps left-to-right in that direction.
private const val PANEL_RADIUS_FRAC = 0.822f
private const val TIME_ARC_START_DEG = 135.3f
private const val TIME_ARC_SWEEP_DEG = -43.3f
private const val BATTERY_ARC_START_DEG = 88.2f
private const val BATTERY_ARC_SWEEP_DEG = -43.4f

// Center-stat text line. Fractions (of the square canvas) re-derived from the
// original design_01_v1.ai content stream (page index 1) the same way the
// panel arc geometry above was: each Tm operator's e/f translation gives the
// left-baseline x/y in PDF points on a 450x450pt page, and the d scale gives
// the font size in points; dividing by 450 gives a resolution-independent
// fraction (450pt page == 937px source art, so px/937 == pt/450). A single
// stat line sits at the vertical center of the dial and auto-cycles through
// all seven Health Services metrics.
private const val STAT_X_FRAC = 0.28f
private const val STAT_Y_FRAC = 0.48f
private const val STAT_SIZE_FRAC = 0.063f

// Shared between OmnitrixCanvasRenderer (the real watch face) and
// DebugPreviewActivity (a standalone on-device preview), so the two can't
// silently drift out of sync the way the placeholder version once did.
// The settings/weather/reminder icon slots (formerly Im3/Im4/Im5 baked mockup
// art) are now real ComplicationSlots declared in OmnitrixWatchFaceService and
// rendered through the WatchFaceService/ComplicationSlotsManager lifecycle —
// they can't be replicated in a plain Activity, so DebugPreviewActivity only
// shows the background art + curved time/battery text.
object DialPainter {

    fun drawActiveFace(
        canvas: Canvas,
        bounds: Rect,
        ambientBitmap: Bitmap,
        activeBitmap: Bitmap,
        timeText: String,
        batteryText: String,
        textPaint: Paint,
        statsPaint: Paint,
        currentStat: String,
        alpha: Int = 255,
        scale: Float = 1f
    ) {
        // The X-logo housing is the persistent base layer -- it's already
        // what's on screen in ambient mode, so it's drawn first, at full
        // opacity/scale, outside the animation layer entirely. It doesn't
        // "arrive" on ambient-exit, so it never pops/fades.
        canvas.drawBitmap(ambientBitmap, null, bounds, null)
        // The ring/dashboard dial (now transparent-background art) is the
        // part that rises into place on top of the housing, so it pops/fades
        // in together with the overlay content below (curved time/battery
        // text, stat lines) rather than snapping in instantly. alpha < 255 /
        // scale != 1 only during that animation (see OmnitrixCanvasRenderer);
        // saveLayerAlpha fades the overlay as one unit without touching
        // individual Paints (whose own alpha is reused elsewhere, e.g.
        // ambientPaint), and canvas.scale() around the face center makes the
        // same content visually rise/snap into place rather than just
        // cross-fade, evoking the Omnitrix dial popping up.
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        val isAnimating = alpha < 255 || scale != 1f
        val layerCount = when {
            !isAnimating -> -1
            alpha < 255 -> canvas.saveLayerAlpha(null, alpha)
            else -> canvas.save()
        }
        if (isAnimating) canvas.scale(scale, scale, cx, cy)
        canvas.drawBitmap(activeBitmap, null, bounds, null)
        val radius = bounds.width() / 2f * PANEL_RADIUS_FRAC
        drawCurvedText(canvas, timeText, cx, cy, radius, TIME_ARC_START_DEG, TIME_ARC_SWEEP_DEG, textPaint)
        drawCurvedText(canvas, batteryText, cx, cy, radius, BATTERY_ARC_START_DEG, BATTERY_ARC_SWEEP_DEG, textPaint)

        drawStatLine(canvas, bounds, currentStat, STAT_X_FRAC, STAT_Y_FRAC, STAT_SIZE_FRAC, statsPaint)
        if (isAnimating) canvas.restoreToCount(layerCount)
    }

    private fun drawStatLine(
        canvas: Canvas,
        bounds: Rect,
        text: String,
        xFrac: Float,
        yFrac: Float,
        sizeFrac: Float,
        paint: Paint
    ) {
        paint.textSize = bounds.width() * sizeFrac
        canvas.drawText(
            text,
            bounds.left + bounds.width() * xFrac,
            bounds.top + bounds.height() * yFrac,
            paint
        )
    }

    fun drawCurvedText(
        canvas: Canvas,
        text: String,
        cx: Float,
        cy: Float,
        radius: Float,
        startAngleDeg: Float,
        sweepAngleDeg: Float,
        paint: Paint
    ) {
        val oval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        val path = Path().apply { addArc(oval, startAngleDeg, sweepAngleDeg) }
        val arcLength = Math.abs(sweepAngleDeg) / 360f * (2f * Math.PI.toFloat() * radius)
        val hOffset = (arcLength - paint.measureText(text)) / 2f
        canvas.drawTextOnPath(text, path, hOffset, 0f, paint)
    }
}
