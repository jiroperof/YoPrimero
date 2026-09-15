package com.driverping.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.driverping.app.data.SettingsRepository
import com.driverping.app.service.overlay.HudOverlayService
import com.driverping.app.service.vpn.FirewallVpnService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Escucha el reinicio del dispositivo.
 * Si el Modo Trabajo estaba activo antes de apagar, levanta automáticamente
 * el HUD y el Firewall VPN sin que el usuario tenga que abrir la app.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (settingsRepository.isWorkModeActive()) {
                // Iniciar HUD
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(Intent(context, HudOverlayService::class.java))
                    } else {
                        context.startService(Intent(context, HudOverlayService::class.java))
                    }
                } catch (_: Exception) {
                    context.startService(Intent(context, HudOverlayService::class.java))
                }
                // Iniciar VPN
                try {
                    context.startService(
                        Intent(context, FirewallVpnService::class.java)
                            .setAction(FirewallVpnService.ACTION_START)
                    )
                } catch (_: Exception) {}
            }
        }
    }
}
