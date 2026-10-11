package com.foldersound.playback

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.MediaStore
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.foldersound.FsApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Фоновый сервис воспроизведения: ExoPlayer + MediaSession (уведомление,
 * кнопки гарнитуры). Эквалайзер вешается на выделенную аудио-сессию.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var eqManager: EqualizerManager? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

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
            .build()

        if (sessionId != AudioManager.ERROR) {
            try {
                player.setAudioSessionId(sessionId)
            } catch (e: Exception) {
                // плеер создаст свою сессию; эквалайзер подключится к ней не сразу
            }
            eqManager = EqualizerManager(sessionId)
        }

        mediaSession = MediaSession.Builder(this, player).build()

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

    private suspend fun savePlaybackState() {
        val item = mediaSession?.player?.currentMediaItem ?: return
        val id = item.mediaId.toLongOrNull() ?: return
        (application as FsApp).settings.savePlayback(id, mediaSession?.player?.currentPosition ?: 0L)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

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
