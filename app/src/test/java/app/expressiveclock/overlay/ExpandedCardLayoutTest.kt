package app.expressiveclock.overlay

import org.junit.Assert.*
import org.junit.Test

class ExpandedCardLayoutTest {
    @Test fun overlapDoesNotAddStatusBarPaddingInsideTheExpandedCard() {
        val overlap = app.expressiveclock.data.IslandExpandedPosition.OVERLAP_CUTOUT
        for (statusBar in listOf(24f, 48f, 74f, 100f)) {
            assertEquals(0f, expandedContentClearance(overlap, statusBar, statusBar, 8f), 0f)
        }
        val below = app.expressiveclock.data.IslandExpandedPosition.BELOW_STATUS_BAR
        assertEquals(0f, expandedContentClearance(below, 74f, 74f, 80f), 0f)
        assertEquals(50f, expandedContentClearance(below, 74f, 74f, 8f), 0f)
    }

    @Test fun readoutAndControlsAlwaysHaveDisjointAllocatedRegions() {
        for (width in listOf(200f, 240f, 280f, 312f, 348f, 380f, 420f)) {
            for (ideal in listOf(110f, 145f, 190f, 240f)) {
                val result = expandedCardLayout(width, ideal, ideal * 20 / 38)
                val controls = 3 * result.button + 2 * result.controlGap
                assertTrue(result.button >= 48f)
                assertTrue(result.button <= 62f)
                assertTrue(controls <= width)
                if (!result.stacked) {
                    assertTrue(result.readoutWidth >= ideal * 20 / 38)
                    assertEquals(width, result.readoutWidth + result.groupGap + controls, .01f)
                } else assertEquals(width, result.readoutWidth, .01f)
            }
        }
    }
    @Test fun roomyCardsKeepOriginalButtonsAndNarrowCardsUseCompactHierarchy() {
        assertEquals(62f, expandedCardLayout(420f, 160f, 85f).button, .01f)
        assertFalse(expandedCardLayout(348f, 150f, 80f).stacked)
        assertTrue(expandedCardLayout(200f, 190f, 100f).stacked)
    }
}
