package com.koval.trainingplanner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val KovalColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = Background,
    primaryContainer = PrimaryDark,
    secondary = Primary,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = Border,
    outlineVariant = BorderStrong,
    error = Danger,
    onError = TextPrimary,
)

@Composable
fun KovalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KovalColorScheme,
        typography = KovalTypography,
        shapes = KovalShapes,
        content = content,
    )
}
