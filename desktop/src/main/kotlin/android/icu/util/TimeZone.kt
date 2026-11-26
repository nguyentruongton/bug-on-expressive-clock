package android.icu.util

import java.time.ZoneId

object TimeZone {
    private val legacyAliases = mapOf(
        "Asia/Calcutta" to "Asia/Kolkata",
        "Asia/Saigon" to "Asia/Ho_Chi_Minh",
        "Asia/Katmandu" to "Asia/Kathmandu",
        "Europe/Kiev" to "Europe/Kyiv",
        "America/Buenos_Aires" to "America/Argentina/Buenos_Aires",
        "US/Eastern" to "America/New_York",
        "US/Central" to "America/Chicago",
        "US/Mountain" to "America/Denver",
        "US/Pacific" to "America/Los_Angeles",
        "GMT" to "Etc/GMT",
        "UTC" to "Etc/UTC",
    )

    @JvmStatic
    fun getCanonicalID(id: String?): String? {
        if (id == null) return null
        return legacyAliases[id] ?: runCatching { ZoneId.of(id).id }.getOrDefault(id)
    }
}
