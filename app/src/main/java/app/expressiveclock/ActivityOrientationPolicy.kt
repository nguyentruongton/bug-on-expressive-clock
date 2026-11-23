package app.expressiveclock

import android.app.Activity
import android.content.pm.ActivityInfo

/** Active-display policy, deliberately independent of the floating overlay's sensor rotation. */
internal fun activityOrientation(smallestWidthDp: Int): Int =
    if (smallestWidthDp < 600) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

private val calibrationOverrides = java.util.WeakHashMap<Activity, Unit>()

/** Main-thread only: calibration deliberately rotates independently of the normal app policy. */
internal fun Activity.allowClockCalibrationRotation(): () -> Unit {
    calibrationOverrides[this] = Unit
    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
    return {
        calibrationOverrides.remove(this)
        applyClockOrientationPolicy()
    }
}

internal fun Activity.applyClockOrientationPolicy() {
    if (calibrationOverrides.containsKey(this)) return
    val desired = activityOrientation(resources.configuration.smallestScreenWidthDp)
    if (requestedOrientation != desired) requestedOrientation = desired
}
