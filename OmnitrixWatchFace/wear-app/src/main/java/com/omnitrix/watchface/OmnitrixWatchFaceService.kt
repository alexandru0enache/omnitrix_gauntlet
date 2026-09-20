package com.omnitrix.watchface

import android.content.ComponentName
import android.graphics.RectF
import android.view.SurfaceHolder
import androidx.wear.watchface.CanvasComplicationFactory
import androidx.wear.watchface.ComplicationSlot
import androidx.wear.watchface.ComplicationSlotsManager
import androidx.wear.watchface.WatchFace
import androidx.wear.watchface.WatchFaceService
import androidx.wear.watchface.WatchFaceType
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.complications.ComplicationSlotBounds
import androidx.wear.watchface.complications.DefaultComplicationDataSourcePolicy
import androidx.wear.watchface.complications.SystemDataSources
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.rendering.CanvasComplicationDrawable
import androidx.wear.watchface.complications.rendering.ComplicationDrawable
import androidx.wear.watchface.style.CurrentUserStyleRepository
import androidx.wear.watchface.style.UserStyleSchema
import androidx.wear.watchface.style.UserStyleSetting

// Fractional bounds (0..1 of the square canvas) for the calendar and alarm icon
// slots in dial_active.png, measured from the source artwork at 937x937.
private val DATE_SLOT_BOUNDS = RectF(0.6105f, 0.0993f, 0.6926f, 0.1825f)
private val ALARM_SLOT_BOUNDS = RectF(0.7183f, 0.1740f, 0.7834f, 0.2391f)

// Settings/weather/reminder/music icon slots. These replace the old baked
// mockup art (Im3/Im4/Im5/Im6) and, before that, a hardcoded-Samsung-package
// bitmap hack.
// Centered on the original mockup icons' positions in the 937x937 source
// artwork, but expanded well past those icons' own tiny bounding boxes
// (e.g. the settings gear was only ~24x24px, ~2.6% of the face) out to
// roughly the size of the date slot above (~8.2% of the face) so they read
// as legible, tappable complications rather than cramped icons.
private val SETTINGS_SLOT_BOUNDS = RectF(0.7611f, 0.2606f, 0.8461f, 0.3456f)
private val WEATHER_SLOT_BOUNDS = RectF(0.2184f, 0.2409f, 0.3034f, 0.3259f)
private val REMINDER_SLOT_BOUNDS = RectF(0.3081f, 0.1453f, 0.3931f, 0.2303f)
private val MUSIC_SLOT_BOUNDS = RectF(0.1608f, 0.3476f, 0.2458f, 0.4326f)

// Samsung Weather's complication provider; supports SHORT_TEXT/LONG_TEXT only.
private val WEATHER_PROVIDER = ComponentName(
    "com.samsung.android.watch.weather",
    "com.samsung.android.watch.weather.complication.WeatherComplicationService"
)

private const val DATE_SLOT_ID = 1
private const val ALARM_SLOT_ID = 2
private const val SETTINGS_SLOT_ID = 3
private const val WEATHER_SLOT_ID = 4
private const val REMINDER_SLOT_ID = 5
private const val MUSIC_SLOT_ID = 6

class OmnitrixWatchFaceService : WatchFaceService() {

    override fun createUserStyleSchema(): UserStyleSchema =
        UserStyleSchema(listOf(buildFavoritesStyleSetting(applicationContext)))

    private fun buildComplicationSlot(
        id: Int,
        bounds: RectF,
        dataSourcePolicy: DefaultComplicationDataSourcePolicy,
        supportedType: ComplicationType = ComplicationType.SHORT_TEXT
    ): ComplicationSlot {
        val canvasComplicationFactory = CanvasComplicationFactory { watchState, listener ->
            CanvasComplicationDrawable(ComplicationDrawable(applicationContext), watchState, listener)
        }
        return ComplicationSlot.createRoundRectComplicationSlotBuilder(
            id,
            canvasComplicationFactory,
            listOf(supportedType),
            dataSourcePolicy,
            ComplicationSlotBounds(bounds)
        ).build()
    }

    override fun createComplicationSlotsManager(
        currentUserStyleRepository: CurrentUserStyleRepository
    ): ComplicationSlotsManager {
        val dateSlot = buildComplicationSlot(
            DATE_SLOT_ID,
            DATE_SLOT_BOUNDS,
            DefaultComplicationDataSourcePolicy(
                SystemDataSources.DATA_SOURCE_DATE,
                ComplicationType.SHORT_TEXT
            )
        )
        // No system data source for "next alarm" exists in this library version (1.2.1) —
        // left with no default so it can be configured manually via the system's
        // complication picker (e.g. a real alarm app's own complication) instead of
        // guessing a mismatched system source.
        val alarmSlot = buildComplicationSlot(
            ALARM_SLOT_ID,
            ALARM_SLOT_BOUNDS,
            DefaultComplicationDataSourcePolicy()
        )
        // DATA_SOURCE_APP_SHORTCUT is the system's generic "launch an app"
        // source — confirmed to exist in 1.2.1 via javap. It's the correct
        // app-agnostic default for an icon slot conceptually representing an
        // app launcher (settings, reminder), letting the system's picker
        // resolve it to whatever's actually installed rather than us
        // hardcoding a package name.
        val settingsSlot = buildComplicationSlot(
            SETTINGS_SLOT_ID,
            SETTINGS_SLOT_BOUNDS,
            DefaultComplicationDataSourcePolicy(
                SystemDataSources.DATA_SOURCE_APP_SHORTCUT,
                ComplicationType.SMALL_IMAGE
            ),
            ComplicationType.SMALL_IMAGE
        )
        val weatherSlot = buildComplicationSlot(
            WEATHER_SLOT_ID,
            WEATHER_SLOT_BOUNDS,
            DefaultComplicationDataSourcePolicy(
                WEATHER_PROVIDER,
                ComplicationType.SHORT_TEXT,
                -1, // NO_DEFAULT_PROVIDER; the Kotlin-visible constant is internal to androidx
                ComplicationType.SHORT_TEXT
            ),
            ComplicationType.SHORT_TEXT
        )
        val reminderSlot = buildComplicationSlot(
            REMINDER_SLOT_ID,
            REMINDER_SLOT_BOUNDS,
            DefaultComplicationDataSourcePolicy(
                SystemDataSources.DATA_SOURCE_APP_SHORTCUT,
                ComplicationType.SMALL_IMAGE
            ),
            ComplicationType.SMALL_IMAGE
        )
        val musicSlot = buildComplicationSlot(
            MUSIC_SLOT_ID,
            MUSIC_SLOT_BOUNDS,
            DefaultComplicationDataSourcePolicy(
                SystemDataSources.DATA_SOURCE_APP_SHORTCUT,
                ComplicationType.SMALL_IMAGE
            ),
            ComplicationType.SMALL_IMAGE
        )
        // The 5 curated favorites + 1 escape-hatch "custom" slot, all stacked
        // at TOP_FAVORITE_SLOT_BOUNDS (the old Im0 fitness-icon position) and
        // switched between via the ComplicationSlotsUserStyleSetting built in
        // createUserStyleSchema() -- see FavoritesComplication.kt.
        val favoriteSlots = buildFavoriteComplicationSlots(applicationContext)
        return ComplicationSlotsManager(
            listOf(dateSlot, alarmSlot, settingsSlot, weatherSlot, reminderSlot, musicSlot) + favoriteSlots,
            currentUserStyleRepository
        )
    }

    override suspend fun createWatchFace(
        surfaceHolder: SurfaceHolder,
        watchState: WatchState,
        complicationSlotsManager: ComplicationSlotsManager,
        currentUserStyleRepository: CurrentUserStyleRepository
    ): WatchFace {
        val renderer = OmnitrixCanvasRenderer(
            context = applicationContext,
            surfaceHolder = surfaceHolder,
            watchState = watchState,
            complicationSlotsManager = complicationSlotsManager,
            currentUserStyleRepository = currentUserStyleRepository
        )
        val complicationsSetting = currentUserStyleRepository.schema.rootUserStyleSettings
            .filterIsInstance<UserStyleSetting.ComplicationSlotsUserStyleSetting>()
            .first { it.id == UserStyleSetting.Id(FAVORITES_STYLE_SETTING_ID) }
        val tapListener = FavoritesTapListener(
            context = applicationContext,
            watchFaceComponentName = ComponentName(applicationContext, OmnitrixWatchFaceService::class.java),
            complicationSlotsManager = complicationSlotsManager,
            currentUserStyleRepository = currentUserStyleRepository,
            complicationsSetting = complicationsSetting,
            renderer = renderer
        )
        return WatchFace(WatchFaceType.ANALOG, renderer).setTapListener(tapListener)
    }
}
