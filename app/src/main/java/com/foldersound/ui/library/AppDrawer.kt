package com.foldersound.ui.library

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldersound.playback.SleepTimer
import com.foldersound.BuildConfig
import com.foldersound.R
import com.foldersound.ui.common.SectionLabel
import com.foldersound.ui.common.formatMs
import com.foldersound.ui.common.holdToggle
import com.foldersound.ui.i18n.AppLang
import com.foldersound.ui.i18n.LocalStrings
import com.foldersound.ui.theme.Fs
import com.foldersound.ui.theme.Skins
import com.foldersound.ui.theme.ThemeMode
import kotlinx.coroutines.delay

/**
 * Боковая шторка: эквалайзер, таймер сна, настройки, выбор языка и темы оформления.
 */
@Composable
fun AppDrawer(
    themeMode: ThemeMode,
    onSetTheme: (ThemeMode) -> Unit,
    skinId: String,
    langMode: AppLang,
    onSetLang: (AppLang) -> Unit,
    onOpenEq: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fs = Fs.c
    val s = LocalStrings.current
    var aboutOpen by remember { mutableStateOf(false) }
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

        // выбор языка интерфейса — перед темой
        SectionLabel(s.langSection, Modifier.padding(start = 8.dp, top = 18.dp, bottom = 4.dp))
        OptionRow(
            icon = Icons.Filled.Language,
            label = s.langSystem,
            selected = langMode == AppLang.SYSTEM,
        ) { onSetLang(AppLang.SYSTEM) }
        OptionRow(
            badge = "RU",
            label = s.langRu,
            selected = langMode == AppLang.RU,
        ) { onSetLang(AppLang.RU) }
        OptionRow(
            badge = "EN",
            label = s.langEn,
            selected = langMode == AppLang.EN,
        ) { onSetLang(AppLang.EN) }
        OptionRow(
            badge = "DE",
            label = s.langDe,
            selected = langMode == AppLang.DE,
        ) { onSetLang(AppLang.DE) }

        // тема: переключатель работает для схем с тёмной парой
        // («Стандартная», «Сталь»); у одиночных схем только светлая версия
        val skin = Skins.byId(skinId)
        val themeAware = skinId == Skins.CLASSIC_ID || skin?.darkColors != null
        SectionLabel(s.themeSection, Modifier.padding(start = 8.dp, top = 18.dp, bottom = 4.dp))
        if (themeAware) {
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
        } else {
            Row(
                Modifier
                    .padding(top = 2.dp, bottom = 2.dp)
                    .fillMaxWidth()
                    .height(46.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.LightMode,
                    contentDescription = null,
                    tint = fs.on3,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    s.themeLightOnly,
                    Modifier.padding(start = 12.dp),
                    color = fs.on3,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.W600,
                )
            }
        }

        // «О программе» прижата к нижнему краю шторки (распорка забирает свободное место)
        Spacer(Modifier.weight(1f))
        ActionRow(
            icon = Icons.Filled.Info,
            label = s.aboutSection,
            onClick = { aboutOpen = true },
        )
        if (aboutOpen) {
            AboutDialog(onDismiss = { aboutOpen = false })
        }
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

/** Диалог «О программе»: версия, автор, лицензия, ссылки на проект и атрибуции. */
@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val fs = Fs.c
    val s = LocalStrings.current
    val context = LocalContext.current
    fun open(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // адаптивную иконку painterResource не грузит — собираем из цвета и PNG-глифа
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF0E3DB)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text("Folderito", color = fs.on, fontSize = 18.sp, fontWeight = FontWeight.W800)
                    Text(
                        "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        color = fs.on3,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.W600,
                    )
                }
            }
        },
        text = {
            Column {
                Text(
                    s.aboutAuthor,
                    Modifier.padding(bottom = 6.dp),
                    color = fs.on2,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.W600,
                )
                AboutLink(s.aboutLicense) {
                    open("https://github.com/petervasiliev-design/Folderito/blob/main/LICENSE")
                }
                AboutLink(s.aboutSource) {
                    open("https://github.com/petervasiliev-design/Folderito")
                }
                AboutLink(s.aboutPrivacy) {
                    open("https://github.com/petervasiliev-design/Folderito/blob/main/PRIVACY.md")
                }
                AboutLink(s.vinylCredit) {
                    open("https://ru.pngtree.com/freepng/vintage-vinyl-record-cutout_21097493.html")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(s.aboutClose, color = fs.accent, fontWeight = FontWeight.W800)
            }
        },
        containerColor = fs.surf,
    )
}

/** Ссылка в диалоге «О программе». */
@Composable
private fun AboutLink(label: String, onClick: () -> Unit) {
    val fs = Fs.c
    Text(
        label,
        Modifier
            .padding(top = 8.dp)
            .clickable(onClick = onClick),
        color = fs.accent,
        fontSize = 13.sp,
        fontWeight = FontWeight.W700,
    )
}
