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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldersound.data.Track
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
    val lib by vm.library.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()

    val connection = vm.connection
    val isPlaying by connection.isPlaying.collectAsStateWithLifecycle()
    val currentMediaId by connection.currentMediaId.collectAsStateWithLifecycle()
    val currentId = currentMediaId?.toLongOrNull()
    val currentTrack = connection.displayTrack(lib)

    var position by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isPlaying, currentMediaId) {
        while (true) {
            position = connection.position()
            delay(500)
        }
    }

    // «Список песен» = текущий выбор галочками (живьём): 20 в дереве — 20 здесь
    val tracks = remember(lib, selected) {
        lib?.allTracks?.filter { selected.contains(it.id) } ?: emptyList()
    }

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
                Icons.Filled.ClearAll,
                s.clearAll,
                vm::clearSelection,
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
                                    // играть весь выбранный список с этой песни
                                    vm.playFrom(tracks, tracks.indexOf(track))
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
    }
}
