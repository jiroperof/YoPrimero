package com.driverping.app.service.notification

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.driverping.app.data.SettingsRepository
import com.driverping.app.util.Constants
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * AlertOverrideNotificationService (Sobreescritura de Alertas de Alta Prioridad)
 * -----------------------------------------------------------------------------
 * Escucha las notificaciones entrantes mediante NotificationListenerService.
 * Cuando llega una notificación de las apps de la lista blanca (Yummy Rides, Yango, Ridery):
 *   1. Enciende la pantalla y despierta la CPU (PowerManager WakeLock con ACQUIRE_CAUSES_WAKEUP).
 *   2. Eleva el volumen multimedia (STREAM_MUSIC) al 100% máximo.
 *   3. Emite un tono sonoro de alerta de alta prioridad mediante ToneGenerator.
 */
@AndroidEntryPoint
class AlertOverrideNotificationService : NotificationListenerService() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    companion object {
        private const val TAG = "AlertOverrideService"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn ?: return

        val pkgName = sbn.packageName ?: return

        // Obtener la lista blanca de paquetes activos
        val whitelist = try {
            settingsRepository.getWhitelistPackages()
        } catch (_: Exception) {
            Constants.WHITELIST.toSet()
        }

        // Si la notificación pertenece a una app de viaje (ej. com.yummyrides o com.yandex.yango)
        if (whitelist.contains(pkgName) || pkgName.contains("yummy") || pkgName.contains("yango") || pkgName.contains("ridery")) {
            Log.i(TAG, "¡ALERTA DE VIAJE DETECTADA! Paquete: $pkgName")
            overrideAlert()
        }
    }

    private fun overrideAlert() {
        try {
            val context = applicationContext

            // 1. Despertar pantalla y CPU inmediatamente
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                "DriverPing:AlertOverrideWakeLock"
            )
            wakeLock.acquire(5000L) // 5 segundos de pantalla encendida

            // 2. Elevar el volumen de STREAM_MUSIC al 100%
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)

            // 3. Emitir tono sonoro de alerta de alta frecuencia
            val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            toneGenerator.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 800)

            Log.i(TAG, "Sobreescritura ejecutada: Volumen al $maxVolume (100%) y pantalla encendida")
        } catch (e: Exception) {
            Log.e(TAG, "Error al ejecutar sobreescritura de alerta", e)
        }
    }
}
