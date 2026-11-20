# Island orientation/profile development pass — 20 November 2026

Baseline: main c5098387e1113a810a787b689e80680ba5798773 (v0.1.7). Clean working tree at start; all changes in this pass are local and unstaged. No commits, pushes, merges, releases, comments or PR changes authorised/performed. Expressive Island was inspected read-only.

## Cause and implementation

The old cutout selector accepted any cutout in the upper half of the display. Rotating a centred hole-punch puts its bounding rectangle at the left/right edge, around the display's vertical centre. Treating that rectangle as a *top* camera gives the island a mid-screen Y anchor. The old layout also took width from Configuration, which can describe a portrait Activity rather than the independently rotating WindowManager overlay.

The overlay now uses a shared physical-display observer (DisplayManager, real display metrics/rotation and hinge sensor where available). Only cutouts touching the top edge and occupying less than half the display height are used as top-camera anchors; side cutouts use a conservative top-screen fallback. Collapsed position and expanded anchor are clamped to visible height. Expanded width is orientation-invariant for phone-sized displays and capped at420dp, so landscape no longer creates a full-width banner. User offsets are retained. Positional motion uses translation rather than padding, so a spring's transient negative excursion cannot throw a negative-padding exception; spring parameters, ticker motion and colours are unchanged.

The checked-out v0.1.7 did NOT lock the main Activity. After explicit owner clarification in this pass, MainActivity now requests portrait on non-foldable phones, leaves foldables unrestricted, and preserves tablet rotation. This Activity policy does not lock the overlay or change system auto-rotation. Debug-only display host requests portrait/landscape for its own window to test background overlays without changing system settings.

A previous cold-start guard waited for an Activity before restoring any island, even with stored running state and a connected AccessibilityService. It was removed: state/service readiness now suffices on restart. Service configuration callbacks refresh display metrics; DisplayManager handles display/config changes independently of Activity lifecycle.

## Profiles, settings and migration

Four stable IDs: Folded Portrait, Folded Landscape, Unfolded Portrait, Unfolded Landscape. Each stores X/Y/width/height. Foldable capability comes from feature declarations or an actual hinge sensor, never a model-name list. Hinge posture is primary; known foldables use shortest physical dp as fallback when no hinge sample is available. Standard phones use their existing single calibration and see no fold selectors. Generic tablets are not classified as foldable solely because they are wide.

Settings identify Active and Editing profiles, offer manual selection and Follow active display, save immediately, and reset only the edited profile. Preview always uses the active display; inactive-profile editing explicitly explains that it is saved without moving the current island. Controls retain ±200dp horizontal, −10..+150dp vertical, −60..+300dp width and ±20dp height adjustment ranges. The dialog scrolls when necessary.

Legacy preference keys remain intact. Each absent profile field reads from the matching legacy value. The next ordinary atomic preferences edit materialises those baselines; explicitly saved profile keys take precedence forever afterward. Profile edits transform the latest settings rather than overwriting another setting from a captured dialog snapshot. Regression tests cover initial migration, independent edits/reset, serialization round trip and immunity to later legacy-key changes. A debug-only CalibrationProbe verified real DataStore rereads for all four profiles independently on BOTH connected phones, then restored only their calibration keys.

## Physical testing and evidence

S25: RFCY41EDGND / SM-S931B / Android16 / OneUI8.0,1080x2340,density override420. Fold:66231FDDJ003QS / Pixel11 Pro Fold / Android17,outer1080x2342,inner2076x2152,density390. OPPO absent. Both ADB authorised. Original settings/state/display dumps were recorded. No system auto-rotation, refresh-rate, density, font or device-state override was changed by this pass. User physically rotated/folded devices; additional window-orientation tests use the debug host. Original timer/stopwatch fixtures were restored in finally cleanup.

S25 physical landscape2340x1080 was recorded after user rotation. The test host verified Timer and Stopwatch collapsed/expanded overlays at rotation0 and1, remaining top-aligned with readable separated controls. Inner Fold2152x2076 landscape, physical state OPENED without an override, was recorded; Timer/Stopwatch overlays remain top-aligned and the active label correctly identifies Unfolded Landscape. Both orientations were requested by the host, but Android's large-screen orientation policy kept the inner display landscape: those portrait-labelled files are NOT counted as Unfolded Portrait verification. Subsequent user-assisted physical rotation verified Unfolded Portrait2076x2152 with rotation0 and the correct active label. A running Stopwatch continued from Unfolded Portrait into Folded Portrait1080x2342 when the owner physically closed the Fold; the active label switched correctly. Folded Landscape2342x1080/rotation1 was then verified with the debug host. Both Timer and Stopwatch collapsed/expanded captures were visually checked across all four actual profiles. The earlier misleading portrait-labelled inner-landscape files remain excluded. Exact optical camera alignment still requires an external photograph.

Alarm regression probe on S25 passed custom/fallback audio, timer expiry/foreground ringing, snooze scheduling, ringing notification dismissal, stale-start protection, skip occurrence and audio-focus handling. Widget capture completed but no widget was visible: no physical ticking PASS is claimed. Existing widget Python/JVM checks cover unchanged widget source. Exhaustive World Clock/swipe/launcher regression and every external-monitor/half-open posture combination are not claimed.

## Signing review and comparison

See PR5-REVIEW.md for findings, local comparison and friendly draft comment. The contributor-copy checks passed debug/tests/lint with no private credentials; release APK/AAB fail clearly without them. Maintainer release builds retain the existing certificate. No silent debug fallback, package/version changes or credentials in Git.

## Limitations

Hinge sensor and feature reporting vary by vendor; half-open devices using an outer screen and unsupported external display routing need further hardware coverage. The observer follows Android's active default display (including this Fold's changing outer/inner default panel); it does not create extra islands on secondary monitors. Optical camera bounds cannot be validated by screenshots. No OPPO or exact S26/Android17/OneUI9 physical verification. Physical tests and automated checks are reported separately. The debug test host/fixtures are absent from production release manifests.

## Final checks and delivery

`./gradlew assembleDebug testDebugUnitTest lintDebug lintRelease assembleRelease bundleRelease` passed. 89 JVM tests, zero failures/errors; Python12 project +21 Installer Helper tests passed; both lint variants have zero errors. The final source also passed assembleDebug/testDebugUnitTest/lintDebug/lintRelease in the credential-free contributor copy. Missing-credential release APK/AAB attempts and incomplete/missing-file cases failed as expected, not bypassed. Production APK signing still matches the existing certificate.

Settings UI was visually checked on both devices: S25 has only its four controls; Fold exposes Active/Editing labels, four profile choices and Follow active display. Changed inactive Unfolded Landscape X from0 to1 through the actual UI, confirmed Folded Portrait stayed0, then restored Unfolded Landscape X to0. Temporary preview was dismissed on both phones. No snapshot file remained after timer/stopwatch restoration. The Fold ended CLOSED with no device-state override; min/peak refresh settings remained absent. User-selected physical orientation/auto-rotation was not overwritten.

An inner-display World Clock screenshot confirms the existing two-pane layout and bottom navigation. Slab Activity requestedOrientation is PORTRAIT; the independent landscape host and island still report rotation1. Compact time units and borderless surfaces remain unchanged. No source files were deleted. All source, tests, Gradle candidate and documentation remain uncommitted on main.

## Final file inventory

```
## main...origin/main
 M app/build.gradle.kts
 M app/src/debug/AndroidManifest.xml
 M app/src/main/java/app/expressiveclock/MainActivity.kt
 M app/src/main/java/app/expressiveclock/data/ClockStore.kt
 M app/src/main/java/app/expressiveclock/data/Settings.kt
 M app/src/main/java/app/expressiveclock/overlay/DynamicIslandOverlay.kt
 M app/src/main/java/app/expressiveclock/overlay/IslandGeometry.kt
 M app/src/main/java/app/expressiveclock/overlay/OverlayAccessibilityService.kt
 M app/src/main/java/app/expressiveclock/overlay/OverlayManager.kt
 M app/src/main/java/app/expressiveclock/ui/screens/SettingsScreen.kt
?? app/src/debug/java/app/expressiveclock/CalibrationProbe.kt
?? app/src/debug/java/app/expressiveclock/IslandDisplayProbeActivity.kt
?? app/src/main/java/app/expressiveclock/data/IslandCalibration.kt
?? app/src/main/java/app/expressiveclock/overlay/IslandDisplayMonitor.kt
?? app/src/test/java/app/expressiveclock/overlay/IslandProfileTest.kt
?? docs/development/
```

## Screenshot-matched calibration UI follow-up

Replaced the modal/dropdown editor with a full-screen configuration list and full-page profile editors, following the supplied Expressive Island screenshots. Foldables expose four cards; standard phones now expose Portrait/Landscape only. The current profile has an outlined card/Current label, each profile has an enable switch, and the editor has Left/Centre/Right alignment, all four existing sliders, a per-profile expanded-card position choice (below status bar or overlap camera cutout), and Reset this profile. Immediate saving and live preview remain. This supersedes the earlier dialog/Follow active display UI described above; existing profile IDs and coordinate values are retained. Standard-phone portrait/landscape entries use the same stable portrait/landscape preference slots, initially seeded from legacy calibration.

New fields migrate to enabled=true, Centre alignment and Overlap camera cutout, preserving the previous default appearance. They are persisted per profile; active-profile enablement controls actual visibility, and expanded-card placement is passed to the renderer. The alignment changes the anchor before applying the saved offset and screen-edge clamp. No mechanical/spring tuning, border or compact duration-format change. Status-bar icon contrast follows the full-screen editor's theme.

Final follow-up verification: assembleDebug, testDebugUnitTest, lintDebug and lintRelease passed;90 JVM tests/zero failures and12 Python regression tests passed. Per-profile option persistence/alignment bounds have additional regression coverage. Full-page UI inspected on the S25 and Fold; changed Right alignment in the Fold editor, observed the preview move, restored Centre and confirmed the stored value was Centre. Feature switches and expanded placement options have persistence coverage; the complete new option matrix has not been physically re-run across all four postures. Temporary alignment edits were restored. Expressive Island remained read-only. All changes remain local/unstaged on main.


## Calibration motion and shallow-cutout correction (09:20 follow-up)

The Horizontal alignment selector now uses a single rounded track and one spring-animated primary-colour indicator that glides between Left/Centre/Right, matching the supplied reference structure while retaining Clock's palette. Selection semantics and touch ripple remain. Calibration pages slide in; predictive back follows gesture progress, reveals the retained previous page, springs back on cancellation, and slides out on completion. Toolbar back uses the same positional transition; platform Dialog fading is disabled. S25 screenshots captured partial back, cancelled back (identical to the starting editor) and completed back to the overview. A Fold partial gesture also revealed the retained overview; ambiguous later Fold captures were excluded from verification.

The S25's reported cutout is Rect(511,0,569,91), density2.625, statusbar91px. The previous geometry required a38/39dp capsule to fit vertically within a34.67dp statusbar, causing a deliberate but inappropriate below-statusbar fallback. Horizontal clearance still fits; removed that strict vertical condition and clamp the camera-centred top against the display edge. Default S25 preview now starts at0dp instead of40.67dp; hardware photographs are still required to confirm exact physical lens centring because Android's rectangle includes space above the lens. Off-centre cameras without horizontal icon clearance retain the below-statusbar automatic fallback.

Removed the artificial -10dp upward calibration restriction. Vertical slider range now spans plus/minus the current display height in dp, with1dp arrow adjustment. Saved values are preserved; the renderer clamps only to visible display bounds. Existing use of positional offset rather than animated padding prevents negative spring positions creating illegal padding; island animation specifications remain unchanged. Updated geometry regression covers extreme offsets and shallow S25 cutout. S25 UI tested at -878dp and +866dp, no crash; top/bottom remained reachable. Restored original0dp vertical offset and Centre alignment; preview dismissed. All unrelated device settings/state retained.

Final source: assembleDebug, testDebugUnitTest (90 tests), lintDebug and12 Python regression checks passed. Installed the final APK successfully on S25 and Pixel Fold. Latest S25 preview, alignment and extreme-offset checks passed; four-posture hardware matrix was not repeated after this last geometry change. Earlier release/signing checks remain as documented, not rerun after this UI/geometry-only follow-up.

Evidence: /tmp/clock-predictive-back/s25-editor-before.png (old low position), s25-partial.png, s25-cancelled.png, s25-committed.png, s25-alignment-crossing.png, s25-camera-fixed.png, s25-offset-high.png, s25-offset-low.png, s25-restored-final.png. Full regression log: /tmp/clock-s25-cutout-final.log. Export: /Users/sambeckman/Downloads/ExpressiveClock-calibration-motion-20261120-092011/ExpressiveClock-debug.apk; SHA2568996a14f700240dec3c3f98734c9f6ec6509e561a439ea70b102a412057fc417. Latest follow-up modifies SettingsScreen.kt, IslandGeometry.kt, IslandGeometryTest.kt and this report; no new/deleted files for this follow-up. All work remains local and uncommitted on main.


## Slider preview ordering fix (09:27)

Reported live-preview stutter traced to two writers: updateCalibrationPreview published the newest slider settings immediately, but the asynchronous DataStore collector subsequently replaced them with earlier saved settings via syncOverlayState. Separated calibrationPreviewSettings from persisted currentSettings; while the editor preview is active, sync uses the latest preview snapshot, and on dismissal resumes the persisted source. Continuous settings saving is unchanged. Removed redundant WindowManager.updateViewLayout calls for the already full-screen preview, including those caused by saved-settings emissions. Normal overlay interactions, animation parameters, mechanical digits and morphs are unchanged.

This follow-up modifies OverlayManager.kt and this report only; no files added/deleted. assembleDebug,90 JVM tests,lintDebug,12 Python checks and diff whitespace check passed. Final APK installed on S25 and Pixel Fold. An8-second S25 screen recording and continuous horizontal drags were collected (/tmp/clock-slider-check.mp4); no crash observed. Recording was not frame-analysed, so do not claim measured frame-rate improvement. User calibration values visible at the end included width197dp/vertical-62dp; these were preserved rather than reset. No system settings changed. Export: /Users/sambeckman/Downloads/ExpressiveClock-smooth-calibration-20261120-092702/ExpressiveClock-debug.apk, SHA25642fbf769140763ac75898d15ec6206527ec7a67d94a60214c956da039f155961. All earlier local work retained, unstaged/uncommitted on main.


## Stepless slider follow-up (09:30)

Removed whole-dp rounding from all four slider callbacks. IslandCalibration uses Float dp coordinates/dimensions throughout preview and per-profile persistence. New *_dp float preference keys take precedence, falling back to existing per-profile integer keys and then legacy single-profile keys. Existing keys are retained, and migration preserves their exact numeric value. The legacy single-profile compatibility fields remain integers; active profile editing uses the precise float keys. Arrow buttons adjust by1dp from the current value, while dragging remains continuous. Labels display up to one decimal place without quantising the stored value. Existing island animation parameters are unchanged.

Files modified in this follow-up: IslandCalibration.kt, ClockStore.kt, SettingsScreen.kt, CalibrationProbe.kt, IslandProfileTest.kt and this report; no files added/deleted. Added coverage for fractional-value round trips and old profile-key migration/precedence. assembleDebug,91 JVM tests,lintDebug,12 Python checks and git diff --check passed. Final APK installed on S25 and Pixel Fold, and the independent-profile DataStore probe passed on both, restoring original calibration afterward. Final smoothness has not been measured visually/frame by frame. Export: /Users/sambeckman/Downloads/ExpressiveClock-stepless-calibration-20261120-093034/ExpressiveClock-debug.apk. All changes remain local/uncommitted on main, preserving the rest of the working tree.


## Preview visibility and Reset follow-up

S25 logs showed BadTokenException adding TYPE_ACCESSIBILITY_OVERLAY after a test restart: accessibility remained enabled but its service token was unavailable. Calibration now renders the existing DynamicIslandOverlayContent directly above the full-screen settings editor, independent of the service window. Editing state is hoisted so the preview and sliders share fractional calibration immediately. Background overlay behaviour/animation parameters and saved values are unchanged. Reset now uses a rounded, tinted FilledTonalButton with bold Reset label; immediate saving remains. This follow-up changes SettingsScreen.kt and this report only; no added/deleted files.

assembleDebug,91 JVM tests,lintDebug,12 Python checks and diff whitespace check passed. Installed on both devices. S25 screenshot /tmp/clock-preview-visible.png confirms preview restored with existing non-default settings; /tmp/clock-reset-final.png confirms Reset styling. Did not activate Reset or overwrite calibration. Final Fold screenshot could not be decoded; final Fold visual verification remains unconfirmed.

Refresh inspection: peak_refresh_rate/min_refresh_rate keys are absent on both devices; no forced60Hz override found. S25 reports120Hz. Fold active inner display reports120Hz, inactive outer60Hz; user-setting display vote allows up to120Hz dynamically. No refresh settings were changed because both already support the requested adaptive120Hz configuration.

APK: /Users/sambeckman/Downloads/ExpressiveClock-preview-reset-20261120-130457/ExpressiveClock-debug.apk. All work remains local/unstaged on main.


## Minute increment and vertical travel follow-up

MechanicalTicker previously allowed only one-unit adjacent changes, so a +60-second change disabled the slide transition. It now permits positive59–60-second jumps (including a simultaneous countdown tick), while retaining snap behaviour for larger resets/format changes. Both expanded and compact readouts derive roll direction from the change: added time rolls upwards, countdown rolls downwards. Existing spring/detent/blur parameters are unchanged.

Vertical calibration now calculates its slider endpoints from actual display dimensions, top cutout/status insets and current capsule height. A shared pure geometry helper returns offsets that map exactly to top0 and bottom(displayHeight-capsuleHeight-8dp). The renderer clamps its base position before applying calibration so shallow cameras map consistently. Old out-of-range saved values are preserved, but the control displays/edits the effective reachable position; this removes dead slider travel. Slider values remain fractional and arrow buttons retain1dp adjustments.

Files changed for this follow-up: MechanicalTicker.kt, DynamicIslandOverlay.kt, IslandGeometry.kt, SettingsScreen.kt, MechanicalTickerTest.kt, IslandGeometryTest.kt, and this report. No files added/deleted. assembleDebug/testDebugUnitTest/lintDebug passed (92 tests,zero failures), plus12 Python checks and diff whitespace check. Installed final APK on S25 and Pixel Fold without uninstalling. Final minute-add animation and full device slider travel have not been visually reverified; regression tests verify minute/hour transitions and monotonic endpoint geometry. Existing calibration data retained. Export: /Users/sambeckman/Downloads/ExpressiveClock-ticker-slider-20261120-131247/ExpressiveClock-debug.apk. Local uncommitted work on main only.


## Centred vertical slider follow-up

Vertical calibration now maps Default(0dp) to slider centre, with separate linear scaling for the negative and positive halves of the asymmetric physical travel range. The displayed label is Default at zero. Fractional dp values and1dp arrow increments are retained; saved calibration is not remapped or overwritten. Where no upward travel exists, negative input maps to zero and the handle remains centred, rather than implying unavailable movement. All other calibration sliders retain their existing mapping.

Changes: SettingsScreen.kt, IslandGeometry.kt, IslandGeometryTest.kt and this report; no new/deleted files. Added round-trip, endpoint, fraction and zero-upward-range regression checks. assembleDebug/testDebugUnitTest/lintDebug and12 Python checks passed. Final visual/device interaction matrix not repeated for this mapping-only change. APK: /Users/sambeckman/Downloads/ExpressiveClock-centred-slider-20261120-131721/ExpressiveClock-debug.apk. Local/uncommitted on main.


## Selection motion and edge falloff

Expanded-position radio rows now crossfade their rounded primary-container highlight with a220ms eased transition, retaining immediate radio selection/persistence and no grey ripple. Calibration overview/editor scrolling reuses progressiveListEdgeMask:32dp top/bottom viewport bands combine real hardware blur with alpha falloff. Strength responds to remaining scroll travel, so endpoints remain readable; header, preview and predictive-back layers are outside the effect. No island animation changes.

Modified SettingsScreen.kt and this report; no added/deleted files. assembleDebug/lintDebug and diff whitespace check passed. Installed on S25 and Pixel Fold. Perceptual smoothness has not been physically assessed in this follow-up. APK: /Users/sambeckman/Downloads/ExpressiveClock-calibration-falloff-20261120-132255/ExpressiveClock-debug.apk. Local uncommitted work retained on main.


## Manual Save, unsaved prompt and rendering work

Calibration editor changes now affect a local draft/preview only. Equal-width rounded Reset/Save buttons match reference hierarchy: Reset uses primary-container tint/primary text; Save solid primary/on-primary. Reset resets draft only. Save persists the profile once and returns to overview. Toolbar/predictive back checks draft against its entry baseline and presents Save/Discard/Keep editing. Cancelled back gestures do not prompt. Existing profile enable switches on overview remain immediate. No delayed-save code remains.

Selection colour animation is read in drawBehind rather than page composition, with an isolated graphics layer. Added opt-in edgeBandsOnly to progressiveListEdgeMask and enabled only for calibration: blur records narrow top/bottom bands instead of a full-page blurred layer; other callers retain their rendering path. Header/preview remain outside edge effect.

Files for these follow-ups: SettingsScreen.kt, WorldClockPanel.kt and report; no added/deleted files. assembleDebug,testDebugUnitTest,lintDebug passed;12 Python checks and diff whitespace passed. Final APK installed on S25/Fold. S25 visual screenshots: /tmp/clock-save-layout.png, /tmp/clock-unsaved-prompt.png. Toolbar Back prompt, Keep editing, completed predictive-back prompt and Discard exercised. Test draft discarded. Save callback inspected; final Save persistence not physically exercised in this follow-up.

S25 gfxinfo repeated12 alternating tap checks: /tmp/clock-selection-before-stable.txt (p50=32ms,p90=133ms,p95=150ms), /tmp/clock-selection-edge-after.txt (p50=29ms,p90=150ms,p95=150ms), final /tmp/clock-selection-manual-save.txt (p50=26ms,p90=36ms,p95=40ms). Initial colour-only change did not improve deadlines. Manual Save reduced long stalls but jank remained high (final428/608 frames,70.39% at reported120Hz). Runs were serial and not controlled thermal/release benchmarks; do not claim full120fps smoothness. Fold final visual/performance checks not repeated.

APK: /Users/sambeckman/Downloads/ExpressiveClock-calibration-save-20261120-133612/ExpressiveClock-debug.apk. All work stays local/uncommitted on main.


## Inactive display-profile guidance

Read Expressive Island CalibrationProfileGuidance.kt and its MainActivity integration without modifying that project. Ported its themed guidance dialog/animated device illustration and reduced-motion handling into Clock. Requests for an inactive profile now show Back/Continue Anyway; matching physical display/profile transitions automatically open the requested editor. Covers folded portrait/landscape and unfolded portrait/landscape, with explicit fold/outer-display wording when returning from an inner display. Slabs expose only their two relevant profiles; existing portrait lock is preserved and Continue Anyway permits offline landscape edits. Manual Save/unsaved-change behaviour is retained.

Modified SettingsScreen.kt and this report; created ui/screens/CalibrationProfileGuidance.kt; no deletions. assembleDebug/testDebugUnitTest/lintDebug,12 Python regressions and diff whitespace check passed. Installed final APK on S25 and Pixel Fold. S25 inactive Landscape prompt and Continue Anyway opening Landscape editor physically verified (/tmp/clock-guidance-s25.png and /tmp/clock-guidance-continue.png); returned without editing/saving. Fold posture auto-open and the complete four-dialog hardware matrix remain unverified in this follow-up. No device settings changed. APK: /Users/sambeckman/Downloads/ExpressiveClock-profile-guidance-20261120-134913/ExpressiveClock-debug.apk. All work local/uncommitted on main.


## Rotation exception for calibration

Added a DisposableEffect scoped to the calibration dialog flow (overview and editor). It temporarily changes a portrait-locked host Activity to FULL_SENSOR; dismissal restores its previous requestedOrientation. Foldable/tablet hosts without the slab lock are untouched. No system auto-rotation settings changed. File modified: SettingsScreen.kt plus this report; no created/deleted files. assembleDebug/testDebugUnitTest/lintDebug and12 Python regressions passed; diff whitespace clean. Installed on S25. ADB confirmed FULL_SENSOR inside calibration and PORTRAIT after exiting (/tmp/clock-calibration-orientation.txt and /tmp/clock-after-calibration-orientation.txt). Fresh physical rotation not performed. Fold disconnected during install attempt; this latest APK is NOT installed on Fold. APK: /Users/sambeckman/Downloads/ExpressiveClock-calibration-rotation-20261120-135553/ExpressiveClock-debug.apk. Local/uncommitted on main.
