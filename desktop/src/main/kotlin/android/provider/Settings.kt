package android.provider

object Settings {
    object Global {
        const val ANIMATOR_DURATION_SCALE: String = "animator_duration_scale"

        @JvmStatic
        fun getFloat(cr: Any?, name: String, def: Float): Float = def
    }
}
