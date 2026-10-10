package com.foldersound.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldersound.playback.SleepTimer
import com.foldersound.ui.common.SectionLabel
import com.foldersound.ui.common.formatMs
import com.foldersound.ui.common.holdToggle
import com.foldersound.ui.i18n.LocalStrings
import com.foldersound.ui.theme.Fs
import com.foldersound.ui.theme.ThemeMode
import kotlinx.coroutines.delay

/**
 * Боковая шторка: эквалайзер, таймер сна, настройки, выбор темы оформления.
 * Выбор языка перенесён на экран «Настройки».
 */
@Composable
fun AppDrawer(
    themeMode: ThemeMode,
    onSetTheme: (ThemeMode) -> Unit,
    onOpenEq: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fs = Fs.c
    val s = LocalStrings.current
    Column(
        modifier
            .fillMaxHeight()
            .width(304.dp)
            .background(fs.surf)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                com.foldersound.ui.icons.FsIcons.SoundWave,
                contentDescription = null,
                tint = fs.accent,
                modifier = Modifier.size(22.dp),
            )
            Text(
                "Folderito",
                color = fs.on,
                fontSize = 19.sp,
                fontWeight = FontWeight.W800,
                letterSpacing = (-0.3).sp,
            )
        }

        // быстрое действие: эквалайзер
        ActionRow(
            icon = Icons.Filled.Tune,
            label = s.equalizer,
            onClick = onOpenEq,
        )

        // таймер сна: когда запущен — пункт показывает остаток прямо в шторке + кнопка закрытия
        val sleepDeadline = SleepTimer.deadlineMs.collectAsStateWithLifecycle()
        var sleepNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(sleepDeadline.value) {
            while (sleepDeadline.value != null) {
                sleepNow = System.currentTimeMillis()
                delay(500)
            }
        }
        val sleepRemainMs = ((sleepDeadline.value ?: 0L) - sleepNow).coerceAtLeast(0L)

        if (sleepDeadline.value == null) {
            ActionRow(
                icon = Icons.Filled.Timer,
                label = s.sleepTimerSection,
                onClick = onOpenSleepTimer,
            )
        } else {
            // пункт меню исчезает: вместо него живой отсчёт и кнопка закрытия
            Row(
                Modifier
                    .padding(top = 14.dp, bottom = 2.dp)
                    .fillMaxWidth()
                    .height(46.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Timer,
                    contentDescription = null,
                    tint = fs.accent,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    s.sleepRemaining,
                    Modifier.padding(start = 12.dp).weight(1f),
                    color = fs.on,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.W600,
                )
                Text(
                    formatMs(sleepRemainMs),
                    color = fs.accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.W800,
                )
            }
            // закрытие таймера — только удержанием 1 с, быстрый тап безвреден
            Row(
                Modifier
                    .padding(top = 4.dp, bottom = 2.dp)
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

        // быстрое действие: настройки плеера
        ActionRow(
            icon = Icons.Filled.Settings,
            label = s.settings,
            onClick = onOpenSettings,
        )

        SectionLabel(s.themeSection, Modifier.padding(start = 8.dp, top = 18.dp, bottom = 4.dp))
        OptionRow(
            icon = Icons.Filled.AutoMode,
            label = s.themeSystem,
            selected = themeMode == ThemeMode.SYSTEM,
        ) { onSetTheme(ThemeMode.SYSTEM) }
        OptionRow(
            icon = Icons.Filled.LightMode,
            label = s.themeLight,
            selected = themeMode == ThemeMode.LIGHT,
        ) { onSetTheme(ThemeMode.LIGHT) }
        OptionRow(
            icon = Icons.Filled.DarkMode,
            label = s.themeDark,
            selected = themeMode == ThemeMode.DARK,
        ) { onSetTheme(ThemeMode.DARK) }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val fs = Fs.c
    Row(
        Modifier
            .padding(top = 14.dp, bottom = 2.dp)
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = fs.on2,
            modifier = Modifier.size(20.dp),
        )
        Text(
            label,
            Modifier.padding(start = 12.dp),
            color = fs.on,
            fontSize = 15.sp,
            fontWeight = FontWeight.W600,
        )
        Spacer(Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = fs.on3,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun OptionRow(
    icon: ImageVector? = null,
    badge: String? = null,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val fs = Fs.c
    Row(
        Modifier
            .padding(top = 2.dp, bottom = 2.dp)
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) fs.surf2 else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            icon != null -> Icon(
                icon,
                contentDescription = null,
                tint = if (selected) fs.accent else fs.on3,
                modifier = Modifier.size(20.dp),
            )
            badge != null -> Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(fs.surf2),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    badge,
                    color = if (selected) fs.accent else fs.on2,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.W800,
                )
            }
        }
        Text(
            label,
            Modifier.padding(start = 12.dp),
            color = if (selected) fs.on else fs.on2,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.W700 else FontWeight.W600,
        )
        Spacer(Modifier.weight(1f))
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = fs.accent,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
