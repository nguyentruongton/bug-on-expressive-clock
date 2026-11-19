package app.expressiveclock.overlay

import org.junit.Assert.*
import org.junit.Test

class IslandGeometryTest {
    @Test fun compactDurationsUseHoursRatherThanThousandsOfMinutes() {
        assertEquals("99h", formatCompactDuration(java.time.Duration.ofSeconds(359999)))
        assertEquals("01h", formatCompactDuration(java.time.Duration.ofHours(1)))
        assertEquals("59m", formatCompactDuration(java.time.Duration.ofSeconds(3599)))
        assertEquals("59s", formatCompactDuration(java.time.Duration.ofSeconds(59)))
        assertEquals("00s", formatCompactDuration(java.time.Duration.ofSeconds(-1)))
    }

    @Test fun s25CutoutUsesFallbackBelowSystemIcons() {
        val result = islandGeometry(1080f / 2.625f, 91f / 2.625f, IslandCutout(511f / 2.625f, 0f, 569f / 2.625f, 91f / 2.625f))
        assertTrue(result.belowStatusBar)
        assertTrue(result.top > 91f / 2.625f)
        assertEquals(1080f / 2.625f / 2f, result.centerX, .01f)
    }
    @Test fun expandedCalibrationRangesActuallyChangeGeometry() {
        val camera = IslandCutout(198.56f, 0f, 244.51f, 74.67f)
        val normal = islandGeometry(443f, 74.67f, camera, widthAdjustment = -43f)
        val enlarged = islandGeometry(443f, 74.67f, camera, widthAdjustment = 257f)
        assertTrue(enlarged.width > normal.width + 200f)
        assertTrue(enlarged.width <= 443f - 16f)
        val right = islandGeometry(443f, 74.67f, camera, offsetX = 200f, offsetY = 150f, widthAdjustment = -43f)
        val left = islandGeometry(443f, 74.67f, camera, offsetX = -200f, widthAdjustment = -43f)
        assertTrue(right.centerX > normal.centerX)
        assertTrue(left.centerX < normal.centerX)
        assertTrue(right.centerX + right.width / 2 <= 443f)
        assertTrue(left.centerX - left.width / 2 >= 0f)
        assertEquals(normal.top + 150f, right.top, .01f)
    }
    @Test fun upwardCalibrationKeepsSpringHeadroom() {
        val camera = IslandCutout(198.56f, 0f, 244.51f, 74.67f)
        for (offset in listOf(-1000f, -30f, -10f, 0f, 30f)) {
            assertTrue(islandGeometry(443f, 74.67f, camera, offsetY = offset).top >= 8f)
        }
    }
    @Test fun zeroOverlayInsetStillUsesTopCutoutExtent() {
        val cutout = IslandCutout(198.56f, 0f, 244.51f, 74.67f)
        val statusBar = islandStatusBarHeight(0f, cutout)
        assertEquals(74.67f, statusBar, .01f)
        assertFalse(islandGeometry(443f, statusBar, cutout, widthAdjustment = -43f, heightAdjustment = 1f).belowStatusBar)
    }
    @Test fun pixelFoldCameraFitsWithinCentralStatusRegion() {
        val density = 2.4375f
        val result = islandGeometry(1080f / density, 182f / density,
            IslandCutout(484f / density, 0f, 596f / density, 182f / density),
            widthAdjustment = -43f, heightAdjustment = 1f)
        assertFalse(result.belowStatusBar)
        assertEquals(91f / density, result.top + result.height / 2f, .01f)
        assertTrue(result.width <= 1080f / density * .28f)
    }
    @Test fun sufficientCameraSpaceIsActuallyCameraCentered() {
        val result = islandGeometry(600f, 72f, IslandCutout(288f, 22f, 312f, 46f))
        assertFalse(result.belowStatusBar)
        assertEquals(300f, result.centerX, .01f)
        assertEquals(34f, result.top + result.height / 2f, .01f)
    }
    @Test fun offCenterCameraFallsBackWithoutCoveringIcons() {
        val result = islandGeometry(360f, 40f, IslandCutout(12f, 0f, 36f, 32f))
        assertTrue(result.belowStatusBar)
        assertEquals(180f, result.centerX, .01f)
    }
    @Test fun missingCutoutAndCalibrationCannotPushPillOffscreen() {
        for (width in listOf(240f, 320f, 360f, 840f)) {
            val result = islandGeometry(width, 32f, null, offsetX = 1000f, offsetY = -1000f, widthAdjustment = 200f)
            assertTrue(result.centerX - result.width / 2f >= 0f)
            assertTrue(result.centerX + result.width / 2f <= width)
            assertTrue(result.top >= 38f)
        }
    }
    @Test fun longDurationsFitTheirAllocatedReadoutRow() {
        assertTrue(islandReadoutCap(280f, 12) < islandReadoutCap(280f, 8))
        for (count in 8..16) assertTrue(islandReadoutCap(280f, count) * count <= 280f)
    }
}
