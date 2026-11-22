package app.expressiveclock.ui.screens

import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test

class WorldClockDetailsGeometryTest {
    @Test fun locationTracksTheOriginalReadoutSize() {
        val dial = 320.dp
        val ink = app.expressiveclock.ui.timerReadoutGeometry(dial * app.expressiveclock.ui.WORLD_CLOCK_READOUT_WIDTH_FRACTION, "00:00:00").capHeight
        val header = worldClockDigitalHeaderHeight(dial, 100.dp, false)
        val expected = (dial - header) / 2 + 8.dp + ink + 24.dp
        assertEquals(expected.value, worldClockDetailsTop(dial, 1f, true).value, .001f)
        assertEquals(.48f, app.expressiveclock.ui.WORLD_CLOCK_READOUT_WIDTH_FRACTION, 0f)
    }

    @Test fun analoguePlacementIsPreservedAndDigitalDetailsMoveUpContinuously() {
        for (dial in listOf(100f, 200f, 320f)) for (use24h in listOf(false, true)) {
            assertEquals(dial + 24f, worldClockDetailsTop(dial.dp, 0f, use24h).value, .001f)
            val positions = (0..20).map { worldClockDetailsTop(dial.dp, it / 20f, use24h).value }
            assertTrue(positions.zipWithNext().all { (a,b) -> b <= a })
            assertTrue(positions.last() > 24f)
            assertEquals(positions.first(), worldClockDetailsTop(dial.dp, -1f, use24h).value, .001f)
            assertEquals(positions.last(), worldClockDetailsTop(dial.dp, 2f, use24h).value, .001f)
        }
    }
}
