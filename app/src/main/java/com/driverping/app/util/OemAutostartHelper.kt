package com.driverping.app.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log

/**
 * Utilidad vital para el mercado latinoamericano / venezolano.
 * Equipos como Xiaomi (MIUI/HyperOS), Infinix/Tecno (XOS/HiOS) y Samsung
 * tienen capas de personalización muy agresivas que matan la VPN y el HUD
 * incluso si tienen WakeLocks y Foreground Services.
 *
 * Esta clase intenta abrir los menús ocultos de "Inicio Automático"
 * (AutoStart) o "Ejecución en segundo plano" específicos de cada fabricante.
 */
object OemAutostartHelper {

    private val AUTO_START_INTENTS = arrayOf(
        // Xiaomi / Redmi / POCO (MIUI & HyperOS)
        Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
        // Infinix / Tecno (XOS / HiOS)
        Intent().setComponent(ComponentName("com.transsion.phonemanager", "com.itel.autobootmanager.activity.AutoBootMgrActivity")),
        Intent().setComponent(ComponentName("com.transsion.phonemaster", "com.cyin.himgr.autostart.AutoStartActivity")),
        // Samsung (OneUI)
        Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")),
        Intent().setComponent(ComponentName("com.samsung.android.sm_cn", "com.samsung.android.sm.ui.battery.BatteryActivity")),
        // Huawei
        Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
        // Oppo / Realme
        Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
        // Vivo
        Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
        Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"))
    )

    /**
     * Intenta abrir el gestor de inicio automático del fabricante.
     * Devuelve true si logró encontrar uno, o false si usó el fallback de sistema.
     */
    fun openAutoStartSettings(context: Context): Boolean {
        for (intent in AUTO_START_INTENTS) {
            if (isIntentAvailable(context, intent)) {
                try {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return true
                } catch (e: Exception) {
                    Log.e("OemAutostart", "Fallo al abrir intent OEM: $e")
                }
            }
        }
        // Fallback: Abrir Info de la Aplicación genérica de Android
        openAppInfoSettings(context)
        return false
    }

    /**
     * Específico para Xiaomi/HyperOS: Abrir la configuración de permisos avanzados
     * para conceder el permiso de "Mostrar ventanas emergentes en segundo plano".
     */
    fun openMiuiAdvancedPermissions(context: Context) {
        try {
            val intent = Intent("miui.intent.action.APP_PERM_EDITOR")
            intent.setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
            intent.putExtra("extra_pkgname", context.packageName)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
            openAppInfoSettings(context)
        }
    }

    private fun openAppInfoSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:${context.packageName}")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    private fun isIntentAvailable(context: Context, intent: Intent): Boolean {
        val list = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return list.isNotEmpty()
    }
}
