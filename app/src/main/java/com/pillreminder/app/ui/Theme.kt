package com.pillreminder.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Warm & calm palette: cream, terracotta and sage.
val Cream = Color(0xFFFBF4E9)
val CardWhite = Color(0xFFFFFCF6)
val Primary = Color(0xFFC0613F)       // terracotta
val PrimaryDeep = Color(0xFF9E4A2E)
val PrimarySoft = Color(0xFFF6E0D3)
val Sage = Color(0xFF5E7F61)
val SageLight = Color(0xFFE2EBDC)
val Honey = Color(0xFF9A6412)
val HoneyLight = Color(0xFFFBEACB)
val Rose = Color(0xFFB0433A)
val RoseLight = Color(0xFFF6DCD5)
val Ink = Color(0xFF3B2E27)
val Muted = Color(0xFF7D6C61)
val Line = Color(0xFFEADCCB)

private val colors = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    primaryContainer = PrimarySoft,
    onPrimaryContainer = PrimaryDeep,
    secondary = Sage,
    onSecondary = Color.White,
    background = Cream,
    surface = CardWhite,
    onBackground = Ink,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF3E8DA),
    onSurfaceVariant = Muted,
    outline = Line,
    error = Rose,
)

private val Heading = FontFamily.Serif

// Large text on purpose (easy to read for older eyes). Serif headings give it a warm, homely feel.
private val type = Typography(
    displaySmall = TextStyle(fontFamily = Heading, fontSize = 38.sp, fontWeight = FontWeight.Bold, lineHeight = 44.sp),
    headlineMedium = TextStyle(fontFamily = Heading, fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = Heading, fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = Heading, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp),
    titleMedium = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.SemiBold, lineHeight = 27.sp),
    bodyLarge = TextStyle(fontSize = 20.sp, lineHeight = 27.sp),
    bodyMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun PillTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = type, content = content)
}
