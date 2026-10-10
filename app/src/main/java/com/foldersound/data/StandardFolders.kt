package com.foldersound.data

/**
 * Словарь стандартных каталогов-хранилищ музыки (2026-10-08, по списку пользователя).
 * Совпадение — по имени папки без учёта регистра и пробелов по краям.
 * Такие папки в дереве помечаются особым цветом плитки (#8895A5).
 */
object StandardFolders {

    /** Прямые хранилища музыки. */
    val MUSIC: Set<String> = setOf(
        "music", "musics", "музыка",
        "tracks", "треки",
        "songs", "песни",
        "audios", "audio", "аудио",
        "mp3",
        "sound", "sounds", "звуки",
    )

    /** Специализированный аудио-контент (не только музыка). */
    val SPECIALIZED: Set<String> = setOf(
        "podcasts", "подкасты",
        "audiobooks", "audio_books", "аудиокниги",
    )

    private val all = MUSIC + SPECIALIZED

    fun isStandard(folderName: String): Boolean =
        folderName.trim().lowercase() in all
}
