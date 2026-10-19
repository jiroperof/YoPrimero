package com.driverping.app.util

import android.app.ActivityManager
import android.content.Context

/**
 * RAM Cleaner: usa ActivityManager para matar procesos en segundo plano
 * de apps no esenciales antes de iniciar el Modo Trabajo.
 */
object RamCleaner {

    /**
     * Mata el proceso en segundo plano de las apps indicadas.
     * Devuelve la lista de paquetes cuyos procesos fueron terminados.
     */
    fun killBackgroundProcesses(
        context: Context,
        packagesToKill: List<String>
    ): List<String> {
        val killed = mutableListOf<String>()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        for (pkg in packagesToKill) {
            try {
                activityManager.killBackgroundProcesses(pkg)
                killed.add(pkg)
            } catch (_: Exception) {
                // Proceso lanzado por otro inicio o sin permiso
            }
        }
        return killed
    }

    /**
     * Limpia TODAS las apps en background EXCEPTO la lista blanca y las
     * esenciales del sistema. Devuelve cuántos procesos se terminaron.
     */
    fun cleanAllBackgroundExcept(
        context: Context,
        whitelist: List<String>
    ): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val essential = setOf(
            "com.android.systemui",
            "com.android.settings",
            context.packageName
        )
        val protected = essential + whitelist

        val running = activityManager.runningAppProcesses ?: emptyList()
        val targetPackages = running
            .map { it.processName }
            .distinct()
            .filter { pkg ->
                pkg.isNotBlank() &&
                    pkg !in protected &&
                    !pkg.startsWith("com.android") &&
                    !pkg.startsWith("com.google") &&
                    pkg.contains(".")
            }

        var killed = 0
        for (pkg in targetPackages) {
            try {
                activityManager.killBackgroundProcesses(pkg)
                killed++
            } catch (_: Exception) {
            }
        }
        return killed
    }
}

/**
 * WakeLock Helper: mantiene el CPU despierto mientras el Modo Trabajo
 * esté activo para evitar que entre en Doze.
 */
object WakeLockHelper {

    private const val TAG = "DriverPing::Wakelock"

    private var wakeLock: android.os.PowerManager.WakeLock? = null

    fun acquire(context: Context) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        if (wakeLock == null || !wakeLock!!.isHeld) {
            wakeLock = pm.newWakeLock(
                android.os.PowerManager.PARTIAL_WAKE_LOCK,
                TAG
            ).apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 60 * 1000L) // 10 horas máximo para evitar drenaje si la app se olvida encendida
            }
        }
    }

    fun release() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock?.setReferenceCounted(true)
        wakeLock = null
    }

    fun isHeld(): Boolean = wakeLock?.isHeld ?: false
}
