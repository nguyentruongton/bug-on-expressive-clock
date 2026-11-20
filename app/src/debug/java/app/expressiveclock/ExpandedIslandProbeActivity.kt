package app.expressiveclock

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.expressiveclock.core.*
import app.expressiveclock.data.*
import app.expressiveclock.overlay.*
import app.expressiveclock.ui.theme.ClockTheme
import java.time.Duration

/** Explicit debug-only host of the production composables. Never touches ClockStore or schedules alarms. */
class ExpandedIslandProbeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = if (intent.getBooleanExtra("landscape", false)) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val seconds = intent.getLongExtra("seconds", 287L)
        val kind = intent.getStringExtra("kind") ?: "timer"
        val scale = intent.getFloatExtra("font", 1f)
        val testWidth = intent.getFloatExtra("width", 0f)
        val paused = intent.getBooleanExtra("paused", false)
        setContent {
            ClockTheme(ThemeSettings(darkMode = if (intent.getBooleanExtra("dark", false)) DarkMode.DARK else DarkMode.LIGHT)) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                    var timer by remember { mutableStateOf(if (kind == "timer") ClockTimer(-1, "UI fixture", Duration.ofSeconds(seconds),
                        if (paused) TimerState.PAUSED else TimerState.RUNNING, SystemClock.elapsedRealtime() + seconds * 1000) else null) }
                    var sw by remember { mutableStateOf(if (kind == "stopwatch") Stopwatch(!paused, SystemClock.elapsedRealtime(), Duration.ofSeconds(seconds)) else null) }
                    var expanded by remember { mutableStateOf(true) }
                    var result by remember { mutableStateOf("No action") }
                    fun report(message: String) { result = message; android.util.Log.i("ClockCardProbe", message) }
                    val toggleTimer = {
                        val now = SystemClock.elapsedRealtime()
                        timer = timer?.let { if (it.state == TimerState.RUNNING) it.copy(state = TimerState.PAUSED, pausedRemaining = it.remaining(now))
                            else it.copy(state = TimerState.RUNNING, deadlineElapsedMillis = now + it.pausedRemaining.toMillis()) }
                        report("Timer toggle")
                    }
                    val add = { timer = timer?.let { it.copy(total = it.total.plusSeconds(60), deadlineElapsedMillis = it.deadlineElapsedMillis + 60000,
                        pausedRemaining = it.pausedRemaining.plusSeconds(60)) }; report("Added 60 seconds") }
                    val toggleSw = { val now = SystemClock.elapsedRealtime(); sw = sw?.let { it.copy(running = !it.running, startedAtElapsed = now, accumulated = it.elapsed(now)) }; report("Stopwatch toggle") }
                    val lap = { sw = sw?.let { it.copy(laps = it.laps + Lap(it.laps.size + 1, it.elapsed(SystemClock.elapsedRealtime()), it.elapsed(SystemClock.elapsedRealtime()))) }; report("Lap ${sw?.laps?.size}") }
                    val cancel = { timer = null; sw = null; report("Cancelled/reset") }
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        if (testWidth > 0f) {
                            Box(Modifier.padding(top = 80.dp).width(testWidth.dp).height((152 + (scale - 1).coerceAtLeast(0f) * 40).dp)
                                .background(androidx.compose.ui.graphics.Color.Black, androidx.compose.foundation.shape.RoundedCornerShape(42.dp))
                                .padding(horizontal = 20.dp, vertical = 16.dp)) {
                                ExpandedIslandCard(timer, sw, {}, {}, toggleTimer, add, cancel, toggleSw, lap)
                            }
                        } else DynamicIslandOverlayContent(timer, sw, 8.dp, isExpanded = expanded,
                            offsetYDp = intent.getFloatExtra("offsetY", 0f).dp,
                            onExpandChanged = { expanded = it }, onPauseResumeTimer = toggleTimer, onAddTenTimer = add,
                            onCancelTimer = cancel, onToggleStopwatch = toggleSw, onLapStopwatch = lap)
                        Column(Modifier.align(androidx.compose.ui.Alignment.BottomCenter).navigationBarsPadding().padding(24.dp).padding(bottom = 96.dp)) {
                            Text("Clock UI test host: $kind / font $scale\n$result\nNo persisted app state modified")
                            Button(onClick = { expanded = !expanded }) { Text("Expand / collapse") }
                        }
                    }
                }
            }
        }
    }
}
