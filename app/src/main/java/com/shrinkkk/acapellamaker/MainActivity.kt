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
import com.shrinkkk.acapellamaker.ui.camera.CameraPreview
import com.shrinkkk.acapellamaker.ui.permissions.PermissionScreen
import com.shrinkkk.acapellamaker.ui.theme.AcapellaMakerTheme

class MainActivity : ComponentActivity() {

    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
    )

    private var permissionsGranted by mutableStateOf(false)
    private var askedOnce by mutableStateOf(false)

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
                if (permissionsGranted) {
                    CameraPreview(lifecycleOwner = this)
                } else {
                    PermissionScreen(
                        // After a denial Android may stop showing the dialog, so offer Settings instead.
                        showSettingsButton = askedOnce && !canAskAgain(),
                        onRequest = { permissionLauncher.launch(requiredPermissions) },
                        onOpenSettings = ::openAppSettings,
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
