package app.expressiveclock.alarm

import app.expressiveclock.applyClockOrientationPolicy
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.map
import app.expressiveclock.data.ClockSettings
import app.expressiveclock.data.ClockStore
import app.expressiveclock.ui.rememberWallTicker
import app.expressiveclock.ui.screens.WidePill
import app.expressiveclock.ui.theme.ClockFace
import app.expressiveclock.ui.theme.ClockTheme
import app.expressiveclock.ui.theme.Numerals
import java.time.LocalTime

/**
 * The screen a ringing alarm puts in front of you.
 *
 * ## Getting onto a locked phone
 *
 * `setShowWhenLocked` and `setTurnScreenOn` are the modern replacements for the deprecated window
 * flags, and they are the reason this appears over the keyguard with the display off. They are set
 * before `setContent` because the window attributes have to be right before the first frame.
 *
 * ## No back button
 *
 * Backing out of a ringing alarm would leave it ringing with no way to reach the controls except
 * the shade. The only exits are Snooze and Dismiss, which is the same contract every alarm clock
 * has had since they had a bell on top.
 */
class AlarmRingActivity : ComponentActivity() {

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        applyClockOrientationPolicy()
    }

    override fun onResume() {
        super.onResume()
        applyClockOrientationPolicy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyClockOrientationPolicy()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        val id = intent.getLongExtra(AlarmReceiver.EXTRA_ID, -1L)
        val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
        val store = ClockStore(applicationContext)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        setContent {
            val settingsFlow = androidx.compose.runtime.remember(store) { store.settings.map { it as ClockSettings? } }
            val loadedSettings by settingsFlow.collectAsStateWithLifecycle(initialValue = null)
            val settings = loadedSettings ?: return@setContent
            ClockTheme(settings.theme) {
                Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
                    Ringing(
                        label = label,
                        snoozeMinutes = settings.alarms.snoozeMinutes,
                        onSnooze = {
                            sendBroadcast(action(ClockActionReceiver.ACTION_SNOOZE, id))
                            finish()
                        },
                        onDismiss = {
                            sendBroadcast(action(ClockActionReceiver.ACTION_DISMISS, id))
                            finish()
                        },
                    )
                }
            }
        }
    }

    private fun action(name: String, id: Long) =
        Intent(this, ClockActionReceiver::class.java)
            .setPackage(packageName)
            .setAction(name)
            .putExtra(AlarmReceiver.EXTRA_ID, id)

    companion object {
        const val EXTRA_LABEL = "label"

        fun intent(context: Context, id: Long, label: String): PendingIntent =
            PendingIntent.getActivity(
                context,
                id.toInt(),
                Intent(context, AlarmRingActivity::class.java)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra(AlarmReceiver.EXTRA_ID, id)
                    .putExtra(EXTRA_LABEL, label),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}

@Composable
private fun Ringing(
    label: String,
    snoozeMinutes: Int,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
) {
    val now by rememberWallTicker()
    val time = LocalTime.now()
    val context = LocalContext.current
    val is24Hour = app.expressiveclock.ui.rememberSystem24HourFormat()

    val formattedTime = if (is24Hour) {
        "%02d:%02d".format(time.hour, time.minute)
    } else {
        "%02d:%02d".format((time.hour % 12).takeIf { it != 0 } ?: 12, time.minute)
    }

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
    val compact = maxHeight < 600.dp
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = if (compact) 12.dp else 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
        Text(
            label.ifBlank { "Alarm" },
            style = MaterialTheme.typography.headlineMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(if (compact) 8.dp else 28.dp))
        // The same ultra-condensed face the grid uses, at the size a room-across glance needs.
        Numerals(
            text = formattedTime,
            capHeight = if (compact) 64.dp else 132.dp,
            color = MaterialTheme.colorScheme.primary,
            width = ClockFace.CONDENSED,
            weight = ClockFace.WEIGHT_ON,
            tracking = ClockFace.CONDENSED_TRACKING,
        )
        }
        Spacer(Modifier.height(if (compact) 12.dp else 56.dp))
        WidePill(
            text = "Dismiss",
            onClick = onDismiss,
            container = MaterialTheme.colorScheme.tertiaryContainer,
            content = MaterialTheme.colorScheme.onTertiaryContainer,
            height = if (compact) 56.dp else 96.dp,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Dismiss" },
        )
        Spacer(Modifier.height(12.dp))
        WidePill(
            text = "Snooze $snoozeMinutes min",
            onClick = onSnooze,
            outlined = true,
            content = MaterialTheme.colorScheme.onSurface,
            height = if (compact) 56.dp else 72.dp,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Snooze $snoozeMinutes min" },
        )
    }
}
}
