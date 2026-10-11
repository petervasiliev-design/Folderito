package com.foldersound.data

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Collator
import java.util.Locale

/**
 * Сканирует аудиотеку устройства через MediaStore и строит дерево папок,
 * как в макете: раскрывающиеся папки + треки корня.
 */
class MusicRepository(private val context: Context) {

    private val collator = Collator.getInstance(Locale("ru"))

    @Suppress("DEPRECATION") // колонка DATA объявлена устаревшей с API 29, но для чтения работает
    suspend fun loadLibrary(): Library = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DISPLAY_NAME,
        )
        context.contentResolver.query(
            collection,
            projection,
            "${MediaStore.Audio.Media.DURATION} > ?",
            arrayOf("0"),
            null,
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val iData = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val iName = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            while (c.moveToNext()) {
                val path = c.getString(iData) ?: continue
                if (path.isBlank() || !path.contains('/')) continue
                val fileName = c.getString(iName)
                val title = c.getString(iTitle)
                    ?.takeIf { it.isNotBlank() }
                    ?: fileName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
                    ?: ""
                // локализуемые подписи («Неизвестный исполнитель» и т.п.)
                // подставляются на отображении из словаря
                val artist = c.getString(iArtist)
                    ?.takeIf { it.isNotBlank() && it != "<unknown>" }
                    ?: ""
                val album = c.getString(iAlbum)
                    ?.takeIf { it.isNotBlank() && it != "<unknown>" }
                    ?: ""
                // MediaStore бывает не в курсе удалённых файлов: помечаем «битые»,
                // они остаются видны в библиотеке, но не играются (PlayerConnection)
                val missing = !File(path).exists()
                tracks += Track(
                    id = c.getLong(iId),
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = c.getLong(iDur),
                    path = path,
                    folderPath = path.substringBeforeLast('/'),
                    albumId = c.getLong(iAlbumId),
                    missing = missing,
                )
            }
        }

        val sorted = tracks.sortedWith { a, b -> collator.compare(a.title, b.title) }
        val library = buildLibrary(sorted)
        val playlists = loadPlaylists(library) // уже внутри Dispatchers.IO
        Library(
            root = library.root,
            rootPath = library.rootPath,
            allTracks = library.allTracks,
            foldersCount = library.foldersCount,
            totalMs = library.totalMs,
            playlists = playlists,
        )
    }

    /**
     * Плейлисты: один запрос MediaStore.Files на файлы m3u и m3u8 (без обхода
     * каталогов), затем разбор каждого файла и сопоставление путей с треками.
     */
    private fun loadPlaylists(library: Library): List<Playlist> {
        if (library.allTracks.isEmpty()) return emptyList()
        val paths = mutableListOf<String>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }
        try {
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.Files.FileColumns.DATA),
                "${MediaStore.Files.FileColumns.DATA} LIKE ? OR ${MediaStore.Files.FileColumns.DATA} LIKE ?",
                arrayOf("%.m3u", "%.m3u8"),
                null,
            )?.use { c ->
                val iData = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                while (c.moveToNext()) {
                    val p = c.getString(iData)
                    if (!p.isNullOrBlank()) paths.add(p)
                }
            }
        } catch (e: Exception) {
            return emptyList() // таблица Files недоступна — плейлисты просто не показываем
        }

        // нормализованный путь → трек: регистр и «file://» не важны
        val byPath = HashMap<String, Track>(library.allTracks.size * 2)
        for (t in library.allTracks) byPath[t.path.trim().lowercase()] = t

        val out = ArrayList<Playlist>()
        for (filePath in paths) {
            val file = File(filePath)
            if (!file.isFile) continue
            val resolved = ArrayList<Track>()
            val seen = HashSet<Long>()
            try {
                file.useLines { lines ->
                    for (raw in lines) {
                        val line = raw.trim()
                        if (line.isEmpty() || line.startsWith("#")) continue
                        val p = line.removePrefix("file://")
                        val normalized = when {
                            p.startsWith("/") -> p
                            // Windows-путь из плейлиста с ПК — отбрасываем диск
                            p.length > 2 && p[1] == ':' -> null
                            else -> file.parent + "/" + p.removePrefix("./")
                        } ?: continue
                        val track = byPath[normalized.trim().lowercase()] ?: continue
                        if (seen.add(track.id)) resolved.add(track)
                    }
                }
            } catch (e: Exception) {
                continue // битый файл пропускаем целиком
            }
            if (resolved.isEmpty()) continue
            val name = file.nameWithoutExtension
            out.add(
                Playlist(
                    id = filePath.lowercase().hashCode().toLong() and 0x7FFFFFFFL,
                    name = name,
                    path = filePath,
                    tracks = resolved,
                )
            )
        }
        out.sortWith { a, b -> collator.compare(a.name, b.name) }
        return out
    }

    private fun buildLibrary(sortedTracks: List<Track>): Library {
        val rootPath = commonRootPath(sortedTracks)
        val prefixSegments = rootPath.split('/').filter { it.isNotBlank() }
        val root = FolderNode("", "Моя музыка")
        for (t in sortedTracks) {
            var node = root
            var cur = ""
            val segments = t.folderPath.split('/').filter { it.isNotBlank() }
            // общий предшественник (например /storage/emulated/0) сворачивается в корень
            var skip = 0
            while (skip < prefixSegments.size && skip < segments.size &&
                segments[skip] == prefixSegments[skip]
            ) skip++
            for (segment in segments.drop(skip)) {
                cur = if (cur.isEmpty()) segment else "$cur/$segment"
                node = node.children.getOrPut(cur) { FolderNode(cur, segment) }
            }
            node.tracks += t
        }
        sortChildrenRecursively(root)
        return Library(
            root = root,
            rootPath = rootPath,
            allTracks = root.allTracks,
            foldersCount = root.foldersCount,
            totalMs = sortedTracks.sumOf { it.durationMs },
        )
    }

    /** Папки на каждом уровне — по имени (учёт русской локали и цифр). */
    private fun sortChildrenRecursively(node: FolderNode) {
        if (node.children.size > 1) {
            val sorted = node.children.values.sortedWith { a, b -> collator.compare(a.name, b.name) }
            node.children.clear()
            sorted.forEach { node.children[it.path] = it }
        }
        node.children.values.forEach { sortChildrenRecursively(it) }
    }

    private fun commonRootPath(tracks: List<Track>): String {
        if (tracks.isEmpty()) return "/storage/emulated/0"
        var prefix = tracks.first().folderPath.split('/').filter { it.isNotBlank() }
        for (t in tracks.drop(1)) {
            val segs = t.folderPath.split('/').filter { it.isNotBlank() }
            var i = 0
            while (i < prefix.size && i < segs.size && prefix[i] == segs[i]) i++
            prefix = prefix.subList(0, i)
            if (prefix.isEmpty()) break
        }
        return if (prefix.isEmpty()) "" else "/" + prefix.joinToString("/")
    }
}
