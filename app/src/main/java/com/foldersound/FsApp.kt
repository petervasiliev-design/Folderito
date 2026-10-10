package com.foldersound

import android.app.Application
import com.foldersound.data.MusicRepository
import com.foldersound.data.SettingsStore
import com.foldersound.playback.AudioEffects
import com.foldersound.playback.PlayerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FsApp : Application() {

    lateinit var repository: MusicRepository
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var playerConnection: PlayerConnection
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        repository = MusicRepository(this)
        settings = SettingsStore(this)
        playerConnection = PlayerConnection(this)

        appScope.launch {
            AudioEffects.state.value = settings.loadEffects()
            AudioEffects.state.collect { settings.saveEffects(it) }
        }

        playerConnection.connect()
    }
}
