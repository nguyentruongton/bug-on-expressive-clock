package app.expressiveclock.overlay

import org.junit.Assert.*
import org.junit.Test

class MechanicalTickerTest {
    @Test fun adjacentCountdownAndCarryRoll() {
        assertTrue(tickerCanRoll("01:00:00", "00:59:59"))
        assertTrue(tickerCanRoll("00:00:09", "00:00:10"))
    }
    @Test fun resyncAndFormatChangeSnap() {
        assertFalse(tickerCanRoll("00:01:00", "00:02:00"))
        assertFalse(tickerCanRoll("59s", "01m"))
    }
    @Test fun cascadeDirectionAndDelayAreBounded() {
        assertEquals(0, tickerDelay(7, 8, true))
        assertEquals(0, tickerDelay(0, 8, false))
        assertEquals(190, tickerDelay(0, 8, true))
    }
}
