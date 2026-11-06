package fr.retroengine

import android.graphics.Color

/**
 * Géométrie de la manette NES. Coordonnées normalisées pour s'adapter
 * aux dimensions de la surface sans agrandir artificiellement les touches.
 */
data class ControllerButton(
    val label: String,
    val inputMask: Int,
    val x: Float,
    val y: Float,
    val radius: Float,
    val color: Int
)

object ControllerLayout {
    const val NES_B = 1 shl 0
    const val NES_SELECT = 1 shl 2
    const val NES_START = 1 shl 3
    const val UP = 1 shl 4
    const val DOWN = 1 shl 5
    const val LEFT = 1 shl 6
    const val RIGHT = 1 shl 7
    const val NES_A = 1 shl 8

    fun nes(isPortrait: Boolean): List<ControllerButton> {
        val actionY = if (isPortrait) 0.76f else 0.73f
        val actionLeft = if (isPortrait) 0.735f else 0.777f
        val actionRight = if (isPortrait) 0.895f else 0.928f
        val actionRadius = if (isPortrait) 0.078f else 0.108f
        val systemRadius = if (isPortrait) 0.052f else 0.088f
        val systemY = if (isPortrait) 0.91f else 0.945f

        return listOf(
            ControllerButton("B", NES_B, actionLeft, actionY, actionRadius, Color.rgb(205, 42, 55)),
            ControllerButton("A", NES_A, actionRight, actionY, actionRadius, Color.rgb(222, 48, 61)),
            ControllerButton("SELECT", NES_SELECT, if (isPortrait) 0.455f else 0.445f, systemY, systemRadius, Color.rgb(65, 69, 78)),
            ControllerButton("START", NES_START, if (isPortrait) 0.565f else 0.635f, systemY, if (isPortrait) 0.052f else 0.117f, Color.rgb(65, 69, 78))
        )
    }
}
