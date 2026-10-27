package com.driverping.app.service.notification

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.driverping.app.data.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * AlertOverrideNotificationService (Alertas Prioritarias para Conductores)
 * -----------------------------------------------------------------------
 * Escucha notificaciones entrantes de apps de trabajo (Yummy, Yango, Ridery, Uber, DiDi, PedidosYa).
 * Cuando llega una oferta de viaje válida:
 *   1. Despierta la pantalla (PowerManager WakeLock).
 *   2. Eleva temporalmente el volumen de STREAM_MUSIC al 100%.
 *   3. Emite un tono de alerta sin cortar el sonido prematuramente.
 *   4. Restaura el volumen anterior después de 5 segundos.
 */
@AndroidEntryPoint
class AlertOverrideNotificationService : NotificationListenerService() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val serviceScope = CoroutineScope(Dispatchers.IO)

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

        // Ignorar notificaciones ongoing (persistentes)
        if (sbn.isOngoing) return

        val notif = sbn.notification ?: return
        val extras = notif.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString()?.lowercase() ?: ""
        val text = extras.getCharSequence("android.text")?.toString()?.lowercase() ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString()?.lowercase() ?: ""
        val fullContent = "$title $text $bigText"

        // Paquetes estrictos de apps de viaje
        val isRideApp = pkgName.contains("yummy") ||
            pkgName.contains("yango") ||
            pkgName.contains("ridery") ||
            pkgName.contains("uber") ||
            pkgName.contains("didi") ||
            pkgName.contains("pedidosya") ||
            pkgName == "com.yummyrides" ||
            pkgName == "com.yandex.yango" ||
            pkgName == "com.ridery"

        // Palabras clave estrictas para filtrar únicamente solicitudes de carrera
        val rideKeywords = listOf(
            "carrera", "viaje", "solicitud", "pedido", "order", "trip",
            "oferta", "nueva", "pasajero", "cliente", "asignad", "servicio"
        )

        val isRideAlert = isRideApp && rideKeywords.any { fullContent.contains(it) }

        if (isRideAlert) {
            Log.i(TAG, "¡Carrera detectada de $pkgName! Iniciando alerta prioritaria...")
            executePriorityAlert()
        }
    }

    private fun executePriorityAlert() {
        serviceScope.launch {
            var toneGenerator: ToneGenerator? = null
            var previousVolume = -1
            var audioManager: AudioManager? = null

            try {
                val context = applicationContext

                // Advertir si hay audífonos o Bluetooth conectados
                audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                if (isAudioOutputHeadsetOrBluetooth(audioManager)) {
                    Log.i(TAG, "Dispositivo de audio externo o Bluetooth detectado")
                }

                // 1. Despertar pantalla y CPU
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                @Suppress("DEPRECATION")
                val wakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                    "YoPrimero:PriorityAlertWakeLock"
                )
                wakeLock.acquire(5000L) // 5s pantalla encendida

                // 2. Guardar volumen actual y elevar al 100%
                previousVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)

                // 3. Emitir tono de alerta respetando la duración completa
                toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                toneGenerator.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 800)
                delay(850L) // Esperar a que el tono de 800ms se escuche completo antes de liberarlo

                // 4. Restaurar volumen anterior tras 5 segundos
                delay(4000L)
                if (previousVolume >= 0) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, previousVolume, 0)
                    Log.i(TAG, "Volumen restaurado al nivel previo: $previousVolume")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al ejecutar alerta prioritaria", e)
            } finally {
                try {
                    toneGenerator?.release()
                } catch (_: Exception) {}
            }
        }
    }

    private fun isAudioOutputHeadsetOrBluetooth(am: AudioManager): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            return devices.any {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            }
        } else {
            @Suppress("DEPRECATION")
            return am.isWiredHeadsetOn || am.isBluetoothA2dpOn
        }
    }
}
