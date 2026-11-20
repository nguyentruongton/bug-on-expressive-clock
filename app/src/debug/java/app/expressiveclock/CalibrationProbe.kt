package app.expressiveclock

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import app.expressiveclock.data.*
import kotlinx.coroutines.runBlocking

/** Tests only calibration keys and restores them, leaving all other settings/state untouched. */
class CalibrationProbe : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val report = Bundle(); var result = Activity.RESULT_OK
        runBlocking {
            val store = ClockStore(targetContext)
            val original = store.settingsNow().theme
            try {
                val baseline = IslandProfile.entries.associateWith { original.calibration(it) }
                IslandProfile.entries.forEachIndexed { index, profile ->
                    val value = IslandCalibration(x = index * 17f, y = 20f + index, width = index * 30f, height = index.toFloat())
                    store.update { it.copy(theme = it.theme.withCalibration(profile, value)) }
                    val reread = ClockStore(targetContext).settingsNow().theme
                    check(reread.calibration(profile) == value)
                    baseline.filterKeys { it.ordinal > index }.forEach { (other, unchanged) -> check(reread.calibration(other) == unchanged) }
                }
                report.putString("stream", "PASS: four profiles persisted independently and untouched profiles retained their baseline\n")
            } catch (e: Throwable) { result = Activity.RESULT_CANCELED; report.putString("stream", e.stackTraceToString()) }
            finally {
                store.update { current -> current.copy(theme = current.theme.copy(
                    overlayOffsetX = original.overlayOffsetX, overlayOffsetY = original.overlayOffsetY,
                    overlayWidthOffset = original.overlayWidthOffset, overlayHeightOffset = original.overlayHeightOffset,
                    overlayProfiles = original.overlayProfiles)) }
            }
        }
        finish(result, report)
    }
}
