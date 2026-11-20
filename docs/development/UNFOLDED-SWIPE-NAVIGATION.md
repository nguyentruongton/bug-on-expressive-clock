# Unfolded swipe navigation

## Cause and fix

The wide layout bypassed HorizontalPager and used tap-driven AnimatedContent. Its pager navigation effects and dock progress were explicitly disabled. ClockApp now uses the same retained four-page pager at every responsive width. Wide Alarm and World Clock destinations retain their existing two-pane compositions, with their destination-specific clock and functional pane. Pager settling, bottom-bar taps, live indicator progress, selection/modal gesture gates and spring snapping share the compact implementation. Pages remain composed across tab navigation, and the same pager survives responsive layout changes.

Physical testing also found that WideClockPages' inactive pane consumed pointer events at the initial pass, blocking swipes over the clock half of the display. Only the destination's active functional pane is now composed, removing the hidden hit target and redundant hidden lists. No alarm schedules, settings, island code, versions, signing or dependencies changed.

## Verification

Pixel 11 Pro Fold, serial 66231FDDJ003QS, Android 17, physically unfolded portrait, display 2076x2152 at 390 dpi (852x883dp reported window). Debug app updated in place, preserving data. Samsung SM-S931B RFCY41EDGND also connected; no Samsung commands or installation in this pass.

- Swiped Alarms -> World Clock -> Timer -> Stopwatch and back, screenshots reviewed at every step.
- Swipes starting in the alarm-card and city-card halves as well as the clock halves worked after removing the hidden blocker.
- Short slow drag returned to Alarms; vertical drag did not change destination.
- Bottom-bar taps switched Alarms/Stopwatch and back; live indicator movement captured while a swipe was held in progress.
- Timer draft 5 seconds survived navigation to Stopwatch and back, then was restored to the original zero draft without starting a timer.
- Selected San Francisco in World Clock, navigated to Timer and back, verified selection retained, then restored Sydney.
- Existing alarm cards and two-pane World Clock grid remained visible. No alarm toggles, saved settings, system settings or refresh-rate changes.

assembleDebug, testDebugUnitTest and lintDebug passed. 96 JVM tests with zero failures/errors; 12 existing Python tests passed via python3 -m unittest discover -s tests. git diff --check passed. A final incremental assembleDebug passed after documentation/comment cleanup. No new unit test was added for this gesture-only change; physical touch checks cover the failure directly.

Evidence: /Users/sambeckman/Downloads/ExpressiveClock-unfolded-swipe-20261120-194654/evidence

Debug APK: /Users/sambeckman/Downloads/ExpressiveClock-debug.apk

## Limits and repository state

Folded mode, unfolded landscape, physical fold transitions, running Timer/Stopwatch state, long presses and modal/slider gesture conflicts were not re-tested in this focused pass. Existing modal/selection gates and child gesture handling are unchanged. The shared ViewModel continues to own Timer/Stopwatch state. No broad alarm/ringing or widget device checks were performed.

Modified ClockApp.kt and WideClockPages.kt; created this report; deleted no files. Main branch, local uncommitted changes only. Nothing committed, pushed or published.
