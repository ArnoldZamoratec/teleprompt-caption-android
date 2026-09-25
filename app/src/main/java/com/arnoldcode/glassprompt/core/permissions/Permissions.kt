package com.arnoldcode.glassprompt.core.permissions

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

enum class PermissionStatus {
    Granted,

    /** Not granted yet; asking will show the system dialog. */
    Requestable,

    /** Denied with "don't ask again" (or blocked by policy): only app settings can grant it. */
    PermanentlyDenied,
}

@Stable
class PermissionsState internal constructor(
    val permissions: List<String>,
    private val context: Context,
    initialStatus: PermissionStatus,
) {
    var status by mutableStateOf(initialStatus)
        internal set

    internal var request: () -> Unit = {}

    val allGranted: Boolean get() = status == PermissionStatus.Granted

    fun launchRequest() = request()

    fun openAppSettings() {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/**
 * Runtime permission state for [permissions]. Refreshes on every resume, so granting from
 * the system settings screen is picked up when the user comes back.
 */
@Composable
fun rememberPermissionsState(permissions: List<String>): PermissionsState {
    val context = LocalContext.current
    var requestedOnce by rememberSaveable { mutableStateOf(false) }
    val state = remember(permissions) {
        PermissionsState(permissions, context, context.statusOf(permissions, requestedOnce))
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        requestedOnce = true
        state.status = context.statusOf(permissions, requestedOnce = true)
    }
    state.request = { launcher.launch(permissions.toTypedArray()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { state.status = context.statusOf(permissions, requestedOnce) }
    return state
}

private fun Context.statusOf(permissions: List<String>, requestedOnce: Boolean): PermissionStatus {
    val missing = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
    if (missing.isEmpty()) return PermissionStatus.Granted
    val activity = findActivity() ?: return PermissionStatus.Requestable
    // After a denial Android stops showing the dialog once the rationale flag goes false.
    val blocked = requestedOnce && missing.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
    return if (blocked) PermissionStatus.PermanentlyDenied else PermissionStatus.Requestable
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
