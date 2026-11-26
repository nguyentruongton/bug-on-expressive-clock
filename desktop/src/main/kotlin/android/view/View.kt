package android.view

class View {
    fun performHapticFeedback(feedbackConstant: Int): Boolean = false
    fun performHapticFeedback(feedbackConstant: Int, flags: Int): Boolean = false
}

object HapticFeedbackConstants {
    const val LONG_PRESS: Int = 0
    const val VIRTUAL_KEY: Int = 1
    const val KEYBOARD_TAP: Int = 3
    const val CLOCK_TICK: Int = 4
    const val CONTEXT_CLICK: Int = 6
    const val KEYBOARD_PRESS: Int = 3
    const val KEYBOARD_RELEASE: Int = 7
    const val VIRTUAL_KEY_RELEASE: Int = 8
    const val TEXT_HANDLE_MOVE: Int = 9
    const val GESTURE_START: Int = 12
    const val GESTURE_END: Int = 13
    const val CONFIRM: Int = 16
    const val REJECT: Int = 17
    const val TOGGLE_ON: Int = 21
    const val TOGGLE_OFF: Int = 22
    const val SEGMENT_TICK: Int = 26
    const val SEGMENT_FREQUENT_TICK: Int = 27
}
