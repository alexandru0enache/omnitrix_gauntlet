package com.omnitrix.watchface

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.wear.watchface.editor.EditorSession
import androidx.wear.watchface.style.UserStyle
import androidx.wear.watchface.style.UserStyleSetting
import kotlinx.coroutines.launch

// Config activity for the favorites "custom" escape hatch, reached via the
// system's own Watch Face Editor flow (androidx.wear.watchface.editor).
// Never call editorSession.close() here -- it auto-closes from onDestroy().
class FavoritesConfigActivity : ComponentActivity() {

    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        statusView = TextView(this).apply {
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            textSize = 12f
            setPadding(24, 24, 24, 24)
            text = "Opening complication picker..."
        }
        setContentView(statusView)

        lifecycleScope.launch {
            val previousIndex = loadPersistedFavoriteIndex(this@FavoritesConfigActivity)
            try {
                val editorSession = EditorSession.createOnWatchEditorSession(this@FavoritesConfigActivity)
                Log.d(TAG, "EditorSession created for ${editorSession.watchFaceComponentName}")

                val setting = editorSession.userStyleSchema.rootUserStyleSettings
                    .filterIsInstance<UserStyleSetting.ComplicationSlotsUserStyleSetting>()
                    .first { it.id == UserStyleSetting.Id(FAVORITES_STYLE_SETTING_ID) }
                editorSession.userStyle.value = UserStyle(mapOf(setting to setting.options[CUSTOM_INDEX]))
                savePersistedFavoriteIndex(this@FavoritesConfigActivity, CUSTOM_INDEX)

                statusView.text = "Choose a complication..."
                val chosen = editorSession.openComplicationDataSourceChooser(FAVORITE_CUSTOM_SLOT_ID)
                Log.d(TAG, "chooser result: $chosen")
                if (chosen == null) {
                    editorSession.commitChangesOnClose = false
                    savePersistedFavoriteIndex(this@FavoritesConfigActivity, previousIndex)
                }
            } catch (e: Exception) {
                Log.e(TAG, "EditorSession/chooser flow failed", e)
                statusView.text = "Could not open the picker:\n${e.message}"
                return@launch
            }
            finish()
        }
    }

    companion object {
        private const val TAG = "FavoritesConfigActivity"
    }
}
