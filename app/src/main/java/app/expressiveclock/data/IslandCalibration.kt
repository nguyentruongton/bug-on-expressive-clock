package app.expressiveclock.data

import kotlin.math.roundToInt

/** Stable preference IDs; labels and display detection never change stored keys. */
enum class IslandProfile(val label: String) {
    FOLDED_PORTRAIT("Folded Portrait"), FOLDED_LANDSCAPE("Folded Landscape"),
    UNFOLDED_PORTRAIT("Unfolded Portrait"), UNFOLDED_LANDSCAPE("Unfolded Landscape")
}

enum class IslandAlignment { LEFT, CENTRE, RIGHT }
enum class IslandExpandedPosition { BELOW_STATUS_BAR, OVERLAP_CUTOUT }

data class IslandCalibration(
    val x: Float = FACTORY_OVERLAY_OFFSET_X.toFloat(), val y: Float = FACTORY_OVERLAY_OFFSET_Y.toFloat(),
    val width: Float = FACTORY_OVERLAY_WIDTH_OFFSET.toFloat(), val height: Float = FACTORY_OVERLAY_HEIGHT_OFFSET.toFloat(),
    val enabled: Boolean = true,
    val alignment: IslandAlignment = IslandAlignment.CENTRE,
    val expandedPosition: IslandExpandedPosition = IslandExpandedPosition.OVERLAP_CUTOUT,
)

fun ThemeSettings.legacyCalibration() = IslandCalibration(overlayOffsetX.toFloat(), overlayOffsetY.toFloat(), overlayWidthOffset.toFloat(), overlayHeightOffset.toFloat())
fun ThemeSettings.calibration(profile: IslandProfile): IslandCalibration = overlayProfiles[profile] ?: legacyCalibration()
fun ThemeSettings.withCalibration(profile: IslandProfile?, value: IslandCalibration): ThemeSettings =
    if (profile == null) copy(overlayOffsetX = value.x.roundToInt(), overlayOffsetY = value.y.roundToInt(),
        overlayWidthOffset = value.width.roundToInt(), overlayHeightOffset = value.height.roundToInt())
    else copy(overlayProfiles = IslandProfile.entries.associateWith { if (it == profile) value else calibration(it) })
