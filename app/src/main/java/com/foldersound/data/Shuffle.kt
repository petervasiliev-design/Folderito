package com.foldersound.data

import java.util.Collections
import java.util.Random

/**
 * Умное перемешивание очереди: тасование Фишера—Йетса (каждый трек играет
 * ровно один раз) + раскладка, при которой рядом не оказываются треки
 * одного исполнителя, альбома и папки.
 */
object Shuffle {

    /**
     * Перемешанный список; трек [keepFirstId] (играющий) остаётся первым,
     * чтобы продолжить его, а впереди был весь перемешанный остаток.
     */
    fun shuffleQueue(tracks: List<Track>, keepFirstId: Long? = null): List<Track> {
        val current = tracks.firstOrNull { it.id == keepFirstId }
        val rest = tracks.filter { it.id != keepFirstId }.toMutableList()
        val rnd = Random()
        for (i in rest.size - 1 downTo 1) {
            val j = rnd.nextInt(i + 1)
            Collections.swap(rest, i, j)
        }
        spread(rest)
        return if (current != null) listOf(current) + rest else rest
    }

    /** Жадная раскладка: если сосед повторяет ключ предыдущего — меняем с первым «чужим» дальше. */
    private fun spread(list: MutableList<Track>) {
        fun key(t: Track): Triple<String, String, String> = Triple(t.artist, t.album, t.folderPath)
        for (i in 1 until list.size) {
            if (key(list[i]) == key(list[i - 1])) {
                var j = i + 1
                while (j < list.size && key(list[j]) == key(list[i - 1])) j++
                if (j < list.size) Collections.swap(list, i, j)
            }
        }
    }
}
