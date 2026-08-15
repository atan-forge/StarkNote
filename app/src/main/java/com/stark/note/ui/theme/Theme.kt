package com.stark.note.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val OLEDColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    primaryContainer = AccentMuted,
    onPrimaryContainer = OnSurfacePrimary,
    background = Background,
    surface = Surface,
    surfaceContainer = Surface,
    surfaceContainerHigh = SurfaceElevated,
    onBackground = OnSurfacePrimary,
    onSurface = OnSurfacePrimary,
    secondary = OnSurfaceSecondary,
    onSecondary = Background,
    secondaryContainer = SurfaceSelected,
    onSecondaryContainer = OnSurfacePrimary,
    tertiary = Locked,
    error = Destructive,
    errorContainer = DestructiveMuted,
    onErrorContainer = OnSurfacePrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = OnSurfaceSecondary,
    outline = Divider
)

private val StarkShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun StarkNoteTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = OLEDColorScheme,
        typography = Typography,
        shapes = StarkShapes,
        content = content
    )
}
