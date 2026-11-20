package app.expressiveclock.overlay

/** Coordinates are in density-independent display space; system icon positions are not public API. */
internal data class IslandCutout(val left: Float, val top: Float, val right: Float, val bottom: Float)
internal data class IslandGeometry(val centerX: Float, val top: Float, val width: Float, val height: Float, val belowStatusBar: Boolean)

internal fun islandGeometry(
    screenWidth: Float, statusBar: Float, cutout: IslandCutout?,
    offsetX: Float = 0f, offsetY: Float = 0f, widthAdjustment: Float = 0f, heightAdjustment: Float = 0f,
    screenHeight: Float = Float.POSITIVE_INFINITY,
    alignment: app.expressiveclock.data.IslandAlignment = app.expressiveclock.data.IslandAlignment.CENTRE,
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
    // A top-edge cutout rectangle includes the space above the physical lens.
    // Requiring the capsule to fit inside that rectangle/status bar incorrectly
    // sends shallow cutouts below the camera. Keep horizontal icon clearance,
    // and clamp the camera-centred capsule against the actual display edge.
    val fits = cutout != null && available >= minimum
    // Automatic sizing reserves icon space. Explicit enlargement honours calibration,
    // while the display-edge clamp still keeps the complete capsule reachable.
    val pillWidth = if (fits && widthAdjustment <= app.expressiveclock.data.FACTORY_OVERLAY_WIDTH_OFFSET)
        minOf(desired, available) else desired
    val anchor = when (alignment) {
        app.expressiveclock.data.IslandAlignment.LEFT -> width * .18f
        app.expressiveclock.data.IslandAlignment.RIGHT -> width * .82f
        app.expressiveclock.data.IslandAlignment.CENTRE -> if (fits) cameraX else width / 2f
    }
    val center = (anchor + offsetX)
        .coerceIn(pillWidth / 2f, width - pillWidth / 2f)
    val top = if (fits) cameraY - height / 2f else maxOf(statusBar, cutout?.bottom ?: 0f) + 6f
    val minTop = 0f
    val maxTop = (screenHeight - height - 8f).coerceAtLeast(minTop)
    return IslandGeometry(center, (top.coerceAtLeast(0f) + offsetY).coerceIn(minTop, maxTop), pillWidth, height, !fits)
}

internal fun islandReadoutCap(width: Float, characters: Int): Float =
    minOf(38f, width.coerceAtLeast(1f) / characters.coerceAtLeast(1)).coerceAtLeast(1f)

/** Full-screen accessibility overlays may receive zero status-bar insets. */
internal fun islandStatusBarHeight(reported: Float, cutout: IslandCutout?): Float =
    maxOf(reported, cutout?.bottom ?: 0f)

internal fun islandExpandedTop(top: Float, screenHeight: Float, contentHeight: Float): Float =
    top.coerceIn(8f, (screenHeight - contentHeight - 8f).coerceAtLeast(8f))

/** A portrait-style control card, never a full-width landscape banner. */
internal fun islandExpandedWidth(width: Float, height: Float): Float =
    minOf(width - 24f, height - 24f, 420f).coerceAtLeast(1f)

/** Offset range corresponding exactly to the capsule's reachable display positions. */
internal fun islandVerticalRange(width: Float, height: Float, statusBar: Float,
    cutout: IslandCutout?, heightAdjustment: Float): ClosedFloatingPointRange<Float> {
    val base = islandGeometry(width, statusBar, cutout, heightAdjustment = heightAdjustment, screenHeight = height)
    return -base.top..((height - base.height - 8f).coerceAtLeast(0f) - base.top)
}

/** Centre is the default; each side uses only its physically available travel. */
internal fun verticalSliderPosition(offset: Float, minimum: Float, maximum: Float): Float =
    if (offset < 0f && minimum < 0f) (-offset / minimum).coerceIn(-1f, 0f)
    else if (offset > 0f && maximum > 0f) (offset / maximum).coerceIn(0f, 1f) else 0f

internal fun verticalSliderOffset(position: Float, minimum: Float, maximum: Float): Float =
    if (position < 0f) -position.coerceAtLeast(-1f) * minimum.coerceAtMost(0f)
    else position.coerceAtMost(1f) * maximum.coerceAtLeast(0f)
