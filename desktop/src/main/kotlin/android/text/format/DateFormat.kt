package android.text.format

import android.content.Context
import java.text.DateFormat as JavaDateFormat
import java.text.SimpleDateFormat
import java.util.Locale

object DateFormat {
    @JvmStatic
    fun is24HourFormat(context: Context?): Boolean {
        val pattern = (JavaDateFormat.getTimeInstance(JavaDateFormat.SHORT, Locale.getDefault()) as? SimpleDateFormat)
            ?.toPattern()
            ?: return true
        return pattern.contains('H') || pattern.contains('k')
    }

    @JvmStatic
    fun getBestDateTimePattern(locale: Locale, skeleton: String): String = skeleton

    @JvmStatic
    fun getTimeFormat(context: Context?): JavaDateFormat =
        JavaDateFormat.getTimeInstance(JavaDateFormat.SHORT, Locale.getDefault())
}
