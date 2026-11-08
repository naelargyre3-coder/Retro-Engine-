package com.retroengine.app

import android.content.Context
import android.content.SharedPreferences
import android.view.InputDevice
import android.view.KeyEvent

enum class NesAction(val label: String, val retroKey: Int, val defaults: List<Int>) {
    UP("Haut", KeyEvent.KEYCODE_DPAD_UP, listOf(KeyEvent.KEYCODE_DPAD_UP)),
    DOWN("Bas", KeyEvent.KEYCODE_DPAD_DOWN, listOf(KeyEvent.KEYCODE_DPAD_DOWN)),
    LEFT("Gauche", KeyEvent.KEYCODE_DPAD_LEFT, listOf(KeyEvent.KEYCODE_DPAD_LEFT)),
    RIGHT("Droite", KeyEvent.KEYCODE_DPAD_RIGHT, listOf(KeyEvent.KEYCODE_DPAD_RIGHT)),
    A("A", KeyEvent.KEYCODE_BUTTON_A, listOf(KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_Y)),
    B("B", KeyEvent.KEYCODE_BUTTON_B, listOf(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_X)),
    START("Start", KeyEvent.KEYCODE_BUTTON_START, listOf(KeyEvent.KEYCODE_BUTTON_START)),
    SELECT("Select", KeyEvent.KEYCODE_BUTTON_SELECT, listOf(KeyEvent.KEYCODE_BUTTON_SELECT))
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

class ControllerMapping(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("controller_mapping", Context.MODE_PRIVATE)
    private val map = LinkedHashMap<NesAction, MutableSet<Int>>()

    init {
        for (action in NesAction.values()) {
            val stored = prefs.getString(action.name, null)
            map[action] = if (stored == null) {
                action.defaults.toMutableSet()
            } else {
                stored.split(",").mapNotNull { it.toIntOrNull() }.toMutableSet()
            }
        }
    }

    private fun save() {
        val editor = prefs.edit()
        for ((action, keys) in map) editor.putString(action.name, keys.joinToString(","))
        editor.apply()
    }

    fun keysFor(action: NesAction): Set<Int> = map[action] ?: emptySet()

    fun describe(action: NesAction): String {
        val keys = keysFor(action)
        return if (keys.isEmpty()) "Non assign\u00e9" else keys.joinToString(" + ") { padButtonName(it) }
    }

    fun lookup(keyCode: Int): NesAction? =
        map.entries.firstOrNull { keyCode in it.value }?.key

    fun assign(action: NesAction, keyCode: Int) {
        for (keys in map.values) keys.remove(keyCode)
        map[action] = mutableSetOf(keyCode)
        save()
    }

    fun swapAB() {
        val a = map[NesAction.A] ?: mutableSetOf()
        val b = map[NesAction.B] ?: mutableSetOf()
        map[NesAction.A] = b
        map[NesAction.B] = a
        save()
    }

    fun reset() {
        for (action in NesAction.values()) map[action] = action.defaults.toMutableSet()
        save()
    }
}
