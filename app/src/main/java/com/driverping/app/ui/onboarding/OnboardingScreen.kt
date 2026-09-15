package com.driverping.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.driverping.app.data.PermissionType

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(0) }
    val steps = remember {
        listOf(
            OnboardingStep(
                icon = Icons.Filled.Security,
                title = "Bienvenido a YoPrimero",
                description = "Optimizamos tu red y rendimiento para que no entregues pedidos o viajes con lentitud. Te guiaremos por los permisos necesarios.",
                type = null
            ),
            OnboardingStep(
                icon = Icons.Filled.Lock,
                title = "Firewall VPN",
                description = "Creamos una VPN local que bloquea el tráfico de apps que no son de tu trabajo y fuerza tu DNS a Cloudflare (1.1.1.1) para latencias mínimas.",
                type = PermissionType.VPN
            ),
            OnboardingStep(
                icon = Icons.Filled.Layers,
                title = "HUD Flotante",
                description = "Una ventana discreta muestra tu ping en tiempo real. También mantiene la pantalla despierta para que no entren en modo reposo durante el trabajo.",
                type = PermissionType.OVERLAY
            ),
            OnboardingStep(
                icon = Icons.Filled.Notifications,
                title = "Accesibilidad + Notificaciones",
                description = "La accesibilidad automatiza la desactivación de la optimización de batería de tus apps de trabajo. Las notificaciones mantienen el servicio activo.",
                type = PermissionType.ACCESSIBILITY
            )
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 28.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(0.4f))

            // Indicador de progreso
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                steps.forEachIndexed { index, _ ->
                    Box(
                        modifier = Modifier
                            .size(
                                width = if (index == currentStep) 28.dp else 8.dp,
                                height = 8.dp
                            )
                            .background(
                                color = if (index <= currentStep)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
                                shape = MaterialTheme.shapes.small
                            )
                    )
                }
            }

            Spacer(Modifier.height(40.dp))

            val step = steps[currentStep]
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = step.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = step.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.weight(0.6f))

            // Botones
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (currentStep > 0) {
                    Button(
                        onClick = { currentStep-- },
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.ButtonDefaults
                            .buttonColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Text("Atrás")
                    }
                }
                Button(
                    onClick = {
                        if (currentStep < steps.lastIndex) {
                            currentStep++
                        } else {
                            onFinished()
                        }
                    },
                    modifier = Modifier.weight(1.1f)
                ) {
                    Text(if (currentStep == steps.lastIndex) "Ir al Dashboard" else "Siguiente")
                }
            }

            // Mini botón de salto para permisos si es una pantalla de permiso
            step.type?.let { type ->
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        "Este permiso se puede conceder luego en el Dashboard",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

private data class OnboardingStep(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val type: PermissionType?
)
