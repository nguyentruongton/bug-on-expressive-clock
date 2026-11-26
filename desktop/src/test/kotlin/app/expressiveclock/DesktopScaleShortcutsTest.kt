package app.expressiveclock

import androidx.compose.ui.input.key.Key
import app.expressiveclock.ui.DesktopDisplayScale
import app.expressiveclock.ui.UiScaleShortcutAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopScaleShortcutsTest {

    @Test
    fun ctrlPlusEqualsAndNumpadAddIncrementScaleUpToMax() {
        assertEquals(1.1f, DesktopDisplayScale.nextUiScaleForShortcut(1.0f, true, Key.Equals))
        assertEquals(1.1f, DesktopDisplayScale.nextUiScaleForShortcut(1.0f, true, Key.Plus))
        assertEquals(1.1f, DesktopDisplayScale.nextUiScaleForShortcut(1.0f, true, Key.NumPadAdd))
        assertEquals(1.5f, DesktopDisplayScale.nextUiScaleForShortcut(1.5f, true, Key.Equals))
    }

    @Test
    fun ctrlMinusAndNumpadSubtractDecrementScaleDownToMin() {
        assertEquals(0.9f, DesktopDisplayScale.nextUiScaleForShortcut(1.0f, true, Key.Minus))
        assertEquals(0.9f, DesktopDisplayScale.nextUiScaleForShortcut(1.0f, true, Key.NumPadSubtract))
        assertEquals(0.8f, DesktopDisplayScale.nextUiScaleForShortcut(0.8f, true, Key.Minus))
    }

    @Test
    fun ctrlZeroAndNumpadZeroResetScaleToOne() {
        assertEquals(1.0f, DesktopDisplayScale.nextUiScaleForShortcut(1.3f, true, Key.Zero))
        assertEquals(1.0f, DesktopDisplayScale.nextUiScaleForShortcut(0.8f, true, Key.NumPad0))
    }

    @Test
    fun unmodifiedKeysReturnNull() {
        assertNull(DesktopDisplayScale.nextUiScaleForShortcut(1.0f, false, Key.Equals))
        assertNull(DesktopDisplayScale.nextUiScaleForShortcut(1.0f, false, Key.Minus))
        assertNull(DesktopDisplayScale.nextUiScaleForShortcut(1.0f, false, Key.Zero))
        assertNull(DesktopDisplayScale.nextUiScaleForShortcut(1.0f, true, Key.A))
    }

    @Test
    fun shortcutActionForKeyReturnsAtomicStepOrReset() {
        assertEquals(UiScaleShortcutAction.Step(+0.1f), DesktopDisplayScale.shortcutActionForKey(true, Key.Equals))
        assertEquals(UiScaleShortcutAction.Step(+0.1f), DesktopDisplayScale.shortcutActionForKey(true, Key.NumPadAdd))
        assertEquals(UiScaleShortcutAction.Step(-0.1f), DesktopDisplayScale.shortcutActionForKey(true, Key.Minus))
        assertEquals(UiScaleShortcutAction.Reset, DesktopDisplayScale.shortcutActionForKey(true, Key.Zero))
        assertNull(DesktopDisplayScale.shortcutActionForKey(false, Key.Equals))
    }
}
