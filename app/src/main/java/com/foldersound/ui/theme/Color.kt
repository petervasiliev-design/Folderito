package com.foldersound.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Цветовые токены, извлечённые из folder-music-player.html
 * (CSS-переменные --bg, --surf, --accent… для тёмной и светлой темы).
 */
@Immutable
data class FsColors(
    val bg: Color,
    val surf: Color,
    val surf2: Color,
    val surf3: Color,
    val outline: Color,
    val on: Color,
    val on2: Color,
    val on3: Color,
    val accent: Color,
    val accent2: Color,
    val primary: Color,
    val secondary: Color,
    val onAccent: Color,
    val folderGradStart: Color,
    val folderGradEnd: Color,
    val standardGradStart: Color,
    val standardGradEnd: Color,
    val playlistGradStart: Color,
    val playlistGradEnd: Color,
    val fabGradStart: Color,
    val coverGradStart: Color,
    val coverGradEnd: Color,
)

val DarkFsColors = FsColors(
    bg = Color(0xFF0C101D),
    surf = Color(0xFF121728),
    surf2 = Color(0xFF191F33),
    surf3 = Color(0xFF232B41),
    outline = Color(0xFF2A334C),
    on = Color(0xFFEEE6DE),
    on2 = Color(0xFFA3A8B6),
    on3 = Color(0xFF6D7386),
    accent = Color(0xFF8996A6),
    accent2 = Color(0xFF9D7B56),
    primary = Color(0xFF283C57),
    secondary = Color(0xFF9D7B56),
    onAccent = Color(0xFFF0E3DB),
    // классика (после обмена): музыкальные папки — коричневый градиент,
    // второстепенные — сине-серый
    folderGradStart = Color(0xFFA6B2C1),
    folderGradEnd = Color(0xFF8895A5),
    standardGradStart = Color(0xFFAF8D68),
    standardGradEnd = Color(0xFF896742),
    // плейлисты: тот же коричневый тон, что у музыкальных папок, чуть светлее
    playlistGradStart = Color(0xFFC2A17E),
    playlistGradEnd = Color(0xFFA28258),
    fabGradStart = Color(0xFF3E577B),
    coverGradStart = Color(0xFF8996A6),
    coverGradEnd = Color(0xFF283C57),
)

val LightFsColors = FsColors(
    bg = Color(0xFFF0E3DB),
    surf = Color(0xFFF8F1EA),
    surf2 = Color(0xFFE8D8CC),
    surf3 = Color(0xFFDAC6B5),
    outline = Color(0xFFD0BBA8),
    on = Color(0xFF050315),
    on2 = Color(0xFF5D5344),
    on3 = Color(0xFF8D7E6D),
    accent = Color(0xFF283C57),
    accent2 = Color(0xFF8996A6),
    primary = Color(0xFF283C57),
    secondary = Color(0xFF9D7B56),
    onAccent = Color(0xFFF0E3DB),
    // классика (после обмена): музыкальные папки — коричневый градиент,
    // второстепенные — сине-серый
    folderGradStart = Color(0xFFA6B2C1),
    folderGradEnd = Color(0xFF8895A5),
    standardGradStart = Color(0xFFAF8D68),
    standardGradEnd = Color(0xFF896742),
    // плейлисты: тот же коричневый тон, что у музыкальных папок, чуть светлее
    playlistGradStart = Color(0xFFC2A17E),
    playlistGradEnd = Color(0xFFA28258),
    fabGradStart = Color(0xFF3E577B),
    coverGradStart = Color(0xFF8996A6),
    coverGradEnd = Color(0xFF283C57),
)
