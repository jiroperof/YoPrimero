package com.driverping.app.data

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat

enum class PermissionType {
    OVERLAY, ACCESSIBILITY, VPN, NOTIFICATION, LOCATION;
}

data class PermissionStatus(
    val type: PermissionType,
    val name: String,
    val description: String,
    val granted: Boolean
)

class PermissionRepository(private val context: Context) {

    fun getPermissionStatuses(): List<PermissionStatus> = listOf(
        PermissionStatus(
            type = PermissionType.OVERLAY,
            name = "Ventana flotante (HUD)",
            description = "Muestra tu ping en tiempo real encima de las demás apps.",
            granted = Settings.canDrawOverlays(context)
        ),
        PermissionStatus(
            type = PermissionType.ACCESSIBILITY,
            name = "Accesibilidad",
            description = "Automatiza la desactivación de la optimización de batería.",
            granted = isAccessibilityEnabled()
        ),
        PermissionStatus(
            type = PermissionType.VPN,
            name = "VPN (Firewall)",
            description = "Bloquea tráfico de apps ajenas y fuerza DNS a Cloudflare.",
            granted = VpnService.prepare(context) == null
        ),
        PermissionStatus(
            type = PermissionType.NOTIFICATION,
            name = "Notificaciones",
            description = "Notificación persistente del servicio de trabajo.",
            granted = hasNotificationPermission()
        ),
        PermissionStatus(
            type = PermissionType.LOCATION,
            name = "Ubicación GPS (Alta Precisión)",
            description = "Refresco GPS de 2s para prioridad de asignación de viajes.",
            granted = hasLocationPermission()
        )
    )

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("InlinedApi")
    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isAccessibilityEnabled(): Boolean {
        val accessibilityEnabled = try {
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            ) == 1
        } catch (_: Exception) {
            false
        }

        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        if (am != null) {
            val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            val myPackage = context.packageName
            val isEnabledInManager = enabledServices.any { service ->
                val info = service.resolveInfo?.serviceInfo
                info != null && info.packageName == myPackage
            }
            if (isEnabledInManager) return true
        }

        val enabledString = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""

        val myPackage = context.packageName
        val isPackageInSettings = enabledString.contains(myPackage, ignoreCase = true) ||
            enabledString.contains("AutoConfigAccessibilityService", ignoreCase = true)

        return (accessibilityEnabled && isPackageInSettings)
    }

    fun openSettingsFor(type: PermissionType): Intent = when (type) {
        PermissionType.OVERLAY -> Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        PermissionType.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        PermissionType.VPN -> VpnService.prepare(context) ?: Intent(Settings.ACTION_SETTINGS)
        PermissionType.NOTIFICATION -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
        PermissionType.LOCATION -> Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
    }
}
