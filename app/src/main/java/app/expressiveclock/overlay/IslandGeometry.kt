package app.expressiveclock.overlay

/** Coordinates are in density-independent display space; system icon positions are not public API. */
internal data class IslandCutout(val left: Float, val top: Float, val right: Float, val bottom: Float)
internal data class IslandGeometry(val centerX: Float, val top: Float, val width: Float, val height: Float, val belowStatusBar: Boolean)

internal fun islandGeometry(
    screenWidth: Float, statusBar: Float, cutout: IslandCutout?,
    offsetX: Float = 0f, offsetY: Float = 0f, widthAdjustment: Float = 0f, heightAdjustment: Float = 0f,
): IslandGeometry {
    val width = screenWidth.coerceAtLeast(1f)
    val cameraWidth = cutout?.let { it.right - it.left } ?: 0f
    val height = (38f + heightAdjustment).coerceIn(32f, 60f)
    val desired = (96f + cameraWidth + widthAdjustment).coerceAtLeast(108f).coerceAtMost((width - 16f).coerceAtLeast(1f))
    // Reserve the outer status-bar regions for clock, notifications and system indicators.
    // These are conservative exclusion zones, not a claim to know Android's icon bounds.
    val safeLeft = width * .36f
    val safeRight = width * .64f
    val cameraX = cutout?.let { (it.left + it.right) / 2f } ?: width / 2f
    val cameraY = cutout?.let { (it.top + it.bottom) / 2f } ?: -1f
    val available = 2f * minOf(cameraX - safeLeft, safeRight - cameraX)
    val minimum = maxOf(96f, cameraWidth + 48f)
    val fits = cutout != null && available >= minimum && cameraY - height / 2f >= 0f &&
        cameraY + height / 2f <= statusBar
    // Automatic sizing reserves icon space. Explicit enlargement honours calibration,
    // while the display-edge clamp still keeps the complete capsule reachable.
    val pillWidth = if (fits && widthAdjustment <= app.expressiveclock.data.FACTORY_OVERLAY_WIDTH_OFFSET)
        minOf(desired, available) else desired
    val center = ((if (fits) cameraX else width / 2f) + offsetX)
        .coerceIn(pillWidth / 2f, width - pillWidth / 2f)
    val top = if (fits) cameraY - height / 2f else maxOf(statusBar, cutout?.bottom ?: 0f) + 6f
    return IslandGeometry(center, (top + offsetY).coerceAtLeast(if (fits) 8f else statusBar + 6f), pillWidth, height, !fits)
}

internal fun islandReadoutCap(width: Float, characters: Int): Float =
    minOf(38f, width.coerceAtLeast(1f) / characters.coerceAtLeast(1)).coerceAtLeast(1f)

/** Full-screen accessibility overlays may receive zero status-bar insets. */
internal fun islandStatusBarHeight(reported: Float, cutout: IslandCutout?): Float =
    maxOf(reported, cutout?.bottom ?: 0f)
