package com.etwow.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val scheme = darkColorScheme(
    primary = Color(0xFF7CD4FD),
    onPrimary = Color(0xFF00344A),
    secondary = Color(0xFF9BE38B),
    onSecondary = Color(0xFF00391A),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF1C232C),
    onSurfaceVariant = Color(0xFFA9B4C0),
    outline = Color(0xFF2B3440),
)

@Composable
fun EtwowTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
