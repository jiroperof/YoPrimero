package com.driverping.app.service.notification

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.driverping.app.data.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * AlertOverrideNotificationService (Sobreescritura de Alertas de Alta Prioridad)
 * -----------------------------------------------------------------------------
 * Escucha notificaciones entrantes. Cuando detecta una oferta de carrera de Yummy, Yango o Ridery:
 *   1. Verifica que el Modo Trabajo esté activo.
 *   2. Enciende la pantalla y despierta la CPU (PowerManager WakeLock con ACQUIRE_CAUSES_WAKEUP).
 *   3. Eleva el volumen multimedia (STREAM_MUSIC) al 100% máximo.
 *   4. Emite un tono sonoro de alerta de alta frecuencia.
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

        // 1. Verificar si el Modo Trabajo está activo
        val workModeActive = try { settingsRepository.isWorkModeActive() } catch (_: Exception) { true }
        if (!workModeActive) return

        val notif = sbn.notification ?: return
        val extras = notif.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString()?.lowercase() ?: ""
        val text = extras.getCharSequence("android.text")?.toString()?.lowercase() ?: ""
        val fullContent = "$title $text"

        // Paquetes estrictos de apps de viaje
        val isRideApp = pkgName.contains("yummy") ||
            pkgName.contains("yango") ||
            pkgName.contains("ridery") ||
            pkgName == "com.yummyrides" ||
            pkgName == "com.yandex.yango" ||
            pkgName == "com.ridery"

        // Palabras clave para filtrar únicamente solicitudes/ofertas de viaje reales
        val rideKeywords = listOf(
            "carrera", "viaje", "solicitud", "pedido", "order", "trip",
            "oferta", "nueva", "pasajero", "cliente", "asignad", "servicio"
        )

        val isRideAlert = isRideApp && rideKeywords.any { fullContent.contains(it) }

        if (isRideAlert) {
            Log.i(TAG, "¡SOLICITUD DE VIAJE DETECTADA! Paquete: $pkgName | Contenido: $fullContent")
            overrideAlert()
        }
    }

    private fun overrideAlert() {
        var toneGenerator: ToneGenerator? = null
        try {
            val context = applicationContext

            // 1. Despertar pantalla y CPU inmediatamente
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                "YoPrimero:AlertOverrideWakeLock"
            )
            wakeLock.acquire(5000L) // 5 segundos de pantalla encendida

            // 2. Elevar el volumen de STREAM_MUSIC al 100%
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)

            // 3. Emitir tono sonoro de alerta de alta frecuencia y liberar generador
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            toneGenerator.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 800)

            Log.i(TAG, "Sobreescritura ejecutada: Volumen al $maxVolume (100%) y pantalla encendida")
        } catch (e: Exception) {
            Log.e(TAG, "Error al ejecutar sobreescritura de alerta", e)
        } finally {
            try {
                toneGenerator?.release()
            } catch (_: Exception) {}
        }
    }
}
