package com.foldersound.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldersound.ui.eq.EqualizerScreen
import com.foldersound.ui.i18n.AppLang
import com.foldersound.ui.theme.ThemeMode
import com.foldersound.ui.library.LibraryScreen
import com.foldersound.ui.library.LibraryViewModel
import com.foldersound.ui.player.PlayerScreen

sealed class Screen(val depth: Int) {
    data object Library : Screen(0)
    data object Player : Screen(1)
    data object Settings : Screen(1)
    data object Sleep : Screen(1)
    data object PlaybackSettings : Screen(2)
    data object Songs : Screen(2)
    data object Eq : Screen(3)
}

/**
 * Навигация как в макете: плеер выезжает снизу, эквалайзер — справа.
 */
@Composable
fun AppNav(
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
) {
    val stack = remember { mutableStateListOf<Screen>(Screen.Library) }
    val current = stack.last()

    val connection = vm.connection
    val lib by vm.library.collectAsStateWithLifecycle()
    val currentMediaId by connection.currentMediaId.collectAsStateWithLifecycle()
    val externalTrack by connection.externalTrack.collectAsStateWithLifecycle()
    val track = lib?.trackById?.get(currentMediaId?.toLongOrNull() ?: -1L)
        ?: externalTrack.takeIf { currentMediaId == com.foldersound.playback.PlayerConnection.EXTERNAL_ID }
    val pathText = if (track != null && lib != null) lib!!.pathOf(track) else "—"

    // списки избранных/скрытых папок для настроек: путь → отображаемый путь
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val hidden by vm.hidden.collectAsStateWithLifecycle()
    fun displayPath(path: String): String =
        lib?.relativeSegments(path)?.joinToString(" / ") ?: path
    val favoriteFolders = favorites.map { it to displayPath(it) }.sortedBy { it.second.lowercase() }
    val hiddenFolders = hidden.map { it to displayPath(it) }.sortedBy { it.second.lowercase() }

    fun push(screen: Screen) {
        if (stack.last() != screen) stack.add(screen)
    }

    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    BackHandler(enabled = stack.size > 1) { pop() }

    AnimatedContent(
        targetState = current,
        transitionSpec = {
            val anim = tween<IntOffset>(420)
            val animF = tween<Float>(420)
            if (targetState.depth > initialState.depth) {
                // вперёд по цепочке: новый экран выезжает справа, как свайп влево
                (slideInHorizontally(anim) { it } + fadeIn(animF)) togetherWith
                    (slideOutHorizontally(anim) { -it / 4 } + fadeOut(animF))
            } else {
                // назад: верхний экран уезжает вправо, как свайп вправо
                (slideInHorizontally(anim) { -it / 4 } + fadeIn(animF)) togetherWith
                    (slideOutHorizontally(anim) { it } + fadeOut(animF))
            }
        },
        label = "nav",
    ) { screen ->
        when (screen) {
            Screen.Library -> LibraryScreen(
                vm = vm,
                themeMode = themeMode,
                onSetTheme = onSetTheme,
                langMode = langMode,
                onSetLang = onSetLang,
                onOpenPlayer = { push(Screen.Player) },
                onOpenEq = { push(Screen.Eq) },
                onOpenSleepTimer = { push(Screen.Sleep) },
                onOpenSettings = { push(Screen.Settings) },
            )
            Screen.Sleep -> com.foldersound.ui.sleep.SleepTimerScreen(
                onBack = { pop() },
            )
            Screen.Settings -> com.foldersound.ui.settings.SettingsScreen(
                onBack = { pop() },
                skinId = skinId,
                onSetSkinId = onSetSkinId,
                seekButtonsShown = seekButtonsShown,
                onSetSeekButtons = onSetSeekButtons,
                seekStepSec = seekStepSec,
                onSetSeekStep = onSetSeekStep,
                favoriteFolders = favoriteFolders,
                hiddenFolders = hiddenFolders,
                onRemoveFavorite = vm::removeFromFavorites,
                onRemoveHidden = vm::unhideFolder,
            )
            Screen.Songs -> com.foldersound.ui.songs.SongsScreen(
                vm = vm,
                onBack = { pop() },
                onOpenPlayer = { push(Screen.Player) },
                fromPlayer = stack.any { it == Screen.Player },
            )
            Screen.Player -> PlayerScreen(
                connection = connection,
                track = track,
                pathText = pathText,
                onBack = { pop() },
                onOpenEq = { push(Screen.Eq) },
                onOpenPlaybackSettings = { push(Screen.PlaybackSettings) },
                onOpenSeekSettings = { push(Screen.Settings) },
                onOpenSongs = { push(Screen.Songs) },
                seekButtonsShown = seekButtonsShown,
                seekStepSec = seekStepSec,
            )
            Screen.PlaybackSettings -> com.foldersound.ui.settings.PlaybackSettingsScreen(
                onBack = { pop() },
                speed = playbackSpeed,
                onSetSpeed = onSetPlaybackSpeed,
                clarity = clarity,
                onSetClarity = onSetClarity,
                skipSilence = skipSilence,
                onSetSkipSilence = onSetSkipSilence,
            )
            Screen.Eq -> EqualizerScreen(
                onBack = { pop() },
                fromPlayer = stack.any { it == Screen.Player },
            )
        }
    }
}
