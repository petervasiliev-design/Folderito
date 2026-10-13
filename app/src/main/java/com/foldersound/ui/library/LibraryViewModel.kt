package com.foldersound.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.foldersound.FsApp
import com.foldersound.data.FolderNode
import com.foldersound.data.Library
import com.foldersound.data.Playlist
import com.foldersound.data.Track
import com.foldersound.playback.PlayerConnection
import com.foldersound.ui.common.CheckState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val fsApp = app as FsApp
    val connection: PlayerConnection = fsApp.playerConnection

    private val _library = MutableStateFlow<Library?>(null)
    val library: StateFlow<Library?> = _library

    val query = MutableStateFlow("")
    val searchOpen = MutableStateFlow(false)
    val expanded = MutableStateFlow<Set<String>>(emptySet())
    val selected = MutableStateFlow<Set<Long>>(emptySet())

    /** Короткое имя папки режима быстрого проигрывания (пусто — режим неактивен). */
    val quickFolder = MutableStateFlow("")

    /** Пути избранных папок — раздел «Избранное»; папки остаются и в основном дереве. */
    val favorites = MutableStateFlow<Set<String>>(emptySet())

    /** Пути скрытых папок — исключаются из дерева, поиска и списка песен. */
    val hidden = MutableStateFlow<Set<String>>(emptySet())

    /** Библиотека без скрытых веток — источник для дерева, поиска и «Списка песен». */
    val visibleLibrary: StateFlow<Library?> = combine(_library, hidden) { lib, h ->
        when {
            lib == null -> null
            h.isEmpty() -> lib
            else -> filterHidden(lib, h)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun filterHidden(lib: Library, hidden: Set<String>): Library {
        fun copy(node: FolderNode): FolderNode {
            val out = FolderNode(node.path, node.name)
            node.children.values.forEach { child ->
                if (child.path !in hidden) {
                    val c = copy(child)
                    out.children[c.path] = c
                }
            }
            node.tracks.forEach { if (it.folderPath !in hidden) out.tracks.add(it) }
            return out
        }
        val root = copy(lib.root)
        val all = root.allTracks
        return Library(
            root = root,
            rootPath = lib.rootPath,
            allTracks = all,
            foldersCount = root.foldersCount,
            totalMs = all.sumOf { it.durationMs },
            playlists = lib.playlists,
        )
    }

    val searchResults: StateFlow<List<Track>> = combine(query, _library) { q, lib ->
        val needle = q.trim().lowercase()
        if (needle.isEmpty()) {
            emptyList()
        } else {
            lib?.allTracks?.filter {
                "${it.title} ${it.artist} ${it.album}".lowercase().contains(needle)
            } ?: emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // резолвер для умного перемешивания (id → Track из библиотеки)
        connection.setTrackResolver { id -> _library.value?.trackById[id] }
        viewModelScope.launch {
            // восстановление состояния прошлой сессии
            expanded.value = fsApp.settings.loadExpanded()
            selected.value = fsApp.settings.loadSelected()
            favorites.value = fsApp.settings.loadFavorites()
            hidden.value = fsApp.settings.loadHidden()
            quickFolder.value = fsApp.settings.loadQuickFolder()
            load()
        }
    }

    fun load() {
        viewModelScope.launch {
            _library.value = fsApp.repository.loadLibrary()
            // как «Rock» в макете: первая папка раскрыта по умолчанию
            if (expanded.value.isEmpty()) {
                _library.value?.root?.children?.keys?.firstOrNull()?.let {
                    setExpanded(setOf(it))
                }
            }
        }
    }

    fun openSearch(open: Boolean) {
        searchOpen.value = open
        if (!open) query.value = ""
    }

    private fun setExpanded(next: Set<String>) {
        expanded.value = next
        viewModelScope.launch { fsApp.settings.saveExpanded(next) }
    }

    /** Свернуть все папки. */
    fun collapseAll() {
        setExpanded(emptySet())
    }

    fun toggleFolder(path: String) {
        val next = expanded.value.toMutableSet()
        if (!next.add(path)) next.remove(path)
        setExpanded(next)
    }

    fun folderCheckState(folder: FolderNode): CheckState {
        val all = folder.allTracks
        if (all.isEmpty()) return CheckState.Off
        val sel = selected.value
        val n = all.count { sel.contains(it.id) }
        return when {
            n == 0 -> CheckState.Off
            n == all.size -> CheckState.On
            else -> CheckState.Mixed
        }
    }

    private fun setSelected(next: Set<Long>) {
        val prev = selected.value
        selected.value = next
        viewModelScope.launch { fsApp.settings.saveSelected(next) }
        val tracksOf: (Set<Long>) -> List<Track> = { sel ->
            _library.value?.allTracks?.filter { sel.contains(it.id) } ?: emptyList()
        }
        // снятие галочки с играющего трека: показать первый трек оставшегося
        // выбора на паузе (при пустом выборе — полностью очистить плеер)
        val currentId = connection.currentMediaId.value?.toLongOrNull()
        val queueEmpty = connection.queue.value.isEmpty()
        if (currentId != null && prev.contains(currentId) && !next.contains(currentId)) {
            // очередь заменяется выбором — быстрый режим заканчивается
            setQuickFolder("")
            connection.prepareQueue(tracksOf(next))
        } else if (next.isNotEmpty() &&
            connection.currentMediaId.value == null && queueEmpty
        ) {
            // плеер пуст (после «Очистить всё» или без сохранённого трека), а выбор
            // появился: наполнить плеер первым треком на паузе — иначе экран плеера
            // открыт, но все кнопки при пустой очереди молча игнорируют тапы
            setQuickFolder("")
            connection.prepareQueue(tracksOf(next))
        }
    }

    /** Ярлык быстрого режима на панели библиотеки; пустое имя выключает режим. */
    private fun setQuickFolder(name: String) {
        if (quickFolder.value == name) return
        quickFolder.value = name
        viewModelScope.launch { fsApp.settings.saveQuickFolder(name) }
    }

    fun toggleFolderSelection(folder: FolderNode) {
        val all = folder.allTracks
        if (all.isEmpty()) return
        val next = selected.value.toMutableSet()
        if (folderCheckState(folder) == CheckState.On) {
            all.forEach { next.remove(it.id) }
        } else {
            all.forEach { next.add(it.id) }
        }
        setSelected(next)
    }

    fun toggleTrackSelection(id: Long) {
        val next = selected.value.toMutableSet()
        if (!next.add(id)) next.remove(id)
        setSelected(next)
    }

    fun clearSelection() {
        setSelected(emptySet())
    }

    /* ==================== избранное и скрытые папки ==================== */

    /** Долгий тап по папке → «Добавить в избранное». Повторное — убирает. */
    fun toggleFavorite(path: String) {
        val next = favorites.value.toMutableSet()
        if (!next.add(path)) next.remove(path)
        favorites.value = next
        viewModelScope.launch { fsApp.settings.saveFavorites(next) }
    }

    /** Удаление из списка в настройках. */
    fun removeFromFavorites(path: String) {
        if (path !in favorites.value) return
        favorites.value = favorites.value - path
        viewModelScope.launch { fsApp.settings.saveFavorites(favorites.value) }
    }

    /** «Спрятать»: папка исчезает из дерева, поиска и списка песен; из избранного убирается,
     *  её треки снимаются с выделения. */
    fun hideFolder(path: String) {
        if (path in hidden.value) return
        hidden.value = hidden.value + path
        viewModelScope.launch { fsApp.settings.saveHidden(hidden.value) }
        if (path in favorites.value) removeFromFavorites(path)
        // снять с выделения треки скрытой ветки, чтобы не висели «мертвые» галочки
        val lib = _library.value ?: return
        val gone = lib.allTracks
            .filter { it.folderPath == path || it.folderPath.startsWith("$path/") }
            .map { it.id }
            .toSet()
        if (gone.any { it in selected.value }) {
            setSelected(selected.value - gone)
        }
    }

    /** Удаление из скрытых в настройках — дерево перерисовывается само (visibleLibrary). */
    fun unhideFolder(path: String) {
        if (path !in hidden.value) return
        hidden.value = hidden.value - path
        viewModelScope.launch { fsApp.settings.saveHidden(hidden.value) }
    }

    fun selectedTracks(): List<Track> {
        val sel = selected.value
        return _library.value?.allTracks?.filter { sel.contains(it.id) } ?: emptyList()
    }

    /** Кнопка «Слушать выбранное» на панели выбора. Выбор сохраняется —
     *  он же «билет» для навигации к плееру. */
    fun playSelected() {
        val list = selectedTracks()
        if (list.isEmpty()) return
        setQuickFolder("")
        connection.playQueue(list, 0)
    }

    fun playFrom(list: List<Track>, index: Int) {
        setQuickFolder("")
        connection.playQueue(list, index)
    }

    /** Сохранить показанный список песен файлом .m3u (раздел «Плейлисты»).
     *  [onDone] = true, если файл записан; библиотека перечитывается сама. */
    fun savePlaylist(name: String, tracks: List<Track>, onDone: (Boolean) -> Unit) {
        if (tracks.isEmpty()) {
            onDone(false)
            return
        }
        viewModelScope.launch {
            val ok = fsApp.repository.savePlaylist(name, tracks) != null
            if (ok) load() // плейлист появляется в дереве без перезапуска
            onDone(ok)
        }
    }

    /** Быстрое проигрывание: двойной тап по треку дерева без выделения —
     *  играет всю папку трека с этой песни (перемешивание и повтор применяются
     *  как обычно; повтор выключен — стоп после папки). */
    fun playQuick(track: Track) {
        val lib = _library.value ?: return
        val folderTracks = lib.allTracks.filter { !it.missing && it.folderPath == track.folderPath }
        val start = folderTracks.indexOfFirst { it.id == track.id }
        if (start < 0) return
        connection.playQueue(folderTracks, start)
        setQuickFolder(track.folderPath.substringAfterLast('/'))
    }

    /* ==================== плейлисты ==================== */

    /** Удержание на плитке плейлиста: играть весь плейлист с начала. */
    fun playPlaylist(playlist: Playlist) {
        if (playlist.tracks.isEmpty()) return
        setQuickFolder("")
        connection.playQueue(playlist.tracks, 0)
    }

    /** Трёхцветный чекбокс плитки плейлиста — по составу плейлиста. */
    fun playlistCheckState(playlist: Playlist): CheckState {
        val all = playlist.tracks
        if (all.isEmpty()) return CheckState.Off
        val sel = selected.value
        val n = all.count { sel.contains(it.id) }
        return when {
            n == 0 -> CheckState.Off
            n == all.size -> CheckState.On
            else -> CheckState.Mixed
        }
    }

    /** Удержание на галочке плитки: отметить/снять весь плейлист. */
    fun togglePlaylistSelection(playlist: Playlist) {
        val next = selected.value.toMutableSet()
        if (playlistCheckState(playlist) == CheckState.On) {
            playlist.tracks.forEach { next.remove(it.id) }
        } else {
            playlist.tracks.forEach { next.add(it.id) }
        }
        setSelected(next)
    }
}
