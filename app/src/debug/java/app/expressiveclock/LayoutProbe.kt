package app.expressiveclock

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.os.SystemClock
import app.expressiveclock.alarm.Notifications
import app.expressiveclock.alarm.TimerScheduler
import app.expressiveclock.core.*
import app.expressiveclock.data.ClockStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration

/** Explicit debug-only ADB fixture. Snapshot/restore Timer and Stopwatch, including laps. */
class LayoutProbe : Instrumentation() {
    private var args = Bundle()
    override fun onCreate(arguments: Bundle?) { args = arguments ?: Bundle(); super.onCreate(arguments); start() }
    override fun onStart() {
        val result = Bundle()
        try {
            runBlocking {
                val ctx = targetContext
                val store = ClockStore(ctx)
                val backup = java.io.File(ctx.filesDir, "layout-probe-snapshot.json")
                fun timerJson(t: ClockTimer) = JSONObject().put("id", t.id).put("label", t.label).put("total", t.total.toMillis())
                    .put("state", t.state.name).put("deadline", t.deadlineElapsedMillis).put("remaining", t.pausedRemaining.toMillis())
                val command = args.getString("command") ?: "status"
                if (command in listOf("timer", "stopwatch")) {
                    if (!backup.exists()) {
                        val sw = store.stopwatch.first()
                        val snapshot = JSONObject().put("timer", store.timerNow()?.let(::timerJson) ?: JSONObject.NULL)
                            .put("running", sw.running).put("started", sw.startedAtElapsed).put("accumulated", sw.accumulated.toMillis())
                            .put("laps", JSONArray().apply { sw.laps.forEach { put(JSONObject().put("index", it.index).put("split", it.split.toMillis()).put("total", it.total.toMillis())) } })
                        backup.writeText(snapshot.toString())
                    }
                    val seconds = (args.getString("seconds") ?: "3600").toLong().coerceIn(60, 3_600_000)
                    val duration = Duration.ofSeconds(seconds)
                    val timer = if (command == "timer") ClockTimer(-17017, "ADB layout probe", duration, TimerState.RUNNING,
                        SystemClock.elapsedRealtime() + duration.toMillis()) else null
                    store.putTimer(timer); TimerScheduler.sync(ctx, timer)
                    val sw = if (command == "stopwatch") Stopwatch(true, SystemClock.elapsedRealtime(), duration) else Stopwatch()
                    store.putStopwatch(sw)
                    if (sw.running) Notifications.showStopwatch(ctx, sw) else Notifications.hideStopwatch(ctx)
                } else if (command == "restore" && backup.exists()) {
                    val j = JSONObject(backup.readText())
                    val t = j.optJSONObject("timer")?.let { ClockTimer(it.getLong("id"), it.getString("label"), Duration.ofMillis(it.getLong("total")),
                        TimerState.valueOf(it.getString("state")), it.getLong("deadline"), Duration.ofMillis(it.getLong("remaining"))) }
                    val laps = j.getJSONArray("laps")
                    val sw = Stopwatch(j.getBoolean("running"), j.getLong("started"), Duration.ofMillis(j.getLong("accumulated")),
                        (0 until laps.length()).map { laps.getJSONObject(it).let { Lap(it.getInt("index"), Duration.ofMillis(it.getLong("split")), Duration.ofMillis(it.getLong("total"))) } })
                    store.putTimer(t); TimerScheduler.sync(ctx, t); store.putStopwatch(sw)
                    if (sw.running || !sw.accumulated.isZero) Notifications.showStopwatch(ctx, sw) else Notifications.hideStopwatch(ctx)
                    check(backup.delete())
                }
                result.putString("stream", "timer=${store.timerNow()}\nstopwatch=${store.stopwatch.first()}\n")
            }
            finish(Activity.RESULT_OK, result)
        } catch (error: Throwable) { result.putString("stream", error.stackTraceToString()); finish(Activity.RESULT_CANCELED, result) }
    }
}
