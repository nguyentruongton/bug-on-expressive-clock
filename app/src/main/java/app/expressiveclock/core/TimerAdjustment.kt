package app.expressiveclock.core

/** Duration adjustment shared by the Timer screen, overlay and notification actions. */
internal fun ClockTimer.withAddedSeconds(seconds: Long): ClockTimer = when (state) {
    TimerState.RUNNING -> copy(total = total.plusSeconds(seconds), deadlineElapsedMillis = deadlineElapsedMillis + seconds * 1000L)
    else -> copy(total = total.plusSeconds(seconds), pausedRemaining = pausedRemaining.plusSeconds(seconds))
}
