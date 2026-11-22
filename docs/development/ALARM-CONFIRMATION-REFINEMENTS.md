# Alarm confirmation wording and replacement motion

Reviewed main and preserved all earlier local Phase 3 changes. No version, signing, alarm scheduling, saved alarms or GitHub changes.

## Changes

AlarmConfirmationDuration.kt formats actual elapsed minutes into full-word day/hour/minute components, retaining minutes for durations over one day, applying singular/plural forms and joining the last two units with and. Zero units are omitted; sub-minute/past intervals return less than a minute. ClockApp uses this formatter only for its saved/enabled alarm confirmations. Existing compact humanUntil formatting is unchanged.

Material3 SnackbarHost temporarily overlaps outgoing and incoming items in its fade/scale container. Previously intrinsic item widths could change the parent measurement and left/start placement during that overlap. AlarmConfirmation now gives every item a full-width, bottom-centered frame with a font-scale-aware minimum height for wrapped text. The visible capsule remains intrinsic-width, themed and shadow-free. The common frame keeps its anchor stable across replacements; existing SnackbarHost motion, announcements and timeout remain intact.

AlarmConfirmationPresenter owns the current request job. A new message cancels the previous request, including one waiting on the host mutex, before showing the latest message. This changes only confirmation delivery, not the ViewModel scheduling/persistence jobs.

## Verification

assembleDebug, testDebugUnitTest and lintDebug passed: 107 JVM tests, no failures/errors; 12 Python regression checks and git diff --check passed. New formatter cases cover the requested 1 day, 13 hours and 33 minutes, singular/plural units, omitted zeros, sub-minute/past durations, DST elapsed time and unchanged compact formatting. Presenter regression sends 20 replacements and checks the latest message and dismissal.

Physical S25 SM-S931B / RFCY41EDGND: installed debug APK in place. Used a debug-only Activity with the production AlarmConfirmation/Presenter to alternate hours-only and day/hour/minute messages, then rapid alternation. Reviewed settled and overlapping frames: capsules stay centered; long text fully readable; latest requested message displayed. Recorded transitions. No real alarms were created, enabled, disabled or deleted in this visual check. Larger accessibility fonts and other physical devices not re-tested in this follow-up. Only Clock's Accessibility Service was rebound after installation, preserving the exact existing enabled-service list; no other settings changed.

## Files

Modified: ClockApp.kt, AlarmConfirmation.kt, debug AndroidManifest.xml. Created: AlarmConfirmationDuration.kt, AlarmConfirmationDurationTest.kt, AlarmConfirmationPresenterTest.kt, debug AlarmConfirmationProbeActivity.kt and this report. No deletions. All prior uncommitted work remains intact on main; nothing committed, pushed or published.

APK: /Users/sambeckman/Downloads/ExpressiveClock-alarm-confirmations-20261122-183949/ExpressiveClock-debug.apk

Evidence: /Users/sambeckman/Downloads/ExpressiveClock-alarm-confirmations-20261122-183949/evidence
