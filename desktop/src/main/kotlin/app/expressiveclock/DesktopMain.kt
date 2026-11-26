package app.expressiveclock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.DesktopConfiguration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import app.expressiveclock.alarm.DesktopAlarmCoordinator
import app.expressiveclock.alarm.DesktopAudioPlayer
import app.expressiveclock.alarm.SystemdAlarmScheduler
import app.expressiveclock.data.ClockStore
import app.expressiveclock.ui.ClockApp
import app.expressiveclock.ui.ClockViewModel
import app.expressiveclock.ui.DesktopDisplayScale
import app.expressiveclock.ui.DesktopUiScaleState
import app.expressiveclock.ui.LocalDesktopUiScale
import app.expressiveclock.ui.UiScaleShortcutAction
import app.expressiveclock.ui.theme.ClockTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Image
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.SwingUtilities
import kotlin.concurrent.thread
import kotlin.reflect.KClass

internal fun defaultRuntimeDir(): Path {
    val runtimeDir = System.getenv("XDG_RUNTIME_DIR")?.takeIf { it.isNotBlank() } ?: "/tmp"
    return Path.of(runtimeDir)
}

internal fun mainSocketPath(runtimeDir: Path = defaultRuntimeDir()): Path =
    runtimeDir.resolve("expressive-clock.sock")

internal fun shouldStopRingSessionAfterCliAction(
    env: Map<String, String> = System.getenv(),
): Boolean = env["EXPRESSIVE_CLOCK_IN_RING_SESSION"] != "1"

internal fun sendToUnixSocket(path: Path, command: String): Boolean {
    if (!Files.exists(path)) return false
    return runCatching {
        SocketChannel.open(StandardProtocolFamily.UNIX).use { channel ->
            channel.connect(UnixDomainSocketAddress.of(path))
            channel.write(ByteBuffer.wrap((command + "\n").toByteArray(StandardCharsets.UTF_8)))
            true
        }
    }.getOrDefault(false)
}

internal fun startUnixSocketServer(
    path: Path,
    dispatch: (() -> Unit) -> Unit = { block -> SwingUtilities.invokeLater(block) },
    onMessage: (String) -> Unit,
): ServerSocketChannel? {
    return runCatching {
        Files.deleteIfExists(path)
        Files.createDirectories(path.parent)
        val server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
        server.bind(UnixDomainSocketAddress.of(path))
        thread(isDaemon = true, name = "expressive-clock-ipc-${path.fileName}") {
            val buf = ByteBuffer.allocate(512)
            while (server.isOpen) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                runCatching {
                    client.use { ch ->
                        buf.clear()
                        val read = ch.read(buf)
                        if (read > 0) {
                            val msg = String(buf.array(), 0, read, StandardCharsets.UTF_8).trim()
                            if (msg.isNotEmpty()) {
                                dispatch { onMessage(msg) }
                            }
                        }
                    }
                }
            }
        }
        server
    }.getOrNull()
}

private fun handleCliAction(action: String, alarmId: Long) {
    // 1. If main app is running, forward via Unix socket so there is zero split-brain state
    if (sendToUnixSocket(mainSocketPath(), "ACTION $action $alarmId")) {
        return
    }
    // 2. Headless native-notification action when main window is closed
    DesktopAudioPlayer.stop()
    val store = ClockStore()
    runBlocking {
        when (action.lowercase()) {
            "snooze" -> DesktopAlarmCoordinator.snooze(store, alarmId)
            "dismiss" -> DesktopAlarmCoordinator.dismiss(store, alarmId)
        }
    }
    if (shouldStopRingSessionAfterCliAction()) {
        SystemdAlarmScheduler.stopRingSession(alarmId)
    }
}

fun main(args: Array<String>) {
    // CLI headless action: expressive-clock --action <snooze|dismiss> <alarm_id>
    val actionIdx = args.indexOf("--action")
    if (actionIdx >= 0 && actionIdx + 2 < args.size) {
        val action = args[actionIdx + 1]
        val alarmId = args[actionIdx + 2].toLongOrNull() ?: return
        handleCliAction(action, alarmId)
        return
    }

    // Legacy CLI flag: forward to open main window if running (background alarms use native notifications)
    val ringIdx = args.indexOf("--ring")
    if (ringIdx >= 0 && ringIdx + 1 < args.size) {
        val alarmId = args[ringIdx + 1].toLongOrNull() ?: return
        sendToUnixSocket(mainSocketPath(), "RING $alarmId")
        return
    }

    // Standard app launch: if another instance is already open, signal focus and exit
    if (sendToUnixSocket(mainSocketPath(), "FOCUS")) {
        return
    }

    launchMainWindow()
}

private class DesktopViewModelStoreOwner(
    private val clockVm: ClockViewModel,
) : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
    override val viewModelStore: ViewModelStore = ViewModelStore()

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T {
            if (modelClass == ClockViewModel::class) return clockVm as T
            throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
        }
    }
}

private fun loadWindowIcon(): Painter? = runCatching {
    val bytes = Thread.currentThread().contextClassLoader
        ?.getResourceAsStream("icons/expressive-clock-256.png")
        ?.use { it.readBytes() }
        ?: return null
    BitmapPainter(Image.makeFromEncoded(bytes).toComposeImageBitmap())
}.getOrNull()

private fun launchMainWindow() = application {
    val vm = remember { ClockViewModel() }
    val vmOwner = remember(vm) { DesktopViewModelStoreOwner(vm) }
    val windowIcon = remember { loadWindowIcon() }
    val initComp = remember {
        val awt = DesktopDisplayScale.detectInitialAwtScale()
        val base = DesktopDisplayScale.resolveBaseScale(awt)
        DesktopDisplayScale.compensationFactor(awt, base)
    }
    val windowState = rememberWindowState(
        size = DpSize((960f * initComp).dp, (720f * initComp).dp),
        position = WindowPosition.Aligned(Alignment.Center),
    )

    val server = remember {
        startUnixSocketServer(mainSocketPath()) { message ->
            val parts = message.split(" ")
            when (parts.firstOrNull()) {
                "RING" -> parts.getOrNull(1)?.toLongOrNull()?.let { id ->
                    windowState.isMinimized = false
                    vm.triggerRingInApp(id)
                }
                "ACTION" -> {
                    val act = parts.getOrNull(1)
                    val id = parts.getOrNull(2)?.toLongOrNull()
                    if (id != null) {
                        when (act?.lowercase()) {
                            "snooze" -> vm.snoozeRingingAlarm(id)
                            "dismiss" -> vm.dismissRingingAlarm(id)
                        }
                    }
                }
                "FOCUS" -> {
                    windowState.isMinimized = false
                }
            }
        }
    }

    Window(
        onCloseRequest = {
            DesktopAudioPlayer.stop()
            runCatching { server?.close() }
            runCatching { Files.deleteIfExists(mainSocketPath()) }
            exitApplication()
        },
        state = windowState,
        title = "Expressive Clock",
        icon = windowIcon,
        undecorated = false,
        transparent = false,
        onPreviewKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown) {
                when (val action = DesktopDisplayScale.shortcutActionForKey(event.isCtrlPressed || event.isMetaPressed, event.key)) {
                    is UiScaleShortcutAction.Step -> {
                        vm.stepUiScale(action.delta)
                        true
                    }
                    UiScaleShortcutAction.Reset -> {
                        vm.setUiScale(1.0f)
                        true
                    }
                    null -> false
                }
            } else {
                false
            }
        },
    ) {
        val ringingId by vm.ringingAlarmId.collectAsState()
        val alarms by vm.alarms.collectAsState()
        val settings by vm.settings.collectAsState()
        val uiScale by vm.uiScale.collectAsState()
        val windowInfo = LocalWindowInfo.current
        val rawDensity = LocalDensity.current
        val baseScale = remember(rawDensity.density) {
            DesktopDisplayScale.resolveBaseScale(rawDensity.density)
        }
        val effectiveScale = DesktopDisplayScale.effectiveDensity(rawDensity.density, baseScale, uiScale)
        val scaledDensity = remember(effectiveScale) { Density(density = effectiveScale, fontScale = 1f) }
        val desktopConfig = remember(windowInfo.containerSize, scaledDensity) {
            with(scaledDensity) {
                DesktopConfiguration(
                    screenWidthDp = windowInfo.containerSize.width.toDp().value.toInt().coerceAtLeast(360),
                    screenHeightDp = windowInfo.containerSize.height.toDp().value.toInt().coerceAtLeast(480),
                )
            }
        }
        val uiScaleState = remember(uiScale, baseScale, effectiveScale) {
            DesktopUiScaleState(
                uiScale = uiScale,
                baseScale = baseScale,
                effectiveScale = effectiveScale,
                onUiScaleChange = vm::setUiScale,
            )
        }

        CompositionLocalProvider(
            LocalDensity provides scaledDensity,
            LocalDesktopUiScale provides uiScaleState,
            LocalViewModelStoreOwner provides vmOwner,
            LocalConfiguration provides desktopConfig,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                ClockApp(vm = vm)

                ringingId?.let { id ->
                    val alarm = alarms.firstOrNull { it.id == id }
                    val snoozeMinutes = settings.alarms.snoozeMinutes.coerceAtLeast(1)
                    val silenceMinutes = settings.alarms.silenceAfterMinutes.coerceAtLeast(1)
                    LaunchedEffect(id, silenceMinutes) {
                        delay(silenceMinutes * 60_000L)
                        vm.silenceRingingAlarm(id)
                    }
                    ClockTheme(settings.theme) {
                        AlertDialog(
                            onDismissRequest = {},
                            title = { Text(alarm?.label?.ifBlank { "Alarm" } ?: "Alarm") },
                            text = {
                                Text(
                                    alarm?.time?.toString() ?: "Alarm is ringing",
                                    style = MaterialTheme.typography.headlineMedium,
                                )
                            },
                            confirmButton = {
                                Button(onClick = { vm.dismissRingingAlarm(id) }) {
                                    Text("Dismiss")
                                }
                            },
                            dismissButton = {
                                OutlinedButton(onClick = { vm.snoozeRingingAlarm(id) }) {
                                    Text("Snooze $snoozeMinutes min")
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
