package app.expressiveclock.alarm

import app.expressiveclock.DesktopResources
import app.expressiveclock.R
import app.expressiveclock.data.SILENT
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Logger
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import kotlin.concurrent.thread

/**
 * Desktop audio playback subsystem using the environment-verified fallback chain:
 * `pw-play` (primary) -> `mpv` (optional) -> `aplay` -> Java `Clip`.
 *
 * Tracks active PID in `${XDG_RUNTIME_DIR:-/tmp}/expressive-clock-audio.pid` for instant stop on dismiss.
 */
object DesktopAudioPlayer {
    private val log = Logger.getLogger("DesktopAudioPlayer")
    private val playing = AtomicBoolean(false)
    @Volatile private var activeProcess: Process? = null
    @Volatile private var activeClip: Clip? = null

    private val pidFile: Path
        get() {
            val runtimeDir = System.getenv("XDG_RUNTIME_DIR")?.takeIf { it.isNotBlank() } ?: "/tmp"
            return Path.of(runtimeDir, "expressive-clock-audio.pid")
        }

    fun bundledFallbackFile(): File {
        val cacheDir = Path.of(
            System.getenv("XDG_CACHE_HOME")?.takeIf { it.isNotBlank() }
                ?: "${System.getProperty("user.home")}/.cache",
            "expressive-clock",
        )
        Files.createDirectories(cacheDir)
        val wavFile = cacheDir.resolve("alarm_fallback.wav").toFile()
        if (!wavFile.exists() || wavFile.length() == 0L) {
            val resPath = DesktopResources.raws[R.raw.alarm_fallback] ?: "raw/alarm_fallback.wav"
            DesktopAudioPlayer::class.java.classLoader.getResourceAsStream(resPath)?.use { input ->
                wavFile.outputStream().use { out -> input.copyTo(out) }
            }
        }
        return wavFile
    }

    fun resolveSoundFile(soundUri: String?): File? {
        if (soundUri == SILENT) return null
        if (!soundUri.isNullOrBlank()) {
            val cleaned = soundUri.removePrefix("file://")
            val f = File(cleaned)
            if (f.exists() && f.isFile) return f
        }
        return bundledFallbackFile().takeIf { it.exists() }
    }

    fun play(soundUri: String?, looping: Boolean = true, onFinished: (() -> Unit)? = null) {
        stop()
        val file = resolveSoundFile(soundUri) ?: run {
            onFinished?.invoke()
            return
        }
        playing.set(true)

        val engine = detectEngine()
        thread(isDaemon = true, name = "expressive-clock-audio") {
            try {
                when (engine) {
                    "pw-play" -> runLoopingCommand(listOf("pw-play", file.absolutePath), looping)
                    "mpv" -> {
                        val cmd = mutableListOf("mpv", "--no-video", "--really-quiet")
                        if (looping) cmd += "--loop=inf"
                        cmd += file.absolutePath
                        runSingleCommand(cmd)
                    }
                    "aplay" -> runLoopingCommand(listOf("aplay", "-q", file.absolutePath), looping)
                    else -> playWithJavaClip(file, looping)
                }
            } catch (t: Throwable) {
                log.warning("Audio playback error: ${t.message}")
            } finally {
                playing.set(false)
                clearPidFile()
                onFinished?.invoke()
            }
        }
    }

    fun stop() {
        playing.set(false)
        activeProcess?.let { proc ->
            runCatching { proc.destroy() }
            runCatching { if (proc.isAlive) proc.destroyForcibly() }
        }
        activeProcess = null

        activeClip?.let { clip ->
            runCatching { clip.stop() }
            runCatching { clip.close() }
        }
        activeClip = null

        // Also stop any external script-spawned audio player recorded in the PID file
        runCatching {
            if (Files.exists(pidFile)) {
                val pid = Files.readString(pidFile).trim().toLongOrNull()
                if (pid != null) {
                    ProcessHandle.of(pid).ifPresent { it.destroy() }
                }
                Files.deleteIfExists(pidFile)
            }
        }
    }

    private fun runLoopingCommand(cmd: List<String>, looping: Boolean) {
        do {
            if (!playing.get()) break
            runSingleCommand(cmd)
        } while (looping && playing.get())
    }

    private fun runSingleCommand(cmd: List<String>) {
        val proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
        activeProcess = proc
        writePidFile(proc.pid())
        proc.waitFor()
        activeProcess = null
    }

    private fun playWithJavaClip(file: File, looping: Boolean) {
        AudioSystem.getAudioInputStream(file).use { stream ->
            val clip = AudioSystem.getClip()
            activeClip = clip
            clip.open(stream)
            if (looping) clip.loop(Clip.LOOP_CONTINUOUSLY) else clip.start()
            while (playing.get() && (looping || clip.isRunning)) {
                Thread.sleep(100)
            }
            clip.stop()
            clip.close()
            activeClip = null
        }
    }

    private fun detectEngine(): String {
        for (candidate in listOf("pw-play", "mpv", "aplay")) {
            val found = runCatching {
                ProcessBuilder("which", candidate).start().waitFor() == 0
            }.getOrDefault(false)
            if (found) return candidate
        }
        return "clip"
    }

    private fun writePidFile(pid: Long) {
        runCatching {
            Files.createDirectories(pidFile.parent)
            Files.writeString(pidFile, pid.toString())
        }
    }

    private fun clearPidFile() {
        runCatching { Files.deleteIfExists(pidFile) }
    }
}
