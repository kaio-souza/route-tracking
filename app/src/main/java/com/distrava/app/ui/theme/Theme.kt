package com.distrava.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DistravaOrange = Color(0xFFFC6A03)
val DistravaSand = Color(0xFFEFECE5)
val DistravaGraphite = Color(0xFF2B2B2B)

private val LightColors = lightColorScheme(
    primary = DistravaOrange,
    onPrimary = Color.White,
    background = DistravaSand,
    onBackground = DistravaGraphite,
    surface = DistravaSand,
    onSurface = DistravaGraphite,
    surfaceContainerLowest = Color.White,
    onSurfaceVariant = Color(0xFF7A766E),
)

private val DarkColors = darkColorScheme(
    primary = DistravaOrange,
    onPrimary = Color.White,
    background = Color(0xFF161514),
    onBackground = DistravaSand,
    surface = Color(0xFF161514),
    onSurface = DistravaSand,
    surfaceContainerLowest = Color(0xFF262523),
    onSurfaceVariant = Color(0xFFB0ABA2),
)

@Composable
fun DistravaTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
