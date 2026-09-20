package ru.mirea.reference.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF3857C8),
    secondary = androidx.compose.ui.graphics.Color(0xFF4D5D92),
    tertiary = androidx.compose.ui.graphics.Color(0xFF765188),
)

private val DarkColors = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFFB9C3FF),
    secondary = androidx.compose.ui.graphics.Color(0xFFC1C6E8),
    tertiary = androidx.compose.ui.graphics.Color(0xFFE4B6F2),
)

@Composable
fun ReferenceTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}

