package com.foldersound.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.log10
import kotlin.math.roundToInt

/**
 * Глобальное состояние эквалайзера (доступно и UI, и сервису).
 * bands — усиления в дБ по полосам устройства; пресеты из макета
 * (5 логических частот 60 Гц … 14 кГц) интерполируются на реальные полосы.
 */
object AudioEffects {

    data class Settings(
        val enabled: Boolean = true,
        val preset: String = "electronic",
        val bands: List<Int> = listOf(5, 1, -2, 1, 5),
        val bass: Int = 35,
        val surround: Int = 20,
        /** «Четкость речи»: голосовой пресет, перекрывает эквалайзер. */
        val clarity: Boolean = false,
    )

    data class BandInfo(
        val count: Int,
        val centerFreqs: List<Int>, // Гц
        val minDb: Int,
        val maxDb: Int,
    )

    /**
     * Пресеты — стабильные идентификаторы, отображаемые имена берутся
     * из словаря (Strings.presetNames); в настройках хранится id.
     */
    val PRESET_ORDER: List<String> = listOf(
        "normal", "rock", "pop", "jazz", "classical", "electronic", "hiphop", "bass",
    )

    val PRESET_BANDS: Map<String, List<Int>> = linkedMapOf(
        "normal" to listOf(0, 0, 0, 0, 0),
        "rock" to listOf(4, 2, -1, 2, 4),
        "pop" to listOf(-1, 3, 4, 2, -1),
        "jazz" to listOf(3, 1, -1, 1, 3),
        "classical" to listOf(4, 3, -1, 3, 4),
        "electronic" to listOf(5, 1, -2, 1, 5),
        "hiphop" to listOf(6, 3, -2, 0, 1),
        "bass" to listOf(8, 4, -1, -1, -2),
    )

    const val CUSTOM = "custom"

    /** Голосовой пресет «Четкость речи»: срез басов + подъем частот разборчивости (дБ). */
    val VOICE_BANDS = listOf(-6, -3, 4, 6, 2)

    private val LOGICAL_FREQS = listOf(60f, 230f, 910f, 3600f, 14000f)

    val state = MutableStateFlow(Settings())
    val bandInfo = MutableStateFlow<BandInfo?>(null)

    fun setEnabled(on: Boolean) {
        state.value = state.value.copy(enabled = on)
    }

    fun setPreset(id: String) {
        val preset = PRESET_BANDS[id] ?: return
        state.value = state.value.copy(preset = id, bands = mapToBands(preset))
    }

    fun setBand(index: Int, db: Int) {
        val cur = state.value
        if (index !in cur.bands.indices) return
        val next = cur.bands.toMutableList()
        next[index] = db
        state.value = cur.copy(bands = next, preset = CUSTOM)
    }

    fun setBass(percent: Int) {
        state.value = state.value.copy(bass = percent.coerceIn(0, 100))
    }

    fun setSurround(percent: Int) {
        state.value = state.value.copy(surround = percent.coerceIn(0, 100))
    }

    /** «Четкость речи»: при включении перекрывает и выключает эквалайзер. */
    fun setClarity(on: Boolean) {
        state.value = state.value.copy(
            clarity = on,
            enabled = if (on) false else state.value.enabled,
        )
    }

    fun reset() {
        setPreset("normal")
        state.value = state.value.copy(bass = 0, surround = 0)
    }

    /** Вызывается сервисом, когда известны реальные полосы устройства. */
    fun onBandInfo(info: BandInfo) {
        bandInfo.value = info
        val s = state.value
        if (s.bands.size != info.count && info.count > 0) {
            val mapped = when {
                s.preset != CUSTOM -> mapToBands(PRESET_BANDS[s.preset] ?: List(5) { 0 })
                else -> mapToBands(s.bands)
            }
            state.value = s.copy(bands = mapped)
        }
    }

    /** Переносит значения (по логической оси частот) на полосы устройства. */
    fun mapToBands(values: List<Int>): List<Int> {
        val info = bandInfo.value
        if (info == null || info.count == 0 || values.isEmpty()) return values
        val anchors = logAnchors(values.size)
        return List(info.count) { i ->
            val f = info.centerFreqs[i].toFloat().coerceAtLeast(1f)
            val v = interpLog(f, anchors, values)
            v.coerceIn(info.minDb, info.maxDb)
        }
    }

    private fun logAnchors(n: Int): List<Float> = when (n) {
        LOGICAL_FREQS.size -> LOGICAL_FREQS
        1 -> listOf(1000f)
        else -> {
            val lo = log10(60f)
            val hi = log10(14000f)
            List(n) { i -> 10f * (lo + (hi - lo) * i / (n - 1).coerceAtLeast(1)) }
        }
    }

    private fun interpLog(freq: Float, anchors: List<Float>, values: List<Int>): Int {
        if (freq <= anchors.first()) return values.first()
        if (freq >= anchors.last()) return values.last()
        var i = 0
        while (i < anchors.size - 1 && freq > anchors[i + 1]) i++
        val la = log10(anchors[i])
        val lb = log10(anchors[i + 1])
        val lf = log10(freq)
        val t = ((lf - la) / (lb - la).coerceAtLeast(1e-6f)).coerceIn(0f, 1f)
        return (values[i] + (values[i + 1] - values[i]) * t).roundToInt()
    }
}

/**
 * Управляет системными эффектами android.media.audiofx на аудио-сессии плеера.
 * Любая ошибка устройства глотается — эквалайзер просто остаётся выключенным.
 */
class EqualizerManager(sessionId: Int) {

    private val eq: Equalizer? = try {
        Equalizer(0, sessionId)
    } catch (e: Exception) {
        null
    }

    private val bass: BassBoost? = try {
        BassBoost(0, sessionId)
    } catch (e: Exception) {
        null
    }

    private val virt: Virtualizer? = try {
        Virtualizer(0, sessionId)
    } catch (e: Exception) {
        null
    }

    init {
        eq?.let { e ->
            try {
                val range = e.bandLevelRange
                val count = e.numberOfBands.toInt()
                val freqs = (0 until count).map { (e.getCenterFreq(it.toShort()) / 1000.0).roundToInt() }
                AudioEffects.onBandInfo(
                    AudioEffects.BandInfo(
                        count = count,
                        centerFreqs = freqs,
                        minDb = range[0] / 100,
                        maxDb = range[1] / 100,
                    )
                )
            } catch (ex: Exception) {
                // без информации о полосах UI покажет пресеты, но не полосы
            }
        }
    }

    fun apply(s: AudioEffects.Settings) {
        eq?.let { e ->
            try {
                if (s.clarity) {
                    // «Четкость речи»: голосовой пресет перекрывает настройки эквалайзера
                    e.enabled = true
                    val bands = AudioEffects.mapToBands(AudioEffects.VOICE_BANDS)
                    val range = e.bandLevelRange
                    for (b in 0 until e.numberOfBands) {
                        val db = bands.getOrNull(b) ?: 0
                        e.setBandLevel(
                            b.toShort(),
                            (db * 100).coerceIn(range[0].toInt(), range[1].toInt()).toShort(),
                        )
                    }
                } else {
                    e.enabled = s.enabled
                    if (s.enabled) {
                        val range = e.bandLevelRange
                        val min = range[0].toInt()
                        val max = range[1].toInt()
                        for (b in 0 until e.numberOfBands) {
                            val db = s.bands.getOrNull(b.toInt()) ?: 0
                            val centiDb = (db * 100).coerceIn(min, max)
                            e.setBandLevel(b.toShort(), centiDb.toShort())
                        }
                    }
                }
            } catch (ex: Exception) {
            }
        }
        bass?.let { b ->
            try {
                b.enabled = s.enabled && !s.clarity
                b.setStrength((s.bass * 10).toShort())
            } catch (ex: Exception) {
            }
        }
        virt?.let { v ->
            try {
                v.enabled = s.enabled && !s.clarity
                v.setStrength((s.surround * 10).toShort())
            } catch (ex: Exception) {
            }
        }
    }

    fun release() {
        try { eq?.enabled = false; eq?.release() } catch (e: Exception) {}
        try { bass?.enabled = false; bass?.release() } catch (e: Exception) {}
        try { virt?.enabled = false; virt?.release() } catch (e: Exception) {}
    }
}
