package com.driverping.app.util

import android.R
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

/**
 * Helpers de notificaciones y servicios del sistema.
 */
object NotificationHelper {

    const val CHANNEL_FIREWALL = "firewall_channel"
    const val CHANNEL_HUD = "hud_channel"

    const val NOTIF_FIREWALL_ID = 1001
    const val NOTIF_HUD_ID = 1002

    fun ensureNotificationChannel(context: Context, id: String, name: String, importance: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(id, name, importance)
            getManager(context).createNotificationChannel(channel)
        }
    }

    fun buildForegroundNotification(
        context: Context,
        channelId: String,
        title: String,
        text: String,
        actionIntent: PendingIntent? = null
    ): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        builder.setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_lock_lock)
            .setOngoing(true)

        if (actionIntent != null) {
            val action = Notification.Action.Builder(
                R.drawable.ic_menu_close_clear_cancel,
                "Detener",
                actionIntent
            ).build()
            builder.addAction(action)
        }

        return builder.build()
    }

    private fun getManager(context: Context): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
}

/**
 * Helpers de permisos sensibles del sistema.
 */
object SystemHelper {

    fun isOverlayGranted(context: Context): Boolean =
        android.provider.Settings.canDrawOverlays(context)

    fun isAccessibilityEnabled(context: Context, serviceClass: Class<*>): Boolean {
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
            enabledString.contains(serviceClass.simpleName, ignoreCase = true)

        return (accessibilityEnabled && isPackageInSettings)
    }

    fun isVpnPrepared(context: Context): Boolean =
        VpnService.prepare(context) == null

    fun isBatteryOptimizationIgnored(context: Context, packageName: String): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    fun isNotifPermissionGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }
}
