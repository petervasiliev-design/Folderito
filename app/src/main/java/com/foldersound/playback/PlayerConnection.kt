package com.foldersound.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.foldersound.FsApp
import com.foldersound.data.Track
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Подключение UI к PlaybackService через MediaController.
 * Живёт столько же, сколько приложение.
 *
 * Система (особенно Samsung Freecess) может убить сервис, когда тот не в
 * foreground (пауза/пустая очередь) — тогда сессия умирает, а контроллер
 * становится «зомби»: UI застывает на старом состоянии, все команды теряются.
 * Поэтому разрыв отслеживается и контроллер пересобирается автоматически.
 */
class PlayerConnection(context: Context) {

    private val appContext = context.applicationContext
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var reconnectJob: Job? = null

    companion object {
        const val EXTERNAL_ID = "external"
    }

    val ready = MutableStateFlow(false)
    val isPlaying = MutableStateFlow(false)
    val currentMediaId = MutableStateFlow<String?>(null)
    val shuffle = MutableStateFlow(false)
    val repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)

    /** Очередь воспроизведения: id треков в порядке очереди. */
    val queue = MutableStateFlow<List<Long>>(emptyList())

    /** Показываемый трек, когда играют файл вне библиотеки (открытие извне). */
    val externalTrack = MutableStateFlow<Track?>(null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Контроллер, только пока подключение живо; иначе null (команды не идут). */
    private fun ctrl(): MediaController? = controller?.takeIf { it.isConnected }

    /** Слушатель жизненного цикла контроллера: разрыв → пересборка. */
    private val sessionListener = object : MediaController.Listener {
        override fun onDisconnected(controller: MediaController) {
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            delay(300) // дать сервису перезапуститься
            try {
                controller?.release()
            } catch (_: Exception) {
            }
            controller = null
            future = null
            ready.value = false
            connect()
        }
    }

    private fun refreshQueue() {
        val c = ctrl() ?: return
        val ids = ArrayList<Long>(c.getMediaItemCount())
        for (i in 0 until c.getMediaItemCount()) {
            c.getMediaItemAt(i).mediaId.toLongOrNull()?.let { ids.add(it) }
        }
        queue.value = ids
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying.value = playing
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            currentMediaId.value = mediaItem?.mediaId
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            refreshQueue()
        }

        override fun onShuffleModeEnabledChanged(enabled: Boolean) {
            shuffle.value = enabled
            persistFlags(enabled, repeatMode.value)
        }

        override fun onRepeatModeChanged(mode: Int) {
            repeatMode.value = mode
            persistFlags(shuffle.value, mode)
        }
    }

    private fun persistFlags(shuffle: Boolean, repeat: Int) {
        val settings = (appContext.applicationContext as? FsApp)?.settings ?: return
        scope.launch { settings.savePlaybackFlags(shuffle, repeat) }
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val f = MediaController.Builder(appContext, token)
            .setListener(sessionListener)
            .buildAsync()
        future = f
        f.addListener({
            val c = try {
                f.get()
            } catch (e: Exception) {
                null
            }
            if (c != null) {
                controller = c
                c.addListener(listener)
                isPlaying.value = c.isPlaying
                currentMediaId.value = c.currentMediaItem?.mediaId
                shuffle.value = c.shuffleModeEnabled
                repeatMode.value = c.repeatMode
                refreshQueue()
                ready.value = true
            } else {
                // биндинг не удался — повторяем, биндинг сам стартует сервис
                scheduleReconnect()
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    fun release() {
        reconnectJob?.cancel()
        reconnectJob = null
        try {
            controller?.release()
        } catch (_: Exception) {
        }
        controller = null
        future = null
        ready.value = false
    }

    fun position(): Long = ctrl()?.currentPosition ?: 0L

    fun duration(): Long = ctrl()?.duration?.takeIf { it > 0 } ?: 0L

    /** Очередь из треков; воспроизведение начинается с startIndex. */
    fun playQueue(tracks: List<Track>, startIndex: Int) {
        val c = ctrl() ?: return
        if (tracks.isEmpty()) return
        externalTrack.value = null
        val items = tracks.map { it.toMediaItem() }
        c.setMediaItems(items, startIndex.coerceIn(0, items.size - 1), 0)
        c.prepare()
        c.play()
    }

    /** Открытие трека из MediaStore по id (например, из системного выбора). */
    fun playById(trackId: Long) {
        val c = ctrl() ?: return
        externalTrack.value = null
        val uri = android.content.ContentUris.withAppendedId(
            android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, trackId
        )
        val item = MediaItem.Builder()
            .setMediaId(trackId.toString())
            .setUri(uri)
            .build()
        c.setMediaItems(listOf(item), 0, 0)
        c.prepare()
        c.play()
    }

    /** Проигрывание файла вне библиотеки (content://file:// от проводника). */
    fun playExternal(
        uri: android.net.Uri,
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
    ) {
        val c = ctrl() ?: return
        externalTrack.value = Track(
            id = Long.MIN_VALUE,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            path = "",
            folderPath = "",
            albumId = -1L,
        )
        val item = MediaItem.Builder()
            .setMediaId(EXTERNAL_ID)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .build()
            )
            .build()
        c.setMediaItems(listOf(item), 0, 0)
        c.prepare()
        c.play()
    }

    /** Трек для отображения: из библиотеки по id либо внешний фолбэк. */
    fun displayTrack(lib: com.foldersound.data.Library?): Track? {
        val id = currentMediaId.value?.toLongOrNull()
        if (id != null && lib?.trackById?.containsKey(id) == true) return lib.trackById[id]
        if (currentMediaId.value == EXTERNAL_ID) return externalTrack.value
        return null
    }

    fun togglePlay() {
        val c = ctrl() ?: return
        if (c.mediaItemCount == 0) return
        c.playWhenReady = !c.playWhenReady
    }

    /** Переключиться на позицию в очереди (индекс в порядке очереди). */
    fun playAt(queueIndex: Int) {
        val c = ctrl() ?: return
        if (queueIndex < 0 || queueIndex >= c.mediaItemCount) return
        c.seekTo(queueIndex, 0)
        c.play()
    }

    /** Полная остановка (играющий трек сняли с выбора): стоп, очередь пустая,
     *  сохранённая позиция сброшена — плеер не должен помнить старую песню. */
    fun stopPlayback() {
        val c = ctrl() ?: return
        // Пауза ДО stop(): Media3 при stop() не доставляет onIsPlayingChanged(false)
        // — без этого UI навсегда оставался в «играющем» состоянии (кнопка «Пауза»)
        c.pause()
        c.stop()
        c.clearMediaItems()
        // конечное состояние известно точно — синхронизируем независимо от событий
        isPlaying.value = false
        val settings = (appContext.applicationContext as? FsApp)?.settings ?: return
        scope.launch { settings.savePlayback(-1L, 0L) }
    }

    /** Подготовить очередь, НЕ запуская воспроизведение:
     *  плеер покажет первый трек на паузе (после остановки играющего). */
    fun prepareQueue(tracks: List<Track>) {
        val c = ctrl() ?: return
        if (tracks.isEmpty()) {
            stopPlayback()
            return
        }
        val items = tracks.map { it.toMediaItem() }
        c.setMediaItems(items, 0, 0)
        c.playWhenReady = false
        c.prepare()
    }

    /** Следующий трек; на последнем — с начала очереди (как в макете). */
    fun next() {
        val c = ctrl() ?: return
        if (c.mediaItemCount == 0) return
        if (c.hasNextMediaItem()) {
            c.seekToNextMediaItem()
        } else {
            c.seekTo(0, 0)
        }
    }

    /** Предыдущий: если прошло >3 с — в начало трека, иначе — назад. */
    fun previous() {
        val c = ctrl() ?: return
        if (c.mediaItemCount == 0) return
        if (position() > 3000 || !c.hasPreviousMediaItem()) {
            c.seekTo(0)
        } else {
            c.seekToPreviousMediaItem()
        }
    }

    fun seekToFraction(fraction: Float) {
        val c = ctrl() ?: return
        val d = duration()
        if (d > 0) c.seekTo((fraction.coerceIn(0f, 1f) * d).toLong())
    }

    /** Относительная перемотка на deltaMs (может быть отрицательной). */
    fun seekBy(deltaMs: Long) {
        val c = ctrl() ?: return
        val d = duration()
        if (d <= 0) return
        c.seekTo((c.currentPosition + deltaMs).coerceIn(0L, d))
    }

    fun setShuffle(enabled: Boolean) {
        ctrl()?.shuffleModeEnabled = enabled
    }

    /** Цикл режимов повтора: выкл → все → один трек → выкл. */
    fun cycleRepeat() {
        val c = ctrl() ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
}

private fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .build()
    )
    .build()
