# Swipe-triggered dock icon animations

ClockDock.kt now triggers its existing icon animation when a destination becomes selected, including during horizontal pager drags. The previously selected state is remembered so initial composition does not animate. A tap on a different destination uses the selection trigger; tapping the already selected icon explicitly replays its animation. This prevents double triggering and keeps the existing animation timing, icon rendering, reduced-motion behaviour and direct pager indicator progress. The same component serves folded/compact and unfolded/two-pane layouts. No page state, gesture handling, signing, version or island code changed in this follow-up.

The earlier local unfolded swipe changes in ClockApp.kt and WideClockPages.kt, and their report, remain intact. This follow-up modifies ClockDock.kt and creates this report; no files deleted.

Verification: assembleDebug, testDebugUnitTest (96 tests), lintDebug and the existing Python checks (12 tests) passed. git diff --check passed. New debug APK installed in place on Pixel 11 Pro Fold 66231FDDJ003QS. Unfolded portrait touch testing covered Alarms -> World Clock -> Timer and back, plus repeated World Clock tapping. Captured Timer icon partway through its half-turn and upright after settling, confirming swipe selection triggers its existing animation. Initial composition does not request an animation by construction. No isolated unit test added for this visual effect; frame captures exercise the physical UI directly.

Physical folded check passed after the user closed/unlocked the Fold: Android reported its outer display at 1080x2342, 390 dpi (443x961dp). Alarms selection survived the physical fold. Swiped forward through all four pages and backward to Alarms. Additional frames show the Timer hourglass rotated and the Stopwatch hand partway through its existing animation after swipe selection. Repeated Alarms tapping was exercised and the device returned to Alarms. Samsung is connected but not used in this follow-up. No device settings changed. Landscape, rapid interruption stress tests and reduced-motion settings were not re-tested.

Evidence and build logs: /Users/sambeckman/Downloads/ExpressiveClock-swipe-icons-20261120-195805

Debug APK: /Users/sambeckman/Downloads/ExpressiveClock-swipe-icons-20261120-195805/ExpressiveClock-debug.apk

All changes remain local, uncommitted on main. No push, release or GitHub writes.
