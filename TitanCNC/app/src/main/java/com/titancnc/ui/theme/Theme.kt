package com.titancnc.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = CyanAccent,
    onPrimary = DarkBackground,
    primaryContainer = CyanAccent.copy(alpha = 0.2f),
    onPrimaryContainer = CyanAccent,
    
    secondary = PurpleAccent,
    onSecondary = DarkBackground,
    secondaryContainer = PurpleAccent.copy(alpha = 0.2f),
    onSecondaryContainer = PurpleAccent,
    
    tertiary = SuccessGreen,
    onTertiary = DarkBackground,
    tertiaryContainer = SuccessGreen.copy(alpha = 0.2f),
    onTertiaryContainer = SuccessGreen,
    
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorRed.copy(alpha = 0.2f),
    onErrorContainer = ErrorRed,
    
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = CardBackground,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceBackground,
    onSurfaceVariant = TextSecondary,
    
    outline = BorderColor,
    outlineVariant = BorderColor.copy(alpha = 0.5f),
    
    scrim = Color.Black.copy(alpha = 0.5f),
    
    inverseSurface = TextPrimary,
    inverseOnSurface = DarkBackground,
    inversePrimary = CyanAccentDark
)

private val LightColorScheme = lightColorScheme(
    primary = CyanAccentDark,
    onPrimary = Color.White,
    primaryContainer = CyanAccent.copy(alpha = 0.15f),
    onPrimaryContainer = CyanAccentDark,
    
    secondary = PurpleAccentDark,
    onSecondary = Color.White,
    secondaryContainer = PurpleAccent.copy(alpha = 0.15f),
    onSecondaryContainer = PurpleAccentDark,
    
    tertiary = SuccessGreen,
    onTertiary = Color.White,
    tertiaryContainer = SuccessGreen.copy(alpha = 0.15f),
    onTertiaryContainer = SuccessGreen,
    
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorRed.copy(alpha = 0.15f),
    onErrorContainer = ErrorRed,
    
    background = Color(0xFFF5F5F5),
    onBackground = Color(0xFF121212),
    surface = Color.White,
    onSurface = Color(0xFF121212),
    surfaceVariant = Color(0xFFEEEEEE),
    onSurfaceVariant = Color(0xFF666666),
    
    outline = Color(0xFFDDDDDD),
    outlineVariant = Color(0xFFEEEEEE),
    
    scrim = Color.Black.copy(alpha = 0.3f),
    
    inverseSurface = Color(0xFF121212),
    inverseOnSurface = Color.White,
    inversePrimary = CyanAccent
)

@Composable
fun TitanCNCTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disabled for consistent industrial look
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
