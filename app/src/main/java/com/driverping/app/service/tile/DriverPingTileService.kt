package com.driverping.app.service.tile

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.driverping.app.data.SettingsRepository
import com.driverping.app.service.overlay.HudOverlayService
import com.driverping.app.service.vpn.FirewallVpnService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * TileService para el menú de Ajustes Rápidos (Barra de estado superior).
 * Permite al conductor encender o apagar el "Modo Trabajo" con un solo toque
 * desde el menú desplegable del teléfono sin abrir la app.
 */
@AndroidEntryPoint
class DriverPingTileService : TileService() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val active = settingsRepository.isWorkModeActive()
        if (active) {
            stopService(Intent(this, FirewallVpnService::class.java))
            stopService(Intent(this, HudOverlayService::class.java))
            settingsRepository.setWorkModeActive(false)
        } else {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(Intent(this, HudOverlayService::class.java))
                } else {
                    startService(Intent(this, HudOverlayService::class.java))
                }
                startService(
                    Intent(this, FirewallVpnService::class.java)
                        .setAction(FirewallVpnService.ACTION_START)
                )
                settingsRepository.setWorkModeActive(true)
            } catch (_: Exception) {}
        }
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val active = settingsRepository.isWorkModeActive()
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (active) "YoPrimero: ON" else "YoPrimero"
        tile.updateTile()
    }
}
