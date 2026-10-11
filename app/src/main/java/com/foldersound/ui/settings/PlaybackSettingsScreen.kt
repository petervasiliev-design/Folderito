package com.foldersound.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldersound.ui.common.FsSwitch
import com.foldersound.ui.common.HSlider
import com.foldersound.ui.common.IconBtn
import com.foldersound.ui.common.SectionLabel
import com.foldersound.ui.i18n.LocalStrings
import com.foldersound.ui.theme.Fs
import java.util.Locale
import kotlin.math.roundToInt

private const val MIN_SPEED = 0.5f
private const val MAX_SPEED = 2f
private const val SPEED_STEPS = 30 // шаг 0.05: (2 - 0.5) / 0.05

/** «1», «0.75», «1.25»… с суффиксом «x». */
private fun speedLabel(v: Float): String =
    String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.') + "x"

/**
 * Экран «Настройки аудиокниг»: скорость воспроизведения (0.5x–2x),
 * вырезание пауз и четкость речи. Всё применяется сразу и хранится между сессиями.
 */
@Composable
fun PlaybackSettingsScreen(
    onBack: () -> Unit,
    speed: Float,
    onSetSpeed: (Float) -> Unit,
    clarity: Boolean,
    onSetClarity: (Boolean) -> Unit,
    skipSilence: Boolean,
    onSetSkipSilence: (Boolean) -> Unit,
) {
    val fs = Fs.c
    val s = LocalStrings.current

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
                s.playbackSettingsTitle,
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
            SectionLabel(s.speedTitle, Modifier.padding(start = 4.dp, bottom = 6.dp))
            Column(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(fs.surf)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                var dragging by remember(speed) { mutableStateOf(false) }
                var dragValue by remember(speed) { mutableStateOf(speed) }
                val shown = if (dragging) dragValue else speed
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Speed,
                        contentDescription = null,
                        tint = fs.on3,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        s.speedTitle,
                        Modifier.padding(start = 12.dp).weight(1f),
                        color = fs.on,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.W700,
                    )
                    Text(
                        speedLabel(shown),
                        color = fs.accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.W800,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBtn(
                        Icons.Filled.Remove,
                        "−",
                        { onSetSpeed((speed - 0.05f).coerceIn(MIN_SPEED, MAX_SPEED)) },
                        size = 36.dp,
                        iconSize = 20.dp,
                    )
                    HSlider(
                        fraction = (shown - MIN_SPEED) / (MAX_SPEED - MIN_SPEED),
                        modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                        onChange = { f ->
                            dragging = true
                            dragValue = MIN_SPEED + (f * SPEED_STEPS).roundToInt() * 0.05f
                        },
                        // применяем и сохраняем только при отпускании: непрерывная смена
                        // параметров звука во время драга ломает воспроизведение
                        onDragEnd = {
                            dragging = false
                            onSetSpeed(dragValue)
                        },
                    )
                    IconBtn(
                        Icons.Filled.Add,
                        "+",
                        { onSetSpeed((speed + 0.05f).coerceIn(MIN_SPEED, MAX_SPEED)) },
                        size = 36.dp,
                        iconSize = 20.dp,
                    )
                }
                // шкала выровнена под ход ползунка: ширина кнопок −/+ (36dp) плюс
                // его горизонтальный отступ (6dp) с каждой стороны
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 42.dp, end = 42.dp)
                ) {
                    Text(
                        "0.5x",
                        Modifier.weight(1f),
                        color = fs.on3,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.W700,
                    )
                    // движок на 1x стоит на трети шкалы, не в середине
                    Text(
                        "1x",
                        Modifier.weight(1f),
                        color = fs.on3,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.W700,
                    )
                    Text(
                        "2x",
                        Modifier.weight(1f),
                        textAlign = TextAlign.End,
                        color = fs.on3,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.W700,
                    )
                }
            }

            // вырезание пауз (Media3 skip silence)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(fs.surf)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.FastForward,
                    contentDescription = null,
                    tint = fs.on3,
                    modifier = Modifier.size(22.dp),
                )
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        s.skipSilenceTitle,
                        color = fs.on,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.W700,
                    )
                    Text(
                        s.skipSilenceHint,
                        Modifier.padding(top = 2.dp),
                        color = fs.on2,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.W600,
                    )
                }
                FsSwitch(checked = skipSilence, onChange = onSetSkipSilence)
            }

            // четкость речи (голосовой пресет, перекрывает эквалайзер)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(fs.surf)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.RecordVoiceOver,
                    contentDescription = null,
                    tint = fs.on3,
                    modifier = Modifier.size(22.dp),
                )
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        s.clarityTitle,
                        color = fs.on,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.W700,
                    )
                    Text(
                        s.clarityHint,
                        Modifier.padding(top = 2.dp),
                        color = fs.on2,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.W600,
                    )
                }
                FsSwitch(checked = clarity, onChange = onSetClarity)
            }
        }
    }
}
