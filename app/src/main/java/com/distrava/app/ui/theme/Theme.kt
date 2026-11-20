package com.distrava.app.ui.theme

import androidx.compose.material3.MaterialTheme
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
)

@Composable
fun DistravaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, content = content)
}
