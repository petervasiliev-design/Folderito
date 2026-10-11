package com.foldersound.ui.player

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.foldersound.ui.icons.FsIcons
import com.foldersound.ui.i18n.LocalStrings
import com.foldersound.data.Track
import com.foldersound.playback.AudioEffects
import com.foldersound.playback.PlayerConnection
import com.foldersound.playback.SleepTimer
import com.foldersound.ui.common.HSlider
import com.foldersound.ui.common.IconBtn
import com.foldersound.ui.common.eqDot
import com.foldersound.ui.common.formatMs
import com.foldersound.ui.common.holdToggle
import com.foldersound.ui.common.horizontalSwipes
import com.foldersound.ui.theme.Fs
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    connection: PlayerConnection,
    track: Track?,
    pathText: String,
    onBack: () -> Unit,
    onOpenEq: () -> Unit,
    onOpenPlaybackSettings: () -> Unit,
    onOpenSongs: () -> Unit,
    seekButtonsShown: Boolean = true,
    seekStepSec: Int = 10,
) {
    val fs = Fs.c
    val s = LocalStrings.current
    val isPlaying by connection.isPlaying.collectAsStateWithLifecycle()
    val shuffle by connection.shuffle.collectAsStateWithLifecycle()
    val repeatMode by connection.repeatMode.collectAsStateWithLifecycle()
    val eqState by AudioEffects.state.collectAsStateWithLifecycle()

    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying) {
        while (true) {
            if (!dragging) {
                position = connection.position()
                duration = connection.duration()
            }
            delay(if (isPlaying) 500 else 1_000)
        }
    }

    // таймер сна: тикаем только пока он запущен
    val sleepDeadline by SleepTimer.deadlineMs.collectAsStateWithLifecycle()
    var sleepNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sleepDeadline) {
        while (sleepDeadline != null) {
            sleepNow = System.currentTimeMillis()
            delay(500)
        }
    }
    val sleepRemainMs = ((sleepDeadline ?: 0L) - sleepNow).coerceAtLeast(0L)

    Column(
        Modifier
            .fillMaxSize()
            .background(fs.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            // свайп влево — список песен, свайп вправо — назад в дерево;
            // пока тянется ползунок перемотки — свайпы выключены
            .horizontalSwipes(
                onSwipeLeft = onOpenSongs,
                onSwipeRight = onBack,
                enabled = !dragging,
            )
    ) {
        // шапка
        Row(
            Modifier.padding(start = 4.dp, end = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBtn(
                Icons.AutoMirrored.Filled.ArrowBack, s.backToLibrary, onBack
            )
            if (sleepDeadline != null) {
                // удержание 1 с на чипе закрывает таймер (дубль кнопки в шторке)
                Chip(
                    Icons.Filled.Timer,
                    formatMs(sleepRemainMs),
                    trailingIcon = Icons.Filled.Close,
                    modifier = Modifier.holdToggle(timeoutMs = 1_000) { SleepTimer.cancel() },
                )
            }
            Text(
                s.nowPlaying,
                Modifier.weight(1f),
                color = fs.on2,
                fontSize = 12.sp,
                fontWeight = FontWeight.W800,
                letterSpacing = 2.2.sp,
                textAlign = TextAlign.Center,
            )
            IconBtn(
                Icons.Filled.Tune,
                s.equalizer,
                onOpenEq,
                modifier = Modifier.eqDot(eqState.enabled, fs.accent),
            )
            IconBtn(
                Icons.Filled.Speed,
                s.playbackSettingsTitle,
                onOpenPlaybackSettings,
            )
            IconBtn(
                Icons.AutoMirrored.Filled.ArrowForward,
                s.toSongs,
                onOpenSongs,
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp)
        ) {
            ArtWithVinyl(
                artUri = track?.albumArtUri,
                isPlaying = isPlaying,
                modifier = Modifier
                    .padding(top = 14.dp)
                    .align(Alignment.CenterHorizontally),
            )

            // метаданные
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    track?.title?.ifBlank { "—" } ?: "—",
                    color = fs.on,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.W800,
                    textAlign = TextAlign.Center,
                    lineHeight = 26.sp,
                )
                Text(
                    track?.artist?.ifBlank { s.unknownArtist } ?: "—",
                    Modifier.padding(top = 3.dp),
                    color = fs.accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.W700,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Chip(FsIcons.SoundWave, track?.album?.ifBlank { s.noAlbum } ?: "—")
                    Chip(FsIcons.Folder, pathText)
                }
            }

            // позиция
            val fraction = if (dragging) dragFraction
            else if (duration > 0) position.toFloat() / duration else 0f
            Column(Modifier.padding(top = 16.dp)) {
                HSlider(
                    fraction = fraction,
                    onChange = {
                        dragging = true
                        dragFraction = it
                    },
                    onDragEnd = {
                        connection.seekToFraction(dragFraction)
                        dragging = false
                    },
                )
                Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                    Text(
                        formatMs(if (dragging) (dragFraction * duration).toLong() else position),
                        color = fs.on3,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.W700,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        formatMs(duration),
                        color = fs.on3,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.W700,
                    )
                }
            }

            // кнопки перемотки ±N с (видимость и шаг — из настроек)
            if (seekButtonsShown) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SeekPill(
                        label = "−$seekStepSec ${s.secShort}",
                        onClick = { connection.seekBy(-seekStepSec * 1000L) },
                    )
                    SeekPill(
                        label = "+$seekStepSec ${s.secShort}",
                        onClick = { connection.seekBy(seekStepSec * 1000L) },
                        modifier = Modifier.padding(start = 14.dp),
                    )
                }
            }

            // управление
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CtlButton(
                    icon = Icons.Filled.Shuffle,
                    label = if (shuffle) s.shuffleOn else s.shuffleOff,
                    active = shuffle,
                ) { connection.setShuffle(!shuffle) }
                CtlButton(
                    icon = Icons.Filled.SkipPrevious,
                    label = s.prevTrack,
                    iconSize = 30.dp,
                ) { connection.previous() }
                PlayFab(isPlaying) { connection.togglePlay() }
                CtlButton(
                    icon = Icons.Filled.SkipNext,
                    label = s.nextTrack,
                    iconSize = 30.dp,
                ) { connection.next() }
                CtlButton(
                    icon = if (repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE)
                        Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    label = when (repeatMode) {
                        androidx.media3.common.Player.REPEAT_MODE_ALL -> s.repeatAll
                        androidx.media3.common.Player.REPEAT_MODE_ONE -> s.repeatOne
                        else -> s.repeatOff
                    },
                    active = repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF,
                ) { connection.cycleRepeat() }
            }
        }
    }
}

/* ==================== обложка + пластинка ==================== */

@Composable
private fun ArtWithVinyl(artUri: Uri?, isPlaying: Boolean, modifier: Modifier = Modifier) {
    val fs = Fs.c
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            var last = 0L
            while (true) {
                withFrameNanos { now ->
                    if (last != 0L) {
                        angle = (angle + (now - last) / 8_500_000_000f * 360f) % 360f
                    }
                    last = now
                }
            }
        }
    }
    Box(modifier.size(308.dp, 252.dp)) {
        // пластинка справа, выглядывает из-за обложки (как .vinyl в макете)
        Box(
            Modifier
                .offset(x = 186.dp, y = 23.dp)
                .size(122.dp, 206.dp)
                .graphicsLayer { rotationZ = angle }
                .shadow(10.dp, RoundedCornerShape(50))
                .clip(RoundedCornerShape(50))
                .background(vinylBrush())
        ) {
            // центральная этикетка
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(fs.secondary)
            ) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(fs.bg)
                )
            }
        }
        // обложка
        Box(
            Modifier
                .size(252.dp)
                .shadow(26.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(fs.coverGradStart, fs.coverGradEnd)))
        ) {
            Icon(
                FsIcons.SoundWave,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(96.dp),
            )
            if (artUri != null) {
                AsyncImage(
                    model = artUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

/** Бороздки пластинки — чередование двух тёмных оттенков. */
private fun vinylBrush(): Brush {
    val stops = ArrayList<Pair<Float, Color>>()
    stops.add(0f to Color(0xFF9D7B56))
    stops.add(0.155f to Color(0xFF9D7B56))
    var stop = 0.16f
    var i = 0
    while (stop < 0.98f) {
        stops.add(stop to if (i % 2 == 0) Color(0xFF1F2536) else Color(0xFF141827))
        stop += 0.045f
        i++
    }
    stops.add(1f to Color(0xFF1F2536))
    return Brush.radialGradient(*stops.toTypedArray())
}

/* ==================== чип ==================== */

@Composable
private fun Chip(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    trailingIcon: ImageVector? = null,
) {
    val fs = Fs.c
    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(fs.surf2)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = fs.on3, modifier = Modifier.size(11.dp))
        Text(
            text,
            color = fs.on2,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.W700,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailingIcon != null) {
            Icon(trailingIcon, contentDescription = null, tint = fs.on3, modifier = Modifier.size(11.dp))
        }
    }
}

/* ==================== кнопки управления ==================== */

/** Таблетка перемотки «−10 с» / «+10 с». */
@Composable
private fun SeekPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fs = Fs.c
    Box(
        modifier
            .noRippleClickable(onClick)
            .clip(RoundedCornerShape(18.dp))
            .background(fs.surf2)
            .border(1.dp, fs.outline, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            color = fs.on,
            fontSize = 14.sp,
            fontWeight = FontWeight.W800,
        )
    }
}

@Composable
private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.clickable(
        interactionSource = interaction,
        indication = null,
        onClick = onClick,
    )
}

@Composable
private fun CtlButton(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    iconSize: Dp = 24.dp,
    onClick: () -> Unit,
) {
    val fs = Fs.c
    Column(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .noRippleClickable(onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (active) fs.accent else fs.on2,
            modifier = Modifier.size(iconSize),
        )
        if (active) {
            Box(
                Modifier
                    .padding(top = 3.dp)
                    .size(4.5.dp)
                    .clip(CircleShape)
                    .background(fs.accent)
            )
        }
    }
}

@Composable
private fun PlayFab(isPlaying: Boolean, onClick: () -> Unit) {
    val fs = Fs.c
    val s = LocalStrings.current
    Box(
        Modifier
            .size(78.dp)
            .shadow(14.dp, CircleShape)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(fs.fabGradStart, fs.primary)))
            .noRippleClickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (isPlaying) s.pause else s.play,
            tint = fs.onAccent,
            modifier = Modifier.size(34.dp),
        )
    }
}
