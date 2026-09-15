package com.driverping.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Tema "Neo-Dark Driver" (YoPrimero)
 * Forzado a estética oscura de alto contraste para visibilidad óptima de conductores.
 * Deshabilita colores dinámicos de Android 12+ para conservar la identidad de marca.
 */
private val NeoDarkColorScheme = darkColorScheme(
    primary = NeoPrimary,
    onPrimary = NeoBackground,
    primaryContainer = NeoPrimaryContainer,
    onPrimaryContainer = NeoPrimary,
    secondary = NeoSecondary,
    onSecondary = NeoBackground,
    background = NeoBackground,
    onBackground = NeoTextPrimary,
    surface = NeoSurface,
    onSurface = NeoTextPrimary,
    surfaceVariant = NeoSurfaceElevated,
    onSurfaceVariant = NeoTextSecondary,
    outline = NeoOutline,
    outlineVariant = NeoOutline
)

@Composable
fun DriverPingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Forzamos el esquema NeoDarkColorScheme para mantener la identidad de marca constante en cualquier dispositivo
    MaterialTheme(
        colorScheme = NeoDarkColorScheme,
        content = content
    )
}
