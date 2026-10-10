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

        scope.launch {
            AudioEffects.state.collect { eqManager?.apply(it) }
        }

        // восстановление состояния прошлой сессии: трек, позиция, флаги
        scope.launch {
            val settings = (application as FsApp).settings
            val (trackId, positionMs) = settings.loadPlayback()
            if (trackId >= 0) {
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
            val (shuffle, repeatMode) = settings.loadPlaybackFlags()
            player.shuffleModeEnabled = shuffle
            player.repeatMode = repeatMode
        }

        // периодическое сохранение позиции (раз в 5 секунд, пока жив сервис)
        scope.launch {
            while (true) {
                delay(5_000)
                savePlaybackState()
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
