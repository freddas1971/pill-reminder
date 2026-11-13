package com.pillreminder.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Teal = Color(0xFF0F5257)
val TealLight = Color(0xFFD7ECEC)
val Amber = Color(0xFFB45309)
val AmberLight = Color(0xFFFFF1D6)
val Green = Color(0xFF1B7A3D)
val GreenLight = Color(0xFFDDF3E4)
val Red = Color(0xFFB42318)
val RedLight = Color(0xFFFDE4E1)
val Ink = Color(0xFF1A1F24)
val Muted = Color(0xFF5B6670)

private val colors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = TealLight,
    onPrimaryContainer = Teal,
    secondary = Color(0xFFE8963A),
    background = Color(0xFFF6F7F7),
    surface = Color.White,
    onBackground = Ink,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEDF0F0),
    onSurfaceVariant = Muted,
    error = Red,
)

// Deliberately large text: the app is meant to be easy to read for older eyes.
private val type = Typography(
    headlineMedium = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp),
    titleLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp),
    titleMedium = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.SemiBold, lineHeight = 27.sp),
    bodyLarge = TextStyle(fontSize = 20.sp, lineHeight = 27.sp),
    bodyMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun PillTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = type, content = content)
}
