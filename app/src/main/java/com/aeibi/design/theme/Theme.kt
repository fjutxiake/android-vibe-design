package com.aeibi.design.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
    darkColorScheme(
        primary = MossLight,
        onPrimary = OnMossDark,
        primaryContainer = MossContainerDark,
        onPrimaryContainer = OnMossContainerDark,
        inversePrimary = Moss,
        secondary = SageLight,
        onSecondary = OnSageDark,
        secondaryContainer = SageContainerDark,
        onSecondaryContainer = SageContainer,
        tertiary = OliveLight,
        onTertiary = OnOliveDark,
        tertiaryContainer = OliveContainerDark,
        onTertiaryContainer = OliveContainer,
        background = DarkBackground,
        onBackground = DarkOnSurface,
        surface = DarkSurface,
        onSurface = DarkOnSurface,
        surfaceVariant = DarkSurfaceVariant,
        onSurfaceVariant = DarkOnSurfaceVariant,
        surfaceTint = MossLight,
        inverseSurface = DarkOnSurface,
        inverseOnSurface = DarkSurfaceVariant,
        error = ErrorLight,
        onError = OnErrorDark,
        errorContainer = ErrorContainerDark,
        onErrorContainer = ErrorContainer,
        outline = DarkOutline,
        outlineVariant = DarkOutlineVariant,
        scrim = ThemeScrim,
        surfaceBright = DarkSurfaceBright,
        surfaceContainer = DarkSurface,
        surfaceContainerHigh = DarkSurfaceContainerHigh,
        surfaceContainerHighest = DarkSurfaceContainerHighest,
        surfaceContainerLow = DarkSurfaceContainerLow,
        surfaceContainerLowest = DarkSurfaceContainerLowest,
        surfaceDim = DarkBackground,
        primaryFixed = MossContainer,
        primaryFixedDim = MossFixedDim,
        onPrimaryFixed = OnMossContainer,
        onPrimaryFixedVariant = OnMossFixedVariant,
        secondaryFixed = SageContainer,
        secondaryFixedDim = SageFixedDim,
        onSecondaryFixed = OnSageContainer,
        onSecondaryFixedVariant = OnSageFixedVariant,
        tertiaryFixed = OliveContainer,
        tertiaryFixedDim = OliveFixedDim,
        onTertiaryFixed = OnOliveContainer,
        onTertiaryFixedVariant = OnOliveFixedVariant
    )

private val LightColorScheme =
    lightColorScheme(
        primary = Moss,
        onPrimary = OnMoss,
        primaryContainer = MossContainer,
        onPrimaryContainer = OnMossContainer,
        inversePrimary = MossLight,
        secondary = Sage,
        onSecondary = OnMoss,
        secondaryContainer = SageContainer,
        onSecondaryContainer = OnSageContainer,
        tertiary = Olive,
        onTertiary = OnMoss,
        tertiaryContainer = OliveContainer,
        onTertiaryContainer = OnOliveContainer,
        background = LightBackground,
        onBackground = LightOnSurface,
        surface = LightSurface,
        onSurface = LightOnSurface,
        surfaceVariant = LightSurfaceVariant,
        onSurfaceVariant = LightOnSurfaceVariant,
        surfaceTint = Moss,
        inverseSurface = DarkSurfaceVariant,
        inverseOnSurface = DarkOnSurface,
        error = Error,
        onError = OnMoss,
        errorContainer = ErrorContainer,
        onErrorContainer = OnErrorContainer,
        outline = LightOutline,
        outlineVariant = LightOutlineVariant,
        scrim = ThemeScrim,
        surfaceBright = LightSurface,
        surfaceContainer = LightSurfaceContainer,
        surfaceContainerHigh = LightSurfaceContainerHigh,
        surfaceContainerHighest = LightSurfaceContainerHighest,
        surfaceContainerLow = LightSurfaceContainerLow,
        surfaceContainerLowest = LightSurfaceContainerLowest,
        surfaceDim = LightSurfaceDim,
        primaryFixed = MossContainer,
        primaryFixedDim = MossFixedDim,
        onPrimaryFixed = OnMossContainer,
        onPrimaryFixedVariant = OnMossFixedVariant,
        secondaryFixed = SageContainer,
        secondaryFixedDim = SageFixedDim,
        onSecondaryFixed = OnSageContainer,
        onSecondaryFixedVariant = OnSageFixedVariant,
        tertiaryFixed = OliveContainer,
        tertiaryFixedDim = OliveFixedDim,
        onTertiaryFixed = OnOliveContainer,
        onTertiaryFixedVariant = OnOliveFixedVariant
    )

@Composable
fun VibeDesignTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalDimensions provides Dimensions()
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
}
