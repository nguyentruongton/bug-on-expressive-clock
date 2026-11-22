package app.expressiveclock

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import app.expressiveclock.data.*
import app.expressiveclock.widget.WidgetColours
import kotlinx.coroutines.runBlocking

/** Scoped persistence/colour tests; no existing widget or alarm is modified. */
class Phase3Probe : Instrumentation() {
    private var command = "test"
    override fun onCreate(arguments: Bundle?) { command = arguments?.getString("command") ?: "test"; super.onCreate(arguments); start() }
    override fun onStart() {
        val result = Bundle(); var code = Activity.RESULT_OK
        try { runBlocking {
            val store = ClockStore(targetContext)
            val marker = java.io.File(targetContext.filesDir, "phase3-island-original.txt")
            if (command == "prepare") {
                if (!marker.exists()) marker.writeText(store.settingsNow().theme.dynamicIsland.toString())
                store.update { it.copy(theme = it.theme.copy(dynamicIsland = true)) }
            } else if (command == "restore") {
                if (marker.exists()) { val original = marker.readText().toBoolean(); store.update { it.copy(theme = it.theme.copy(dynamicIsland = original)) }; marker.delete() }
            } else if (command == "timer") {
                check(store.timerNow() == null) { "Existing timer preserved; run this check without a timer" }
                try {
                    val timer = app.expressiveclock.core.ClockTimer(-330004, "Completion probe", java.time.Duration.ofSeconds(3),
                        app.expressiveclock.core.TimerState.RUNNING, android.os.SystemClock.elapsedRealtime() + 3000)
                    store.putTimer(timer)
                    app.expressiveclock.alarm.TimerScheduler.sync(targetContext, timer)
                    repeat(80) {
                        if (store.timerNow()?.state != app.expressiveclock.core.TimerState.FINISHED) kotlinx.coroutines.delay(100)
                    }
                    check(store.timerNow()?.state == app.expressiveclock.core.TimerState.FINISHED)
                    kotlinx.coroutines.delay(400)
                    val notifications = targetContext.getSystemService(android.app.NotificationManager::class.java).activeNotifications
                    val ringing = notifications.first { it.notification.actions?.any { action -> action.title.toString() == "Dismiss" } == true }
                    ringing.notification.actions.first { it.title.toString() == "Dismiss" }.actionIntent.send()
                    kotlinx.coroutines.delay(500)
                    check(store.timerNow() == null) { "Dismiss did not clear finished Timer" }
                    @Suppress("DEPRECATION")
                    check(targetContext.getSystemService(android.app.ActivityManager::class.java).getRunningServices(100)
                        .none { it.service.className == app.expressiveclock.alarm.AlarmService::class.java.name })
                } finally {
                    app.expressiveclock.alarm.AlarmService.stop(targetContext)
                    if (store.timerNow()?.id == -330004L) store.putTimer(null)
                    app.expressiveclock.alarm.TimerScheduler.sync(targetContext, store.timerNow())
                }
            } else {
                val launcher = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER).setPackage(targetContext.packageName)
                check(targetContext.packageManager.resolveActivity(launcher, 0)?.loadLabel(targetContext.packageManager).toString() == "Clock")
                val id = -330003
                check(store.widgetConfigNow(id) == null)
                val settings = store.settingsNow()
                try {
                    val original = WidgetConfig(analogPrimaryColor = 0xFF009944, analogFaceColor = 0xFFCC2233)
                    store.putWidgetConfig(id, original)
                    val loaded = ClockStore(targetContext).widgetConfigNow(id)!!
                    check(loaded == original)
                    val colours = WidgetColours.resolve(targetContext, loaded, settings)
                    check(colours.face == 0xFFCC2233.toInt() && colours.hourHand == 0xFF009944.toInt())
                    val changed = WidgetColours.resolve(targetContext, loaded.copy(analogFaceColor = 0xFF2233CC), settings)
                    check(changed.face == 0xFF2233CC.toInt() && changed.hourHand == colours.hourHand)
                    val darkFace = WidgetColours.resolve(targetContext, loaded.copy(analogFaceColor = 0xFF101010), settings)
                    check(darkFace.onFace == android.graphics.Color.WHITE && darkFace.hourHand == colours.hourHand)
                    for (style in WorldClockStyle.entries) {
                        store.update { it.copy(world = it.world.copy(defaultStyle = style)) }
                        check(ClockStore(targetContext).settingsNow().world.defaultStyle == style)
                    }
                } finally {
                    store.deleteWidgetConfigs(intArrayOf(id))
                    store.update { it.copy(world = it.world.copy(defaultStyle = settings.world.defaultStyle)) }
                }
            }
            result.putString("stream", "PASS $command: ${store.settingsNow().theme.dynamicIsland}\n")
        } } catch (t: Throwable) { code = Activity.RESULT_CANCELED; result.putString("stream", t.stackTraceToString()) }
        finish(code, result)
    }
}
