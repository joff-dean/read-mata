package com.readmata.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ReadMataColors = lightColorScheme(
    primary = Color(0xFF8B3D2E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD2),
    onPrimaryContainer = Color(0xFF3B0903),
    secondary = Color(0xFF725A54),
    background = Color(0xFFFFF8F6),
    surface = Color(0xFFFFF8F6),
    surfaceVariant = Color(0xFFF5DDD7),
)

@Composable
fun ReadMataTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ReadMataColors,
        content = content,
    )
}
