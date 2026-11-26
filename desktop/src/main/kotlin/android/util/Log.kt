package android.util

import java.util.logging.Level
import java.util.logging.Logger

object Log {
    private fun logger(tag: String): Logger = Logger.getLogger("ExpressiveClock.$tag")

    @JvmStatic
    fun d(tag: String, msg: String): Int {
        logger(tag).log(Level.FINE, msg)
        return 0
    }

    @JvmStatic
    fun i(tag: String, msg: String): Int {
        logger(tag).log(Level.INFO, msg)
        return 0
    }

    @JvmStatic
    fun w(tag: String, msg: String, tr: Throwable? = null): Int {
        if (tr != null) logger(tag).log(Level.WARNING, msg, tr) else logger(tag).log(Level.WARNING, msg)
        return 0
    }

    @JvmStatic
    fun e(tag: String, msg: String, tr: Throwable? = null): Int {
        if (tr != null) logger(tag).log(Level.SEVERE, msg, tr) else logger(tag).log(Level.SEVERE, msg)
        return 0
    }
}
