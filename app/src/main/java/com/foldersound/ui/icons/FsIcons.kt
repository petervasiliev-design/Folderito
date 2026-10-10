package com.foldersound.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Собственные иконки приложения:
 *  - Folder / FolderOpen — Material Symbols Outlined (как в макете),
 *    скачаны с fonts.google.com;
 *  - SoundWave — fluent:sound-wave-circle-24-regular (Iconify).
 */
object FsIcons {

    val Folder: ImageVector by lazy {
        symbolVector(
            name = "FsFolder",
            pathData = "M160-160q-33 0-56.5-23.5T80-240v-480q0-33 23.5-56.5T160-800h240l80 80h320q33 0 56.5 23.5T880-640v400q0 33-23.5 56.5T800-160H160Zm0-80h640v-400H447l-80-80H160v480Zm0 0v-480 480Z",
        )
    }

    val FolderOpen: ImageVector by lazy {
        symbolVector(
            name = "FsFolderOpen",
            pathData = "M160-160q-33 0-56.5-23.5T80-240v-480q0-33 23.5-56.5T160-800h240l80 80h320q33 0 56.5 23.5T880-640H447l-80-80H160v480l96-320h684L837-217q-8 26-29.5 41.5T760-160H160Zm84-80h516l72-240H316l-72 240Zm0 0 72-240-72 240Zm-84-400v-80 80Z",
        )
    }

    val SoundWave: ImageVector by lazy {
        ImageVector.Builder(
            name = "FsSoundWave",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = PathParser().parsePathString(
                    "M11 8.75a.75.75 0 0 0-1.5 0v6.5a.75.75 0 0 0 1.5 0zm6 0a.75.75 0 0 0-1.5 0v6.5a.75.75 0 0 0 1.5 0zm-3 1.5a.75.75 0 0 0-1.5 0v3.5a.75.75 0 0 0 1.5 0zm-6 .5a.75.75 0 0 0-1.5 0v2.5a.75.75 0 0 0 1.5 0zM12 2C6.477 2 2 6.477 2 12s4.477 10 10 10s10-4.477 10-10S17.523 2 12 2M3.5 12a8.5 8.5 0 1 1 17 0a8.5 8.5 0 0 1-17 0"
                ).toNodes(),
                // без fill путь не рисуется; Icon() перекрашивает tint'ом
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /**
     * Material Symbols используют viewBox «0 -960 960 960» —
     * сдвигаем группу на +960 по Y, чтобы путь попал в положительные координаты.
     */
    private fun symbolVector(name: String, pathData: String, size: Dp = 24.dp): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = size,
            defaultHeight = size,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).apply {
            group(translationY = 960f) {
                addPath(
                    pathData = PathParser().parsePathString(pathData).toNodes(),
                    // без fill путь не рисуется; Icon() перекрашивает tint'ом
                    fill = SolidColor(Color.Black),
                )
            }
        }.build()
}
