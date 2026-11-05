package fr.retroengine

import android.graphics.Color

/**
 * Configuration visuelle de la manette NES.
 * Les coordonnées sont normalisées (0..1) et s'adaptent à l'orientation.
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
        val dpadX = if (isPortrait) 0.22f else 0.16f
        val dpadY = if (isPortrait) 0.70f else 0.76f
        val actionX = if (isPortrait) 0.78f else 0.82f
        val actionY = if (isPortrait) 0.73f else 0.76f
        val r = if (isPortrait) 0.115f else 0.110f

        return listOf(
            ControllerButton("B", NES_B, actionX - 0.115f, actionY, r, Color.rgb(225, 55, 75)),
            ControllerButton("A", NES_A, actionX + 0.115f, actionY - 0.025f, r, Color.rgb(35, 190, 220)),
            ControllerButton("SELECT", NES_SELECT, 0.44f, 0.91f, 0.055f, Color.rgb(90, 105, 125)),
            ControllerButton("START", NES_START, 0.56f, 0.91f, 0.043f, Color.rgb(90, 105, 125))
        )
    }
}
