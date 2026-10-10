package com.shrinkkk.acapellamaker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.shrinkkk.acapellamaker.model.Project
import com.shrinkkk.acapellamaker.ui.calibration.CalibrationScreen
import com.shrinkkk.acapellamaker.ui.permissions.PermissionScreen
import com.shrinkkk.acapellamaker.ui.record.GridRecordScreen
import com.shrinkkk.acapellamaker.ui.setup.SetupScreen
import com.shrinkkk.acapellamaker.ui.theme.AcapellaMakerTheme

class MainActivity : ComponentActivity() {

    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
    )

    private var permissionsGranted by mutableStateOf(false)
    private var askedOnce by mutableStateOf(false)

    /** The acapella being recorded; null means we're on the setup screen. */
    private var project by mutableStateOf<Project?>(null)

    /** Calibration is shown on top of whatever screen opened it (the project is kept). */
    private var calibrating by mutableStateOf(false)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            askedOnce = true
            permissionsGranted = hasAllPermissions()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        permissionsGranted = hasAllPermissions()

        setContent {
            AcapellaMakerTheme {
                val current = project
                when {
                    !permissionsGranted -> PermissionScreen(
                        // After a denial Android may stop showing the dialog, so offer Settings instead.
                        showSettingsButton = askedOnce && !canAskAgain(),
                        onRequest = { permissionLauncher.launch(requiredPermissions) },
                        onOpenSettings = ::openAppSettings,
                    )

                    calibrating -> CalibrationScreen(onBack = { calibrating = false })

                    current == null -> SetupScreen(
                        onOpenCalibration = { calibrating = true },
                        // Takes are stored privately in the app (not in the gallery) until export exists.
                        onStart = { layout -> project = Project.createFresh(filesDir, layout) },
                    )

                    else -> GridRecordScreen(
                        lifecycleOwner = this,
                        project = current,
                        onExit = { project = null },
                        onOpenCalibration = { calibrating = true },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Picks up permissions granted from the system Settings screen.
        permissionsGranted = hasAllPermissions()
    }

    private fun hasAllPermissions(): Boolean = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun canAskAgain(): Boolean = requiredPermissions.any {
        ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED &&
            shouldShowRequestPermissionRationale(it)
    }

    private fun openAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null),
            )
        )
    }
}
