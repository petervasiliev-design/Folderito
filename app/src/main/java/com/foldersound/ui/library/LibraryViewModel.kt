package com.foldersound.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.foldersound.FsApp
import com.foldersound.data.FolderNode
import com.foldersound.data.Library
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
        viewModelScope.launch {
            // восстановление состояния прошлой сессии
            expanded.value = fsApp.settings.loadExpanded()
            selected.value = fsApp.settings.loadSelected()
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
            connection.prepareQueue(tracksOf(next))
        } else if (next.isNotEmpty() &&
            connection.currentMediaId.value == null && queueEmpty
        ) {
            // плеер пуст (после «Очистить всё» или без сохранённого трека), а выбор
            // появился: наполнить плеер первым треком на паузе — иначе экран плеера
            // открыт, но все кнопки при пустой очереди молча игнорируют тапы
            connection.prepareQueue(tracksOf(next))
        }
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

    fun selectedTracks(): List<Track> {
        val sel = selected.value
        return _library.value?.allTracks?.filter { sel.contains(it.id) } ?: emptyList()
    }

    /** Кнопка «Слушать выбранное» на панели выбора. Выбор сохраняется —
     *  он же «билет» для навигации к плееру. */
    fun playSelected() {
        val list = selectedTracks()
        if (list.isEmpty()) return
        connection.playQueue(list, 0)
    }

    fun playFrom(list: List<Track>, index: Int) {
        connection.playQueue(list, index)
    }
}
