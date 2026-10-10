package com.foldersound.ui.sleep

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldersound.playback.SleepTimer
import com.foldersound.ui.common.HSlider
import com.foldersound.ui.common.IconBtn
import com.foldersound.ui.common.formatMs
import com.foldersound.ui.common.holdToggle
import com.foldersound.ui.i18n.LocalStrings
import com.foldersound.ui.theme.Fs
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Экран «Таймер сна»: пресеты кружками (дуга — доля от максимальных 90 минут,
 * подпись под кружком) — клик запускает таймер и закрывает окно; свой интервал
 * слайдером 5–180 мин с кнопкой «Запустить» (активна после изменения слайдера).
 * Запущенный таймер виден в шторке (отсчёт + закрытие удержанием) и чипом в плеере.
 */
@Composable
fun SleepTimerScreen(onBack: () -> Unit) {
    val fs = Fs.c
    val s = LocalStrings.current
    val deadlineState = SleepTimer.deadlineMs.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(fs.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // шапка
        Row(
            Modifier.padding(start = 4.dp, end = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBtn(Icons.AutoMirrored.Filled.ArrowBack, s.backToLibrary, onBack)
            Text(
                s.sleepTimerSection,
                Modifier.weight(1f),
                color = fs.on2,
                fontSize = 12.sp,
                fontWeight = FontWeight.W800,
                letterSpacing = 2.2.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(42.dp))
        }

        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (deadlineState.value == null) {
                // пресеты: клик запускает таймер и закрывает окно (возврат на главный экран)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    for (min in SleepTimer.PRESETS_MIN) {
                        PresetCircle(minutes = min) {
                            SleepTimer.start(min)
                            onBack()
                        }
                    }
                }
                SleepSliderRow(onStart = { min ->
                    SleepTimer.start(min)
                    onBack()
                })
            } else {
                SleepRunning(deadline = deadlineState.value ?: 0L)
            }
        }
    }
}

/** Кружок-пресет: дуга внутри — доля минут от максимума (90 мин — полный круг). */
@Composable
private fun PresetCircle(minutes: Int, onClick: () -> Unit) {
    val fs = Fs.c
    val s = LocalStrings.current
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(54.dp)) {
            val stroke = 4.dp.toPx()
            val d = size.width - stroke
            val topLeft = Offset(stroke / 2, stroke / 2)
            drawArc(
                color = fs.outline,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = Size(d, d),
                style = Stroke(stroke),
            )
            drawArc(
                color = fs.accent,
                startAngle = -90f,
                sweepAngle = 360f * minutes / SleepTimer.PRESETS_MIN.last(),
                useCenter = false,
                topLeft = topLeft,
                size = Size(d, d),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            "$minutes ${s.minutesShort}",
            Modifier.padding(top = 6.dp),
            color = fs.on2,
            fontSize = 11.sp,
            fontWeight = FontWeight.W600,
        )
    }
}

/** Свой интервал: слайдер 5–180 мин и кнопка «Запустить», оживающая после изменения слайдера. */
@Composable
private fun SleepSliderRow(onStart: (Int) -> Unit) {
    val fs = Fs.c
    val s = LocalStrings.current
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(30) }
    var chosen by remember { mutableStateOf(30) }
    var armed by remember { mutableStateOf(false) }
    val shown = if (dragging) dragValue else chosen
    val span = (SleepTimer.CUSTOM_MAX - SleepTimer.CUSTOM_MIN).toFloat()

    Column(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(fs.surf)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Timer,
                contentDescription = null,
                tint = fs.on3,
                modifier = Modifier.size(22.dp),
            )
            Text(
                s.sleepCustom,
                Modifier.padding(start = 12.dp).weight(1f),
                color = fs.on,
                fontSize = 15.sp,
                fontWeight = FontWeight.W700,
            )
            Text(
                "$shown ${s.minutesShort}",
                color = fs.accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.W800,
            )
        }
        HSlider(
            fraction = (shown - SleepTimer.CUSTOM_MIN) / span,
            onChange = { f ->
                dragging = true
                dragValue = SleepTimer.CUSTOM_MIN + (f * span).roundToInt()
                armed = true
            },
            onDragEnd = {
                dragging = false
                chosen = dragValue
            },
        )
        // кнопка в группе «Своё»: неактивна, пока слайдер не тронули
        Row(
            Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (armed) Brush.horizontalGradient(listOf(fs.accent, fs.accent2))
                    else SolidColor(fs.surf2)
                )
                .clickable(
                    enabled = armed,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onStart(chosen) },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                s.sleepStart,
                color = if (armed) fs.onAccent else fs.on3,
                fontSize = 15.sp,
                fontWeight = FontWeight.W800,
            )
        }
    }
}

/** Запущенный таймер: тикающий остаток и кнопка закрытия, срабатывающая удержанием 2 с. */
@Composable
private fun SleepRunning(deadline: Long) {
    val fs = Fs.c
    val s = LocalStrings.current
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(500)
        }
    }
    val remainMs = (deadline - nowMs).coerceAtLeast(0L)

    Column(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(fs.surf)
            .padding(vertical = 6.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Timer,
                contentDescription = null,
                tint = fs.accent,
                modifier = Modifier.size(22.dp),
            )
            Text(
                s.sleepRemaining,
                Modifier.padding(start = 12.dp).weight(1f),
                color = fs.on,
                fontSize = 15.sp,
                fontWeight = FontWeight.W700,
            )
            Text(
                formatMs(remainMs),
                color = fs.accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.W800,
            )
        }
        // срабатывает только удержанием 1 секунды: быстрый тап ничего не делает
        Row(
            Modifier
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(fs.surf2)
                .holdToggle(timeoutMs = 1_000) { SleepTimer.cancel() }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                s.sleepCancel,
                Modifier.weight(1f),
                color = fs.on,
                fontSize = 14.sp,
                fontWeight = FontWeight.W700,
                textAlign = TextAlign.Center,
            )
        }
    }
}
