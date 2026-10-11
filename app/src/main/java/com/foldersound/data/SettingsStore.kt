package com.foldersound.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.foldersound.playback.AudioEffects
import com.foldersound.ui.i18n.AppLang
import com.foldersound.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "foldersound_settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val SKIN = stringPreferencesKey("skin")
        val LANG = stringPreferencesKey("lang")
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val EQ_PRESET = stringPreferencesKey("eq_preset")
        val EQ_BANDS = stringPreferencesKey("eq_bands")
        val EQ_BASS = intPreferencesKey("eq_bass")
        val EQ_SURROUND = intPreferencesKey("eq_surround")
        val EXPANDED = stringSetPreferencesKey("expanded_folders")
        val SELECTED = stringSetPreferencesKey("selected_tracks")
        val PB_TRACK = longPreferencesKey("pb_track_id")
        val PB_POS = longPreferencesKey("pb_position_ms")
        val PB_QUEUE = stringPreferencesKey("pb_queue")
        val PB_QUEUE_BASE = stringPreferencesKey("pb_queue_base")
        val SHUFFLE = booleanPreferencesKey("shuffle")
        val REPEAT = intPreferencesKey("repeat_mode")
        val SEEK_BUTTONS = booleanPreferencesKey("seek_buttons")
        val SEEK_STEP = intPreferencesKey("seek_step")
    }

    /** Русские имена пресетов до перехода на id — для миграции настроек. */
    private val legacyPresetNames = mapOf(
        "Обычный" to "normal",
        "Рок" to "rock",
        "Поп" to "pop",
        "Джаз" to "jazz",
        "Классика" to "classical",
        "Электроника" to "electronic",
        "Хип-хоп" to "hiphop",
        "Бас+" to "bass",
        "Свои" to "custom",
    )

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { p ->
        when (p[Keys.THEME]) {
            "light" -> ThemeMode.LIGHT
            "dark" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit {
            it[Keys.THEME] = when (mode) {
                ThemeMode.SYSTEM -> "system"
                ThemeMode.LIGHT -> "light"
                ThemeMode.DARK -> "dark"
            }
        }
    }

    /** Выбранный скин (цветовая схема); "classic" — стандартная схема приложения. */
    val skinId: Flow<String> = context.dataStore.data.map { p ->
        p[Keys.SKIN] ?: com.foldersound.ui.theme.Skins.CLASSIC_ID
    }

    suspend fun setSkinId(id: String) {
        context.dataStore.edit { it[Keys.SKIN] = id }
    }

    val appLang: Flow<AppLang> = context.dataStore.data.map { p ->
        AppLang.fromCode(p[Keys.LANG])
    }

    suspend fun setAppLang(lang: AppLang) {
        context.dataStore.edit { it[Keys.LANG] = lang.code }
    }

    /** Показывать ли кнопки перемотки ±N с в плеере (по умолчанию — да). */
    val seekButtonsShown: Flow<Boolean> = context.dataStore.data.map { it[Keys.SEEK_BUTTONS] ?: true }

    suspend fun setSeekButtonsShown(shown: Boolean) {
        context.dataStore.edit { it[Keys.SEEK_BUTTONS] = shown }
    }

    /** Шаг перемотки в секундах, 5..60 (по умолчанию 10). */
    val seekStepSec: Flow<Int> = context.dataStore.data.map { (it[Keys.SEEK_STEP] ?: 10).coerceIn(5, 60) }

    suspend fun setSeekStepSec(sec: Int) {
        context.dataStore.edit { it[Keys.SEEK_STEP] = sec.coerceIn(5, 60) }
    }

    /* ==================== состояние сессии ==================== */

    /** Раскрытые папки (пути). */
    suspend fun saveExpanded(paths: Set<String>) {
        context.dataStore.edit { it[Keys.EXPANDED] = paths }
    }

    suspend fun loadExpanded(): Set<String> =
        context.dataStore.data.first()[Keys.EXPANDED] ?: emptySet()

    /** Выделенные треки (id из MediaStore). */
    suspend fun saveSelected(ids: Set<Long>) {
        context.dataStore.edit { it[Keys.SELECTED] = ids.map(Long::toString).toSet() }
    }

    suspend fun loadSelected(): Set<Long> =
        context.dataStore.data.first()[Keys.SELECTED]
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet() ?: emptySet()

    /** Текущий трек и позиция в нём. */
    suspend fun savePlayback(trackId: Long, positionMs: Long) {
        context.dataStore.edit {
            it[Keys.PB_TRACK] = trackId
            it[Keys.PB_POS] = positionMs
        }
    }

    suspend fun loadPlayback(): Pair<Long, Long> {
        val p = context.dataStore.data.first()
        val id = p[Keys.PB_TRACK] ?: -1L
        val pos = p[Keys.PB_POS] ?: 0L
        return id to pos
    }

    /**
     * Очередь воспроизведения: физический порядок (при перемешивании — перемешанный)
     * и линейный порядок до перемешивания. Восстанавливается при старте сервиса,
     * чтобы рандом продолжал цепочку после перезапуска приложения.
     */
    suspend fun saveQueue(order: List<Long>, base: List<Long>) {
        context.dataStore.edit {
            it[Keys.PB_QUEUE] = order.joinToString(",")
            it[Keys.PB_QUEUE_BASE] = base.joinToString(",")
        }
    }

    /** null — сохранённой очереди нет. */
    suspend fun loadQueue(): Pair<List<Long>, List<Long>>? {
        val p = context.dataStore.data.first()
        val order = p[Keys.PB_QUEUE]?.split(',')?.mapNotNull { it.toLongOrNull() }.orEmpty()
        if (order.isEmpty()) return null
        val base = p[Keys.PB_QUEUE_BASE]?.split(',')?.mapNotNull { it.toLongOrNull() }.orEmpty()
        return order to base
    }

    suspend fun clearQueue() {
        context.dataStore.edit {
            it.remove(Keys.PB_QUEUE)
            it.remove(Keys.PB_QUEUE_BASE)
        }
    }

    /** Флаги перемешивания и повтора. */
    suspend fun savePlaybackFlags(shuffle: Boolean, repeatMode: Int) {
        context.dataStore.edit {
            it[Keys.SHUFFLE] = shuffle
            it[Keys.REPEAT] = repeatMode
        }
    }

    suspend fun loadPlaybackFlags(): Pair<Boolean, Int> {
        val p = context.dataStore.data.first()
        return (p[Keys.SHUFFLE] ?: false) to (p[Keys.REPEAT] ?: 0)
    }

    suspend fun loadEffects(): AudioEffects.Settings {
        val p = context.dataStore.data.first()
        val defaults = AudioEffects.Settings()
        val preset = legacyPresetNames[p[Keys.EQ_PRESET]] ?: p[Keys.EQ_PRESET] ?: defaults.preset
        val bands = p[Keys.EQ_BANDS]
            ?.takeIf { it.isNotBlank() }
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.takeIf { it.isNotEmpty() }
            ?: (AudioEffects.PRESET_BANDS[preset] ?: defaults.bands)
        return AudioEffects.Settings(
            enabled = p[Keys.EQ_ENABLED] ?: defaults.enabled,
            preset = preset,
            bands = bands,
            bass = p[Keys.EQ_BASS] ?: defaults.bass,
            surround = p[Keys.EQ_SURROUND] ?: defaults.surround,
        )
    }

    suspend fun saveEffects(s: AudioEffects.Settings) {
        context.dataStore.edit {
            it[Keys.EQ_ENABLED] = s.enabled
            it[Keys.EQ_PRESET] = s.preset
            it[Keys.EQ_BANDS] = s.bands.joinToString(",")
            it[Keys.EQ_BASS] = s.bass
            it[Keys.EQ_SURROUND] = s.surround
        }
    }
}
