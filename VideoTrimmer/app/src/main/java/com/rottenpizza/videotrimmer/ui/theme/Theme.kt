package com.rottenpizza.videotrimmer.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val Purple = Color(0xFF7C4DFF)
private val PurpleDim = Color(0xFF5E35B1)
private val Teal = Color(0xFF00E5C3)

private val DarkColors = darkColorScheme(
    primary = Purple,
    onPrimary = Color.White,
    primaryContainer = PurpleDim,
    onPrimaryContainer = Color.White,
    secondary = Teal,
    onSecondary = Color(0xFF00201B),
    background = Color(0xFF0E0E12),
    onBackground = Color(0xFFECECF0),
    surface = Color(0xFF16161C),
    onSurface = Color(0xFFECECF0),
    surfaceVariant = Color(0xFF25252E),
    onSurfaceVariant = Color(0xFFB9B9C4),
    outline = Color(0xFF3A3A45),
    error = Color(0xFFFF6B6B),
)

private val LightColors = lightColorScheme(
    primary = PurpleDim,
    secondary = Color(0xFF00897B),
)

@Composable
fun VideoTrimmerTheme(
    // Dark by default, per the app's design.
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        content = content,
    )
}
