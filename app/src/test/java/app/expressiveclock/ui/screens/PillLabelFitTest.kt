package app.expressiveclock.ui.screens

import org.junit.Assert.*
import org.junit.Test

class PillLabelFitTest {
    @Test fun normalLabelsKeepTheirNaturalSize() {
        assertEquals(1f, pillLabelScale(80, 30, 100, 56), 0f)
    }
    @Test fun enlargedLabelFitsWithoutLosingItsLastGlyph() {
        val fit = pillLabelScale(240, 70, 120, 56)
        assertEquals(.5f, fit, 0f)
        assertTrue(240 * fit <= 120)
        assertTrue(70 * fit <= 56)
    }
    @Test fun veryTallTextAlsoFitsVertically() {
        val fit = pillLabelScale(80, 120, 100, 56)
        assertTrue(120 * fit <= 56)
    }
}
