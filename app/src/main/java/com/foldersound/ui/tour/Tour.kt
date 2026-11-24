package com.foldersound.ui.tour

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.foldersound.ui.icons.FsIcons
import com.foldersound.ui.i18n.LocalStrings
import com.foldersound.ui.theme.Fs

/* ==================== реестр целей ==================== */

/** Прямоугольники целей тура в координатах окна: ключ → rect. */
object TourTargets {
    val bounds = mutableStateMapOf<String, Rect>()
}

/**
 * Регистрирует элемент целью тура. Прямоугольник перечитывается на каждой
 * раскладке (поворот, анимации), при уходе элемента из композиции цель снимается.
 */
fun Modifier.tourTarget(key: String): Modifier = composed {
    DisposableEffect(key) {
        onDispose { TourTargets.bounds.remove(key) }
    }
    onGloballyPositioned { TourTargets.bounds[key] = it.boundsInWindow() }
}

/* ==================== состояние тура ==================== */

/** Шаг тура. [targetKey] = null — пузырь по центру, без прожектора.
 *  В [text] можно ставить маркеры в квадратных скобках:
 *  пиктограммы [папка] [галочка] [звезда] [трек] [кн-меню] [кн-поиск] [кн-свернуть] [кн-очистить] [кн-плеер]
 *  и перенос строки [новая строка] — текст после него выводится курсивом (примечание).
 *  Неизвестные маркеры остаются обычным текстом. */
data class TourStep(
    val targetKey: String? = null,
    val title: String,
    val text: String,
)

/* ==================== inline-пиктограммы в тексте ==================== */

internal const val TOK_FOLDER = "folder"
internal const val TOK_CHECK = "check"
internal const val TOK_STAR = "star"
internal const val TOK_SONG = "song"
internal const val TOK_BULB = "bulb"
internal const val TOK_NL = "nl"
internal const val TOK_BTN_MENU = "btn-menu"
internal const val TOK_BTN_SEARCH = "btn-search"
internal const val TOK_BTN_COLLAPSE = "btn-collapse"
internal const val TOK_BTN_CLEAR = "btn-clear"
internal const val TOK_BTN_PLAYER = "btn-player"

/** Синонимы маркеров: RU (основные) + EN/DE. */
private val TourPictAliases = mapOf(
    "папка" to TOK_FOLDER, "folder" to TOK_FOLDER, "ordner" to TOK_FOLDER,
    "галочка" to TOK_CHECK, "check" to TOK_CHECK, "haken" to TOK_CHECK,
    "звезда" to TOK_STAR, "star" to TOK_STAR, "stern" to TOK_STAR,
    "трек" to TOK_SONG, "song" to TOK_SONG, "titel" to TOK_SONG,
    "лампочка" to TOK_BULB, "bulb" to TOK_BULB, "lampe" to TOK_BULB,
    "новая строка" to TOK_NL, "new line" to TOK_NL, "neue zeile" to TOK_NL,
    "кн-меню" to TOK_BTN_MENU, "btn-menu" to TOK_BTN_MENU,
    "кн-поиск" to TOK_BTN_SEARCH, "btn-search" to TOK_BTN_SEARCH,
    "кн-свернуть" to TOK_BTN_COLLAPSE, "btn-collapse" to TOK_BTN_COLLAPSE,
    "кн-очистить" to TOK_BTN_CLEAR, "btn-clear" to TOK_BTN_CLEAR,
    "кн-плеер" to TOK_BTN_PLAYER, "btn-player" to TOK_BTN_PLAYER,
)

private val TourPictRegex = Regex("""\[([A-Za-zА-Яа-яЁё -]+)]""")

/** Кнопки шапки: маркер → иконка. */
private val TourBtnVectors = mapOf(
    TOK_BTN_MENU to Icons.Filled.Menu,
    TOK_BTN_SEARCH to Icons.Filled.Search,
    TOK_BTN_COLLAPSE to Icons.Filled.UnfoldLess,
    TOK_BTN_CLEAR to Icons.Filled.Cancel,
    TOK_BTN_PLAYER to Icons.AutoMirrored.Filled.ArrowForward,
)

/** Текст шага с маркерами → AnnotatedString: inline-слоты, переносы, курсив после переноса. */
fun tourAnnotated(text: String): AnnotatedString = buildAnnotatedString {
    var last = 0
    var italicStart = -1
    TourPictRegex.findAll(text).forEach { m ->
        val raw = m.groupValues[1].lowercase()
        if (raw == TOK_NL || TourPictAliases[raw] == TOK_NL) {
            append(text.substring(last, m.range.first))
            if (italicStart >= 0) {
                addStyle(SpanStyle(fontStyle = FontStyle.Italic), italicStart, length)
                italicStart = -1
            }
            append('\n')
            italicStart = length // всё после переноса — примечание курсивом
            last = m.range.last + 1
        } else {
            val token = TourPictAliases[raw] ?: return@forEach
            append(text.substring(last, m.range.first))
            if (italicStart >= 0) {
                addStyle(SpanStyle(fontStyle = FontStyle.Italic), italicStart, length)
                italicStart = length
            }
            appendInlineContent(token, "▪")
            last = m.range.last + 1
        }
    }
    append(text.substring(last))
    if (italicStart >= 0) {
        addStyle(SpanStyle(fontStyle = FontStyle.Italic), italicStart, length)
    }
}

/** Карта inline-содержимого для всех известных маркеров-пиктограмм. */
fun tourInlineContent(): Map<String, InlineTextContent> {
    val map = mutableMapOf<String, InlineTextContent>()
    TourPictAliases.values.filter { it != TOK_NL }.distinct().forEach { token ->
        map[token] = InlineTextContent(
            Placeholder(22.sp, 22.sp, PlaceholderVerticalAlign.TextCenter),
        ) { TourInlineView(token) }
    }
    return map
}

@Composable
private fun TourInlineView(token: String) {
    val fs = Fs.c
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (token) {
            TOK_FOLDER -> Icon(FsIcons.Folder, null, tint = fs.on2, modifier = Modifier.size(20.dp))
            TOK_STAR -> Icon(Icons.Filled.Star, null, tint = fs.accent, modifier = Modifier.size(20.dp))
            TOK_SONG -> Icon(FsIcons.SoundWave, null, tint = fs.on2, modifier = Modifier.size(20.dp))
            TOK_BULB -> Icon(Icons.Filled.Lightbulb, null, tint = fs.accent, modifier = Modifier.size(20.dp))
            TOK_CHECK -> Box(
                Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .border(2.dp, fs.on2, RoundedCornerShape(5.dp)),
            )
            else -> TourBtnVectors[token]?.let { vector ->
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(fs.fabGradStart, fs.primary))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(vector, null, tint = fs.onAccent, modifier = Modifier.size(13.dp))
                }
            }
        }
    }
}

/** Состояние обхода шагов: старт из шторки, вперёд-назад, досрочное завершение. */
class TourState {
    var active by mutableStateOf(false)
        private set
    var index by mutableIntStateOf(0)
        private set
    var steps by mutableStateOf(emptyList<TourStep>())
        private set

    fun start(steps: List<TourStep>) {
        this.steps = steps
        index = 0
        active = true
    }

    fun stop() {
        active = false
    }

    fun next() {
        if (index >= steps.size - 1) stop() else index++
    }

    fun back() {
        if (index > 0) index--
    }
}

/* ==================== оверлей ==================== */

/**
 * Полноэкранный тур поверх живого UI: затемнение с «прожектором» вокруг цели,
 * пузырь с текстом и кнопками «Назад / Дальше». Тапы мимо пузыря глотаются —
 * во время тура приложение не реагирует на прочие нажатия.
 */
@Composable
fun TourOverlay(state: TourState, modifier: Modifier = Modifier) {
    if (!state.active) return
    val fs = Fs.c
    val s = LocalStrings.current
    val steps = state.steps
    val step = steps.getOrNull(state.index) ?: return
    val rect = step.targetKey?.let { TourTargets.bounds[it] }

    Box(modifier.fillMaxSize().zIndex(20f)) {
        // затемнение с вырезом; слой offscreen обязателен — иначе BlendMode.Clear
        // не пробьёт дырку в затемнении
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            drawRect(Color.Black.copy(alpha = 0.62f))
            if (rect != null) {
                val pad = 9.dp.toPx()
                val hole = Rect(
                    rect.left - pad, rect.top - pad,
                    rect.right + pad, rect.bottom + pad,
                )
                val corner = CornerRadius(19.dp.toPx(), 19.dp.toPx())
                drawRoundRect(
                    color = Color.Black,
                    topLeft = Offset(hole.left, hole.top),
                    size = Size(hole.width, hole.height),
                    cornerRadius = corner,
                    blendMode = BlendMode.Clear,
                )
                drawRoundRect(
                    color = fs.accent,
                    topLeft = Offset(hole.left, hole.top),
                    size = Size(hole.width, hole.height),
                    cornerRadius = corner,
                    style = Stroke(width = 2.5.dp.toPx()),
                )
            }
        }

        // пузырь: цель в нижней половине экрана → пузырь сверху, иначе снизу;
        // шаг без цели — пузырь по центру
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val bottomHalf = rect != null &&
                with(LocalDensity.current) { rect.center.y > maxHeight.toPx() / 2f }
            val alignment = when {
                rect == null -> Alignment.Center
                bottomHalf -> Alignment.TopCenter
                else -> Alignment.BottomCenter
            }
            Box(
                Modifier
                    .fillMaxSize()
                    // пузырь не должен уезжать под статус- и навигационную панели
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .pointerInput(Unit) { detectTapGestures { } },
                contentAlignment = alignment,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(fs.surf2)
                        .border(1.dp, fs.outline, RoundedCornerShape(18.dp))
                        .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                            tint = fs.accent,
                            modifier = Modifier.padding(top = 2.dp).size(24.dp),
                        )
                        Text(
                            step.title,
                            Modifier.weight(1f).padding(start = 10.dp),
                            color = fs.on,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.W800,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // «Закрыть» в правом верхнем углу — подальше от «Дальше»
                        if (state.index < steps.size - 1) {
                            Text(
                                s.tourSkip,
                                Modifier
                                    .padding(start = 12.dp)
                                    .clickableNoRipple { state.stop() },
                                color = fs.on3,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.W700,
                            )
                        }
                    }
                    // текст с inline-пиктограммами: маркеры [папка] [звезда] [кн-поиск] …
                    Text(
                        tourAnnotated(step.text),
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        color = fs.on2,
                        fontSize = 14.5.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.W600,
                        inlineContent = tourInlineContent(),
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            s.tourBack,
                            Modifier.clickableNoRipple(enabled = state.index > 0) { state.back() },
                            color = if (state.index > 0) fs.on2 else fs.on3,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.W800,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${state.index + 1} / ${steps.size}",
                            color = fs.on3,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.W700,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (state.index == steps.size - 1) s.tourDone else s.tourNext,
                            Modifier.clickableNoRipple { state.next() },
                            color = fs.accent,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.W800,
                        )
                    }
                }
            }
        }
    }
}

/** Текст-кнопка без «плашки» нажатия, как у остальных строк приложения. */
private fun Modifier.clickableNoRipple(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    composed {
        this.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
    }
