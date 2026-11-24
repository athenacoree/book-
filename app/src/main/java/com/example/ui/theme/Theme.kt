package com.example.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.engine.AppThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = AmberGoldLight,
    onPrimary = DarkEspressoBg,
    primaryContainer = DarkAmberContainer,
    onPrimaryContainer = OnDarkAmberContainer,
    secondary = SageGreen,
    onSecondary = DarkIvoryText,
    secondaryContainer = OnSageSoftContainer,
    onSecondaryContainer = SageSoftContainer,
    tertiary = TerracottaAccent,
    background = DarkEspressoBg,
    onBackground = DarkIvoryText,
    surface = DarkSurface,
    onSurface = DarkIvoryText,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = DarkMutedText,
    outline = DarkOutline,
    outlineVariant = DarkOutline
)

private val LightColorScheme = lightColorScheme(
    primary = AmberGold,
    onPrimary = PaperCard,
    primaryContainer = AmberSoftContainer,
    onPrimaryContainer = OnAmberSoftContainer,
    secondary = SageGreen,
    onSecondary = PaperCard,
    secondaryContainer = SageSoftContainer,
    onSecondaryContainer = OnSageSoftContainer,
    tertiary = TerracottaAccent,
    background = PaperIvory,
    onBackground = EspressoInk,
    surface = PaperCard,
    onSurface = EspressoInk,
    surfaceVariant = PaperCream,
    onSurfaceVariant = WarmTaupe,
    outline = SubtleOutline,
    outlineVariant = SubtleOutline
)

val LuminaShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
private fun animateColor(target: Color): Color {
    val color by animateColorAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 320),
        label = "themeColor"
    )
    return color
}

@Composable
fun LuminaBookTheme(
    appThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDark = when (appThemeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    val base = if (useDark) DarkColorScheme else LightColorScheme
    val animatedScheme: ColorScheme = base.copy(
        primary = animateColor(base.primary),
        onPrimary = animateColor(base.onPrimary),
        primaryContainer = animateColor(base.primaryContainer),
        onPrimaryContainer = animateColor(base.onPrimaryContainer),
        secondary = animateColor(base.secondary),
        secondaryContainer = animateColor(base.secondaryContainer),
        onSecondaryContainer = animateColor(base.onSecondaryContainer),
        background = animateColor(base.background),
        onBackground = animateColor(base.onBackground),
        surface = animateColor(base.surface),
        onSurface = animateColor(base.onSurface),
        surfaceVariant = animateColor(base.surfaceVariant),
        onSurfaceVariant = animateColor(base.onSurfaceVariant),
        outline = animateColor(base.outline)
    )

    MaterialTheme(
        colorScheme = animatedScheme,
        typography = Typography,
        shapes = LuminaShapes,
        content = content
    )
}
