package app.expressiveclock.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClockStoreUiScaleTest {

    @Test
    fun legacySettingsJsonWithoutUiScaleDefaultsToOneAndPreservesOtherFields() = runBlocking {
        val tmpRoot = Files.createTempDirectory("clock-store-legacy-test")
        val cfgDir = tmpRoot.resolve("config")
        val dataDir = tmpRoot.resolve("data")
        Files.createDirectories(cfgDir)
        Files.createDirectories(dataDir)

        try {
            val legacyJson = """
                {
                  "theme": {
                    "dynamicColor": false,
                    "palette": "CORAL",
                    "darkMode": "DARK",
                    "amoledBlack": true
                  },
                  "defaultStartupPage": "WORLD",
                  "hasCompletedInitialSetup": true
                }
            """.trimIndent()
            Files.writeString(cfgDir.resolve("settings.json"), legacyJson)

            val store = ClockStore(configDirOverride = cfgDir, dataDirOverride = dataDir)
            assertEquals(1.0f, store.uiScaleNow(), 0.0001f)
            assertTrue(store.settingsNow().theme.amoledBlack)
            assertEquals(DefaultStartupPage.WORLD, store.settingsNow().defaultStartupPage)

            store.updateUiScale(1.2f)
            store.update { it.copy(defaultStartupPage = DefaultStartupPage.TIMER) }

            val reloaded = ClockStore(configDirOverride = cfgDir, dataDirOverride = dataDir)
            assertEquals(1.2f, reloaded.uiScaleNow(), 0.0001f)
            assertEquals(DefaultStartupPage.TIMER, reloaded.settingsNow().defaultStartupPage)
            assertTrue(reloaded.settingsNow().theme.amoledBlack)
        } finally {
            tmpRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun updateUiScaleClampsPersistsAndSurvivesReload() = runBlocking {
        val tmpRoot = Files.createTempDirectory("clock-store-scale-test")
        val cfgDir = tmpRoot.resolve("config")
        val dataDir = tmpRoot.resolve("data")
        Files.createDirectories(cfgDir)
        Files.createDirectories(dataDir)

        try {
            val store = ClockStore(configDirOverride = cfgDir, dataDirOverride = dataDir)
            assertEquals(1.3f, store.updateUiScale(1.3f), 0.0001f)

            val reloaded = ClockStore(configDirOverride = cfgDir, dataDirOverride = dataDir)
            assertEquals(1.3f, reloaded.uiScaleNow(), 0.0001f)

            assertEquals(1.5f, reloaded.updateUiScale(9.9f), 0.0001f)
            assertEquals(0.8f, reloaded.updateUiScale(0.2f), 0.0001f)
            assertEquals(1.0f, reloaded.updateUiScale(Float.NaN), 0.0001f)
        } finally {
            tmpRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun concurrentStepUiScaleCallsNeverDropIncrements() = runBlocking {
        val tmpRoot = Files.createTempDirectory("clock-store-concurrent-step-test")
        val cfgDir = tmpRoot.resolve("config")
        val dataDir = tmpRoot.resolve("data")
        Files.createDirectories(cfgDir)
        Files.createDirectories(dataDir)

        try {
            val store = ClockStore(configDirOverride = cfgDir, dataDirOverride = dataDir)
            store.updateUiScale(0.8f)

            val jobs = List(5) {
                async(Dispatchers.Default) {
                    store.stepUiScale(0.1f)
                }
            }
            jobs.awaitAll()

            assertEquals(1.3f, store.uiScaleNow(), 0.0001f)
            val reloaded = ClockStore(configDirOverride = cfgDir, dataDirOverride = dataDir)
            assertEquals(1.3f, reloaded.uiScaleNow(), 0.0001f)
        } finally {
            tmpRoot.toFile().deleteRecursively()
        }
    }
}
