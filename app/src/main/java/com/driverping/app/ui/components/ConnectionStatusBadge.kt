package com.driverping.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.driverping.app.ui.theme.StatusActive
import com.driverping.app.ui.theme.StatusInactive
import com.driverping.app.ui.theme.StatusWarning

/**
 * Estados de conexión de la optimización de red.
 */
enum class ConnectionState(
    val displayText: String,
    val color: Color
) {
    OPTIMAL("Protegido", StatusActive),
    TUNING("Ajustando...", StatusWarning),
    DISCONNECTED("En Pausa", StatusInactive)
}

/**
 * Componente de Semáforo de Estado (ConnectionStatusBadge)
 * Píldora redondeada de alta visibilidad para conductores.
 */
@Composable
fun ConnectionStatusBadge(
    state: ConnectionState,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            // Punto de brillo del estado
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .background(color = state.color, shape = CircleShape)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = state.displayText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = state.color
            )
        }
    }
}
