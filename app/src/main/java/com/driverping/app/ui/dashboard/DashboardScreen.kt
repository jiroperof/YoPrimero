package com.driverping.app.ui.dashboard

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.driverping.app.MainActivity
import com.driverping.app.R
import com.driverping.app.data.PermissionStatus
import com.driverping.app.data.PermissionType
import com.driverping.app.ui.components.ConnectionState
import com.driverping.app.ui.components.ConnectionStatusBadge
import com.driverping.app.ui.components.PowerButton

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    // Refrescar permisos cuando la app vuelve a primer plano
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Snackbar de mensajes
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    fun handlePermissionClick(type: PermissionType) {
        when (type) {
            PermissionType.VPN -> {
                val intent = VpnService.prepare(context)
                if (intent != null) {
                    val activity = context as? Activity
                    if (activity is MainActivity) {
                        activity.launchVpnPermission(intent)
                    } else {
                        @Suppress("DEPRECATION")
                        activity?.startActivityForResult(intent, 1001)
                    }
                } else {
                    viewModel.refreshPermissions()
                }
            }
            PermissionType.NOTIFICATION -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    val activity = context as? Activity
                    if (activity != null) {
                        ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1002)
                    } else {
                        viewModel.openPermissionSettings(type)
                    }
                } else {
                    viewModel.openPermissionSettings(type)
                }
            }
            else -> {
                viewModel.openPermissionSettings(type)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Header(workModeActive = uiState.workModeActive)
            Spacer(Modifier.height(12.dp))
            TrustBadgesRow(workModeActive = uiState.workModeActive)
            Spacer(Modifier.height(16.dp))
            WorkModeButton(
                active = uiState.workModeActive,
                enabled = uiState.allPermissionsGranted,
                inProgress = uiState.progress,
                livePingMs = uiState.livePingMs,
                ramKilled = uiState.ramProcessesKilled,
                onClick = {
                    if (uiState.allPermissionsGranted) {
                        viewModel.toggleWorkMode()
                    } else {
                        val missing = uiState.permissions.firstOrNull { !it.granted }
                        if (missing != null) {
                            handlePermissionClick(missing.type)
                        } else {
                            viewModel.toggleWorkMode()
                        }
                    }
                }
            )
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.runTurboOptimization(context) },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🚀", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.size(10.dp))
                        Column {
                            Text("Turbo Limpieza y Aceleración", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Libera RAM y limpia caché DNS en 1 segundo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text("Ejecutar", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(14.dp))
            DriverOptimizationsSection(
                workModeActive = uiState.workModeActive,
                onOpenNotificationListener = {
                    try {
                        val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } catch (_: Exception) {
                    }
                }
            )
            Spacer(Modifier.height(20.dp))
            LivePingMonitoringSection(
                pingMs = uiState.livePingMs,
                pingHistory = uiState.pingHistory,
                isTestingDns = uiState.isTestingDns,
                dnsResults = uiState.dnsResults,
                onRunDnsTest = { viewModel.runDnsTest() }
            )
            Spacer(Modifier.height(20.dp))
            PermissionsSection(
                permissions = uiState.permissions,
                onPermissionClick = { permission ->
                    handlePermissionClick(permission.type)
                }
            )
            Spacer(Modifier.height(20.dp))
            ChineseRomCompatSection(
                onAutoStartClick = { viewModel.openAutoStartSettings() },
                onMiuiPopupClick = { viewModel.openMiuiAdvancedSettings() }
            )
            Spacer(Modifier.height(20.dp))
            WhitelistSection(
                whitelist = uiState.whitelistPackages,
                installedApps = uiState.installedApps,
                isLoadingApps = uiState.isLoadingApps,
                onLoadApps = { viewModel.loadInstalledApps(context) },
                onToggleApp = { viewModel.toggleAppWhitelist(it) },
                onAddPackage = { viewModel.addPackageToWhitelist(it) },
                onRemovePackage = { viewModel.removePackageFromWhitelist(it) },
                onResetDefault = { viewModel.resetWhitelist() }
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Header(workModeActive: Boolean) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = "YoPrimero Logo",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(Modifier.size(10.dp))
                Column {
                    Text(
                        text = "YoPrimero",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "¡Tú entregas primero!",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Spacer(Modifier.size(8.dp))

            val connectionState = when {
                workModeActive -> ConnectionState.OPTIMAL
                else -> ConnectionState.DISCONNECTED
            }
            ConnectionStatusBadge(state = connectionState)
        }

        Spacer(Modifier.height(10.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Optimización de red y Firewall para repartidores y conductores",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.size(8.dp))
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "v2.7 REAL",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun TrustBadgesRow(workModeActive: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TrustBadgeChip(
            icon = "⚡",
            label = "Red Cloudflare",
            active = workModeActive,
            modifier = Modifier.weight(1f)
        )
        TrustBadgeChip(
            icon = "📍",
            label = "GPS 2s Activo",
            active = workModeActive,
            modifier = Modifier.weight(1f)
        )
        TrustBadgeChip(
            icon = "🛡️",
            label = "Anti-Cierre",
            active = workModeActive,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TrustBadgeChip(
    icon: String,
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (active) Color(0xFF00D084).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (active) Color(0xFF00D084).copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(icon, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (active) Color(0xFF00D084) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun WorkModeButton(
    active: Boolean,
    enabled: Boolean,
    inProgress: Boolean,
    livePingMs: Long,
    ramKilled: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PowerButton(
                active = active,
                enabled = enabled,
                inProgress = inProgress,
                onClick = onClick
            )

            Spacer(Modifier.height(16.dp))

            when {
                inProgress -> {
                    Text(
                        text = "Ajustando ruta de red y VPN...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                !enabled -> {
                    Text(
                        text = "Toca para configurar permisos faltantes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Se requieren permisos para encender el Modo Trabajo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                active -> {
                    val pingText = if (livePingMs >= 0) "$livePingMs ms" else "--"
                    Text(
                        text = "MODO YOPRIMERO ACTIVO",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Firewall Activo • Ping $pingText • RAM Limpia ($ramKilled proc)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                else -> {
                    Text(
                        text = "MODO YOPRIMERO EN PAUSA",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Toca el botón central para activar la protección de red",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DriverOptimizationsSection(
    workModeActive: Boolean,
    onOpenNotificationListener: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Optimización Avanzada de Asignación",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))

            // 1. Refresco GPS 2s
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Refresco GPS de Alta Frecuencia (2s)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (workModeActive) "Módem GPS activo reportando coordenadas cada 2s" else "Se activará al encender el Modo Trabajo",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .background(
                            color = if (workModeActive) Color(0xFF00D084).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (workModeActive) "2s ACTIVO" else "EN REPOSO",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (workModeActive) Color(0xFF00D084) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // 2. Sobreescritura de Alertas
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenNotificationListener() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Sobreescritura de Alertas (100% Vol)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Enciende pantalla y eleva volumen al recibir carreras",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = "Ajustes",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
fun LivePingMonitoringSection(
    pingMs: Long,
    pingHistory: List<Long>,
    isTestingDns: Boolean,
    dnsResults: List<DnsResult>,
    onRunDnsTest: () -> Unit
) {
    var showDnsTestDialog by remember { mutableStateOf(false) }

    val (qualityLabel, qualityColor) = when {
        pingMs in 0..50 -> "Excelente" to Color(0xFF10B981)
        pingMs in 51..120 -> "Aceptable" to Color(0xFFFFB800)
        pingMs > 120 -> "Lento" to Color(0xFFEF4444)
        else -> "Midiendo..." to Color.Gray
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF222E42), RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = "Latencia en Vivo (Ping)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .background(qualityColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = qualityLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = qualityColor
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = if (pingMs >= 0) "$pingMs ms" else "-- ms",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "DNS Cloudflare 1.1.1.1 (UDP 53)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.height(48.dp)
                ) {
                    val maxPing = (pingHistory.maxOrNull() ?: 150L).coerceAtLeast(100L)
                    pingHistory.forEach { p ->
                        val barHeight = ((p.toFloat() / maxPing) * 44).coerceIn(6f, 44f).dp
                        Box(
                            modifier = Modifier
                                .size(width = 8.dp, height = barHeight)
                                .background(
                                    color = if (p <= 60) Color(0xFF10B981) else if (p <= 120) Color(0xFFFFB800) else Color(0xFFEF4444),
                                    shape = RoundedCornerShape(4.dp)
                                )
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    onRunDnsTest()
                    showDnsTestDialog = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.NetworkCheck, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Probar Servidores DNS (Multi-Speedtest)", fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showDnsTestDialog) {
        AlertDialog(
            onDismissRequest = { showDnsTestDialog = false },
            title = { Text("Prueba de Latencia Multi-DNS", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Midiendo el servidor DNS más rápido para tu operadora:")
                    Spacer(Modifier.height(16.dp))

                    if (isTestingDns) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(12.dp))
                                Text("Realizando pings a Cloudflare, Google y Quad9...")
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            dnsResults.forEachIndexed { index, dns ->
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (index == 0)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else
                                            MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(dns.providerName, fontWeight = FontWeight.Bold)
                                                if (index == 0) {
                                                    Spacer(Modifier.size(6.dp))
                                                    Text("🏆 Más Rápido", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                            Text(dns.host, style = MaterialTheme.typography.labelSmall)
                                        }
                                        Text(
                                            text = if (dns.latencyMs >= 0) "${dns.latencyMs} ms" else "Error",
                                            fontWeight = FontWeight.Bold,
                                            color = if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showDnsTestDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }
}

@Composable
private fun PermissionsSection(
    permissions: List<PermissionStatus>,
    onPermissionClick: (PermissionStatus) -> Unit
) {
    Text(
        text = "Permisos para Salir a Trabajar",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground
    )

    if (permissions.isNotEmpty()) {
        val granted = permissions.count { it.granted }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { granted.toFloat() / permissions.size },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "$granted / ${permissions.size} permisos concedidos",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(Modifier.height(12.dp))

    when {
        permissions.isEmpty() -> Card(modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Verificando permisos...")
                }
            }
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            permissions.forEach { permission ->
                PermissionRow(permission, onClick = { onPermissionClick(permission) })
            }
        }
    }
}

private fun PermissionIcon(type: PermissionType): ImageVector = when (type) {
    PermissionType.OVERLAY -> Icons.Filled.Layers
    PermissionType.ACCESSIBILITY -> Icons.Filled.Accessibility
    PermissionType.VPN -> Icons.Filled.Lock
    PermissionType.NOTIFICATION -> Icons.Filled.Notifications
    PermissionType.LOCATION -> Icons.Filled.LocationOn
}

@Composable
private fun PermissionRow(
    permission: PermissionStatus,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF222E42), RoundedCornerShape(16.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        color = if (permission.granted)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else
                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = PermissionIcon(permission.type),
                    contentDescription = null,
                    tint = if (permission.granted)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                )
            }
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = permission.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = permission.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (permission.granted) {
                Icon(Icons.Filled.CheckCircle, contentDescription = "Concedido", tint = Color(0xFF10B981))
            } else {
                Text("Configurar", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun ChineseRomCompatSection(
    onAutoStartClick: () -> Unit,
    onMiuiPopupClick: () -> Unit
) {
    Column {
        Text(
            text = "Compatibilidad (Xiaomi, Infinix, Samsung)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Si la app se cierra sola o el HUD no aparece, activa estas opciones manualmente:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Color(0xFF222E42), RoundedCornerShape(18.dp))
                    .clickable { onAutoStartClick() },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Icon(Icons.Filled.Power, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text("Inicio Automático", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("Evita que se cierre", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Color(0xFF222E42), RoundedCornerShape(18.dp))
                    .clickable { onMiuiPopupClick() },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Icon(Icons.Filled.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text("Permisos Ocultos", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("Ventana emergente", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WhitelistSection(
    whitelist: Set<String>,
    installedApps: List<InstalledAppInfo>,
    isLoadingApps: Boolean,
    onLoadApps: () -> Unit,
    onToggleApp: (String) -> Unit,
    onAddPackage: (String) -> Unit,
    onRemovePackage: (String) -> Unit,
    onResetDefault: () -> Unit
) {
    var showAppPicker by remember { mutableStateOf(false) }
    var showAddManualDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var manualInput by remember { mutableStateOf("") }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Lista Blanca (Apps Permitidas)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Row {
                IconButton(onClick = {
                    onLoadApps()
                    showAppPicker = true
                }) {
                    Icon(Icons.Filled.PhoneAndroid, contentDescription = "Seleccionar de Teléfono", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { showAddManualDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Añadir ID Manual", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = "Apps cuyo tráfico no es bloqueado por el Firewall y no son cerradas por el limpiador de RAM:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        // Botón principal destacado para seleccionar apps del teléfono
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                .clickable {
                    onLoadApps()
                    showAppPicker = true
                },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Seleccionar Apps de mi Teléfono",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Toca para ver la lista visual de todas tus apps instaladas",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Preset chips rápidos de apps conocidas
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val presets = listOf("Uber Conductor" to "com.ubercab.driver", "DiDi Conductor" to "com.didi.echo.seller", "PedidosYa" to "com.pedidosya")
            presets.forEach { (label, pkg) ->
                val isAdded = whitelist.contains(pkg)
                AssistChip(
                    onClick = { if (!isAdded) onAddPackage(pkg) },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        if (isAdded) Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                        else Icon(Icons.Filled.Add, null, modifier = Modifier.size(14.dp))
                    }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            whitelist.forEach { pkg ->
                AssistChip(
                    onClick = { },
                    label = { Text(pkg, style = MaterialTheme.typography.labelSmall) },
                    trailingIcon = {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Eliminar",
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onRemovePackage(pkg) }
                        )
                    }
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { onResetDefault() }) {
            Text("Restablecer Lista Blanca por Defecto", color = MaterialTheme.colorScheme.primary)
        }
    }

    // Modal para seleccionar Apps instaladas
    if (showAppPicker) {
        AlertDialog(
            onDismissRequest = { showAppPicker = false },
            title = {
                Text(
                    text = "Seleccionar Apps Instaladas",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.height(420.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Buscar app...") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))

                    if (isLoadingApps) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        val filtered = installedApps.filter {
                            it.appName.contains(searchQuery, ignoreCase = true) ||
                                it.packageName.contains(searchQuery, ignoreCase = true)
                        }

                        if (filtered.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No se encontraron aplicaciones", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filtered, key = { it.packageName }) { app ->
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (app.isWhitelisted)
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            else
                                                MaterialTheme.colorScheme.surface
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onToggleApp(app.packageName) }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = app.appName,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = app.packageName,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Switch(
                                                checked = app.isWhitelisted,
                                                onCheckedChange = { onToggleApp(app.packageName) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showAppPicker = false }) {
                    Text("Listo")
                }
            }
        )
    }

    // Modal para añadir paquete manual
    if (showAddManualDialog) {
        AlertDialog(
            onDismissRequest = { showAddManualDialog = false },
            title = { Text("Añadir ID de Paquete Manual") },
            text = {
                Column {
                    Text("Introduce el ID del paquete de la aplicación (ej. com.ubercab.driver):")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = manualInput,
                        onValueChange = { manualInput = it },
                        label = { Text("ID del Paquete (com.ejemplo.app)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (manualInput.isNotBlank()) {
                            onAddPackage(manualInput)
                            manualInput = ""
                            showAddManualDialog = false
                        }
                    }
                ) {
                    Text("Añadir")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddManualDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
