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
    // «Стандартная» и скины с тёмной парой подчиняются переключателю Темы;
    // одиночный скин заменяет схему целиком, его «светлость» определяет фон
    val themeAware = skin == null || skin.darkColors != null
    val dark = if (themeAware) {
        when (mode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    } else {
        requireNotNull(skin).colors.bg.luminance() < 0.5f
    }
    val fs = when {
        skin == null -> if (dark) DarkFsColors else LightFsColors
        skin.darkColors != null -> if (dark) skin.darkColors else skin.colors
        else -> skin.colors
    }
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
