package com.foldersound.ui.eq

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldersound.playback.AudioEffects
import com.foldersound.ui.common.FsSwitch
import com.foldersound.ui.i18n.LocalStrings
import com.foldersound.ui.common.HSlider
import com.foldersound.ui.common.IconBtn
import com.foldersound.ui.common.VSlider
import com.foldersound.ui.theme.Fs
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun EqualizerScreen(onBack: () -> Unit, fromPlayer: Boolean = true) {
    val fs = Fs.c
    val s = LocalStrings.current
    val state by AudioEffects.state.collectAsStateWithLifecycle()
    val bandInfo by AudioEffects.bandInfo.collectAsStateWithLifecycle()
    val on = state.enabled
    val dim = if (on) 1f else 0.42f

    Column(
        Modifier
            .fillMaxSize()
            .background(fs.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // шапка
        Row(
            Modifier.padding(start = 4.dp, end = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBtn(
                Icons.AutoMirrored.Filled.ArrowBack,
                if (fromPlayer) s.backToPlayer else s.backToLibrary,
                onBack,
            )
            Text(
                s.equalizer,
                Modifier.weight(1f),
                color = fs.on2,
                fontSize = 12.sp,
                fontWeight = FontWeight.W800,
                letterSpacing = 2.2.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                s.reset,
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { AudioEffects.reset() }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                color = fs.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.W800,
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 26.dp)
        ) {
            // главный выключатель
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(s.equalizer, color = fs.on, fontSize = 16.sp, fontWeight = FontWeight.W800)
                    Text(
                        s.eqSubtitle,
                        Modifier.padding(top = 2.dp),
                        color = fs.on2,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.W600,
                    )
                }
                FsSwitch(checked = on, onChange = { AudioEffects.setEnabled(it) })
            }

            // пресеты
            LazyRow(
                Modifier
                    .alpha(dim)
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                val chips = AudioEffects.PRESET_ORDER + AudioEffects.CUSTOM
                itemsIndexed(chips) { _, id ->
                    PresetChip(
                        name = s.presetNames[id] ?: id,
                        active = state.preset == id,
                        enabled = on,
                        onClick = { AudioEffects.setPreset(id) },
                    )
                }
            }

            // полосы
            Row(
                Modifier
                    .alpha(dim)
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(fs.surf)
                    .border(1.dp, fs.outline, RoundedCornerShape(22.dp))
                    .padding(vertical = 16.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val info = bandInfo
                if (info == null || info.count == 0 || state.bands.isEmpty()) {
                    Text(
                        s.eqUnavailable,
                        Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                        color = fs.on3,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.W700,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    val n = maxOf(info.count, state.bands.size)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(n) { i ->
                            val value = state.bands.getOrElse(i) { 0 }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(9.dp),
                            ) {
                                VSlider(
                                    value = value,
                                    onValueChange = { AudioEffects.setBand(i, it) },
                                    min = info.minDb,
                                    max = info.maxDb,
                                    enabled = on,
                                )
                                Text(
                                    if (value > 0) "+$value" else "$value",
                                    color = fs.on,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.W800,
                                )
                                Text(
                                    freqLabel(info.centerFreqs.getOrElse(i) { 0 }),
                                    color = fs.on3,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.W700,
                                )
                            }
                        }
                    }
                }
            }
            Text(
                "${s.rangeLabel} ${bandInfo?.minDb ?: -15} … +${bandInfo?.maxDb ?: 15} ${s.dbUnit}",
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                color = fs.on3,
                fontSize = 11.sp,
                fontWeight = FontWeight.W700,
                letterSpacing = 0.4.sp,
                textAlign = TextAlign.Center,
            )

            // бас / объёмный звук
            Column(
                Modifier
                    .alpha(dim)
                    .padding(top = 18.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(fs.surf)
                    .border(1.dp, fs.outline, RoundedCornerShape(22.dp))
                    .padding(horizontal = 18.dp, vertical = 4.dp),
            ) {
                PercentRow(
                    title = s.bassBoost,
                    percent = state.bass,
                    enabled = on,
                    onSet = { AudioEffects.setBass(it) },
                )
                PercentRow(
                    title = s.surround,
                    percent = state.surround,
                    enabled = on,
                    onSet = { AudioEffects.setSurround(it) },
                )
            }
        }
    }
}

@Composable
private fun PresetChip(name: String, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val fs = Fs.c
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (active) fs.primary else fs.surf2)
            .border(1.5.dp, if (active) fs.primary else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
            ) { onClick() }
            .padding(horizontal = 15.dp, vertical = 9.dp),
    ) {
        Text(
            name,
            color = if (active) fs.onAccent else fs.on2,
            fontSize = 13.sp,
            fontWeight = FontWeight.W800,
        )
    }
}

@Composable
private fun PercentRow(
    title: String,
    percent: Int,
    enabled: Boolean,
    onSet: (Int) -> Unit,
) {
    val fs = Fs.c
    Column(Modifier.padding(top = 12.dp, bottom = 6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(title, color = fs.on, fontSize = 14.5.sp, fontWeight = FontWeight.W800)
            Spacer(Modifier.weight(1f))
            Text(
                "$percent%",
                color = fs.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.W800,
            )
        }
        HSlider(
            fraction = percent / 100f,
            onChange = if (enabled) ({ f -> onSet((f * 100).roundToInt()) }) else null,
        )
    }
}

/** 60 → «60», 3600 → «3.6k», 14000 → «14k». */
private fun freqLabel(hz: Int): String = if (hz < 1000) {
    "$hz"
} else {
    val k = hz / 1000.0
    if (k % 1.0 == 0.0) "${k.toInt()}k" else String.format(Locale.US, "%.1fk", k)
}
