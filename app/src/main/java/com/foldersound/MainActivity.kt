package com.foldersound

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.foldersound.ui.icons.FsIcons
import com.foldersound.ui.AppNav
import com.foldersound.ui.i18n.AppLang
import com.foldersound.ui.i18n.LocalStrings
import com.foldersound.ui.i18n.effectiveLangCode
import com.foldersound.ui.i18n.stringsFor
import com.foldersound.ui.library.LibraryViewModel
import com.foldersound.ui.theme.Fs
import com.foldersound.ui.theme.FsTheme
import com.foldersound.ui.theme.Skins
import com.foldersound.ui.theme.ThemeMode
import com.foldersound.playback.AudioEffects
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: LibraryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val fsApp = application as FsApp

        handleViewIntent(intent)
        handleSearchIntent(intent)

        setContent {
            val themeMode by fsApp.settings.themeMode
                .collectAsStateWithLifecycle(ThemeMode.SYSTEM)
            val skinId by fsApp.settings.skinId
                .collectAsStateWithLifecycle(Skins.CLASSIC_ID)
            val langMode by fsApp.settings.appLang
                .collectAsStateWithLifecycle(AppLang.SYSTEM)
            val langCode = remember(langMode) { effectiveLangCode(langMode) }
            val seekButtons by fsApp.settings.seekButtonsShown
                .collectAsStateWithLifecycle(true)
            val seekStep by fsApp.settings.seekStepSec
                .collectAsStateWithLifecycle(10)
            val playbackSpeed by fsApp.settings.playbackSpeed
                .collectAsStateWithLifecycle(1f)
            val eqState by AudioEffects.state
                .collectAsStateWithLifecycle(AudioEffects.Settings())
            val skipSilence by fsApp.settings.skipSilence
                .collectAsStateWithLifecycle(false)
            CompositionLocalProvider(LocalStrings provides stringsFor(langCode)) {
                FsTheme(themeMode, skinId) {
                    AppRoot(
                        vm = vm,
                        themeMode = themeMode,
                        onSetTheme = { mode ->
                            lifecycleScope.launch { fsApp.settings.setThemeMode(mode) }
                        },
                        skinId = skinId,
                        onSetSkinId = { id ->
                            lifecycleScope.launch { fsApp.settings.setSkinId(id) }
                        },
                        langMode = langMode,
                        onSetLang = { lang ->
                            lifecycleScope.launch { fsApp.settings.setAppLang(lang) }
                        },
                        seekButtonsShown = seekButtons,
                        onSetSeekButtons = { shown ->
                            lifecycleScope.launch { fsApp.settings.setSeekButtonsShown(shown) }
                        },
                        seekStepSec = seekStep,
                        onSetSeekStep = { sec ->
                            lifecycleScope.launch { fsApp.settings.setSeekStepSec(sec) }
                        },
                        playbackSpeed = playbackSpeed,
                        onSetPlaybackSpeed = { speed ->
                            fsApp.playerConnection.setPlaybackSpeed(speed)
                            lifecycleScope.launch { fsApp.settings.savePlaybackSpeed(speed) }
                        },
                        clarity = eqState.clarity,
                        onSetClarity = { on -> AudioEffects.setClarity(on) },
                        skipSilence = skipSilence,
                        onSetSkipSilence = { on ->
                            fsApp.playerConnection.setSkipSilence(on)
                            lifecycleScope.launch { fsApp.settings.saveSkipSilence(on) }
                        },
                        onPermissionGranted = { vm.load() },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleViewIntent(intent)
        handleSearchIntent(intent)
    }

    /** Голосовой запрос из Android Auto/ассистента: пересылаем в сессию плеера. */
    private fun handleSearchIntent(intent: android.content.Intent?) {
        if (intent?.action != "android.media.action.MEDIA_PLAY_FROM_SEARCH") return
        val query = intent.getStringExtra("query")?.trim()
            .takeUnless { it.isNullOrEmpty() } ?: return
        (application as FsApp).playerConnection.playFromSearch(query)
    }

    /** Открытие аудиофайла извне (проводник/галерея/«Открыть с помощью»). */
    private fun handleViewIntent(intent: android.content.Intent?) {
        if (intent?.action != android.content.Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        val fsApp = application as FsApp
        val connection = fsApp.playerConnection

        // content://media/external/audio/media/<id> — трек из MediaStore
        val segments = uri.pathSegments
        if (uri.host?.contains("media") == true && segments.size >= 2) {
            segments.last().toLongOrNull()?.let { id ->
                connection.playById(id)
                return
            }
        }
        // прочие uri (SAF, file://) — достаём метаданные и играем напрямую
        var title = uri.lastPathSegment ?: ""
        val dot = title.lastIndexOf('.')
        if (dot > 0) title = title.substring(0, dot)
        var artist = ""
        var album = ""
        var duration = 0L
        try {
            val mmr = android.media.MediaMetadataRetriever()
            mmr.setDataSource(this, uri)
            mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }?.let { title = it }
            mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() }?.let { artist = it }
            mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() }?.let { album = it }
            duration = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            mmr.release()
        } catch (e: Exception) {
            // без метаданных сыграем хотя бы с именем файла
        }
        connection.playExternal(uri, title, artist, album, duration)
    }
}

/* ==================== корень: разрешения + навигация ==================== */

@Composable
private fun AppRoot(
    vm: LibraryViewModel,
    themeMode: ThemeMode,
    onSetTheme: (ThemeMode) -> Unit,
    skinId: String,
    onSetSkinId: (String) -> Unit,
    langMode: AppLang,
    onSetLang: (AppLang) -> Unit,
    seekButtonsShown: Boolean,
    onSetSeekButtons: (Boolean) -> Unit,
    seekStepSec: Int,
    onSetSeekStep: (Int) -> Unit,
    playbackSpeed: Float,
    onSetPlaybackSpeed: (Float) -> Unit,
    clarity: Boolean,
    onSetClarity: (Boolean) -> Unit,
    skipSilence: Boolean,
    onSetSkipSilence: (Boolean) -> Unit,
    onPermissionGranted: () -> Unit,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasAudioPermission(context)) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val audioOk = grants[requiredAudioPermission()] == true
        granted = audioOk
        if (audioOk) onPermissionGranted()
    }

    // доступ могли выдать в настройках системы: при возврате в приложение
    // перечитываем состояние и библиотеку
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val now = hasAudioPermission(context)
                if (now && !granted) onPermissionGranted()
                granted = now
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // приложение доступно и без разрешения: системный запрос показываем
    // только по кнопке баннера, сам не выскакиваем при каждом запуске
    Box(Modifier.fillMaxSize()) {
        AppNav(
            vm = vm,
            themeMode = themeMode,
            onSetTheme = onSetTheme,
            skinId = skinId,
            onSetSkinId = onSetSkinId,
            langMode = langMode,
            onSetLang = onSetLang,
            seekButtonsShown = seekButtonsShown,
            onSetSeekButtons = onSetSeekButtons,
            seekStepSec = seekStepSec,
            onSetSeekStep = onSetSeekStep,
            playbackSpeed = playbackSpeed,
            onSetPlaybackSpeed = onSetPlaybackSpeed,
            clarity = clarity,
            onSetClarity = onSetClarity,
            skipSilence = skipSilence,
            onSetSkipSilence = onSetSkipSilence,
            accessBanner = {
                if (!granted) {
                    AccessBanner(
                        onRequest = { launcher.launch(requiredPermissions()) },
                        onOpenSettings = ::openAppSettings,
                    )
                }
            },
        )
    }
}

private fun hasAudioPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, requiredAudioPermission()) ==
        PackageManager.PERMISSION_GRANTED

private fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        )
    )
}

private fun requiredAudioPermission(): String =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

private fun requiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS,
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

/* ==================== баннер «нет доступа» ==================== */

/**
 * Карточка поверх библиотеки, когда доступ к музыке не выдан. Приложение
 * при этом остаётся полностью доступным: библиотека пустая, всё остальное
 * работает. Системный диалог — только по кнопке «Разрешить доступ».
 */
@Composable
private fun AccessBanner(onRequest: () -> Unit, onOpenSettings: (Context) -> Unit) {
    val fs = Fs.c
    val s = LocalStrings.current
    val context = LocalContext.current
    var dismissed by rememberSaveable { mutableStateOf(false) }
    if (dismissed) return

    Card(
        Modifier
            .statusBarsPadding()
            .padding(top = 64.dp, start = 16.dp, end = 16.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = fs.surf2),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    FsIcons.Folder,
                    contentDescription = null,
                    tint = fs.on3,
                    modifier = Modifier.size(26.dp),
                )
                Text(
                    s.needAccess,
                    Modifier
                        .padding(start = 10.dp)
                        .weight(1f),
                    color = fs.on,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.W800,
                )
                IconButton(onClick = { dismissed = true }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = s.aboutClose,
                        tint = fs.on3,
                    )
                }
            }
            Text(
                s.permText,
                Modifier.padding(top = 6.dp),
                color = fs.on2,
                fontSize = 13.sp,
                fontWeight = FontWeight.W600,
            )
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Button(
                    onClick = onRequest,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = fs.primary,
                        contentColor = fs.onAccent,
                    ),
                ) {
                    Icon(Icons.Filled.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("  " + s.allowAccess, fontWeight = FontWeight.W800)
                }
                TextButton(onClick = { onOpenSettings(context) }) {
                    Text(s.openSettings, color = fs.accent, fontWeight = FontWeight.W800)
                }
            }
            Text(
                s.permHint,
                Modifier.padding(top = 10.dp),
                color = fs.on3,
                fontSize = 12.sp,
                fontWeight = FontWeight.W600,
            )
        }
    }
}
