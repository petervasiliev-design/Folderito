package com.foldersound.data

import android.content.ContentValues
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

    /**
     * Сохраняет плейлист файлом .m3u в общий каталог Playlists/ (через MediaStore —
     * без разрешений на Q+; сканер плейлистов находит его в разделе «Плейлисты»).
     * При совпадении имён создаёт копию: «Имя», «Имя (2)», «Имя (3)»…
     * Возвращает фактическое имя сохранённого файла (без расширения) или null при ошибке.
     */
    suspend fun savePlaylist(name: String, tracks: List<Track>): String? = withContext(Dispatchers.IO) {
        val safe = name.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim()
        if (safe.isEmpty() || tracks.isEmpty()) return@withContext null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            savePlaylistMediaStore(safe, tracks)
        } else {
            savePlaylistLegacy(safe, tracks)
        }
    }

    @Suppress("DEPRECATION") // DISPLAY_NAME у плейлистов работает, но помечен устаревшим
    private fun savePlaylistMediaStore(safe: String, tracks: List<Track>): String? {
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Playlists.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        // имена уже занятых плейлистов — для суффикса « (2)», « (3)»…
        val existing = HashSet<String>()
        try {
            resolver.query(
                collection,
                arrayOf(MediaStore.Audio.Playlists.DISPLAY_NAME),
                null, null, null,
            )?.use { c ->
                val iName = c.getColumnIndexOrThrow(MediaStore.Audio.Playlists.DISPLAY_NAME)
                while (c.moveToNext()) {
                    existing.add(c.getString(iName)?.trim()?.lowercase() ?: "")
                }
            }
        } catch (_: Exception) {
            // список имён недоступен — просто пробуем вставить, MediaStore сам разрулит коллизию
        }
        var candidate = safe
        var n = 2
        while ((candidate + ".m3u").lowercase() in existing) {
            candidate = "$safe ($n)"
            n++
        }

        val values = ContentValues().apply {
            put(MediaStore.Audio.Playlists.DISPLAY_NAME, "$candidate.m3u")
            // на Q+ каталог Playlists недоступен: MediaStore разрешает [Music, Movies]
            put(MediaStore.Audio.Playlists.RELATIVE_PATH, "Music/Плейлисты")
            put(MediaStore.Audio.Playlists.IS_PENDING, 1)
        }
        return try {
            val item = resolver.insert(collection, values) ?: return null
            resolver.openOutputStream(item)?.use { stream ->
                stream.write(playlistContent(tracks).toByteArray(Charsets.UTF_8))
            } ?: return null
            values.clear()
            values.put(MediaStore.Audio.Playlists.IS_PENDING, 0)
            resolver.update(item, values, null, null)
            candidate
        } catch (_: Exception) {
            null
        }
    }

    /** До Android 10 — прямая запись; без WRITE_EXTERNAL_STORAGE вернёт null. */
    private fun savePlaylistLegacy(safe: String, tracks: List<Track>): String? {
        val dir = File("/storage/emulated/0/Playlists")
        val candidate = "$safe.m3u"
        return try {
            if (!dir.isDirectory && !dir.mkdirs()) return null
            File(dir, candidate).writeText(playlistContent(tracks), Charsets.UTF_8)
            safe
        } catch (_: Exception) {
            null
        }
    }

    /** Формат .m3u: абсолютные пути построчно (сканер сопоставляет их с треками). */
    private fun playlistContent(tracks: List<Track>): String = buildString {
        append("#EXTM3U\n")
        for (t in tracks) {
            append(t.path.trim())
            append('\n')
        }
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
