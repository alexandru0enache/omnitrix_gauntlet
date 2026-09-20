package com.omnitrix.watchface

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

// One-off companion activity to grant the runtime permissions Health
// Services needs (BODY_SENSORS for heart rate, ACTIVITY_RECOGNITION for
// steps, and on API 33+ BODY_SENSORS_BACKGROUND for delivery while the
// watch face -- a bound WatchFaceService, not a foreground Activity -- is
// what's actually reading), plus the Health Connect permission for the
// workout-duration stat. A WatchFaceService has no foreground UI of its own
// to show a permission dialog from, so this activity exists purely to be
// launched once by the user (or via adb) to grant them:
//   adb shell am start -n com.omnitrix.watchface/.PermissionRequestActivity
// or from the watch's app drawer, listed as "Omnitrix Permissions".
//
// Health Connect permissions are NOT standard Android runtime permissions --
// they're requested through
// PermissionController.createRequestPermissionResultContract(), an
// ActivityResultContract, which is why this extends ComponentActivity
// (needed for registerForActivityResult) rather than plain Activity.
class PermissionRequestActivity : ComponentActivity() {

    private lateinit var statusView: TextView

    private val foregroundPermissions = arrayOf(
        Manifest.permission.BODY_SENSORS,
        Manifest.permission.ACTIVITY_RECOGNITION
    )

    private val requestForegroundLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            updateStatus()
            maybeRequestBackground()
        }

    private val requestBackgroundLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            updateStatus()
            maybeRequestHealthConnect()
        }

    private val requestHealthConnectLauncher =
        registerForActivityResult(
            PermissionController.createRequestPermissionResultContract(HealthConnectStatsTracker.PROVIDER_PACKAGE)
        ) {
            updateStatus()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        statusView = TextView(this).apply {
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            textSize = 12f
            setPadding(24, 24, 24, 24)
        }
        setContentView(statusView)
        updateStatus()

        val missing = foregroundPermissions.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestForegroundLauncher.launch(missing.toTypedArray())
        } else {
            maybeRequestBackground()
        }
    }

    private fun maybeRequestBackground() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            maybeRequestHealthConnect()
            return
        }
        if (checkSelfPermission(Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            maybeRequestHealthConnect()
            return
        }
        if (checkSelfPermission(Manifest.permission.BODY_SENSORS_BACKGROUND) == PackageManager.PERMISSION_GRANTED) {
            maybeRequestHealthConnect()
            return
        }
        requestBackgroundLauncher.launch(Manifest.permission.BODY_SENSORS_BACKGROUND)
    }

    private fun maybeRequestHealthConnect() {
        if (!HealthConnectStatsTracker.isSdkAvailable(this)) {
            updateStatus()
            return
        }
        lifecycleScope.launch {
            val granted = try {
                androidx.health.connect.client.HealthConnectClient.getOrCreate(
                    this@PermissionRequestActivity, HealthConnectStatsTracker.PROVIDER_PACKAGE
                ).permissionController.getGrantedPermissions()
            } catch (e: Exception) {
                emptySet()
            }
            val needed = HealthConnectStatsTracker.requiredPermissions() - granted
            if (needed.isNotEmpty()) {
                requestHealthConnectLauncher.launch(needed)
            } else {
                updateStatus()
            }
        }
    }

    private fun updateStatus() {
        val allPerms = foregroundPermissions.toList() +
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                listOf(Manifest.permission.BODY_SENSORS_BACKGROUND)
            } else {
                emptyList()
            })
        val lines = allPerms.map { perm ->
            val granted = checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED
            "${perm.substringAfterLast('.')}: ${if (granted) "GRANTED" else "denied"}"
        }
        val healthConnectLine = if (HealthConnectStatsTracker.isSdkAvailable(this)) {
            "Health Connect: requesting/granted -- see toast/dialog"
        } else {
            "Health Connect: unavailable on this device"
        }
        statusView.text = (listOf("Omnitrix health permissions", "") + lines + listOf("", healthConnectLine))
            .joinToString("\n")
    }
}
