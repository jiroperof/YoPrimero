package com.driverping.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.driverping.app.ui.theme.StatusActive
import com.driverping.app.ui.theme.StatusInactive

/**
 * Botón Maestro de Encendido (PowerButton)
 * Botón central circular grande optimizado para conductores.
 * Incluye respuesta táctil fluida (escala) de bajo consumo de GPU.
 */
@Composable
fun PowerButton(
    active: Boolean,
    enabled: Boolean,
    inProgress: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Animación de escala ligera para respuesta táctil suave
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = tween(durationMillis = 100),
        label = "power_button_scale"
    )

    val buttonColor = when {
        active -> StatusActive
        else -> StatusInactive
    }

    val glowBrush = when {
        active -> Brush.radialGradient(
            listOf(StatusActive.copy(alpha = 0.25f), Color.Transparent)
        )
        else -> Brush.radialGradient(
            listOf(MaterialTheme.colorScheme.surfaceVariant, Color.Transparent)
        )
    }

    Box(
        modifier = modifier
            .scale(scale)
            .size(160.dp),
        contentAlignment = Alignment.Center
    ) {
        // Resplandor exterior suave
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(glowBrush, CircleShape)
        )

        // Círculo interactivo principal
        Surface(
            modifier = Modifier
                .size(136.dp)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = !inProgress,
                    onClick = onClick
                ),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                width = 3.dp,
                color = if (active) StatusActive else MaterialTheme.colorScheme.outline
            ),
            shadowElevation = if (active) 12.dp else 4.dp
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                if (inProgress) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(54.dp)
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PowerSettingsNew,
                            contentDescription = if (active) "Desactivar" else "Activar",
                            tint = buttonColor,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (active) "ON" else "OFF",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = buttonColor
                        )
                    }
                }
            }
        }
    }
}
