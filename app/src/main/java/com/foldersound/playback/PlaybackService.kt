@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.foldersound.playback

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.MediaStore
import android.view.KeyEvent
import androidx.core.content.IntentCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.foldersound.FsApp
import com.foldersound.data.FolderNode
import com.foldersound.data.Library
import com.foldersound.data.Shuffle
import com.foldersound.data.Track
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Мост UI → сервис для пропуска пауз: у MediaController в media3 1.5 нет
 * setSkipSilenceEnabled, поэтому тумблер пишет сюда, а сервис применяет к плееру.
 */
object SkipSilenceState {
    val enabled = MutableStateFlow(false)
}

/** Идентификаторы дерева библиотеки для обозревателей вроде Android Auto. */
private const val ROOT_ID = "!root!"
private const val FOLDER_PREFIX = "F:"
private const val TRACK_PREFIX = "T:"
private const val PLAYALL_PREFIX = "P:"

/**
 * Фоновый сервис воспроизведения: ExoPlayer + MediaLibrarySession (уведомление,
 * кнопки гарнитуры). Библиотечная часть отдаёт дерево папок Android Auto:
 * Auto сам рисует интерфейс и играет элементы через колбэки ниже.
 * Эквалайзер вешается на выделенную аудио-сессию.
 */
class PlaybackService : MediaLibraryService() {

    private var mediaSession: MediaLibraryService.MediaLibrarySession? = null
    private var eqManager: EqualizerManager? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Кэш библиотеки для обозрения: сканирование MediaStore недёшево для каждого тапа. */
    private var cachedLibrary: Library? = null
    private var cachedLibraryAt = 0L

    override fun onCreate() {
        super.onCreate()
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val sessionId = audioManager.generateAudioSessionId()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setWakeMode(C.WAKE_MODE_LOCAL)
            // отключились наушники/BT — пауза; позиция и так сохраняется циклом ниже
            .setHandleAudioBecomingNoisy(true)
            .build()

        if (sessionId != AudioManager.ERROR) {
            try {
                player.setAudioSessionId(sessionId)
            } catch (e: Exception) {
                // плеер создаст свою сессию; эквалайзер подключится к ней не сразу
            }
            eqManager = EqualizerManager(sessionId)
        }

        // тап по уведомлению плеера открывает приложение
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, com.foldersound.MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        mediaSession = MediaLibraryService.MediaLibrarySession.Builder(this, player, sessionCallback)
            .setSessionActivity(sessionActivity)
            .build()

        // битый/недоступный файл посреди сессии: плавно переходим к следующему треку
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // коды 2000-2999 — ошибки ввода-вывода (файл не найден, недоступен…)
                if (error.errorCode !in 2000..2999) return
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                }
            }
        })

        scope.launch {
            AudioEffects.state.collect { eqManager?.apply(it) }
        }

        // восстановление состояния прошлой сессии: очередь (если сохранена), трек, позиция
        scope.launch {
            val settings = (application as FsApp).settings
            val (trackId, positionMs) = settings.loadPlayback()
            // скорость и пропуск пауз из прошлой сессии применяются к плееру сразу
            player.setPlaybackSpeed(settings.loadPlaybackSpeed())
            player.skipSilenceEnabled = settings.loadSkipSilence()
            SkipSilenceState.enabled.value = player.skipSilenceEnabled
            scope.launch {
                SkipSilenceState.enabled.collect { player.skipSilenceEnabled = it }
            }
            val saved = if (trackId >= 0) settings.loadQueue() else null
            val restored = saved?.let { (order, _) ->
                if (!order.contains(trackId)) return@let false
                val items = order.map { id ->
                    MediaItem.Builder()
                        .setMediaId(id.toString())
                        .setUri(
                            ContentUris.withAppendedId(
                                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                            )
                        )
                        .build()
                }
                player.setMediaItems(items, order.indexOf(trackId).coerceAtLeast(0), positionMs)
                player.prepare()
                true
            } ?: false
            if (!restored && trackId >= 0) {
                try {
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, trackId
                    )
                    val item = MediaItem.Builder()
                        .setMediaId(trackId.toString())
                        .setUri(uri)
                        .build()
                    player.setMediaItem(item, positionMs)
                    player.prepare() // подготовить, но не запускать воспроизведение
                } catch (e: Exception) {
                    // трек мог исчезнуть с устройства — просто начинаем с чистого листа
                }
            }
            val (_, repeatMode) = settings.loadPlaybackFlags()
            // порядок очереди управляется приложением (физическое перемешивание)
            player.shuffleModeEnabled = false
            player.repeatMode = repeatMode
        }

        // периодическое сохранение позиции (раз в 5 секунд, пока жив сервис)
        scope.launch {
            while (true) {
                delay(5_000)
                savePlaybackState()
            }
        }

        // таймер сна: по истечении дедлайна пауза и снятие таймера
        scope.launch {
            while (true) {
                delay(500)
                val deadline = SleepTimer.deadlineMs.value ?: continue
                if (System.currentTimeMillis() >= deadline) {
                    mediaSession?.player?.pause()
                    SleepTimer.cancel()
                }
            }
        }
    }

    /**
     * Колбэки сессии: медиа-кнопки (магнитола), восстановление после смерти
     * процесса и библиотечные операции для обозревателей (Android Auto).
     */
    private val sessionCallback = object : MediaLibraryService.MediaLibrarySession.Callback {

        override fun onMediaButtonEvent(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            mediaButtonEvent: Intent,
        ): Boolean {
            if (mediaButtonEvent.action == Intent.ACTION_MEDIA_BUTTON) {
                val event = IntentCompat.getParcelableExtra(
                    mediaButtonEvent, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java
                )
                // магнитола глушит машину: STOP трактуем как паузу,
                // не разрушая сессию — BT-возврат продолжит с того же места
                if (event != null &&
                    event.action == KeyEvent.ACTION_DOWN &&
                    event.keyCode == KeyEvent.KEYCODE_MEDIA_STOP
                ) {
                    mediaSession?.player?.pause()
                    return true
                }
            }
            return super.onMediaButtonEvent(session, controllerInfo, mediaButtonEvent)
        }

        /**
         * Процесс убит системой, а магнитола/Auto нажала play: сессия пуста —
         * отдаём сохранённые очередь, трек и позицию, фреймворк запустит сам.
         */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            scope.launch {
                try {
                    val settings = (application as FsApp).settings
                    val (trackId, positionMs) = settings.loadPlayback()
                    val saved = if (trackId >= 0) settings.loadQueue() else null
                    val order = (saved?.first ?: if (trackId >= 0) listOf(trackId) else emptyList())
                        .filter { it > 0 }
                    if (!order.contains(trackId)) {
                        throw IllegalStateException("Nothing to resume")
                    }
                    val items = order.map { id ->
                        MediaItem.Builder()
                            .setMediaId(id.toString())
                            .setUri(
                                ContentUris.withAppendedId(
                                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                                )
                            )
                            .build()
                    }
                    future.set(
                        MediaSession.MediaItemsWithStartPosition(
                            items, order.indexOf(trackId), positionMs
                        )
                    )
                } catch (e: Exception) {
                    future.setException(e)
                }
            }
            return future
        }

        /** Корень обозрения: Auto показывает его как единственную «книгу» плеера. */
        override fun onGetLibraryRoot(
            mediaSession: MediaLibraryService.MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val root = MediaItem.Builder()
                .setMediaId(ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Folderito")
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .build()
                )
                .build()
            return SettableFuture.create<LibraryResult<MediaItem>>()
                .apply { set(LibraryResult.ofItem(root, params)) }
        }

        /** Дети узла: для корня — папки верхнего уровня, для папки — подпапки и треки. */
        override fun onGetChildren(
            mediaSession: MediaLibraryService.MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            scope.launch {
                try {
                    val lib = library()
                    val items = when (parentId) {
                        ROOT_ID -> lib.root.children.values.map { folderItem(it) }
                        else -> {
                            val node = lib.root.findNode(parentId.removePrefix(FOLDER_PREFIX))
                                ?: throw IllegalStateException("Folder not found: $parentId")
                            // «играть всю папку» первым пунктом + подпапки + треки
                            listOf(playAllItem(node)) +
                                node.children.values.map { folderItem(it) } +
                                node.tracks.filter { !it.missing }.map { trackItem(it) }
                        }
                    }
                    future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
                } catch (t: Throwable) {
                    future.setException(t)
                }
            }
            return future
        }

        /** Один элемент по идентификатору — так обозреватели уточняют метаданные. */
        override fun onGetItem(
            mediaSession: MediaLibraryService.MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val future = SettableFuture.create<LibraryResult<MediaItem>>()
            scope.launch {
                try {
                    val lib = library()
                    val item = when {
                        mediaId == ROOT_ID -> rootItem()
                        mediaId.startsWith(FOLDER_PREFIX) ->
                            lib.root.findNode(mediaId.removePrefix(FOLDER_PREFIX))
                                ?.let { folderItem(it) }
                        mediaId.startsWith(PLAYALL_PREFIX) ->
                            lib.root.findNode(mediaId.removePrefix(PLAYALL_PREFIX))
                                ?.let { playAllItem(it) }
                        mediaId.startsWith(TRACK_PREFIX) ->
                            lib.trackById[mediaId.removePrefix(TRACK_PREFIX).toLongOrNull()]
                                ?.let { trackItem(it) }
                        else -> null
                    } ?: throw IllegalStateException("Item not found: $mediaId")
                    future.set(LibraryResult.ofItem(item, null))
                } catch (t: Throwable) {
                    future.setException(t)
                }
            }
            return future
        }

        /**
         * Auto нажал «играть» на папке или треке: элементы приходят без URI —
         * разворачиваем их в очередь (папка целиком, старт на выбранном треке).
         * При включённом рандоме папка играет перемешанной со случайной песни;
         * тап по треку держит его первым, дальше — перемешанный остаток.
         * Элементы приложения с URI (обычный путь) не трогаем.
         */
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            items: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            if (items.none { it.localConfiguration == null }) {
                return super.onSetMediaItems(mediaSession, controller, items, startIndex, startPositionMs)
            }
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            scope.launch {
                try {
                    val lib = library()

                    // голосовой запрос из Auto/ассистента: requestMetadata.searchQuery
                    val query = items.firstNotNullOfOrNull { it.requestMetadata?.searchQuery }
                        ?.trim().orEmpty()
                    if (query.isNotBlank()) {
                        val matches = lib.allTracks
                            .filter { !it.missing }
                            .filter {
                                it.title.contains(query, true) ||
                                    it.artist.contains(query, true) ||
                                    it.album.contains(query, true)
                            }
                            .take(25)
                        if (matches.isEmpty()) throw IllegalStateException("No search results")
                        future.set(
                            MediaSession.MediaItemsWithStartPosition(
                                matches.map { it.playableItem() }, 0, startPositionMs
                            )
                        )
                        return@launch
                    }

                    val clicked = items.first { it.localConfiguration == null }.mediaId
                    val shuffleOn = (application as FsApp).settings.loadPlaybackFlags().first
                    val picked: Pair<List<Track>, Int> = when {
                        clicked.startsWith(TRACK_PREFIX) -> {
                            val track = lib.trackById[clicked.removePrefix(TRACK_PREFIX).toLongOrNull()]
                                ?: throw IllegalStateException("Track not found: $clicked")
                            val folderQueue = queueOfFolderContaining(lib, track)
                            if (shuffleOn) Shuffle.shuffleQueue(folderQueue, track.id) to 0
                            else folderQueue to folderQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                        }
                        clicked.startsWith(FOLDER_PREFIX) -> {
                            val node = lib.root.findNode(clicked.removePrefix(FOLDER_PREFIX))
                                ?: throw IllegalStateException("Folder not found: $clicked")
                            val folderQueue = node.allTracks.filter { !it.missing }
                            // папка целиком без якоря: перемешанный список со случайной песни
                            if (shuffleOn) Shuffle.shuffleQueue(folderQueue) to 0
                            else folderQueue to 0
                        }
                        // «▶ …» — играть папку с первого трека
                        clicked.startsWith(PLAYALL_PREFIX) -> {
                            val node = lib.root.findNode(clicked.removePrefix(PLAYALL_PREFIX))
                                ?: throw IllegalStateException("Folder not found: $clicked")
                            val folderQueue = node.allTracks.filter { !it.missing }
                            if (shuffleOn) Shuffle.shuffleQueue(folderQueue) to 0
                            else folderQueue to 0
                        }
                        else -> throw IllegalStateException("Unsupported media id: $clicked")
                    }
                    future.set(
                        MediaSession.MediaItemsWithStartPosition(
                            picked.first.map { it.playableItem() }, picked.second, startPositionMs
                        )
                    )
                } catch (t: Throwable) {
                    future.setException(t)
                }
            }
            return future
        }

        /** Дозапись в очередь: «голые» элементы разворачиваем по одному. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            items: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            if (items.none { it.localConfiguration == null }) {
                return super.onAddMediaItems(mediaSession, controller, items)
            }
            val future = SettableFuture.create<MutableList<MediaItem>>()
            scope.launch {
                try {
                    val lib = library()
                    val out = mutableListOf<MediaItem>()
                    items.forEach { item ->
                        when {
                            item.localConfiguration != null -> out.add(item)
                            item.mediaId.startsWith(TRACK_PREFIX) -> {
                                val track = lib.trackById[item.mediaId.removePrefix(TRACK_PREFIX).toLongOrNull()]
                                    ?: throw IllegalStateException("Track not found: ${item.mediaId}")
                                out.add(track.playableItem())
                            }
                            item.mediaId.startsWith(FOLDER_PREFIX) -> {
                                val node = lib.root.findNode(item.mediaId.removePrefix(FOLDER_PREFIX))
                                    ?: throw IllegalStateException("Folder not found: ${item.mediaId}")
                                node.allTracks.filter { !it.missing }
                                    .forEach { out.add(it.playableItem()) }
                            }
                            item.mediaId.startsWith(PLAYALL_PREFIX) -> {
                                val node = lib.root.findNode(item.mediaId.removePrefix(PLAYALL_PREFIX))
                                    ?: throw IllegalStateException("Folder not found: ${item.mediaId}")
                                node.allTracks.filter { !it.missing }
                                    .forEach { out.add(it.playableItem()) }
                            }
                            else -> throw IllegalStateException("Unsupported media id: ${item.mediaId}")
                        }
                    }
                    future.set(out)
                } catch (t: Throwable) {
                    future.setException(t)
                }
            }
            return future
        }
    }

    /** Библиотека с кэшем на 30 секунд и вычтенными скрытыми папками. */
    private suspend fun library(): Library {
        cachedLibrary?.takeIf { System.currentTimeMillis() - cachedLibraryAt < 30_000 }
            ?.let { return it }
        val app = application as FsApp
        val raw = app.repository.loadLibrary()
        val hidden = app.settings.loadHidden()
        val root = filterHidden(raw.root, hidden)
        val filtered = Library(root, raw.rootPath, root.allTracks, root.foldersCount, raw.totalMs)
        cachedLibrary = filtered
        cachedLibraryAt = System.currentTimeMillis()
        return filtered
    }

    /** Рекурсивная копия дерева без скрытых папок — как в интерфейсе приложения. */
    private fun filterHidden(node: FolderNode, hidden: Set<String>): FolderNode {
        val copy = FolderNode(node.path, node.name)
        node.children.values.forEach { child ->
            if (child.path !in hidden) copy.children[child.path] = filterHidden(child, hidden)
        }
        copy.tracks.addAll(node.tracks)
        return copy
    }

    private fun FolderNode.findNode(path: String): FolderNode? {
        if (this.path == path) return this
        children.values.firstNotNullOfOrNull { it.findNode(path) }?.let { return it }
        return null
    }

    /** Очередь папки, содержащей трек: контекст трека в Auto — его папка. */
    private fun queueOfFolderContaining(lib: Library, track: Track): List<Track> {
        val node = lib.root.findNode(track.folderPath)
            ?: throw IllegalStateException("Folder not found: ${track.folderPath}")
        return node.allTracks.filter { !it.missing }
    }

    private fun rootItem(): MediaItem = MediaItem.Builder()
        .setMediaId(ROOT_ID)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle("Folderito")
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .build()
        )
        .build()

    private fun folderItem(node: FolderNode): MediaItem = MediaItem.Builder()
        .setMediaId(FOLDER_PREFIX + node.path)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(node.name)
                .setIsBrowsable(true)
                // папка НЕ играемая: тап открывает её, играть — пункт «▶ …» или треки
                .setIsPlayable(false)
                .setFolderType(MediaMetadata.FOLDER_TYPE_MIXED)
                .build()
        )
        .build()

    /** Псевдо-пункт «играть всю папку»: тап по нему запускает очередь папки. */
    private fun playAllItem(node: FolderNode): MediaItem = MediaItem.Builder()
        .setMediaId(PLAYALL_PREFIX + node.path)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle("▶ ${node.name}")
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build()
        )
        .build()

    private fun trackItem(track: Track): MediaItem = MediaItem.Builder()
        .setMediaId(TRACK_PREFIX + track.id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
                .setArtworkUri(track.albumArtUri)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .build()
        )
        .build()

    /** Играемый элемент из трека: с URI и метаданными для уведомления и Auto. */
    private fun Track.playableItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(albumArtUri)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build()
        )
        .build()

    private suspend fun savePlaybackState() {
        val item = mediaSession?.player?.currentMediaItem ?: return
        val id = item.mediaId.toLongOrNull() ?: return
        (application as FsApp).settings.savePlayback(id, mediaSession?.player?.currentPosition ?: 0L)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        // финальное сохранение позиции перед смертью сервиса
        val pos = mediaSession?.player?.currentPosition
        val id = mediaSession?.player?.currentMediaItem?.mediaId?.toLongOrNull()
        if (id != null && pos != null) {
            val app = application as FsApp
            // onDestroy без корутин — блокируем короткую запись DataStore
            kotlinx.coroutines.runBlocking {
                app.settings.savePlayback(id, pos)
            }
        }
        scope.cancel()
        eqManager?.release()
        eqManager = null
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
