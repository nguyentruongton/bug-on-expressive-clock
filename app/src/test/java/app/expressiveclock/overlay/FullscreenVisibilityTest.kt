package app.expressiveclock.overlay

import org.junit.Assert.*
import org.junit.Test

class FullscreenVisibilityTest {
    @Test fun requiresBothSystemBarsHidden() {
        assertTrue(shouldHideIslandForFullscreen(false, false, false, false))
        assertFalse(shouldHideIslandForFullscreen(true, false, false, false))
        assertFalse(shouldHideIslandForFullscreen(false, true, false, false))
        assertFalse(shouldHideIslandForFullscreen(true, true, false, false))
    }
    @Test fun unknownInsetsLockAndCalibrationAreConservative() {
        assertFalse(shouldHideIslandForFullscreen(null, false, false, false))
        assertFalse(shouldHideIslandForFullscreen(false, null, false, false))
        assertFalse(shouldHideIslandForFullscreen(false, false, true, false))
        assertFalse(shouldHideIslandForFullscreen(false, false, false, true))
    }
}
