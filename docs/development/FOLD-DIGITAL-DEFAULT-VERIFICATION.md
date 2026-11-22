# Unfolded Fold: smaller digital clock verification

Physical check on 22 November 2026 using Pixel 11 Pro Fold, serial 66231FDDJ003QS, Android 17, unfolded portrait, 2076 x 2152 pixels at density 390. Installed the latest local debug APK in place, preserving app data. No application source changes were needed during this verification.

Temporarily changed Default World Clock style from Analogue to Digital through Settings. Kept Default startup page set to Timer. Confirmed a fresh Activity opened directly on World Clock with the smaller HH:mm:ss readout. Also opened a fresh Activity with the normal Timer startup and swiped to World Clock: Digital was correctly selected. This was an Activity recreation check, not a process cold-start test.

Verified swipes and bottom-navigation taps between Alarms and World Clock. Alarms displayed its local analogue clock on the right; World Clock returned to its smaller digital presentation on the left. Selected Queensland, which was one hour behind local Sydney, and confirmed the city and digital style survived navigation. Reviewed captured transition frames, partial-swipe snapback and rapid alternating tab taps. No readout clipping, duplicate analogue clock or abrupt size jump was observed. No frame-time profiling was performed.

Restored the original Analogue default through Settings and verified the startup preference remained Timer. The currently displayed World Clock stayed digital, confirming the default preference does not override the user's current manual presentation. No alarm schedules or saved city entries were changed. Accessibility dumpsys reported no crashed services after testing. System rotation, density, fonts and refresh-rate preferences were not changed.

Evidence: /Users/sambeckman/Downloads/ExpressiveClock-Fold-digital-verification-20261122

APK tested: /Users/sambeckman/Downloads/ExpressiveClock-alarm-confirmations-20261122-183949/ExpressiveClock-debug.apk

This follow-up covered unfolded portrait only. Landscape, folded mode and accessibility scaling were not re-tested here. No new Gradle run was necessary for this test-only pass; the tested APK previously passed assembleDebug, testDebugUnitTest and lintDebug, with 107 JVM tests and 12 Python regression checks. git diff --check passed during this follow-up.

Only this report was added. Existing local work remains uncommitted on main. Nothing was committed, pushed or published.
