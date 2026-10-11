package com.foldersound.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.outlined.KeyboardArrowRight
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldersound.ui.icons.FsIcons
import com.foldersound.ui.i18n.AppLang
import com.foldersound.ui.i18n.LocalStrings
import com.foldersound.ui.theme.ThemeMode
import com.foldersound.data.FolderNode
import com.foldersound.data.Library
import com.foldersound.data.Playlist
import com.foldersound.data.StandardFolders
import com.foldersound.data.Track
import com.foldersound.ui.common.CheckState
import com.foldersound.ui.common.ConfirmDialog
import com.foldersound.ui.common.HSlider
import com.foldersound.ui.common.IconBtn
import com.foldersound.ui.common.MetaText
import com.foldersound.ui.common.NestIndentStep
import com.foldersound.ui.common.NestTag
import com.foldersound.ui.common.PlayingBars
import com.foldersound.ui.common.SectionLabel
import com.foldersound.ui.common.TrackCover
import com.foldersound.ui.common.TriCheck
import com.foldersound.ui.common.formatMs
import com.foldersound.ui.common.highlight
import com.foldersound.ui.common.holdToggle
import com.foldersound.ui.common.horizontalSwipes
import com.foldersound.ui.common.nestLine
import com.foldersound.ui.common.rememberNestMetrics
import com.foldersound.ui.theme.Fs
import kotlinx.coroutines.delay

/* ==================== строки дерева ==================== */

private sealed interface Row {
    data class Label(val text: String) : Row
    data class FolderRow(val node: FolderNode, val depth: Int, val tag: String? = null) : Row
    data class TrackRow(
        val track: Track,
        val depth: Int,
        val tag: String? = null,
        // трек может входить в несколько плейлистов — ключ должен остаться уникальным
        val keySuffix: String = "",
    ) : Row

    /** Синтетическая папка «Плейлисты» — первая плитка корня. */
    data class PlaylistFolderRow(val playlistsCount: Int, val tracksCount: Int) : Row
    data class PlaylistRow(val playlist: Playlist, val depth: Int) : Row
}

private const val PLAYLISTS_KEY = "@playlists"

private fun buildRows(
    lib: Library,
    expanded: Set<String>,
    foldersLabel: String,
    tracksLabel: String,
): List<Row> {
    val out = mutableListOf<Row>()
    val playlists = lib.playlists
    if (playlists.isNotEmpty()) {
        out += Row.PlaylistFolderRow(
            playlistsCount = playlists.size,
            tracksCount = playlists.sumOf { it.tracks.size },
        )
        if (expanded.contains(PLAYLISTS_KEY)) {
            playlists.forEach { playlist ->
                out += Row.PlaylistRow(playlist, depth = 1)
                if (expanded.contains(playlist.path)) {
                    // подпись на черте — только у блоков с достаточным числом строк (≥3)
                    val tag = if (playlist.tracks.size >= 3) playlist.name else null
                    playlist.tracks.forEach {
                        out += Row.TrackRow(it, depth = 2, tag = tag, keySuffix = "#p${playlist.path}")
                    }
                }
            }
        }
    }
    out += flatten(lib.root, expanded, 0, true, foldersLabel, tracksLabel)
    return out
}

private fun flatten(
    node: FolderNode,
    expanded: Set<String>,
    depth: Int,
    isRoot: Boolean,
    foldersLabel: String,
    tracksLabel: String,
): List<Row> {
    val out = mutableListOf<Row>()
    if (isRoot && node.children.isNotEmpty()) out += Row.Label(foldersLabel)
    node.children.values.forEach { child ->
        out += Row.FolderRow(child, depth)
        if (expanded.contains(child.path)) {
            val inner = flatten(child, expanded, depth + 1, false, foldersLabel, tracksLabel)
            // подпись — только у блоков с достаточным числом элементов (≥3)
            out += if (inner.size >= 3) {
                inner.mapIndexed { index, row ->
                    if (index == 0) when (row) {
                        is Row.FolderRow -> row.copy(tag = child.name)
                        is Row.TrackRow -> row.copy(tag = child.name)
                        else -> row
                    } else row
                }
            } else {
                inner
            }
        }
    }
    if (isRoot && node.tracks.isNotEmpty()) out += Row.Label(tracksLabel)
    node.tracks.forEach { out += Row.TrackRow(it, depth) }
    return out
}

/* ==================== экран ==================== */

@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    themeMode: ThemeMode,
    onSetTheme: (ThemeMode) -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenEq: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val fs = Fs.c
    val s = LocalStrings.current
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val lib by vm.library.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val searchOpen by vm.searchOpen.collectAsStateWithLifecycle()
    val expanded by vm.expanded.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()

    val connection = vm.connection
    val isPlaying by connection.isPlaying.collectAsStateWithLifecycle()
    val currentMediaId by connection.currentMediaId.collectAsStateWithLifecycle()

    var drawerOpen by remember { mutableStateOf(false) }

    // «назад»: сначала шторка, потом поиск, потом сброс выбора
    BackHandler(enabled = !drawerOpen && (searchOpen || selected.isNotEmpty())) {
        when {
            searchOpen -> vm.openSearch(false)
            else -> vm.clearSelection()
        }
    }
    BackHandler(enabled = drawerOpen) { drawerOpen = false }

    // свайп влево → плеер, только если что-то выбрано (и шторка закрыта)
    fun tryOpenPlayer() {
        if (!drawerOpen && selected.isNotEmpty()) onOpenPlayer()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(fs.bg)
            .horizontalSwipes(onSwipeLeft = ::tryOpenPlayer)
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            AnimatedContent(
                targetState = searchOpen,
                transitionSpec = { fadeIn().togetherWith(fadeOut()) },
                label = "header",
            ) { open ->
                if (open) {
                    SearchRow(
                        query = query,
                        onQuery = { vm.query.value = it },
                        onClose = { vm.openSearch(false) },
                    )
                } else {
                    Header(
                        lib = lib,
                        onOpenMenu = { drawerOpen = true },
                        onOpenSearch = { vm.openSearch(true) },
                        onCollapseAll = vm::collapseAll,
                        onClearSelection = vm::clearSelection,
                        canGoToPlayer = selected.isNotEmpty(),
                        onOpenPlayer = ::tryOpenPlayer,
                    )
                }
            }

        Box(Modifier.weight(1f)) {
            val body: @Composable () -> Unit = {
                when {
                    lib == null -> Centered { CircularProgressIndicator(color = fs.accent) }
                    lib!!.allTracks.isEmpty() -> EmptyLibrary()
                    searchOpen && query.isNotBlank() -> SearchResults(
                        results = results,
                        query = query,
                        lib = lib!!,
                        currentId = currentMediaId?.toLongOrNull(),
                        isPlaying = isPlaying,
                    )
                    else -> TreeList(
                        lib = lib!!,
                        expanded = expanded,
                        selected = selected,
                        vm = vm,
                        currentId = currentMediaId?.toLongOrNull(),
                        isPlaying = isPlaying,
                    )
                }
            }
            Box(Modifier.fillMaxSize()) {
                body()
                // нижние панели поверх списка
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(start = 12.dp, end = 12.dp, bottom = 16.dp)
                        .fillMaxWidth()
                ) {
                    val selectedCount = selected.size
                    if (selectedCount > 0) {
                        SelectionBar(
                            count = selectedCount,
                            totalMs = vm.selectedTracks().sumOf { it.durationMs },
                            isPlaying = isPlaying,
                            onPlay = vm::playSelected,
                            onTogglePlay = connection::togglePlay,
                            onPrev = connection::previous,
                            onNext = connection::next,
                            onOpen = onOpenPlayer,
                        )
                    } else {
                        // без выбора — панель-подсказка, переходы на другие экраны закрыты
                        NothingSelectedBar()
                    }
                }
            }
        }
        } // конец Column контента

        // затемнение + шторка поверх всего экрана
        AnimatedVisibility(
            visible = drawerOpen,
            enter = fadeIn(animationSpec = tween(220)),
            exit = fadeOut(animationSpec = tween(180)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { drawerOpen = false }
            )
        }
        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInHorizontally(animationSpec = tween(260)) { -it },
            exit = slideOutHorizontally(animationSpec = tween(200)) { -it },
        ) {
            AppDrawer(
                themeMode = themeMode,
                onSetTheme = onSetTheme,
                onOpenEq = {
                    drawerOpen = false
                    onOpenEq()
                },
                onOpenSleepTimer = {
                    drawerOpen = false
                    onOpenSleepTimer()
                },
                onOpenSettings = {
                    drawerOpen = false
                    onOpenSettings()
                },
            )
        }
    }
}

/* ==================== шапка ==================== */

@Composable
private fun Header(
    lib: Library?,
    onOpenMenu: () -> Unit,
    onOpenSearch: () -> Unit,
    onCollapseAll: () -> Unit,
    onClearSelection: () -> Unit,
    canGoToPlayer: Boolean,
    onOpenPlayer: () -> Unit,
) {
    val fs = Fs.c
    val s = LocalStrings.current
    var confirmClear by remember { mutableStateOf(false) }
    Column(Modifier.padding(start = 6.dp, end = 10.dp, top = 10.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            IconBtn(Icons.Filled.Menu, s.menu, onOpenMenu)
            Column(Modifier.weight(1f)) {
                Text(
                    "Folderito",
                    fontSize = 27.sp,
                    fontWeight = FontWeight.W800,
                    letterSpacing = (-0.4).sp,
                    color = fs.on,
                )
                Row(
                    Modifier.padding(top = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        FsIcons.Folder,
                        contentDescription = null,
                        tint = fs.on3,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = buildString {
                            append(lib?.rootPath ?: "/storage/emulated/0")
                            lib?.let { l ->
                                append("  ·  ${s.plural(l.foldersCount, s.pFolders)}")
                                append("  ·  ${s.plural(l.allTracks.size, s.pTracks)}")
                                append("  ·  ${s.formatTotalMs(l.totalMs)}")
                            }
                        },
                        color = fs.on2,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.W600,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconBtn(Icons.Filled.Search, s.search, onOpenSearch)
            IconBtn(
                Icons.Filled.UnfoldLess,
                s.collapseAllFolders,
                onCollapseAll,
            )
            IconBtn(
                Icons.Filled.ClearAll,
                s.clearAll,
                onClick = { confirmClear = true },
            )
            // стрелка «к плееру» — активна только при выборе
            IconBtn(
                Icons.AutoMirrored.Filled.ArrowForward,
                s.toPlayer,
                onOpenPlayer,
                tint = if (canGoToPlayer) fs.accent else fs.on3,
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
                onClearSelection()
            },
            onDismiss = { confirmClear = false },
        )
    }
}

/** Панель-подсказка, когда выбор пуст: переходы на другие экраны закрыты. */
@Composable
private fun NothingSelectedBar() {
    val fs = Fs.c
    val s = LocalStrings.current
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(14.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(fs.surf2)
            .border(1.dp, fs.outline, RoundedCornerShape(18.dp))
            .padding(start = 12.dp, end = 12.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(fs.surf3),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.QueueMusic, contentDescription = null, tint = fs.on3, modifier = Modifier.size(22.dp))
        }
        Text(
            s.nothingSelected,
            Modifier.padding(start = 11.dp),
            color = fs.on2,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.W700,
        )
    }
}

/* ==================== поиск ==================== */

@Composable
private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

@Composable
private fun SearchRow(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val fs = Fs.c
    val s = LocalStrings.current
    Row(
        Modifier.padding(start = 6.dp, end = 6.dp, top = 2.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBtn(Icons.AutoMirrored.Filled.ArrowBack, s.closeSearch, onClose)
        Box(
            Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(fs.surf2),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(
                    color = fs.on,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.W600,
                ),
                cursorBrush = Brush.verticalGradient(listOf(fs.accent, fs.accent)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            s.searchHint,
                            color = fs.on3,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.W600,
                        )
                    }
                    inner()
                },
            )
        }
        if (query.isNotEmpty()) {
            IconBtn(Icons.Filled.Close, s.clear, { onQuery("") })
        } else {
            Spacer(Modifier.width(42.dp))
        }
    }
}

@Composable
private fun SearchResults(
    results: List<Track>,
    query: String,
    lib: Library,
    currentId: Long?,
    isPlaying: Boolean,
) {
    val fs = Fs.c
    val s = LocalStrings.current
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 120.dp + navBottom),
    ) {
        if (results.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 60.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Filled.Search, null, tint = fs.on3, modifier = Modifier.size(36.dp))
                    Text(
                        s.nothingFound,
                        Modifier.padding(top = 10.dp),
                        color = fs.on3,
                        fontWeight = FontWeight.W700,
                    )
                }
            }
        } else {
            item {
                SectionLabel(
                    "${s.found} · ${s.plural(results.size, s.pTracks)}",
                    Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
                )
            }
            items(results, key = { "s${it.id}" }) { track ->
                TrackRowView(
                    track = track,
                    isCurrent = track.id == currentId,
                    subText = "${track.artist.ifBlank { s.unknownArtist }} · ${lib.pathOf(track)}",
                    query = query,
                    onToggleSel = null,
                    onPlay = null,
                    isPlaying = isPlaying,
                )
            }
        }
    }
}

/* ==================== дерево ==================== */

@Composable
private fun TreeList(
    lib: Library,
    expanded: Set<String>,
    selected: Set<Long>,
    vm: LibraryViewModel,
    currentId: Long?,
    isPlaying: Boolean,
) {
    val fs = Fs.c
    val s = LocalStrings.current
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val rows = remember(lib, expanded, s) { buildRows(lib, expanded, s.folders, s.tracks) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 120.dp + navBottom),
    ) {
        items(rows, key = {
            when (it) {
                is Row.Label -> "l${it.text}"
                is Row.FolderRow -> "f${it.node.path}"
                is Row.TrackRow -> "t${it.track.id}${it.keySuffix}"
                is Row.PlaylistFolderRow -> "f$PLAYLISTS_KEY"
                is Row.PlaylistRow -> "p${it.playlist.path}"
            }
        }) { row ->
            when (row) {
                is Row.Label -> SectionLabel(
                    row.text,
                    Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
                )
                is Row.FolderRow -> {
                    val segs = lib?.relativeSegments(row.node.path) ?: emptyList()
                    FolderRowView(
                        node = row.node,
                        depth = row.depth,
                        tag = row.tag,
                        parentPath = if (segs.size > 1) segs.dropLast(1).joinToString(" / ") else null,
                        inMusicBranch = segs.firstOrNull()?.let { StandardFolders.isStandard(it) } == true,
                        expandedNow = expanded.contains(row.node.path),
                        checkState = vm.folderCheckState(row.node),
                        onToggleSel = { vm.toggleFolderSelection(row.node) },
                        onToggleExpand = { vm.toggleFolder(row.node.path) },
                    )
                }
                is Row.TrackRow -> TrackRowView(
                    track = row.track,
                    isCurrent = row.track.id == currentId,
                    subText = "${row.track.artist.ifBlank { s.unknownArtist }} · ${row.track.album.ifBlank { s.noAlbum }}",
                    query = "",
                    depth = row.depth,
                    tag = row.tag,
                    onToggleSel = { vm.toggleTrackSelection(row.track.id) },
                    // запуска из дерева нет — только галочки и «Слушать выбранное»
                    onPlay = null,
                    selected = selected.contains(row.track.id),
                    isPlaying = isPlaying,
                )
                is Row.PlaylistFolderRow -> PlaylistFolderRowView(
                    playlistsCount = row.playlistsCount,
                    tracksCount = row.tracksCount,
                    expandedNow = expanded.contains(PLAYLISTS_KEY),
                    onToggleExpand = { vm.toggleFolder(PLAYLISTS_KEY) },
                )
                is Row.PlaylistRow -> PlaylistRowView(
                    playlist = row.playlist,
                    depth = row.depth,
                    expandedNow = expanded.contains(row.playlist.path),
                    checkState = vm.playlistCheckState(row.playlist),
                    onToggleSel = { vm.togglePlaylistSelection(row.playlist) },
                    onToggleExpand = { vm.toggleFolder(row.playlist.path) },
                    onPlay = { vm.playPlaylist(row.playlist) },
                )
            }
        }
    }
}

/* ==================== папка ==================== */

@Composable
private fun FolderRowView(
    node: FolderNode,
    depth: Int,
    tag: String?,
    parentPath: String?,
    inMusicBranch: Boolean,
    expandedNow: Boolean,
    checkState: CheckState,
    onToggleSel: () -> Unit,
    onToggleExpand: () -> Unit,
) {
    val fs = Fs.c
    val s = LocalStrings.current
    val nest = rememberNestMetrics(tag)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val folderView = LocalView.current
    // музыкальные ветки (корень — стандартный каталог) на любой глубине вложения
    // и любые папки со стандартным именем — плитка особого (standardGrad) цвета
    val isStandard = StandardFolders.isStandard(node.name) || inMusicBranch
    val gradStart = if (isStandard) fs.standardGradStart else fs.folderGradStart
    val gradEnd = if (isStandard) fs.standardGradEnd else fs.folderGradEnd
    // zIndex: плашка подписи этой строки рисуется поверх черт нижних строк
    Box(Modifier.zIndex(if (nest != null) 1f else 0f).nestLine(depth, fs)) {
        if (nest != null) {
            NestTag(depth, nest, fs)
        }
        Row(
            Modifier
                .padding(
                    start = 10.dp + NestIndentStep * depth,
                    end = 10.dp,
                    // с путём сверху плитка сжимается по вертикали, чтобы её высота
                    // была ближе к высоте верхних папок (без третьей строки)
                    top = if (parentPath != null) 5.dp else 10.dp,
                    bottom = if (parentPath != null) 5.dp else 10.dp,
                )
                .fillMaxWidth()
                .clip(RoundedCornerShape(15.dp))
                .background(
                    if (pressed) Brush.linearGradient(
                        listOf(gradStart.copy(alpha = 0.88f), gradEnd.copy(alpha = 0.88f))
                    ) else Brush.linearGradient(listOf(gradStart, gradEnd))
                )
                .clickable(interactionSource = interaction, indication = null, onClick = onToggleExpand)
                .padding(start = 7.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        Box(
            Modifier
                .size(38.dp, 44.dp)
                .clip(CircleShape)
                // галочка — только удержанием ~1 с (защита от случайных тапов)
                .holdToggle {
                    folderView.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                    onToggleSel()
                },
            contentAlignment = Alignment.Center,
        ) {
            TriCheck(state = checkState, onFolderRow = true, modifier = Modifier.size(38.dp, 44.dp))
        }
        Icon(
            if (expandedNow) FsIcons.FolderOpen else FsIcons.Folder,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.padding(start = 2.dp).size(32.dp),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 11.dp)
        ) {
            if (parentPath != null) {
                Text(
                    parentPath,
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 10.5.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.W600,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                node.name,
                color = Color.White,
                fontSize = 15.sp,
                lineHeight = if (parentPath != null) 18.sp else TextUnit.Unspecified,
                fontWeight = FontWeight.W700,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subs = node.children.size
            val cnt = node.allTracks.size
            Text(
                text = buildString {
                    if (subs > 0) append("${s.plural(subs, s.pSubfolders)} · ")
                    append(s.plural(cnt, s.pTracks))
                },
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                lineHeight = if (parentPath != null) 13.sp else TextUnit.Unspecified,
                fontWeight = FontWeight.W600,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
            Icon(
                Icons.Outlined.KeyboardArrowRight,
                contentDescription = if (expandedNow) s.collapse else s.expand,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier
                    .size(20.dp)
                    .rotate(if (expandedNow) 90f else 0f),
            )
        }
    }
}

/* ==================== плейлисты ==================== */

/** Синтетическая плитка «Плейлисты» — первая в корне; цвет родственный музыкальным папкам. */
@Composable
private fun PlaylistFolderRowView(
    playlistsCount: Int,
    tracksCount: Int,
    expandedNow: Boolean,
    onToggleExpand: () -> Unit,
) {
    val fs = Fs.c
    val s = LocalStrings.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(Modifier.nestLine(0, fs)) {
        Row(
            Modifier
                .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(15.dp))
                .background(
                    if (pressed) Brush.linearGradient(
                        listOf(
                            fs.playlistGradStart.copy(alpha = 0.88f),
                            fs.playlistGradEnd.copy(alpha = 0.88f),
                        )
                    ) else Brush.linearGradient(listOf(fs.playlistGradStart, fs.playlistGradEnd))
                )
                .clickable(interactionSource = interaction, indication = null, onClick = onToggleExpand)
                .padding(start = 7.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (expandedNow) FsIcons.FolderOpen else FsIcons.Folder,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.padding(start = 2.dp).size(32.dp),
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 11.dp)
            ) {
                Text(
                    s.playlists,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.W700,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append(s.plural(playlistsCount, s.pPlaylists))
                        if (tracksCount > 0) append(" · ${s.plural(tracksCount, s.pTracks)}")
                    },
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W600,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.Outlined.KeyboardArrowRight,
                contentDescription = if (expandedNow) s.collapse else s.expand,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier
                    .size(20.dp)
                    .rotate(if (expandedNow) 90f else 0f),
            )
        }
    }
}

/** Плитка плейлиста: тап — раскрыть, удержание — играть, галочка удержанием — выделить. */
@Composable
private fun PlaylistRowView(
    playlist: Playlist,
    depth: Int,
    expandedNow: Boolean,
    checkState: CheckState,
    onToggleSel: () -> Unit,
    onToggleExpand: () -> Unit,
    onPlay: () -> Unit,
) {
    val fs = Fs.c
    val s = LocalStrings.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val view = LocalView.current
    Box(Modifier.nestLine(depth, fs)) {
        Row(
            Modifier
                .padding(
                    start = 10.dp + NestIndentStep * depth,
                    end = 10.dp,
                    top = 2.dp,
                    bottom = 2.dp,
                )
                .fillMaxWidth()
                .clip(RoundedCornerShape(15.dp))
                .background(
                    if (pressed) Brush.linearGradient(
                        listOf(
                            fs.playlistGradStart.copy(alpha = 0.88f),
                            fs.playlistGradEnd.copy(alpha = 0.88f),
                        )
                    ) else Brush.linearGradient(listOf(fs.playlistGradStart, fs.playlistGradEnd))
                )
                .clickable(interactionSource = interaction, indication = null, onClick = onToggleExpand)
                .padding(start = 7.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(38.dp, 44.dp)
                    .clip(CircleShape)
                    .holdToggle {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                        onToggleSel()
                    },
                contentAlignment = Alignment.Center,
            ) {
                TriCheck(state = checkState, onFolderRow = true, modifier = Modifier.size(38.dp, 44.dp))
            }
            Icon(
                FsIcons.SoundWave,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.padding(start = 2.dp).size(32.dp),
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 11.dp)
            ) {
                Text(
                    playlist.name,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.W700,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${s.plural(playlist.tracks.size, s.pTracks)} · ≈ ${s.formatTotalMs(playlist.totalMs)}",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W600,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.Outlined.KeyboardArrowRight,
                contentDescription = if (expandedNow) s.collapse else s.expand,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier
                    .size(20.dp)
                    .rotate(if (expandedNow) 90f else 0f),
            )
        }
        // удержание правее галочки — играть плейлист целиком; зона галочки не перекрыта
        Box(
            Modifier
                .matchParentSize()
                .padding(start = 52.dp)
                .holdToggle {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                    onPlay()
                },
        )
    }
}

/* ==================== трек ==================== */

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TrackRowView(
    track: Track,
    isCurrent: Boolean,
    subText: String,
    query: String,
    depth: Int = 0,
    tag: String? = null,
    selected: Boolean = false,
    isPlaying: Boolean = false,
    playOnLongPress: Boolean = false,
    onToggleSel: (() -> Unit)?,
    onPlay: (() -> Unit)? = null,
) {
    val fs = Fs.c
    val s = LocalStrings.current
    val nest = rememberNestMetrics(tag)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    // трек с отсутствующим файлом: приглушаем и помечаем, при запуске он пропускается
    val missing = track.missing
    // zIndex: плашка подписи этой строки рисуется поверх черт нижних строк
    Box(Modifier.zIndex(if (nest != null) 1f else 0f).nestLine(depth, fs)) {
        if (nest != null) {
            NestTag(depth, nest, fs)
        }
        Row(
            Modifier
                .padding(
                    start = 10.dp + NestIndentStep * depth,
                    end = 10.dp,
                    top = 2.dp,
                    bottom = 2.dp,
                )
                .fillMaxWidth()
                .clip(RoundedCornerShape(15.dp))
                .background(if (pressed && onPlay != null) fs.surf2 else Color.Transparent)
                .then(
                    if (onPlay == null) {
                        // пассивная строка (дерево): только галочка, запуска нет
                        Modifier
                    } else {
                        Modifier.combinedClickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = if (playOnLongPress) ({ /* тап не играет — удержание или двойной тап */ }) else onPlay,
                            onLongClick = if (playOnLongPress) {
                                {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onPlay()
                                }
                            } else null,
                            onDoubleClick = if (playOnLongPress) onPlay else null,
                        )
                    }
                )
                .padding(start = 7.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        if (onToggleSel != null) {
            Box(
                Modifier
                    .size(38.dp, 44.dp)
                    .clip(CircleShape)
                    // галочка — только удержанием ~1 с (защита от случайных тапов)
                    .holdToggle {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                        onToggleSel()
                    },
                contentAlignment = Alignment.Center,
            ) {
                TriCheck(
                    state = if (selected) CheckState.On else CheckState.Off,
                    modifier = Modifier.size(38.dp, 44.dp),
                )
            }
        }
        Box {
            TrackCover(
                artUri = track.albumArtUri,
                modifier = Modifier
                    .size(46.dp)
                    .alpha(if (missing) 0.35f else 1f)
                    .let { m ->
                        if (isCurrent) m.border(2.dp, fs.accent, RoundedCornerShape(13.dp)) else m
                    },
                corner = 13.dp,
            )
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                highlight(
                    text = track.title,
                    query = query,
                    baseColor = when {
                        isCurrent -> fs.accent
                        missing -> fs.on3
                        else -> fs.on
                    },
                    highlightColor = fs.accent,
                ),
                fontSize = 14.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MetaText(
                if (missing) "$subText · ${s.unavailable}" else subText,
                Modifier.padding(top = 1.dp),
            )
        }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (isCurrent) {
                    PlayingBars(active = isPlaying)
                } else {
                    Text(
                        formatMs(track.durationMs),
                        color = fs.on3,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.W700,
                    )
                }
            }
        }
    }
}

/* ==================== мини-плеер и панель выбора ==================== */

@Composable
fun MiniPlayer(
    track: Track,
    isPlaying: Boolean,
    fraction: Float,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fs = Fs.c
    val s = LocalStrings.current
    Column(
        modifier
            .fillMaxWidth()
            .shadow(14.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(fs.surf2)
            .border(1.dp, fs.outline, RoundedCornerShape(18.dp)),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.5.dp)
                .background(fs.surf3)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(fs.accent, fs.accent2)))
            )
        }
        Row(
            Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpen,
                )
                .padding(start = 10.dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackCover(
                artUri = track.albumArtUri,
                modifier = Modifier.size(42.dp),
                corner = 10.dp,
                noteSize = 14.dp,
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 11.dp)
            ) {
                Text(
                    track.title,
                    color = fs.on,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.W800,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                MetaText(track.artist.ifBlank { s.unknownArtist }, Modifier.padding(top = 1.dp), fontSize = 11.5f)
            }
            PlayingBars(active = isPlaying, height = 13.dp)
            IconBtn(
                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                s.playPause,
                onPlayPause,
                size = 40.dp,
                iconSize = 24.dp,
            )
            IconBtn(Icons.Filled.SkipNext, s.nextTrack, onNext, size = 40.dp, iconSize = 22.dp)
        }
    }
}

@Composable
fun SelectionBar(
    count: Int,
    totalMs: Long,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onTogglePlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit,
) {
    val fs = Fs.c
    val s = LocalStrings.current
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(14.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(fs.surf2)
            .border(1.dp, fs.outline, RoundedCornerShape(18.dp))
            // клик по корпусу панели открывает плеер (кнопки перехватывают свои тапы сами)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpen,
            )
            .padding(start = 12.dp, end = 10.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(fs.surf3),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.QueueMusic, contentDescription = null, tint = fs.accent, modifier = Modifier.size(22.dp))
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 11.dp)
        ) {
            Text(
                "${s.selectedPrefix}: ${s.plural(count, s.pTracks)}",
                color = fs.on,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.W800,
                maxLines = 1,
            )
            MetaText("≈ ${s.formatTotalMs(totalMs)}", Modifier.padding(top = 1.dp), fontSize = 11.5f)
        }
        // транспорт, как на странице плеера: назад / play-пауза / вперёд
        IconBtn(Icons.Filled.SkipPrevious, s.prevTrack, onPrev, size = 40.dp, iconSize = 22.dp)
        // единая кнопка: ▶ «Слушать выбранное» / ⏸ пауза-продолжение во время игры
        Box(
            Modifier
                .padding(start = 4.dp)
                .size(44.dp)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(fs.fabGradStart, fs.primary)))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { if (isPlaying) onTogglePlay() else onPlay() },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) s.pause else s.playSelected,
                tint = fs.onAccent,
                modifier = Modifier.size(22.dp),
            )
        }
        IconBtn(Icons.Filled.SkipNext, s.nextTrack, onNext, size = 40.dp, iconSize = 22.dp)
    }
}

/* ==================== вспомогательное ==================== */

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun EmptyLibrary() {
    val fs = Fs.c
    val s = LocalStrings.current
    Column(
        Modifier.fillMaxSize().padding(vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            FsIcons.Folder,
            contentDescription = null,
            tint = fs.on3,
            modifier = Modifier.size(36.dp),
        )
        Text(
            s.emptyLibrary,
            Modifier.padding(top = 10.dp),
            color = fs.on3,
            fontWeight = FontWeight.W700,
        )
    }
}
