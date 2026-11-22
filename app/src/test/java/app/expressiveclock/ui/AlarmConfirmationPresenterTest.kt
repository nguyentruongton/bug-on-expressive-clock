package app.expressiveclock.ui

import kotlinx.coroutines.*
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmConfirmationPresenterTest {
    @Test fun rapidlyReplacingRequestsShowsOnlyTheLatestMessage() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val presenter = AlarmConfirmationPresenter(scope)
            repeat(20) { presenter.show("Alarm $it") }
            assertEquals("Alarm 19", presenter.hostState.currentSnackbarData?.visuals?.message)
            presenter.show("Alarm in 1 day, 13 hours and 33 minutes")
            assertEquals("Alarm in 1 day, 13 hours and 33 minutes", presenter.hostState.currentSnackbarData?.visuals?.message)
            presenter.hostState.currentSnackbarData?.dismiss()
            assertEquals(null, presenter.hostState.currentSnackbarData)
        } finally { scope.cancel() }
    }
}
