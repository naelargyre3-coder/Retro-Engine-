package com.retroengine.app

import android.content.Context
import android.content.SharedPreferences
import android.view.InputDevice
import android.view.KeyEvent

enum class PadAction(val label: String, val retroKey: Int) {
    UP("Haut", KeyEvent.KEYCODE_DPAD_UP),
    DOWN("Bas", KeyEvent.KEYCODE_DPAD_DOWN),
    LEFT("Gauche", KeyEvent.KEYCODE_DPAD_LEFT),
    RIGHT("Droite", KeyEvent.KEYCODE_DPAD_RIGHT),
    A("A", KeyEvent.KEYCODE_BUTTON_A),
    B("B", KeyEvent.KEYCODE_BUTTON_B),
    X("X", KeyEvent.KEYCODE_BUTTON_X),
    Y("Y", KeyEvent.KEYCODE_BUTTON_Y),
    L("L", KeyEvent.KEYCODE_BUTTON_L1),
    R("R", KeyEvent.KEYCODE_BUTTON_R1),
    START("Start", KeyEvent.KEYCODE_BUTTON_START),
    SELECT("Select", KeyEvent.KEYCODE_BUTTON_SELECT),
    // Mega Drive : le moteur attend A=Y, B=B, C=A, X=L, Y=X, Z=R sur la manette libretro
    MD_A("A", KeyEvent.KEYCODE_BUTTON_Y),
    MD_B("B", KeyEvent.KEYCODE_BUTTON_B),
    MD_C("C", KeyEvent.KEYCODE_BUTTON_A),
    MD_X("X", KeyEvent.KEYCODE_BUTTON_L1),
    MD_Y("Y", KeyEvent.KEYCODE_BUTTON_X),
    MD_Z("Z", KeyEvent.KEYCODE_BUTTON_R1)
}

fun actionsFor(set: ButtonSet): List<PadAction> = when (set) {
    ButtonSet.TWO -> listOf(
        PadAction.UP, PadAction.DOWN, PadAction.LEFT, PadAction.RIGHT,
        PadAction.A, PadAction.B, PadAction.START, PadAction.SELECT
    )
    ButtonSet.TWO_SHOULDER -> listOf(
        PadAction.UP, PadAction.DOWN, PadAction.LEFT, PadAction.RIGHT,
        PadAction.A, PadAction.B, PadAction.L, PadAction.R, PadAction.START, PadAction.SELECT
    )
    ButtonSet.FOUR_SHOULDER -> listOf(
        PadAction.UP, PadAction.DOWN, PadAction.LEFT, PadAction.RIGHT,
        PadAction.A, PadAction.B, PadAction.X, PadAction.Y,
        PadAction.L, PadAction.R, PadAction.START, PadAction.SELECT
    )
    ButtonSet.SIX -> listOf(
        PadAction.UP, PadAction.DOWN, PadAction.LEFT, PadAction.RIGHT,
        PadAction.MD_A, PadAction.MD_B, PadAction.MD_C,
        PadAction.MD_X, PadAction.MD_Y, PadAction.MD_Z,
        PadAction.START, PadAction.SELECT
    )
}

fun defaultKeys(action: PadAction, set: ButtonSet): List<Int> {
    val strict = set == ButtonSet.FOUR_SHOULDER
    return when (action) {
        PadAction.UP -> listOf(KeyEvent.KEYCODE_DPAD_UP)
        PadAction.DOWN -> listOf(KeyEvent.KEYCODE_DPAD_DOWN)
        PadAction.LEFT -> listOf(KeyEvent.KEYCODE_DPAD_LEFT)
        PadAction.RIGHT -> listOf(KeyEvent.KEYCODE_DPAD_RIGHT)
        PadAction.A ->
            if (strict) listOf(KeyEvent.KEYCODE_BUTTON_B)
            else listOf(KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_Y)
        PadAction.B ->
            if (strict) listOf(KeyEvent.KEYCODE_BUTTON_A)
            else listOf(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_X)
        PadAction.X -> listOf(KeyEvent.KEYCODE_BUTTON_Y)
        PadAction.Y -> listOf(KeyEvent.KEYCODE_BUTTON_X)
        PadAction.L -> listOf(KeyEvent.KEYCODE_BUTTON_L1)
        PadAction.R -> listOf(KeyEvent.KEYCODE_BUTTON_R1)
        PadAction.START -> listOf(KeyEvent.KEYCODE_BUTTON_START)
        PadAction.SELECT -> listOf(KeyEvent.KEYCODE_BUTTON_SELECT)
        PadAction.MD_A -> listOf(KeyEvent.KEYCODE_BUTTON_X)
        PadAction.MD_B -> listOf(KeyEvent.KEYCODE_BUTTON_A)
        PadAction.MD_C -> listOf(KeyEvent.KEYCODE_BUTTON_B)
        PadAction.MD_X -> listOf(KeyEvent.KEYCODE_BUTTON_L1)
        PadAction.MD_Y -> listOf(KeyEvent.KEYCODE_BUTTON_Y)
        PadAction.MD_Z -> listOf(KeyEvent.KEYCODE_BUTTON_R1)
    }
}

fun isGamepadSource(source: Int): Boolean =
    (source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
        (source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK

fun hasGamepad(): Boolean =
    InputDevice.getDeviceIds().any { id ->
        val device = InputDevice.getDevice(id)
        device != null && !device.isVirtual && isGamepadSource(device.sources)
    }

fun padButtonName(keyCode: Int): String = when (keyCode) {
    KeyEvent.KEYCODE_BUTTON_A -> "Bouton A"
    KeyEvent.KEYCODE_BUTTON_B -> "Bouton B"
    KeyEvent.KEYCODE_BUTTON_X -> "Bouton X"
    KeyEvent.KEYCODE_BUTTON_Y -> "Bouton Y"
    KeyEvent.KEYCODE_BUTTON_L1 -> "L1"
    KeyEvent.KEYCODE_BUTTON_R1 -> "R1"
    KeyEvent.KEYCODE_BUTTON_L2 -> "L2"
    KeyEvent.KEYCODE_BUTTON_R2 -> "R2"
    KeyEvent.KEYCODE_BUTTON_THUMBL -> "Stick gauche (clic)"
    KeyEvent.KEYCODE_BUTTON_THUMBR -> "Stick droit (clic)"
    KeyEvent.KEYCODE_BUTTON_START -> "Start"
    KeyEvent.KEYCODE_BUTTON_SELECT -> "Select"
    KeyEvent.KEYCODE_BUTTON_MODE -> "Mode"
    KeyEvent.KEYCODE_DPAD_UP -> "Croix haut"
    KeyEvent.KEYCODE_DPAD_DOWN -> "Croix bas"
    KeyEvent.KEYCODE_DPAD_LEFT -> "Croix gauche"
    KeyEvent.KEYCODE_DPAD_RIGHT -> "Croix droite"
    else -> KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
}

class ControllerMapping(context: Context, val buttonSet: ButtonSet) {

    private val prefsName = when (buttonSet) {
        ButtonSet.TWO -> "controller_mapping"
        ButtonSet.TWO_SHOULDER -> "controller_mapping_gba"
        ButtonSet.FOUR_SHOULDER -> "controller_mapping_snes"
        ButtonSet.SIX -> "controller_mapping_md"
    }
    private val prefs: SharedPreferences =
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val actions = actionsFor(buttonSet)
    private val map = LinkedHashMap<PadAction, MutableSet<Int>>()

    init {
        for (action in actions) {
            val stored = prefs.getString(action.name, null)
            map[action] = if (stored == null) {
                defaultKeys(action, buttonSet).toMutableSet()
            } else {
                stored.split(",").mapNotNull { it.toIntOrNull() }.toMutableSet()
            }
        }
    }

    fun actionList(): List<PadAction> = actions

    private fun save() {
        val editor = prefs.edit()
        for ((action, keys) in map) editor.putString(action.name, keys.joinToString(","))
        editor.apply()
    }

    fun keysFor(action: PadAction): Set<Int> = map[action] ?: emptySet()

    fun describe(action: PadAction): String {
        val keys = keysFor(action)
        return if (keys.isEmpty()) "Non assign\u00e9" else keys.joinToString(" + ") { padButtonName(it) }
    }

    fun lookup(keyCode: Int): PadAction? =
        map.entries.firstOrNull { keyCode in it.value }?.key

    fun assign(action: PadAction, keyCode: Int) {
        for (keys in map.values) keys.remove(keyCode)
        map[action] = mutableSetOf(keyCode)
        save()
    }

    // Inverse les deux boutons principaux (A et B)
    fun swapAB() {
        val first = if (buttonSet == ButtonSet.SIX) PadAction.MD_A else PadAction.A
        val second = if (buttonSet == ButtonSet.SIX) PadAction.MD_B else PadAction.B
        val a = map[first] ?: mutableSetOf()
        val b = map[second] ?: mutableSetOf()
        map[first] = b
        map[second] = a
        save()
    }

    fun reset() {
        for (action in actions) map[action] = defaultKeys(action, buttonSet).toMutableSet()
        save()
    }
}
