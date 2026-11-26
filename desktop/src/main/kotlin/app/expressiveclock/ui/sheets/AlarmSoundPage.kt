package app.expressiveclock.ui.sheets

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import app.expressiveclock.alarm.DesktopAudioPlayer
import app.expressiveclock.data.SILENT
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

internal fun alarmSoundTitle(
    @Suppress("UNUSED_PARAMETER") context: Context,
    source: String?,
): String = when {
    source == null -> "Default"
    source == SILENT -> "Silent"
    else -> File(source.removePrefix("file://")).nameWithoutExtension.ifBlank { source }
}

@Composable
internal fun rememberAlarmSoundSelector(
    sound: String?,
    onSelected: (String?) -> Unit,
): () -> Unit {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var visible by rememberSaveable { mutableStateOf(false) }

    if (visible) {
        DesktopAlarmSoundDialog(
            initial = sound,
            onDismiss = {
                DesktopAudioPlayer.stop()
                visible = false
            },
            onSelected = { chosen ->
                DesktopAudioPlayer.stop()
                onSelected(chosen)
                visible = false
            },
        )
    }

    return {
        focus.clearFocus()
        keyboard?.hide()
        visible = true
    }
}

private fun pickAudioFileNative(): String? {
    // Prefer zenity on Wayland/Hyprland, fallback to AWT FileDialog
    val zenityResult = runCatching {
        val proc = ProcessBuilder(
            "zenity",
            "--file-selection",
            "--title=Select Alarm Sound",
            "--file-filter=Audio files | *.wav *.ogg *.mp3 *.flac *.opus",
        ).start()
        val output = proc.inputStream.bufferedReader().readText().trim()
        if (proc.waitFor() == 0 && output.isNotBlank()) output else null
    }.getOrNull()
    if (zenityResult != null) return zenityResult

    return runCatching {
        val dialog = FileDialog(null as Frame?, "Select Alarm Sound", FileDialog.LOAD)
        dialog.isVisible = true
        val dir = dialog.directory
        val file = dialog.file
        if (dir != null && file != null) File(dir, file).absolutePath else null
    }.getOrNull()
}

@Composable
private fun DesktopAlarmSoundDialog(
    initial: String?,
    onDismiss: () -> Unit,
    onSelected: (String?) -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf(initial) }
    var playing by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { DesktopAudioPlayer.stop() }
    }

    fun togglePreview(target: String?) {
        if (playing) {
            DesktopAudioPlayer.stop()
            playing = false
        } else {
            playing = true
            DesktopAudioPlayer.play(target, looping = false) {
                playing = false
            }
        }
    }

    val customLabel = draft
        ?.takeIf { it != SILENT }
        ?.let { File(it.removePrefix("file://")).name }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Alarm Sound") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { draft = null }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = draft == null, onClick = { draft = null })
                    Spacer(Modifier.width(8.dp))
                    Text("Default (Bundled Chime)", modifier = Modifier.weight(1f))
                    IconButton(onClick = { togglePreview(null) }) {
                        Icon(
                            if (playing && draft == null) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                            contentDescription = "Preview",
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { draft = SILENT }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = draft == SILENT, onClick = { draft = SILENT })
                    Spacer(Modifier.width(8.dp))
                    Text("Silent", modifier = Modifier.weight(1f))
                }

                if (customLabel != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = true, onClick = {})
                        Spacer(Modifier.width(8.dp))
                        Text(
                            customLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { togglePreview(draft) }) {
                            Icon(
                                if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                contentDescription = "Preview custom sound",
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = {
                        pickAudioFileNative()?.let { path ->
                            draft = path
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Choose Audio File…")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelected(draft) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
