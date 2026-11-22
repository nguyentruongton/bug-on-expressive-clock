package app.expressiveclock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.expressiveclock.core.alarmConfirmationUntil
import app.expressiveclock.ui.AlarmConfirmation
import app.expressiveclock.ui.AlarmConfirmationPresenter
import app.expressiveclock.ui.theme.ClockTheme
import java.time.ZonedDateTime

/** Exercises production confirmation layout and replacement without changing any alarms. */
class AlarmConfirmationProbeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val scope = rememberCoroutineScope()
            val presenter = remember(scope) { AlarmConfirmationPresenter(scope) }
            ClockTheme {
                Scaffold(snackbarHost = {
                    SnackbarHost(presenter.hostState, Modifier.padding(bottom = 100.dp)) { AlarmConfirmation(it) }
                }) { padding ->
                    Column(Modifier.padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = {
                            val now = ZonedDateTime.now()
                            presenter.show("Alarm in ${alarmConfirmationUntil(now, now.plusHours(2).plusMinutes(7))}")
                        }) { Text("Hours and minutes") }
                        Button(onClick = {
                            val now = ZonedDateTime.now()
                            presenter.show("Alarm in ${alarmConfirmationUntil(now, now.plusDays(1).plusHours(13).plusMinutes(33))}")
                        }) { Text("Days, hours and minutes") }
                    }
                }
            }
        }
    }
}
