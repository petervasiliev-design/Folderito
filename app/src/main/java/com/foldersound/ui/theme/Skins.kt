package com.foldersound.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * Скины (цветовые схемы). Каждый скин — полный набор токенов FsColors.
 *
 * Формат добавления (короткий, от пользователя):
 *   Text / Background / Primary / Secondary / Accent —
 *   остальные поверхности, контуры и градиенты выводятся автоматически [skinFromShort].
 */
data class Skin(
    val id: String,
    val nameRu: String,
    val nameEn: String,
    /** Основная (светлая) таблица. */
    val colors: FsColors,
    /** Тёмная пара: если задана, переключатель «Тема» работает и для скина. */
    val darkColors: FsColors? = null,
)

/** Затемнение/осветление для вывода градиентов. */
private fun darken(c: Color, f: Float) = lerp(c, Color.Black, f)
private fun lighten(c: Color, f: Float) = lerp(c, Color.White, f)

/** Короткий формат: text/background/primary/secondary/accent → полная таблица. */
fun skinFromShort(
    id: String,
    nameRu: String,
    nameEn: String,
    text: Long,
    background: Long,
    primary: Long,
    secondary: Long,
    accent: Long,
): Skin {
    val t = Color(text)
    val bg = Color(background)
    fun mixBg(f: Float) = lerp(bg, t, f)
    val a = Color(accent)
    val p = Color(primary)
    val sec = Color(secondary)
    return Skin(
        id = id,
        nameRu = nameRu,
        nameEn = nameEn,
        colors = FsColors(
            bg = bg,
            surf = mixBg(0.04f),
            surf2 = mixBg(0.08f),
            surf3 = mixBg(0.16f),
            outline = mixBg(0.24f),
            on = t,
            on2 = t.copy(alpha = 0.70f),
            on3 = t.copy(alpha = 0.45f),
            accent = a,
            accent2 = sec,
            primary = p,
            secondary = sec,
            onAccent = if (p.luminance() > 0.5f) Color(0xFF101018) else Color(0xFFFCFCF8),
            folderGradStart = lighten(sec, 0.12f),
            folderGradEnd = darken(sec, 0.28f),
            standardGradStart = lighten(a, 0.14f),
            standardGradEnd = a,
            // плейлисты: чуть светлее музыкальных папок (standardGrad)
            playlistGradStart = lighten(a, 0.24f),
            playlistGradEnd = lighten(a, 0.10f),
            fabGradStart = lighten(p, 0.16f),
            coverGradStart = lighten(a, 0.22f),
            coverGradEnd = darken(a, 0.38f),
        ),
    )
}

/**
 * Каталог скинов. CLASSIC_ID — исходная схема приложения
 * (светлая/тёмная по переключателю темы); остальные применяются как есть.
 */
object Skins {
    const val CLASSIC_ID = "classic"

    private val steel = skinFromShort(
        id = "steel",
        nameRu = "Сталь",
        nameEn = "Steel",
        text = 0xFFFFFFFF,
        background = 0xFF5E6E88,
        primary = 0xFF2D3E52,
        secondary = 0xFF41516A,
        accent = 0xFF1C2739,
    )

    private val midnight = skinFromShort(
        id = "steel-dark",
        nameRu = "Полночь",
        nameEn = "Midnight",
        text = 0xFFFFFFFF,
        background = 0xFF1C2739,
        primary = 0xFF41516A,
        secondary = 0xFF2D3E52,
        accent = 0xFF5E6E88,
    )

    val all: List<Skin> = listOf(
        // «Янтарь»: белый фон, янтарная primary, тёмно-синий акцент (формат пользователя;
        // раньше называлась «Своя»).
        // Text не был задан — взят равным акценту #232F3E.
        // Обычные папки заданы вручную — темнее автоправила от Secondary;
        // стандартные папки (music…) остаются на автоправиле от Accent.
        skinFromShort(
            id = "custom1",
            nameRu = "Янтарь",
            nameEn = "Amber",
            text = 0xFF232F3E,
            background = 0xFFFFFFFF,
            primary = 0xFFFFD814,
            secondary = 0xFFE3E6E6,
            accent = 0xFF232F3E,
        ).let { s ->
            s.copy(
                colors = s.colors.copy(
                    folderGradStart = Color(0xFF9FA1A1),
                    folderGradEnd = Color(0xFF666768),
                ),
            )
        },

        // «Туман»: кремовый фон, стальной primary, голубой акцент (формат пользователя).
        skinFromShort(
            id = "mist",
            nameRu = "Туман",
            nameEn = "Mist",
            text = 0xFF161A1E,
            background = 0xFFF4EFEB,
            primary = 0xFF2E4056,
            secondary = 0xFF637C8D,
            accent = 0xFF4C8BAD,
        ),

        // «Олива»: песочный фон, оливковый primary, терракотовый акцент (формат пользователя).
        // Оба градиента папок заданы вручную: стандартные затемнены на 10%,
        // у обычных градиент сжат к тёмному краю и затемнён на 10%.
        skinFromShort(
            id = "olive",
            nameRu = "Олива",
            nameEn = "Olive",
            text = 0xFF161A1E,
            background = 0xFFF6F2E5,
            primary = 0xFF959C7A,
            secondary = 0xFFB9BA97,
            accent = 0xFFD39D73,
        ).let { s ->
            s.copy(
                colors = s.colors.copy(
                    standardGradStart = Color(0xFFC3997A),
                    standardGradEnd = Color(0xFFBE8D68),
                    folderGradStart = Color(0xFF9E9E84),
                    folderGradEnd = Color(0xFF787962),
                ),
            )
        },

        // «Сталь» + её тёмная пара «Полночь»: та же палитра, фон и акцент поменяны местами.
        steel.copy(darkColors = midnight.colors),
    )

    fun byId(id: String?): Skin? = all.firstOrNull { it.id == id }
}
