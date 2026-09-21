package com.alan.app.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Alan brand palette: deep indigo primary, teal secondary, amber tertiary.
 * All screens reference these (or MaterialTheme roles) — no raw grays/blacks in screens.
 */
object AlanBrand {
    // Header gradient (deep enough for white text in both light and dark mode).
    val GradientStart = Color(0xFF4338CA)
    val GradientEnd = Color(0xFF0F766E)
    val OnGradient = Color.White

    // Log terminal (intentionally dark — code/logs may stay dark).
    val TerminalBg = Color(0xFF0B1220)
    val TerminalText = Color(0xFFD7E2EE)
    val TerminalDim = Color(0xFF8FA1B8)

    // QR codes must sit on white to stay scannable.
    val QrPaper = Color.White
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Color(0xFF0D9488),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF042F2E),
    tertiary = Color(0xFFD97706),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF451A03),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF450A0A),
    background = Color(0xFFF5F6FA),
    onBackground = Color(0xFF14172B),
    surface = Color(0xFFF5F6FA),
    onSurface = Color(0xFF14172B),
    surfaceVariant = Color(0xFFE6E8F2),
    onSurfaceVariant = Color(0xFF454A65),
    outline = Color(0xFF757A9C),
    outlineVariant = Color(0xFFC9CDE6),
    scrim = Color(0xFF000000)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA5B4FC),
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF3730A3),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFF2DD4BF),
    onSecondary = Color(0xFF042F2E),
    secondaryContainer = Color(0xFF115E59),
    onSecondaryContainer = Color(0xFFCCFBF1),
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF451A03),
    tertiaryContainer = Color(0xFF92400E),
    onTertiaryContainer = Color(0xFFFEF3C7),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
    background = Color(0xFF0F1222),
    onBackground = Color(0xFFE5E8F5),
    surface = Color(0xFF0F1222),
    onSurface = Color(0xFFE5E8F5),
    surfaceVariant = Color(0xFF2A2F45),
    onSurfaceVariant = Color(0xFFBFC5DD),
    outline = Color(0xFF8E94B5),
    outlineVariant = Color(0xFF434861)
)

@Composable
fun AlanTheme(
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
