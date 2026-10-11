package com.foldersound.data

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val path: String,
    val folderPath: String,
    val albumId: Long,
    /** Файл не найден на диске при сканировании: трек виден в библиотеке, но не играется. */
    val missing: Boolean = false,
) {
    val uri: Uri
        get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

    /** Обложка альбома из MediaStore (может не открыться — тогда рисуем градиент). */
    val albumArtUri: Uri
        get() = ContentUris.withAppendedId(ALBUM_ART_URI, albumId)

    companion object {
        private val ALBUM_ART_URI: Uri = Uri.parse("content://media/external/audio/albumart")
    }
}

/** Плейлист, найденный в библиотеке как файл .m3u/.m3u8. */
data class Playlist(
    val id: Long,
    val name: String,
    /** Абсолютный путь к файлу плейлиста. */
    val path: String,
    /** Треки в порядке следования в файле (несуществующие пути пропущены). */
    val tracks: List<Track>,
) {
    val totalMs: Long get() = tracks.sumOf { it.durationMs }
}

/** Узел дерева папок. path — абсолютный путь, name — имя папки. */
class FolderNode(val path: String, val name: String) {

    val children = LinkedHashMap<String, FolderNode>()
    val tracks = mutableListOf<Track>()

    private var cachedAll: List<Track>? = null

    /** Все треки подпапки: сначала собственные, затем рекурсивно вложенные. */
    val allTracks: List<Track>
        get() {
            cachedAll?.let { return it }
            val out = ArrayList<Track>(tracks)
            children.values.forEach { out.addAll(it.allTracks) }
            cachedAll = out
            return out
        }

    val foldersCount: Int
        get() = children.values.sumOf { 1 + it.foldersCount }
}

data class Library(
    val root: FolderNode,
    val rootPath: String,
    val allTracks: List<Track>,
    val foldersCount: Int,
    val totalMs: Long,
    val playlists: List<Playlist> = emptyList(),
) {
    val trackById: Map<Long, Track> = allTracks.associateBy { it.id }

    /** Отображаемый путь трека относительно корня библиотеки: «Rock / Классика рока». */
    fun pathOf(track: Track): String {
        val segments = relativeSegments(track.folderPath)
        return if (segments.isEmpty()) root.name else segments.joinToString(" / ")
    }

    /** Сегменты пути папки без общего префикса корня (например, без /storage/emulated/0). */
    fun relativeSegments(folderPath: String): List<String> {
        val prefix = rootPath.split('/').filter { it.isNotBlank() }
        val segs = folderPath.split('/').filter { it.isNotBlank() }
        var i = 0
        while (i < prefix.size && i < segs.size && segs[i] == prefix[i]) i++
        return segs.drop(i)
    }
}
