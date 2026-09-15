package com.driverping.app.service.vpn

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import com.driverping.app.data.SettingsRepository
import com.driverping.app.util.Constants
import com.driverping.app.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import java.io.IOException
import javax.inject.Inject

/**
 * FirewallVpnService
 * ------------------
 * VpnService local que actúa como firewall y optimizador de red.
 * Implementa el ciclo de vida completo y desconexión limpia del túnel TUN.
 */
@AndroidEntryPoint
class FirewallVpnService : VpnService() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    companion object {
        const val ACTION_START = "com.driverping.action.START_VPN"
        const val ACTION_STOP = "com.driverping.action.STOP_VPN"
    }

    private var descriptor: ParcelFileDescriptor? = null
    private var proxyWorker: ProxyWorker? = null
    @Volatile private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.i("VpnService", "Recibida acción ACTION_STOP. Deteniendo servicio...")
            stopInternal()
            stopSelf()
            return START_NOT_STICKY
        }
        startInternal()
        return START_STICKY
    }

    override fun onRevoke() {
        Log.i("VpnService", "Permiso de VPN revocado por el sistema. Limpiando...")
        stopInternal()
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        Log.i("VpnService", "onDestroy ejecutado. Ejecutando rutina de limpieza...")
        stopInternal()
        super.onDestroy()
    }

    private fun startInternal() {
        if (descriptor != null || running) return

        val builder = Builder()
        builder.setSession(Constants.SESSION_KEY)
        builder.setMtu(Constants.MTU)
        builder.addAddress(Constants.TUN_VIRTUAL_IP, Constants.TUN_NETMASK)
        builder.addRoute("0.0.0.0", 0)

        // DNS forzado a Cloudflare (baja latencia)
        builder.addDnsServer(Constants.DNS_PRIMARY)
        builder.addDnsServer(Constants.DNS_SECONDARY)

        // Rutas para que los servidores DNS queden accesibles desde el túnel
        try {
            builder.addRoute(Constants.DNS_PRIMARY, 32)
            builder.addRoute(Constants.DNS_SECONDARY, 32)
        } catch (_: Exception) {
        }

        // FIREWALL POR APP: usa builder.addAllowedApplication(...) para cada paquete
        // de la lista blanca dinámica guardada en preferencias (SettingsRepository).
        val activeWhitelist = settingsRepository.getWhitelistPackages()
        for (pkg in activeWhitelist) {
            try {
                builder.addAllowedApplication(pkg)
            } catch (_: Exception) {
                // El paquete no está instalado: se ignora
            }
        }

        builder.setBlocking(true)
        builder.setUnderlyingNetworks(null) // Usar siempre la red física activa (WiFi/Datos)

        val fd = try {
            builder.establish()
        } catch (e: Exception) {
            Log.e("VpnService", "Fallo al establecer la interfaz VPN", e)
            null
        } ?: run {
            stopSelf()
            return
        }

        descriptor = fd
        running = true
        startForegroundCompat()

        // Motor ProxyWorker que lee los paquetes (DNS, TCP, UDP),
        // los envía evadiendo la VPN (protect) y devuelve las respuestas al túnel.
        proxyWorker = ProxyWorker(this, fd)
        proxyWorker?.start()
    }

    /**
     * Rutina única y centralizada de apagado y limpieza del túnel VPN.
     * Cierra el FileDescriptor en el kernel, detiene los hilos del proxy,
     * cancela las notificaciones y libera el icono de la barra de estado.
     */
    @Synchronized
    private fun stopInternal() {
        if (!running && descriptor == null) return
        running = false

        // 1. Cancelar e interrumpir el motor ProxyWorker y sus hilos/sockets de red
        try {
            proxyWorker?.stop()
            Log.i("VpnService", "Motor ProxyWorker e hilos de red detenidos")
        } catch (e: Exception) {
            Log.e("VpnService", "Error al detener ProxyWorker", e)
        } finally {
            proxyWorker = null
        }

        // 2. Cierre explícito del ParcelFileDescriptor de la interfaz TUN
        descriptor?.let { fd ->
            try {
                fd.close()
                Log.i("VpnService", "ParcelFileDescriptor TUN cerrado limpiamente")
            } catch (e: IOException) {
                Log.e("VpnService", "Error al cerrar ParcelFileDescriptor TUN", e)
            } finally {
                descriptor = null
            }
        }

        // 3. Detener servicio Foreground y cancelar la notificación persistente
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NotificationHelper.NOTIF_FIREWALL_ID)
            Log.i("VpnService", "Notificación persistente removida")
        } catch (e: Exception) {
            Log.e("VpnService", "Error al remover notificación Foreground", e)
        }

        // 4. Actualizar las preferencias
        try {
            settingsRepository.setWorkModeActive(false)
        } catch (_: Exception) {}
    }

    private fun startForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationHelper.ensureNotificationChannel(
                this, NotificationHelper.CHANNEL_FIREWALL,
                "Firewall VPN", NotificationManager.IMPORTANCE_LOW
            )
        }
        val stopIntent = Intent(this, FirewallVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationHelper.buildForegroundNotification(
            this,
            NotificationHelper.CHANNEL_FIREWALL,
            "YoPrimero Firewall",
            "Firewall activo · DNS en Cloudflare 1.1.1.1",
            pendingIntent
        )
        startForeground(NotificationHelper.NOTIF_FIREWALL_ID, notif)
    }
}
