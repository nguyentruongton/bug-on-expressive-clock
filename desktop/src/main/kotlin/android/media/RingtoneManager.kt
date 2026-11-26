package android.media

import android.content.Context
import android.net.Uri
import java.io.File

class Ringtone(private val title: String) {
    fun getTitle(context: Context?): String = title
}

object RingtoneManager {
    const val TYPE_ALARM: Int = 4

    @JvmStatic
    fun getRingtone(context: Context?, uri: Uri?): Ringtone? {
        if (uri == null) return null
        val raw = uri.toString()
        val name = if (raw.isBlank()) "Default" else File(raw.removePrefix("file://")).nameWithoutExtension.ifBlank { raw }
        return Ringtone(name)
    }

    @JvmStatic
    fun getDefaultUri(type: Int): Uri? = null
}
