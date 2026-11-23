# Compose + Kotlin metadata are handled by the AGP defaults. Only app-specific keeps here.
# Keep boot-entry coroutine state machines separate from foreground-ringer state machines.
# R8 otherwise horizontally merges widget BOOT_COMPLETED handlers with AlarmService chimes,
# creating a shared dispatch method that Play's FGS boot analysis can over-approximate.
# Names may still be obfuscated; runtime scheduling and service behavior remain unchanged.
-keep,allowobfuscation class app.expressiveclock.alarm.BootReceiver$* { *; }
-keep,allowobfuscation class app.expressiveclock.widget.ClockWidgetProvider$* { *; }
-keep,allowobfuscation class app.expressiveclock.widget.DigitalClockWidgetProvider$* { *; }
