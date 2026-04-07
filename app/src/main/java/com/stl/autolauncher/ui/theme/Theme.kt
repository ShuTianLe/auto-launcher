package com.stl.autolauncher.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Color(0xFF145B52),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDEEE6),
    onPrimaryContainer = Color(0xFF0A2E29),
    secondary = Color(0xFF456A63),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD3E8E2),
    onSecondaryContainer = Color(0xFF183631),
    tertiary = Color(0xFF87613A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDBD),
    onTertiaryContainer = Color(0xFF321C05),
    background = Color(0xFFF3F1EC),
    onBackground = Color(0xFF161D1B),
    surface = Color(0xFFFFFBF7),
    onSurface = Color(0xFF161D1B),
    surfaceVariant = Color(0xFFDCE5E1),
    onSurfaceVariant = Color(0xFF3F4946),
    outline = Color(0xFF6F7975),
    outlineVariant = Color(0xFFBEC9C4),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF79D7C4),
    onPrimary = Color(0xFF003730),
    primaryContainer = Color(0xFF004F46),
    onPrimaryContainer = Color(0xFFCDEEE6),
    secondary = Color(0xFFB7CCC6),
    onSecondary = Color(0xFF213B36),
    secondaryContainer = Color(0xFF37504A),
    onSecondaryContainer = Color(0xFFD3E8E2),
    tertiary = Color(0xFFE7C099),
    onTertiary = Color(0xFF4C2F0E),
    tertiaryContainer = Color(0xFF65471F),
    onTertiaryContainer = Color(0xFFFFDDBD),
    background = Color(0xFF0F1513),
    onBackground = Color(0xFFE2E3DE),
    surface = Color(0xFF141B19),
    onSurface = Color(0xFFE2E3DE),
    surfaceVariant = Color(0xFF3F4946),
    onSurfaceVariant = Color(0xFFBEC9C4),
    outline = Color(0xFF89938F),
    outlineVariant = Color(0xFF3F4946),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun AutoLauncherTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current

    SideEffect {
        if (!view.isInEditMode) {
            view.context.findActivity()?.window?.let { window ->
                val useDarkIcons = colorScheme.background.luminance() > 0.5f
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = useDarkIcons
                    isAppearanceLightNavigationBars = useDarkIcons
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
