package app.expressiveclock.data

import app.expressiveclock.core.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import androidx.datastore.preferences.core.*
import java.nio.file.Files

class DateAlarmCodecTest {
    private val date = LocalDate.of(2027, 6, 18)
    private val alarm = Alarm(123, LocalTime.of(6, 30), "Future", specificDate = date)
    @Test fun roundTripPreservesDateAndOccurrenceMetadata() {
        val a = alarm.copy(snoozedUntilMillis = 123456, skippedOccurrenceDate = date, skippedOccurrenceMillis = 456789)
        assertEquals(listOf(a), parseAlarms(encodeAlarms(listOf(a))))
    }
    @Test fun legacyRecordWithoutDateRetainsItsRule() {
        val a = alarm.copy(specificDate = null, days = setOf(DayOfWeek.FRIDAY))
        val json = encodeAlarms(listOf(a))
        assertFalse(json.contains("specificDate"))
        assertEquals(listOf(a), parseAlarms(json))
    }
    @Test fun nullDateIsLegacyCompatible() {
        val json = encodeAlarms(listOf(alarm.copy(specificDate = null))).replace("}]", ",\"specificDate\":null}]")
        assertNull(parseAlarms(json).single().specificDate)
        assertTrue(parseAlarms(json).single().enabled)
    }
    @Test fun malformedDateDisablesOnlyThatRecordAndSurvivesAnotherWrite() {
        for (raw in listOf("bad", "2027-02-30", "")) {
            val bad = encodeAlarms(listOf(alarm)).replace(date.toString(), raw)
            val decoded = parseAlarms(bad).single()
            assertFalse(decoded.enabled)
            assertEquals(raw, decoded.invalidSpecificDate)
            assertEquals(decoded, parseAlarms(encodeAlarms(listOf(decoded))).single())
            val combined = parseAlarms("[" + bad.removePrefix("[").removeSuffix("]") + "," +
                encodeAlarms(listOf(alarm.copy(id = 124))).removePrefix("[").removeSuffix("]") + "]")
            assertEquals(2, combined.size)
            assertTrue(combined.last().enabled)
        }
    }
    @Test fun nonStringExplicitDateFailsClosed() {
        val bad = encodeAlarms(listOf(alarm)).replace("\"$date\"", "42")
        assertFalse(parseAlarms(bad).single().enabled)
        assertEquals("42", parseAlarms(bad).single().invalidSpecificDate)
    }
    @Test fun conflictingModesAreNormalizedInBothCodecDirections() {
        val weekly = alarm.copy(days = setOf(DayOfWeek.MONDAY))
        assertTrue(parseAlarms(encodeAlarms(listOf(weekly))).single().days.isEmpty())
    }
    @Test fun lockedProcessDoesNotReadSeedDataAndUnlockRestoresExactIdentitiesAndTimes() = runBlocking {
        val dir = Files.createTempDirectory("date-recovery").toFile()
        val file = dir.resolve("clock.preferences_pb")
        val key = stringPreferencesKey("alarms")
        val fixtures = listOf(alarm, alarm.copy(id = 124, enabled = false),
            alarm.copy(id = 125, specificDate = null, days = setOf(DayOfWeek.MONDAY)))
        var job = SupervisorJob()
        var data = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { file }
        data.edit { it[key] = encodeAlarms(fixtures) }; job.cancelAndJoin()
        job = SupervisorJob()
        data = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { file }
        val unlocks = MutableStateFlow(false)
        var reads = 0
        val result = async { afterCredentialUnlock(unlocks) { reads++; data.data }.first() }
        yield(); assertEquals(0, reads)
        unlocks.value = true
        val restored = parseAlarms(result.await()[key]!!)
        val now = ZonedDateTime.of(2027, 1, 1, 0, 0, 0, 0, ZoneId.of("Australia/Sydney"))
        fun schedules(list: List<Alarm>) = list.mapNotNull { a -> a.nextFire(now)?.let { a.id to it.toInstant().toEpochMilli() } }.toMap()
        assertEquals(fixtures, restored)
        assertEquals(schedules(fixtures), schedules(restored))
        assertEquals(date.atTime(6, 30).atZone(now.zone).toInstant().toEpochMilli(), schedules(restored)[123])
        assertFalse(schedules(restored).containsKey(124))
        job.cancelAndJoin()
        job = SupervisorJob()
        data = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { file }
        assertEquals(schedules(fixtures), schedules(parseAlarms(data.data.first()[key]!!)))
        job.cancelAndJoin(); dir.deleteRecursively(); Unit
    }
}
