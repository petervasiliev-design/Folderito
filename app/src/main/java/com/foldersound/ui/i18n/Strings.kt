package com.foldersound.ui.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

/** Язык интерфейса: система / русский / английский. */
enum class AppLang(val code: String) {
    SYSTEM("system"),
    RU("ru"),
    EN("en");

    companion object {
        fun fromCode(code: String?): AppLang = entries.firstOrNull { it.code == code } ?: SYSTEM
    }
}

/** Эффективный язык для режима SYSTEM: русский, если системная локаль русская. */
fun effectiveLangCode(mode: AppLang): String = when (mode) {
    AppLang.RU -> "ru"
    AppLang.EN -> "en"
    AppLang.SYSTEM ->
        if (Locale.getDefault().language.startsWith("ru")) "ru" else "en"
}

/** Формы множественного числа: one / few / many (в английском few не используется). */
data class Plurals(val one: String, val few: String, val many: String)

/** Словарь интерфейса. Все видимые строки приложения — только отсюда. */
data class FsStrings(
    val langCode: String,
    // ==================== библиотека ====================
    val folders: String,
    val tracks: String,
    val selectionEmpty: String,
    val search: String,
    val searchHint: String,
    val collapseAllFolders: String,
    val closeSearch: String,
    val clear: String,
    val toggleTheme: String,
    val switchLanguage: String,
    val nothingFound: String,
    val found: String,
    val emptyLibrary: String,
    val collapse: String,
    val expand: String,
    val selectedPrefix: String,
    val nothingSelected: String,
    val clearAll: String,
    val toPlayer: String,
    val toSongs: String,
    val clearSelection: String,
    val playSelected: String,
    val playPause: String,
    val nextTrack: String,
    val unknownArtist: String,
    val noAlbum: String,
    val hoursShort: String,
    val minutesShort: String,
    val pTracks: Plurals,
    val pFolders: Plurals,
    val pSubfolders: Plurals,
    // ==================== плеер ====================
    val nowPlaying: String,
    val backToLibrary: String,
    val equalizer: String,
    val shuffleOn: String,
    val shuffleOff: String,
    val prevTrack: String,
    val pause: String,
    val play: String,
    val repeatAll: String,
    val repeatOne: String,
    val repeatOff: String,
    // ==================== эквалайзер ====================
    val backToPlayer: String,
    val reset: String,
    val eqSubtitle: String,
    val eqUnavailable: String,
    val rangeLabel: String,
    val dbUnit: String,
    val bassBoost: String,
    val surround: String,
    val presetNames: Map<String, String>,
    // ==================== разрешения ====================
    val needAccess: String,
    val permText: String,
    val allowAccess: String,
    val permHint: String,
    val openSettings: String,
    // ==================== меню-шторка ====================
    val menu: String,
    val settings: String,
    val seekButtonsLabel: String,
    val seekStepLabel: String,
    val secShort: String,
    val songsList: String,
    val themeSection: String,
    val themeSystem: String,
    val themeLight: String,
    val themeDark: String,
    val skinSection: String,
    val skinClassic: String,
    val langSection: String,
    val langSystem: String,
    val langRu: String,
    val langEn: String,
    val sleepTimerSection: String,
    val sleepCustom: String,
    val sleepRemaining: String,
    val sleepCancel: String,
    val sleepStart: String,
    val clearAllConfirm: String,
    val cancel: String,
    val unavailable: String,
    val batteryTitle: String,
    val batteryHint: String,
    val playbackSettingsTitle: String,
    val speedTitle: String,
) {
    /** Словоформы по правилам языка. */
    fun plural(n: Int, p: Plurals): String {
        val form = if (langCode == "ru") {
            val m = n % 100
            val d = n % 10
            when {
                m in 11..14 -> p.many
                d == 1 -> p.one
                d in 2..4 -> p.few
                else -> p.many
            }
        } else {
            if (n == 1) p.one else p.many
        }
        return "$n $form"
    }

    /** «3 ч 28 мин» / «3 h 28 min». */
    fun formatTotalMs(ms: Long): String {
        val totalMin = (ms / 60000).toInt()
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) "$h $hoursShort $m $minutesShort" else "$m $minutesShort"
    }
}

private val RuStrings = FsStrings(
    langCode = "ru",
    folders = "Папки",
    tracks = "Треки",
    selectionEmpty = "Ничего не выбрано — отметьте песни в дереве",
    search = "Поиск",
    searchHint = "Трек, исполнитель или альбом…",
    collapseAllFolders = "Свернуть все папки",
    closeSearch = "Закрыть поиск",
    clear = "Очистить",
    toggleTheme = "Переключить тему",
    switchLanguage = "Сменить язык",
    nothingFound = "Ничего не найдено",
    found = "Найдено",
    emptyLibrary = "Музыка не найдена",
    collapse = "Свернуть",
    expand = "Раскрыть",
    selectedPrefix = "Выбрано",
    nothingSelected = "Ничего не выбрано",
    clearAll = "Очистить всё",
    toPlayer = "К плееру",
    toSongs = "К списку песен",
    clearSelection = "Сбросить выбор",
    playSelected = "Слушать выбранное",
    playPause = "Пауза/воспроизведение",
    nextTrack = "Следующий трек",
    unknownArtist = "Неизвестный исполнитель",
    noAlbum = "Без альбома",
    hoursShort = "ч",
    minutesShort = "мин",
    pTracks = Plurals("трек", "трека", "треков"),
    pFolders = Plurals("папка", "папки", "папок"),
    pSubfolders = Plurals("подпапка", "подпапки", "подпапок"),
    nowPlaying = "Сейчас играет",
    backToLibrary = "Назад к библиотеке",
    equalizer = "Эквалайзер",
    shuffleOn = "Перемешивание включено",
    shuffleOff = "Перемешивание выключено",
    prevTrack = "Предыдущий трек",
    pause = "Пауза",
    play = "Воспроизвести",
    repeatAll = "Повтор всех треков",
    repeatOne = "Повтор одного трека",
    repeatOff = "Повтор выключен",
    backToPlayer = "Назад к плееру",
    reset = "Сбросить",
    eqSubtitle = "Настраивает звучание под ваши наушники",
    eqUnavailable = "Полосы эквалайзера недоступны на этом устройстве",
    rangeLabel = "Диапазон",
    dbUnit = "дБ",
    bassBoost = "Усиление басов",
    surround = "Объёмный звук",
    presetNames = mapOf(
        "normal" to "Обычный",
        "rock" to "Рок",
        "pop" to "Поп",
        "jazz" to "Джаз",
        "classical" to "Классика",
        "electronic" to "Электроника",
        "hiphop" to "Хип-хоп",
        "bass" to "Бас+",
        "custom" to "Свои",
    ),
    needAccess = "Нужен доступ к музыке",
    permText = "Приложение читает только аудиофайлы, чтобы построить библиотеку по папкам. Другие файлы ему не видны.",
    allowAccess = "Разрешить доступ",
    permHint = "Если запрос не появляется — откройте настройки приложения и включите доступ к музыке вручную.",
    openSettings = "Открыть настройки",
    menu = "Меню",
    settings = "Настройки",
    seekButtonsLabel = "Кнопки перемотки",
    seekStepLabel = "Шаг перемотки",
    secShort = "с",
    songsList = "Список песен",
    themeSection = "Тема",
    themeSystem = "Системная",
    themeLight = "Светлая",
    themeDark = "Тёмная",
    skinSection = "Цветовая схема",
    skinClassic = "Стандартная",
    langSection = "Язык",
    langSystem = "Системный",
    langRu = "Русский",
    langEn = "English",
    sleepTimerSection = "Таймер сна",
    sleepCustom = "Своё",
    sleepRemaining = "Осталось",
    sleepCancel = "Закрыть таймер (удержание 1 с)",
    sleepStart = "Запустить",
    clearAllConfirm = "Убрать выделение со всех папок и треков?",
    cancel = "Отмена",
    unavailable = "недоступен",
    batteryTitle = "Батарея",
    batteryHint = "Разрешить работу в фоне, чтобы музыка не прерывалась",
    playbackSettingsTitle = "Настройки воспроизведения",
    speedTitle = "Скорость воспроизведения",
)

private val EnStrings = FsStrings(
    langCode = "en",
    folders = "Folders",
    tracks = "Tracks",
    selectionEmpty = "Nothing selected — check songs in the tree",
    search = "Search",
    searchHint = "Track, artist or album…",
    collapseAllFolders = "Collapse all folders",
    closeSearch = "Close search",
    clear = "Clear",
    toggleTheme = "Toggle theme",
    switchLanguage = "Switch language",
    nothingFound = "Nothing found",
    found = "Found",
    emptyLibrary = "No music found",
    collapse = "Collapse",
    expand = "Expand",
    selectedPrefix = "Selected",
    nothingSelected = "Nothing selected",
    clearAll = "Clear all",
    toPlayer = "To player",
    toSongs = "To songs",
    clearSelection = "Clear selection",
    playSelected = "Play selected",
    playPause = "Play/pause",
    nextTrack = "Next track",
    unknownArtist = "Unknown artist",
    noAlbum = "No album",
    hoursShort = "h",
    minutesShort = "min",
    pTracks = Plurals("track", "tracks", "tracks"),
    pFolders = Plurals("folder", "folders", "folders"),
    pSubfolders = Plurals("subfolder", "subfolders", "subfolders"),
    nowPlaying = "Now playing",
    backToLibrary = "Back to library",
    equalizer = "Equalizer",
    shuffleOn = "Shuffle on",
    shuffleOff = "Shuffle off",
    prevTrack = "Previous track",
    pause = "Pause",
    play = "Play",
    repeatAll = "Repeat all tracks",
    repeatOne = "Repeat one track",
    repeatOff = "Repeat off",
    backToPlayer = "Back to player",
    reset = "Reset",
    eqSubtitle = "Tunes the sound for your headphones",
    eqUnavailable = "Equalizer bands are unavailable on this device",
    rangeLabel = "Range",
    dbUnit = "dB",
    bassBoost = "Bass boost",
    surround = "Surround sound",
    presetNames = mapOf(
        "normal" to "Flat",
        "rock" to "Rock",
        "pop" to "Pop",
        "jazz" to "Jazz",
        "classical" to "Classical",
        "electronic" to "Electronic",
        "hiphop" to "Hip-hop",
        "bass" to "Bass+",
        "custom" to "Custom",
    ),
    needAccess = "Music access needed",
    permText = "The app reads only audio files to build the folder-based library. Other files stay invisible to it.",
    allowAccess = "Allow access",
    permHint = "If the prompt doesn't appear, open the app settings and enable music access manually.",
    openSettings = "Open settings",
    menu = "Menu",
    settings = "Settings",
    seekButtonsLabel = "Seek buttons",
    seekStepLabel = "Seek step",
    secShort = "s",
    songsList = "All songs",
    themeSection = "Theme",
    themeSystem = "System",
    themeLight = "Light",
    themeDark = "Dark",
    skinSection = "Color scheme",
    skinClassic = "Default",
    langSection = "Language",
    langSystem = "System",
    langRu = "Русский",
    langEn = "English",
    sleepTimerSection = "Sleep timer",
    sleepCustom = "Custom",
    sleepRemaining = "Time left",
    sleepCancel = "Cancel timer (hold 1 s)",
    sleepStart = "Start",
    clearAllConfirm = "Remove selection from all folders and tracks?",
    cancel = "Cancel",
    unavailable = "unavailable",
    batteryTitle = "Battery",
    batteryHint = "Allow background operation so music keeps playing",
    playbackSettingsTitle = "Playback settings",
    speedTitle = "Playback speed",
)

fun stringsFor(langCode: String): FsStrings = if (langCode == "en") EnStrings else RuStrings

val LocalStrings = staticCompositionLocalOf { RuStrings }
