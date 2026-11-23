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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
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

/** Шаг тура. [targetKey] = null — пузырь по центру, без прожектора. */
data class TourStep(
    val targetKey: String? = null,
    val title: String,
    val text: String,
)

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
                    .pointerInput(Unit) { detectTapGestures { } },
                contentAlignment = alignment,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(fs.surf2)
                        .border(1.dp, fs.outline, RoundedCornerShape(18.dp))
                        .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 10.dp),
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
                            fontSize = 16.sp,
                            fontWeight = FontWeight.W800,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        step.text,
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        color = fs.on2,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.W600,
                    )
                    if (state.index < steps.size - 1) {
                        Text(
                            s.tourSkip,
                            Modifier
                                .align(Alignment.End)
                                .padding(top = 8.dp)
                                .clickableNoRipple { state.stop() },
                            color = fs.on3,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.W700,
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            s.tourBack,
                            Modifier.clickableNoRipple(enabled = state.index > 0) { state.back() },
                            color = if (state.index > 0) fs.on2 else fs.on3,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.W800,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${state.index + 1} / ${steps.size}",
                            color = fs.on3,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.W700,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (state.index == steps.size - 1) s.tourDone else s.tourNext,
                            Modifier.clickableNoRipple { state.next() },
                            color = fs.accent,
                            fontSize = 14.sp,
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
