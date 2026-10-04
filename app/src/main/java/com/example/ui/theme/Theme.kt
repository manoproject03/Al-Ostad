package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.example.domain.LocalSettings

val LocalTeacherPalette = staticCompositionLocalOf {
    ContrastUtils.buildPalette("rose", null)
}

@Composable
fun TeacherAppTheme(
    settings: LocalSettings = LocalSettings(),
    content: @Composable () -> Unit
) {
    val palette = ContrastUtils.buildPalette(settings.theme, settings.customPrimaryHex)
    val isLarge = settings.fontSize == "large"
    val typography = buildTeacherTypography(isLarge)

    val colorScheme = if (palette.isDark) {
        darkColorScheme(
            primary = palette.primary,
            onPrimary = palette.onPrimary,
            primaryContainer = palette.muted,
            onPrimaryContainer = palette.text,
            secondary = palette.goldAccent,
            onSecondary = palette.bg,
            secondaryContainer = palette.raised,
            onSecondaryContainer = palette.text,
            background = palette.bg,
            onBackground = palette.text,
            surface = palette.surface,
            onSurface = palette.text,
            surfaceVariant = palette.raised,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.border,
            error = palette.error
        )
    } else {
        lightColorScheme(
            primary = palette.primary,
            onPrimary = palette.onPrimary,
            primaryContainer = palette.muted,
            onPrimaryContainer = palette.text,
            secondary = palette.goldAccent,
            onSecondary = palette.onPrimary,
            secondaryContainer = palette.raised,
            onSecondaryContainer = palette.text,
            background = palette.bg,
            onBackground = palette.text,
            surface = palette.surface,
            onSurface = palette.text,
            surfaceVariant = palette.raised,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.border,
            error = palette.error
        )
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalTeacherPalette provides palette
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    TeacherAppTheme(
        settings = LocalSettings(theme = if (darkTheme) "dark" else "rose"),
        content = content
    )
}
