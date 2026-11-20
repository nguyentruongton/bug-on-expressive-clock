# Restored expanded Timer and Stopwatch cards

## Original implementation and cause

Reviewed AGENTS.md and the clean main working tree at v0.1.8 before starting. No pre-existing modifications were present. Compared v0.1.6 DynamicIslandOverlay.kt:ExpandedIslandCard with the current source and the separate Expressive Island project clockui/ClockCardUi.kt:ExpandedClockCardContent. Expressive Island was read only.

The original Clock card used a Box containing a left-aligned time/caption Column and a separately right-aligned three-button Row. The readout had a fixed38dp cap height, controls were62dp with6dp gaps, and their independently measured widths could occupy the same horizontal space. The Samsung fix replaced that composition with two centred rows and expanded the default height from152dp to168dp. Expressive Island retains the left time/right controls arrangement with proportional narrow-screen compaction.

## Changes

Restored left-aligned time with caption beneath and three scalloped controls on the right. The normal expanded height is152dp again, with additional height for accessibility text;42dp expanded corners, black borderless surface, colours and spring/blur/morph/ticker animation specifications remain unchanged. Existing width cap, cutout clearance, position calculation, landscape anchors, calibration profiles/offsets and compact collapsed formatting are retained.

Horizontal outer padding responds to card width (5.5%, clamped10–24dp). The pure expandedCardLayout allocator reserves separate readout/control regions, adaptive4–8dp control gaps and8–14dp separation. Buttons adapt between62dp and a48dp minimum touch target. Digit fitting measures the widest digit and separators using the actual ClockFace font, includes1.5dp inter-cell spacing, and fits a physical cap height up to38dp into the reserved region with a2dp safety margin. Measurements are cached across ordinary ticking by width/text length/density. The physical cap-height numeral box uses neutral font scaling to avoid enlarged glyphs clipping its fixed ink box; captions and action labels still respect accessibility font scale. Caption autosizing and up to two lines handle larger fonts. Only exceptionally narrow cards fall back to left readout above right-aligned controls. This fallback still preserves the same hierarchy and button style.

No Timer/Stopwatch receiver, scheduler, persistence or action arithmetic changed. No app version/signing/Gradle/production manifest/settings changes.

## Device testing

S25 RFCY41EDGND:1080x2340, density480 with existing420 override, system font1.0. Pixel11 Pro Fold66231FDDJ003QS: outer1080x2342,density390,font1.0, CLOSED state without a posture override. No system settings or persisted Timer/Stopwatch/calibration values were changed.

Added an explicit debug-only ExpandedIslandProbeActivity hosting production DynamicIslandOverlayContent with ephemeral local timer/stopwatch state. Its default path exercises the full rendered overlay composable and transitions, but does not create a background accessibility-service window. Width-stress cases host ExpandedIslandCard at constrained widths with conservative20dp outer padding. Font overrides are local Compose Density overrides, not changes to system settings.

S25 visual checks: Timer4m47s, Stopwatch1h1m1s, Timer99h59m59s,320dp cards with2x fonts,240dp/2x-font compact fallback, and logical landscape via the test Activity. Readout/caption/control separation verified in screenshots. A2x-font caption truncation found during testing was fixed and recaptured. The S25 locked before the remaining action tests; those action checks are NOT claimed as S25 passes. Final APK installation succeeded even while locked. Owner unlock requested, not bypassed.

Fold outer display: Timer/Stopwatch visual checks, Timer1000h at320dp/2x fonts in dark theme, and actual outer landscape2342x1080 via Activity orientation request. Timer resume/pause,+60s,cancel and Stopwatch start/pause,lap,reset callbacks were tapped on the physical outer display and confirmed by debug-host logs. Six rapid expansion changes returned to a readable expanded card, with no crash observed. These validate composable controls and callbacks, not end-to-end background service scheduling. An early UIAutomator action loop failed because the running ticker prevented an idle dump; callback logs and direct touches were used instead. Early Fold screenshots contained an Android17 warning prefix; only the transport prefix was removed to read their original PNG payload. Final Fold captures explicitly select the outer physical display ID.

Unfolded physical display remains unverified in this pass: the Fold stayed CLOSED and a user-assisted opening was requested. No posture override was applied. Exact GalaxyS26/Android17/OneUI9 remains unavailable. Smoothness is not certified as120fps; animation parameters are unchanged and transition retargeting was exercised. No original optical camera alignment claim is made.

## Build and regression checks

`./gradlew assembleDebug testDebugUnitTest lintDebug` passed.95 JVM tests,zero failures/errors;12 project and21 Installer Helper Python checks passed. New ExpandedCardLayoutTest covers disjoint allocation,48dp minimum controls, width bounds and compact fallback. Initial lint WrongConstant findings were confined to the new debug host and corrected to ActivityInfo constants. Final git diff --check is clean. Full app alarm/widget/swipe regression was not physically repeated because production changes are limited to expanded presentation.

## Screenshots and artifact

APK: /Users/sambeckman/Downloads/ExpressiveClock-restored-expanded-island-20261120-151544/ExpressiveClock-debug.apk
SHA256: 76c803f00a6d91a140e86ab7d923fac0e4d2bc49d685edc7d640745c494fc83c

- S25 Timer before: /Users/sambeckman/Downloads/ExpressiveClock-restored-expanded-island-20261120-151544/evidence/before/s25-timer.png
- S25 Timer after: /Users/sambeckman/Downloads/ExpressiveClock-restored-expanded-island-20261120-151544/evidence/after/s25-timer.png
- S25 Stopwatch before/after: evidence/before/s25-stopwatch.png and evidence/after/s25-stopwatch.png
- Narrow/font2: evidence/after/s25-narrow-font2.png
- Extreme compact fallback: evidence/after/s25-extreme-240-font2.png
- S25 landscape: evidence/after/s25-landscape.png
- Fold before: evidence/before/fold-timer.png
- Fold Timer final: evidence/after/fold-timer-added-paused-final.png
- Fold Stopwatch final: evidence/after/fold-stopwatch-lap-final.png
- Fold1000h/font2/dark: evidence/after/fold-1000hours-font2-dark.png
- Fold landscape: evidence/after/fold-landscape-final.png
- Fold action evidence: evidence/fold-controls.log

## Files and repository state

Modified: app/src/main/java/app/expressiveclock/overlay/DynamicIslandOverlay.kt; IslandGeometry.kt in the same directory; app/src/debug/AndroidManifest.xml.
Created: app/src/debug/java/app/expressiveclock/ExpandedIslandProbeActivity.kt; app/src/test/java/app/expressiveclock/overlay/ExpandedCardLayoutTest.kt; this report. Deleted:none.

Branch main. All implementation changes are local, unstaged and uncommitted. No GitHub/Play writes or release changes were made. Original production signing and all unrelated source remain unchanged.


## Follow-up: S25 unlocked and Fold opened in portrait

This follow-up supersedes the earlier S25-action and unfolded-portrait verification limits. Android confirmed physical Fold state OPENED(2),2076x2152,rotation0 with no posture override. S25 remained at its original420dpi override/font1; Fold390dpi/font1.

S25 test-host checks passed Timer resume/pause,+60s,cancel; Stopwatch start/pause,lap,reset; and six expansion changes. Fold inner portrait checks passed the same controls and expansion changes, plus short Timer values,1000-hour Timer at320dp/2x font in dark mode and long Stopwatch at320dp/2x font. Callback logs use only fresh events from each run. Screenshots confirm readout/control clearance and readable labels. Tests still use the production composables hosted with ephemeral data, not background service state/scheduling.

The first inner expansion attempt hit the system taskbar because the debug host footer was too low. That run is excluded, and unrelated-app frames were removed from the test evidence. Moved only the debug host footer96dp upward and repeated the inner tests successfully. No production source changes in this follow-up. Android settings and persisted app state were not modified. Both devices returned to MainActivity; Fold left OPENED in portrait. Physical unfolded-landscape verification is still not claimed.

Latest assembleDebug/testDebugUnitTest/lintDebug passed with95 tests;33 Python checks passed earlier in this same implementation pass. Final debug-host APK installed on both devices. Fresh export: /Users/sambeckman/Downloads/ExpressiveClock-expanded-island-verified-20261120-152657/ExpressiveClock-debug.apk
SHA256: 5786d82ae54f1b63efd5ad6dea726072eeaa9eeeb24d3594f87372ddc6e417db

Evidence in /Users/sambeckman/Downloads/ExpressiveClock-expanded-island-verified-20261120-152657/evidence/after:
- s25-final-timer-before-actions.png
- s25-final-timer-added-paused.png
- s25-final-stopwatch-lap.png
- fold-inner-final-timer-before-actions.png
- fold-inner-final-expanded-after-rapid-transitions.png
- fold-inner-final-stopwatch-lap.png
- fold-inner-final-1000hours-font2-dark.png
- fold-inner-final-stopwatch-font2.png

Action logs: evidence/s25-final-controls.log and evidence/fold-inner-final-controls.log. Source remains local/uncommitted on main. Only the debug host and this report changed during this follow-up; no files deleted or created in the repository.


## Overlap-mode top-spacing correction

The expanded card was reserving the full status-bar/cutout band as additional internal top clearance even when OVERLAP_CUTOUT was selected. For an upward-calibrated Fold card this added both padding and height above the centred content. expandedContentClearance now returns zero in overlap mode, retaining normal16dp content padding; below-status-bar mode retains its existing calculation and anchor. Camera/display geometry, saved calibration values, collapsed layout and animation specifications are unchanged.

Reproduced on the unfolded portrait Fold using the production-composable debug host with a temporary offsetY=-200dp input, rather than changing user calibration. Captured old/new implementations at the identical fixture position. Before:/Users/sambeckman/Downloads/ExpressiveClock-overlap-spacing-20261120-180744/before.png. After:/Users/sambeckman/Downloads/ExpressiveClock-overlap-spacing-20261120-180744/after.png. Normal test-host placement did not reproduce the gap and is not used as the before comparison. Actual service-window optical cutout alignment was not reverified in this follow-up.

Modified in this follow-up: DynamicIslandOverlay.kt, IslandGeometry.kt, ExpandedCardLayoutTest.kt, debug ExpandedIslandProbeActivity.kt and this report. No new/deleted repository files in this follow-up. assembleDebug/testDebugUnitTest/lintDebug passed with96 tests,zero failures;12 project Python checks and diff whitespace check passed. Fresh APK installed on S25 and Fold: /Users/sambeckman/Downloads/ExpressiveClock-overlap-spacing-20261120-180744/ExpressiveClock-debug.apk. No persisted device/app settings changed. All work remains local/uncommitted on main.
