# Phase 3 usability pass

## Scope and repository

Read AGENTS.md; clean main baseline v0.1.12. All changes local. No version, SDK, dependencies, application ID, signing, releases or GitHub discussions changed. No files deleted. Existing Timer keypad/presets, island content geometry/animations, calibration profiles, slab portrait restriction, alarm scheduling and upcoming notifications are retained.

## World Clock

Default WorldClockStyle (Analogue/Digital) is stored through ClockStore/Preferences DataStore, with Analogue fallback for existing/unknown values. Settings > Miscellaneous > Default World Clock style opens a two-choice picker. ClockApp initializes the hoisted presentation once after settings load; subsequent manual switches do not overwrite the preference. Tap animates the same collapse fraction used by scrolling, preserving selected city and presentation across tabs and responsive branches. Standard Compose gesture cancellation prevents a scroll from becoming a tap. No ripple/grey rectangle is drawn.

The unfolded shared clock remains a single clock-only host, with the existing hand-motion state. Its header reuses the compact analogue/digital visibility/reel animation. The stored style fraction is blended with the existing horizontal shared-clock progress when moving into/out of Alarms. Vertical dragging works on the shared clock and city pane, with endpoint settling. A parent scrollable uses the same PagerState to route unconsumed horizontal drags from the clock sibling; existing modal/selection gates apply to both. The main digital readout uses the original 48 percent of its allotted width, with seconds restored following device feedback. City details glide upward using the same style progress. Compact mode retains the city title in both styles; unfolded title spacing is measured from the digital ink. Expanded drawing-layer outsets preserve the analogue glow during opacity transitions, preventing square clipping. Other readouts and the intentionally compact collapsed island are unchanged.

Physical Pixel Fold: folded tap changed analogue to digital. Unfolded portrait: larger hour/minute display, ripple-free tap, clock-origin horizontal swipe to Alarms and back with style/city retained, vertical drag back to analogue. Screenshots captured. Persistence of both preference values tested on both devices, restoring the original preference. A fresh cold-launch digital-default UI check was not independently exercised. A subsequent physical unfolded-landscape check (2152x2076, rotation 90) verified the larger digital readout with seconds, upward location movement, and glow during reversal. The refined compact digital presentation was also checked on S25.

## Fullscreen island

Uses Android 11+ WindowInsets visibility for BOTH status and navigation bars, with a known status-bar source. Transparent/edge-to-edge layouts are not considered fullscreen just because they occupy the screen. Unknown/OEM failures, lock screen and calibration previews keep visibility conservative. The non-fitting accessibility window can report zero insets on Samsung; WindowManager.currentWindowMetrics supplies the display bar state instead. Insets/window-change notifications are supplemented with a 500ms metrics sample only while an island window exists, because this overlay does not reliably receive same-Activity bar changes. State changes settle for 160ms to avoid repeated toggles. Initial attachment samples before showing to avoid an initial flash. Sampling stops when the overlay is removed.

Only native render/interaction view visibility changes during fullscreen. Windows/composition and Timer/Stopwatch data remain intact. Restoration respects the same global/profile visibility and lifecycle that created the window. No app-name allowlist, content inspection, node retrieval, gestures or additional Accessibility capability is used; the service subscribes only to window-state-change signals. API 26-29 conservatively retain existing visibility; automatic hiding is not implemented there.

S25 controlled edge-to-edge/immersive debug Activity: normal island visible, immersive island hidden, Home restored it. Running Stopwatch continued. A subsequent running Timer test showed the identical deadline and RUNNING state before/after hiding/restoration. Service restart/rebinding tested. No third-party video/game, screen lock/unlock or physical rotation/folding fullscreen matrix was tested. Typical fallback latency is under approximately 0.7 seconds. The debug Activity tests real Android immersive window APIs, not a mocked policy.

## Launcher label

Only activity-alias labels use the new launcher_name=Clock resource. Application/internal app_name remains Expressive Clock, as do IDs/icons/signing. PackageManager launcher resolution returned Clock on both connected physical devices. No claim of checking every third-party launcher.

## Analogue widget colours

Confirmed source defect: the colour control beneath Background wrote analogPrimaryColor, which resolves to the hour-hand/accent colour; the face stayed neutral. It now writes a separately persisted optional analogFaceColor and is explicitly labelled Background colour. Legacy primary/hand settings are preserved, with no new hand-colour controls. The face override applies only to analogue widgets. Custom dark faces receive contrasting indices, without changing hand colours; the targeted device probe verifies that dark-face contrast and hand independence. Preview colour cache keys now include the complete configuration, so custom changes are not stale. Both editor and actual RemoteViews resolve through WidgetColours.

Device instrumentation on both phones wrote a temporary negative-ID config, reloaded it through a fresh ClockStore, checked independent face/hour colours, changed only the face and verified the hand remained fixed, then deleted the temporary key. S25 comparison host displayed the production AnalogPreview and production ClockWidgetRenderer RemoteViews. Pink and blue faces matched after the preview crossfade settled; the green hour hand stayed unchanged. This is actual production RemoteViews in a debug host, not a newly placed home-screen widget. Existing launcher widgets were not edited or moved. Fresh launcher-host reload across reboot was not tested.

## Completed Timer

Existing foreground completion already had a single close-icon Stop button in each responsive branch; the ringing notification also said Stop. Both branches and the completion notification now say Dismiss, using their unchanged cancel callback/PendingIntent. It stops AlarmService, clears the Timer and cancels scheduling/notification. The finished island exits under the existing lifecycle rather than adding a duplicate dismissal action. There is no separate full-screen Timer completion Activity in this code; locked completion relies on the public alarm-category notification and opening Clock.

S25: finished screen captured with Dismiss; tapping it cleared the fixture Timer. A targeted instrumentation test ran a real 3-second Timer through AlarmManager, observed FINISHED/ringing service, sent the notification's actual Dismiss PendingIntent, then verified no Timer and no ringing service. Speaker audibility/lock-screen OEM delivery were not asserted. Fixture finally blocks removed owned Timer state. Existing alarms were not modified.

## Upcoming alarms and regression

UpcomingAlarm already uses Dismiss since v0.1.12. Its next-occurrence PendingIntent and recurrence behaviour are untouched. No additional wording change needed.

Passed ./gradlew assembleDebug testDebugUnitTest lintDebug lintRelease: 99 JVM tests, zero failures/errors; debug/release lint zero errors/fatals, existing warnings retained. FullscreenVisibilityTest covers hidden/visible/unknown bars, lock and preview policy. 12 project and 21 Installer Helper Python regressions passed. git diff --check passed. No broad fresh physical alarm ringing, widget reboot, Samsung scaling or navigation stress matrix is claimed.

Devices: Samsung SM-S931B/RFCY41EDGND, Android 16/One UI 8.0, 1080x2340, existing 420dpi override (physical 480), font scale 1.0. Pixel 11 Pro Fold/66231FDDJ003QS, Android 17, outer 1080x2342 and inner 2076x2152, 390dpi, portrait. No OPPO connected. No font/display/refresh/rotation settings changed. Instrumentation restarts mark Clock's Accessibility service crashed on Samsung; only Clock's service was rebound, retaining the exact original enabled-service lists. Temporary Timer/Stopwatch, default-style test preference and island test preference were restored; temporary widget and fixture snapshot keys removed.

## Artifacts

APK: /Users/sambeckman/Downloads/ExpressiveClock-phase3-refined-20261122-163252/ExpressiveClock-debug.apk

Screenshots and build/lint log: /Users/sambeckman/Downloads/ExpressiveClock-phase3-refined-20261122-163252

## Follow-up refinements and verification

WorldClockDetailsGeometryTest covers original analogue placement, monotonic upward movement, clamped progress and multiple dial sizes/12-24-hour formats. Latest debug build, 99 unit tests and both lint variants passed; 33 Python tests passed. Both devices received the final debug APK. App/service fixture state was restored and original enabled Accessibility lists retained. No landscape fullscreen, third-party video/game or lock-screen delivery claim is added by the World Clock landscape test.

Reference for visibility semantics: https://developer.android.com/reference/android/view/WindowInsets#isVisible(int)

## Files changed

- app/src/debug/AndroidManifest.xml
- app/src/debug/java/app/expressiveclock/ImmersiveProbeActivity.kt
- app/src/debug/java/app/expressiveclock/LayoutProbe.kt
- app/src/debug/java/app/expressiveclock/Phase3Probe.kt
- app/src/debug/java/app/expressiveclock/WidgetComparisonActivity.kt
- app/src/main/AndroidManifest.xml
- app/src/main/java/app/expressiveclock/alarm/AlarmService.kt
- app/src/main/java/app/expressiveclock/data/ClockStore.kt
- app/src/main/java/app/expressiveclock/data/Settings.kt
- app/src/main/java/app/expressiveclock/data/WidgetConfig.kt
- app/src/main/java/app/expressiveclock/overlay/FullscreenVisibility.kt
- app/src/main/java/app/expressiveclock/overlay/OverlayAccessibilityService.kt
- app/src/main/java/app/expressiveclock/overlay/OverlayManager.kt
- app/src/main/java/app/expressiveclock/ui/ClockApp.kt
- app/src/main/java/app/expressiveclock/ui/WorldClockDigitalReadout.kt
- app/src/main/java/app/expressiveclock/ui/screens/SettingsScreen.kt
- app/src/main/java/app/expressiveclock/ui/screens/TimersScreen.kt
- app/src/main/java/app/expressiveclock/ui/screens/WideClockPages.kt
- app/src/main/java/app/expressiveclock/ui/screens/WorldClockHeader.kt
- app/src/main/java/app/expressiveclock/ui/screens/WorldClockPanel.kt
- app/src/main/java/app/expressiveclock/widget/ClockWidgetRenderer.kt
- app/src/main/java/app/expressiveclock/widget/WidgetColours.kt
- app/src/main/java/app/expressiveclock/widget/WidgetConfigScreen.kt
- app/src/main/res/values/strings.xml
- app/src/main/res/xml/accessibility_service_config.xml
- app/src/test/java/app/expressiveclock/overlay/FullscreenVisibilityTest.kt
- app/src/test/java/app/expressiveclock/ui/screens/WorldClockDetailsGeometryTest.kt
- docs/development/PHASE3-USABILITY.md

## Original digital-size follow-up

Restored the exact pre-Phase-3 readout width fraction (.48 rather than .92), shared by rendering and location/detail geometry. Kept seconds, tap/scroll toggling, the saved default preference, moving city label and expanded glow layer bounds. No other Phase 3 changes reverted. Updated geometry regression to verify details track the original-sized ink.

S25 RFCY41EDGND: installed in place; verified analogue -> digital -> analogue by tap, scrolling to digital, and navigating to Timer and back with digital style and Sydney retained. No font/display/rotation/refresh settings changed. assembleDebug, testDebugUnitTest (100 tests), lintDebug, 12 Python checks and git diff --check passed. No fresh Fold test in this sizing follow-up.

Updated APK: /Users/sambeckman/Downloads/ExpressiveClock-world-original-size-20261122-180023/ExpressiveClock-debug.apk

Evidence: /Users/sambeckman/Downloads/ExpressiveClock-world-original-size-20261122-180023/evidence

Only WorldClockDigitalReadout.kt, WorldClockHeader.kt, WorldClockDetailsGeometryTest.kt and this report were edited in this follow-up; the earlier local Phase 3 work is preserved. No files deleted, version changes, commits, pushes or releases.
