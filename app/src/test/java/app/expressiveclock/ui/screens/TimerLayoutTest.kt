package app.expressiveclock.ui.screens

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerLayoutTest {
    @Test fun roomyScreensKeepOriginalTargets() {
        assertEquals(76.dp, timerKeyHeight(700.dp, 120.dp, 78.dp, 70.dp))
    }
    @Test fun compactViewportReservesSpaceForPresets() {
        val height = timerKeyHeight(540.dp, 120.dp, 78.dp, 70.dp)
        assertEquals(68.dp, height)
        assertEquals(540.dp, height * 4 + 120.dp + 78.dp + 70.dp)
    }
    @Test fun shortScreensScrollRatherThanMakingTinyTargets() {
        assertEquals(60.dp, timerKeyHeight(180.dp, 120.dp, 120.dp, 70.dp))
    }
    @Test fun largerFontsCannotIncreaseKeypadHeight() {
        val ordinary = timerKeyHeight(540.dp, 120.dp, 78.dp, 70.dp)
        val enlarged = timerKeyHeight(540.dp, 120.dp, 156.dp, 70.dp)
        assertTrue(enlarged <= ordinary)
        assertTrue(enlarged >= 60.dp)
    }
}
