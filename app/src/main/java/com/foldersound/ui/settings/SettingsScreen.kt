package com.foldersound.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.foldersound.ui.theme.FsColors
import com.foldersound.ui.theme.Skins
import kotlin.math.roundToInt

/**
 * Экран «Настройки»: язык, цветовая схема, кнопки перемотки и их шаг (5–60 с).
 * Плюс списки избранных и скрытых папок с удалением (дерево перерисовывается само).
 * Таймер сна вынесен на отдельный экран (шторка → «Таймер сна»).
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    skinId: String,
    onSetSkinId: (String) -> Unit,
    seekButtonsShown: Boolean,
    onSetSeekButtons: (Boolean) -> Unit,
    seekStepSec: Int,
    onSetSeekStep: (Int) -> Unit,
    favoriteFolders: List<Pair<String, String>> = emptyList(),
    hiddenFolders: List<Pair<String, String>> = emptyList(),
    onRemoveFavorite: (String) -> Unit = {},
    onRemoveHidden: (String) -> Unit = {},
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
                s.settings,
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

            // цветовая схема (скин)
            SectionLabel(s.skinSection, Modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp))
            Column(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(fs.surf)
                    .padding(vertical = 6.dp),
            ) {
                SkinRow(
                    label = s.skinClassic,
                    swatch = null,
                    selected = skinId == Skins.CLASSIC_ID,
                    onClick = { onSetSkinId(Skins.CLASSIC_ID) },
                )
                val langRu = s.langCode == "ru"
                for (skin in Skins.all) {
                    SkinRow(
                        label = if (langRu) skin.nameRu else skin.nameEn,
                        swatch = skin.colors,
                        selected = skinId == skin.id,
                        onClick = { onSetSkinId(skin.id) },
                    )
                }
            }

            SectionLabel(s.seekButtonsLabel, Modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp))

            // показать/скрыть кнопки перемотки в плеере
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(fs.surf)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        s.seekButtonsLabel,
                        color = fs.on,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.W700,
                    )
                    Text(
                        "±$seekStepSec ${s.secShort}",
                        Modifier.padding(top = 2.dp),
                        color = fs.on2,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.W600,
                    )
                }
                FsSwitch(checked = seekButtonsShown, onChange = onSetSeekButtons)
            }

            // шаг перемотки: слайдер 5..60 с
            Column(
                Modifier
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(fs.surf)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        s.seekStepLabel,
                        Modifier.weight(1f),
                        color = fs.on,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.W700,
                    )
                    Text(
                        "$seekStepSec ${s.secShort}",
                        color = fs.accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.W800,
                    )
                }
                var dragging by remember { mutableStateOf(false) }
                var dragValue by remember { mutableStateOf(seekStepSec) }
                HSlider(
                    fraction = if (dragging) (dragValue - 5) / 55f else (seekStepSec - 5) / 55f,
                    onChange = { f ->
                        dragging = true
                        dragValue = 5 + (f * 55).roundToInt()
                        onSetSeekStep(dragValue)
                    },
                    onDragEnd = { dragging = false },
                )
                Text(
                    s.seekHoldHint,
                    Modifier.padding(top = 6.dp),
                    color = fs.on3,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.W600,
                )
            }

            // избранные и скрытые папки: подсказка о добавлении, удаление из
            // списка перерисовывает дерево; пустой список подписан
            SectionLabel(s.favFoldersSection, Modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp), icon = Icons.Filled.Star)
            Column(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(fs.surf)
                    .padding(vertical = 6.dp),
            ) {
                Text(
                    s.favFoldersHint,
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = fs.on3,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.W600,
                )
                if (favoriteFolders.isEmpty()) {
                    Text(
                        s.listEmpty,
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = fs.on3,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.W600,
                    )
                } else {
                    favoriteFolders.forEach { (path, label) ->
                        FolderListRow(label) { onRemoveFavorite(path) }
                    }
                }
            }

            SectionLabel(s.hiddenFoldersSection, Modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp), icon = Icons.Filled.VisibilityOff)
            Column(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(fs.surf)
                    .padding(vertical = 6.dp),
            ) {
                Text(
                    s.hiddenFoldersHint,
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = fs.on3,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.W600,
                )
                if (hiddenFolders.isEmpty()) {
                    Text(
                        s.listEmpty,
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = fs.on3,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.W600,
                    )
                } else {
                    hiddenFolders.forEach { (path, label) ->
                        FolderListRow(label) { onRemoveHidden(path) }
                    }
                }
            }

            // защита от энергосберегающих оптимизаций (критично для Xiaomi/Huawei)
            val context = LocalContext.current
            val powerManager = remember {
                context.getSystemService(Context.POWER_SERVICE) as PowerManager
            }
            if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(fs.surf)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            try {
                                context.startActivity(
                                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                        .setData(Uri.parse("package:${context.packageName}"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } catch (_: Exception) {
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.BatteryAlert,
                        contentDescription = null,
                        tint = fs.accent,
                        modifier = Modifier.size(22.dp),
                    )
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            s.batteryTitle,
                            color = fs.on,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.W700,
                        )
                        Text(
                            s.batteryHint,
                            Modifier.padding(top = 2.dp),
                            color = fs.on2,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.W600,
                        )
                    }
                }
            }
        }
    }
}

/** Строка списка избранных/скрытых папок: путь и кнопка удаления. */
@Composable
private fun FolderListRow(label: String, onRemove: () -> Unit) {
    val fs = Fs.c
    val s = LocalStrings.current
    Row(
        Modifier
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Folder,
            contentDescription = null,
            tint = fs.on3,
            modifier = Modifier.size(20.dp),
        )
        Text(
            label,
            Modifier
                .weight(1f)
                .padding(start = 12.dp),
            color = fs.on,
            fontSize = 14.sp,
            fontWeight = FontWeight.W600,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        IconBtn(
            Icons.Filled.Cancel,
            s.clear,
            onRemove,
            size = 36.dp,
            iconSize = 20.dp,
            tint = fs.on3,
        )
    }
}

/** Строка выбора скина: превью-свотч (или значок для стандартной схемы), название, галочка. */
@Composable
private fun SkinRow(
    label: String,
    swatch: FsColors?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val fs = Fs.c
    Row(
        Modifier
            .padding(horizontal = 8.dp, vertical = 2.dp)
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
        if (swatch != null) {
            SkinSwatch(swatch)
        } else {
            Icon(
                Icons.Filled.Palette,
                contentDescription = null,
                tint = if (selected) fs.accent else fs.on3,
                modifier = Modifier.size(22.dp),
            )
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

/** Мини-превью схемы: фон скина + кружки primary/secondary/accent. */
@Composable
private fun SkinSwatch(c: FsColors) {
    Box(
        Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(c.bg)
            .border(1.dp, c.outline, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(c.primary))
            Box(Modifier.size(6.dp).clip(CircleShape).background(c.secondary))
            Box(Modifier.size(6.dp).clip(CircleShape).background(c.accent))
        }
    }
}

/** Строка выбора языка: значок-бейдж (или глобус), название, галочка при выборе. */
