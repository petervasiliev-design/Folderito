package com.foldersound.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.luminance

val LocalFsColors = staticCompositionLocalOf { DarkFsColors }

/** Режим темы: система, светлая или тёмная (как переключатель в макете). */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun FsTheme(
    mode: ThemeMode,
    skinId: String = Skins.CLASSIC_ID,
    content: @Composable () -> Unit,
) {
    val skin = Skins.byId(skinId)
    // выбранный скин заменяет схему целиком; его «светлость» определяет фон
    val dark = skin?.let { it.colors.bg.luminance() < 0.5f }
        ?: when (mode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    val fs = skin?.colors ?: if (dark) DarkFsColors else LightFsColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = fs.primary,
            onPrimary = fs.onAccent,
            secondary = fs.secondary,
            onSecondary = fs.onAccent,
            background = fs.bg,
            onBackground = fs.on,
            surface = fs.surf,
            onSurface = fs.on,
            surfaceVariant = fs.surf2,
            onSurfaceVariant = fs.on2,
            outline = fs.outline,
        )
    } else {
        lightColorScheme(
            primary = fs.primary,
            onPrimary = fs.onAccent,
            secondary = fs.secondary,
            onSecondary = fs.onAccent,
            background = fs.bg,
            onBackground = fs.on,
            surface = fs.surf,
            onSurface = fs.on,
            surfaceVariant = fs.surf2,
            onSurfaceVariant = fs.on2,
            outline = fs.outline,
        )
    }
    CompositionLocalProvider(LocalFsColors provides fs) {
        MaterialTheme(
            colorScheme = scheme,
            typography = FsTypography,
            content = content,
        )
    }
}

object Fs {
    val c: FsColors
        @Composable get() = LocalFsColors.current
}
