package app.expressiveclock.ui.screens

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StopwatchSizingTest {
    @Test fun narrowWindowsRetainSideClearance() {
        // Layout widths cover compact windows, display scaling, phones and foldable panes.
        for (width in listOf(200, 280, 320, 360, 403, 412, 480, 600, 840)) {
            val diameter = stopwatchFaceDiameter(width.dp).value
            assertTrue("$width dp viewport", diameter <= width - 32f + .01f)
        }
    }
    @Test fun roomyWindowsKeepOriginalFaceSize() {
        assertEquals(410.dp, stopwatchFaceDiameter(443.dp))
        assertEquals(410.dp, stopwatchFaceDiameter(640.dp))
    }
}
