package com.brianrobinson.fonds.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Reading-room palette: paper, ink, archival-box slate, manila folders, approval green.

private val LightScheme = lightColorScheme(
    primary = Color(0xFF2E4A5A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E2EB),
    onPrimaryContainer = Color(0xFF0D2633),
    inversePrimary = Color(0xFFA9C8DA),
    secondary = Color(0xFF7D6128),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF1E2BE),
    onSecondaryContainer = Color(0xFF2B2006),
    tertiary = Color(0xFF3E6A4A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD2E8D5),
    onTertiaryContainer = Color(0xFF0E2615),
    background = Color(0xFFF5F1E8),
    onBackground = Color(0xFF1F1C17),
    surface = Color(0xFFF8F4EB),
    onSurface = Color(0xFF1F1C17),
    surfaceVariant = Color(0xFFE8E1D2),
    onSurfaceVariant = Color(0xFF4C473D),
    surfaceTint = Color(0xFF2E4A5A),
    inverseSurface = Color(0xFF34302A),
    inverseOnSurface = Color(0xFFF7F0E4),
    error = Color(0xFFA63A2B),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF8DAD3),
    onErrorContainer = Color(0xFF3F0B03),
    outline = Color(0xFF7D766A),
    outlineVariant = Color(0xFFD3CBBB),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFDFAF3),
    surfaceDim = Color(0xFFE2DCD0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFCF8F0),
    surfaceContainer = Color(0xFFF3EEE3),
    surfaceContainerHigh = Color(0xFFEDE7DB),
    surfaceContainerHighest = Color(0xFFE7E1D4),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFA9C8DA),
    onPrimary = Color(0xFF10303F),
    primaryContainer = Color(0xFF284352),
    onPrimaryContainer = Color(0xFFD3E2EB),
    inversePrimary = Color(0xFF2E4A5A),
    secondary = Color(0xFFE2C88E),
    onSecondary = Color(0xFF3F2E05),
    secondaryContainer = Color(0xFF574416),
    onSecondaryContainer = Color(0xFFF6E8C6),
    tertiary = Color(0xFFA8D2B0),
    onTertiary = Color(0xFF14361F),
    tertiaryContainer = Color(0xFF2B4D33),
    onTertiaryContainer = Color(0xFFD2E8D5),
    background = Color(0xFF15130F),
    onBackground = Color(0xFFECE6DA),
    surface = Color(0xFF18160F),
    onSurface = Color(0xFFECE6DA),
    surfaceVariant = Color(0xFF4A453C),
    onSurfaceVariant = Color(0xFFCFC6B5),
    surfaceTint = Color(0xFFA9C8DA),
    inverseSurface = Color(0xFFECE6DA),
    inverseOnSurface = Color(0xFF34302A),
    error = Color(0xFFFFB4A6),
    onError = Color(0xFF5F1509),
    errorContainer = Color(0xFF842819),
    onErrorContainer = Color(0xFFF8DAD3),
    outline = Color(0xFF988F80),
    outlineVariant = Color(0xFF4A453C),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF3D3a33),
    surfaceDim = Color(0xFF15130F),
    surfaceContainerLowest = Color(0xFF100E0A),
    surfaceContainerLow = Color(0xFF1E1B16),
    surfaceContainer = Color(0xFF221F1A),
    surfaceContainerHigh = Color(0xFF2D2A24),
    surfaceContainerHighest = Color(0xFF38342E),
)

/** Colors Material doesn't have names for. */
@Immutable
data class FondsColors(
    val manila: Color,
    val onManila: Color,
    val manilaEdge: Color,
    val draft: Color,
    val onDraft: Color,
    val approved: Color,
    val busy: Color,
    val labelPaper: Color,
    val collectionBands: List<Color>,
)

private val LightExtra = FondsColors(
    manila = Color(0xFFF1E2BE),
    onManila = Color(0xFF3A2C0C),
    manilaEdge = Color(0xFFD8C08A),
    draft = Color(0xFFFBF1D9),
    onDraft = Color(0xFF5A4413),
    approved = Color(0xFF3E6A4A),
    busy = Color(0xFF2E4A5A),
    labelPaper = Color(0xFFFFFDF7),
    collectionBands = listOf(
        Color(0xFF2E4A5A), Color(0xFF7A3B2E), Color(0xFF5A6B3A),
        Color(0xFF8A6A2F), Color(0xFF2F6466), Color(0xFF5E4466),
    ),
)

private val DarkExtra = FondsColors(
    manila = Color(0xFF4A3A16),
    onManila = Color(0xFFF6E8C6),
    manilaEdge = Color(0xFF7A6430),
    draft = Color(0xFF3A2F17),
    onDraft = Color(0xFFF1DDB0),
    approved = Color(0xFFA8D2B0),
    busy = Color(0xFFA9C8DA),
    labelPaper = Color(0xFF2A2721),
    collectionBands = listOf(
        Color(0xFF7FA6BC), Color(0xFFC98A7C), Color(0xFFA9BC84),
        Color(0xFFD9B979), Color(0xFF7FB7B8), Color(0xFFB79BC0),
    ),
)

val LocalFondsColors = staticCompositionLocalOf { LightExtra }

object Fonds {
    val colors: FondsColors
        @Composable get() = LocalFondsColors.current
}

val Serif = FontFamily.Serif
val Mono = FontFamily.Monospace

private val base = Typography()

private val FondsTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Serif),
    displayMedium = base.displayMedium.copy(fontFamily = Serif),
    displaySmall = base.displaySmall.copy(fontFamily = Serif),
    headlineLarge = base.headlineLarge.copy(fontFamily = Serif),
    headlineMedium = base.headlineMedium.copy(fontFamily = Serif),
    headlineSmall = base.headlineSmall.copy(fontFamily = Serif),
    titleLarge = base.titleLarge.copy(fontFamily = Serif, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontFamily = Serif, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall,
    bodyLarge = base.bodyLarge,
    bodyMedium = base.bodyMedium,
    bodySmall = base.bodySmall,
    labelLarge = base.labelLarge,
    labelMedium = base.labelMedium,
    labelSmall = base.labelSmall,
)

/** Monospaced "label maker" styles for box and folder numbers. */
object LabelType {
    val huge = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 44.sp, letterSpacing = (-1).sp)
    val big = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 22.sp)
    val small = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.5.sp)
    val caps = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.4.sp)
}

private val FondsShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(22.dp),
)

@Composable
fun FondsTheme(themeMode: String = "system", dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val scheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkScheme
        else -> LightScheme
    }
    CompositionLocalProvider(LocalFondsColors provides if (dark) DarkExtra else LightExtra) {
        MaterialTheme(colorScheme = scheme, typography = FondsTypography, shapes = FondsShapes, content = content)
    }
}
