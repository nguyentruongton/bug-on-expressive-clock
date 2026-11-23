package app.expressiveclock.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CredentialStorageTest {
    private val key = stringPreferencesKey("alarms")
    private suspend fun fixture(file: File) {
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { file }
        store.edit { it[key] = "persisted-user-alarms" }
        job.cancelAndJoin()
    }

    @Test fun earlyMissingFileReadReallyCachesDefaultsInDataStore121() = runBlocking {
        val dir = Files.createTempDirectory("clock-cache-proof").toFile()
        val job = SupervisorJob()
        try {
            val file = File(dir, "locked.preferences_pb")
            val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { file }
            assertNull(store.data.first()[key])
            val source = File(dir, "existing.preferences_pb")
            fixture(source)
            source.copyTo(file)
            // Unlock makes the existing file visible but does not update DataStore's coordinator.
            assertNull(store.data.first()[key])
        } finally { job.cancelAndJoin(); dir.deleteRecursively() }
    }

    @Test fun processReuseWaitsForUnlockBeforeAnyDelegateReadAndLoadsRealRecords() = runBlocking {
        val dir = Files.createTempDirectory("clock-unlock").toFile()
        val job = SupervisorJob()
        try {
            val file = File(dir, "clock.preferences_pb")
            val unlocked = MutableStateFlow(false)
            var accesses = 0
            val data = afterCredentialUnlock(unlocked) {
                accesses++
                PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { file }.data
            }
            val result = async(start = CoroutineStart.UNDISPATCHED) { data.first()[key] }
            assertFalse(result.isCompleted)
            assertEquals(0, accesses)
            fixture(file)
            unlocked.value = true
            assertEquals("persisted-user-alarms", withTimeout(5000) { result.await() })
            assertEquals(1, accesses)
        } finally { job.cancelAndJoin(); dir.deleteRecursively() }
    }

    @Test fun processRestartAfterUnlockLoadsSamePersistedData() = runBlocking {
        val dir = Files.createTempDirectory("clock-restart").toFile()
        try {
            val file = File(dir, "clock.preferences_pb")
            fixture(file)
            repeat(2) {
                val job = SupervisorJob()
                try {
                    val data = afterCredentialUnlock(flowOf(true)) {
                        PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { file }.data
                    }
                    assertEquals("persisted-user-alarms", data.first()[key])
                } finally { job.cancelAndJoin() }
            }
        } finally { dir.deleteRecursively() }
    }

    @Test fun repeatedUnlockSignalsDoNotRecreateDataSource() = runBlocking {
        val unlocks = MutableStateFlow(false)
        val values = MutableStateFlow(7)
        var accesses = 0
        val result = async(start = CoroutineStart.UNDISPATCHED) {
            afterCredentialUnlock(unlocks) { accesses++; values }.take(2).toList()
        }
        unlocks.value = true
        yield()
        unlocks.value = false
        unlocks.value = true
        values.value = 9
        assertEquals(listOf(7,9), withTimeout(5000) { result.await() })
        assertEquals(1, accesses)
    }

    @Test fun cancelledLockedCollectorDoesNotInitializeStorage() = runBlocking {
        var accessed = false
        val pending = launch(start = CoroutineStart.UNDISPATCHED) {
            afterCredentialUnlock(MutableStateFlow(false)) { accessed = true; flowOf(1) }.collect()
        }
        pending.cancelAndJoin()
        assertFalse(accessed)
    }
}
