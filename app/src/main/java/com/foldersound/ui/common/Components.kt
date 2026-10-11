package com.foldersound.ui.common

import android.net.Uri
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.foldersound.ui.icons.FsIcons
import com.foldersound.ui.theme.Fs
import com.foldersound.ui.theme.FsColors
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/* ==================== утилиты ==================== */

fun formatMs(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

/** Подсветка совпадения поиска, как <mark> в макете. */
fun highlight(
    text: String,
    query: String,
    baseColor: Color,
    highlightColor: Color,
    baseWeight: FontWeight = FontWeight.W700,
): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(color = baseColor, fontWeight = baseWeight)) {
        append(text)
    }
    val q = query.trim().lowercase()
    if (q.isNotEmpty()) {
        val i = text.lowercase().indexOf(q)
        if (i >= 0) {
            addStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.W800), i, i + q.length)
        }
    }
}

/* ==================== чекбокс (три состояния) ==================== */

enum class CheckState { Off, Mixed, On }

@Composable
fun TriCheck(
    state: CheckState,
    onFolderRow: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val fs = Fs.c
    val filled = state != CheckState.Off
    val boxColor = when {
        !filled && onFolderRow -> Color.Transparent
        onFolderRow -> Color.White
        else -> fs.primary
    }
    val borderColor = when {
        filled && onFolderRow -> Color.White
        onFolderRow -> Color.White.copy(alpha = 0.75f)
        filled -> fs.primary
        else -> fs.on2
    }
    val iconTint = if (onFolderRow) fs.primary else Color.White
    Box(modifier.size(38.dp, 44.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(21.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(boxColor)
                .border(2.dp, borderColor, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                CheckState.On -> Icon(Icons.Filled.Check, null, tint = iconTint, modifier = Modifier.size(15.dp))
                CheckState.Mixed -> Icon(Icons.Filled.Remove, null, tint = iconTint, modifier = Modifier.size(15.dp))
                CheckState.Off -> Unit
            }
        }
    }
}

/* ==================== обложка трека ==================== */

@Composable
fun TrackCover(
    artUri: Uri?,
    modifier: Modifier = Modifier,
    corner: Dp = 13.dp,
    noteSize: Dp = 32.dp,
) {
    val fs = Fs.c
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(Brush.linearGradient(listOf(fs.coverGradStart, fs.coverGradEnd))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            FsIcons.SoundWave,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.92f),
            modifier = Modifier.size(noteSize),
        )
        if (artUri != null) {
            AsyncImage(
                model = artUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        // блики как у .cov::after в макете
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.32f), Color.Transparent),
                        center = Offset(0.18f, 0.1f),
                        radius = 0.9f,
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.28f), Color.Transparent),
                        center = Offset(0.88f, 0.98f),
                        radius = 0.85f,
                    )
                )
        )
    }
}

/* ==================== «эквалайзерные» полоски ==================== */

@Composable
fun PlayingBars(
    active: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 15.dp,
    barColor: Color = Fs.c.accent,
) {
    Row(
        modifier.height(height),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        repeat(3) { i ->
            val transition = rememberInfiniteTransition(label = "bars$i")
            val animated by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 525, easing = EaseInOutSine),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(i * 280),
                ),
                label = "barsAnim$i",
            )
            Box(
                Modifier
                    .width(3.5.dp)
                    .fillMaxHeight(if (active) animated else 0.3f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )
        }
    }
}

/* ==================== выключатель-«пилюля» ==================== */

@Composable
fun FsSwitch(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fs = Fs.c
    // checked через rememberUpdatedState: pointerInput(Unit) держит лямбду
    // из первой композиции, и без этого тапы после первого игнорируются
    val currentChecked by rememberUpdatedState(checked)
    val knobSize by animateDpAsState(if (checked) 19.dp else 15.dp, label = "knobSize")
    val knobX by animateDpAsState(if (checked) 25.dp else 5.dp, label = "knobX")
    Box(
        modifier
            .size(54.dp, 31.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (checked) fs.primary else fs.surf3)
            .border(2.dp, if (checked) fs.primary else fs.on3, RoundedCornerShape(16.dp))
            .pointerInput(Unit) { detectTapGestures { onChange(!currentChecked) } },
    ) {
        Box(
            Modifier
                .offset(x = knobX)
                .align(Alignment.CenterStart)
                .size(knobSize)
                .clip(CircleShape)
                .background(if (checked) Color.White else fs.on3)
        )
    }
}

/* ==================== горизонтальный ползунок ==================== */

/**
 * Ползунок как .hs в макете: дорожка 5dp, градиентная заливка,
 * белый круглый thumb, увеличивается при перетаскивании.
 * onChange == null — неинтерактивный (прогресс мини-плеера).
 */
@Composable
fun HSlider(
    fraction: Float,
    modifier: Modifier = Modifier,
    onChange: ((Float) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
) {
    val fs = Fs.c
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(fraction) }
    val shown = (if (dragging) dragFraction else fraction).coerceIn(0f, 1f)
    val thumbSize by animateDpAsState(if (dragging) 20.dp else 15.dp, label = "thumb")

    fun handlePosition(x: Float, width: Float) {
        if (width <= 0f) return
        val f = (x / width).coerceIn(0f, 1f)
        dragFraction = f
        onChange?.invoke(f)
    }

    val gesture = if (onChange != null) {
        Modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    handlePosition(offset.x, size.width.toFloat())
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = {
                        dragging = false
                        onDragEnd?.invoke()
                    },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    change.consume()
                    handlePosition(change.position.x, size.width.toFloat())
                }
            }
    } else {
        Modifier
    }

    BoxWithConstraints(modifier.height(30.dp).then(gesture)) {
        val w = maxWidth
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(fs.surf3)
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .width(w * shown)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Brush.horizontalGradient(listOf(fs.accent, fs.accent2)))
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = (w - thumbSize) * shown + (thumbSize - 15.dp) / 2)
                .size(thumbSize)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/* ==================== вертикальный ползунок (полоса эквалайзера) ==================== */

@Composable
fun VSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    min: Int,
    max: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val fs = Fs.c
    val range = (max - min).coerceAtLeast(1)
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(value) }
    val shown = if (dragging) dragValue else value
    val frac = ((shown - min) / range.toFloat()).coerceIn(0f, 1f)

    fun fromY(y: Float, height: Float): Int {
        if (height <= 0f) return shown
        val f = (1f - y / height).coerceIn(0f, 1f)
        return min + (f * range).roundToInt()
    }

    val gesture = if (enabled) {
        Modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    dragValue = fromY(offset.y, size.height.toFloat())
                    onValueChange(dragValue)
                }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    change.consume()
                    dragValue = fromY(change.position.y, size.height.toFloat())
                    onValueChange(dragValue)
                }
            }
    } else {
        Modifier
    }

    BoxWithConstraints(
        modifier
            .size(40.dp, 196.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(fs.surf3)
            .then(gesture)
    ) {
        val h = maxHeight
        // максимум наверху: позиция от верха инверсна доле значения
        val t = 1f - frac
        val top = minOf(0.5f, t)
        val bottom = maxOf(0.5f, t)
        // линия нуля
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.75f)
                .height(1.5.dp)
                .background(fs.outline)
        )
        // заливка от центра
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset(x = 5.dp, y = h * top)
                .width(30.dp)
                .height((h * (bottom - top)).coerceAtLeast(0.dp))
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.verticalGradient(listOf(fs.accent2, fs.accent)))
        )
        // thumb
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset(x = 10.dp, y = h * t - 10.dp)
                .size(20.dp)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/* ==================== круглая кнопка-иконка ==================== */

@Composable
fun IconBtn(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    iconSize: Dp = 22.dp,
    tint: Color = Fs.c.on,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

/* ==================== подпись секции ==================== */

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = Fs.c.on3,
        fontSize = 11.sp,
        fontWeight = FontWeight.W800,
        letterSpacing = 1.6.sp,
    )
}

@Composable
fun MetaText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Fs.c.on2,
    fontSize: Float = 12f,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize.sp,
        fontWeight = FontWeight.W600,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Точка-индикатор на кнопке эквалайзера (как .p-eq-btn .dot в макете). */
fun Modifier.eqDot(visible: Boolean, color: Color): Modifier = drawWithContent {
    drawContent()
    if (visible) {
        drawCircle(
            color = color,
            radius = 3.5.dp.toPx(),
            center = Offset(size.width - 14.dp.toPx(), 14.dp.toPx()),
        )
    }
}

/** Диалог подтверждения в цветах приложения. */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val fs = Fs.c
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title, color = fs.on, fontSize = 17.sp, fontWeight = FontWeight.W800)
        },
        text = {
            Text(text, color = fs.on2, fontSize = 14.sp, fontWeight = FontWeight.W600)
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = fs.accent, fontWeight = FontWeight.W800)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(cancelLabel, color = fs.on2, fontWeight = FontWeight.W700)
            }
        },
        containerColor = fs.surf,
    )
}

/**
 * Действие по удержанию (~0,5 с): защита галочек от случайных тапов.
 * Быстрое нажатие-отпускание не consumed и проваливается к строке
 * (раскрытие папки / обычное действие строки).
 */
fun Modifier.holdToggle(
    timeoutMs: Long = 500,
    onHold: () -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val released = withTimeoutOrNull(timeoutMs) {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                if (change.isConsumed) {
                    // жест перехватил дочерний обработчик — это не наше удержание
                    return@withTimeoutOrNull true
                }
            }
            true
        } ?: false
        if (!released) {
            onHold()
            // добить жест, чтобы строка не получила клик при отпускании
            while (true) {
                val event = awaitPointerEvent()
                event.changes.forEach { it.consume() }
                if (event.changes.none { it.pressed }) break
            }
        }
    }
}

/**
 * Горизонтальные свайпы экрана. Срабатывает в начальной фазе обработки
 * (Initial pass) — раньше кликабельных детей, поэтому работает поверх
 * списков со строками. После срабатывания «добивает» жест, чтобы дети
 * не получили случайный клик.
 *
 * [enabled] = false отключает свайпы посреди жеста: так плеер гасит их,
 * когда жест начался на ползунке перемотки.
 */
fun Modifier.horizontalSwipes(
    onSwipeLeft: (() -> Unit)? = null,
    onSwipeRight: (() -> Unit)? = null,
    threshold: Dp = 100.dp,
    enabled: Boolean = true,
): Modifier = composed {
    val currentEnabled by rememberUpdatedState(enabled)
    pointerInput(Unit) {
        val thrPx = threshold.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!currentEnabled) return@awaitEachGesture
            var acc = 0f
            var fired = false
            while (!fired) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                if (!currentEnabled) return@awaitEachGesture
                acc += change.positionChange().x
                when {
                    acc < -thrPx && onSwipeLeft != null -> {
                        onSwipeLeft()
                        fired = true
                    }
                    acc > thrPx && onSwipeRight != null -> {
                        onSwipeRight()
                        fired = true
                    }
                }
            }
            if (fired) {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                    if (event.changes.none { any -> any.pressed }) break
                }
            }
        }
    }
}

/* ==================== «гнездо» раскрытой папки ==================== */

/** Шаг отступа за уровень вложенности (как .nest в макете). */
val NestIndentStep = 24.dp

/** Размеры вертикальной подписи папки. */
class NestMetrics(
    val tag: String,
    val textWidthDp: Dp,
    val textHeightDp: Dp,
) {
    val chipLen: Dp = textWidthDp + 14.dp
}

/** x-координата черты уровня depth. */
fun nestLineX(depth: Int): Dp = 10.dp + NestIndentStep * (depth - 1) + 8.dp

/**
 * Метрики подписи для первой строки блока вложенности.
 */
@Composable
fun rememberNestMetrics(tag: String?): NestMetrics? {
    if (tag == null) return null
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val size = remember(tag, density) {
        with(density) {
            val layout = measurer.measure(
                tag,
                TextStyle(fontSize = 10.sp, fontWeight = FontWeight.W800, letterSpacing = 1.sp),
                softWrap = false,
                maxLines = 1,
                constraints = Constraints(maxWidth = 170.dp.roundToPx()),
            )
            layout.size.width.toDp() to layout.size.height.toDp()
        }
    }
    return NestMetrics(tag = tag, textWidthDp = size.first, textHeightDp = size.second)
}

/**
 * Вертикальные черты слева от вложенных строк (border-left у .nest).
 * Строка уровня depth рисует черты ВСЕХ внешних уровней 1..depth —
 * как вложенные .nest в макете, где внешняя рамка не прерывается.
 * Вызывать САМЫМ ПЕРВЫМ модификатором строки — черта непрерывна и в зазорах.
 */
fun Modifier.nestLine(depth: Int, fs: FsColors): Modifier {
    if (depth < 1) return this
    val lineXs = (1..depth).map { nestLineX(it) }
    return drawBehind {
        lineXs.forEach { lineX ->
            val x = lineX.toPx()
            drawLine(
                color = fs.outline,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 2.dp.toPx(),
            )
        }
    }
}

/**
 * Вертикальная подпись имени раскрытой папки (.nest-tag): настоящий Text,
 * повёрнутый на -90° вокруг центра; центр текста лежит ровно на черте.
 * Идёт ВДОЛЬ строк (как top:12px у .nest-tag в макете), не сдвигая их.
 * Плашка фона прерывает линию.
 */
@Composable
fun NestTag(depth: Int, metrics: NestMetrics, fs: FsColors) {
    Text(
        text = metrics.tag,
        style = TextStyle(
            fontSize = 10.sp,
            fontWeight = FontWeight.W800,
            letterSpacing = 1.sp,
            color = fs.on3,
        ),
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            // точка привязки: центр плашки на черте, верх плашки — 12 dp от начала блока
            .offset(x = nestLineX(depth), y = 12.dp + metrics.chipLen / 2)
            .centerOnOrigin()
            .graphicsLayer { rotationZ = -90f }
            .background(fs.bg, RoundedCornerShape(3.dp))
            .padding(start = 7.dp, end = 7.dp, top = 2.dp, bottom = 2.dp)
            // тот же лимит, что в rememberNestMetrics
            .widthIn(max = 170.dp),
    )
}

/** Кладёт центр узла в точку (0,0) родителя и не занимает место в layout. */
private fun Modifier.centerOnOrigin(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(0, 0) {
        placeable.place(-(placeable.width / 2), -(placeable.height / 2))
    }
}
