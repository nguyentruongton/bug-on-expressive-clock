package app.expressiveclock.overlay

import app.expressiveclock.data.*
import androidx.datastore.preferences.core.*
import org.junit.Assert.*
import org.junit.Test

class IslandProfileTest {
    @Test fun fractionalCalibrationSurvivesSavingAndOldProfileKeysMigrate() {
        val profile = IslandProfile.FOLDED_PORTRAIT
        val prefs = mutablePreferencesOf(intPreferencesKey("island_${profile.name}_x") to 18)
        assertEquals(18f, prefs.toSettings().theme.calibration(profile).x, 0f)
        val value = IslandCalibration(x = 18.375f, y = -2.125f, width = 7.625f, height = 1.25f)
        val settings = prefs.toSettings()
        prefs.writeSettings(settings.copy(theme = settings.theme.withCalibration(profile, value)))
        assertEquals(value, prefs.toSettings().theme.calibration(profile))
        // Old integer keys remain untouched and cannot override the precise value.
        assertEquals(18, prefs[intPreferencesKey("island_${profile.name}_x")])
        prefs[intPreferencesKey("island_${profile.name}_x")] = 99
        assertEquals(value, prefs.toSettings().theme.calibration(profile))
    }
    @Test fun alignmentAndProfileOptionsPersistIndependently() {
        val original = ClockSettings()
        val value = IslandCalibration(enabled = false, alignment = IslandAlignment.RIGHT,
            expandedPosition = IslandExpandedPosition.BELOW_STATUS_BAR)
        val changed = original.copy(theme = original.theme.withCalibration(IslandProfile.FOLDED_LANDSCAPE, value))
        val prefs = mutablePreferencesOf(); prefs.writeSettings(changed)
        assertEquals(value, prefs.toSettings().theme.calibration(IslandProfile.FOLDED_LANDSCAPE))
        assertTrue(prefs.toSettings().theme.calibration(IslandProfile.FOLDED_PORTRAIT).enabled)
        val left = islandGeometry(443f, 74f, null, alignment = IslandAlignment.LEFT)
        val centre = islandGeometry(443f, 74f, null, alignment = IslandAlignment.CENTRE)
        val right = islandGeometry(443f, 74f, null, alignment = IslandAlignment.RIGHT)
        assertTrue(left.centerX < centre.centerX)
        assertTrue(right.centerX > centre.centerX)
        assertTrue(right.centerX + right.width / 2 <= 443f)
    }
    @Test fun expandedWidthIsOrientationInvariantAndCompactOnInnerDisplays() {
        assertEquals(islandExpandedWidth(411f, 891f), islandExpandedWidth(891f, 411f), 0f)
        assertEquals(islandExpandedWidth(443f, 961f), islandExpandedWidth(961f, 443f), 0f)
        assertEquals(420f, islandExpandedWidth(883f, 852f), 0f)
        assertTrue(islandExpandedTop(200f, 240f, 168f) + 168f <= 232f)
    }
    @Test fun coverDisplaysLockAndInnerDisplaysReleaseOrientation() {
        val portrait = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val system = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        assertEquals(portrait, app.expressiveclock.activityOrientation(411))
        assertEquals(portrait, app.expressiveclock.activityOrientation(443))
        assertEquals(portrait, app.expressiveclock.activityOrientation(599))
        assertEquals(system, app.expressiveclock.activityOrientation(600))
        assertEquals(system, app.expressiveclock.activityOrientation(840))
        // Folding back must restore the lock; there is no retained hardware exemption.
        assertEquals(portrait, app.expressiveclock.activityOrientation(443))
    }
    @Test fun slabHasNoFoldProfilesAndRotationSelectsEachFoldProfile() {
        assertNull(islandProfile(false, false, 1080, 2340))
        assertNull(islandProfile(false, true, 2340, 1080))
        assertEquals(IslandProfile.FOLDED_PORTRAIT, islandProfile(true, false, 1080, 2340))
        assertEquals(IslandProfile.FOLDED_LANDSCAPE, islandProfile(true, false, 2340, 1080))
        assertEquals(IslandProfile.UNFOLDED_PORTRAIT, islandProfile(true, true, 2076, 2152))
        assertEquals(IslandProfile.UNFOLDED_LANDSCAPE, islandProfile(true, true, 2152, 2076))
    }
    @Test fun migrationAndPreferenceRoundTripPreserveIndependentProfiles() {
        val prefs = mutablePreferencesOf(intPreferencesKey("overlayOffsetX") to 27,
            intPreferencesKey("overlayOffsetY") to -1, intPreferencesKey("overlayWidthOffset") to 90)
        val legacy = prefs.toSettings()
        IslandProfile.entries.forEach { assertEquals(27f, legacy.theme.calibration(it).x) }
        val changed = legacy.copy(theme = legacy.theme.withCalibration(IslandProfile.FOLDED_LANDSCAPE,
            IslandCalibration(x = -75f, y = 50f, width = 100f, height = 12f)))
        prefs.writeSettings(changed)
        val restored = prefs.toSettings()
        assertEquals(changed.theme.overlayProfiles, restored.theme.overlayProfiles)
        prefs[intPreferencesKey("overlayOffsetX")] = 500
        assertEquals(27f, prefs.toSettings().theme.calibration(IslandProfile.UNFOLDED_PORTRAIT).x)
        assertEquals(-75f, prefs.toSettings().theme.calibration(IslandProfile.FOLDED_LANDSCAPE).x)
    }
    @Test fun editingOneProfileKeepsAllOtherLegacyBaselinesAndResetIsLocal() {
        val old = ThemeSettings(overlayOffsetX = 17, overlayOffsetY = 23, overlayWidthOffset = 44, overlayHeightOffset = 5)
        val edited = old.withCalibration(IslandProfile.UNFOLDED_LANDSCAPE, IslandCalibration(x = 50f))
        IslandProfile.entries.filter { it != IslandProfile.UNFOLDED_LANDSCAPE }.forEach {
            assertEquals(old.legacyCalibration(), edited.calibration(it))
        }
        val reset = edited.withCalibration(IslandProfile.UNFOLDED_LANDSCAPE, IslandCalibration())
        assertEquals(IslandCalibration(), reset.calibration(IslandProfile.UNFOLDED_LANDSCAPE))
        assertEquals(old.legacyCalibration(), reset.calibration(IslandProfile.FOLDED_PORTRAIT))
    }
}
