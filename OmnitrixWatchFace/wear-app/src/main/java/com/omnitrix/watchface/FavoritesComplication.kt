package com.omnitrix.watchface

import android.content.ComponentName
import android.content.Context
import android.graphics.RectF
import androidx.wear.watchface.CanvasComplicationFactory
import androidx.wear.watchface.ComplicationDataSourceChooserIntent
import androidx.wear.watchface.ComplicationSlot
import androidx.wear.watchface.ComplicationSlotsManager
import androidx.wear.watchface.TapEvent
import androidx.wear.watchface.TapType
import androidx.wear.watchface.WatchFace
import androidx.wear.watchface.complications.ComplicationDataSourceInfoRetriever
import androidx.wear.watchface.complications.ComplicationSlotBounds
import androidx.wear.watchface.complications.DefaultComplicationDataSourcePolicy
import androidx.wear.watchface.complications.SystemDataSources
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.rendering.CanvasComplicationDrawable
import androidx.wear.watchface.complications.rendering.ComplicationDrawable
import androidx.wear.watchface.style.CurrentUserStyleRepository
import androidx.wear.watchface.style.UserStyle
import androidx.wear.watchface.style.UserStyleSetting
import androidx.wear.watchface.style.WatchFaceLayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Duration

// ---- Geometry (fractions of the 937x937 dial_active.png source art) --------
//
// The top-center ring slot used to be the baked-in Im0 fitness/workout icon
// image, deliberately left alone by every prior agent this session because it
// had no unambiguous single real-data mapping. It now does (this feature), so
// Im0 was stripped from the art the same way the calendar/alarm/settings/
// weather/reminder icons were: via StripImages.java against
// design_stripped4.pdf -> design_stripped5.pdf (scratchpad from the PDF
// toolkit session), re-rendered at 937x937 and diffed pixel-for-pixel against
// the shipped dial_active.png to confirm the only change was the icon's own
// 61.36pt square (diff bbox landed exactly inside it). The decorative green
// rounded-square "badge" frame drawn behind/around Im0 in the content stream
// is a separate, self-contained fill+stroke path group; it was deliberately
// left in place as a backing card for the new complication rather than also
// torn out, because unlike the single self-balanced `q GS0 gs ... Im0 Do Q`
// image block, the frame's q/Q nesting extends outside a clean token window
// in the stream and removing it risked unbalancing the graphics state stack
// for unrelated content drawn after it.
val TOP_FAVORITE_SLOT_BOUNDS = RectF(0.40128f, 0.02348f, 0.59551f, 0.19957f)

// The bright-green chevron/arrow shapes flanking the ring on the mid-left and
// mid-right sides, found by pure-color pixel scan on the shipped
// dial_active.png (937x937): isolate pixels matching r<30, g>220, b<30 (the
// bright #00FF00-ish chevron fill, distinct from every other lime/olive tone
// used elsewhere on the ring), split into left/right halves at the canvas
// midline, then padded outward by ~20px (937px-canvas terms) for a more
// forgiving tap target. These are hit-test-only regions, not
// ComplicationSlots -- taps here are dispatched by WatchFace.TapListener as
// "background" taps (no ComplicationSlot under the finger) and handled below.
val LEFT_ARROW_BOUNDS = RectF(0.07257f, 0.54322f, 0.20171f, 0.72470f)
val RIGHT_ARROW_BOUNDS = RectF(0.79615f, 0.54322f, 0.92529f, 0.72470f)

// ---- Favorites list ---------------------------------------------------

const val FAVORITES_STYLE_SETTING_ID = "TopFavoriteComplicationStyle"
private const val PREFS_NAME = "favorites_complication"
private const val PREF_KEY_INDEX = "selected_index"

data class FavoriteSource(
    val slotId: Int,
    val label: String,
    val policy: DefaultComplicationDataSourcePolicy,
    val supportedTypes: List<ComplicationType>
)

// Slot IDs 20-24 for the curated favorites, 25 for the "escape to the real
// system picker" custom slot -- well clear of the existing 1-5 slot ids in
// OmnitrixWatchFaceService.kt.
const val FAVORITE_STEP_COUNT_SLOT_ID = 20
const val FAVORITE_BATTERY_SLOT_ID = 21
const val FAVORITE_DAY_OF_WEEK_SLOT_ID = 22
const val FAVORITE_WORLD_CLOCK_SLOT_ID = 23
const val FAVORITE_APP_SHORTCUT_SLOT_ID = 24
const val FAVORITE_CUSTOM_SLOT_ID = 25

// Curated favorites, cycled by the left/right arrow taps. All five are
// Google's own first-party SystemDataSources (confirmed to exist in 1.2.1 via
// javap against the watchface-complications AAR, not assumed from docs) --
// the same class of source this project already proved works without any
// consent-picker interaction (DATE and APP_SHORTCUT are already bound this
// way in OmnitrixWatchFaceService.kt). Picked to avoid dangerous-permission
// dependencies that would confound the "does cycling actually rebind data"
// test with an unrelated "permission not granted" result: WATCH_BATTERY,
// DAY_OF_WEEK and WORLD_CLOCK need no runtime permission at all; STEP_COUNT
// rides the same Activity Recognition/Body Sensors grant HealthStatsTracker
// already has; APP_SHORTCUT needs none and is already proven in this exact
// codebase (SETTINGS_SLOT/REMINDER_SLOT).
val FAVORITES: List<FavoriteSource> = listOf(
    FavoriteSource(
        FAVORITE_STEP_COUNT_SLOT_ID, "Steps",
        DefaultComplicationDataSourcePolicy(SystemDataSources.DATA_SOURCE_STEP_COUNT, ComplicationType.SHORT_TEXT),
        listOf(ComplicationType.SHORT_TEXT)
    ),
    FavoriteSource(
        FAVORITE_BATTERY_SLOT_ID, "Battery",
        DefaultComplicationDataSourcePolicy(SystemDataSources.DATA_SOURCE_WATCH_BATTERY, ComplicationType.SHORT_TEXT),
        listOf(ComplicationType.SHORT_TEXT)
    ),
    FavoriteSource(
        FAVORITE_DAY_OF_WEEK_SLOT_ID, "Day of week",
        DefaultComplicationDataSourcePolicy(SystemDataSources.DATA_SOURCE_DAY_OF_WEEK, ComplicationType.SHORT_TEXT),
        listOf(ComplicationType.SHORT_TEXT)
    ),
    FavoriteSource(
        FAVORITE_WORLD_CLOCK_SLOT_ID, "World clock",
        DefaultComplicationDataSourcePolicy(SystemDataSources.DATA_SOURCE_WORLD_CLOCK, ComplicationType.SHORT_TEXT),
        listOf(ComplicationType.SHORT_TEXT)
    ),
    FavoriteSource(
        FAVORITE_APP_SHORTCUT_SLOT_ID, "Favorite app",
        DefaultComplicationDataSourcePolicy(SystemDataSources.DATA_SOURCE_APP_SHORTCUT, ComplicationType.SMALL_IMAGE),
        listOf(ComplicationType.SMALL_IMAGE)
    )
)

// Supported types for the escape-hatch slot are deliberately broad since the
// real system picker can bind literally anything installed to it.
val CUSTOM_SUPPORTED_TYPES: List<ComplicationType> = listOf(
    ComplicationType.SHORT_TEXT,
    ComplicationType.LONG_TEXT,
    ComplicationType.RANGED_VALUE,
    ComplicationType.MONOCHROMATIC_IMAGE,
    ComplicationType.SMALL_IMAGE,
    ComplicationType.PHOTO_IMAGE
)

// All slot ids sharing TOP_FAVORITE_SLOT_BOUNDS, favorites first then the
// escape-hatch custom slot, in cycling order. Cycling right past the last
// favorite (or left past the first, wrapping the other way) lands on
// CUSTOM_INDEX, which opens the real system complication picker the first
// time it's reached while unconfigured -- see FavoritesTapListener.
val ALL_TOP_SLOT_IDS: List<Int> = FAVORITES.map { it.slotId } + FAVORITE_CUSTOM_SLOT_ID
val CUSTOM_INDEX: Int = FAVORITES.size

fun loadPersistedFavoriteIndex(context: Context): Int =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(PREF_KEY_INDEX, 0)

fun savePersistedFavoriteIndex(context: Context, index: Int) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putInt(PREF_KEY_INDEX, index).apply()
}

private fun optionLabel(index: Int): String =
    if (index < FAVORITES.size) FAVORITES[index].label else "Custom"

/**
 * Builds the N+1 ComplicationSlots stacked at [TOP_FAVORITE_SLOT_BOUNDS]
 * (the 5 favorites plus the escape-hatch custom slot). All start disabled;
 * exactly one is flipped on by the ComplicationSlotsUserStyleSetting's
 * initial/default option (see [buildFavoritesStyleSetting]) as soon as the
 * ComplicationSlotsManager applies it -- there is no visible flash of all of
 * them stacked.
 */
fun buildFavoriteComplicationSlots(context: Context): List<ComplicationSlot> {
    fun canvasFactory() = CanvasComplicationFactory { watchState, listener ->
        CanvasComplicationDrawable(ComplicationDrawable(context), watchState, listener)
    }
    val favoriteSlots = FAVORITES.map { fav ->
        ComplicationSlot.createRoundRectComplicationSlotBuilder(
            fav.slotId,
            canvasFactory(),
            fav.supportedTypes,
            fav.policy,
            ComplicationSlotBounds(TOP_FAVORITE_SLOT_BOUNDS)
        ).setEnabled(false).build()
    }
    // No default data source: like ALARM_SLOT_ID in OmnitrixWatchFaceService.kt,
    // this one is only ever populated by the user going through the real
    // system complication picker.
    val customSlot = ComplicationSlot.createRoundRectComplicationSlotBuilder(
        FAVORITE_CUSTOM_SLOT_ID,
        canvasFactory(),
        CUSTOM_SUPPORTED_TYPES,
        DefaultComplicationDataSourcePolicy(),
        ComplicationSlotBounds(TOP_FAVORITE_SLOT_BOUNDS)
    ).setEnabled(false).build()
    return favoriteSlots + customSlot
}

/**
 * The public, non-internal mechanism this project found for changing which
 * data source backs a complication slot at runtime without going through the
 * real system picker: a [UserStyleSetting.ComplicationSlotsUserStyleSetting]
 * with one [UserStyleSetting.ComplicationSlotsUserStyleSetting.ComplicationSlotsOption]
 * per favorite, each enabling exactly one of the N+1 stacked slots built by
 * [buildFavoriteComplicationSlots] and disabling the rest. Selecting an
 * option is a normal, fully public `currentUserStyleRepository.updateUserStyle(...)`
 * call -- see the report for why this exists instead of directly mutating a
 * slot's bound data source.
 */
fun buildFavoritesStyleSetting(context: Context): UserStyleSetting.ComplicationSlotsUserStyleSetting {
    val options = ALL_TOP_SLOT_IDS.mapIndexed { index, activeId ->
        val overlays = ALL_TOP_SLOT_IDS.map { id ->
            UserStyleSetting.ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay.Builder(id)
                .setEnabled(id == activeId)
                .build()
        }
        UserStyleSetting.ComplicationSlotsUserStyleSetting.ComplicationSlotsOption(
            UserStyleSetting.Option.Id("favorite_$index"),
            optionLabel(index) as CharSequence,
            optionLabel(index) as CharSequence,
            null,
            overlays
        )
    }
    val persistedIndex = loadPersistedFavoriteIndex(context).coerceIn(0, options.size - 1)
    return UserStyleSetting.ComplicationSlotsUserStyleSetting(
        UserStyleSetting.Id(FAVORITES_STYLE_SETTING_ID),
        "Top favorite complication",
        "Which source is shown in the top ring slot",
        null,
        options,
        listOf(WatchFaceLayer.COMPLICATIONS),
        options[persistedIndex]
    )
}

/**
 * Handles taps on the watch face that don't land on an existing
 * ComplicationSlot (the left/right arrows) plus long-presses that do land on
 * the top favorite slot itself. Registered via `WatchFace.setTapListener` in
 * OmnitrixWatchFaceService.createWatchFace().
 */
class FavoritesTapListener(
    private val context: Context,
    private val watchFaceComponentName: ComponentName,
    private val complicationSlotsManager: ComplicationSlotsManager,
    private val currentUserStyleRepository: CurrentUserStyleRepository,
    private val complicationsSetting: UserStyleSetting.ComplicationSlotsUserStyleSetting,
    private val renderer: androidx.wear.watchface.Renderer
) : WatchFace.TapListener {

    private var lastDownTimeMillis: Long? = null

    // Coroutine scope for requestFreshDataFor (no LifecycleOwner available here).
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onTapEvent(tapType: Int, tapEvent: TapEvent, complicationSlot: ComplicationSlot?) {
        android.util.Log.d(TAG, "onTapEvent type=$tapType x=${tapEvent.xPos} y=${tapEvent.yPos} slot=${complicationSlot?.id}")
        when (tapType) {
            TapType.DOWN -> {
                lastDownTimeMillis = tapEvent.tapTime.toEpochMilli()
            }
            TapType.UP -> {
                val downMillis = lastDownTimeMillis
                val isLongPress = downMillis != null &&
                    (tapEvent.tapTime.toEpochMilli() - downMillis) >= LONG_PRESS_THRESHOLD_MS
                handleUp(tapEvent, complicationSlot, isLongPress)
            }
            else -> Unit
        }
    }

    private fun handleUp(tapEvent: TapEvent, complicationSlot: ComplicationSlot?, isLongPress: Boolean) {
        if (complicationSlot != null) {
            android.util.Log.d(TAG, "handleUp: on slot ${complicationSlot.id}, longPress=$isLongPress")
            if (isLongPress && complicationSlot.id in ALL_TOP_SLOT_IDS) {
                launchPickerFor(complicationSlot.id)
            }
            // Otherwise this is a normal tap on a real complication slot;
            // leave it to the slot's own default handling.
            return
        }

        val screenWidth = context.resources.displayMetrics.widthPixels.toFloat()
        val screenHeight = context.resources.displayMetrics.heightPixels.toFloat()
        if (screenWidth <= 0f || screenHeight <= 0f) return
        val fx = tapEvent.xPos / screenWidth
        val fy = tapEvent.yPos / screenHeight

        val direction = when {
            LEFT_ARROW_BOUNDS.contains(fx, fy) -> -1
            RIGHT_ARROW_BOUNDS.contains(fx, fy) -> 1
            else -> 0
        }
        android.util.Log.d(TAG, "background tap fx=$fx fy=$fy direction=$direction")
        if (direction == 0) return

        val optionCount = complicationsSetting.options.size
        val current = loadPersistedFavoriteIndex(context).coerceIn(0, optionCount - 1)
        val next = (current + direction + optionCount) % optionCount
        android.util.Log.d(TAG, "cycling favorite index $current -> $next")
        selectIndex(next)
    }

    private fun selectIndex(index: Int) {
        savePersistedFavoriteIndex(context, index)
        android.util.Log.d(TAG, "updateUserStyle -> option ${complicationsSetting.options[index]}")
        currentUserStyleRepository.updateUserStyle(
            UserStyle(mapOf(complicationsSetting to complicationsSetting.options[index]))
        )
        // The renderer only redraws on its own minute-cadence tick otherwise
        // (interactiveDrawModeUpdateDelayMillis in OmnitrixCanvasRenderer) --
        // force an immediate repaint so cycling feels instant.
        renderer.invalidate()
        if (index == CUSTOM_INDEX) {
            val currentData = complicationSlotsManager.get(FAVORITE_CUSTOM_SLOT_ID)?.complicationData?.value
            if (currentData == null || currentData is NoDataComplicationData) {
                launchPickerFor(FAVORITE_CUSTOM_SLOT_ID)
            }
        } else {
            requestFreshDataFor(ALL_TOP_SLOT_IDS[index])
        }
    }

    // Resolves the slot's actual bound data source, then nudges it via
    // ComplicationDataSourceUpdateRequester.
    private fun requestFreshDataFor(slotId: Int) {
        scope.launch {
            try {
                val info = ComplicationDataSourceInfoRetriever(context).use { retriever ->
                    retriever.retrieveComplicationDataSourceInfo(watchFaceComponentName, intArrayOf(slotId))
                        ?.firstOrNull { it.slotId == slotId }
                        ?.info
                }
                android.util.Log.d(TAG, "requestFreshDataFor slot=$slotId resolved source=${info?.componentName}")
                val sourceComponent = info?.componentName
                if (sourceComponent == null) {
                    android.util.Log.d(TAG, "requestFreshDataFor slot=$slotId: no bound data source to nudge")
                    return@launch
                }
                ComplicationDataSourceUpdateRequester.create(context, sourceComponent).requestUpdateAll()
                android.util.Log.d(TAG, "requestUpdateAll() sent to $sourceComponent for slot=$slotId")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "requestFreshDataFor slot=$slotId failed", e)
            }
        }
    }

    private fun launchPickerFor(slotId: Int) {
        val types = if (slotId == FAVORITE_CUSTOM_SLOT_ID) {
            CUSTOM_SUPPORTED_TYPES
        } else {
            FAVORITES.first { it.slotId == slotId }.supportedTypes
        }
        val wireTypes = ComplicationType.toWireTypes(types)
        ComplicationDataSourceChooserIntent.startProviderChooserActivity(
            context, watchFaceComponentName, slotId, *wireTypes
        )
    }

    companion object {
        private const val LONG_PRESS_THRESHOLD_MS = 400L
        private const val TAG = "FavoritesTapListener"
    }
}
