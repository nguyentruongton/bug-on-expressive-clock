package app.expressiveclock

import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopIpcSocketTest {

    @Test
    fun unixSocketServerDeliversCommandsThroughDispatcherAndCleansUpStaleSocketFile() {
        val dir = Files.createTempDirectory("ec-ipc")
        val sockPath = mainSocketPath(dir)
        Files.writeString(sockPath, "stale")

        val received = AtomicReference<String?>(null)
        val ranOnEdt = AtomicBoolean(false)
        val latch = CountDownLatch(1)

        val server = startUnixSocketServer(
            path = sockPath,
            dispatch = { block -> SwingUtilities.invokeLater(block) },
        ) { msg ->
            received.set(msg)
            ranOnEdt.set(SwingUtilities.isEventDispatchThread())
            latch.countDown()
        }
        assertNotNull(server, "Expected Unix socket server to bind")
        try {
            val sent = sendToUnixSocket(sockPath, "ACTION snooze 42")
            assertTrue(sent, "Expected client message to be delivered")
            assertTrue(latch.await(3, TimeUnit.SECONDS), "Timed out waiting for socket callback")
            assertEquals("ACTION snooze 42", received.get())
            assertTrue(ranOnEdt.get(), "Callback must execute on Swing EDT")
        } finally {
            runCatching { server.close() }
            runCatching { Files.deleteIfExists(sockPath) }
            runCatching { Files.deleteIfExists(dir) }
        }
    }

    @Test
    fun cliActionStopsRingSessionOnlyWhenInvokedExternally() {
        val dir = Files.createTempDirectory("ec-ipc-paths")
        try {
            assertEquals(dir.resolve("expressive-clock.sock"), mainSocketPath(dir))
            assertFalse(sendToUnixSocket(mainSocketPath(dir), "FOCUS"))
            assertTrue(shouldStopRingSessionAfterCliAction(emptyMap()))
            assertTrue(shouldStopRingSessionAfterCliAction(mapOf("EXPRESSIVE_CLOCK_IN_RING_SESSION" to "0")))
            assertFalse(shouldStopRingSessionAfterCliAction(mapOf("EXPRESSIVE_CLOCK_IN_RING_SESSION" to "1")))
        } finally {
            runCatching { Files.deleteIfExists(dir) }
        }
    }
}
