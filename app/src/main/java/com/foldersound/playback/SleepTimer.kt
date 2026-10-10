package com.foldersound.playback

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Таймер сна: общий для UI и сервиса дедлайн (epoch millis) момента срабатывания.
 * Дедлайн фиксирован по часам — ручная пауза музыки отсчёт не останавливает,
 * а UI не тикает зря: остаток вычисляется из дедлайна по запросу.
 * Состояние сессионное — после перезапуска приложения таймер не восстанавливается.
 */
object SleepTimer {

    val PRESETS_MIN = listOf(10, 15, 30, 60, 90)
    const val CUSTOM_MIN = 5
    const val CUSTOM_MAX = 180

    /** Момент срабатывания (epoch millis) или null, если таймер не запущен. */
    val deadlineMs = MutableStateFlow<Long?>(null)

    fun start(minutes: Int) {
        deadlineMs.value = System.currentTimeMillis() + minutes * 60_000L
    }

    fun cancel() {
        deadlineMs.value = null
    }

    /** Остаток в миллисекундах; 0, если таймер не запущен. */
    fun remainingMs(): Long =
        deadlineMs.value?.let { (it - System.currentTimeMillis()).coerceAtLeast(0L) } ?: 0L
}
