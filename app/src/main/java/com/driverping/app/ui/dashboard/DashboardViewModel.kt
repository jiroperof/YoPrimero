package com.driverping.app.ui.dashboard

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.driverping.app.data.PermissionRepository
import com.driverping.app.data.PermissionStatus
import com.driverping.app.data.SettingsRepository
import com.driverping.app.service.overlay.HudOverlayService
import com.driverping.app.service.vpn.FirewallVpnService
import com.driverping.app.util.OemAutostartHelper
import com.driverping.app.util.RamCleaner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlinx.coroutines.delay

data class InstalledAppInfo(
    val appName: String,
    val packageName: String,
    val isSystemApp: Boolean,
    val isWhitelisted: Boolean
)

data class DnsResult(
    val providerName: String,
    val host: String,
    val latencyMs: Long
)

data class DashboardUiState(
    val workModeActive: Boolean = false,
    val permissions: List<PermissionStatus> = emptyList(),
    val allPermissionsGranted: Boolean = false,
    val progress: Boolean = false,
    val message: String? = null,
    val ramProcessesKilled: Int = 0,
    val whitelistPackages: Set<String> = emptySet(),
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val isLoadingApps: Boolean = false,
    val livePingMs: Long = -1L,
    val pingHistory: List<Long> = emptyList(),
    val isTestingDns: Boolean = false,
    val dnsResults: List<DnsResult> = emptyList()
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    application: Application,
    private val permissionRepository: PermissionRepository,
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        // Restaurar el estado persistido del Modo Trabajo y Lista Blanca
        _uiState.value = _uiState.value.copy(
            workModeActive = settingsRepository.isWorkModeActive(),
            whitelistPackages = settingsRepository.getWhitelistPackages()
        )
        startLivePingMonitoring()
    }

    fun refreshPermissions() {
        val perms = permissionRepository.getPermissionStatuses()
        _uiState.value = _uiState.value.copy(
            permissions = perms,
            allPermissionsGranted = perms.all { it.granted }
        )
    }

    fun toggleWorkMode() {
        val current = _uiState.value.workModeActive
        val context = getApplication<Application>()
        if (current) {
            stopWorkMode(context)
        } else {
            startWorkMode(context)
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun openPermissionSettings(type: com.driverping.app.data.PermissionType) {
        val context = getApplication<Application>()
        val intent = permissionRepository.openSettingsFor(type)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun openAutoStartSettings() {
        val context = getApplication<Application>()
        val success = OemAutostartHelper.openAutoStartSettings(context)
        if (!success) {
            _uiState.value = _uiState.value.copy(message = "Usa los ajustes para buscar 'Inicio Automático'")
        }
    }

    fun openMiuiAdvancedSettings() {
        val context = getApplication<Application>()
        OemAutostartHelper.openMiuiAdvancedPermissions(context)
    }

    fun testPriorityAlert(context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(message = "🔔 Probando alerta sonora al 100% de volumen...")
            withContext(Dispatchers.IO) {
                try {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                    val prevVol = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                    val maxVol = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                    audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, maxVol, 0)
                    val toneGen = android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 100)
                    toneGen.startTone(android.media.ToneGenerator.TONE_CDMA_HIGH_L, 800)
                    delay(850L)
                    toneGen.release()
                    delay(3000L)
                    audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, prevVol, 0)
                } catch (_: Exception) {
                }
            }
        }
    }

    fun runTurboOptimization(context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(progress = true)
            val activeWhitelist = settingsRepository.getWhitelistPackages().toList()
            val killed = withContext(Dispatchers.IO) {
                RamCleaner.cleanAllBackgroundExcept(context, activeWhitelist)
            }
            delay(800L)
            _uiState.value = _uiState.value.copy(
                progress = false,
                ramProcessesKilled = killed,
                message = "🚀 ¡Turbo Aplicado! $killed procesos cerrados y memoria optimizada"
            )
        }
    }

    fun refreshWhitelist() {
        _uiState.value = _uiState.value.copy(
            whitelistPackages = settingsRepository.getWhitelistPackages()
        )
    }

    fun loadInstalledApps(context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingApps = true)
            val apps = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val whitelist = settingsRepository.getWhitelistPackages()

                val installed = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        pm.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getInstalledApplications(PackageManager.GET_UNINSTALLED_PACKAGES)
                    }
                } catch (_: Exception) {
                    pm.getInstalledApplications(0)
                }

                installed
                    .map { appInfo ->
                        val appName = pm.getApplicationLabel(appInfo).toString()
                        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        InstalledAppInfo(
                            appName = appName,
                            packageName = appInfo.packageName,
                            isSystemApp = isSystem,
                            isWhitelisted = whitelist.contains(appInfo.packageName)
                        )
                    }
                    .filter { !it.isSystemApp || whitelist.contains(it.packageName) || it.packageName.contains("chrome") || it.packageName.contains("vending") }
                    .sortedBy { it.appName.lowercase() }
            }
            _uiState.value = _uiState.value.copy(installedApps = apps, isLoadingApps = false)
        }
    }

    fun toggleAppWhitelist(packageName: String) {
        val currentWhitelist = settingsRepository.getWhitelistPackages()
        if (currentWhitelist.contains(packageName)) {
            settingsRepository.removeWhitelistPackage(packageName)
            _uiState.value = _uiState.value.copy(message = "Eliminada de lista blanca: $packageName")
        } else {
            settingsRepository.addWhitelistPackage(packageName)
            _uiState.value = _uiState.value.copy(message = "Añadida a lista blanca: $packageName")
        }
        val newWhitelist = settingsRepository.getWhitelistPackages()
        val updatedApps = _uiState.value.installedApps.map {
            if (it.packageName == packageName) {
                it.copy(isWhitelisted = newWhitelist.contains(packageName))
            } else it
        }
        _uiState.value = _uiState.value.copy(
            whitelistPackages = newWhitelist,
            installedApps = updatedApps
        )
    }

    fun addPackageToWhitelist(pkg: String) {
        val cleanPkg = pkg.trim().lowercase()
        if (cleanPkg.isNotEmpty()) {
            settingsRepository.addWhitelistPackage(cleanPkg)
            refreshWhitelist()
            _uiState.value = _uiState.value.copy(message = "Añadida a lista blanca: $cleanPkg")
        }
    }

    fun removePackageFromWhitelist(pkg: String) {
        settingsRepository.removeWhitelistPackage(pkg)
        refreshWhitelist()
        _uiState.value = _uiState.value.copy(message = "Eliminada de lista blanca: $pkg")
    }

    fun resetWhitelist() {
        settingsRepository.resetWhitelistToDefault()
        refreshWhitelist()
        _uiState.value = _uiState.value.copy(message = "Lista blanca restablecida por defecto")
    }

    private fun startLivePingMonitoring() {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val ping = measureDnsPing("1.1.1.1")
                val currentHistory = _uiState.value.pingHistory.takeLast(9).toMutableList()
                if (ping >= 0) {
                    currentHistory.add(ping)
                }
                _uiState.value = _uiState.value.copy(
                    livePingMs = ping,
                    pingHistory = currentHistory
                )
                delay(3000L)
            }
        }
    }

    fun runDnsTest() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTestingDns = true, dnsResults = emptyList())
            val results = withContext(Dispatchers.IO) {
                listOf(
                    DnsResult("Cloudflare Principal", "1.1.1.1", measureDnsPing("1.1.1.1")),
                    DnsResult("Cloudflare Secundario", "1.0.0.1", measureDnsPing("1.0.0.1")),
                    DnsResult("Google Public DNS", "8.8.8.8", measureDnsPing("8.8.8.8")),
                    DnsResult("Quad9 Secure DNS", "9.9.9.9", measureDnsPing("9.9.9.9"))
                ).sortedBy { if (it.latencyMs >= 0) it.latencyMs else 99999L }
            }
            _uiState.value = _uiState.value.copy(isTestingDns = false, dnsResults = results)
        }
    }

    private fun measureDnsPing(host: String): Long {
        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket()
            socket.soTimeout = 2000
            val query = byteArrayOf(
                0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01
            )
            val address = InetAddress.getByName(host)
            val packet = DatagramPacket(query, query.size, address, 53)
            val start = System.nanoTime()
            socket.send(packet)
            val responseBuffer = ByteArray(512)
            socket.receive(DatagramPacket(responseBuffer, responseBuffer.size))
            (System.nanoTime() - start) / 1_000_000L
        } catch (_: Exception) {
            -1L
        } finally {
            socket?.close()
        }
    }

    private fun startWorkMode(context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(progress = true, message = null)
            // 1. Limpiar RAM en segundo plano respetando la lista blanca dinámica
            val activeWhitelist = settingsRepository.getWhitelistPackages().toList()
            val killed = withContext(Dispatchers.IO) {
                RamCleaner.cleanAllBackgroundExcept(context, activeWhitelist)
            }
            // 2. Preparar y arrancar la VPN
            val prepared = VpnService.prepare(context) == null
            // 3. Arrancar el HUD (overlay + wakelock)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(Intent(context, HudOverlayService::class.java))
                } else {
                    context.startService(Intent(context, HudOverlayService::class.java))
                }
            } catch (_: Exception) {
                context.startService(Intent(context, HudOverlayService::class.java))
            }
            // 4. Arrancar la VPN si está preparada
            if (prepared) {
                try {
                    context.startService(
                        Intent(context, FirewallVpnService::class.java)
                            .setAction(FirewallVpnService.ACTION_START)
                    )
                } catch (_: Exception) {
                }
            }
            // 5. Arrancar el Refresco de GPS de Alta Precisión
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(Intent(context, com.driverping.app.service.location.LocationRefreshService::class.java))
                } else {
                    context.startService(Intent(context, com.driverping.app.service.location.LocationRefreshService::class.java))
                }
            } catch (_: Exception) {
            }
            _uiState.value = _uiState.value.copy(
                workModeActive = true,
                progress = false,
                ramProcessesKilled = killed,
                message = "Modo Trabajo activo · RAM limpiada ($killed procesos)"
            )
            settingsRepository.setWorkModeActive(true)
        }
    }

    private fun stopWorkMode(context: Context) {
        context.stopService(Intent(context, FirewallVpnService::class.java))
        context.stopService(Intent(context, HudOverlayService::class.java))
        context.stopService(Intent(context, com.driverping.app.service.location.LocationRefreshService::class.java))
        _uiState.value = _uiState.value.copy(
            workModeActive = false,
            ramProcessesKilled = 0,
            message = null
        )
        settingsRepository.setWorkModeActive(false)
    }
}
