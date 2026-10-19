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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PowerSettingsNew
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

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(0) }
    val steps = remember {
        listOf(
            OnboardingStep(
                icon = Icons.Filled.Bolt,
                title = "Recibe Solicitudes Primero",
                description = "Acelera la respuesta de tu red para que las ofertas de viaje de Yummy, Yango y Ridery lleguen directamente a tu pantalla sin demoras."
            ),
            OnboardingStep(
                icon = Icons.Filled.LocationOn,
                title = "Ubicación Exacta y Alertas Fuertes",
                description = "Mantiene tu GPS activo en alta precisión y eleva el volumen al 100% cuando llega un nuevo servicio para que jamás pierdas una carrera."
            ),
            OnboardingStep(
                icon = Icons.Filled.PowerSettingsNew,
                title = "Listo para Salir a Trabajar",
                description = "Con un solo toque en el botón central de encendido, activas toda la protección y aceleración de red. ¡Tú entregas primero!"
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
                    containerColor = MaterialTheme.colorScheme.surface
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
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = step.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        Text("Atrás", color = MaterialTheme.colorScheme.onSurface)
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
                    Text(if (currentStep == steps.lastIndex) "Comenzar" else "Siguiente")
                }
            }
        }
    }
}

private data class OnboardingStep(
    val icon: ImageVector,
    val title: String,
    val description: String
)
