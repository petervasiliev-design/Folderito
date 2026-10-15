package com.foldersound.ui.songs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldersound.data.Track
import com.foldersound.ui.common.ConfirmDialog
import com.foldersound.ui.common.horizontalSwipes
import com.foldersound.ui.i18n.LocalStrings
import com.foldersound.ui.library.LibraryViewModel
import com.foldersound.ui.library.MiniPlayer
import com.foldersound.ui.library.TrackRowView
import com.foldersound.ui.theme.Fs
import kotlinx.coroutines.delay

/**
 * «Список песен»: все треки библиотеки одним плоским списком по алфавиту.
 * Тап по строке играет весь список с этого трека; выбор — как на основном экране.
 */
@Composable
fun SongsScreen(
    vm: LibraryViewModel,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    fromPlayer: Boolean = false,
) {
    val fs = Fs.c
    val s = LocalStrings.current
    // видимая библиотека — без скрытых папок (списки), полная — для мини-плеера,
    // чтобы играющий трек скрытой папки не исчезал
    val rawLib by vm.library.collectAsStateWithLifecycle()
    val lib by vm.visibleLibrary.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()

    val connection = vm.connection
    val isPlaying by connection.isPlaying.collectAsStateWithLifecycle()
    val currentMediaId by connection.currentMediaId.collectAsStateWithLifecycle()
    val currentId = currentMediaId?.toLongOrNull()
    val currentTrack = connection.displayTrack(rawLib)
    val queueIds by connection.queue.collectAsStateWithLifecycle()

    var position by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isPlaying, currentMediaId) {
        while (true) {
            position = connection.position()
            delay(500)
        }
    }

    // «Список песен» = текущий выбор галочками (живьём): 20 в дереве — 20 здесь.
    // Без выделения, но с играющей очередью (быстрое проигрывание папки) —
    // показываем песни этой папки из очереди, в её текущем порядке.
    val queueMode = selected.isEmpty() && queueIds.isNotEmpty()
    val tracks = remember(lib, selected, queueIds) {
        when {
            selected.isNotEmpty() ->
                lib?.allTracks?.filter { selected.contains(it.id) } ?: emptyList()
            queueMode ->
                queueIds.mapNotNull { id -> lib?.trackById?.get(id) }
            else -> emptyList()
        }
    }

    var confirmClear by remember { mutableStateOf(false) }
    var saveDialog by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(fs.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            // свайп вправо — возврат (симметрично свайпу влево из плеера)
            .horizontalSwipes(onSwipeRight = onBack)
    ) {
        // шапка
        Row(
            Modifier.padding(start = 4.dp, end = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.foldersound.ui.common.IconBtn(
                Icons.AutoMirrored.Filled.ArrowBack,
                if (fromPlayer) s.backToPlayer else s.backToLibrary,
                onBack,
            )
            Text(
                s.songsList,
                Modifier.weight(1f),
                color = fs.on2,
                fontSize = 12.sp,
                fontWeight = FontWeight.W800,
                letterSpacing = 2.2.sp,
                textAlign = TextAlign.Center,
            )
            com.foldersound.ui.common.IconBtn(
                Icons.AutoMirrored.Filled.PlaylistAdd,
                s.savePlaylist,
                // сохраняет показанный список: выделение, а без выделения — очередь
                onClick = if (tracks.isEmpty()) ({ /* сохранять нечего */ }) else ({ saveError = false; saveDialog = true }),
            )
            com.foldersound.ui.common.IconBtn(
                Icons.Filled.Cancel,
                s.clearAll,
                onClick = { confirmClear = true },
            )
        }

        Box(Modifier.weight(1f)) {
            when {
                tracks.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        s.selectionEmpty,
                        Modifier.padding(horizontal = 40.dp),
                        color = fs.on3,
                        fontWeight = FontWeight.W700,
                        textAlign = TextAlign.Center,
                    )
                }
                else -> {
                    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 120.dp + navBottom),
                    ) {
                        items(tracks, key = { it.id }) { track ->
                            TrackRowView(
                                track = track,
                                isCurrent = track.id == currentId,
                                subText = "${track.artist.ifBlank { s.unknownArtist }} · ${track.album.ifBlank { s.noAlbum }}",
                                query = "",
                                onToggleSel = null,
                                playOnLongPress = true,
                                onPlay = {
                                    if (queueMode) {
                                        // играть текущую очередь с этой песни — без пересборки
                                        connection.playAt(queueIds.indexOf(track.id))
                                    } else {
                                        // играть весь выбранный список с этой песни
                                        vm.playFrom(tracks, tracks.indexOf(track))
                                    }
                                },
                                isPlaying = isPlaying,
                            )
                        }
                    }
                }
            }

            // панель плеера внизу (галочек и панели выбора на этом экране нет)
            if (currentTrack != null) {
                MiniPlayer(
                    track = currentTrack,
                    isPlaying = isPlaying,
                    fraction = if (connection.duration() > 0)
                        position.toFloat() / connection.duration() else 0f,
                    onOpen = onOpenPlayer,
                    onPlayPause = connection::togglePlay,
                    onNext = connection::next,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(start = 12.dp, end = 12.dp, bottom = 16.dp)
                        .fillMaxWidth(),
                )
            }
        }

        if (confirmClear) {
            ConfirmDialog(
                title = s.clearAll,
                text = s.clearAllConfirm,
                confirmLabel = s.clear,
                cancelLabel = s.cancel,
                onConfirm = {
                    confirmClear = false
                    vm.clearSelection()
                },
                onDismiss = { confirmClear = false },
            )
        }

        if (saveDialog) {
            SavePlaylistDialog(
                error = saveError,
                onDismiss = { saveDialog = false },
                onSave = { name ->
                    vm.savePlaylist(name, tracks) { ok ->
                        if (ok) saveDialog = false else saveError = true
                    }
                },
            )
        }
    }
}

/** Диалог сохранения плейлиста: поле имени с автофокусом, «Сохранить»/«Отмена». */
@Composable
private fun SavePlaylistDialog(
    error: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val fs = Fs.c
    val s = LocalStrings.current
    var name by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(s.savePlaylist, color = fs.on, fontSize = 17.sp, fontWeight = FontWeight.W800)
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(s.playlistName) },
                isError = error,
                supportingText = if (error) {
                    { Text(s.saveFailed) }
                } else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onSave(name) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name) },
                enabled = name.isNotBlank(),
            ) {
                Text(s.save, color = fs.accent, fontWeight = FontWeight.W800)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(s.cancel, color = fs.on2, fontWeight = FontWeight.W700)
            }
        },
        containerColor = fs.surf,
    )
}
