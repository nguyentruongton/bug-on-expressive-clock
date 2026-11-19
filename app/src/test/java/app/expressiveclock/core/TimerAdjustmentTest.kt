package app.expressiveclock.core

import java.time.Duration
import org.junit.Assert.*
import org.junit.Test

class TimerAdjustmentTest {
    @Test fun runningAdjustmentExtendsDeadlineByExactlyOneMinute() {
        val timer = ClockTimer(1, "Test", Duration.ofSeconds(10), TimerState.RUNNING, 10000)
        val next = timer.withAddedSeconds(60)
        assertEquals(70000L, next.deadlineElapsedMillis)
        assertEquals(Duration.ofSeconds(70), next.total)
        assertEquals(timer.state, next.state)
    }
    @Test fun screenAdjustmentStillAddsTenSeconds() {
        val timer = ClockTimer(1, "Test", Duration.ofSeconds(10), TimerState.RUNNING, 10000)
        assertEquals(20000L, timer.withAddedSeconds(10).deadlineElapsedMillis)
        assertEquals(Duration.ofSeconds(20), timer.withAddedSeconds(10).total)
    }
    @Test fun pausedAdjustmentPreservesPauseAndAddsMinuteToRemaining() {
        val timer = ClockTimer(1, "Test", Duration.ofHours(1), TimerState.PAUSED, 123, Duration.ofSeconds(5))
        val next = timer.withAddedSeconds(60)
        assertEquals(Duration.ofSeconds(65), next.pausedRemaining)
        assertEquals(123L, next.deadlineElapsedMillis)
        assertEquals(TimerState.PAUSED, next.state)
    }
}
